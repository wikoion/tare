package tare.ui

import tare.body.BodyComposition
import tare.body.Rating
import tare.body.Ratings
import tare.data.Profile
import tare.data.WeighIn
import tare.data.composition
import tare.units.WeightFormat
import tare.units.WeightUnit
import tare.units.formatDecimal
import tare.units.kgToLb

enum class Metric(val label: String, val needsImpedance: Boolean, val about: String) {
    WEIGHT("Weight", false, "Total body weight."),
    BMI("BMI", false, "Body mass index: weight divided by height squared. It can't tell muscle from fat."),
    BODY_FAT("Body fat", true, "Share of your weight that is fat, estimated from bioimpedance."),
    MUSCLE_MASS("Muscle mass", true, "Estimated weight of all muscle, including smooth and heart muscle."),
    SKELETAL_MUSCLE("Skeletal muscle", true, "Muscle attached to bone, as a share of your weight."),
    FAT_FREE_MASS("Fat-free mass", true, "Everything that isn't fat: muscle, bone, organs and water."),
    BODY_WATER("Body water", true, "Total water as a share of your weight. It shifts with hydration."),
    BONE_MASS("Bone mass", true, "Estimated mineral weight of your bones."),
    PROTEIN("Protein", true, "Estimated protein as a share of your weight."),
    VISCERAL_FAT("Visceral fat", true, "An index of fat around the organs. 1–9 is the usual healthy band."),
    SUBCUTANEOUS_FAT("Subcutaneous fat", true, "Fat just under the skin, as a share of your weight."),
    BMR("BMR", true, "Basal metabolic rate: energy your body uses at complete rest."),
    METABOLIC_AGE("Metabolic age", true, "Compares your BMR with typical values for each age."),
}

data class MetricValue(
    /** The number to plot, in the display unit. */
    val value: Double,
    val text: String,
    val unit: String,
    val rating: Rating?,
)

/** A metric for one weigh-in in the user's units, or null when it can't be worked out. */
fun Metric.of(w: WeighIn, profile: Profile, unit: WeightUnit, c: BodyComposition? = w.composition(profile)): MetricValue? {
    fun mass(kg: Double, rating: Rating? = null): MetricValue {
        val lbUnit = unit != WeightUnit.KG
        val v = if (lbUnit) kgToLb(kg) else kg
        return MetricValue(v, formatDecimal(v, 1), if (lbUnit) "lb" else "kg", rating)
    }
    fun pct(p: Double, rating: Rating? = null) = MetricValue(p, formatDecimal(p, 1), "%", rating)

    return when (this) {
        Metric.WEIGHT -> {
            val d = WeightFormat.compact(w.weightKg, unit)
            val plotted = when (unit) {
                WeightUnit.KG -> w.weightKg
                WeightUnit.LB -> kgToLb(w.weightKg)
                WeightUnit.ST -> kgToLb(w.weightKg) / 14
            }
            MetricValue(plotted, d.value, d.unit, profile.heightCm?.let { Ratings.weight(w.weightKg, it) })
        }
        Metric.BMI -> profile.heightCm?.let {
            val bmi = BodyComposition.bmi(w.weightKg, it)
            MetricValue(bmi, formatDecimal(bmi, 1), "", Ratings.bmi(bmi))
        }
        Metric.BODY_FAT -> c?.let { pct(it.bodyFatPercent, Ratings.bodyFat(it.bodyFatPercent, profile.sex)) }
        Metric.MUSCLE_MASS -> c?.let { mass(it.muscleMassKg) }
        Metric.SKELETAL_MUSCLE -> c?.let { pct(it.skeletalMusclePercent) }
        Metric.FAT_FREE_MASS -> c?.let { mass(it.fatFreeMassKg) }
        Metric.BODY_WATER -> c?.let { pct(it.bodyWaterPercent, Ratings.bodyWater(it.bodyWaterPercent, profile.sex)) }
        Metric.BONE_MASS -> c?.let { mass(it.boneMassKg) }
        Metric.PROTEIN -> c?.let { pct(it.proteinPercent, Ratings.protein(it.proteinPercent)) }
        Metric.VISCERAL_FAT -> c?.let {
            MetricValue(it.visceralFat.toDouble(), it.visceralFat.toString(), "", Ratings.visceralFat(it.visceralFat))
        }
        Metric.SUBCUTANEOUS_FAT -> c?.let { pct(it.subcutaneousFatPercent) }
        Metric.BMR -> c?.let { MetricValue(it.basalMetabolicRate.toDouble(), it.basalMetabolicRate.toString(), "kcal", null) }
        Metric.METABOLIC_AGE -> c?.let {
            MetricValue(it.metabolicAge.toDouble(), it.metabolicAge.toString(), "yrs", Ratings.metabolicAge(it.metabolicAge, it.age))
        }
    }
}

/** Formats a plotted value of [metric] for chart labels. */
fun Metric.formatPlotted(value: Double, unit: WeightUnit): String = when (this) {
    Metric.WEIGHT -> if (unit == WeightUnit.ST) formatDecimal(value, 1) + " st" else formatDecimal(value, 1)
    Metric.VISCERAL_FAT, Metric.BMR, Metric.METABOLIC_AGE -> formatDecimal(value, 0)
    else -> formatDecimal(value, 1)
}
