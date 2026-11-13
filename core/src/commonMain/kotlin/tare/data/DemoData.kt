package tare.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import tare.body.Sex
import tare.protocol.ScaleModel
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Synthetic history for screenshots and the simulator (launch argument `-demoData`). The figures
 * are made up: keep them clear of any real person's readings.
 */
object DemoData {
    fun seed(repo: TareRepository, days: Int = 120) {
        if (repo.all().isNotEmpty()) return
        val random = Random(24)
        val now = Clock.System.now()
        val zone = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(zone).date
        for (d in days downTo 0) {
            if (d > 0 && random.nextInt(7) == 0) continue // skipped days
            val trend = 81.4 - (days - d) * 0.022
            val wobble = sin(d / 9.0 * PI) * 0.35 + random.nextDouble(-0.25, 0.25)
            val kg = ((trend + wobble) * 100).toInt() / 100.0
            val impedance = 470 + random.nextInt(-12, 12) + (days - d) / 10
            // Earlier weigh-ins fall on the morning of their own day, so no two share a date.
            val at = if (d == 0) {
                now - random.nextInt(0, 50).minutes
            } else {
                val time = LocalTime(6 + random.nextInt(0, 3), random.nextInt(0, 60))
                LocalDateTime(today.minus(d, DateTimeUnit.DAY), time).toInstant(zone)
            }
            if (d % 11 == 5) {
                repo.add(at, kg, null, null, Source.MANUAL)
            } else {
                repo.add(at, kg, impedance, impedance - 40, Source.SCALE)
            }
        }
        repo.updateSettings {
            it.copy(
                profile = Profile(Sex.MALE, LocalDate(1990, 4, 12), heightCm = 182.0, athlete = false),
                goalWeightKg = 76.0,
                pairedScale = PairedScale("demo-esf551", ScaleModel.ESF551.displayName, ScaleModel.ESF551),
                onboarded = true,
            )
        }
    }
}
