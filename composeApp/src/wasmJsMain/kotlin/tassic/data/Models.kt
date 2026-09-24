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
    /**
     * Checklist inside a task. A task list without sub-steps forces the user to
     * choose between one vague row ("sort out the visa") and six rows that bury
     * everything else — neither of which is what the work actually looks like.
     */
    var subtasks: List<SubTask> = emptyList(),
    /**
     * Optional link to the [GoalItem] this task serves. When the goal has
     * `autoProgress` on, ticking linked tasks moves the goal bar by itself, so
     * a long-horizon goal stops going stale the moment the user forgets to drag
     * its slider.
     */
    var goalId: Long? = null,
    /** Pinned tasks sort to the top of Today regardless of due date. */
    var pinned: Boolean = false,
    var done: Boolean = false,
    /** When this was last checked off — required for any completion-rate analytics. */
    var completedAt: Long? = null,
    var createdAt: Long = 0
) : Identifiable

/** One step inside a [TodoItem]. Deliberately tiny — a title and a tick. */
@Serializable
data class SubTask(
    var title: String = "",
    var done: Boolean = false
)

@Serializable
data class GoalItem(
    override var id: Long = 0,
    var title: String = "",
    var description: String = "",
    var horizon: Horizon = Horizon.SHORT,
    var category: String = "General",
    var progress: Int = 0,
    var targetEpochDay: Long? = null,
    /**
     * When true, [progress] is derived from the tasks linked to this goal
     * rather than typed in by hand. Hand-maintained progress bars drift within
     * a fortnight; derived ones can't.
     */
    var autoProgress: Boolean = false,
    /** The "why". Surfaced when a goal stalls, because that's when it matters. */
    var motivation: String = "",
    var archived: Boolean = false,
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
    /**
     * Key of a photo in the media store. Images live in IndexedDB alongside
     * voice clips for the same reason: a downscaled photo is still far past
     * what localStorage will hold.
     */
    var imageId: String? = null,
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
    /**
     * When this was last prayed over, and how many times.
     *
     * A prayer list with no sense of time becomes a wall of text nobody reads.
     * Knowing you've carried something for eighty days across forty prayers is
     * the part worth seeing — and it's what makes an answer, when it comes,
     * land as something more than a checkbox.
     */
    var lastPrayedEpochDay: Long = 0,
    var prayedCount: Int = 0,
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

/**
 * A habit you're trying to *keep*.
 *
 * The app already tracked habits you're trying to *break* ([RecoveryHabit],
 * counting days clean) and exercises ([WorkoutItem]), but there was nowhere to
 * put "read 20 minutes", "drink 8 glasses", "call home on Sundays" — the
 * repeating positives that make up most of a week. Those were being forced into
 * the task list, where completing one retires it and the streak is invisible.
 *
 * Ticks are not stored here. They go into [ActivityLog] with `domain = "habit"`
 * and `refId = id`, which means streaks, heatmaps and momentum all read habits
 * through the same machinery as everything else instead of a parallel one.
 */
@Serializable
data class Habit(
    override var id: Long = 0,
    var name: String = "",
    /** Icon key resolved by the UI: spark, water, book, run, sun, moon, heart, pen, music, pray. */
    var icon: String = "spark",
    /** Accent key resolved by the UI: blue, green, amber, coral, violet. */
    var color: String = "blue",
    /** "DAILY" | "WEEKDAYS" | "WEEKEND" | "CUSTOM" | "WEEKLY_COUNT" */
    var cadence: String = "DAILY",
    /** For CUSTOM: 0 = Monday … 6 = Sunday. */
    var daysOfWeek: List<Int> = emptyList(),
    /** For WEEKLY_COUNT: how many days a week counts as keeping it. */
    var timesPerWeek: Int = 3,
    /** Repetitions that count as done for a day — 1 for a plain tick, 8 for glasses of water. */
    var targetPerDay: Int = 1,
    /** Unit shown next to the count: "glasses", "pages", "min". Blank for a plain tick. */
    var unit: String = "",
    /** "ANY" | "MORNING" | "AFTERNOON" | "EVENING" — used to slot it into the day plan. */
    var timeOfDay: String = "ANY",
    var reminderOn: Boolean = false,
    var reminderHour: Int = 8,
    var archived: Boolean = false,
    var sortOrder: Int = 0,
    var createdAt: Long = 0
) : Identifiable

// ---------------------------------------------------------------- people

/**
 * Someone you want to stay in touch with.
 *
 * The app tracked tasks, goals, habits, training, faith and money, and had
 * nothing at all for the people those things are usually for. Relationships
 * decay quietly — nobody notices the month they stopped calling — which makes
 * them exactly the sort of thing a tracker is good at catching.
 *
 * [cadenceDays] is the interval you'd like to keep, not a rule. Zero means
 * "no schedule, just keep the birthday".
 */
@Serializable
data class Person(
    override var id: Long = 0,
    var name: String = "",
    var relationship: String = "Friend",
    /** 1..12, or 0 when no birthday is recorded. */
    var birthdayMonth: Int = 0,
    /** 1..31, or 0 when no birthday is recorded. */
    var birthdayDay: Int = 0,
    /** Preferred days between contact; 0 disables the nudge. */
    var cadenceDays: Int = 30,
    var lastContactEpochDay: Long = 0,
    var notes: String = "",
    /** Pinned people sort first and are never hidden behind a filter. */
    var pinned: Boolean = false,
    var archived: Boolean = false,
    var createdAt: Long = 0
) : Identifiable

// ---------------------------------------------------------------- calendar

/**
 * An imported calendar.
 *
 * The day plan previously only knew about things typed into Tassic, so it
 * confidently reported a free afternoon that actually held two meetings. A
 * subscribed .ics feed fixes that without a backend or an account — the file is
 * parsed on the device and its events are read-only here, because Tassic is not
 * the system of record for someone else's invite.
 */
@Serializable
data class CalendarFeed(
    override var id: Long = 0,
    var name: String = "",
    /** Optional webcal/https .ics URL. Empty for a one-off file import. */
    var url: String = "",
    /** Accent key: blue, green, amber, coral, violet. */
    var color: String = "violet",
    var enabled: Boolean = true,
    var lastSyncedAt: Long = 0,
    var eventCount: Int = 0,
    var createdAt: Long = 0
) : Identifiable

/**
 * One event from an imported feed.
 *
 * Recurrence is stored as the handful of RRULE parts that actually appear in
 * practice rather than as a full RFC 5545 implementation: frequency, interval,
 * the weekdays for a weekly rule, and an end. Anything more exotic is imported
 * as its first occurrence, which is honest — it shows up once and doesn't
 * pretend to a pattern the parser didn't understand.
 */
@Serializable
data class CalendarEvent(
    override var id: Long = 0,
    var feedId: Long = 0,
    /** ICS UID, used to replace an event on re-import instead of duplicating it. */
    var uid: String = "",
    var title: String = "",
    var location: String = "",
    var startEpochDay: Long = 0,
    /** Minutes since local midnight; null for an all-day event. */
    var startMinutes: Int? = null,
    var durationMinutes: Int = 60,
    var allDay: Boolean = false,
    /** "" | "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY" */
    var freq: String = "",
    var interval: Int = 1,
    /** For weekly rules: 0 = Monday … 6 = Sunday. Empty means "same day as the start". */
    var byDay: List<Int> = emptyList(),
    /** Last day the rule applies, or 0 for open-ended. */
    var untilEpochDay: Long = 0,
    /** Number of occurrences, or 0 when unbounded. */
    var count: Int = 0
) : Identifiable

// ------------------------------------------------------------- week plans

/** One of the (at most three) things that matter in a given week. */
@Serializable
data class Intention(
    var title: String = "",
    var done: Boolean = false
)

/**
 * The Sunday counterpart to the evening review.
 *
 * A daily review closes days; nothing closed the week, so momentum was always
 * measured against generic activity rather than against what the user actually
 * decided mattered. Three priorities is the limit on purpose — a list of ten is
 * a backlog, not a plan.
 */
@Serializable
data class WeekPlan(
    override var id: Long = 0,
    /** Monday-aligned week index from [T.weekIndex]. */
    var weekIndex: Long = 0,
    var priorities: List<Intention> = emptyList(),
    var notes: String = "",
    var createdAt: Long = 0,
    var closedAt: Long = 0
) : Identifiable

// ---------------------------------------------------------------- growth

/**
 * An area of yourself you're trying to make better.
 *
 * Everything else in the app tracks *doing*: tasks finished, sessions logged,
 * streaks held. None of it touches *becoming* — patience, generosity, honesty,
 * the way you speak to people when you're tired. Those don't decompose into
 * checkboxes, and trying to force them into a habit tracker produces a
 * dishonest number, so they get their own shape here: a named intention, the
 * concrete practices you've chosen, and an honest monthly rating rather than a
 * daily tick.
 *
 * The monthly cadence is the point. Character doesn't move on a daily scale,
 * and asking someone to score their humility every evening produces noise and
 * self-flagellation rather than insight.
 */
@Serializable
data class GrowthArea(
    override var id: Long = 0,
    var name: String = "",
    /** CHARACTER | MIND | BODY | SPIRIT | RELATIONSHIPS | CRAFT | MONEY | SERVICE */
    var dimension: String = "CHARACTER",
    /** The "who I'm trying to become" line, in your own words. */
    var intention: String = "",
    /** Concrete, observable things you've decided to do about it. */
    var practices: List<String> = emptyList(),
    /** What honest progress would look like — written before you rate it. */
    var evidence: String = "",
    var archived: Boolean = false,
    var sortOrder: Int = 0,
    var createdAt: Long = 0
) : Identifiable

/**
 * One month's honest rating of a growth area.
 *
 * [monthIndex] is `year * 12 + (month - 1)`, so months sort and subtract
 * without any calendar arithmetic at the call site.
 */
@Serializable
data class GrowthCheckin(
    override var id: Long = 0,
    var areaId: Long = 0,
    var monthIndex: Long = 0,
    /** 1..5, where 3 means "roughly where I was". */
    var rating: Int = 3,
    /** What actually happened — the evidence for the number. */
    var note: String = "",
    var createdAt: Long = 0
) : Identifiable

/**
 * Something good done for someone or something, once a month.
 *
 * Deliberately not a habit and not a task. A habit would put it on a streak,
 * and a streak turns a kindness into a score you're protecting — which is the
 * opposite of the thing. One a month, logged after the fact, with room to name
 * who it was for.
 */
@Serializable
data class GoodDeed(
    override var id: Long = 0,
    var title: String = "",
    /** PERSON | COMMUNITY | STRANGER | FAMILY | CREATION | ANONYMOUS */
    var kind: String = "PERSON",
    /** Who or what it was for. Optional — anonymous giving is the point sometimes. */
    var recipient: String = "",
    var notes: String = "",
    var monthIndex: Long = 0,
    var epochDay: Long = 0,
    /** Optional link to a [Person] when it was for someone already tracked. */
    var personId: Long? = null,
    var createdAt: Long = 0
) : Identifiable

// ------------------------------------------------------------------- faith

/**
 * An active scripture reading plan.
 *
 * [days] is generated once from a template and stored as plain reference
 * strings ("Genesis 1–3"), which keeps the whole plan a few kilobytes and means
 * a plan already underway can never be changed out from under you by a later
 * build that generates chapters differently.
 *
 * Progress is a set of completed day indices rather than a pointer, because
 * people skip a day and come back to it, and a pointer forces them to either
 * lie or lose the day.
 */
@Serializable
data class ReadingPlan(
    override var id: Long = 0,
    var name: String = "",
    var templateKey: String = "",
    var days: List<String> = emptyList(),
    var startEpochDay: Long = 0,
    var completedDays: List<Int> = emptyList(),
    var active: Boolean = true,
    var createdAt: Long = 0
) : Identifiable

/**
 * A verse being committed to memory.
 *
 * [text] is typed by the user, not fetched. Writing a verse out by hand is a
 * better first pass at learning it than reading one, and Tassic ships no Bible
 * text of its own — see [Bible] for why.
 *
 * [box] is a Leitner box, 1 to 5. Getting it right moves it up and pushes the
 * next review further out; getting it wrong sends it back to box 1, which is
 * the whole mechanism.
 */
@Serializable
data class MemoryVerse(
    override var id: Long = 0,
    var reference: String = "",
    var text: String = "",
    var box: Int = 1,
    var nextReviewEpochDay: Long = 0,
    var lastReviewedEpochDay: Long = 0,
    var reviewCount: Int = 0,
    var correctCount: Int = 0,
    var note: String = "",
    var createdAt: Long = 0
) : Identifiable

/**
 * One thing you were grateful for on a given day.
 *
 * Kept as its own small table rather than as journal entries: gratitude is
 * three short lines a day, and burying them in long-form entries makes the one
 * thing you'd actually want — reading a month of them at once — impossible.
 */
@Serializable
data class GratitudeItem(
    override var id: Long = 0,
    var text: String = "",
    var epochDay: Long = 0,
    var createdAt: Long = 0
) : Identifiable
