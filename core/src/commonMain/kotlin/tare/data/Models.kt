package tare.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearsUntil
import tare.body.BodyComposition
import tare.body.Sex
import tare.protocol.ScaleModel
import tare.units.WeightUnit
import kotlin.time.Instant

enum class Source(val key: String) {
    SCALE("scale"),
    SCALE_OFFLINE("scale_offline"),
    MANUAL("manual");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: MANUAL
    }
}

data class WeighIn(
    val id: Long,
    val takenAt: Instant,
    val weightKg: Double,
    val impedanceOhms: Int?,
    val impedanceHighOhms: Int?,
    val source: Source,
    val healthSynced: Boolean,
)

data class Profile(
    val sex: Sex = Sex.MALE,
    val birthDate: LocalDate? = null,
    val heightCm: Double? = null,
    val athlete: Boolean = false,
) {
    val isComplete: Boolean get() = birthDate != null && heightCm != null

    fun ageOn(date: LocalDate): Int? = birthDate?.yearsUntil(date)
}

/** [model] is null when the user paired a device Tare didn't recognise. */
data class PairedScale(val identifier: String, val name: String, val model: ScaleModel? = null)

data class Settings(
    val profile: Profile = Profile(),
    val unit: WeightUnit = WeightUnit.KG,
    val goalWeightKg: Double? = null,
    val pairedScale: PairedScale? = null,
    /** Fetch readings the scale stored while the app wasn't connected. This removes them from the scale. */
    val importOfflineReadings: Boolean = true,
    val appleHealth: Boolean = false,
    val onboarded: Boolean = false,
)

/**
 * Body composition for a weigh-in, or null when it can't be estimated: no impedance (bare feet
 * are needed), or the profile lacks height or birth date. Age is taken on the day of the reading.
 */
fun WeighIn.composition(profile: Profile, zone: TimeZone = TimeZone.currentSystemDefault()): BodyComposition? {
    val impedance = impedanceOhms ?: return null
    val height = profile.heightCm ?: return null
    val age = profile.ageOn(takenAt.toLocalDateTime(zone).date) ?: return null
    if (age !in 10..99) return null
    return BodyComposition(weightKg, height, age, profile.sex, impedance, profile.athlete)
}
