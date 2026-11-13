package tare

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tare.ble.ScaleManager
import tare.data.Source
import tare.data.TareRepository
import tare.data.WeighIn
import tare.health.HealthBridge
import tare.health.HealthSync
import kotlin.time.Instant

/** The app's long-lived objects, created once at launch. */
class AppGraph(val repo: TareRepository, healthBridge: HealthBridge, demo: Boolean = false) {
    val scope = MainScope()
    val scale = ScaleManager(repo, scope, demo = demo)
    val health = HealthSync(repo, healthBridge, scope)

    init {
        scope.launch { scale.saved.collect { health.syncPending() } }
        scope.launch {
            repo.settings.map { it.profile }.distinctUntilChanged().drop(1).collect { health.resyncAll() }
        }
        if (repo.settings.value.onboarded) scale.start()
        health.syncPending()
    }

    fun addManual(at: Instant, weightKg: Double): WeighIn =
        repo.add(at, weightKg, null, null, Source.MANUAL).also { health.syncPending() }

    fun delete(weighIn: WeighIn) {
        health.delete(weighIn)
        repo.delete(weighIn.id)
    }

    fun finishOnboarding() {
        repo.updateSettings { it.copy(onboarded = true) }
        scale.start()
    }
}
