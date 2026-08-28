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
    var currency: String = "$"
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
}
