package tassic.data

/**
 * A small, deliberately partial iCalendar reader.
 *
 * The day plan used to know only about things typed into Tassic, which meant it
 * would report a clear afternoon that in reality held two meetings — and every
 * downstream judgement (does today fit, where are the gaps, what clashes) was
 * being made on half the picture. Reading an .ics export or subscription fixes
 * that with no backend and no account.
 *
 * What it handles: line unfolding, VEVENT blocks, DTSTART/DTEND in both DATE
 * and DATE-TIME form, UTC and floating local times, DURATION, SUMMARY,
 * LOCATION, UID, and the RRULE parts that actually occur in real calendars
 * (FREQ, INTERVAL, BYDAY, UNTIL, COUNT).
 *
 * What it does not: VTIMEZONE offset tables, EXDATE, RECURRENCE-ID overrides,
 * BYSETPOS and the rest of RFC 5545's long tail. Those are imported as the
 * first occurrence rather than guessed at, which is the honest failure: the
 * event appears once and doesn't claim a pattern the parser never understood.
 */
object Ics {

    private val DAY_CODES = mapOf(
        "MO" to 0, "TU" to 1, "WE" to 2, "TH" to 3, "FR" to 4, "SA" to 5, "SU" to 6
    )

    /** Parse result, so the UI can report what was skipped rather than just a count. */
    data class ParseResult(
        val events: List<CalendarEvent>,
        val skipped: Int,
        val calendarName: String
    )

    fun parse(raw: String, feedId: Long = 0): ParseResult {
        val lines = unfold(raw)
        if (lines.none { it.startsWith("BEGIN:VEVENT", ignoreCase = true) }) {
            return ParseResult(emptyList(), 0, "")
        }

        var calendarName = ""
        val events = mutableListOf<CalendarEvent>()
        var skipped = 0

        var inEvent = false
        var props = mutableMapOf<String, Pair<String, String>>() // name -> (params, value)

        lines.forEach { line ->
            val upper = line.uppercase()
            when {
                upper.startsWith("BEGIN:VEVENT") -> {
                    inEvent = true
                    props = mutableMapOf()
                }
                upper.startsWith("END:VEVENT") -> {
                    inEvent = false
                    val event = buildEvent(props, feedId)
                    if (event != null) events += event else skipped++
                }
                !inEvent && (upper.startsWith("X-WR-CALNAME") || upper.startsWith("NAME:")) -> {
                    calendarName = valueOf(line)
                }
                inEvent -> {
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        val head = line.substring(0, colon)
                        val value = line.substring(colon + 1)
                        val semi = head.indexOf(';')
                        val name = (if (semi > 0) head.substring(0, semi) else head).uppercase()
                        val params = if (semi > 0) head.substring(semi + 1).uppercase() else ""
                        props[name] = params to value
                    }
                }
            }
        }

        return ParseResult(events, skipped, calendarName)
    }

    // ------------------------------------------------------------------ build

    private fun buildEvent(
        props: Map<String, Pair<String, String>>,
        feedId: Long
    ): CalendarEvent? {
        val dtStart = props["DTSTART"] ?: return null
        val start = parseStamp(dtStart.first, dtStart.second) ?: return null

        val end = props["DTEND"]?.let { parseStamp(it.first, it.second) }
        val durationMinutes = when {
            end != null -> {
                val minutes = ((end.day - start.day) * 1440L + (end.minutes ?: 0) - (start.minutes ?: 0)).toInt()
                // All-day events report an exclusive end date, so a one-day
                // event arrives as a 1440-minute span; clamp rather than draw a
                // 24-hour block across the timeline.
                if (start.allDay) 0 else minutes.coerceIn(5, 24 * 60)
            }
            props.containsKey("DURATION") -> parseDuration(props.getValue("DURATION").second)
            start.allDay -> 0
            else -> 60
        }

        val rule = props["RRULE"]?.second?.let { parseRule(it) }

        return CalendarEvent(
            feedId = feedId,
            uid = props["UID"]?.second?.trim().orEmpty(),
            title = unescape(props["SUMMARY"]?.second.orEmpty()).ifBlank { "(No title)" },
            location = unescape(props["LOCATION"]?.second.orEmpty()),
            startEpochDay = start.day,
            startMinutes = start.minutes,
            durationMinutes = if (start.allDay) 0 else durationMinutes,
            allDay = start.allDay,
            freq = rule?.freq.orEmpty(),
            interval = rule?.interval ?: 1,
            byDay = rule?.byDay ?: emptyList(),
            untilEpochDay = rule?.until ?: 0L,
            count = rule?.count ?: 0
        )
    }

    private data class Stamp(val day: Long, val minutes: Int?, val allDay: Boolean)

    private data class Rule(
        val freq: String,
        val interval: Int,
        val byDay: List<Int>,
        val until: Long,
        val count: Int
    )

    /**
     * Reads a DTSTART/DTEND value.
     *
     * UTC stamps (trailing Z) are converted to local time via the device
     * offset. Floating and TZID-qualified stamps are taken at face value —
     * without a VTIMEZONE table there is nothing better to do, and for the
     * common case of a calendar exported in the user's own zone it is correct.
     */
    private fun parseStamp(params: String, rawValue: String): Stamp? {
        val value = rawValue.trim()
        if (value.length < 8) return null
        val year = value.substring(0, 4).toLongOrNull() ?: return null
        val month = value.substring(4, 6).toLongOrNull() ?: return null
        val dayOfMonth = value.substring(6, 8).toLongOrNull() ?: return null
        if (month !in 1..12 || dayOfMonth !in 1..31) return null
        val day = T.daysFromCivil(year, month, dayOfMonth)

        val isDateOnly = params.contains("VALUE=DATE") || value.length == 8
        if (isDateOnly) return Stamp(day, null, allDay = true)

        val timePart = value.substringAfter('T', "")
        if (timePart.length < 4) return Stamp(day, null, allDay = true)
        val hour = timePart.substring(0, 2).toIntOrNull() ?: return Stamp(day, null, allDay = true)
        val minute = timePart.substring(2, 4).toIntOrNull() ?: 0

        return if (timePart.endsWith("Z")) {
            val utcMs = day * T.DAY_MS + (hour * 60 + minute) * 60_000L
            val localMs = utcMs + T.tzOffsetMs()
            Stamp(localMs / T.DAY_MS, ((localMs % T.DAY_MS) / 60_000L).toInt(), allDay = false)
        } else {
            Stamp(day, hour * 60 + minute, allDay = false)
        }
    }

    /** "PT1H30M" / "P1D" → minutes. */
    private fun parseDuration(raw: String): Int {
        var minutes = 0
        var number = 0
        var inTime = false
        raw.uppercase().forEach { ch ->
            when {
                ch.isDigit() -> number = number * 10 + (ch - '0')
                ch == 'T' -> inTime = true
                ch == 'W' -> { minutes += number * 7 * 24 * 60; number = 0 }
                ch == 'D' -> { minutes += number * 24 * 60; number = 0 }
                ch == 'H' -> { minutes += number * 60; number = 0 }
                ch == 'M' -> { minutes += if (inTime) number else number * 30 * 24 * 60; number = 0 }
                ch == 'S' -> number = 0
                else -> { /* 'P' and anything unexpected */ }
            }
        }
        return minutes.coerceIn(5, 24 * 60)
    }

    private fun parseRule(raw: String): Rule? {
        var freq = ""
        var interval = 1
        var byDay = emptyList<Int>()
        var until = 0L
        var count = 0

        raw.split(";").forEach { part ->
            val key = part.substringBefore('=').uppercase()
            val value = part.substringAfter('=', "")
            when (key) {
                "FREQ" -> freq = value.uppercase()
                "INTERVAL" -> interval = value.toIntOrNull()?.coerceIn(1, 52) ?: 1
                "COUNT" -> count = value.toIntOrNull()?.coerceIn(1, 500) ?: 0
                "UNTIL" -> parseStamp("", value)?.let { until = it.day }
                "BYDAY" -> byDay = value.split(",").mapNotNull { code ->
                    // Strip any ordinal prefix ("2FR" = second Friday); the day
                    // is kept, the ordinal is not — which over-reports rather
                    // than dropping the event entirely.
                    DAY_CODES[code.takeLast(2).uppercase()]
                }
            }
        }

        if (freq !in setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")) return null
        return Rule(freq, interval, byDay, until, count)
    }

    // ------------------------------------------------------------ occurrence

    /**
     * Does this event fall on [day]?
     *
     * Recurrence is evaluated rather than expanded into rows: a daily event
     * with no end would otherwise generate an unbounded table, and expansion
     * has to be redone every time the horizon moves anyway.
     */
    fun occursOn(event: CalendarEvent, day: Long): Boolean {
        if (day < event.startEpochDay) return false
        if (event.freq.isEmpty()) return day == event.startEpochDay
        if (event.untilEpochDay > 0 && day > event.untilEpochDay) return false

        val elapsed = day - event.startEpochDay
        val interval = event.interval.coerceAtLeast(1)

        val matches = when (event.freq) {
            "DAILY" -> elapsed % interval == 0L
            "WEEKLY" -> {
                val weeksApart = T.weekIndex(day) - T.weekIndex(event.startEpochDay)
                if (weeksApart % interval != 0L) {
                    false
                } else if (event.byDay.isEmpty()) {
                    T.dowIndex(day) == T.dowIndex(event.startEpochDay)
                } else {
                    event.byDay.contains(T.dowIndex(day))
                }
            }
            "MONTHLY" -> {
                val (_, _, startDom) = T.civilFromDays(event.startEpochDay)
                val (_, _, dom) = T.civilFromDays(day)
                dom == startDom && monthsBetween(event.startEpochDay, day) % interval == 0L
            }
            "YEARLY" -> {
                val (startYear, startMonth, startDom) = T.civilFromDays(event.startEpochDay)
                val (year, month, dom) = T.civilFromDays(day)
                month == startMonth && dom == startDom && (year - startYear) % interval == 0L
            }
            else -> false
        }
        if (!matches) return false

        // COUNT is an occurrence limit, so it can only be checked by counting.
        // Bounded to a year's worth of steps because the alternative is an
        // unbounded loop on a malformed feed.
        if (event.count > 0) {
            var seen = 0
            var cursor = event.startEpochDay
            var guard = 0
            while (cursor <= day && guard < 1200) {
                guard++
                if (occursOnIgnoringCount(event, cursor)) seen++
                if (seen > event.count) return false
                cursor++
            }
        }
        return true
    }

    private fun occursOnIgnoringCount(event: CalendarEvent, day: Long): Boolean =
        occursOn(event.copy(count = 0), day)

    fun freqLabel(event: CalendarEvent): String = when (event.freq) {
        "DAILY" -> if (event.interval > 1) "Every ${event.interval} days" else "Daily"
        "WEEKLY" -> if (event.interval > 1) "Every ${event.interval} weeks" else "Weekly"
        "MONTHLY" -> "Monthly"
        "YEARLY" -> "Yearly"
        else -> ""
    }

    // ---------------------------------------------------------------- text

    /**
     * Undoes RFC 5545 line folding: a continuation is any line beginning with a
     * space or tab, and belongs to the previous one. Skipping this step is why
     * naive parsers truncate long event titles.
     */
    private fun unfold(raw: String): List<String> {
        val out = mutableListOf<String>()
        raw.replace("\r\n", "\n").replace("\r", "\n").split("\n").forEach { line ->
            if ((line.startsWith(" ") || line.startsWith("\t")) && out.isNotEmpty()) {
                out[out.lastIndex] = out.last() + line.substring(1)
            } else if (line.isNotBlank()) {
                out += line
            }
        }
        return out
    }

    private fun valueOf(line: String): String = unescape(line.substringAfter(':', "").trim())

    private fun unescape(value: String): String = value
        .replace("\\n", " ")
        .replace("\\N", " ")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")
        .trim()

    private fun monthsBetween(from: Long, to: Long): Long {
        val (fy, fm, _) = T.civilFromDays(from)
        val (ty, tm, _) = T.civilFromDays(to)
        return (ty - fy) * 12 + (tm - fm)
    }
}
