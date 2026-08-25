package tassic.data

import tassic.platform.jsNow

/** Pure-Kotlin civil calendar math (no external datetime dependency needed). */
object T {

    const val DAY_MS: Long = 86_400_000L

    val DAY_TAGS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val MONTHS = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    fun now(): Long = jsNow().toLong()

    fun today(): Long = now() / DAY_MS

    /** 0 = Monday … 6 = Sunday */
    fun dowIndex(epochDay: Long): Int {
        val idx = ((epochDay + 3) % 7 + 7) % 7 // 1970-01-01 was a Thursday
        return idx.toInt()
    }

    fun dayTagOf(epochDay: Long): String = DAY_TAGS[dowIndex(epochDay)]

    fun dayName(epochDay: Long): String = DAY_NAMES[dowIndex(epochDay)]

    fun isWeekend(epochDay: Long): Boolean = dowIndex(epochDay) >= 5

    fun tagMatches(tag: String, epochDay: Long): Boolean {
        val t = tag.trim().uppercase()
        if (t.isEmpty() || t == "ALL" || t == "ANY") return true
        if (t == "WEEKEND") return isWeekend(epochDay)
        if (t == "WEEKDAY") return !isWeekend(epochDay)
        return t == dayTagOf(epochDay)
    }

    /** Monday-aligned ISO week number (used for weekly targets). */
    fun weekIndex(epochDay: Long): Long = (epochDay + 3) / 7

    fun dayOfYear(epochDay: Long): Int {
        val (y, _, _) = civilFromDays(epochDay)
        return (epochDay - daysFromCivil(y, 1, 1)).toInt() + 1
    }

    fun dateLabel(epochDay: Long): String {
        val (y, m, d) = civilFromDays(epochDay)
        return "$d ${MONTHS[(m - 1).toInt()]} $y"
    }

    fun shortDate(epochDay: Long): String {
        val (_, m, d) = civilFromDays(epochDay)
        return "$d ${MONTHS[(m - 1).toInt()]}"
    }

    fun fullLabel(ms: Long): String {
        val day = ms / DAY_MS
        val minutes = (ms % DAY_MS) / 60_000
        val hh = (minutes / 60).toString().padStart(2, '0')
        val mm = (minutes % 60).toString().padStart(2, '0')
        return "${dateLabel(day)} · $hh:$mm"
    }

    fun timeLabel(ms: Long): String {
        val minutes = (ms % DAY_MS) / 60_000
        val hh = (minutes / 60).toString().padStart(2, '0')
        val mm = (minutes % 60).toString().padStart(2, '0')
        return "$hh:$mm"
    }

    fun relativeDays(targetEpochDay: Long, today: Long): String {
        val diff = (targetEpochDay - today).toInt()
        return when {
            diff == 0 -> "today"
            diff == 1 -> "tomorrow"
            diff == -1 -> "yesterday"
            diff > 0 -> "in ${diff}d"
            else -> "${-diff}d ago"
        }
    }

    /** Howard Hinnant's civil_from_days. */
    fun civilFromDays(z: Long): Triple<Long, Long, Long> {
        val zz = z + 719468L
        val era = (if (zz >= 0) zz else zz - 146096L) / 146097L
        val doe = zz - era * 146097L
        val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
        val y = yoe + era * 400L
        val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
        val mp = (5L * doy + 2L) / 153L
        val d = doy - (153L * mp + 2L) / 5L + 1L
        val m = if (mp < 10L) mp + 3L else mp - 9L
        return Triple(if (m <= 2L) y + 1L else y, m, d)
    }

    /** Howard Hinnant's days_from_civil. */
    fun daysFromCivil(yIn: Long, m: Long, d: Long): Long {
        val y = if (m <= 2L) yIn - 1L else yIn
        val era = (if (y >= 0) y else y - 399L) / 400L
        val yoe = y - era * 400L
        val doy = (153L * (if (m > 2L) m - 3L else m + 9L) + 2L) / 5L + d - 1L
        val doe = yoe * 365L + yoe / 4L - yoe / 100L + doy
        return era * 146097L + doe - 719468L
    }
}
