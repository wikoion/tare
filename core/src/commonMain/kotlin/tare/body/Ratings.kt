package tare.body

/** How a value compares with a reference range, for the coloured label under each metric. */
data class Rating(val label: String, val tone: Tone) {
    enum class Tone { GOOD, NEUTRAL, CAUTION, ALERT }
}

/**
 * Reference ranges. These are widely published general-population bands (WHO for BMI, the
 * American Council on Exercise for body fat, the usual 1-12 visceral-fat scale), not the
 * vendor app's, and not medical advice.
 */
object Ratings {
    private val low = Rating("Low", Rating.Tone.CAUTION)
    private val healthy = Rating("Healthy", Rating.Tone.GOOD)
    private val high = Rating("High", Rating.Tone.CAUTION)
    private val veryHigh = Rating("Very high", Rating.Tone.ALERT)

    fun bmi(bmi: Double): Rating = when {
        bmi < 18.5 -> low
        bmi < 25.0 -> healthy
        bmi < 30.0 -> high
        else -> veryHigh
    }

    /** Weight is judged by the BMI it gives at the user's height. */
    fun weight(weightKg: Double, heightCm: Double): Rating = bmi(BodyComposition.bmi(weightKg, heightCm))

    /** Healthy weight band for a height: BMI 18.5 to 25. */
    fun healthyWeightRangeKg(heightCm: Double): ClosedFloatingPointRange<Double> {
        val h = heightCm / 100.0
        return (18.5 * h * h)..(25.0 * h * h)
    }

    fun bodyFat(percent: Double, sex: Sex): Rating {
        // ACE bands: essential / athletes / fitness / average / obese.
        val bands = if (sex == Sex.MALE) doubleArrayOf(6.0, 14.0, 18.0, 25.0) else doubleArrayOf(14.0, 21.0, 25.0, 32.0)
        return when {
            percent < bands[0] -> low
            percent < bands[1] -> Rating("Athletic", Rating.Tone.GOOD)
            percent < bands[2] -> Rating("Fit", Rating.Tone.GOOD)
            percent < bands[3] -> Rating("Average", Rating.Tone.NEUTRAL)
            else -> high
        }
    }

    fun visceralFat(level: Int): Rating = when {
        level <= 9 -> healthy
        level <= 14 -> high
        else -> veryHigh
    }

    fun bodyWater(percent: Double, sex: Sex): Rating {
        val range = if (sex == Sex.MALE) 50.0..65.0 else 45.0..60.0
        return when {
            percent < range.start -> low
            percent <= range.endInclusive -> healthy
            else -> Rating("High", Rating.Tone.NEUTRAL)
        }
    }

    fun protein(percent: Double): Rating = when {
        percent < 16.0 -> low
        percent <= 20.0 -> healthy
        else -> Rating("High", Rating.Tone.GOOD)
    }

    fun metabolicAge(metabolicAge: Int, age: Int): Rating =
        if (metabolicAge <= age) Rating("Younger", Rating.Tone.GOOD) else Rating("Older", Rating.Tone.CAUTION)
}
