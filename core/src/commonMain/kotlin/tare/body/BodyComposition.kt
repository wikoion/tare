package tare.body

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

enum class Sex { MALE, FEMALE }

/**
 * Body-composition estimates from weight, height, age, sex and the scale's 50 kHz impedance.
 *
 * These formulas reproduce the algorithm the vendor app pairs with the ESF-24, ESF-551 and
 * FIT-8S, as reverse-engineered in `etekcity_esf551_ble` (MIT, Ron Ben Yosef). This class is a
 * Kotlin port of its `body_metrics.py`, constants included; see THIRD_PARTY_NOTICES.md. The
 * results are bioimpedance *estimates*, not clinical measurements.
 */
class BodyComposition(
    val weightKg: Double,
    heightCm: Double,
    val age: Int,
    val sex: Sex,
    val impedanceOhms: Int,
    val athlete: Boolean = false,
) {
    private val heightM = heightCm / 100.0
    private val s = if (sex == Sex.MALE) 0 else 1
    private fun pick(male: Double, female: Double) = if (s == 0) male else female

    val bmi: Double = bmi(weightKg, heightCm)

    val bodyFatPercent: Double = run {
        var bfp = pick(0.103, 0.097) * age + pick(1.524, 1.545) * bmi -
            500.0 / impedanceOhms - pick(22.0, 12.7)
        if (athlete) bfp = bfp / pick(3.5, 3.0) + bmi / pick(3.0, 2.4)
        (floor(bfp * 10) / 10).coerceIn(5.0, 75.0)
    }

    val fatMassKg: Double get() = weightKg - fatFreeMassKg

    val fatFreeMassKg: Double = roundTo(weightKg * (1 - bodyFatPercent / 100), 2)

    val visceralFat: Int = (
        pick(0.8666, 0.8895) * bmi + pick(0.0082, 0.0943) * bodyFatPercent +
            pick(0.026, -0.0534) * (weightKg - fatFreeMassKg) - pick(14.2692, 16.215)
        ).toInt().coerceIn(1, 30)

    val subcutaneousFatPercent: Double =
        roundTo(pick(0.965, 0.983) * bodyFatPercent - pick(0.22, 0.303) * visceralFat, 1)

    private val boneEstimate = max(1.0, pick(0.05, 0.06) * fatFreeMassKg)

    val bodyWaterPercent: Double =
        roundTo(pick(0.76, 0.73) * (fatFreeMassKg - boneEstimate) / weightKg * 100, 1).coerceIn(10.0, 80.0)

    val basalMetabolicRate: Int = (fatFreeMassKg * 21.6 + 370).toInt().coerceIn(900, 2500)

    val skeletalMusclePercent: Double =
        roundTo(pick(0.68, 0.62) * (fatFreeMassKg - boneEstimate) / weightKg * 100, 1)

    val muscleMassKg: Double = roundTo(fatFreeMassKg - boneEstimate, 2)

    val boneMassKg: Double = max(1.0, roundTo(pick(0.05, 0.06) * fatFreeMassKg, 2))

    val proteinPercent: Double = max(
        5.0,
        roundTo(100 - pick(1.0, 1.05) * bodyFatPercent - boneMassKg / weightKg * 100 - bodyWaterPercent, 1),
    )

    private val weightScore: Int = run {
        val ideal = pick(0.7, 0.45) * (pick(100.0, 137.0) * heightM - pick(80.0, 110.0))
        when {
            ideal <= weightKg ->
                if (ideal * 1.3 < weightKg) 50 else (100 - 50 * (weightKg - ideal) / (0.3 * ideal)).toInt()
            ideal * 0.7 < weightKg -> (100 - 50 * (ideal - weightKg) / (0.3 * ideal)).toInt()
            else -> (0..5).firstOrNull { ideal * it / 10 > weightKg }?.let { it * 10 } ?: 0
        }
    }

    private val fatScore: Int = run {
        val ideal = pick(16.0, 26.0)
        when {
            ideal < bodyFatPercent ->
                if (bodyFatPercent >= 45) 50 else (100 - 50 * (bodyFatPercent - ideal) / (45 - ideal)).toInt()
            else -> (100 - 50 * (ideal - bodyFatPercent) / (ideal - 5)).toInt()
        }
    }

    private val bmiScore: Int = when {
        bmi >= 35 -> 50
        bmi >= 22 -> (100 - 3.85 * (bmi - 22)).toInt()
        bmi >= 15 -> (100 - 3.85 * (22 - bmi)).toInt()
        bmi >= 10 -> 40
        bmi >= 5 -> 30
        else -> 20
    }

    private val healthScore: Int = (weightScore + fatScore + bmiScore) / 3

    val metabolicAge: Int = run {
        val thresholds = intArrayOf(50, 60, 65, 68, 70, 73, 75, 80, 85, 88, 90, 93, 95, 97, 98, 99)
        val adjustment = thresholds.indexOfFirst { healthScore < it }.let { if (it == -1) 16 else it }
        max(18, age + 8 - adjustment)
    }

    companion object {
        /** BMI truncated to two decimals, as the vendor app shows it. */
        fun bmi(weightKg: Double, heightCm: Double): Double {
            val h = heightCm / 100.0
            return floor(weightKg / (h * h) * 100) / 100
        }
    }
}

internal fun roundTo(value: Double, decimals: Int): Double {
    val f = 10.0.pow(decimals)
    return round(value * f) / f
}
