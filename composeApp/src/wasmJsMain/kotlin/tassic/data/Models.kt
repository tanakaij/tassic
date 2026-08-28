package tassic.data

import kotlinx.serialization.Serializable

/** Every persisted row is editable & deletable — presets are seed data, never read-only. */
interface Identifiable {
    var id: Long
}

@Serializable
enum class Priority { URGENT, HIGH, NORMAL, LOW }

@Serializable
enum class Horizon { SHORT, MEDIUM, LONG }

@Serializable
enum class PracticeKind { SHAPE, SUBTASK, STYLE, SONG, MODULE, MODE, KEY }

@Serializable
data class TodoItem(
    override var id: Long = 0,
    var title: String = "",
    var notes: String = "",
    var priority: Priority = Priority.NORMAL,
    var dueEpochDay: Long? = null,
    /** Minutes since local midnight on [dueEpochDay]. Null = date only, no specific time. */
    var dueTimeMinutes: Int? = null,
    /** Minutes before the due date/time to notify; null = no reminder, 0 = right at the time. */
    var reminderMinutesBefore: Int? = null,
    /** Internal bookkeeping so the reminder scheduler notifies at most once. */
    var reminderFired: Boolean = false,
    /**
     * Set by the "Snooze" notification action. While this is in the future the
     * scheduler ignores [reminderFired] and re-fires once the snooze expires.
     */
    var snoozedUntilMs: Long? = null,
    /**
     * Repeat rule. One of "", "DAILY", "WEEKDAYS", "WEEKLY", "FORTNIGHTLY",
     * "MONTHLY". Completing a repeating task rolls its due date forward and
     * re-opens it instead of retiring it — the single biggest thing a task list
     * needs before it can hold real routines.
     */
    var recurrence: String = "",
    /** Rough effort in minutes; powers the "today's load" estimate on the brief. */
    var estimateMinutes: Int? = null,
    var tags: List<String> = emptyList(),
    var done: Boolean = false,
    /** When this was last checked off — required for any completion-rate analytics. */
    var completedAt: Long? = null,
    var createdAt: Long = 0
) : Identifiable

@Serializable
data class GoalItem(
    override var id: Long = 0,
    var title: String = "",
    var description: String = "",
    var horizon: Horizon = Horizon.SHORT,
    var category: String = "General",
    var progress: Int = 0,
    var targetEpochDay: Long? = null,
    var createdAt: Long = 0
) : Identifiable

@Serializable
data class PracticeItem(
    override var id: Long = 0,
    var section: String = "guitar",
    var kind: PracticeKind = PracticeKind.MODULE,
    var title: String = "",
    var detail: String = "",
    var dayTag: String = "ALL",
    var parentId: Long? = null,
    var targetPerWeek: Int = 0,
    var doneEpochDay: Long? = null,
    var doneCount: Int = 0,
    var sortOrder: Int = 0,
    var createdAt: Long = 0
) : Identifiable

@Serializable
data class AlbumGoal(
    override var id: Long = 0,
    var album: String = "",
    var artist: String = "",
    var totalTracks: Int = 1,
    var learnedTracks: Int = 0,
    var createdAt: Long = 0
) : Identifiable

@Serializable
data class RecoveryHabit(
    override var id: Long = 0,
    var name: String = "",
    var startEpochDay: Long = 0,
    var bestStreak: Int = 0,
    var relapses: Int = 0,
    var active: Boolean = true,
    var createdAt: Long = 0
) : Identifiable

@Serializable
data class HabitLog(
    override var id: Long = 0,
    var habitId: Long = 0,
    var event: String = "RELAPSE",
    var triggerNote: String = "",
    var loggedAt: Long = 0
) : Identifiable

@Serializable
data class WorkoutItem(
    override var id: Long = 0,
    var name: String = "",
    var sets: Int = 3,
    var reps: Int = 10,
    var unit: String = "reps",
    var dayTag: String = "ALL",
    var doneEpochDay: Long? = null,
    var sortOrder: Int = 0,
    var createdAt: Long = 0
) : Identifiable

@Serializable
data class WorkoutLog(
    override var id: Long = 0,
    var name: String = "",
    var sets: Int = 0,
    var reps: Int = 0,
    var unit: String = "reps",
    var loggedAt: Long = 0
) : Identifiable

@Serializable
data class CareerItem(
    override var id: Long = 0,
    var path: String = "GeoDev Roadmap",
    var stage: String = "",
    var stageOrder: Int = 0,
    var title: String = "",
    var url: String = "",
    var done: Boolean = false,
    var sortOrder: Int = 0,
    var createdAt: Long = 0
) : Identifiable

@Serializable
data class JournalEntry(
    override var id: Long = 0,
    var title: String = "",
    var body: String = "",
    var mood: Int = 3,
    var audioId: String? = null,
    var tags: List<String> = emptyList(),
    var createdAt: Long = 0
) : Identifiable

@Serializable
data class PrayerPoint(
    override var id: Long = 0,
    var title: String = "",
    var details: String = "",
    var category: String = "General",
    var answered: Boolean = false,
    var answeredAt: Long? = null,
    var createdAt: Long = 0
) : Identifiable

@Serializable
data class FaithRoutine(
    override var id: Long = 0,
    var title: String = "",
    var cadence: String = "Daily",
    var dayTag: String = "",
    var reminderHour: Int = 8,
    var reminderOn: Boolean = false,
    var lastDoneEpochDay: Long? = null,
    var timesCompleted: Int = 0,
    var createdAt: Long = 0
) : Identifiable

/**
 * An action the user took on a notification button while the app was closed.
 *
 * The service worker can't call into a Compose app that isn't running, so it
 * appends these to localStorage and the app replays them on next launch. This
 * is what makes "Mark done" from the notification shade actually mean something
 * rather than just dismissing the alert.
 */
@Serializable
data class QueuedAction(
    /** "todo" | "routine" */
    val kind: String = "",
    val refId: Long = 0,
    /** "done" | "snooze" */
    val action: String = "",
    val at: Long = 0
)

/**
 * Append-only cross-domain event log.
 *
 * Every table before this one only stored *current* state — `doneEpochDay` is
 * the last time something was ticked, not a history — so nothing in the app
 * could answer "how did this week compare to last week". Writing one small row
 * per completion is what makes trends, momentum, heatmaps and any honest
 * insight possible.
 */
@Serializable
data class ActivityLog(
    override var id: Long = 0,
    /** "practice" | "fitness" | "tasks" | "recovery" | "faith" | "goals" | "journal" | "music" */
    var domain: String = "",
    /** Free-form event name, e.g. "COMPLETE", "UNDO", "RELAPSE", "PROGRESS". */
    var event: String = "COMPLETE",
    var refId: Long = 0,
    var title: String = "",
    /** Optional magnitude — reps, minutes, progress delta. */
    var value: Int = 1,
    var epochDay: Long = 0,
    /** Local hour 0..23, stored explicitly so hour-of-day analysis is trivial. */
    var hour: Int = 0,
    var loggedAt: Long = 0
) : Identifiable

@Serializable
data class WishItem(
    override var id: Long = 0,
    var name: String = "",
    var category: String = "Gear",
    var price: Double = 0.0,
    var priority: Priority = Priority.NORMAL,
    var url: String = "",
    var purchased: Boolean = false,
    var createdAt: Long = 0
) : Identifiable
