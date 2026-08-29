package tassic.data

/**
 * Natural-language quick capture.
 *
 * The single biggest thing standing between a person and an organised life is
 * how long it takes to write something down. Opening a sheet, typing a title,
 * tapping a date chip, tapping a time stepper, tapping a repeat chip and then
 * tapping Save is six interactions for a thought that took one second to have —
 * so the thought doesn't get written down, and the app slowly becomes a record
 * of the few things that were worth the ceremony.
 *
 * This turns one line of ordinary text into a fully-formed row:
 *
 *     gym tomorrow 7am every weekday ~45m !high #health
 *     → task · tomorrow 07:00 · repeats weekdays · 45 min · high · #health
 *
 *     buy audio interface $149 !high
 *     → wishlist item, priced
 *
 *     note: the E-shape run works better starting on the 5th string
 *     → journal note
 *
 * Everything is parsed deterministically from the text — nothing is guessed at
 * or inferred from previous behaviour, so what the preview says is what gets
 * saved. Anything the parser doesn't recognise stays in the title rather than
 * being silently dropped, which is the failure mode that makes this kind of
 * feature untrustworthy.
 */

enum class CaptureKind { TASK, NOTE, JOURNAL, GOAL, WISH, HABIT, PRAYER }

/** One thing the parser understood, shown as a chip under the input. */
data class CaptureChip(
    /** "date" | "time" | "repeat" | "priority" | "tag" | "estimate" | "price" | "reminder" | "kind" */
    val kind: String,
    val label: String
)

/** The parsed result. [chips] exists so the UI can show its work before saving. */
data class Capture(
    val kind: CaptureKind = CaptureKind.TASK,
    val title: String = "",
    val notes: String = "",
    val dueEpochDay: Long? = null,
    val timeMinutes: Int? = null,
    val priority: Priority = Priority.NORMAL,
    val tags: List<String> = emptyList(),
    val recurrence: String = "",
    val estimateMinutes: Int? = null,
    val reminderMinutesBefore: Int? = null,
    val price: Double = 0.0,
    val chips: List<CaptureChip> = emptyList()
) {
    val isEmpty: Boolean get() = title.isBlank()
}

object Nlp {

    private val WEEKDAYS: Map<String, Int> = mapOf(
        "monday" to 0, "mon" to 0,
        "tuesday" to 1, "tue" to 1, "tues" to 1,
        "wednesday" to 2, "wed" to 2, "weds" to 2,
        "thursday" to 3, "thu" to 3, "thur" to 3, "thurs" to 3,
        "friday" to 4, "fri" to 4,
        "saturday" to 5, "sat" to 5,
        "sunday" to 6, "sun" to 6
    )

    private val MONTHS: Map<String, Int> = mapOf(
        "jan" to 1, "january" to 1, "feb" to 2, "february" to 2, "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4, "may" to 5, "jun" to 6, "june" to 6,
        "jul" to 7, "july" to 7, "aug" to 8, "august" to 8, "sep" to 9, "sept" to 9,
        "september" to 9, "oct" to 10, "october" to 10, "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12
    )

    /** Words that mean nothing on their own once their operand has been consumed. */
    private val FILLER = setOf("at", "on", "by", "in", "for", "the", "a", "an", "to", "every", "next", "this")

    private val KIND_PREFIXES: List<Pair<String, CaptureKind>> = listOf(
        "task:" to CaptureKind.TASK,
        "todo:" to CaptureKind.TASK,
        "note:" to CaptureKind.NOTE,
        "idea:" to CaptureKind.NOTE,
        "journal:" to CaptureKind.JOURNAL,
        "log:" to CaptureKind.JOURNAL,
        "goal:" to CaptureKind.GOAL,
        "buy:" to CaptureKind.WISH,
        "wish:" to CaptureKind.WISH,
        "want:" to CaptureKind.WISH,
        "habit:" to CaptureKind.HABIT,
        "track:" to CaptureKind.HABIT,
        "pray:" to CaptureKind.PRAYER,
        "prayer:" to CaptureKind.PRAYER
    )

    // ------------------------------------------------------------------ entry

    fun parse(
        raw: String,
        today: Long = T.today(),
        defaultKind: CaptureKind = CaptureKind.TASK,
        smart: Boolean = true,
        defaultReminderLead: Int = 30
    ): Capture {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Capture(kind = defaultKind)

        // ---- kind ---------------------------------------------------------
        var body = trimmed
        var kind = defaultKind
        var kindExplicit = false
        val lowered = trimmed.lowercase()
        for ((prefix, k) in KIND_PREFIXES) {
            if (lowered.startsWith(prefix)) {
                kind = k
                kindExplicit = true
                body = trimmed.substring(prefix.length).trim()
                break
            }
        }
        if (!kindExplicit && lowered.startsWith("buy ")) {
            kind = CaptureKind.WISH
            body = trimmed.substring(4).trim()
            kindExplicit = true
        }

        if (!smart) {
            return Capture(kind = kind, title = clean(body), chips = listOf(CaptureChip("kind", kindLabel(kind))))
        }

        // ---- tokenise -----------------------------------------------------
        val words = body.split(" ", "\t", "\n").filter { it.isNotBlank() }
        val used = BooleanArray(words.size)
        val low = words.map { strip(it.lowercase()) }

        val chips = mutableListOf<CaptureChip>()
        if (kindExplicit) chips += CaptureChip("kind", kindLabel(kind))

        val tags = mutableListOf<String>()
        var priority = Priority.NORMAL
        var estimate: Int? = null
        var price = 0.0
        var recurrence = ""
        var dueDay: Long? = null
        var timeMinutes: Int? = null
        var reminder: Int? = null

        fun take(vararg idx: Int) = idx.forEach { if (it in used.indices) used[it] = true }
        fun free(i: Int): Boolean = i in used.indices && !used[i]
        fun at(i: Int): String = if (i in low.indices) low[i] else ""

        // ---- #tags and !priority (single tokens, safest first) ------------
        for (i in words.indices) {
            val w = words[i]
            if (w.length > 1 && w.startsWith("#")) {
                tags += strip(w.substring(1)).lowercase()
                take(i)
            }
        }
        for (i in low.indices) {
            if (!free(i)) continue
            val p = when (low[i]) {
                "!urgent", "!!!", "!u" -> Priority.URGENT
                "!high", "!!", "!h" -> Priority.HIGH
                "!normal", "!n" -> Priority.NORMAL
                "!low", "!l" -> Priority.LOW
                "urgent", "asap" -> Priority.URGENT
                else -> null
            }
            if (p != null) {
                priority = p
                take(i)
            }
        }

        // ---- money --------------------------------------------------------
        for (i in low.indices) {
            if (!free(i)) continue
            val amount = parseMoney(low[i])
            if (amount != null) {
                price = amount
                take(i)
            }
        }

        // ---- estimates ("~45m", "30min", "for 2 hours") --------------------
        for (i in low.indices) {
            if (!free(i)) continue
            val token = low[i].removePrefix("~")
            val direct = parseDuration(token)
            if (direct != null && (low[i].startsWith("~") || isDurationWord(token))) {
                estimate = direct
                take(i)
                continue
            }
            // "for 45 minutes" / "45 minutes"
            val n = token.toIntOrNull()
            if (n != null && free(i + 1) && isDurationUnit(at(i + 1))) {
                estimate = n * unitMinutes(at(i + 1))
                take(i, i + 1)
                if (i > 0 && at(i - 1) == "for" && free(i - 1)) take(i - 1)
            }
        }

        // ---- recurrence ----------------------------------------------------
        for (i in low.indices) {
            if (!free(i)) continue
            when (low[i]) {
                "daily", "everyday" -> { recurrence = "DAILY"; take(i) }
                "weekly" -> { recurrence = "WEEKLY"; take(i) }
                "fortnightly", "biweekly" -> { recurrence = "FORTNIGHTLY"; take(i) }
                "monthly" -> { recurrence = "MONTHLY"; take(i) }
                "weekdays" -> { recurrence = "WEEKDAYS"; take(i) }
                "every" -> {
                    val next = at(i + 1)
                    when {
                        next == "day" -> { recurrence = "DAILY"; take(i, i + 1) }
                        next == "weekday" || next == "weekdays" -> { recurrence = "WEEKDAYS"; take(i, i + 1) }
                        next == "week" -> { recurrence = "WEEKLY"; take(i, i + 1) }
                        next == "month" -> { recurrence = "MONTHLY"; take(i, i + 1) }
                        next == "morning" -> { recurrence = "DAILY"; timeMinutes = 8 * 60; take(i, i + 1) }
                        next == "evening" || next == "night" -> { recurrence = "DAILY"; timeMinutes = 20 * 60; take(i, i + 1) }
                        next == "other" && at(i + 2) == "week" -> { recurrence = "FORTNIGHTLY"; take(i, i + 1, i + 2) }
                        next == "2" && (at(i + 2) == "weeks") -> { recurrence = "FORTNIGHTLY"; take(i, i + 1, i + 2) }
                        WEEKDAYS.containsKey(next) -> {
                            recurrence = "WEEKLY"
                            dueDay = nextWeekday(today, WEEKDAYS.getValue(next), includeToday = true)
                            take(i, i + 1)
                        }
                        else -> { /* leave "every" in the title if it leads nowhere */ }
                    }
                }
            }
        }

        // ---- dates ---------------------------------------------------------
        if (dueDay == null) {
            outer@ for (i in low.indices) {
                if (!free(i)) continue
                val w = low[i]
                when {
                    w == "today" -> { dueDay = today; take(i); break@outer }
                    w == "tonight" -> {
                        dueDay = today
                        if (timeMinutes == null) timeMinutes = 20 * 60
                        take(i); break@outer
                    }
                    w == "tomorrow" || w == "tmr" || w == "tmrw" -> { dueDay = today + 1; take(i); break@outer }
                    w == "yesterday" -> { dueDay = today - 1; take(i); break@outer }
                    w == "next" && at(i + 1) == "week" -> { dueDay = today + 7; take(i, i + 1); break@outer }
                    w == "next" && at(i + 1) == "month" -> { dueDay = today + 28; take(i, i + 1); break@outer }
                    w == "next" && WEEKDAYS.containsKey(at(i + 1)) -> {
                        dueDay = nextWeekday(today, WEEKDAYS.getValue(at(i + 1)), includeToday = false)
                        take(i, i + 1); break@outer
                    }
                    w == "this" && at(i + 1) == "weekend" -> {
                        dueDay = nextWeekday(today, 5, includeToday = true)
                        take(i, i + 1); break@outer
                    }
                    w == "weekend" -> { dueDay = nextWeekday(today, 5, includeToday = true); take(i); break@outer }
                    w == "in" && at(i + 1).toIntOrNull() != null -> {
                        val n = at(i + 1).toInt()
                        val unit = at(i + 2)
                        val days = when (unit) {
                            "day", "days" -> n.toLong()
                            "week", "weeks" -> n * 7L
                            "month", "months" -> n * 28L
                            else -> null
                        }
                        if (days != null) { dueDay = today + days; take(i, i + 1, i + 2); break@outer }
                    }
                    WEEKDAYS.containsKey(w) -> {
                        dueDay = nextWeekday(today, WEEKDAYS.getValue(w), includeToday = true)
                        take(i); break@outer
                    }
                    MONTHS.containsKey(w) && ordinal(at(i + 1)) != null -> {
                        // "sep 12" / "september 12th"
                        dueDay = calendarDay(today, MONTHS.getValue(w), ordinal(at(i + 1))!!)
                        take(i, i + 1); break@outer
                    }
                    ordinal(w) != null && MONTHS.containsKey(at(i + 1)) -> {
                        // "12 sep" / "12th september"
                        dueDay = calendarDay(today, MONTHS.getValue(at(i + 1)), ordinal(w)!!)
                        take(i, i + 1); break@outer
                    }
                    else -> {
                        val slash = parseSlashDate(w, today)
                        if (slash != null) { dueDay = slash; take(i); break@outer }
                    }
                }
            }
        }

        // ---- times ----------------------------------------------------------
        if (timeMinutes == null) {
            for (i in low.indices) {
                if (!free(i)) continue
                val w = low[i]
                if (w == "at" && free(i + 1)) {
                    val t = parseClock(at(i + 1))
                    if (t != null) { timeMinutes = t; take(i, i + 1); break }
                    // "at 7 pm" with a space
                    val bare = at(i + 1).toIntOrNull()
                    val suffix = at(i + 2)
                    if (bare != null && (suffix == "pm" || suffix == "am")) {
                        timeMinutes = hourWithMeridiem(bare, 0, suffix)
                        take(i, i + 1, i + 2); break
                    }
                    continue
                }
                val named = when (w) {
                    "noon", "midday" -> 12 * 60
                    "midnight" -> 0
                    "morning" -> 8 * 60
                    "afternoon" -> 14 * 60
                    "evening" -> 19 * 60
                    else -> null
                }
                if (named != null) { timeMinutes = named; take(i); break }
                val t = parseClock(w)
                if (t != null) { timeMinutes = t; take(i); break }
                val bare = w.toIntOrNull()
                if (bare != null && (at(i + 1) == "pm" || at(i + 1) == "am")) {
                    timeMinutes = hourWithMeridiem(bare, 0, at(i + 1))
                    take(i, i + 1); break
                }
            }
        }

        // ---- reminder ---------------------------------------------------------
        for (i in low.indices) {
            if (!free(i)) continue
            if (low[i] == "remind" || low[i] == "reminder") {
                reminder = defaultReminderLead
                take(i)
                if (at(i + 1) == "me" && free(i + 1)) take(i + 1)
            }
        }
        // A time with no explicit reminder still deserves one; that is what
        // people mean by "at 7am" far more often than a silent due stamp.
        if (reminder == null && timeMinutes != null && kind == CaptureKind.TASK) {
            reminder = 0
        }

        // ---- title -------------------------------------------------------------
        val kept = words.filterIndexed { i, _ -> !used[i] }
        var title = clean(kept.joinToString(" "))
        if (title.isBlank()) title = clean(body)

        // A repeating rule needs something to roll forward from.
        if (recurrence.isNotEmpty() && dueDay == null) dueDay = today

        // ---- chips ---------------------------------------------------------------
        dueDay?.let { chips += CaptureChip("date", dateChip(it, today)) }
        timeMinutes?.let { chips += CaptureChip("time", T.timeLabel(it * 60_000L)) }
        if (recurrence.isNotEmpty()) chips += CaptureChip("repeat", recurrenceChip(recurrence))
        if (priority != Priority.NORMAL) {
            chips += CaptureChip("priority", priority.name.lowercase().replaceFirstChar { it.uppercase() })
        }
        estimate?.let { chips += CaptureChip("estimate", durationLabel(it)) }
        if (price > 0.0) chips += CaptureChip("price", trimPrice(price))
        reminder?.let {
            chips += CaptureChip("reminder", if (it == 0) "Alert at time" else "Alert ${durationLabel(it)} before")
        }
        tags.forEach { chips += CaptureChip("tag", "#$it") }

        return Capture(
            kind = kind,
            title = title,
            dueEpochDay = dueDay,
            timeMinutes = timeMinutes,
            priority = priority,
            tags = tags.distinct(),
            recurrence = recurrence,
            estimateMinutes = estimate,
            reminderMinutesBefore = if (kind == CaptureKind.TASK) reminder else null,
            price = price,
            chips = chips
        )
    }

    // ------------------------------------------------------------------ helpers

    fun kindLabel(kind: CaptureKind): String = when (kind) {
        CaptureKind.TASK -> "Task"
        CaptureKind.NOTE -> "Note"
        CaptureKind.JOURNAL -> "Journal"
        CaptureKind.GOAL -> "Goal"
        CaptureKind.WISH -> "Wishlist"
        CaptureKind.HABIT -> "Habit"
        CaptureKind.PRAYER -> "Prayer"
    }

    /** One-line explanation of what will be saved, shown above the Save button. */
    fun summary(capture: Capture, today: Long = T.today()): String {
        if (capture.isEmpty) return "Type anything — dates, times and repeats are picked up automatically."
        val parts = mutableListOf(kindLabel(capture.kind))
        capture.dueEpochDay?.let { day ->
            val time = capture.timeMinutes?.let { " at " + T.timeLabel(it * 60_000L) } ?: ""
            parts += dateChip(day, today) + time
        }
        if (capture.recurrence.isNotEmpty()) parts += recurrenceChip(capture.recurrence).lowercase()
        capture.estimateMinutes?.let { parts += durationLabel(it) }
        return parts.joinToString(" · ")
    }

    private fun clean(text: String): String {
        var words = text.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
        while (words.isNotEmpty() && FILLER.contains(words.last().lowercase())) words = words.dropLast(1)
        while (words.isNotEmpty() && FILLER.contains(words.first().lowercase())) words = words.drop(1)
        val joined = words.joinToString(" ").trim().trim(',', '-', '·')
        return joined.replaceFirstChar { if (it.isLowerCase()) it.uppercaseChar() else it }
    }

    /**
     * Strips surrounding sentence punctuation without touching "7:30", "12/09"
     * or the leading marks that carry meaning. Trimming naively also ate the
     * "!" off "!high" and turned "!!!" into an empty token, which quietly
     * disabled the entire priority grammar.
     */
    private fun strip(word: String): String {
        val front = word.trimStart('"', '\'', '(')
        return if (front.startsWith("!")) {
            front.trimEnd('.', ',', ';', '?', '"', '\'', ')')
        } else {
            front.trimEnd('.', ',', ';', '!', '?', '"', '\'', ')')
        }
    }

    private fun ordinal(word: String): Int? {
        val digits = word.takeWhile { it.isDigit() }
        if (digits.isEmpty()) return null
        val rest = word.drop(digits.length)
        if (rest.isNotEmpty() && rest !in setOf("st", "nd", "rd", "th")) return null
        return digits.toIntOrNull()?.takeIf { it in 1..31 }
    }

    private fun isDurationUnit(word: String): Boolean = unitMinutes(word) > 0

    private fun unitMinutes(word: String): Int = when (word) {
        "m", "min", "mins", "minute", "minutes" -> 1
        "h", "hr", "hrs", "hour", "hours" -> 60
        else -> 0
    }

    private fun isDurationWord(token: String): Boolean {
        val digits = token.takeWhile { it.isDigit() }
        if (digits.isEmpty()) return false
        return unitMinutes(token.drop(digits.length)) > 0
    }

    private fun parseDuration(token: String): Int? {
        val digits = token.takeWhile { it.isDigit() }
        if (digits.isEmpty()) return null
        val unit = unitMinutes(token.drop(digits.length))
        if (unit == 0) return null
        val n = digits.toIntOrNull() ?: return null
        return (n * unit).coerceIn(1, 24 * 60)
    }

    private fun parseMoney(token: String): Double? {
        val symbols = listOf("$", "£", "€", "r", "zwl")
        val lower = token.lowercase()
        val symbol = symbols.firstOrNull { lower.startsWith(it) } ?: return null
        val rest = lower.substring(symbol.length).replace(",", "")
        if (rest.isEmpty()) return null
        return rest.toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    /** "7pm", "7:30am", "19:00", "0730" → minutes since midnight. */
    private fun parseClock(token: String): Int? {
        var text = token
        var meridiem = ""
        if (text.endsWith("pm") || text.endsWith("am")) {
            meridiem = text.takeLast(2)
            text = text.dropLast(2)
        }
        if (text.isEmpty()) return null
        val parts = text.split(":", ".")
        val hour: Int
        val minute: Int
        when (parts.size) {
            1 -> {
                val n = parts[0].toIntOrNull() ?: return null
                if (meridiem.isEmpty()) {
                    // Bare digits are only a time when written as 24-hour clock,
                    // otherwise "3 pages" would silently become 03:00.
                    if (parts[0].length != 4) return null
                    hour = n / 100
                    minute = n % 100
                } else {
                    hour = n
                    minute = 0
                }
            }
            2 -> {
                hour = parts[0].toIntOrNull() ?: return null
                minute = parts[1].toIntOrNull() ?: return null
            }
            else -> return null
        }
        if (minute !in 0..59) return null
        return if (meridiem.isEmpty()) {
            if (hour !in 0..23) null else hour * 60 + minute
        } else {
            hourWithMeridiem(hour, minute, meridiem)
        }
    }

    private fun hourWithMeridiem(hourIn: Int, minute: Int, meridiem: String): Int {
        if (hourIn !in 1..12) return (hourIn.coerceIn(0, 23)) * 60 + minute
        val h = when {
            meridiem == "pm" && hourIn < 12 -> hourIn + 12
            meridiem == "am" && hourIn == 12 -> 0
            else -> hourIn
        }
        return h * 60 + minute
    }

    /** "12/09" or "12/09/2026", day-first (the format used everywhere but the US). */
    private fun parseSlashDate(token: String, today: Long): Long? {
        val sep = when {
            token.contains("/") -> "/"
            token.count { it == '-' } == 2 -> "-"
            else -> return null
        }
        val parts = token.split(sep)
        if (parts.size !in 2..3) return null
        val d = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (d !in 1..31 || m !in 1..12) return null
        val year = if (parts.size == 3) {
            val y = parts[2].toIntOrNull() ?: return null
            if (y < 100) 2000 + y else y
        } else {
            null
        }
        return if (year != null) {
            T.daysFromCivil(year.toLong(), m.toLong(), d.toLong())
        } else {
            calendarDay(today, m, d)
        }
    }

    /** Resolves a bare day+month to the next occurrence, this year or next. */
    private fun calendarDay(today: Long, month: Int, day: Int): Long {
        val (year, _, _) = T.civilFromDays(today)
        val thisYear = T.daysFromCivil(year, month.toLong(), day.toLong())
        return if (thisYear >= today) thisYear else T.daysFromCivil(year + 1, month.toLong(), day.toLong())
    }

    fun nextWeekday(today: Long, targetDow: Int, includeToday: Boolean): Long {
        val current = T.dowIndex(today)
        var delta = ((targetDow - current) % 7 + 7) % 7
        if (delta == 0 && !includeToday) delta = 7
        return today + delta
    }

    private fun dateChip(day: Long, today: Long): String = when (day - today) {
        0L -> "Today"
        1L -> "Tomorrow"
        -1L -> "Yesterday"
        in 2L..6L -> T.dayNameFullOf(day)
        else -> T.dateLabel(day)
    }

    private fun recurrenceChip(rule: String): String = when (rule) {
        "DAILY" -> "Every day"
        "WEEKDAYS" -> "Weekdays"
        "WEEKLY" -> "Every week"
        "FORTNIGHTLY" -> "Every 2 weeks"
        "MONTHLY" -> "Every month"
        else -> rule
    }

    fun durationLabel(minutes: Int): String = when {
        minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60}h"
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
        else -> "${minutes}m"
    }

    private fun trimPrice(value: Double): String {
        val rounded = (value * 100).toLong()
        return if (rounded % 100 == 0L) (rounded / 100).toString() else value.toString()
    }
}

/**
 * Writes a parsed capture into whichever table it belongs in.
 *
 * Lives as an extension so [Store] doesn't need to know the capture grammar
 * exists — the store stays a persistence layer, and the parser stays testable
 * on its own.
 */
fun Store.commitCapture(capture: Capture): String {
    if (capture.isEmpty) return "Nothing to save"
    val now = T.now()
    return when (capture.kind) {
        CaptureKind.TASK -> {
            addTodo(
                TodoItem(
                    title = capture.title,
                    priority = capture.priority,
                    dueEpochDay = capture.dueEpochDay,
                    dueTimeMinutes = capture.timeMinutes,
                    reminderMinutesBefore = capture.reminderMinutesBefore,
                    recurrence = capture.recurrence,
                    estimateMinutes = capture.estimateMinutes,
                    tags = capture.tags,
                    createdAt = now
                )
            )
            "Task added"
        }
        CaptureKind.NOTE -> {
            addJournal(
                JournalEntry(
                    title = capture.title.take(60),
                    body = capture.title,
                    mood = 3,
                    tags = (capture.tags + "note").distinct(),
                    createdAt = now
                )
            )
            "Note saved"
        }
        CaptureKind.JOURNAL -> {
            addJournal(
                JournalEntry(
                    title = capture.title.take(60),
                    body = capture.title,
                    mood = 3,
                    tags = capture.tags,
                    createdAt = now
                )
            )
            "Journal entry saved"
        }
        CaptureKind.GOAL -> {
            addGoal(
                GoalItem(
                    title = capture.title,
                    horizon = Horizon.MEDIUM,
                    category = capture.tags.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "General",
                    targetEpochDay = capture.dueEpochDay,
                    createdAt = now
                )
            )
            "Goal added"
        }
        CaptureKind.WISH -> {
            addWish(
                WishItem(
                    name = capture.title,
                    category = capture.tags.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "General",
                    price = capture.price,
                    priority = capture.priority,
                    createdAt = now
                )
            )
            "Added to wishlist"
        }
        CaptureKind.HABIT -> {
            addHabit(
                Habit(
                    name = capture.title,
                    cadence = when (capture.recurrence) {
                        "WEEKDAYS" -> "WEEKDAYS"
                        "WEEKLY" -> "WEEKLY_COUNT"
                        else -> "DAILY"
                    },
                    reminderOn = capture.timeMinutes != null,
                    reminderHour = (capture.timeMinutes ?: (8 * 60)) / 60,
                    sortOrder = activeHabits().size,
                    createdAt = now
                )
            )
            "Habit added"
        }
        CaptureKind.PRAYER -> {
            addPrayer(
                PrayerPoint(
                    title = capture.title,
                    category = capture.tags.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "General",
                    createdAt = now
                )
            )
            "Prayer point added"
        }
    }
}
