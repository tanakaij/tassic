package tassic.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import tassic.platform.AudioStore
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

    fun updateJournal(item: JournalEntry, previousAudioId: String?) {
        if (previousAudioId != null && previousAudioId != item.audioId) {
            AudioStore.delete(previousAudioId) // fire-and-forget cleanup
        }
        replace(journal, item)
    }

    fun deleteJournal(item: JournalEntry) {
        item.audioId?.let { AudioStore.delete(it) } // fire-and-forget cleanup
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
            q("progressLabel", "${report.doneToday}/${report.doneToday + report.dueToday} today")
        ).joinToString(",") + "}"
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

    /** Loads the editable presets once on first launch. */
    fun seedIfEmpty() {
        if (metaGet("seeded") != null) return
        if (todos.flow.value.isEmpty()) todos.flow.value = Seeds.todos()
        if (goals.flow.value.isEmpty()) goals.flow.value = Seeds.goals()
        if (practice.flow.value.isEmpty()) practice.flow.value = Seeds.practice()
        if (albums.flow.value.isEmpty()) albums.flow.value = Seeds.albums()
        if (recovery.flow.value.isEmpty()) recovery.flow.value = Seeds.recovery()
        if (workouts.flow.value.isEmpty()) workouts.flow.value = Seeds.workouts()
        if (career.flow.value.isEmpty()) career.flow.value = Seeds.career()
        if (routines.flow.value.isEmpty()) routines.flow.value = Seeds.routines()
        if (prayers.flow.value.isEmpty()) prayers.flow.value = Seeds.prayers()
        if (wishlist.flow.value.isEmpty()) wishlist.flow.value = Seeds.wishlist()
        persist(todos); persist(goals); persist(practice); persist(albums)
        persist(recovery); persist(workouts); persist(career); persist(routines)
        persist(prayers); persist(wishlist)
        metaSet("seeded", "v1")
    }
}
