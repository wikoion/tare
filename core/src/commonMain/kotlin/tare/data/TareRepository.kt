package tare.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import tare.body.Sex
import tare.db.TareDb
import tare.protocol.ScaleModel
import tare.units.WeightUnit
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

expect fun createSqlDriver(name: String = "tare.db"): SqlDriver

class TareRepository(driver: SqlDriver, defaultUnit: WeightUnit = WeightUnit.KG) {
    private val db = TareDb(driver)
    private val measurements = db.measurementQueries
    private val settingsQueries = db.settingQueries

    val weighIns: Flow<List<WeighIn>> =
        measurements.selectAll(::toWeighIn).asFlow().mapToList(Dispatchers.Default)

    private val _settings = MutableStateFlow(loadSettings(defaultUnit))
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun all(): List<WeighIn> = measurements.selectAll(::toWeighIn).executeAsList()

    fun byId(id: Long): WeighIn? = measurements.selectById(id, ::toWeighIn).executeAsOneOrNull()

    fun add(takenAt: Instant, weightKg: Double, impedance: Int?, impedanceHigh: Int?, source: Source): WeighIn =
        measurements.transactionWithResult {
            measurements.insert(
                takenAt.toEpochMilliseconds(), weightKg,
                impedance?.toLong(), impedanceHigh?.toLong(), source.key,
            )
            val id = measurements.lastInsertId().executeAsOne()
            WeighIn(id, takenAt, weightKg, impedance, impedanceHigh, source, healthSynced = false)
        }

    /**
     * Saves a reading the scale stored offline, unless it is already here. The scale can store
     * a weigh-in that was also delivered live, so a reading within 15 minutes and 0.1 kg of an
     * existing one counts as the same. Readings under 10 kg (a bag, a pet) are skipped.
     */
    fun importStored(takenAt: Instant, weightKg: Double, impedance: Int?, impedanceHigh: Int?): WeighIn? {
        if (weightKg < 10.0) return null
        val window = 15.minutes
        val nearby = measurements.selectBetween(
            (takenAt - window).toEpochMilliseconds(),
            (takenAt + window).toEpochMilliseconds(),
            ::toWeighIn,
        ).executeAsList()
        if (nearby.any { abs(it.weightKg - weightKg) <= 0.1 }) return null
        return add(takenAt, weightKg, impedance, impedanceHigh, Source.SCALE_OFFLINE)
    }

    fun delete(id: Long) = measurements.deleteById(id)

    fun unsyncedToHealth(): List<WeighIn> = measurements.selectUnsynced(::toWeighIn).executeAsList()

    fun markHealthSynced(id: Long) = measurements.markHealthSynced(id)

    fun clearHealthSynced() = measurements.clearHealthSynced()

    fun updateSettings(transform: (Settings) -> Settings) {
        _settings.update { old ->
            transform(old).also { new -> if (new != old) saveSettings(new) }
        }
    }

    private fun loadSettings(defaultUnit: WeightUnit): Settings {
        val kv = settingsQueries.selectAll().executeAsList().associate { it.key to it.value_ }
        fun bool(key: String, default: Boolean) = kv[key]?.toBooleanStrictOrNull() ?: default
        val scaleId = kv[K_SCALE_ID]
        return Settings(
            profile = Profile(
                sex = kv[K_SEX]?.let { runCatching { Sex.valueOf(it) }.getOrNull() } ?: Sex.MALE,
                birthDate = kv[K_BIRTH]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                heightCm = kv[K_HEIGHT]?.toDoubleOrNull(),
                athlete = bool(K_ATHLETE, false),
            ),
            unit = kv[K_UNIT]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() } ?: defaultUnit,
            goalWeightKg = kv[K_GOAL]?.toDoubleOrNull(),
            pairedScale = scaleId?.let {
                PairedScale(it, kv[K_SCALE_NAME] ?: "Scale", kv[K_SCALE_MODEL]?.let { m -> ScaleModel.entries.firstOrNull { e -> e.name == m } })
            },
            importOfflineReadings = bool(K_IMPORT_OFFLINE, true),
            appleHealth = bool(K_HEALTH, false),
            onboarded = bool(K_ONBOARDED, false),
        )
    }

    private fun saveSettings(s: Settings) = settingsQueries.transaction {
        fun put(key: String, value: Any?) =
            if (value == null) settingsQueries.remove(key) else settingsQueries.put(key, value.toString())
        put(K_SEX, s.profile.sex.name)
        put(K_BIRTH, s.profile.birthDate)
        put(K_HEIGHT, s.profile.heightCm)
        put(K_ATHLETE, s.profile.athlete)
        put(K_UNIT, s.unit.name)
        put(K_GOAL, s.goalWeightKg)
        put(K_SCALE_ID, s.pairedScale?.identifier)
        put(K_SCALE_NAME, s.pairedScale?.name)
        put(K_SCALE_MODEL, s.pairedScale?.model?.name)
        put(K_IMPORT_OFFLINE, s.importOfflineReadings)
        put(K_HEALTH, s.appleHealth)
        put(K_ONBOARDED, s.onboarded)
    }

    private fun toWeighIn(
        id: Long,
        takenAt: Long,
        weightKg: Double,
        impedance: Long?,
        impedanceHigh: Long?,
        source: String,
        healthSynced: Long,
    ) = WeighIn(
        id = id,
        takenAt = Instant.fromEpochMilliseconds(takenAt),
        weightKg = weightKg,
        impedanceOhms = impedance?.toInt(),
        impedanceHighOhms = impedanceHigh?.toInt(),
        source = Source.of(source),
        healthSynced = healthSynced != 0L,
    )

    private companion object {
        const val K_SEX = "profile.sex"
        const val K_BIRTH = "profile.birthDate"
        const val K_HEIGHT = "profile.heightCm"
        const val K_ATHLETE = "profile.athlete"
        const val K_UNIT = "unit"
        const val K_GOAL = "goalWeightKg"
        const val K_SCALE_ID = "scale.identifier"
        const val K_SCALE_NAME = "scale.name"
        const val K_SCALE_MODEL = "scale.model"
        const val K_IMPORT_OFFLINE = "scale.importOffline"
        const val K_HEALTH = "appleHealth"
        const val K_ONBOARDED = "onboarded"
    }
}
