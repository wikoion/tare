package tare.health

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tare.body.BodyComposition
import tare.data.TareRepository
import tare.data.WeighIn
import tare.data.composition
import kotlin.coroutines.resume

/**
 * The platform's health store (Apple Health on iOS), implemented natively.
 * Missing values are passed as NaN. Samples are tagged with the weigh-in id so they can be
 * replaced or deleted later.
 */
interface HealthBridge {
    fun isAvailable(): Boolean
    fun requestAuthorization(completion: (Boolean) -> Unit)
    fun saveWeighIn(
        id: Long,
        epochMillis: Long,
        weightKg: Double,
        bmi: Double,
        bodyFatFraction: Double,
        leanMassKg: Double,
        completion: (Boolean) -> Unit,
    )
    fun deleteWeighIn(id: Long, completion: (Boolean) -> Unit)
}

/** For platforms (and previews) without a health store. */
object NoHealthBridge : HealthBridge {
    override fun isAvailable() = false
    override fun requestAuthorization(completion: (Boolean) -> Unit) = completion(false)
    override fun saveWeighIn(
        id: Long, epochMillis: Long, weightKg: Double, bmi: Double, bodyFatFraction: Double,
        leanMassKg: Double, completion: (Boolean) -> Unit,
    ) = completion(false)
    override fun deleteWeighIn(id: Long, completion: (Boolean) -> Unit) = completion(false)
}

class HealthSync(
    private val repo: TareRepository,
    private val bridge: HealthBridge,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    val isAvailable: Boolean get() = bridge.isAvailable()

    suspend fun authorize(): Boolean = suspendCancellableCoroutine { c ->
        bridge.requestAuthorization { c.resume(it) }
    }

    /** Writes every weigh-in not yet in the health store, when the user has turned syncing on. */
    fun syncPending() = sync(all = false)

    /**
     * Writes every weigh-in again. Body fat, lean mass and BMI are worked out from the profile,
     * so a profile change makes the values already in the health store stale; saving a weigh-in
     * again replaces its samples.
     */
    fun resyncAll() = sync(all = true)

    private fun sync(all: Boolean) {
        if (!repo.settings.value.appleHealth || !bridge.isAvailable()) return
        scope.launch {
            mutex.withLock {
                if (all) repo.clearHealthSynced()
                val profile = repo.settings.value.profile
                for (w in repo.unsyncedToHealth()) {
                    if (save(w, w.composition(profile), profile.heightCm)) repo.markHealthSynced(w.id)
                }
            }
        }
    }

    fun delete(weighIn: WeighIn) {
        if (!weighIn.healthSynced || !bridge.isAvailable()) return
        bridge.deleteWeighIn(weighIn.id) {}
    }

    private suspend fun save(w: WeighIn, c: BodyComposition?, heightCm: Double?): Boolean =
        suspendCancellableCoroutine { cont ->
            bridge.saveWeighIn(
                id = w.id,
                epochMillis = w.takenAt.toEpochMilliseconds(),
                weightKg = w.weightKg,
                bmi = heightCm?.let { BodyComposition.bmi(w.weightKg, it) } ?: Double.NaN,
                bodyFatFraction = c?.let { it.bodyFatPercent / 100 } ?: Double.NaN,
                leanMassKg = c?.fatFreeMassKg ?: Double.NaN,
            ) { cont.resume(it) }
        }
}
