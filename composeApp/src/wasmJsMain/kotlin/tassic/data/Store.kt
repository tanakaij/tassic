package tassic.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import tassic.platform.AudioStore
import tassic.platform.lsGet
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
    fun toggleTodo(item: TodoItem) = replace(todos, item.copy(done = !item.done))

    // ---- Goals -------------------------------------------------------------

    fun addGoal(item: GoalItem) = insert(goals, item)
    fun updateGoal(item: GoalItem) = replace(goals, item)
    fun deleteGoal(id: Long) = remove(goals, id)
    fun bumpGoal(item: GoalItem, delta: Int) =
        replace(goals, item.copy(progress = (item.progress + delta).coerceIn(0, 100)))

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
    fun bumpAlbum(item: AlbumGoal, delta: Int) =
        replace(albums, item.copy(learnedTracks = (item.learnedTracks + delta).coerceIn(0, item.totalTracks)))

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
        } else {
            replace(workouts, item.copy(doneEpochDay = today))
            insert(
                workoutLogs,
                WorkoutLog(name = item.name, sets = item.sets, reps = item.reps, unit = item.unit, loggedAt = T.now())
            )
        }
    }

    /** Consecutive-day workout streak (today or yesterday counts as alive). */
    fun workoutStreak(): Int {
        val days = workoutLogs.flow.value.map { it.loggedAt / T.DAY_MS }.distinct().sortedDescending()
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
    fun toggleCareer(item: CareerItem) = replace(career, item.copy(done = !item.done))

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

    fun addJournal(item: JournalEntry) = insert(journal, item)

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
    fun setPrayerAnswered(item: PrayerPoint, answered: Boolean) =
        replace(prayers, item.copy(answered = answered, answeredAt = if (answered) T.now() else null))

    // ---- Faith routines -----------------------------------------------------------------

    fun addRoutine(item: FaithRoutine) = insert(routines, item)
    fun updateRoutine(item: FaithRoutine) = replace(routines, item)
    fun deleteRoutine(id: Long) = remove(routines, id)
    fun completeRoutine(item: FaithRoutine) =
        replace(routines, item.copy(lastDoneEpochDay = T.today(), timesCompleted = item.timesCompleted + 1))

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
