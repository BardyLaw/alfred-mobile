package tv.alfred.sports

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** Game times in the phone's own time zone (the server sends UTC). */
internal object SportsTime {
    private val zone get() = TimeZone.currentSystemDefault()

    /** "Today" runs to 4 am, so a late West Coast game still counts as tonight's. */
    fun isToday(g: SportsApi.Game): Boolean {
        val now = Clock.System.now().toLocalDateTime(zone)
        val day = if (now.hour < 4) now.date.minus(1, DateTimeUnit.DAY) else now.date
        val end = LocalDateTime(day.plus(1, DateTimeUnit.DAY), LocalTime(4, 0)).toInstant(zone)
        return g.isLive || g.start < end
    }

    /** "7:30 PM" */
    fun time(at: Instant): String {
        val t = at.toLocalDateTime(zone)
        val h = if (t.hour % 12 == 0) 12 else t.hour % 12
        return "$h:${t.minute.toString().padStart(2, '0')} ${if (t.hour < 12) "AM" else "PM"}"
    }

    /** "Sat 7:30 PM" */
    fun dayTime(at: Instant): String {
        val day = at.toLocalDateTime(zone).dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        return "$day ${time(at)}"
    }
}
