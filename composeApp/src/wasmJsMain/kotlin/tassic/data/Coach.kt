package tassic.data

import kotlin.math.roundToInt

/**
 * The companion layer.
 *
 * [Insights] answers "how am I doing" by scoring what already happened. This
 * answers the two questions a personal assistant is actually for: *what should
 * I do with the hours I have left today*, and *what did today amount to*.
 *
 * Same discipline as the insights engine: every line names the rows behind it,
 * nothing is invented, and where the data is thin the answer says so instead of
 * manufacturing confidence.
 */

/** A line in the evening review. */
data class ReviewLine(
    /** "win" | "slip" | "note" | "ahead" */
    val kind: String,
    val text: String
)

data class ReviewDraft(
    val day: Long,
    val headline: String,
    val lines: List<ReviewLine>,
    val doneCount: Int,
    val openCount: Int,
    val focusMinutes: Int,
    /** Pre-filled body for a journal entry, if the user wants to keep it. */
    val journalBody: String
)

/** Habit health, computed over the last four weeks. */
data class HabitPulse(
    val habit: Habit,
    val streak: Int,
    val keptLast28: Int,
    val dueLast28: Int,
    val doneToday: Boolean,
    val countToday: Int,
    val weekCount: Int
) {
    val consistency: Int get() = if (dueLast28 == 0) 0 else (keptLast28 * 100) / dueLast28
}

object Coach {

    private const val HISTORY_DAYS = 28

    // --------------------------------------------------------------- habits

    fun pulse(store: Store, habit: Habit, today: Long = T.today()): HabitPulse {
        val history = store.habitHistory(habit, HISTORY_DAYS, today)
        val due = history.count { it != null }
        val kept = history.count { it == true }
        val count = store.habitCount(habit.id, today)
        return HabitPulse(
            habit = habit,
            streak = store.habitStreak(habit, today),
            keptLast28 = kept,
            dueLast28 = due,
            doneToday = count >= habit.targetPerDay.coerceAtLeast(1),
            countToday = count,
            weekCount = store.habitWeekCount(habit, today)
        )
    }

    fun allPulses(store: Store, today: Long = T.today()): List<HabitPulse> =
        store.activeHabits().map { pulse(store, it, today) }

    /**
     * Habit observations, strongest signal first.
     *
     * Held to a floor of two weeks of history: a three-day-old habit at 33%
     * isn't a pattern, it's a habit that's three days old, and saying otherwise
     * teaches people to ignore the app.
     */
    fun habitInsights(store: Store, today: Long = T.today()): List<Insight> {
        val out = mutableListOf<Insight>()
        allPulses(store, today).forEach { p ->
            val ageDays = ((T.now() - p.habit.createdAt) / T.DAY_MS).toInt()
            if (p.dueLast28 < 5 || ageDays < 5) return@forEach

            when {
                p.streak >= 21 -> out += Insight(
                    id = "habit-locked-${p.habit.id}",
                    severity = Severity.POSITIVE,
                    domain = "habits",
                    title = "${p.habit.name} is holding at ${p.streak} days",
                    detail = "Kept ${p.keptLast28} of the last ${p.dueLast28} days it was due. This one no longer needs watching.",
                    weight = 60
                )
                p.consistency >= 80 -> out += Insight(
                    id = "habit-strong-${p.habit.id}",
                    severity = Severity.POSITIVE,
                    domain = "habits",
                    title = "${p.habit.name} at ${p.consistency}%",
                    detail = "Kept ${p.keptLast28} of ${p.dueLast28} due days over four weeks.",
                    weight = 40
                )
                p.consistency < 35 -> out += Insight(
                    id = "habit-stalling-${p.habit.id}",
                    severity = Severity.WARNING,
                    domain = "habits",
                    title = "${p.habit.name} has stalled at ${p.consistency}%",
                    detail = "Kept only ${p.keptLast28} of ${p.dueLast28} due days. Either shrink it to something you'd do on your worst day, or retire it — a habit you ignore costs more than it looks.",
                    actionLabel = "Open habits",
                    actionTab = "PLAN",
                    weight = 75
                )
                p.streak == 0 && p.keptLast28 > 0 -> out += Insight(
                    id = "habit-broken-${p.habit.id}",
                    severity = Severity.INFO,
                    domain = "habits",
                    title = "${p.habit.name} lost its streak",
                    detail = "Still kept ${p.keptLast28} of ${p.dueLast28} days this month — one miss isn't the trend.",
                    actionLabel = "Open habits",
                    actionTab = "PLAN",
                    weight = 30
                )
            }
        }

        // Slot analysis: which weekday do habits fall over on?
        val missesByDow = IntArray(7)
        val dueByDow = IntArray(7)
        store.activeHabits().forEach { habit ->
            store.habitHistory(habit, HISTORY_DAYS, today).forEachIndexed { index, kept ->
                if (kept == null) return@forEachIndexed
                val day = today - (HISTORY_DAYS - 1 - index)
                val dow = T.dowIndex(day)
                dueByDow[dow]++
                if (!kept) missesByDow[dow]++
            }
        }
        val worst = (0..6).filter { dueByDow[it] >= 3 }.maxByOrNull { missesByDow[it].toFloat() / dueByDow[it] }
        if (worst != null && dueByDow[worst] >= 3) {
            val rate = missesByDow[worst].toFloat() / dueByDow[worst]
            val overall = missesByDow.sum().toFloat() / dueByDow.sum().coerceAtLeast(1)
            if (rate > 0.5f && rate > overall * 1.5f) {
                out += Insight(
                    id = "habit-weekday",
                    severity = Severity.INFO,
                    domain = "habits",
                    title = "${T.dayNameFull(worst)}s are where habits slip",
                    detail = "${missesByDow[worst]} of ${dueByDow[worst]} habit days missed on ${T.dayNameFull(worst)}s over four weeks, against ${(overall * 100).roundToInt()}% on an average day.",
                    weight = 55
                )
            }
        }

        return out.sortedByDescending { it.weight }
    }

    // -------------------------------------------------------------- planning

    /**
     * One sentence about whether the rest of today fits.
     *
     * Returns null when there's nothing worth saying — an empty plan or a
     * finished one shouldn't produce filler.
     */
    fun scheduleHint(plan: DayPlan, nowMinutes: Int = T.localMinuteOfDay()): String? {
        val outstanding = plan.allEntries.count { !it.done }
        if (outstanding == 0) return null
        if (plan.freeSlots.isEmpty()) {
            return "No clear gaps left today — anything not started is really tomorrow's."
        }
        val biggest = plan.freeSlots.maxByOrNull { it.minutes } ?: return null
        val fitsLabel = Nlp.durationLabel(plan.remainingMinutes)
        val freeLabel = Nlp.durationLabel(plan.availableMinutes)

        return if (plan.fits) {
            "$fitsLabel of work left against $freeLabel free — the longest clear run starts at ${Agenda.clockLabel(biggest.startMinutes)}."
        } else {
            val over = plan.remainingMinutes - plan.availableMinutes
            "$fitsLabel of work against $freeLabel free. That's ${Nlp.durationLabel(over)} more than today holds — move or drop something rather than finding out at 22:00."
        }
    }

    /** The hour band where completions actually happen, from the activity log. */
    fun productiveWindow(store: Store, today: Long = T.today()): Pair<Int, Int>? {
        val logs = store.activity.items.value.filter {
            it.epochDay >= today - HISTORY_DAYS && it.event == "COMPLETE"
        }
        if (logs.size < 12) return null
        val byHour = IntArray(24)
        logs.forEach { byHour[it.hour.coerceIn(0, 23)]++ }
        var bestStart = 0
        var bestScore = -1
        for (start in 0..21) {
            val score = byHour[start] + byHour[start + 1] + byHour[start + 2]
            if (score > bestScore) {
                bestScore = score
                bestStart = start
            }
        }
        if (bestScore < logs.size / 4) return null
        return bestStart to (bestStart + 3)
    }


    // ----------------------------------------------------------- weekly plan

    /**
     * Whether to prompt for a weekly plan.
     *
     * From the chosen hour on the chosen day, and only while no plan exists for
     * the week about to start. Asking again once someone has written one is how
     * a ritual turns into nagging.
     */
    fun weeklyPlanDue(store: Store, today: Long = T.today(), hour: Int = T.localHour()): Boolean {
        val s = store.settings()
        if (!s.weeklyPlanOn) return false
        // The plan is for the week that contains tomorrow when prompting on the
        // last day of the current week, and for this week otherwise.
        val target = targetWeek(store, today)
        if (store.weekPlanFor(target) != null) return false
        return T.dowIndex(today) == s.weeklyPlanDow && hour >= s.weeklyPlanHour
    }

    /** The week a plan written now should apply to. */
    fun targetWeek(store: Store, today: Long = T.today()): Long {
        val s = store.settings()
        // Planning on Sunday for a Monday-start week means planning the week
        // that begins tomorrow, not the one that is ending in a few hours.
        val ahead = s.weekStartsMonday && T.dowIndex(today) == 6
        return T.weekIndex(if (ahead) today + 1 else today)
    }

    /**
     * How the week is going against what the person actually said mattered.
     *
     * Momentum answers "was I busy"; this answers "was I busy with the right
     * things", which is the only version of the question worth asking on a
     * Sunday.
     */
    fun weekVerdict(store: Store, today: Long = T.today()): String? {
        val plan = store.weekPlanFor(T.weekIndex(today)) ?: return null
        if (plan.priorities.isEmpty()) return null
        val done = plan.priorities.count { it.done }
        val total = plan.priorities.size
        val dayOfWeek = T.dowIndex(today) + 1
        return when {
            done == total -> "All $total priorities closed with ${7 - dayOfWeek} day(s) of the week left."
            done == 0 && dayOfWeek >= 5 ->
                "None of this week's $total priorities have moved, and it's ${T.dayNameFullOf(today)}."
            done == 0 -> "$total priorities set, none closed yet."
            else -> "$done of $total priorities closed."
        }
    }

    // --------------------------------------------------------------- people

    /**
     * Relationship observations.
     *
     * Deliberately factual and few. A contact list that scores friendships is
     * unpleasant to use, so this names the gap and the birthday and says
     * nothing about what anyone should do about it.
     */
    fun peopleInsights(store: Store, today: Long = T.today()): List<Insight> {
        val out = mutableListOf<Insight>()

        People.upcomingBirthdays(store, today, People.BIRTHDAY_HORIZON).take(3).forEach { status ->
            val days = status.daysToBirthday ?: return@forEach
            out += Insight(
                id = "birthday-${status.person.id}",
                severity = Severity.INFO,
                domain = "people",
                title = when (days) {
                    0 -> "${status.person.name}'s birthday is today"
                    1 -> "${status.person.name}'s birthday is tomorrow"
                    else -> "${status.person.name}'s birthday is in $days days"
                },
                detail = "Recorded as ${status.person.relationship.lowercase()}.",
                actionLabel = "Open people",
                actionTab = "PEOPLE",
                weight = 90 - days
            )
        }

        People.overdue(store, today).take(3).forEach { status ->
            val since = status.daysSince
            out += Insight(
                id = "contact-${status.person.id}",
                severity = Severity.INFO,
                domain = "people",
                title = "${status.person.name} — ${if (since == null) "no contact logged" else "$since days"}",
                detail = if (since == null) {
                    "You set a ${People.cadenceLabel(status.person.cadenceDays)} rhythm here but haven't logged a conversation yet."
                } else {
                    "That's ${status.overdueBy} days past the ${People.cadenceLabel(status.person.cadenceDays)} rhythm you set."
                },
                actionLabel = "Open people",
                actionTab = "PEOPLE",
                weight = 40 + status.overdueBy.coerceAtMost(40)
            )
        }

        return out.sortedByDescending { it.weight }
    }


    // ------------------------------------------------------------------ goals

    /**
     * Goal health.
     *
     * The failure mode for a goal list is not that goals are missed — it's that
     * they go quiet. A goal nobody has touched in six weeks is still sitting
     * there at 40%, looking exactly like one that's moving, and the list slowly
     * becomes a museum. This names the stale ones and the ones with a date
     * closing in on them.
     */
    fun goalInsights(store: Store, today: Long = T.today()): List<Insight> {
        val out = mutableListOf<Insight>()
        val logs = store.activity.items.value.filter { it.domain == "goals" }

        store.goals.items.value.filter { !it.archived && it.progress < 100 }.forEach { goal ->
            val lastTouched = logs.filter { it.refId == goal.id }.maxOfOrNull { it.epochDay }
                ?: (goal.createdAt / T.DAY_MS)
            val quietDays = (today - lastTouched).toInt()
            val linked = store.tasksForGoal(goal.id)
            val openLinked = linked.count { !it.done }

            when {
                goal.targetEpochDay != null && goal.targetEpochDay!! in today..(today + 14) && goal.progress < 70 ->
                    out += Insight(
                        id = "goal-deadline-${goal.id}",
                        severity = Severity.WARNING,
                        domain = "goals",
                        title = "${goal.title} is due in ${goal.targetEpochDay!! - today} day(s) at ${goal.progress}%",
                        detail = if (linked.isEmpty()) {
                            "Nothing is linked to it, so nothing is moving it. Either link the tasks that serve it or move the date honestly."
                        } else {
                            "$openLinked of ${linked.size} linked task(s) still open."
                        },
                        actionLabel = "Open goals",
                        actionTab = "LIFE",
                        weight = 85
                    )
                goal.targetEpochDay != null && goal.targetEpochDay!! < today ->
                    out += Insight(
                        id = "goal-overdue-${goal.id}",
                        severity = Severity.INFO,
                        domain = "goals",
                        title = "${goal.title} passed its date at ${goal.progress}%",
                        detail = "${today - goal.targetEpochDay!!} days ago. Moving the date is a decision; leaving it is a decision too, just a quieter one.",
                        actionLabel = "Open goals",
                        actionTab = "LIFE",
                        weight = 55
                    )
                quietDays >= 45 ->
                    out += Insight(
                        id = "goal-stale-${goal.id}",
                        severity = Severity.INFO,
                        domain = "goals",
                        title = "${goal.title} hasn't moved in $quietDays days",
                        detail = if (goal.motivation.isNotBlank()) {
                            "You wrote: \"${goal.motivation}\" — still true, or has this run its course?"
                        } else {
                            "Still at ${goal.progress}%. A goal that's stopped mattering is worth archiving rather than carrying."
                        },
                        actionLabel = "Open goals",
                        actionTab = "LIFE",
                        weight = 45 + (quietDays / 10).coerceAtMost(20)
                    )
            }
        }

        // Horizon balance: all-short is drift, all-long is avoidance.
        val active = store.goals.items.value.filter { !it.archived && it.progress < 100 }
        if (active.size >= 4) {
            val byHorizon = active.groupBy { it.horizon }
            val dominant = byHorizon.maxByOrNull { it.value.size }
            if (dominant != null && dominant.value.size == active.size) {
                out += Insight(
                    id = "goal-horizon",
                    severity = Severity.INFO,
                    domain = "goals",
                    title = "Every goal sits on one horizon",
                    detail = "All ${active.size} are ${dominant.key.name.lowercase()}-term. Short-only tends to be drift; long-only tends to be avoidance.",
                    weight = 30
                )
            }
        }
        return out.sortedByDescending { it.weight }
    }

    /** Wishlist totals, for the summary card rather than as an insight. */
    fun wishlistSummary(store: Store): Triple<Int, Double, Double> {
        val items = store.wishlist.items.value
        val open = items.filter { !it.purchased }
        return Triple(open.size, open.sumOf { it.price }, items.filter { it.purchased }.sumOf { it.price })
    }

    // ------------------------------------------------------------------ music

    /** Minutes logged against practice and focus in the last seven days. */
    fun practiceMinutesThisWeek(store: Store, today: Long = T.today()): Int =
        store.activity.items.value
            .filter {
                it.epochDay > today - 7 &&
                    (it.domain == "practice" || it.domain == "music" ||
                        (it.domain == "focus" && it.title.contains("Practice", ignoreCase = true)))
            }
            .sumOf { if (it.domain == "focus") it.value else 15 }

    /** Consecutive days ending today with any practice logged. */
    fun practiceStreak(store: Store, today: Long = T.today()): Int {
        val days = store.activity.items.value
            .filter { it.domain == "practice" || it.domain == "music" }
            .map { it.epochDay }
            .toSet()
        var streak = 0
        var cursor = if (days.contains(today)) today else today - 1
        while (days.contains(cursor) && streak < 400) {
            streak++
            cursor--
        }
        return streak
    }

    /**
     * Practice observations.
     *
     * The studio holds shapes, modes, keys and songs with no sense of which
     * have gone cold — so the comfortable ones get rehearsed and the awkward
     * ones quietly stop appearing. Naming the neglected item is the single most
     * useful thing this data can do.
     */
    fun musicInsights(store: Store, section: String, today: Long = T.today()): List<Insight> {
        val out = mutableListOf<Insight>()
        val items = store.practice.items.value.filter { it.section == section }
        if (items.isEmpty()) return out

        val coldest = items
            .filter { it.kind == PracticeKind.SHAPE || it.kind == PracticeKind.MODE || it.kind == PracticeKind.KEY }
            .minByOrNull { it.doneEpochDay ?: 0L }
        val lastDone = coldest?.doneEpochDay
        if (coldest != null && (lastDone == null || today - lastDone >= 14)) {
            out += Insight(
                id = "music-cold-${coldest.id}",
                severity = Severity.INFO,
                domain = "practice",
                title = "${coldest.title} hasn't come round in a while",
                detail = if (lastDone == null) {
                    "Never logged. The comfortable material rehearses itself; this is the kind that doesn't."
                } else {
                    "Last worked ${today - lastDone} days ago."
                },
                actionLabel = "Open studio",
                actionTab = "MUSIC",
                weight = 40
            )
        }

        val streak = practiceStreak(store, today)
        if (streak >= 5) {
            out += Insight(
                id = "music-streak",
                severity = Severity.POSITIVE,
                domain = "practice",
                title = "$streak days of practice in a row",
                detail = "${practiceMinutesThisWeek(store, today)} minutes logged over the last week.",
                weight = 35
            )
        }
        return out.sortedByDescending { it.weight }
    }


    // ----------------------------------------------------------- today brief

    /**
     * One thing worth knowing, with somewhere to go about it.
     *
     * [kind] drives the icon and colour; [tab] is where tapping it lands.
     */
    data class BriefSignal(val kind: String, val label: String, val tab: String)

    /**
     * The status readout for the Today screen.
     *
     * Deliberately capped. The app now tracks eleven domains, and a home screen
     * that reports on all of them is a dashboard — impressive once, ignored by
     * the end of the week, and no help at all on a morning when you're already
     * behind. So: one headline, one next action with its reason, and **at most
     * three** signals, chosen by what is actually true today rather than by a
     * fixed layout.
     *
     * Everything here is already computed elsewhere. This picks, orders and
     * throws away — which is the part that makes it usable.
     */
    /** How many signals the brief will ever show. */
    const val MAX_SIGNALS = 3

    fun signals(
        store: Store,
        today: Long = T.today(),
        hour: Int = T.localHour()
    ): List<BriefSignal> {
        val plan = Agenda.plan(store, today)
        val settings = store.settings()

        // Candidates in priority order. Only the true ones survive, and only
        // the first MAX_SIGNALS of those are shown — so a quiet day says almost
        // nothing rather than padding itself out.
        val candidates = mutableListOf<BriefSignal>()

        // 1. The next thing pinned to a clock. Nothing else on this list can
        //    be missed by simply not looking up.
        val nextTimed = plan.timed.firstOrNull { !it.done && (it.startMinutes ?: 0) >= T.localMinuteOfDay() }
        if (nextTimed?.startMinutes != null) {
            candidates += BriefSignal(
                "clock",
                "${Agenda.clockLabel(nextTimed.startMinutes)} · ${nextTimed.title}",
                if (nextTimed.kind == "event") "PLAN" else "PLAN"
            )
        }

        // 2. The day genuinely not fitting is worth knowing early enough to act.
        if (settings.planningHintsOn && !plan.fits && plan.remainingMinutes > 0) {
            candidates += BriefSignal(
                "warn",
                "${Nlp.durationLabel(plan.remainingMinutes - plan.availableMinutes)} more than today holds",
                "PLAN"
            )
        }

        // 3. A streak about to break outranks a plain count of open habits.
        val habitsDue = store.habitsDueToday(today)
        val openHabits = habitsDue.filter { !store.habitDoneOn(it, today) }
        val riskiest = openHabits.maxByOrNull { store.habitStreak(it, today) }
        val riskStreak = riskiest?.let { store.habitStreak(it, today) } ?: 0
        if (riskiest != null && riskStreak >= 3 && hour >= 16) {
            candidates += BriefSignal("streak", "${riskiest.name} · $riskStreak-day run open", "PLAN")
        } else if (openHabits.isNotEmpty()) {
            candidates += BriefSignal(
                "habit",
                "${habitsDue.size - openHabits.size}/${habitsDue.size} habits kept",
                "PLAN"
            )
        }

        // 4. Faith: today's reading, then verses due.
        val readingPlan = store.activeReadingPlan()
        if (readingPlan != null) {
            val index = store.readingDayIndex(readingPlan, today)
            if (!readingPlan.completedDays.contains(index)) {
                readingPlan.days.getOrNull(index)?.let {
                    candidates += BriefSignal("book", it, "FAITH")
                }
            }
        }
        val versesDue = store.versesDue(today).size
        if (versesDue > 0) {
            candidates += BriefSignal("book", "$versesDue verse(s) to review", "FAITH")
        }

        // 5. A birthday is time-critical in a way nothing else here is.
        People.upcomingBirthdays(store, today, 1).firstOrNull()?.let { status ->
            candidates.add(
                0,
                BriefSignal(
                    "cake",
                    if (status.daysToBirthday == 0) {
                        "${status.person.name}'s birthday today"
                    } else {
                        "${status.person.name}'s birthday tomorrow"
                    },
                    "PEOPLE"
                )
            )
        }

        // 6. The week's stated priorities, if any are still open.
        store.weekPlanFor(T.weekIndex(today))?.priorities?.firstOrNull { !it.done }?.let {
            candidates += BriefSignal("flag", it.title, "PLAN")
        }

        // 7. The good deed, but only once the month is nearly gone — earlier
        //    than that it's a nag rather than information.
        if (Growth.daysLeftInMonth(today) <= 7 && !Growth.deedDoneThisMonth(store, today)) {
            candidates += BriefSignal("heart", "No good deed logged this month", "GROWTH")
        }

        return candidates.distinctBy { it.label }.take(MAX_SIGNALS)
    }

    // ---------------------------------------------------------------- review

    /**
     * The evening review.
     *
     * Structured as wins, slips and one thing to carry forward, because a
     * review that only lists failures gets closed and a review that only lists
     * wins gets ignored.
     */
    fun review(store: Store, day: Long = T.today()): ReviewDraft {
        val logs = store.activity.items.value.filter { it.epochDay == day }
        val completed = logs.filter { it.event == "COMPLETE" }
        val plan = Agenda.plan(store, day, nowMinutes = 0)
        val open = plan.allEntries.count { !it.done }
        val focus = store.focusMinutesOn(day)
        val lines = mutableListOf<ReviewLine>()

        val byDomain = completed.groupBy { it.domain }
        byDomain.entries.sortedByDescending { it.value.size }.take(4).forEach { (domain, rows) ->
            lines += ReviewLine("win", "${domainLabel(domain)}: ${rows.size} logged — ${rows.take(3).joinToString(", ") { it.title }}")
        }

        if (focus > 0) {
            lines += ReviewLine("win", "${Nlp.durationLabel(focus)} of focused time in ${logs.count { it.domain == "focus" }} session(s).")
        }

        val missedHabits = allPulses(store, day).filter {
            store.habitDueOn(it.habit, day) && !it.doneToday
        }
        if (missedHabits.isNotEmpty()) {
            lines += ReviewLine(
                "slip",
                "Habits still open: " + missedHabits.joinToString(", ") { it.habit.name } + "."
            )
        }

        val overdue = store.todos.items.value.filter {
            !it.done && (it.dueEpochDay ?: Long.MAX_VALUE) < day
        }
        if (overdue.isNotEmpty()) {
            lines += ReviewLine(
                "slip",
                "${overdue.size} task${if (overdue.size == 1) "" else "s"} carried past their date — oldest is \"${overdue.minByOrNull { it.dueEpochDay ?: 0 }?.title}\"."
            )
        }

        val plan7 = store.weekPlanFor(T.weekIndex(day))
        if (plan7 != null && plan7.priorities.isNotEmpty()) {
            val closed = plan7.priorities.count { it.done }
            lines += ReviewLine(
                if (closed == plan7.priorities.size) "win" else "note",
                "Week priorities: $closed of ${plan7.priorities.size} closed — " +
                    plan7.priorities.joinToString(", ") { (if (it.done) "✓ " else "") + it.title } + "."
            )
        }

        People.upcomingBirthdays(store, day, 3).forEach { status ->
            lines += ReviewLine("ahead", "${status.person.name}'s birthday in ${status.daysToBirthday} day(s).")
        }

        // End of the month brings two things forward that a daily review would
        // otherwise never surface, because neither moves on a daily scale.
        if (Growth.daysLeftInMonth(day) <= 5) {
            val unrated = Growth.unratedThisMonth(store, day)
            if (unrated.isNotEmpty()) {
                lines += ReviewLine(
                    "note",
                    "${Growth.shortMonthLabel(Growth.monthIndex(day))} ends in ${Growth.daysLeftInMonth(day)} day(s) — " +
                        "${unrated.size} growth area(s) still unrated."
                )
            }
            if (!Growth.deedDoneThisMonth(store, day)) {
                lines += ReviewLine(
                    "note",
                    "Nothing logged in the good-deeds column for ${Growth.shortMonthLabel(Growth.monthIndex(day))} yet."
                )
            }
        }

        val tomorrow = Agenda.plan(store, day + 1, nowMinutes = 0)
        val first = tomorrow.timed.firstOrNull { !it.done } ?: tomorrow.anytime.firstOrNull { !it.done }
        if (first != null) {
            val at = first.startMinutes?.let { " at ${Agenda.clockLabel(it)}" } ?: ""
            lines += ReviewLine("ahead", "Tomorrow opens with \"${first.title}\"$at.")
        }

        if (lines.none { it.kind == "win" }) {
            lines += ReviewLine(
                "note",
                "Nothing logged today. Days like this are data too — the streaks that survive them are the ones worth having."
            )
        }

        val headline = when {
            completed.size >= 8 -> "A heavy day — ${completed.size} things logged."
            completed.size >= 3 -> "${completed.size} logged, $open still open."
            completed.isNotEmpty() -> "A quiet ${completed.size}-item day."
            else -> "A blank day on the log."
        }

        val body = buildString {
            append("Review · ${T.dateLabel(day)}\n\n")
            lines.forEach { append("- ${it.text}\n") }
        }

        return ReviewDraft(
            day = day,
            headline = headline,
            lines = lines,
            doneCount = completed.size,
            openCount = open,
            focusMinutes = focus,
            journalBody = body
        )
    }

    fun reviewDue(store: Store, today: Long = T.today(), hour: Int = T.localHour()): Boolean {
        val s = store.settings()
        if (!s.dailyReviewOn) return false
        if (s.lastReviewedEpochDay >= today) return false
        return hour >= s.dailyReviewHour
    }

    // ------------------------------------------------------------- companion

    /**
     * One warm, factual line for the top of the day plan.
     *
     * Rotates by what's actually true rather than by a random pick from a list
     * of encouragements — a companion that says something accurate is worth
     * more than one that says something nice.
     */
    fun companionLine(store: Store, plan: DayPlan, today: Long = T.today()): String {
        val hour = T.localHour()
        val open = plan.allEntries.count { !it.done }
        val streaks = allPulses(store, today).filter { it.streak >= 3 && !it.doneToday }

        return when {
            plan.totalCount == 0 ->
                "Nothing on the board for ${T.dayNameFullOf(today)}. Capture something, or take the clear day."
            open == 0 ->
                "Everything on today's plan is done. ${plan.totalCount} item${if (plan.totalCount == 1) "" else "s"}, all closed."
            plan.clashes.isNotEmpty() ->
                "Two things are booked over each other — worth resolving before the day starts."
            streaks.isNotEmpty() && hour >= 17 -> {
                val h = streaks.maxByOrNull { it.streak }!!
                "${h.habit.name} is on a ${h.streak}-day run and still open tonight."
            }
            hour < 10 && plan.timed.isNotEmpty() -> {
                val next = plan.timed.firstOrNull { !it.done }
                if (next?.startMinutes != null) {
                    "First fixed point is ${next.title} at ${Agenda.clockLabel(next.startMinutes)}."
                } else {
                    "$open open, nothing pinned to a time — you choose the order."
                }
            }
            !plan.fits ->
                "More on the board than the day holds. Pick the two that matter."
            hour >= 20 ->
                "$open still open with the evening nearly gone. Close what you can, move the rest honestly."
            else ->
                "$open open · ${Nlp.durationLabel(plan.remainingMinutes)} of work left."
        }
    }

    private fun domainLabel(domain: String): String = when (domain) {
        "tasks" -> "Tasks"
        "habit" -> "Habits"
        "fitness" -> "Training"
        "practice" -> "Practice"
        "music" -> "Music"
        "faith" -> "Faith"
        "goals" -> "Goals"
        "journal" -> "Journal"
        "focus" -> "Focus"
        "people" -> "People"
        "growth" -> "Growth"
        "service" -> "Good deeds"
        else -> domain.replaceFirstChar { it.uppercase() }
    }
}
