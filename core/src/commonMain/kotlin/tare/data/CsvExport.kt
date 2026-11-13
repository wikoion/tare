package tare.data

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tare.units.formatDecimal

/** Every weigh-in as CSV, in kilograms, with body composition derived from [profile]. */
object CsvExport {
    private val header = listOf(
        "time", "weight_kg", "source", "impedance_50khz_ohm", "impedance_500khz_ohm",
        "bmi", "body_fat_pct", "fat_free_mass_kg", "muscle_mass_kg", "skeletal_muscle_pct",
        "body_water_pct", "bone_mass_kg", "protein_pct", "visceral_fat", "subcutaneous_fat_pct",
        "bmr_kcal", "metabolic_age",
    )

    fun build(weighIns: List<WeighIn>, profile: Profile, zone: TimeZone = TimeZone.currentSystemDefault()): String =
        buildString {
            appendLine(header.joinToString(","))
            for (w in weighIns.sortedBy { it.takenAt }) {
                val c = w.composition(profile, zone)
                val bmi = profile.heightCm?.let { tare.body.BodyComposition.bmi(w.weightKg, it) }
                val row = listOf(
                    w.takenAt.toLocalDateTime(zone).toString(),
                    formatDecimal(w.weightKg, 2),
                    w.source.key,
                    w.impedanceOhms?.toString(),
                    w.impedanceHighOhms?.toString(),
                    bmi?.let { formatDecimal(it, 2) },
                    c?.let { formatDecimal(it.bodyFatPercent, 1) },
                    c?.let { formatDecimal(it.fatFreeMassKg, 2) },
                    c?.let { formatDecimal(it.muscleMassKg, 2) },
                    c?.let { formatDecimal(it.skeletalMusclePercent, 1) },
                    c?.let { formatDecimal(it.bodyWaterPercent, 1) },
                    c?.let { formatDecimal(it.boneMassKg, 2) },
                    c?.let { formatDecimal(it.proteinPercent, 1) },
                    c?.visceralFat?.toString(),
                    c?.let { formatDecimal(it.subcutaneousFatPercent, 1) },
                    c?.basalMetabolicRate?.toString(),
                    c?.metabolicAge?.toString(),
                )
                appendLine(row.joinToString(",") { it ?: "" })
            }
        }
}
