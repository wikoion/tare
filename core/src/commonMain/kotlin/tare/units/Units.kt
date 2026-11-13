package tare.units

import tare.protocol.ScaleDisplayUnit
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

enum class WeightUnit(val symbol: String) {
    KG("kg"),
    LB("lb"),
    ST("st");

    val scaleUnit: ScaleDisplayUnit
        get() = when (this) {
            KG -> ScaleDisplayUnit.KG
            LB -> ScaleDisplayUnit.LB
            ST -> ScaleDisplayUnit.ST
        }
}

const val LB_PER_KG = 2.2046226218

fun kgToLb(kg: Double) = kg * LB_PER_KG
fun lbToKg(lb: Double) = lb / LB_PER_KG

/** A weight split for display: the number, and its unit label. Stones show as "12 st 5.1". */
data class DisplayWeight(val value: String, val unit: String)

object WeightFormat {
    fun display(kg: Double, unit: WeightUnit): DisplayWeight = when (unit) {
        WeightUnit.KG -> DisplayWeight(formatDecimal(kg, 1), "kg")
        WeightUnit.LB -> DisplayWeight(formatDecimal(kgToLb(kg), 1), "lb")
        WeightUnit.ST -> {
            val tenthsOfLb = (kgToLb(kg) * 10).roundToLong()
            val stones = tenthsOfLb / 140
            val rest = (tenthsOfLb % 140) / 10.0
            DisplayWeight("$stones st ${formatDecimal(rest, 1)}", "lb")
        }
    }

    /** Like [display] but narrow: stones as "12:5.1" st, as the scale's own display shows them. */
    fun compact(kg: Double, unit: WeightUnit): DisplayWeight =
        if (unit == WeightUnit.ST) {
            DisplayWeight(display(kg, unit).value.replace(" st ", ":"), "st")
        } else {
            display(kg, unit)
        }

    fun text(kg: Double, unit: WeightUnit): String = display(kg, unit).let { "${it.value} ${it.unit}" }

    /** A change in weight, unsigned (the caller shows the direction). Stones show in pounds. */
    fun delta(kg: Double, unit: WeightUnit): DisplayWeight = when (unit) {
        WeightUnit.KG -> DisplayWeight(formatDecimal(abs(kg), 1), "kg")
        else -> DisplayWeight(formatDecimal(abs(kgToLb(kg)), 1), "lb")
    }

    /** Parses what the user typed, in their unit. Stones accept "12 5.1", "12st 5.1" or "12.4". */
    fun parse(text: String, unit: WeightUnit): Double? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty()) return null
        return when (unit) {
            WeightUnit.KG -> t.toDoubleOrNull()
            WeightUnit.LB -> t.toDoubleOrNull()?.let(::lbToKg)
            WeightUnit.ST -> {
                val parts = t.lowercase().replace("st", " ").replace("lb", " ").split(' ').filter { it.isNotBlank() }
                when (parts.size) {
                    1 -> parts[0].toDoubleOrNull()?.let { lbToKg(it * 14) }
                    2 -> {
                        val st = parts[0].toDoubleOrNull() ?: return null
                        val lb = parts[1].toDoubleOrNull() ?: return null
                        lbToKg(st * 14 + lb)
                    }
                    else -> null
                }
            }
        }?.takeIf { it > 0 && it < 400 }
    }
}

object HeightFormat {
    fun text(cm: Double, imperial: Boolean): String =
        if (imperial) {
            val inches = (cm / 2.54).roundToLong()
            "${inches / 12}′ ${inches % 12}″"
        } else {
            "${cm.roundToLong()} cm"
        }
}

/** Rounds half away from zero to [decimals] places, without trailing-zero trimming. */
fun formatDecimal(value: Double, decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val scaled = (abs(value) * factor + 1e-9).roundToLong()
    val factorL = factor.roundToLong()
    val whole = scaled / factorL
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    if (decimals == 0) return "$sign$whole"
    val frac = (scaled % factorL).toString().padStart(decimals, '0')
    return "$sign$whole.$frac"
}
