package tassic.data

import kotlinx.serialization.Serializable

/**
 * User-tunable behaviour, persisted as a single JSON blob under
 * `tassic.meta.settings`. Kept out of the [Store.Table] machinery deliberately:
 * it is a singleton, not a list, and it is read on nearly every frame (theme,
 * motion) so it wants to be a plain value rather than a flow of one element.
 */
@Serializable
data class Settings(
    // ---- Appearance -------------------------------------------------------
    /** "system" | "light" | "dark" */
    var themeMode: String = "system",
    /** "amber" | "blue" | "green" | "coral" | "violet" */
    var accent: String = "amber",
    /** Disables the ambient wallpaper animation and press/scale micro-motion. */
    var reduceMotion: Boolean = false,
    /** Ambient wallpaper on/off (independent of reduceMotion). */
    var ambientBackground: Boolean = true,

    // ---- Notifications ----------------------------------------------------
    var remindersOn: Boolean = true,
    /** Nothing fires between [quietStartHour] and [quietEndHour] local time. */
    var quietHoursOn: Boolean = true,
    var quietStartHour: Int = 22,
    var quietEndHour: Int = 7,
    /** Morning "here's your day" summary. */
    var dailyBriefOn: Boolean = true,
    var dailyBriefHour: Int = 7,
    /** Evening "streak at risk / N things still open" nudge. */
    var eveningNudgeOn: Boolean = true,
    var eveningNudgeHour: Int = 20,
    /** Weekly review notification. */
    var weeklyReviewOn: Boolean = true,
    /** 0 = Monday … 6 = Sunday. */
    var weeklyReviewDow: Int = 6,
    var weeklyReviewHour: Int = 18,
    /** Default lead time offered when a new task gets a reminder. */
    var defaultReminderLeadMinutes: Int = 30,
    /** Minutes added by the "Snooze" notification action. */
    var snoozeMinutes: Int = 15,

    // ---- Home-surface integrations ---------------------------------------
    /** Show the open-task count on the installed app icon (Badging API). */
    var badgeOn: Boolean = true,
    /**
     * Keep a silent, persistent "Today at a glance" notification pinned in the
     * shade. On Android this is the only widget-shaped surface a PWA can
     * actually own, so it stands in for the home-screen widget that the
     * Widgets API can't deliver outside Windows/Edge.
     */
    var ongoingSummaryOn: Boolean = false,

    // ---- Intelligence -----------------------------------------------------
    var insightsOn: Boolean = true,
    /** Hide POSITIVE/INFO insights and show only things needing attention. */
    var insightsCriticalOnly: Boolean = false,
    /** Weekly target used by the song tracker + practice insights. */
    var songsPerWeekTarget: Int = 1,
    /** Weekly training-session target used by the fitness insights. */
    var workoutsPerWeekTarget: Int = 4,
    /** Currency symbol for wishlist totals. */
    var currency: String = "$",

    // ---- Companion --------------------------------------------------------
    /**
     * The evening "close the day" prompt: what got done, what slipped, and
     * what tomorrow opens with. A tracker without a review is a pile of rows;
     * the review is where it turns into something you actually act on.
     */
    var dailyReviewOn: Boolean = true,
    var dailyReviewHour: Int = 21,
    /** Last day the review was completed, so the prompt disappears once done. */
    var lastReviewedEpochDay: Long = 0,
    /** Show the schedule-fit suggestion ("2h free after 14:00 fits both tasks"). */
    var planningHintsOn: Boolean = true,
    /** Warn when two timed items overlap on the day plan. */
    var conflictWarningsOn: Boolean = true,

    // ---- Quick capture ----------------------------------------------------
    /** Where a capture with no explicit prefix lands: "TASK" | "NOTE" | "JOURNAL". */
    var captureDefaultKind: String = "TASK",
    /** Parse dates, times, priorities and repeats out of captured text. */
    var smartCaptureOn: Boolean = true,

    // ---- Focus sessions ---------------------------------------------------
    var focusMinutes: Int = 25,
    var focusBreakMinutes: Int = 5,
    /** Chime + vibrate when a focus session ends. */
    var focusAlertOn: Boolean = true,

    // ---- Habits -----------------------------------------------------------
    var habitsOnToday: Boolean = true,

    // ---- Data -------------------------------------------------------------
    /** Nudge to export a backup when the last one is older than this many days. */
    var backupReminderDays: Int = 30,
    var lastBackupAt: Long = 0,

    // ---- Week -------------------------------------------------------------
    /** Monday-first is the default; some users plan Sunday-first. */
    var weekStartsMonday: Boolean = true,

    // ---- Weekly ritual ----------------------------------------------------
    /**
     * The Sunday counterpart to the evening review: pick up to three things
     * that matter this week, then have the week measured against them.
     */
    var weeklyPlanOn: Boolean = true,
    /** 0 = Monday … 6 = Sunday. Sunday by default. */
    var weeklyPlanDow: Int = 6,
    var weeklyPlanHour: Int = 17,

    // ---- Privacy ----------------------------------------------------------
    /**
     * A PIN screen over the sections that hold the most personal material.
     * This is a shoulder-surfing guard, not encryption — the rows are still
     * plain JSON in localStorage, and anyone with devtools can read them. It is
     * described that way in the UI rather than implying more than it does.
     */
    var lockEnabled: Boolean = false,
    /** Salted, iterated hash of the PIN. The PIN itself is never stored. */
    var lockPinHash: String = "",
    var lockSalt: String = "",
    var lockJournal: Boolean = true,
    var lockRecovery: Boolean = true,
    var lockPeople: Boolean = false,
    /** How long an unlock lasts before the gate returns. */
    var lockGraceMinutes: Int = 10,

    // ---- Modules ----------------------------------------------------------
    /**
     * Which domains this person actually uses. Everyone previously got CAGED
     * shapes, fasting rhythms and recovery counters whether or not any of it
     * applied to them, which is the fastest way to make an app feel like it was
     * built for somebody else.
     */
    var modules: List<String> = listOf(
        "TASKS", "HABITS", "PLANNER", "GOALS", "JOURNAL",
        "MUSIC", "FITNESS", "FAITH", "RECOVERY", "CAREER", "WISHLIST", "PEOPLE", "GROWTH"
    ),
    /** Set once the first-run picker has been completed or skipped. */
    var onboarded: Boolean = false,

    // ---- Calendar ---------------------------------------------------------
    /** Show imported calendar events on the day plan. */
    var calendarOnPlan: Boolean = true,
    /** Include imported events when working out what will fit in the day. */
    var calendarBlocksTime: Boolean = true
) {
    /** True when [hour] falls inside the configured quiet window. */
    fun isQuiet(hour: Int): Boolean {
        if (!quietHoursOn) return false
        // Windows that wrap past midnight (22 → 07) need the OR form.
        return if (quietStartHour <= quietEndHour) {
            hour in quietStartHour until quietEndHour
        } else {
            hour >= quietStartHour || hour < quietEndHour
        }
    }

    /**
     * Whether a domain is switched on.
     *
     * Defaults to true for anything not named in [modules], so a setting saved
     * by an older build — which had no module list at all — never hides a
     * section the person was already using.
     */
    fun hasModule(key: String): Boolean =
        modules.isEmpty() || modules.contains(key.uppercase())

    /** True when a PIN has actually been set, not merely enabled. */
    val lockReady: Boolean get() = lockEnabled && lockPinHash.isNotEmpty()
}
