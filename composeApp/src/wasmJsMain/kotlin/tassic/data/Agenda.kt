package tassic.data

/**
 * The day plan.
 *
 * Until now every commitment lived in its own list: timed tasks on Today,
 * exercises on Today, faith routines on Faith, practice in the Studio, habits
 * nowhere. Nothing anywhere answered the question a person actually asks in the
 * morning — *what does today look like, and does it fit?*
 *
 * This assembles one timeline out of all of them, and because it knows both the
 * clock position and the rough duration of each entry it can do the two things
 * a paper list can't: notice when two commitments collide, and tell you whether
 * what's left will fit in the hours you have.
 *
 * Everything here is derived. No new state, no duplicate rows — change a task's
 * time and the plan changes with it.
 */

/** One thing on the timeline. */
data class AgendaEntry(
    /** Stable within a day; used as a composition key. */
    val id: String,
    /** "task" | "habit" | "routine" | "workout" | "practice" | "event" | "birthday" */
    val kind: String,
    val title: String,
    val subtitle: String,
    /** Minutes since local midnight, or null for "anytime today". */
    val startMinutes: Int?,
    val durationMinutes: Int,
    val done: Boolean,
    val refId: Long,
    /** Accent key the UI maps to a colour: blue, green, amber, coral, violet. */
    val accent: String,
    /** URGENT/HIGH tasks sort above the rest inside the anytime list. */
    val priority: Priority = Priority.NORMAL,
    /**
     * Imported calendar events can't be ticked off here — Tassic is not the
     * system of record for someone else's invite, and a checkbox that silently
     * does nothing is worse than no checkbox.
     */
    val readOnly: Boolean = false
) {
    val endMinutes: Int? get() = startMinutes?.plus(durationMinutes)
}

/** A gap in the timed schedule big enough to be worth offering. */
data class FreeSlot(val startMinutes: Int, val endMinutes: Int) {
    val minutes: Int get() = endMinutes - startMinutes
}

/** Two timed entries that overlap. */
data class Clash(val first: AgendaEntry, val second: AgendaEntry)

data class DayPlan(
    val day: Long,
    val timed: List<AgendaEntry>,
    val anytime: List<AgendaEntry>,
    val clashes: List<Clash>,
    val freeSlots: List<FreeSlot>,
    /** Total estimated minutes still outstanding. */
    val remainingMinutes: Int,
    /** Free minutes left between now and the end of the waking day. */
    val availableMinutes: Int,
    val doneCount: Int,
    val totalCount: Int
) {
    val allEntries: List<AgendaEntry> get() = timed + anytime
    val fits: Boolean get() = remainingMinutes <= availableMinutes
    val progress: Float get() = if (totalCount == 0) 0f else doneCount.toFloat() / totalCount.toFloat()
}

/** One column of the week strip. */
data class DaySummary(
    val day: Long,
    val total: Int,
    val done: Int,
    val timedCount: Int,
    val loadMinutes: Int
) {
    val progress: Float get() = if (total == 0) 0f else done.toFloat() / total.toFloat()
}

object Agenda {

    /** Anything shorter than this isn't a usable working gap. */
    private const val MIN_SLOT = 20

    /** The window the planner treats as available for work, local minutes. */
    private const val DAY_START = 7 * 60
    private const val DAY_END = 22 * 60

    /** Fallback duration for an entry with no estimate. */
    private const val DEFAULT_TASK_MINUTES = 20

    // ------------------------------------------------------------------ build

    fun plan(store: Store, day: Long = T.today(), nowMinutes: Int = T.localMinuteOfDay()): DayPlan {
        val entries = mutableListOf<AgendaEntry>()
        val isToday = day == T.today()

        // ---- tasks ---------------------------------------------------------
        store.todos.items.value.forEach { todo ->
            val due = todo.dueEpochDay ?: return@forEach
            // Overdue work belongs on today's plan, not on the day it was
            // missed — a plan you can't act on is just a reprimand.
            val showsHere = due == day || (isToday && due < day && !todo.done)
            if (!showsHere) return@forEach
            val overdue = due < day
            entries += AgendaEntry(
                id = "task-${todo.id}",
                kind = "task",
                title = todo.title,
                subtitle = buildString {
                    if (overdue) append("Overdue · ${T.relativeDays(due, day)}")
                    if (todo.recurrence.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(store.recurrenceLabel(todo.recurrence))
                    }
                    if (todo.tags.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append(todo.tags.joinToString(", ") { "#$it" })
                    }
                },
                startMinutes = todo.dueTimeMinutes,
                durationMinutes = todo.estimateMinutes ?: DEFAULT_TASK_MINUTES,
                done = todo.done,
                refId = todo.id,
                accent = if (overdue) "coral" else "blue",
                priority = todo.priority
            )
        }

        // ---- habits ---------------------------------------------------------
        store.activeHabits().forEach { habit ->
            if (!store.habitDueOn(habit, day)) return@forEach
            val count = store.habitCount(habit.id, day)
            val target = habit.targetPerDay.coerceAtLeast(1)
            entries += AgendaEntry(
                id = "habit-${habit.id}",
                kind = "habit",
                title = habit.name,
                subtitle = if (target > 1) {
                    "$count of $target ${habit.unit}".trim()
                } else {
                    habitCadenceLabel(habit)
                },
                startMinutes = slotForTimeOfDay(habit.timeOfDay),
                durationMinutes = 15,
                done = count >= target,
                refId = habit.id,
                accent = habit.color
            )
        }

        // ---- faith routines --------------------------------------------------
        store.routines.items.value.forEach { routine ->
            if (!store.routineDueToday(routine, day) && (routine.lastDoneEpochDay ?: -1L) != day) return@forEach
            entries += AgendaEntry(
                id = "routine-${routine.id}",
                kind = "routine",
                title = routine.title,
                subtitle = "${routine.cadence} rhythm",
                startMinutes = if (routine.reminderOn) routine.reminderHour * 60 else null,
                durationMinutes = 30,
                done = (routine.lastDoneEpochDay ?: -1L) >= day,
                refId = routine.id,
                accent = "violet"
            )
        }

        // ---- training ---------------------------------------------------------
        store.workouts.items.value.forEach { workout ->
            if (!T.tagMatches(workout.dayTag, day)) return@forEach
            entries += AgendaEntry(
                id = "workout-${workout.id}",
                kind = "workout",
                title = workout.name,
                subtitle = "${workout.sets} × ${workout.reps} ${workout.unit}",
                startMinutes = null,
                durationMinutes = 10,
                done = workout.doneEpochDay == day,
                refId = workout.id,
                accent = "green"
            )
        }

        // ---- practice ----------------------------------------------------------
        store.practice.items.value
            .filter { it.kind == PracticeKind.SHAPE && T.tagMatches(it.dayTag, day) }
            .forEach { shape ->
                entries += AgendaEntry(
                    id = "practice-${shape.id}",
                    kind = "practice",
                    title = shape.title,
                    subtitle = shape.detail.ifBlank { "Shape of the day" },
                    startMinutes = null,
                    durationMinutes = 30,
                    done = shape.doneEpochDay == day,
                    refId = shape.id,
                    accent = "amber"
                )
            }

        // ---- imported calendar --------------------------------------------
        //
        // Without these the planner was confidently describing a free
        // afternoon that in fact held two meetings, and every judgement built
        // on top of it — what fits, where the gaps are, what clashes — was
        // being made on half the picture.
        if (store.settings().calendarOnPlan) {
            store.calendarEventsOn(day).forEach { event ->
                entries += AgendaEntry(
                    id = "event-${event.id}",
                    kind = "event",
                    title = event.title,
                    subtitle = listOf(event.location, Ics.freqLabel(event))
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    startMinutes = if (event.allDay) null else event.startMinutes,
                    // An event the user can't reschedule from here still costs
                    // time, so it blocks the day unless they've said otherwise.
                    durationMinutes = if (store.settings().calendarBlocksTime) {
                        event.durationMinutes.coerceAtLeast(15)
                    } else {
                        0
                    },
                    done = false,
                    refId = event.id,
                    accent = store.calendarColor(event.feedId),
                    readOnly = true
                )
            }
        }

        // ---- birthdays -------------------------------------------------------
        People.birthdaysOn(store, day).forEach { person ->
            entries += AgendaEntry(
                id = "birthday-${person.id}",
                kind = "birthday",
                title = "${person.name}'s birthday",
                subtitle = person.relationship,
                startMinutes = null,
                durationMinutes = 0,
                done = person.lastContactEpochDay >= day,
                refId = person.id,
                accent = "coral",
                readOnly = true
            )
        }

        val timed = entries.filter { it.startMinutes != null }
            .sortedWith(compareBy({ it.startMinutes ?: 0 }, { it.title }))
        val anytime = entries.filter { it.startMinutes == null }
            .sortedWith(compareBy({ it.done }, { it.priority.ordinal }, { it.title }))

        val clashes = findClashes(timed)
        val slots = freeSlots(timed, if (isToday) maxOf(nowMinutes, DAY_START) else DAY_START)

        // Imported events block the clock but are not *your* workload, and
        // birthdays are not tasks. Counting either as outstanding work would
        // make a day of back-to-back meetings read as an impossible to-do list,
        // and would stop the progress ring ever reaching full.
        val actionable = entries.filter { !it.readOnly }
        val remaining = actionable.filter { !it.done }.sumOf { it.durationMinutes }
        val available = slots.sumOf { it.minutes }

        return DayPlan(
            day = day,
            timed = timed,
            anytime = anytime,
            clashes = clashes,
            freeSlots = slots,
            remainingMinutes = remaining,
            availableMinutes = available,
            doneCount = actionable.count { it.done },
            totalCount = actionable.size
        )
    }

    /** Lightweight per-day counts for the week strip — avoids building 7 full plans. */
    fun week(store: Store, startDay: Long, days: Int = 7): List<DaySummary> =
        (0 until days).map { offset ->
            val day = startDay + offset
            val p = plan(store, day, nowMinutes = 0)
            DaySummary(
                day = day,
                total = p.totalCount,
                done = p.doneCount,
                timedCount = p.timed.size,
                loadMinutes = p.remainingMinutes
            )
        }

    /** Start of the Monday-aligned (or Sunday-aligned) week containing [day]. */
    fun weekStart(day: Long, mondayFirst: Boolean = true): Long {
        val dow = T.dowIndex(day) // 0 = Monday
        val offset = if (mondayFirst) dow else (dow + 1) % 7
        return day - offset
    }

    // --------------------------------------------------------------- analysis

    private fun findClashes(timed: List<AgendaEntry>): List<Clash> {
        val result = mutableListOf<Clash>()
        for (i in timed.indices) {
            val a = timed[i]
            val aStart = a.startMinutes ?: continue
            val aEnd = aStart + a.durationMinutes
            for (j in (i + 1) until timed.size) {
                val b = timed[j]
                val bStart = b.startMinutes ?: continue
                if (bStart >= aEnd) break // sorted, so nothing later can overlap
                if (a.done && b.done) continue
                result += Clash(a, b)
            }
        }
        return result
    }

    /**
     * Gaps between timed commitments from [from] to the end of the day.
     *
     * Deliberately conservative: only gaps of at least [MIN_SLOT] count, because
     * suggesting someone squeeze a task into eleven minutes between two
     * appointments is how planning tools lose credibility.
     */
    private fun freeSlots(timed: List<AgendaEntry>, from: Int): List<FreeSlot> {
        val busy = timed
            .filter { !it.done }
            .mapNotNull { entry ->
                val start = entry.startMinutes ?: return@mapNotNull null
                start to (start + entry.durationMinutes)
            }
            .sortedBy { it.first }

        val slots = mutableListOf<FreeSlot>()
        var cursor = from.coerceIn(DAY_START, DAY_END)
        busy.forEach { (start, end) ->
            if (start > cursor && start - cursor >= MIN_SLOT) {
                slots += FreeSlot(cursor, minOf(start, DAY_END))
            }
            if (end > cursor) cursor = end
        }
        if (DAY_END - cursor >= MIN_SLOT) slots += FreeSlot(cursor, DAY_END)
        return slots.filter { it.minutes >= MIN_SLOT && it.startMinutes < DAY_END }
    }

    /**
     * Greedily drops the outstanding anytime work into the free gaps, longest
     * task first. The output is a suggestion, never a write — nothing is
     * scheduled behind the user's back.
     */
    fun suggestPlacements(plan: DayPlan): List<Pair<AgendaEntry, Int>> {
        val slots = plan.freeSlots.map { it.startMinutes to it.endMinutes }.toMutableList()
        val placed = mutableListOf<Pair<AgendaEntry, Int>>()
        plan.anytime
            .filter { !it.done && !it.readOnly && it.durationMinutes > 0 }
            .sortedByDescending { it.durationMinutes }
            .forEach { entry ->
                val index = slots.indexOfFirst { (start, end) -> end - start >= entry.durationMinutes }
                if (index < 0) return@forEach
                val (start, end) = slots[index]
                placed += entry to start
                slots[index] = (start + entry.durationMinutes) to end
            }
        return placed.sortedBy { it.second }
    }

    // ----------------------------------------------------------------- labels

    fun clockLabel(minutes: Int): String {
        val h = ((minutes / 60) % 24).toString().padStart(2, '0')
        val m = (minutes % 60).toString().padStart(2, '0')
        return "$h:$m"
    }

    fun slotForTimeOfDay(timeOfDay: String): Int? = when (timeOfDay.uppercase()) {
        "MORNING" -> 8 * 60
        "AFTERNOON" -> 14 * 60
        "EVENING" -> 19 * 60
        else -> null
    }

    fun habitCadenceLabel(habit: Habit): String = when (habit.cadence.uppercase()) {
        "WEEKDAYS" -> "Weekdays"
        "WEEKEND" -> "Weekends"
        "WEEKLY_COUNT" -> "${habit.timesPerWeek}× a week"
        "CUSTOM" -> habit.daysOfWeek.sorted().joinToString(", ") { T.DAY_TAGS[it].lowercase().replaceFirstChar { c -> c.uppercase() } }
        else -> "Every day"
    }

    fun kindLabel(kind: String): String = when (kind) {
        "task" -> "Task"
        "habit" -> "Habit"
        "routine" -> "Rhythm"
        "workout" -> "Training"
        "practice" -> "Practice"
        "event" -> "Calendar"
        "birthday" -> "Birthday"
        else -> kind.replaceFirstChar { it.uppercase() }
    }
}

/**
 * Ticks whatever a timeline row actually represents.
 *
 * The plan deliberately flattens five different record types into one list, so
 * something has to put the tap back into the right table. Keeping that mapping
 * here rather than in the screen means a new entry kind is a one-line change in
 * two adjacent functions instead of a hunt through the UI.
 */
fun Store.toggleAgendaEntry(entry: AgendaEntry) {
    if (entry.readOnly) return
    when (entry.kind) {
        "task" -> todos.items.value.firstOrNull { it.id == entry.refId }?.let { toggleTodo(it) }
        "habit" -> habits.items.value.firstOrNull { it.id == entry.refId }?.let { habit ->
            if (habitDoneOn(habit, T.today())) untickHabit(habit) else tickHabit(habit)
        }
        "routine" -> routines.items.value.firstOrNull { it.id == entry.refId }?.let { completeRoutine(it) }
        "workout" -> workouts.items.value.firstOrNull { it.id == entry.refId }?.let { toggleWorkoutDone(it) }
        "practice" -> practice.items.value.firstOrNull { it.id == entry.refId }?.let { togglePracticeDone(it) }
    }
}
