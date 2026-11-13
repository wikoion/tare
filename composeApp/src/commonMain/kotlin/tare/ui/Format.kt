package tare.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tare.platform.dayBeforeMonth
import tare.platform.uses24HourClock
import kotlin.time.Clock
import kotlin.time.Instant

/** Dates and times in the user's regional style, without depending on a platform formatter. */
object Format {
    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val dayFirst by lazy { dayBeforeMonth() }
    private val clock24 by lazy { uses24HourClock() }

    val zone: TimeZone get() = TimeZone.currentSystemDefault()

    fun local(instant: Instant): LocalDateTime = instant.toLocalDateTime(zone)

    fun time(instant: Instant): String {
        val t = local(instant)
        val mm = t.minute.toString().padStart(2, '0')
        if (clock24) return "${t.hour.toString().padStart(2, '0')}:$mm"
        val h = (t.hour + 11) % 12 + 1
        return "$h:$mm ${if (t.hour < 12) "AM" else "PM"}"
    }

    /** "Oct 26" or "26 Oct". */
    fun shortDate(date: LocalDate): String {
        val m = months[date.month.ordinal]
        return if (dayFirst) "${date.day} $m" else "$m ${date.day}"
    }

    /** "10/26" or "26/10", for tight chart labels. */
    fun numericDate(date: LocalDate): String {
        val m = date.month.ordinal + 1
        return if (dayFirst) "${date.day}/$m" else "$m/${date.day}"
    }

    /** "Today", "Yesterday", "Mon, Oct 20", or with the year when it isn't this year. */
    fun relativeDay(instant: Instant): String {
        val date = local(instant).date
        val today = Clock.System.now().toLocalDateTime(zone).date
        return when (date) {
            today -> "Today"
            LocalDate.fromEpochDays(today.toEpochDays() - 1) -> "Yesterday"
            else -> {
                val base = "${weekdays[date.dayOfWeek.ordinal]}, ${shortDate(date)}"
                if (date.year == today.year) base else "$base ${date.year}"
            }
        }
    }

    fun dayAndTime(instant: Instant): String = "${relativeDay(instant)} · ${time(instant)}"
}
