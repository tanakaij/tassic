package tassic.data

import kotlin.math.abs
import kotlin.math.roundToInt

/** How loudly an insight should present itself. */
enum class Severity { POSITIVE, INFO, WARNING, CRITICAL }

/**
 * One derived observation about the user's data. Everything here is computed
 * from rows the user actually created — no invented numbers, and every insight
 * carries the evidence that produced it in [detail].
 */
data class Insight(
    val id: String,
    val severity: Severity,
    val domain: String,
    val title: String,
    val detail: String,
    val actionLabel: String? = null,
    /** Tab enum *name* (e.g. "TASKS"); resolved by the UI layer. */
    val actionTab: String? = null,
    /** Higher sorts first within a severity band. */
    val weight: Int = 0
)

/** A single ranked "do this next" suggestion for the Today brief. */
data class NextAction(
    val title: String,
    val reason: String,
    val domain: String,
    val actionTab: String,
    val weight: Int,
    val estimateMinutes: Int = 0
)

/** Per-area health, 0..100, with the change against the previous 7 days. */
data class DomainScore(
    val domain: String,
    val score: Int,
    val delta: Int,
    val caption: String
)

/** Everything the Insights tab and the Today brief render from. */
data class Report(
    val today: Long,
    val greeting: String,
    val headline: String,
    val momentum: Int,
    val momentumDelta: Int,
    val activeStreak: Int,
    val bestActiveStreak: Int,
    val domains: List<DomainScore>,
    /** Normalised 0..1 activity for the last 28 days, oldest first. */
    val spark: List<Float>,
    /** Raw event counts for the last 28 days, oldest first (heatmap source). */
    val heat: List<Int>,
    val insights: List<Insight>,
    val nextActions: List<NextAction>,
    val loadMinutes: Int,
    val dueToday: Int,
    val doneToday: Int
)

/**
 * The analytics layer.
 *
 * Reads only from [Store] and [T]; holds no state of its own so it can be
 * recomputed freely on recomposition. Every public entry point is deterministic
 * for a given (store, today) pair, which keeps the UI stable across frames.
 */
object Insights {

    private const val WINDOW = 7
    private const val HEAT_DAYS = 28

    // ------------------------------------------------------------------ main

    fun report(store: Store, today: Long = T.today()): Report {
        val logs = store.activity.items.value
        val hour = T.localHour()

        val heat = (0 until HEAT_DAYS).map { back ->
            val day = today - (HEAT_DAYS - 1 - back)
            logs.count { it.epochDay == day && it.event != "UNDO" }
        }
        val peak = (heat.maxOrNull() ?: 0).coerceAtLeast(1)
        val spark = heat.map { it.toFloat() / peak.toFloat() }

        val domains = domainScores(store, today)
        val momentum = if (domains.isEmpty()) 0 else {
            domains.sumOf { it.score }.toDouble().div(domains.size).roundToInt()
        }
        val momentumDelta = if (domains.isEmpty()) 0 else {
            domains.sumOf { it.delta }.toDouble().div(domains.size).roundToInt()
        }

        val streaks = activeStreak(logs, today)
        val list = buildInsights(store, today, hour, domains, momentumDelta)
        val actions = nextActions(store, today, hour)

        val dueToday = countDueToday(store, today)
        val doneToday = logs.count { it.epochDay == today && it.event == "COMPLETE" }
        val load = store.todos.items.value
            .filter { !it.done && it.dueEpochDay == today }
            .sumOf { it.estimateMinutes ?: 10 }

        return Report(
            today = today,
            greeting = greeting(hour),
            headline = headline(momentum, momentumDelta, dueToday, doneToday, streaks.first),
            momentum = momentum,
            momentumDelta = momentumDelta,
            activeStreak = streaks.first,
            bestActiveStreak = streaks.second,
            domains = domains,
            spark = spark,
            heat = heat,
            insights = list,
            nextActions = actions,
            loadMinutes = load,
            dueToday = dueToday,
            doneToday = doneToday
        )
    }

    // -------------------------------------------------------------- greeting

    fun greeting(hour: Int): String = when {
        hour < 5 -> "Still up"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        hour < 22 -> "Good evening"
        else -> "Winding down"
    }

    private fun headline(
        momentum: Int,
        delta: Int,
        due: Int,
        done: Int,
        streak: Int
    ): String = when {
        due == 0 && done > 0 -> "Everything scheduled is done. $done logged today."
        due == 0 -> "Nothing scheduled today — a clean slate."
        done == 0 && due == 1 -> "One thing on the board today."
        done == 0 -> "$due things on the board today."
        done >= due -> "$done done, you're through today's list."
        streak >= 3 && delta >= 0 -> "$done of $due done · $streak-day streak alive."
        momentum >= 70 -> "$done of $due done · momentum is holding."
        delta < -10 -> "$done of $due done · this week is lighter than last."
        else -> "$done of $due done today."
    }

    // ------------------------------------------------------------ domain math

    /**
     * Scores each area 0..100 over the trailing 7 days and compares it with the
     * 7 days before that. Scoring is intentionally simple and explainable — a
     * ratio of what happened to what was scheduled — so the number can always
     * be justified to the user in [DomainScore.caption].
     */
    private fun domainScores(store: Store, today: Long): List<DomainScore> {
        val logs = store.activity.items.value
        val settings = store.settings()

        fun window(domain: String, from: Long, to: Long): Int =
            logs.count { it.domain == domain && it.event == "COMPLETE" && it.epochDay in from..to }

        val curFrom = today - (WINDOW - 1)
        val prevFrom = today - (WINDOW * 2 - 1)
        val prevTo = today - WINDOW

        val out = mutableListOf<DomainScore>()

        // Practice — target is one session per day.
        run {
            val cur = daysWithActivity(logs, "practice", curFrom, today)
            val prev = daysWithActivity(logs, "practice", prevFrom, prevTo)
            out += DomainScore(
                "Practice",
                pct(cur, WINDOW),
                pct(cur, WINDOW) - pct(prev, WINDOW),
                "$cur of 7 days practised"
            )
        }

        // Fitness — target from settings (sessions per week).
        run {
            val target = settings.workoutsPerWeekTarget.coerceAtLeast(1)
            val cur = daysWithActivity(logs, "fitness", curFrom, today)
            val prev = daysWithActivity(logs, "fitness", prevFrom, prevTo)
            out += DomainScore(
                "Fitness",
                pct(cur, target),
                pct(cur, target) - pct(prev, target),
                "$cur of $target sessions"
            )
        }

        // Tasks — completion rate against everything that came due.
        run {
            val todos = store.todos.items.value
            val closedCur = todos.count { t ->
                val at = t.completedAt ?: return@count false
                T.dayOf(at) in curFrom..today
            }
            val closedPrev = todos.count { t ->
                val at = t.completedAt ?: return@count false
                T.dayOf(at) in prevFrom..prevTo
            }
            val openOverdue = todos.count { !it.done && (it.dueEpochDay ?: Long.MAX_VALUE) < today }
            val base = (closedCur + openOverdue).coerceAtLeast(1)
            val score = pct(closedCur, base)
            val prevBase = (closedPrev + openOverdue).coerceAtLeast(1)
            out += DomainScore(
                "Tasks",
                score,
                score - pct(closedPrev, prevBase),
                if (openOverdue == 0) "$closedCur closed, nothing overdue"
                else "$closedCur closed, $openOverdue overdue"
            )
        }

        // Faith — routines completed against routines that came due.
        run {
            val routines = store.routines.items.value
            if (routines.isNotEmpty()) {
                val cur = window("faith", curFrom, today)
                val prev = window("faith", prevFrom, prevTo)
                val expected = expectedRoutineHits(routines).coerceAtLeast(1)
                out += DomainScore(
                    "Faith",
                    pct(cur, expected),
                    pct(cur, expected) - pct(prev, expected),
                    "$cur of ~$expected rhythms kept"
                )
            }
        }

        // Recovery — clean days out of the window, per active habit.
        run {
            val habits = store.recovery.items.value.filter { it.active }
            if (habits.isNotEmpty()) {
                val resetsCur = store.habitLogs.items.value.count {
                    it.event == "RELAPSE" && T.dayOf(it.loggedAt) in curFrom..today
                }
                val resetsPrev = store.habitLogs.items.value.count {
                    it.event == "RELAPSE" && T.dayOf(it.loggedAt) in prevFrom..prevTo
                }
                val score = pct((WINDOW - resetsCur).coerceAtLeast(0), WINDOW)
                val prevScore = pct((WINDOW - resetsPrev).coerceAtLeast(0), WINDOW)
                out += DomainScore(
                    "Recovery",
                    score,
                    score - prevScore,
                    if (resetsCur == 0) "clean all 7 days" else "$resetsCur reset${plural(resetsCur)} this week"
                )
            }
        }

        return out
    }

    private fun daysWithActivity(logs: List<ActivityLog>, domain: String, from: Long, to: Long): Int =
        logs.filter { it.domain == domain && it.event == "COMPLETE" && it.epochDay in from..to }
            .map { it.epochDay }
            .distinct()
            .size

    private fun expectedRoutineHits(routines: List<FaithRoutine>): Int =
        routines.sumOf { r ->
            when (r.cadence.lowercase()) {
                "weekly" -> 1
                "monthly" -> 0
                else -> 7
            }
        }

    private fun pct(part: Int, whole: Int): Int =
        if (whole <= 0) 0 else ((part.toDouble() / whole.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)

    private fun plural(n: Int) = if (n == 1) "" else "s"

    /** Consecutive days ending today (or yesterday) with at least one logged event. */
    private fun activeStreak(logs: List<ActivityLog>, today: Long): Pair<Int, Int> {
        val days = logs.filter { it.event == "COMPLETE" }.map { it.epochDay }.distinct().sorted()
        if (days.isEmpty()) return 0 to 0

        var best = 1
        var run = 1
        for (i in 1 until days.size) {
            if (days[i] == days[i - 1] + 1) run++ else run = 1
            if (run > best) best = run
        }

        val set = days.toSet()
        var cursor = if (set.contains(today)) today else if (set.contains(today - 1)) today - 1 else return 0 to best
        var current = 0
        while (set.contains(cursor)) {
            current++
            cursor--
        }
        return current to maxOf(best, current)
    }

    private fun countDueToday(store: Store, today: Long): Int {
        val tasks = store.todos.items.value.count { !it.done && (it.dueEpochDay ?: Long.MAX_VALUE) <= today }
        val workouts = store.workouts.items.value.count { T.tagMatches(it.dayTag, today) && it.doneEpochDay != today }
        val routines = store.routines.items.value.count { store.routineDueToday(it, today) }
        val shape = store.cagedShapeForToday()
        val subtasks = if (shape == null) 0 else store.practice.items.value
            .count { it.kind == PracticeKind.SUBTASK && it.parentId == shape.id && it.doneEpochDay != today }
        return tasks + workouts + routines + subtasks
    }

    // ------------------------------------------------------------- insights

    private fun buildInsights(
        store: Store,
        today: Long,
        hour: Int,
        domains: List<DomainScore>,
        momentumDelta: Int
    ): List<Insight> {
        val out = mutableListOf<Insight>()
        val settings = store.settings()
        val logs = store.activity.items.value

        notificationHealth(store, out)
        taskInsights(store, today, out)
        fitnessInsights(store, today, hour, out)
        recoveryInsights(store, today, out)
        goalInsights(store, today, out)
        practiceInsights(store, today, settings, out)
        faithInsights(store, today, out)
        journalInsights(store, today, out)
        wishlistInsights(store, settings, out)
        rhythmInsights(logs, out)
        momentumInsight(domains, momentumDelta, out)

        return out.sortedWith(
            compareBy(
                { severityRank(it.severity) },
                { -it.weight }
            )
        )
    }

    private fun severityRank(s: Severity): Int = when (s) {
        Severity.CRITICAL -> 0
        Severity.WARNING -> 1
        Severity.INFO -> 2
        Severity.POSITIVE -> 3
    }

    // -- notifications -------------------------------------------------------

    private fun notificationHealth(store: Store, out: MutableList<Insight>) {
        val scheduled = store.todos.items.value.count {
            !it.done && it.reminderMinutesBefore != null && it.dueEpochDay != null
        }
        val routineReminders = store.routines.items.value.count { it.reminderOn }
        val total = scheduled + routineReminders
        if (total == 0) return

        val perm = store.metaGet("notify.permission") ?: "default"
        if (perm == "granted") return

        out += Insight(
            id = "notify.permission",
            severity = Severity.CRITICAL,
            domain = "Reminders",
            title = "$total reminder${plural(total)} can't reach you",
            detail = if (perm == "denied") {
                "Notifications are blocked for this app in your browser settings. " +
                    "Until that's reversed these reminders only appear while Tassic is open."
            } else {
                "Notification permission hasn't been granted yet, so nothing will alert you " +
                    "when the app is closed."
            },
            actionLabel = "Fix reminders",
            actionTab = "SETTINGS",
            weight = 100
        )
    }

    // -- tasks ---------------------------------------------------------------

    private fun taskInsights(store: Store, today: Long, out: MutableList<Insight>) {
        val todos = store.todos.items.value
        val open = todos.filter { !it.done }
        val overdue = open.filter { (it.dueEpochDay ?: Long.MAX_VALUE) < today }

        if (overdue.isNotEmpty()) {
            val oldest = overdue.minByOrNull { it.dueEpochDay ?: Long.MAX_VALUE }
            val age = oldest?.dueEpochDay?.let { (today - it).toInt() } ?: 0
            out += Insight(
                id = "tasks.overdue",
                severity = if (overdue.size >= 5 || age >= 7) Severity.WARNING else Severity.INFO,
                domain = "Tasks",
                title = "${overdue.size} task${plural(overdue.size)} past due",
                detail = if (oldest != null) {
                    "Oldest is \"${oldest.title}\", ${age} day${plural(age)} late. " +
                        "Reschedule what's no longer real so the list stays trustworthy."
                } else "Reschedule or close them out.",
                actionLabel = "Review tasks",
                actionTab = "TODAY",
                weight = 90
            )
        }

        // Backlog inflow vs outflow — is the list growing faster than it shrinks?
        val weekAgo = today - 6
        val created = todos.count { T.dayOf(it.createdAt) in weekAgo..today }
        val closed = todos.count { t ->
            val at = t.completedAt ?: return@count false
            T.dayOf(at) in weekAgo..today
        }
        if (created >= 3 && created - closed >= 3) {
            out += Insight(
                id = "tasks.backlog",
                severity = Severity.WARNING,
                domain = "Tasks",
                title = "Backlog grew by ${created - closed} this week",
                detail = "You added $created and closed $closed. Either the estimates are " +
                    "optimistic or something needs to come off the list entirely.",
                weight = 60
            )
        } else if (closed > created && closed >= 3) {
            out += Insight(
                id = "tasks.burndown",
                severity = Severity.POSITIVE,
                domain = "Tasks",
                title = "Closed more than you opened",
                detail = "$closed done against $created added this week — the backlog is shrinking.",
                weight = 40
            )
        }

        // Untriaged: open, no due date, older than a fortnight.
        val stale = open.count { it.dueEpochDay == null && today - T.dayOf(it.createdAt) > 14 }
        if (stale >= 3) {
            out += Insight(
                id = "tasks.stale",
                severity = Severity.INFO,
                domain = "Tasks",
                title = "$stale task${plural(stale)} have sat undated for 2+ weeks",
                detail = "Undated tasks never surface in a daily view. Give them a date or delete them.",
                weight = 30
            )
        }

        val urgent = open.count { it.priority == Priority.URGENT }
        if (urgent >= 4) {
            out += Insight(
                id = "tasks.urgent",
                severity = Severity.INFO,
                domain = "Tasks",
                title = "$urgent tasks marked urgent",
                detail = "When everything is urgent the priority field stops carrying signal. " +
                    "Consider demoting the ones that can wait a day.",
                weight = 25
            )
        }
    }

    // -- fitness -------------------------------------------------------------

    private fun fitnessInsights(store: Store, today: Long, hour: Int, out: MutableList<Insight>) {
        val streak = store.workoutStreak()
        val logs = store.workoutLogs.items.value
        val trainedToday = logs.any { T.dayOf(it.loggedAt) == today }
        val due = store.workoutsDueToday()

        if (streak >= 2 && !trainedToday && hour >= 17 && due.isNotEmpty()) {
            out += Insight(
                id = "fitness.streakrisk",
                severity = Severity.WARNING,
                domain = "Fitness",
                title = "$streak-day streak ends tonight",
                detail = "${due.size} exercise${plural(due.size)} scheduled and nothing logged yet. " +
                    "Even one set keeps the chain intact.",
                actionLabel = "Log a set",
                actionTab = "TODAY",
                weight = 85
            )
        }

        if (streak > 0 && streak in listOf(7, 14, 21, 30, 50, 75, 100, 200, 365)) {
            out += Insight(
                id = "fitness.milestone",
                severity = Severity.POSITIVE,
                domain = "Fitness",
                title = "$streak days in a row",
                detail = "That's a genuine habit now, not a push.",
                weight = 70
            )
        }

        // Volume trend across two weeks.
        val cur = logs.count { T.dayOf(it.loggedAt) in (today - 6)..today }
        val prev = logs.count { T.dayOf(it.loggedAt) in (today - 13)..(today - 7) }
        if (prev >= 3 && cur < prev && prev - cur >= 3) {
            out += Insight(
                id = "fitness.volume",
                severity = Severity.INFO,
                domain = "Fitness",
                title = "Training volume down ${prev - cur} sets",
                detail = "$cur sets this week against $prev last week. Worth checking whether " +
                    "that's a deload or a drift.",
                weight = 45
            )
        } else if (cur > prev && prev > 0 && cur - prev >= 3) {
            out += Insight(
                id = "fitness.volume.up",
                severity = Severity.POSITIVE,
                domain = "Fitness",
                title = "Training volume up ${cur - prev} sets",
                detail = "$cur sets this week against $prev last week.",
                weight = 35
            )
        }

        // Which exercise is being skipped?
        val neglected = store.workouts.items.value
            .filter { it.dayTag.uppercase() != "NONE" }
            .filter { w -> logs.none { it.name == w.name && T.dayOf(it.loggedAt) >= today - 13 } }
        if (neglected.size >= 2 && logs.isNotEmpty()) {
            out += Insight(
                id = "fitness.neglected",
                severity = Severity.INFO,
                domain = "Fitness",
                title = "${neglected.size} exercises untouched for 2 weeks",
                detail = neglected.take(3).joinToString(", ") { it.name } +
                    if (neglected.size > 3) " and ${neglected.size - 3} more." else ".",
                weight = 28
            )
        }
    }

    // -- recovery ------------------------------------------------------------

    private fun recoveryInsights(store: Store, today: Long, out: MutableList<Insight>) {
        val habits = store.recovery.items.value.filter { it.active }

        habits.forEach { h ->
            val days = store.daysClean(h, today)

            if (h.bestStreak > 0 && days == h.bestStreak) {
                out += Insight(
                    id = "recovery.tied.${h.id}",
                    severity = Severity.POSITIVE,
                    domain = "Recovery",
                    title = "${h.name}: matching your record",
                    detail = "$days days — level with your best ever. Tomorrow sets a new one.",
                    weight = 75
                )
            } else if (h.bestStreak > 0 && days == h.bestStreak - 1) {
                out += Insight(
                    id = "recovery.near.${h.id}",
                    severity = Severity.INFO,
                    domain = "Recovery",
                    title = "${h.name}: one day off your record",
                    detail = "$days days clean, best is ${h.bestStreak}.",
                    weight = 65
                )
            } else if (days > h.bestStreak && h.bestStreak > 0) {
                out += Insight(
                    id = "recovery.record.${h.id}",
                    severity = Severity.POSITIVE,
                    domain = "Recovery",
                    title = "${h.name}: new record at $days days",
                    detail = "Past your previous best of ${h.bestStreak}.",
                    weight = 72
                )
            }

            // Day-of-week clustering in reset history.
            val resets = store.relapseLogsFor(h.id)
            if (resets.size >= 3) {
                val byDow = resets.groupBy { T.dowIndex(T.dayOf(it.loggedAt)) }
                val worst = byDow.maxByOrNull { it.value.size }
                if (worst != null && worst.value.size.toDouble() / resets.size >= 0.4) {
                    out += Insight(
                        id = "recovery.pattern.${h.id}",
                        severity = Severity.WARNING,
                        domain = "Recovery",
                        title = "${h.name} resets cluster on ${T.dayNameFull(worst.key)}s",
                        detail = "${worst.value.size} of ${resets.size} resets landed on a " +
                            "${T.dayNameFull(worst.key)}. Worth planning that day differently.",
                        weight = 80
                    )
                }

                val byHour = resets.groupBy { T.hourOf(it.loggedAt) / 6 }
                val worstBlock = byHour.maxByOrNull { it.value.size }
                if (worstBlock != null && worstBlock.value.size.toDouble() / resets.size >= 0.5) {
                    out += Insight(
                        id = "recovery.window.${h.id}",
                        severity = Severity.INFO,
                        domain = "Recovery",
                        title = "${h.name}: the risky window is ${blockLabel(worstBlock.key)}",
                        detail = "${worstBlock.value.size} of ${resets.size} resets happened then.",
                        weight = 55
                    )
                }
            }
        }
    }

    private fun blockLabel(block: Int): String = when (block) {
        0 -> "midnight–06:00"
        1 -> "06:00–noon"
        2 -> "noon–18:00"
        else -> "18:00–midnight"
    }

    // -- goals ---------------------------------------------------------------

    private fun goalInsights(store: Store, today: Long, out: MutableList<Insight>) {
        val goals = store.goals.items.value

        goals.forEach { g ->
            val target = g.targetEpochDay ?: return@forEach
            val start = T.dayOf(g.createdAt)
            val total = (target - start).toInt()
            if (total <= 0) return@forEach
            val elapsed = (today - start).toInt().coerceIn(0, total)
            val expected = ((elapsed.toDouble() / total) * 100).roundToInt()
            val gap = g.progress - expected
            val left = (target - today).toInt()

            if (left < 0 && g.progress < 100) {
                out += Insight(
                    id = "goal.overdue.${g.id}",
                    severity = Severity.WARNING,
                    domain = "Goals",
                    title = "\"${g.title}\" passed its target date",
                    detail = "${abs(left)} day${plural(abs(left))} over, sitting at ${g.progress}%. " +
                        "Move the date or cut the scope.",
                    actionLabel = "Open goals",
                    actionTab = "LIFE",
                    weight = 78
                )
            } else if (gap <= -15 && left in 0..365) {
                val remaining = 100 - g.progress
                val perWeek = if (left > 0) (remaining.toDouble() / left * 7).roundToInt() else remaining
                out += Insight(
                    id = "goal.behind.${g.id}",
                    severity = Severity.WARNING,
                    domain = "Goals",
                    title = "\"${g.title}\" is ${abs(gap)}% behind pace",
                    detail = "${g.progress}% done with $left day${plural(left)} left — that needs about " +
                        "$perWeek% a week from here.",
                    actionLabel = "Open goals",
                    actionTab = "LIFE",
                    weight = 68
                )
            } else if (gap >= 15) {
                out += Insight(
                    id = "goal.ahead.${g.id}",
                    severity = Severity.POSITIVE,
                    domain = "Goals",
                    title = "\"${g.title}\" is ${gap}% ahead of pace",
                    detail = "${g.progress}% done with $left day${plural(left)} still on the clock.",
                    weight = 38
                )
            } else if (left in 0..7 && g.progress < 100) {
                out += Insight(
                    id = "goal.soon.${g.id}",
                    severity = Severity.INFO,
                    domain = "Goals",
                    title = "\"${g.title}\" is due ${T.relativeDays(target, today)}",
                    detail = "${100 - g.progress}% still to go.",
                    actionTab = "LIFE",
                    weight = 58
                )
            }
        }

        val undated = goals.count { it.targetEpochDay == null }
        if (undated >= 3) {
            out += Insight(
                id = "goals.undated",
                severity = Severity.INFO,
                domain = "Goals",
                title = "$undated goals have no target date",
                detail = "Without a date there's no pace to measure against, so these can't " +
                    "surface as behind or ahead.",
                actionTab = "LIFE",
                weight = 22
            )
        }

        val stalled = goals.filter { it.progress in 1..99 }.filter { g ->
            store.activity.items.value.none {
                it.domain == "goals" && it.refId == g.id && it.epochDay >= today - 21
            }
        }
        if (stalled.size >= 2) {
            out += Insight(
                id = "goals.stalled",
                severity = Severity.INFO,
                domain = "Goals",
                title = "${stalled.size} goals haven't moved in 3 weeks",
                detail = stalled.take(2).joinToString(", ") { "\"${it.title}\"" } +
                    if (stalled.size > 2) " and others." else ".",
                actionTab = "LIFE",
                weight = 42
            )
        }
    }

    // -- practice ------------------------------------------------------------

    private fun practiceInsights(
        store: Store,
        today: Long,
        settings: Settings,
        out: MutableList<Insight>
    ) {
        val practice = store.practice.items.value

        val keys = practice.filter { it.kind == PracticeKind.KEY }
        val untouched = keys.filter { it.doneCount == 0 }
        if (keys.isNotEmpty() && untouched.isNotEmpty()) {
            out += Insight(
                id = "music.keys",
                severity = Severity.INFO,
                domain = "Music",
                title = "${untouched.size} of ${keys.size} keys never practised",
                detail = untouched.take(4).joinToString(", ") { it.title } +
                    if (untouched.size > 4) " and more — the cycle is lopsided." else ".",
                actionLabel = "Open studio",
                actionTab = "MUSIC",
                weight = 34
            )
        }

        val songsThisWeek = store.songsLearnedThisWeek()
        val target = settings.songsPerWeekTarget
        if (target > 0) {
            val dow = T.dowIndex(today)
            if (songsThisWeek >= target) {
                out += Insight(
                    id = "music.songs.hit",
                    severity = Severity.POSITIVE,
                    domain = "Music",
                    title = "Weekly song target met",
                    detail = "$songsThisWeek of $target learned this week.",
                    weight = 36
                )
            } else if (dow >= 4) {
                out += Insight(
                    id = "music.songs.miss",
                    severity = Severity.WARNING,
                    domain = "Music",
                    title = "Song target behind with ${6 - dow + 1} day${plural(6 - dow + 1)} left",
                    detail = "$songsThisWeek of $target learned so far this week.",
                    actionLabel = "Open studio",
                    actionTab = "MUSIC",
                    weight = 52
                )
            }
        }

        // Album completion projection from observed rate.
        store.albums.items.value.forEach { a ->
            if (a.learnedTracks <= 0 || a.learnedTracks >= a.totalTracks) return@forEach
            val ageDays = (today - T.dayOf(a.createdAt)).toInt().coerceAtLeast(1)
            val perDay = a.learnedTracks.toDouble() / ageDays
            if (perDay <= 0.0) return@forEach
            val remaining = a.totalTracks - a.learnedTracks
            val daysLeft = (remaining / perDay).roundToInt()
            if (daysLeft in 1..3650) {
                out += Insight(
                    id = "music.album.${a.id}",
                    severity = Severity.INFO,
                    domain = "Music",
                    title = "\"${a.album}\" finishes around ${T.dateLabel(today + daysLeft)}",
                    detail = "${a.learnedTracks} of ${a.totalTracks} tracks in $ageDays days — " +
                        "that pace puts $remaining more at roughly $daysLeft days.",
                    actionTab = "MUSIC",
                    weight = 26
                )
            }
        }

        // Today's shape checklist.
        val shape = store.cagedShapeForToday()
        if (shape != null) {
            val subs = practice.filter { it.kind == PracticeKind.SUBTASK && it.parentId == shape.id }
            val remaining = subs.count { it.doneEpochDay != today }
            if (subs.isNotEmpty() && remaining == 0) {
                out += Insight(
                    id = "music.shape.done",
                    severity = Severity.POSITIVE,
                    domain = "Music",
                    title = "${shape.title} fully worked today",
                    detail = "All ${subs.size} drills ticked off.",
                    weight = 44
                )
            }
        }
    }

    // -- faith ---------------------------------------------------------------

    private fun faithInsights(store: Store, today: Long, out: MutableList<Insight>) {
        val routines = store.routines.items.value

        routines.forEach { r ->
            val last = r.lastDoneEpochDay ?: return@forEach
            val gap = (today - last).toInt()
            val threshold = when (r.cadence.lowercase()) {
                "weekly" -> 14
                "monthly" -> 45
                else -> 5
            }
            if (gap >= threshold) {
                out += Insight(
                    id = "faith.gap.${r.id}",
                    severity = Severity.INFO,
                    domain = "Faith",
                    title = "\"${r.title}\" last kept $gap days ago",
                    detail = "Cadence is ${r.cadence.lowercase()}, so this one has drifted.",
                    actionLabel = "Open faith",
                    actionTab = "FAITH",
                    weight = 48
                )
            }
        }

        val prayers = store.prayers.items.value
        val answered = prayers.filter { it.answered }
        val recentAnswered = answered.count { (it.answeredAt ?: 0L).let { at -> T.dayOf(at) >= today - 30 } }
        if (recentAnswered > 0) {
            out += Insight(
                id = "faith.answered",
                severity = Severity.POSITIVE,
                domain = "Faith",
                title = "$recentAnswered prayer${plural(recentAnswered)} marked answered this month",
                detail = "${answered.size} of ${prayers.size} logged prayers answered overall.",
                actionTab = "FAITH",
                weight = 32
            )
        }

        val ancient = prayers.filter { !it.answered && today - T.dayOf(it.createdAt) > 180 }
        if (ancient.size >= 3) {
            out += Insight(
                id = "faith.ancient",
                severity = Severity.INFO,
                domain = "Faith",
                title = "${ancient.size} prayer points older than 6 months",
                detail = "Worth revisiting — some may have quietly been answered.",
                actionTab = "FAITH",
                weight = 20
            )
        }
    }

    // -- journal -------------------------------------------------------------

    private fun journalInsights(store: Store, today: Long, out: MutableList<Insight>) {
        val entries = store.journal.items.value
        if (entries.isEmpty()) return

        val lastDay = entries.maxOf { T.dayOf(it.createdAt) }
        val gap = (today - lastDay).toInt()
        if (gap >= 7) {
            out += Insight(
                id = "journal.gap",
                severity = Severity.INFO,
                domain = "Journal",
                title = "No entry for $gap days",
                detail = "The mood series needs regular points before it can show a trend.",
                actionLabel = "Write one",
                actionTab = "JOURNAL",
                weight = 30
            )
        }

        val recent = entries.filter { T.dayOf(it.createdAt) >= today - 13 }
        val cur = recent.filter { T.dayOf(it.createdAt) >= today - 6 }
        val prev = recent.filter { T.dayOf(it.createdAt) < today - 6 }
        if (cur.size >= 2 && prev.size >= 2) {
            val curAvg = cur.map { it.mood }.average()
            val prevAvg = prev.map { it.mood }.average()
            val delta = curAvg - prevAvg
            if (delta <= -0.7) {
                out += Insight(
                    id = "journal.mood.down",
                    severity = Severity.WARNING,
                    domain = "Journal",
                    title = "Mood is trending down",
                    detail = "Averaging ${fmt1(curAvg)} this week against ${fmt1(prevAvg)} last week " +
                        "across ${cur.size + prev.size} entries.",
                    actionTab = "JOURNAL",
                    weight = 62
                )
            } else if (delta >= 0.7) {
                out += Insight(
                    id = "journal.mood.up",
                    severity = Severity.POSITIVE,
                    domain = "Journal",
                    title = "Mood is trending up",
                    detail = "Averaging ${fmt1(curAvg)} this week against ${fmt1(prevAvg)} last week.",
                    weight = 40
                )
            }
        }

        // Does training correlate with mood? Needs a reasonable sample on both sides.
        val trainedDays = store.workoutLogs.items.value.map { T.dayOf(it.loggedAt) }.toSet()
        val withTraining = entries.filter { trainedDays.contains(T.dayOf(it.createdAt)) }.map { it.mood }
        val without = entries.filter { !trainedDays.contains(T.dayOf(it.createdAt)) }.map { it.mood }
        if (withTraining.size >= 4 && without.size >= 4) {
            val diff = withTraining.average() - without.average()
            if (abs(diff) >= 0.5) {
                out += Insight(
                    id = "journal.correlation",
                    severity = Severity.INFO,
                    domain = "Journal",
                    title = if (diff > 0) {
                        "Mood runs ${fmt1(diff)} higher on training days"
                    } else {
                        "Mood runs ${fmt1(abs(diff))} lower on training days"
                    },
                    detail = "${fmt1(withTraining.average())} across ${withTraining.size} entries on days " +
                        "you trained, ${fmt1(without.average())} across ${without.size} on days you didn't. " +
                        "That's an association in your own log, not proof of cause.",
                    weight = 50
                )
            }
        }
    }

    private fun fmt1(v: Double): String {
        val r = (v * 10).roundToInt()
        return "${r / 10}.${abs(r % 10)}"
    }

    // -- wishlist ------------------------------------------------------------

    private fun wishlistInsights(store: Store, settings: Settings, out: MutableList<Insight>) {
        val open = store.wishlist.items.value.filter { !it.purchased }
        if (open.isEmpty()) return
        val total = open.sumOf { it.price }
        if (total <= 0.0) return

        val top = open.filter { it.priority == Priority.URGENT || it.priority == Priority.HIGH }
            .sortedBy { it.price }
        out += Insight(
            id = "wishlist.total",
            severity = Severity.INFO,
            domain = "Wishlist",
            title = "${settings.currency}${money(total)} outstanding across ${open.size} item${plural(open.size)}",
            detail = if (top.isNotEmpty()) {
                "Cheapest high-priority buy is \"${top.first().name}\" at " +
                    "${settings.currency}${money(top.first().price)}."
            } else "Nothing flagged high priority yet.",
            actionTab = "LIFE",
            weight = 18
        )
    }

    private fun money(v: Double): String {
        val cents = (v * 100).roundToInt()
        val whole = cents / 100
        val frac = abs(cents % 100)
        return if (frac == 0) "$whole" else "$whole.${frac.toString().padStart(2, '0')}"
    }

    // -- rhythm --------------------------------------------------------------

    private fun rhythmInsights(logs: List<ActivityLog>, out: MutableList<Insight>) {
        val completes = logs.filter { it.event == "COMPLETE" }
        if (completes.size < 15) return

        val byBlock = completes.groupBy { it.hour / 3 }
        val best = byBlock.maxByOrNull { it.value.size }
        if (best != null && best.value.size.toDouble() / completes.size >= 0.3) {
            val from = best.key * 3
            val to = from + 3
            out += Insight(
                id = "rhythm.hour",
                severity = Severity.INFO,
                domain = "Rhythm",
                title = "You're most productive ${pad(from)}:00–${pad(to)}:00",
                detail = "${best.value.size} of ${completes.size} completions land in that window. " +
                    "Worth protecting it for the work that matters.",
                weight = 46
            )
        }

        val byDow = completes.groupBy { T.dowIndex(it.epochDay) }
        val weakest = (0..6).minByOrNull { byDow[it]?.size ?: 0 }
        val strongest = byDow.maxByOrNull { it.value.size }
        if (weakest != null && strongest != null && strongest.value.size >= 5) {
            val weakCount = byDow[weakest]?.size ?: 0
            if (weakCount * 3 <= strongest.value.size) {
                out += Insight(
                    id = "rhythm.dow",
                    severity = Severity.INFO,
                    domain = "Rhythm",
                    title = "${T.dayNameFull(weakest)} is your quietest day",
                    detail = "$weakCount completions against ${strongest.value.size} on " +
                        "${T.dayNameFull(strongest.key)}. Either lighten the schedule there or " +
                        "move something into it deliberately.",
                    weight = 24
                )
            }
        }
    }

    private fun pad(h: Int) = h.toString().padStart(2, '0')

    // -- momentum ------------------------------------------------------------

    private fun momentumInsight(
        domains: List<DomainScore>,
        delta: Int,
        out: MutableList<Insight>
    ) {
        if (domains.isEmpty()) return
        val weakest = domains.minByOrNull { it.score } ?: return
        val strongest = domains.maxByOrNull { it.score } ?: return

        if (strongest.score - weakest.score >= 40) {
            out += Insight(
                id = "momentum.imbalance",
                severity = Severity.INFO,
                domain = "Momentum",
                title = "${weakest.domain} is lagging the rest",
                detail = "${weakest.domain} at ${weakest.score}% against ${strongest.domain} at " +
                    "${strongest.score}% — ${weakest.caption}.",
                weight = 54
            )
        }

        if (delta <= -15) {
            out += Insight(
                id = "momentum.drop",
                severity = Severity.WARNING,
                domain = "Momentum",
                title = "Overall momentum down $delta points",
                detail = "Averaged across ${domains.size} areas against the previous 7 days. " +
                    "One quiet week isn't a trend, but two is.",
                weight = 56
            )
        } else if (delta >= 15) {
            out += Insight(
                id = "momentum.rise",
                severity = Severity.POSITIVE,
                domain = "Momentum",
                title = "Momentum up $delta points on last week",
                detail = "Gains across ${domains.count { it.delta > 0 }} of ${domains.size} areas.",
                weight = 42
            )
        }
    }

    // --------------------------------------------------------- next actions

    /**
     * Ranked "what should I do right now". Ordering blends urgency (overdue,
     * streak about to break) with time of day, so an evening open surfaces
     * different work from a morning one.
     */
    fun nextActions(store: Store, today: Long = T.today(), hour: Int = T.localHour()): List<NextAction> {
        val out = mutableListOf<NextAction>()

        store.todos.items.value
            .filter { !it.done }
            .filter { (it.dueEpochDay ?: Long.MAX_VALUE) <= today }
            .sortedWith(compareBy({ it.dueEpochDay ?: Long.MAX_VALUE }, { it.priority.ordinal }))
            .take(3)
            .forEach { t ->
                val day = t.dueEpochDay
                val late = if (day != null && day < today) (today - day).toInt() else 0
                out += NextAction(
                    title = t.title,
                    reason = when {
                        late > 0 -> "$late day${plural(late)} overdue"
                        t.priority == Priority.URGENT -> "Urgent, due today"
                        else -> "Due today"
                    },
                    domain = "Tasks",
                    actionTab = "TODAY",
                    weight = 900 + late * 5 + (4 - t.priority.ordinal) * 3,
                    estimateMinutes = t.estimateMinutes ?: 10
                )
            }

        val streak = store.workoutStreak()
        val trainedToday = store.workoutLogs.items.value.any { T.dayOf(it.loggedAt) == today }
        val dueWorkouts = store.workoutsDueToday().filter { it.doneEpochDay != today }
        if (dueWorkouts.isNotEmpty() && !trainedToday) {
            out += NextAction(
                title = dueWorkouts.first().name,
                reason = if (streak >= 2) "Keeps a $streak-day streak alive" else "Scheduled for today",
                domain = "Fitness",
                actionTab = "TODAY",
                weight = if (streak >= 2 && hour >= 17) 950 else 700,
                estimateMinutes = 10
            )
        }

        val shape = store.cagedShapeForToday()
        if (shape != null) {
            val subs = store.practice.items.value
                .filter { it.kind == PracticeKind.SUBTASK && it.parentId == shape.id && it.doneEpochDay != today }
            if (subs.isNotEmpty()) {
                out += NextAction(
                    title = "${shape.title}: ${subs.first().title}",
                    reason = "${subs.size} drill${plural(subs.size)} left on today's shape",
                    domain = "Practice",
                    actionTab = "TODAY",
                    weight = if (hour in 6..20) 640 else 380,
                    estimateMinutes = 15
                )
            }
        }

        store.routines.items.value
            .filter { store.routineDueToday(it, today) }
            .take(2)
            .forEach { r ->
                out += NextAction(
                    title = r.title,
                    reason = "${r.cadence} rhythm, due today",
                    domain = "Faith",
                    actionTab = "FAITH",
                    weight = if (hour < 10 || hour >= 19) 660 else 480,
                    estimateMinutes = 15
                )
            }

        if (hour >= 19) {
            val wroteToday = store.journal.items.value.any { T.dayOf(it.createdAt) == today }
            if (!wroteToday) {
                out += NextAction(
                    title = "Journal today",
                    reason = "Evening — close the day out",
                    domain = "Journal",
                    actionTab = "JOURNAL",
                    weight = 520,
                    estimateMinutes = 5
                )
            }
        }

        val behind = store.goals.items.value.firstOrNull { g ->
            val target = g.targetEpochDay ?: return@firstOrNull false
            val start = T.dayOf(g.createdAt)
            val total = (target - start).toInt()
            if (total <= 0) return@firstOrNull false
            val expected = (((today - start).toInt().coerceIn(0, total).toDouble() / total) * 100).roundToInt()
            g.progress < expected - 15 && target >= today
        }
        if (behind != null) {
            out += NextAction(
                title = "Move \"${behind.title}\" forward",
                reason = "Behind pace at ${behind.progress}%",
                domain = "Goals",
                actionTab = "LIFE",
                weight = 560,
                estimateMinutes = 20
            )
        }

        return out.sortedByDescending { it.weight }.take(5)
    }

    // -------------------------------------------------------- weekly review

    /**
     * Narrative week-in-review, generated from the same log the scores use.
     * Returned as discrete lines so the UI can render them as a list.
     */
    fun weeklyReview(store: Store, today: Long = T.today()): List<String> {
        val lines = mutableListOf<String>()
        val logs = store.activity.items.value
        val from = today - 6
        val prevFrom = today - 13
        val prevTo = today - 7

        val cur = logs.count { it.event == "COMPLETE" && it.epochDay in from..today }
        val prev = logs.count { it.event == "COMPLETE" && it.epochDay in prevFrom..prevTo }

        lines += if (prev == 0) {
            "$cur things logged over the last 7 days."
        } else {
            val diff = cur - prev
            val dir = if (diff >= 0) "up" else "down"
            "$cur things logged this week, $dir ${abs(diff)} on the week before."
        }

        val activeDays = logs.filter { it.event == "COMPLETE" && it.epochDay in from..today }
            .map { it.epochDay }.distinct().size
        lines += "Active on $activeDays of 7 days."

        val byDomain = logs.filter { it.event == "COMPLETE" && it.epochDay in from..today }
            .groupBy { it.domain }
        byDomain.entries.sortedByDescending { it.value.size }.take(3).forEach { (domain, rows) ->
            lines += "${domain.replaceFirstChar { it.uppercase() }}: ${rows.size} entries."
        }

        val closed = store.todos.items.value.count { t ->
            val at = t.completedAt ?: return@count false
            T.dayOf(at) in from..today
        }
        if (closed > 0) lines += "$closed task${plural(closed)} closed."

        val sets = store.workoutLogs.items.value.count { T.dayOf(it.loggedAt) in from..today }
        if (sets > 0) lines += "$sets training set${plural(sets)} logged."

        val entries = store.journal.items.value.filter { T.dayOf(it.createdAt) in from..today }
        if (entries.isNotEmpty()) {
            lines += "${entries.size} journal entr${if (entries.size == 1) "y" else "ies"}, " +
                "mood averaging ${fmt1(entries.map { it.mood }.average())}."
        }

        val resets = store.habitLogs.items.value.count {
            it.event == "RELAPSE" && T.dayOf(it.loggedAt) in from..today
        }
        lines += if (resets == 0) "No recovery resets this week." else "$resets recovery reset${plural(resets)}."

        return lines
    }
}
