package tassic.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import tassic.platform.AudioStore
import tassic.platform.MediaStore
import tassic.platform.lsGet
import tassic.platform.lsRemove
import tassic.platform.lsSet

/** Single app-wide store instance. */
object Graph {
    val store: Store by lazy { Store() }
}

/**
 * Reactive offline-first persistence engine.
 *
 * Architecture note: the canonical relational schema lives in
 * `src/commonMain/sqldelight/tassic/db` (*.sq files, compile-time verified by the
 * SQLDelight compiler plugin). At runtime on the web this store persists the
 * same typed rows to localStorage as JSON — synchronous, quota-safe for text,
 * with audio clips offloaded to IndexedDB ([AudioStore]). Swapping in the
 * SQLDelight WebWorkerDriver only requires re-pointing these CRUD methods at
 * the generated `TassicDatabase` queries; nothing in the UI layer changes.
 */
class Store() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    class Table<T : Identifiable>(
        val key: String,
        val listSerializer: KSerializer<List<T>>,
        val flow: MutableStateFlow<List<T>>
    ) {
        val items: StateFlow<List<T>> get() = flow
    }

    private fun <T> load(key: String, serializer: KSerializer<T>): List<T> {
        val raw = lsGet("tassic.$key") ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(serializer), raw)
        }.getOrDefault(emptyList())
    }

    private fun <T : Identifiable> persist(table: Table<T>) {
        lsSet("tassic.${table.key}", json.encodeToString(table.listSerializer, table.flow.value))
    }

    private fun <T : Identifiable> table(key: String, serializer: KSerializer<T>): Table<T> =
        Table(key, ListSerializer(serializer), MutableStateFlow(load(key, serializer)))

    // ---- Tables ----------------------------------------------------------

    val todos = table("todos", TodoItem.serializer())
    val goals = table("goals", GoalItem.serializer())
    val practice = table("practice", PracticeItem.serializer())
    val albums = table("albums", AlbumGoal.serializer())
    val recovery = table("recovery", RecoveryHabit.serializer())
    val habitLogs = table("habitLogs", HabitLog.serializer())
    val workouts = table("workouts", WorkoutItem.serializer())
    val workoutLogs = table("workoutLogs", WorkoutLog.serializer())
    val career = table("career", CareerItem.serializer())
    val journal = table("journal", JournalEntry.serializer())
    val prayers = table("prayers", PrayerPoint.serializer())
    val routines = table("routines", FaithRoutine.serializer())
    val wishlist = table("wishlist", WishItem.serializer())
    val habits = table("habits", Habit.serializer())
    val people = table("people", Person.serializer())
    val calendars = table("calendars", CalendarFeed.serializer())
    val calendarEvents = table("calendarEvents", CalendarEvent.serializer())
    val weekPlans = table("weekPlans", WeekPlan.serializer())
    val growth = table("growth", GrowthArea.serializer())
    val growthCheckins = table("growthCheckins", GrowthCheckin.serializer())
    val deeds = table("deeds", GoodDeed.serializer())
    val readingPlans = table("readingPlans", ReadingPlan.serializer())
    val verses = table("verses", MemoryVerse.serializer())
    val gratitude = table("gratitude", GratitudeItem.serializer())
    val activity = table("activity", ActivityLog.serializer())

    // ---- Settings ------------------------------------------------------------
    //
    // A singleton rather than a list, so it lives outside the Table machinery
    // but still behaves reactively for anything that observes it.

    private val settingsFlow = MutableStateFlow(loadSettings())
    val settingsState: StateFlow<Settings> get() = settingsFlow

    private fun loadSettings(): Settings {
        val raw = lsGet("tassic.settings") ?: return Settings()
        return runCatching { json.decodeFromString(Settings.serializer(), raw) }.getOrDefault(Settings())
    }

    fun settings(): Settings = settingsFlow.value

    fun saveSettings(next: Settings) {
        settingsFlow.value = next
        lsSet("tassic.settings", json.encodeToString(Settings.serializer(), next))
    }

    fun updateSettings(mutate: (Settings) -> Settings) = saveSettings(mutate(settingsFlow.value))

    // ---- Activity log --------------------------------------------------------

    /**
     * Records one completion event. Called from every toggle path so trends,
     * heatmaps and momentum have real history to read rather than inferring it
     * from `doneEpochDay`, which only ever holds the most recent tick.
     *
     * The log is capped so a long-lived install can't grow localStorage without
     * bound — a year of daily use is well inside the cap, and the analytics
     * windows never look back further than 28 days anyway.
     */
    fun logActivity(
        domain: String,
        title: String,
        refId: Long = 0,
        event: String = "COMPLETE",
        value: Int = 1
    ) {
        val now = T.now()
        insert(
            activity,
            ActivityLog(
                domain = domain,
                event = event,
                refId = refId,
                title = title,
                value = value,
                epochDay = T.today(),
                hour = T.localHour(),
                loggedAt = now
            )
        )
        val cap = 4000
        if (activity.flow.value.size > cap) {
            activity.flow.value = activity.flow.value.takeLast(cap - 500)
            persist(activity)
        }
    }

    /** Removes the most recent matching COMPLETE row when something is un-ticked. */
    private fun unlogActivity(domain: String, refId: Long) {
        val today = T.today()
        val match = activity.flow.value.lastOrNull {
            it.domain == domain && it.refId == refId && it.epochDay == today && it.event == "COMPLETE"
        } ?: return
        remove(activity, match.id)
    }

    // ---- Generic CRUD ------------------------------------------------------

    private fun <T : Identifiable> nextId(table: Table<T>): Long =
        (table.flow.value.maxOfOrNull { it.id } ?: 0L) + 1L

    fun <T : Identifiable> insert(table: Table<T>, item: T): T {
        item.id = nextId(table)
        table.flow.value = table.flow.value + item
        persist(table)
        return item
    }

    fun <T : Identifiable> replace(table: Table<T>, item: T) {
        table.flow.value = table.flow.value.map { if (it.id == item.id) item else it }
        persist(table)
    }

    fun <T : Identifiable> remove(table: Table<T>, id: Long) {
        table.flow.value = table.flow.value.filterNot { it.id == id }
        persist(table)
    }

    /**
     * Puts a removed row back with its original id.
     *
     * [insert] always allocates a new id, which is right for new rows and wrong
     * for an undo: anything referencing the old id — a task's `goalId`, a
     * habit's activity history — would be orphaned by a re-insert. Undo has to
     * restore the row that was there, not a copy of it.
     */
    fun <T : Identifiable> restoreItem(table: Table<T>, item: T) {
        if (table.flow.value.any { it.id == item.id }) return
        table.flow.value = (table.flow.value + item).sortedBy { it.id }
        persist(table)
    }

    // ---- Todos -------------------------------------------------------------

    fun addTodo(item: TodoItem) = insert(todos, item)
    fun updateTodo(item: TodoItem) = replace(todos, item)
    fun deleteTodo(id: Long) = remove(todos, id)

    /**
     * Checks a task off (or back on).
     *
     * Two things happen here that didn't before: the completion is timestamped
     * so completion-rate analytics are possible at all, and a repeating task
     * rolls its due date forward and stays open instead of disappearing.
     */
    fun toggleTodo(item: TodoItem) {
        if (item.done) {
            replace(todos, item.copy(done = false, completedAt = null))
            unlogActivity("tasks", item.id)
            syncGoalProgress(item.goalId)
            return
        }

        val nextDue = nextOccurrence(item)
        if (nextDue != null) {
            // Repeating: log the hit, then re-arm for the next occurrence.
            replace(
                todos,
                item.copy(
                    done = false,
                    completedAt = T.now(),
                    dueEpochDay = nextDue,
                    reminderFired = false,
                    snoozedUntilMs = null
                )
            )
        } else {
            replace(todos, item.copy(done = true, completedAt = T.now()))
        }
        logActivity("tasks", item.title, item.id)
        syncGoalProgress(item.goalId)
    }

    /** Toggles one step inside a task, and closes the task when the last step lands. */
    fun toggleSubtask(item: TodoItem, index: Int) {
        if (index !in item.subtasks.indices) return
        val next = item.subtasks.toMutableList()
        next[index] = next[index].copy(done = !next[index].done)
        val updated = item.copy(subtasks = next)
        replace(todos, updated)
        // Finishing every step is the same statement as ticking the task, so
        // don't make the user say it twice.
        if (!updated.done && next.isNotEmpty() && next.all { it.done }) toggleTodo(updated)
    }

    /** Tasks that serve a given goal, newest last. */
    fun tasksForGoal(goalId: Long): List<TodoItem> =
        todos.flow.value.filter { it.goalId == goalId }

    /**
     * Recomputes a goal's progress from its linked tasks.
     *
     * Only runs for goals with `autoProgress` on and at least one linked task —
     * a goal with a hand-set bar and no links must keep the number the user
     * typed, or turning the feature on would silently zero everything.
     */
    fun syncGoalProgress(goalId: Long?) {
        val id = goalId ?: return
        val goal = goals.flow.value.firstOrNull { it.id == id } ?: return
        if (!goal.autoProgress) return
        val linked = tasksForGoal(id)
        if (linked.isEmpty()) return
        val pct = (linked.count { it.done } * 100) / linked.size
        if (pct == goal.progress) return
        replace(goals, goal.copy(progress = pct))
    }

    /** Next due day for a repeating task, or null when it doesn't repeat. */
    fun nextOccurrence(item: TodoItem, from: Long = item.dueEpochDay ?: T.today()): Long? {
        val rule = item.recurrence.trim().uppercase()
        if (rule.isEmpty() || rule == "NONE") return null
        // Always land strictly in the future, even if the task was overdue.
        var next = from
        val today = T.today()
        do {
            next = when (rule) {
                "DAILY" -> next + 1
                "WEEKDAYS" -> {
                    var candidate = next + 1
                    while (T.dowIndex(candidate) >= 5) candidate += 1
                    candidate
                }
                "WEEKLY" -> next + 7
                "FORTNIGHTLY" -> next + 14
                "MONTHLY" -> next + 28
                else -> return null
            }
        } while (next <= today)
        return next
    }

    /** Human label for a recurrence rule, for chips and subtitles. */
    fun recurrenceLabel(rule: String): String = when (rule.trim().uppercase()) {
        "DAILY" -> "Every day"
        "WEEKDAYS" -> "Weekdays"
        "WEEKLY" -> "Every week"
        "FORTNIGHTLY" -> "Every 2 weeks"
        "MONTHLY" -> "Every 4 weeks"
        else -> "Does not repeat"
    }

    /** Pushes a reminder out by [minutes] and clears its fired flag. */
    fun snoozeTodo(id: Long, minutes: Int) {
        val item = todos.flow.value.firstOrNull { it.id == id } ?: return
        replace(
            todos,
            item.copy(
                snoozedUntilMs = T.localNow() + minutes * 60_000L,
                reminderFired = false
            )
        )
    }

    // ---- Goals -------------------------------------------------------------

    fun addGoal(item: GoalItem) = insert(goals, item)
    fun updateGoal(item: GoalItem) = replace(goals, item)
    fun deleteGoal(id: Long) = remove(goals, id)
    fun bumpGoal(item: GoalItem, delta: Int) {
        val next = (item.progress + delta).coerceIn(0, 100)
        if (next == item.progress) return
        replace(goals, item.copy(progress = next))
        logActivity("goals", item.title, item.id, event = "PROGRESS", value = delta)
    }

    // ---- Music practice ------------------------------------------------------

    fun addPractice(item: PracticeItem) = insert(practice, item)
    fun updatePractice(item: PracticeItem) = replace(practice, item)
    fun deletePractice(item: PracticeItem) {
        remove(practice, item.id)
        // cascade subtasks
        practice.flow.value.filter { it.parentId == item.id }.forEach { remove(practice, it.id) }
    }

    fun togglePracticeDone(item: PracticeItem) {
        val today = T.today()
        val turningOn = item.doneEpochDay != today
        replace(
            practice,
            item.copy(
                doneEpochDay = if (turningOn) today else null,
                doneCount = if (turningOn) item.doneCount + 1 else (item.doneCount - 1).coerceAtLeast(0)
            )
        )
        val domain = if (item.kind == PracticeKind.SONG || item.kind == PracticeKind.MODULE) "music" else "practice"
        if (turningOn) logActivity(domain, item.title, item.id) else unlogActivity(domain, item.id)
    }

    fun movePractice(item: PracticeItem, up: Boolean) {
        val group = practice.flow.value
            .filter {
                it.section == item.section && it.kind == item.kind && it.parentId == item.parentId
            }
            .sortedBy { it.sortOrder }
        val idx = group.indexOfFirst { it.id == item.id }
        val swap = if (up) idx - 1 else idx + 1
        if (idx < 0 || swap !in group.indices) return
        val a = group[idx]
        val b = group[swap]
        replace(practice, a.copy(sortOrder = b.sortOrder))
        replace(practice, b.copy(sortOrder = a.sortOrder))
    }

    fun currentModeRotation(): PracticeItem? {
        val modes = practice.flow.value
            .filter { it.kind == PracticeKind.MODE }
            .sortedBy { it.sortOrder }
        if (modes.isEmpty()) return null
        return modes[(T.dayOfYear(T.today()) - 1).mod(modes.size)]
    }

    fun focusKeyRotation(): PracticeItem? {
        val keys = practice.flow.value
            .filter { it.kind == PracticeKind.KEY }
            .sortedBy { it.sortOrder }
        if (keys.isEmpty()) return null
        return keys[T.dayOfYear(T.today()).mod(keys.size)]
    }

    fun songsLearnedThisWeek(): Int {
        val week = T.weekIndex(T.today())
        return practice.flow.value.count { p ->
            val done = p.doneEpochDay
            p.kind == PracticeKind.SONG && done != null && T.weekIndex(done) == week
        }
    }

    fun cagedShapeForToday(): PracticeItem? =
        practice.flow.value
            .filter { it.kind == PracticeKind.SHAPE && it.section == "guitar" }
            .firstOrNull { T.tagMatches(it.dayTag, T.today()) }

    // ---- Albums --------------------------------------------------------------

    fun addAlbum(item: AlbumGoal) = insert(albums, item)
    fun updateAlbum(item: AlbumGoal) = replace(albums, item)
    fun deleteAlbum(id: Long) = remove(albums, id)
    fun bumpAlbum(item: AlbumGoal, delta: Int) {
        val next = (item.learnedTracks + delta).coerceIn(0, item.totalTracks)
        if (next == item.learnedTracks) return
        replace(albums, item.copy(learnedTracks = next))
        if (delta > 0) logActivity("music", item.album, item.id, value = delta)
    }

    // ---- Recovery --------------------------------------------------------------

    fun addRecoveryHabit(item: RecoveryHabit) = insert(recovery, item)
    fun updateRecoveryHabit(item: RecoveryHabit) = replace(recovery, item)
    fun deleteRecoveryHabit(id: Long) {
        remove(recovery, id)
        habitLogs.flow.value.filter { it.habitId == id }.forEach { remove(habitLogs, it.id) }
    }

    fun daysClean(habit: RecoveryHabit, today: Long = T.today()): Int =
        (today - habit.startEpochDay).toInt() + 1

    fun logRelapse(habit: RecoveryHabit, triggerNote: String) {
        val today = T.today()
        val streak = daysClean(habit, today)
        replace(
            recovery,
            habit.copy(
                startEpochDay = today,
                bestStreak = maxOf(habit.bestStreak, streak),
                relapses = habit.relapses + 1
            )
        )
        insert(
            habitLogs,
            HabitLog(habitId = habit.id, event = "RELAPSE", triggerNote = triggerNote, loggedAt = T.now())
        )
        logActivity("recovery", habit.name, habit.id, event = "RELAPSE", value = streak)
    }

    fun relapseLogsFor(habitId: Long): List<HabitLog> =
        habitLogs.flow.value.filter { it.habitId == habitId && it.event == "RELAPSE" }
            .sortedByDescending { it.loggedAt }

    // ---- Workouts ----------------------------------------------------------------

    fun addWorkout(item: WorkoutItem) = insert(workouts, item)
    fun updateWorkout(item: WorkoutItem) = replace(workouts, item)
    fun deleteWorkout(id: Long) = remove(workouts, id)

    fun toggleWorkoutDone(item: WorkoutItem) {
        val today = T.today()
        if (item.doneEpochDay == today) {
            replace(workouts, item.copy(doneEpochDay = null))
            unlogActivity("fitness", item.id)
        } else {
            replace(workouts, item.copy(doneEpochDay = today))
            insert(
                workoutLogs,
                WorkoutLog(name = item.name, sets = item.sets, reps = item.reps, unit = item.unit, loggedAt = T.now())
            )
            logActivity("fitness", item.name, item.id, value = item.sets * item.reps)
        }
    }

    /** Consecutive-day workout streak (today or yesterday counts as alive). */
    fun workoutStreak(): Int {
        // T.dayOf() (local calendar day) rather than a raw UTC division, so the
        // streak lines up with the same "today" the rest of the UI shows.
        val days = workoutLogs.flow.value.map { T.dayOf(it.loggedAt) }.distinct().sortedDescending()
        if (days.isEmpty()) return 0
        var expected = T.today()
        if (days.first() == expected - 1) expected -= 1
        var streak = 0
        for (d in days) {
            if (d == expected) {
                streak++
                expected--
            } else break
        }
        return streak
    }

    fun workoutsDueToday(): List<WorkoutItem> =
        workouts.flow.value.filter { T.tagMatches(it.dayTag, T.today()) }

    // ---- Career -----------------------------------------------------------------

    fun addCareer(item: CareerItem) = insert(career, item)
    fun updateCareer(item: CareerItem) = replace(career, item)
    fun deleteCareer(id: Long) = remove(career, id)
    fun toggleCareer(item: CareerItem) {
        replace(career, item.copy(done = !item.done))
        if (!item.done) logActivity("goals", item.title, item.id) else unlogActivity("goals", item.id)
    }

    fun moveCareer(item: CareerItem, up: Boolean) {
        val group = career.flow.value
            .filter { it.path == item.path && it.stage == item.stage }
            .sortedBy { it.sortOrder }
        val idx = group.indexOfFirst { it.id == item.id }
        val swap = if (up) idx - 1 else idx + 1
        if (idx < 0 || swap !in group.indices) return
        val a = group[idx]
        val b = group[swap]
        replace(career, a.copy(sortOrder = b.sortOrder))
        replace(career, b.copy(sortOrder = a.sortOrder))
    }

    fun careerPaths(): List<String> =
        career.flow.value.map { it.path }.distinct().ifEmpty { listOf("GeoDev Roadmap") }

    // ---- Journal -------------------------------------------------------------------

    fun addJournal(item: JournalEntry): JournalEntry {
        val saved = insert(journal, item)
        logActivity("journal", item.title.ifBlank { "Entry" }, saved.id, value = item.mood)
        return saved
    }

    /**
     * Replaces an entry, cleaning up any media it no longer points at.
     *
     * Both the clip and the photo live in IndexedDB keyed by id, so swapping
     * either one without deleting the old blob leaks storage that nothing will
     * ever reference again.
     */
    fun updateJournal(item: JournalEntry, previousAudioId: String?, previousImageId: String? = null) {
        if (previousAudioId != null && previousAudioId != item.audioId) {
            AudioStore.delete(previousAudioId) // fire-and-forget cleanup
        }
        if (previousImageId != null && previousImageId != item.imageId) {
            MediaStore.delete(previousImageId)
        }
        replace(journal, item)
    }

    fun deleteJournal(item: JournalEntry) {
        item.audioId?.let { AudioStore.delete(it) } // fire-and-forget cleanup
        item.imageId?.let { MediaStore.delete(it) }
        remove(journal, item.id)
    }

    // ---- Prayer ----------------------------------------------------------------------

    fun addPrayer(item: PrayerPoint) = insert(prayers, item)
    fun updatePrayer(item: PrayerPoint) = replace(prayers, item)
    fun deletePrayer(id: Long) = remove(prayers, id)
    fun setPrayerAnswered(item: PrayerPoint, answered: Boolean) {
        replace(prayers, item.copy(answered = answered, answeredAt = if (answered) T.now() else null))
        if (answered) logActivity("faith", item.title, item.id, event = "ANSWERED")
    }

    // ---- Faith routines -----------------------------------------------------------------

    fun addRoutine(item: FaithRoutine) = insert(routines, item)
    fun updateRoutine(item: FaithRoutine) = replace(routines, item)
    fun deleteRoutine(id: Long) = remove(routines, id)
    fun completeRoutine(item: FaithRoutine) {
        replace(routines, item.copy(lastDoneEpochDay = T.today(), timesCompleted = item.timesCompleted + 1))
        logActivity("faith", item.title, item.id)
    }

    fun routineDueToday(item: FaithRoutine, today: Long = T.today()): Boolean {
        val last = item.lastDoneEpochDay
        if (last != null && last >= today) return false
        return when (item.cadence.lowercase()) {
            "weekly" -> T.tagMatches(item.dayTag, today)
            "monthly" -> last == null || today - last >= 28
            else -> true // Daily & custom cadences
        }
    }

    // ---- Wishlist -------------------------------------------------------------------------

    fun addWish(item: WishItem) = insert(wishlist, item)
    fun updateWish(item: WishItem) = replace(wishlist, item)
    fun deleteWish(id: Long) = remove(wishlist, id)
    fun toggleWishPurchased(item: WishItem) = replace(wishlist, item.copy(purchased = !item.purchased))

    // ---- Habits ---------------------------------------------------------------------------
    //
    // Ticks live in the activity log rather than a habit_ticks table. That
    // sounds like a shortcut and isn't: it means a habit tick is the same kind
    // of event as a finished task or a logged workout, so momentum, the
    // heatmap and the hour-of-day analysis all pick habits up for free instead
    // of needing a second code path each.

    fun addHabit(item: Habit) = insert(habits, item)
    fun updateHabit(item: Habit) = replace(habits, item)
    fun deleteHabit(id: Long) = remove(habits, id)

    fun activeHabits(): List<Habit> =
        habits.flow.value.filter { !it.archived }.sortedBy { it.sortOrder }

    /** Whether this habit is expected on [day] at all. */
    fun habitDueOn(habit: Habit, day: Long): Boolean {
        val dow = T.dowIndex(day)
        return when (habit.cadence.uppercase()) {
            "WEEKDAYS" -> dow < 5
            "WEEKEND" -> dow >= 5
            "CUSTOM" -> habit.daysOfWeek.isEmpty() || habit.daysOfWeek.contains(dow)
            else -> true // DAILY and WEEKLY_COUNT are both open every day
        }
    }

    /** Reps logged for a habit on a given day. */
    fun habitCount(habitId: Long, day: Long): Int =
        activity.flow.value
            .filter { it.domain == "habit" && it.refId == habitId && it.epochDay == day && it.event == "COMPLETE" }
            .sumOf { it.value }

    fun habitDoneOn(habit: Habit, day: Long): Boolean =
        habitCount(habit.id, day) >= habit.targetPerDay.coerceAtLeast(1)

    /** Adds one rep. Repeat-target habits ("8 glasses") just get ticked more than once. */
    fun tickHabit(habit: Habit) {
        logActivity("habit", habit.name, habit.id, value = 1)
    }

    /** Removes the most recent rep logged today. */
    fun untickHabit(habit: Habit) {
        val today = T.today()
        val match = activity.flow.value.lastOrNull {
            it.domain == "habit" && it.refId == habit.id && it.epochDay == today && it.event == "COMPLETE"
        } ?: return
        remove(activity, match.id)
    }

    /** Days this habit was kept in the Monday-aligned week containing [day]. */
    fun habitWeekCount(habit: Habit, day: Long = T.today()): Int {
        val week = T.weekIndex(day)
        return (0..6).count { back ->
            val d = day - back
            T.weekIndex(d) == week && habitDoneOn(habit, d)
        }
    }

    /**
     * Consecutive kept streak, counted over the days the habit was actually
     * *due*. A weekday habit shouldn't lose its streak over the weekend, and a
     * habit that's simply not due today shouldn't read as broken either.
     */
    fun habitStreak(habit: Habit, today: Long = T.today()): Int {
        if (habit.cadence.uppercase() == "WEEKLY_COUNT") {
            var weeks = 0
            var cursor = today
            while (weeks < 260) {
                val kept = habitWeekCount(habit, cursor)
                val target = habit.timesPerWeek.coerceAtLeast(1)
                // The running week only breaks a streak once it can no longer
                // reach the target, not the moment it's short.
                val isCurrentWeek = T.weekIndex(cursor) == T.weekIndex(today)
                if (kept >= target) weeks++
                else if (isCurrentWeek) { /* still in play — don't count, don't break */ }
                else break
                cursor -= 7
            }
            return weeks
        }

        var streak = 0
        var cursor = today
        var scanned = 0
        // Today counts only once it's kept; an unkept today doesn't break a
        // streak that's still alive until midnight.
        if (habitDueOn(habit, cursor) && !habitDoneOn(habit, cursor)) cursor -= 1
        while (scanned < 400) {
            scanned++
            if (!habitDueOn(habit, cursor)) { cursor -= 1; continue }
            if (habitDoneOn(habit, cursor)) { streak++; cursor -= 1 } else break
        }
        return streak
    }

    /** Kept/not-kept history for the last [days] days, oldest first. Null = not due. */
    fun habitHistory(habit: Habit, days: Int = 28, today: Long = T.today()): List<Boolean?> =
        (0 until days).map { back ->
            val day = today - (days - 1 - back)
            if (!habitDueOn(habit, day)) null else habitDoneOn(habit, day)
        }

    fun habitsDueToday(today: Long = T.today()): List<Habit> =
        activeHabits().filter { habitDueOn(it, today) }

    // ---- Focus sessions & check-ins -------------------------------------------------------

    /** Records a completed focus block. [minutes] is the magnitude, so it sums. */
    fun logFocusSession(minutes: Int, label: String) {
        if (minutes <= 0) return
        logActivity("focus", label.ifBlank { "Focus session" }, event = "FOCUS", value = minutes)
    }

    /** Minutes focused on a given day. */
    fun focusMinutesOn(day: Long): Int =
        activity.flow.value.filter { it.domain == "focus" && it.epochDay == day }.sumOf { it.value }

    /** A one-tap mood/energy reading, separate from a written journal entry. */
    fun logCheckIn(mood: Int, energy: Int) {
        logActivity("mood", "Check-in", event = "CHECKIN", value = mood.coerceIn(1, 5))
        logActivity("energy", "Check-in", event = "CHECKIN", value = energy.coerceIn(1, 5))
    }

    fun checkedInToday(today: Long = T.today()): Boolean =
        activity.flow.value.any { it.domain == "mood" && it.epochDay == today }

    // ---- Backup ---------------------------------------------------------------------------
    //
    // Everything lives in localStorage, which a browser is free to evict, a
    // "clear site data" tap wipes instantly, and no reinstall preserves. A life
    // tracker with no way to get the data out is a life tracker that will
    // eventually lose it.

    private fun <T : Identifiable> dump(t: Table<T>): String =
        json.encodeToString(t.listSerializer, t.flow.value)

    /** Whole-database snapshot as a single JSON document. */
    fun exportJson(): String {
        val tables = listOf(
            "todos" to dump(todos),
            "goals" to dump(goals),
            "practice" to dump(practice),
            "albums" to dump(albums),
            "recovery" to dump(recovery),
            "habitLogs" to dump(habitLogs),
            "workouts" to dump(workouts),
            "workoutLogs" to dump(workoutLogs),
            "career" to dump(career),
            "journal" to dump(journal),
            "prayers" to dump(prayers),
            "routines" to dump(routines),
            "wishlist" to dump(wishlist),
            "habits" to dump(habits),
            "people" to dump(people),
            "calendars" to dump(calendars),
            "calendarEvents" to dump(calendarEvents),
            "weekPlans" to dump(weekPlans),
            "growth" to dump(growth),
            "growthCheckins" to dump(growthCheckins),
            "deeds" to dump(deeds),
            "readingPlans" to dump(readingPlans),
            "verses" to dump(verses),
            "gratitude" to dump(gratitude),
            "activity" to dump(activity)
        )
        val body = tables.joinToString(",") { (k, v) -> "\"$k\":$v" }
        val settingsJson = json.encodeToString(Settings.serializer(), settings())
        return "{\"format\":\"tassic-backup\",\"version\":3," +
            "\"exportedAt\":${T.now()},\"settings\":$settingsJson,$body}"
    }

    private fun <T : Identifiable> restore(t: Table<T>, root: JsonObject, key: String, merge: Boolean) {
        val element = root[key] ?: return
        val incoming = runCatching {
            json.decodeFromJsonElement(t.listSerializer, element)
        }.getOrNull() ?: return
        t.flow.value = if (!merge) {
            incoming
        } else {
            // Merge keeps whatever is already here and re-ids the incoming rows
            // so two devices' data can be combined without silent overwrites.
            var next = (t.flow.value.maxOfOrNull { it.id } ?: 0L)
            val shifted = incoming.map { row -> row.also { next += 1; it.id = next } }
            t.flow.value + shifted
        }
        persist(t)
    }

    /**
     * Restores a snapshot. Returns the number of rows brought in, or null when
     * the file isn't a Tassic backup — worth distinguishing, because "0 rows"
     * and "wrong file" need different words in the UI.
     */
    fun importJson(raw: String, merge: Boolean): Int? {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val format = (root["format"] as? JsonPrimitive)?.contentOrNull
        if (format != "tassic-backup") return null

        restore(todos, root, "todos", merge)
        restore(goals, root, "goals", merge)
        restore(practice, root, "practice", merge)
        restore(albums, root, "albums", merge)
        restore(recovery, root, "recovery", merge)
        restore(habitLogs, root, "habitLogs", merge)
        restore(workouts, root, "workouts", merge)
        restore(workoutLogs, root, "workoutLogs", merge)
        restore(career, root, "career", merge)
        restore(journal, root, "journal", merge)
        restore(prayers, root, "prayers", merge)
        restore(routines, root, "routines", merge)
        restore(wishlist, root, "wishlist", merge)
        restore(habits, root, "habits", merge)
        restore(people, root, "people", merge)
        restore(calendars, root, "calendars", merge)
        restore(calendarEvents, root, "calendarEvents", merge)
        restore(weekPlans, root, "weekPlans", merge)
        restore(growth, root, "growth", merge)
        restore(growthCheckins, root, "growthCheckins", merge)
        restore(deeds, root, "deeds", merge)
        restore(readingPlans, root, "readingPlans", merge)
        restore(verses, root, "verses", merge)
        restore(gratitude, root, "gratitude", merge)
        restore(activity, root, "activity", merge)

        if (!merge) {
            root["settings"]?.let { element ->
                runCatching { json.decodeFromJsonElement(Settings.serializer(), element) }
                    .getOrNull()?.let { saveSettings(it) }
            }
        }
        metaSet("seeded", "restored")
        return countRows()
    }

    fun countRows(): Int =
        todos.flow.value.size + goals.flow.value.size + practice.flow.value.size +
            albums.flow.value.size + recovery.flow.value.size + habitLogs.flow.value.size +
            workouts.flow.value.size + workoutLogs.flow.value.size + career.flow.value.size +
            journal.flow.value.size + prayers.flow.value.size + routines.flow.value.size +
            wishlist.flow.value.size + habits.flow.value.size + people.flow.value.size +
            calendars.flow.value.size + calendarEvents.flow.value.size +
            weekPlans.flow.value.size + growth.flow.value.size +
            growthCheckins.flow.value.size + deeds.flow.value.size +
            readingPlans.flow.value.size + verses.flow.value.size +
            gratitude.flow.value.size + activity.flow.value.size

    /** Wipes every table and the seed marker. The caller is responsible for confirming. */
    fun eraseAll() {
        todos.flow.value = emptyList(); persist(todos)
        goals.flow.value = emptyList(); persist(goals)
        practice.flow.value = emptyList(); persist(practice)
        albums.flow.value = emptyList(); persist(albums)
        recovery.flow.value = emptyList(); persist(recovery)
        habitLogs.flow.value = emptyList(); persist(habitLogs)
        workouts.flow.value = emptyList(); persist(workouts)
        workoutLogs.flow.value = emptyList(); persist(workoutLogs)
        career.flow.value = emptyList(); persist(career)
        journal.flow.value = emptyList(); persist(journal)
        prayers.flow.value = emptyList(); persist(prayers)
        routines.flow.value = emptyList(); persist(routines)
        wishlist.flow.value = emptyList(); persist(wishlist)
        habits.flow.value = emptyList(); persist(habits)
        people.flow.value = emptyList(); persist(people)
        calendars.flow.value = emptyList(); persist(calendars)
        calendarEvents.flow.value = emptyList(); persist(calendarEvents)
        weekPlans.flow.value = emptyList(); persist(weekPlans)
        growth.flow.value = emptyList(); persist(growth)
        growthCheckins.flow.value = emptyList(); persist(growthCheckins)
        deeds.flow.value = emptyList(); persist(deeds)
        readingPlans.flow.value = emptyList(); persist(readingPlans)
        verses.flow.value = emptyList(); persist(verses)
        gratitude.flow.value = emptyList(); persist(gratitude)
        activity.flow.value = emptyList(); persist(activity)
        metaClear("seeded")
    }

    // ---- People ---------------------------------------------------------------------------

    fun addPerson(item: Person) = insert(people, item)
    fun updatePerson(item: Person) = replace(people, item)
    fun deletePerson(id: Long) = remove(people, id)

    /**
     * Records that you spoke to someone today.
     *
     * Writes to the activity log as well, so time spent on relationships counts
     * toward momentum the same way a finished task does. It is at least as
     * valid a use of a day.
     */
    fun logContact(person: Person, note: String = "") {
        val today = T.today()
        replace(
            people,
            person.copy(
                lastContactEpochDay = today,
                notes = if (note.isBlank()) person.notes else (note.trim() + "\n" + person.notes).trim()
            )
        )
        logActivity("people", person.name, person.id, event = "CONTACT")
    }

    // ---- Calendar feeds ---------------------------------------------------------------------

    fun addCalendar(item: CalendarFeed) = insert(calendars, item)
    fun updateCalendar(item: CalendarFeed) = replace(calendars, item)

    /** Removes a feed and everything it brought in. */
    fun deleteCalendar(id: Long) {
        calendarEvents.flow.value = calendarEvents.flow.value.filterNot { it.feedId == id }
        persist(calendarEvents)
        remove(calendars, id)
    }

    /**
     * Parses an .ics document into a feed, replacing whatever that feed held
     * before.
     *
     * Replace rather than merge: a calendar export is a snapshot, so merging
     * would resurrect events the user has since deleted upstream. Returns the
     * event count, or null when the text isn't a calendar at all.
     */
    fun importIcs(feedId: Long, raw: String): Int? {
        val feed = calendars.flow.value.firstOrNull { it.id == feedId } ?: return null
        val result = Ics.parse(raw, feedId)
        if (result.events.isEmpty()) return null

        calendarEvents.flow.value = calendarEvents.flow.value.filterNot { it.feedId == feedId }
        var nextId = (calendarEvents.flow.value.maxOfOrNull { it.id } ?: 0L)
        val incoming = result.events.map { event ->
            nextId += 1
            event.also { it.id = nextId }
        }
        calendarEvents.flow.value = calendarEvents.flow.value + incoming
        persist(calendarEvents)

        replace(
            calendars,
            feed.copy(
                name = feed.name.ifBlank { result.calendarName.ifBlank { "Calendar" } },
                lastSyncedAt = T.now(),
                eventCount = incoming.size
            )
        )
        return incoming.size
    }

    /** Imported events falling on a given day, from enabled feeds only. */
    fun calendarEventsOn(day: Long): List<CalendarEvent> {
        val enabled = calendars.flow.value.filter { it.enabled }.map { it.id }.toSet()
        if (enabled.isEmpty()) return emptyList()
        return calendarEvents.flow.value
            .filter { it.feedId in enabled && Ics.occursOn(it, day) }
            .sortedBy { it.startMinutes ?: 0 }
    }

    fun calendarColor(feedId: Long): String =
        calendars.flow.value.firstOrNull { it.id == feedId }?.color ?: "violet"

    // ---- Week plans -------------------------------------------------------------------------

    fun weekPlanFor(weekIndex: Long): WeekPlan? =
        weekPlans.flow.value.firstOrNull { it.weekIndex == weekIndex }

    /** Creates or updates the plan for a week, keeping at most three priorities. */
    fun saveWeekPlan(weekIndex: Long, priorities: List<Intention>, notes: String) {
        val trimmed = priorities.filter { it.title.isNotBlank() }.take(3)
        val existing = weekPlanFor(weekIndex)
        if (existing == null) {
            insert(
                weekPlans,
                WeekPlan(weekIndex = weekIndex, priorities = trimmed, notes = notes, createdAt = T.now())
            )
        } else {
            replace(weekPlans, existing.copy(priorities = trimmed, notes = notes))
        }
    }

    fun toggleIntention(weekIndex: Long, index: Int) {
        val plan = weekPlanFor(weekIndex) ?: return
        if (index !in plan.priorities.indices) return
        val next = plan.priorities.toMutableList()
        next[index] = next[index].copy(done = !next[index].done)
        replace(weekPlans, plan.copy(priorities = next))
        if (next[index].done) logActivity("goals", next[index].title, plan.id, event = "INTENTION")
    }

    // ---- Growth & good deeds ----------------------------------------------------------------

    fun addGrowthArea(item: GrowthArea) = insert(growth, item)
    fun updateGrowthArea(item: GrowthArea) = replace(growth, item)

    /** Removes an area and the monthly ratings that only make sense alongside it. */
    fun deleteGrowthArea(id: Long) {
        growthCheckins.flow.value = growthCheckins.flow.value.filterNot { it.areaId == id }
        persist(growthCheckins)
        remove(growth, id)
    }

    fun activeGrowthAreas(): List<GrowthArea> =
        growth.flow.value.filter { !it.archived }.sortedBy { it.sortOrder }

    /**
     * Records this month's honest rating for an area, replacing any earlier one
     * for the same month.
     *
     * Replace rather than append: a month has one answer, and keeping a
     * revision history of how you felt about your own patience on the 3rd
     * versus the 27th would be data nobody benefits from reading.
     */
    fun rateGrowthArea(areaId: Long, monthIndex: Long, rating: Int, note: String) {
        val existing = growthCheckins.flow.value.firstOrNull {
            it.areaId == areaId && it.monthIndex == monthIndex
        }
        if (existing == null) {
            insert(
                growthCheckins,
                GrowthCheckin(
                    areaId = areaId,
                    monthIndex = monthIndex,
                    rating = rating.coerceIn(1, 5),
                    note = note.trim(),
                    createdAt = T.now()
                )
            )
        } else {
            replace(growthCheckins, existing.copy(rating = rating.coerceIn(1, 5), note = note.trim()))
        }
        val area = growth.flow.value.firstOrNull { it.id == areaId }
        logActivity("growth", area?.name ?: "Growth area", areaId, event = "REVIEW", value = rating)
    }

    fun growthNoteFor(areaId: Long, monthIndex: Long): String =
        growthCheckins.flow.value.firstOrNull { it.areaId == areaId && it.monthIndex == monthIndex }?.note ?: ""

    fun addDeed(item: GoodDeed): GoodDeed {
        val saved = insert(deeds, item)
        logActivity("service", item.title, saved.id, event = "DEED")
        // A deed for someone already tracked also counts as being in touch —
        // recording it twice by hand would be busywork.
        item.personId?.let { id ->
            people.flow.value.firstOrNull { it.id == id }?.let { person ->
                replace(people, person.copy(lastContactEpochDay = T.today()))
            }
        }
        return saved
    }

    fun updateDeed(item: GoodDeed) = replace(deeds, item)
    fun deleteDeed(id: Long) = remove(deeds, id)

    // ---- Scripture reading ------------------------------------------------------------------

    /** Starts a plan from a template, generating its daily references once. */
    fun startReadingPlan(template: Bible.PlanTemplate): ReadingPlan {
        // Only one plan runs at a time. Two half-finished plans is the state
        // people are actually in when they say reading plans don't work.
        readingPlans.flow.value = readingPlans.flow.value.map { it.copy(active = false) }
        persist(readingPlans)
        return insert(
            readingPlans,
            ReadingPlan(
                name = template.name,
                templateKey = template.key,
                days = Bible.buildPlan(template),
                startEpochDay = T.today(),
                createdAt = T.now()
            )
        )
    }

    fun activeReadingPlan(): ReadingPlan? = readingPlans.flow.value.firstOrNull { it.active }

    /**
     * Which day of the plan today is — by elapsed days, not by progress.
     *
     * Showing the day you're *due* rather than the next unread one is the
     * honest version: it lets the app say "you're four days behind" instead of
     * silently pretending a month-old plan started this morning.
     */
    fun readingDayIndex(plan: ReadingPlan, today: Long = T.today()): Int =
        (today - plan.startEpochDay).toInt().coerceIn(0, (plan.days.size - 1).coerceAtLeast(0))

    fun readingBehind(plan: ReadingPlan, today: Long = T.today()): Int {
        val due = readingDayIndex(plan, today) + 1
        return (due - plan.completedDays.size).coerceAtLeast(0)
    }

    fun toggleReadingDay(plan: ReadingPlan, index: Int) {
        val next = if (plan.completedDays.contains(index)) {
            plan.completedDays - index
        } else {
            plan.completedDays + index
        }
        replace(readingPlans, plan.copy(completedDays = next.sorted()))
        if (!plan.completedDays.contains(index)) {
            logActivity("faith", plan.days.getOrElse(index) { plan.name }, plan.id, event = "READ")
        }
    }

    fun deleteReadingPlan(id: Long) = remove(readingPlans, id)

    // ---- Memory verses ----------------------------------------------------------------------

    /** Leitner intervals in days, one per box. */
    private val BOX_INTERVALS = listOf(1, 3, 7, 21, 60)

    fun addVerse(item: MemoryVerse) = insert(
        verses,
        item.copy(nextReviewEpochDay = T.today(), createdAt = T.now())
    )

    fun updateVerse(item: MemoryVerse) = replace(verses, item)
    fun deleteVerse(id: Long) = remove(verses, id)

    fun versesDue(today: Long = T.today()): List<MemoryVerse> =
        verses.flow.value.filter { it.nextReviewEpochDay <= today }.sortedBy { it.nextReviewEpochDay }

    /**
     * Records a review.
     *
     * A miss drops straight back to box one rather than down a single step.
     * That is the point of the Leitner system: a verse you couldn't recall is
     * not "slightly less learned", it's unlearned, and spacing it out again on
     * a three-week interval would just hide that.
     */
    fun reviewVerse(verse: MemoryVerse, remembered: Boolean) {
        val today = T.today()
        val box = if (remembered) (verse.box + 1).coerceAtMost(BOX_INTERVALS.size) else 1
        val interval = BOX_INTERVALS[(box - 1).coerceIn(0, BOX_INTERVALS.lastIndex)]
        replace(
            verses,
            verse.copy(
                box = box,
                nextReviewEpochDay = today + interval,
                lastReviewedEpochDay = today,
                reviewCount = verse.reviewCount + 1,
                correctCount = verse.correctCount + if (remembered) 1 else 0
            )
        )
        logActivity("faith", verse.reference, verse.id, event = "MEMORISE", value = if (remembered) 1 else 0)
    }

    // ---- Gratitude --------------------------------------------------------------------------

    fun addGratitude(text: String, day: Long = T.today()) {
        if (text.isBlank()) return
        insert(gratitude, GratitudeItem(text = text.trim(), epochDay = day, createdAt = T.now()))
        logActivity("faith", text.trim().take(40), event = "GRATITUDE")
    }

    fun gratitudeOn(day: Long = T.today()): List<GratitudeItem> =
        gratitude.flow.value.filter { it.epochDay == day }.sortedBy { it.createdAt }

    fun deleteGratitude(id: Long) = remove(gratitude, id)

    // ---- Prayer -----------------------------------------------------------------------------

    /** Marks a prayer point as prayed over today. */
    fun prayedFor(point: PrayerPoint) {
        val today = T.today()
        if (point.lastPrayedEpochDay == today) return
        replace(
            prayers,
            point.copy(lastPrayedEpochDay = today, prayedCount = point.prayedCount + 1)
        )
        logActivity("faith", point.title, point.id, event = "PRAYED")
    }

    /** Days a request has been carried, counted from when it was written down. */
    fun daysCarried(point: PrayerPoint, today: Long = T.today()): Int {
        if (point.createdAt <= 0) return 0
        return ((today - point.createdAt / T.DAY_MS)).toInt().coerceAtLeast(0)
    }

    fun logPrayerSession(minutes: Int) {
        if (minutes <= 0) return
        logActivity("faith", "Prayer", event = "PRAYER", value = minutes)
    }

    fun prayerMinutesOn(day: Long): Int =
        activity.flow.value.filter { it.domain == "faith" && it.event == "PRAYER" && it.epochDay == day }
            .sumOf { it.value }

    // ---- Modules --------------------------------------------------------------------------
    //
    // Switching a module on used to do nothing but reveal an empty tab, and
    // switching one off left its rows sitting in storage invisibly. These make
    // both directions concrete: turning one on can fill it with the same
    // starter set a fresh install gets, and turning one off can take its data
    // with it if that's what you actually meant.

    /** How many rows a module owns right now — shown next to its toggle. */
    fun moduleItemCount(key: String): Int = when (key.uppercase()) {
        "TASKS" -> todos.flow.value.size
        "GOALS" -> goals.flow.value.size
        "HABITS" -> habits.flow.value.size
        "JOURNAL" -> journal.flow.value.size
        "PEOPLE" -> people.flow.value.size
        "FITNESS" -> workouts.flow.value.size
        "MUSIC" -> practice.flow.value.size + albums.flow.value.size
        "FAITH" -> routines.flow.value.size + prayers.flow.value.size +
            readingPlans.flow.value.size + verses.flow.value.size
        "RECOVERY" -> recovery.flow.value.size
        "CAREER" -> career.flow.value.size
        "WISHLIST" -> wishlist.flow.value.size
        "GROWTH" -> growth.flow.value.size + deeds.flow.value.size
        else -> 0
    }

    /**
     * Fills an empty module with its starter set.
     *
     * Only ever adds to an empty table — this can't overwrite anything you've
     * written, which is what makes it safe to offer as a button rather than
     * behind a confirmation.
     */
    fun seedModule(key: String): Int {
        val before = moduleItemCount(key)
        when (key.uppercase()) {
            "TASKS" -> if (todos.flow.value.isEmpty()) { todos.flow.value = Seeds.todos(); persist(todos) }
            "GOALS" -> if (goals.flow.value.isEmpty()) { goals.flow.value = Seeds.goals(); persist(goals) }
            "HABITS" -> if (habits.flow.value.isEmpty()) { habits.flow.value = Seeds.habits(); persist(habits) }
            "GROWTH" -> if (growth.flow.value.isEmpty()) { growth.flow.value = Seeds.growth(); persist(growth) }
            "FITNESS" -> if (workouts.flow.value.isEmpty()) { workouts.flow.value = Seeds.workouts(); persist(workouts) }
            "CAREER" -> if (career.flow.value.isEmpty()) { career.flow.value = Seeds.career(); persist(career) }
            "WISHLIST" -> if (wishlist.flow.value.isEmpty()) { wishlist.flow.value = Seeds.wishlist(); persist(wishlist) }
            "RECOVERY" -> if (recovery.flow.value.isEmpty()) { recovery.flow.value = Seeds.recovery(); persist(recovery) }
            "MUSIC" -> {
                if (practice.flow.value.isEmpty()) { practice.flow.value = Seeds.practice(); persist(practice) }
                if (albums.flow.value.isEmpty()) { albums.flow.value = Seeds.albums(); persist(albums) }
            }
            "FAITH" -> {
                if (routines.flow.value.isEmpty()) { routines.flow.value = Seeds.routines(); persist(routines) }
                if (prayers.flow.value.isEmpty()) { prayers.flow.value = Seeds.prayers(); persist(prayers) }
            }
        }
        return moduleItemCount(key) - before
    }

    /** Deletes everything a module owns. Caller must confirm — this is not undoable. */
    fun clearModule(key: String): Int {
        val removed = moduleItemCount(key)
        when (key.uppercase()) {
            "TASKS" -> { todos.flow.value = emptyList(); persist(todos) }
            "GOALS" -> { goals.flow.value = emptyList(); persist(goals) }
            "HABITS" -> { habits.flow.value = emptyList(); persist(habits) }
            "JOURNAL" -> { journal.flow.value = emptyList(); persist(journal) }
            "PEOPLE" -> { people.flow.value = emptyList(); persist(people) }
            "FITNESS" -> {
                workouts.flow.value = emptyList(); persist(workouts)
                workoutLogs.flow.value = emptyList(); persist(workoutLogs)
            }
            "MUSIC" -> {
                practice.flow.value = emptyList(); persist(practice)
                albums.flow.value = emptyList(); persist(albums)
            }
            "FAITH" -> {
                routines.flow.value = emptyList(); persist(routines)
                prayers.flow.value = emptyList(); persist(prayers)
                readingPlans.flow.value = emptyList(); persist(readingPlans)
                verses.flow.value = emptyList(); persist(verses)
                gratitude.flow.value = emptyList(); persist(gratitude)
            }
            "RECOVERY" -> {
                recovery.flow.value = emptyList(); persist(recovery)
                habitLogs.flow.value = emptyList(); persist(habitLogs)
            }
            "CAREER" -> { career.flow.value = emptyList(); persist(career) }
            "WISHLIST" -> { wishlist.flow.value = emptyList(); persist(wishlist) }
            "GROWTH" -> {
                growth.flow.value = emptyList(); persist(growth)
                growthCheckins.flow.value = emptyList(); persist(growthCheckins)
                deeds.flow.value = emptyList(); persist(deeds)
            }
        }
        return removed
    }

    // ---- Meta / seeding ----------------------------------------------------------------------

    fun metaGet(key: String): String? = lsGet("tassic.meta.$key")
    fun metaSet(key: String, value: String) = lsSet("tassic.meta.$key", value)
    fun metaClear(key: String) = lsRemove("tassic.meta.$key")

    // ---- Home-screen widget ------------------------------------------------------------------

    private fun jsonEscape(raw: String): String = buildString {
        raw.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n', '\r', '\t' -> append(' ')
                else -> if (c.code >= 0x20) append(c)
            }
        }
    }

    private fun q(key: String, value: String): String = "\"" + key + "\":\"" + jsonEscape(value) + "\""

    private fun n(key: String, value: Int): String = "\"" + key + "\":" + value

    /**
     * Live payload for the "Tassic Today" widget and the persistent summary
     * notification, matching the bindings in
     * `resources/widgets/today-widget-template.json`. The bundled
     * `today-widget-data.json` is only a static placeholder, so without this the
     * widget could never show anything but "Open Tassic to sync today's numbers".
     * sw.js swaps in this JSON whenever the app pushes it.
     */
    fun widgetDataJson(): String {
        val today = T.today()
        val report = Insights.report(this, today)
        val shape = cagedShapeForToday()
        val modulesDone = practice.flow.value.count {
            it.kind == PracticeKind.MODULE && it.doneEpochDay == today
        }
        val openCount = todos.flow.value.count { !it.done }
        val overdue = todos.flow.value.count { !it.done && (it.dueEpochDay ?: Long.MAX_VALUE) < today }
        val nextAction = report.nextActions.firstOrNull()

        return "{" + listOf(
            q("dayLabel", T.dayName(today) + " \u00b7 " + T.dateLabel(today)),
            q("modulesDone", modulesDone.toString()),
            q("openTodos", openCount.toString()),
            q("streakDays", report.activeStreak.toString()),
            q("focusTitle", shape?.title ?: "No shape scheduled today"),
            q("headline", report.headline),
            q("nextAction", nextAction?.title ?: "Nothing pending"),
            q("nextReason", nextAction?.reason ?: "You're clear"),
            q("momentum", report.momentum.toString()),
            n("momentumValue", report.momentum),
            n("dueToday", report.dueToday),
            n("doneToday", report.doneToday),
            n("overdue", overdue),
            n("badge", openCount + overdue),
            q("progressLabel", "${report.doneToday}/${report.doneToday + report.dueToday} today"),
            // Added in v3.3. The widget's second tile was "modules done", a
            // music-practice count that meant nothing to anyone not using the
            // studio, while habits — the thing most people open the app for —
            // were invisible on the home screen entirely.
            q("habitsLabel", habitsWidgetLabel(today)),
            n("habitsDue", habitsDueToday(today).size),
            n("habitsKept", habitsDueToday(today).count { habitDoneOn(it, today) }),
            q("readingToday", readingWidgetLabel(today)),
            n("versesDue", versesDue(today).size),
            q(
                "deedThisMonth",
                if (Growth.deedDoneThisMonth(this, today)) "Logged" else "Nothing yet"
            )
        ).joinToString(",") + "}"
    }

    private fun habitsWidgetLabel(today: Long): String {
        val due = habitsDueToday(today)
        if (due.isEmpty()) return "0/0"
        return "${due.count { habitDoneOn(it, today) }}/${due.size}"
    }

    private fun readingWidgetLabel(today: Long): String {
        val plan = activeReadingPlan() ?: return "No plan"
        val index = readingDayIndex(plan, today)
        return plan.days.getOrNull(index) ?: "Plan complete"
    }

    // ---- Reminder scheduling handed to the service worker ---------------------

    /**
     * Every reminder due in the next 7 days, serialised for sw.js.
     *
     * A page-context timer can only fire while the tab is alive, which is why
     * reminders never arrived once the app was closed. Handing the schedule to
     * the service worker lets it fire them on any wake — periodic sync, push,
     * a notification click, or the next navigation — which is as close to a
     * real alarm as a backend-less PWA can get.
     *
     * `fireAt` values are LOCAL wall-clock milliseconds so the worker can
     * compare them without re-deriving the timezone offset.
     */
    fun reminderScheduleJson(): String {
        val s = settings()
        val today = T.today()
        val horizon = today + 7
        val nowLocal = T.localNow()
        val rows = mutableListOf<String>()

        if (s.remindersOn) {
            todos.flow.value.forEach { t ->
                if (t.done) return@forEach
                val day = t.dueEpochDay ?: return@forEach
                val lead = t.reminderMinutesBefore ?: return@forEach
                if (day > horizon) return@forEach

                val timeMinutes = t.dueTimeMinutes ?: (9 * 60)
                val dueMs = day * T.DAY_MS + timeMinutes * 60_000L
                val snooze = t.snoozedUntilMs
                val fireAt = if (snooze != null && snooze > nowLocal) snooze else dueMs - lead * 60_000L
                val fired = t.reminderFired && (snooze == null || snooze <= nowLocal)

                rows += "{" + listOf(
                    q("id", "todo-${t.id}"),
                    n("refId", t.id.toInt()),
                    q("kind", "todo"),
                    q("title", t.title),
                    q(
                        "body",
                        if (t.dueTimeMinutes != null) {
                            "Due ${T.dateLabel(day)} at ${T.timeLabel(timeMinutes * 60_000L)}"
                        } else "Due ${T.dateLabel(day)}"
                    ),
                    "\"fireAt\":$fireAt",
                    "\"fired\":$fired",
                    "\"actions\":true"
                ).joinToString(",") + "}"
            }

            routines.flow.value.forEach { r ->
                if (!r.reminderOn) return@forEach
                for (offset in 0..7) {
                    val day = today + offset
                    val dueThatDay = when (r.cadence.lowercase()) {
                        "weekly" -> T.tagMatches(r.dayTag, day)
                        "monthly" -> r.lastDoneEpochDay == null || day - (r.lastDoneEpochDay ?: 0L) >= 28
                        else -> true
                    }
                    if (!dueThatDay) continue
                    if ((r.lastDoneEpochDay ?: -1L) >= day) continue
                    val fireAt = day * T.DAY_MS + r.reminderHour * 3_600_000L
                    if (fireAt < nowLocal - 6L * 3_600_000L) continue
                    rows += "{" + listOf(
                        q("id", "routine-${r.id}-$day"),
                        n("refId", r.id.toInt()),
                        q("kind", "routine"),
                        q("title", r.title),
                        q("body", "${r.cadence} rhythm \u00b7 time to check in."),
                        "\"fireAt\":$fireAt",
                        "\"fired\":false",
                        "\"actions\":true"
                    ).joinToString(",") + "}"
                }
            }
        }

        // Habits and verses. These sit in their own guard because the block
        // above already closed the reminders check.
        if (s.remindersOn) {
            // Habits. Without these the worker could deliver task and routine
            // reminders while the app was shut but not habit ones, so a habit
            // reminder only ever arrived if you happened to have Tassic open —
            // which is precisely when you don't need reminding.
            activeHabits().forEach { h ->
                if (!h.reminderOn) return@forEach
                for (offset in 0..7) {
                    val day = today + offset
                    if (!habitDueOn(h, day)) continue
                    if (day == today && habitDoneOn(h, day)) continue
                    val fireAt = day * T.DAY_MS + h.reminderHour * 3_600_000L
                    if (fireAt < nowLocal - 6L * 3_600_000L) continue
                    rows += "{" + listOf(
                        q("id", "habit-${h.id}-$day"),
                        n("refId", h.id.toInt()),
                        q("kind", "habit"),
                        q("title", h.name),
                        q("body", Agenda.habitCadenceLabel(h) + " \u00b7 not yet today."),
                        "\"fireAt\":$fireAt",
                        "\"fired\":false",
                        "\"actions\":true"
                    ).joinToString(",") + "}"
                }
            }

            // Memory verses, batched into one row a day rather than one per
            // card — five notifications for five due verses is how someone
            // ends up turning reminders off altogether.
            for (offset in 0..7) {
                val day = today + offset
                val due = verses.flow.value.count { it.nextReviewEpochDay <= day }
                if (due == 0) continue
                val fireAt = day * T.DAY_MS + s.dailyBriefHour * 3_600_000L
                if (fireAt < nowLocal - 6L * 3_600_000L) continue
                rows += "{" + listOf(
                    q("id", "verses-$day"),
                    n("refId", 0),
                    q("kind", "verses"),
                    q("title", "$due verse(s) to review"),
                    q("body", "Say them before you look."),
                    "\"fireAt\":$fireAt",
                    "\"fired\":false",
                    "\"actions\":false"
                ).joinToString(",") + "}"
            }
        }

        // Recurring digests. These are the notifications that make the app feel
        // present without the user having to set anything up themselves.
        if (s.dailyBriefOn) {
            for (offset in 0..7) {
                val day = today + offset
                rows += "{" + listOf(
                    q("id", "brief-$day"),
                    n("refId", 0),
                    q("kind", "brief"),
                    q("title", "Today at a glance"),
                    q("body", briefBodyFor(day)),
                    "\"fireAt\":${day * T.DAY_MS + s.dailyBriefHour * 3_600_000L}",
                    "\"fired\":false",
                    "\"actions\":false"
                ).joinToString(",") + "}"
            }
        }

        if (s.eveningNudgeOn) {
            for (offset in 0..7) {
                val day = today + offset
                rows += "{" + listOf(
                    q("id", "evening-$day"),
                    n("refId", 0),
                    q("kind", "evening"),
                    q("title", "Evening check-in"),
                    q("body", "Close the loop on today before it rolls over."),
                    "\"fireAt\":${day * T.DAY_MS + s.eveningNudgeHour * 3_600_000L}",
                    "\"fired\":false",
                    "\"actions\":false"
                ).joinToString(",") + "}"
            }
        }

        if (s.weeklyReviewOn) {
            for (offset in 0..7) {
                val day = today + offset
                if (T.dowIndex(day) != s.weeklyReviewDow) continue
                rows += "{" + listOf(
                    q("id", "weekly-$day"),
                    n("refId", 0),
                    q("kind", "weekly"),
                    q("title", "Your week in review"),
                    q("body", "Seven days of momentum, streaks and what slipped."),
                    "\"fireAt\":${day * T.DAY_MS + s.weeklyReviewHour * 3_600_000L}",
                    "\"fired\":false",
                    "\"actions\":false"
                ).joinToString(",") + "}"
            }
        }

        return "{" + listOf(
            "\"generatedAt\":${T.now()}",
            "\"tzOffsetMs\":${T.tzOffsetMs()}",
            "\"quiet\":${s.quietHoursOn}",
            "\"quietStart\":${s.quietStartHour}",
            "\"quietEnd\":${s.quietEndHour}",
            "\"snoozeMinutes\":${s.snoozeMinutes}",
            "\"ongoing\":${s.ongoingSummaryOn}",
            "\"badge\":${s.badgeOn}",
            "\"items\":[" + rows.joinToString(",") + "]"
        ).joinToString(",") + "}"
    }

    /** How many reminders the worker currently has queued — surfaced in diagnostics. */
    fun scheduledReminderCount(): Int {
        val s = settings()
        val today = T.today()
        val horizon = today + 7
        var count = 0
        if (s.remindersOn) {
            count += todos.flow.value.count { t ->
                !t.done && t.reminderMinutesBefore != null &&
                    (t.dueEpochDay ?: Long.MAX_VALUE) <= horizon
            }
            count += routines.flow.value.count { it.reminderOn }
        }
        if (s.dailyBriefOn) count += 8
        if (s.eveningNudgeOn) count += 8
        if (s.weeklyReviewOn) count += 1
        return count
    }

    private fun briefBodyFor(day: Long): String {
        val tasks = todos.flow.value.count { !it.done && (it.dueEpochDay ?: Long.MAX_VALUE) <= day }
        val work = workouts.flow.value.count { T.tagMatches(it.dayTag, day) }
        val rout = routines.flow.value.count { routineDueToday(it, day) }
        val parts = mutableListOf<String>()
        if (tasks > 0) parts += "$tasks task${if (tasks == 1) "" else "s"}"
        if (work > 0) parts += "$work exercise${if (work == 1) "" else "s"}"
        if (rout > 0) parts += "$rout rhythm${if (rout == 1) "" else "s"}"
        return if (parts.isEmpty()) "Nothing scheduled — a clean slate."
        else parts.joinToString(" \u00b7 ") + " on the board."
    }

    // ---- Action queue drained from notification buttons ------------------------

    /**
     * Applies actions the user took from a notification while the app was
     * closed. sw.js parks them in localStorage under this key because a worker
     * has no way to reach into the running app that doesn't exist yet.
     */
    fun drainNotificationActions(): Int {
        val raw = lsGet("tassic.actionQueue") ?: return 0
        lsSet("tassic.actionQueue", "[]")
        val parsed = runCatching {
            json.decodeFromString(ListSerializer(QueuedAction.serializer()), raw)
        }.getOrNull() ?: return 0

        var applied = 0
        parsed.forEach { action ->
            when (action.kind) {
                "todo" -> {
                    val t = todos.flow.value.firstOrNull { it.id == action.refId } ?: return@forEach
                    when (action.action) {
                        "done" -> { if (!t.done) toggleTodo(t); applied++ }
                        "snooze" -> { snoozeTodo(t.id, settings().snoozeMinutes); applied++ }
                        // The worker delivered this one while the app was shut.
                        // Recording that here is what stops the in-page loop
                        // firing a duplicate on next launch.
                        "fired" -> { replace(todos, t.copy(reminderFired = true, snoozedUntilMs = null)); applied++ }
                    }
                }
                "routine" -> {
                    val r = routines.flow.value.firstOrNull { it.id == action.refId } ?: return@forEach
                    if (action.action == "done") { completeRoutine(r); applied++ }
                }
                "habit" -> {
                    val h = habits.flow.value.firstOrNull { it.id == action.refId } ?: return@forEach
                    if (action.action == "done" && !habitDoneOn(h, T.today())) {
                        tickHabit(h)
                        applied++
                    }
                }
            }
        }
        return applied
    }

    /** Marks a reminder as delivered so neither the app nor the worker repeats it. */
    fun markReminderFired(id: String) {
        if (!id.startsWith("todo-")) return
        val refId = id.removePrefix("todo-").toLongOrNull() ?: return
        val t = todos.flow.value.firstOrNull { it.id == refId } ?: return
        replace(todos, t.copy(reminderFired = true, snoozedUntilMs = null))
    }

    /**
     * Loads the editable presets once on first launch.
     *
     * [modules] limits what gets seeded. Everyone previously received CAGED
     * shapes, fasting rhythms and recovery counters whether or not any of it
     * applied to them, which is the fastest way to make an app feel like it was
     * built for somebody else and left open on your phone by mistake.
     */
    fun seedIfEmpty(modules: List<String> = emptyList()) {
        if (metaGet("seeded") != null) return
        fun on(key: String) = modules.isEmpty() || modules.contains(key)

        if (on("TASKS") && todos.flow.value.isEmpty()) todos.flow.value = Seeds.todos()
        if (on("GOALS") && goals.flow.value.isEmpty()) goals.flow.value = Seeds.goals()
        if (on("MUSIC") && practice.flow.value.isEmpty()) practice.flow.value = Seeds.practice()
        if (on("MUSIC") && albums.flow.value.isEmpty()) albums.flow.value = Seeds.albums()
        if (on("RECOVERY") && recovery.flow.value.isEmpty()) recovery.flow.value = Seeds.recovery()
        if (on("FITNESS") && workouts.flow.value.isEmpty()) workouts.flow.value = Seeds.workouts()
        if (on("CAREER") && career.flow.value.isEmpty()) career.flow.value = Seeds.career()
        if (on("FAITH") && routines.flow.value.isEmpty()) routines.flow.value = Seeds.routines()
        if (on("FAITH") && prayers.flow.value.isEmpty()) prayers.flow.value = Seeds.prayers()
        if (on("WISHLIST") && wishlist.flow.value.isEmpty()) wishlist.flow.value = Seeds.wishlist()
        if (on("HABITS") && habits.flow.value.isEmpty()) habits.flow.value = Seeds.habits()
        if (on("GROWTH") && growth.flow.value.isEmpty()) growth.flow.value = Seeds.growth()

        persist(todos); persist(goals); persist(practice); persist(albums)
        persist(recovery); persist(workouts); persist(career); persist(routines)
        persist(prayers); persist(wishlist); persist(habits); persist(growth)
        metaSet("seeded", "v3")
    }
}
