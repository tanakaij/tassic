package tassic.data

import tassic.platform.Badge
import tassic.platform.Notifications
import tassic.platform.ReminderBridge

/**
 * The reminder engine.
 *
 * Delivery in a backend-less PWA is a two-layer problem and this object owns
 * both layers:
 *
 *  1. **While the app is open** — a coroutine loop in `App.kt` calls [tick]
 *     every 30 seconds. Precise, but only alive while a tab is.
 *  2. **While the app is closed** — [syncSchedule] hands the next 7 days of
 *     reminders to sw.js, which fires them on any wake (periodic sync, push,
 *     notification click, navigation). Not minute-accurate, but it is the
 *     difference between "reminders sometimes arrive" and "reminders never
 *     arrive", which is what a page-only timer amounts to.
 *
 * Both layers write to the same `fired` bookkeeping, so a reminder is delivered
 * at most once regardless of which gets there first.
 */
object Reminders {

    /** Anything overdue by more than this is stale: retire it without alerting. */
    private const val CATCH_UP_WINDOW_MS = 6L * 60L * 60L * 1000L

    /** Digest notifications are only interesting within a couple of hours. */
    private const val DIGEST_WINDOW_MS = 2L * 60L * 60L * 1000L

    /**
     * One scheduler pass. Safe and cheap to call repeatedly.
     *
     * Returns how many notifications were actually shown, which the settings
     * screen surfaces so "did that do anything?" has an answer.
     */
    fun tick(store: Store): Int {
        val settings = store.settings()

        // Replay anything tapped in the notification shade first, so a task
        // marked done from a notification isn't then re-reminded.
        store.drainNotificationActions()

        // Keep the worker's copy of the schedule current and refresh the app
        // icon badge, whether or not anything is due right now.
        syncSchedule(store)
        syncBadge(store)

        if (!settings.remindersOn) return 0

        val perm = Notifications.permission()
        store.metaSet("notify.permission", perm)
        if (perm == "denied" || perm == "unsupported") return 0

        val nowMs = T.localNow()
        val hour = T.localHour()
        val today = T.today()
        var shown = 0

        // ---- Task reminders --------------------------------------------------
        store.todos.items.value.forEach { t ->
            val day = t.dueEpochDay ?: return@forEach
            val lead = t.reminderMinutesBefore ?: return@forEach
            if (t.done) return@forEach

            val snooze = t.snoozedUntilMs
            if (snooze != null && snooze > nowMs) return@forEach
            if (t.reminderFired && snooze == null) return@forEach

            val timeMinutes = t.dueTimeMinutes ?: (9 * 60)
            val dueMs = day * T.DAY_MS + timeMinutes * 60_000L
            val fireAtMs = snooze ?: (dueMs - lead * 60_000L)
            if (nowMs < fireAtMs) return@forEach

            // Fell due while the app was closed and has gone stale.
            if (nowMs - fireAtMs > CATCH_UP_WINDOW_MS) {
                store.updateTodo(t.copy(reminderFired = true, snoozedUntilMs = null))
                return@forEach
            }

            // Quiet hours suppress the alert but do not consume it — it fires
            // as soon as the window ends, which is what someone setting a 23:00
            // reminder with quiet hours on actually wants.
            if (settings.isQuiet(hour)) return@forEach

            val whenLabel = if (t.dueTimeMinutes != null) {
                "Due ${T.dateLabel(day)} at ${T.timeLabel(timeMinutes * 60_000L)}"
            } else {
                "Due ${T.dateLabel(day)}"
            }
            Notifications.showTask(t.title, whenLabel, t.id)
            store.updateTodo(t.copy(reminderFired = true, snoozedUntilMs = null))
            shown++
        }

        // ---- Faith routines ---------------------------------------------------
        store.routines.items.value.forEach { r ->
            if (!r.reminderOn) return@forEach
            if (hour < r.reminderHour) return@forEach
            if (!store.routineDueToday(r, today)) return@forEach
            val key = "notified.routine.${r.id}.$today"
            if (store.metaGet(key) != null) return@forEach
            if (settings.isQuiet(hour)) return@forEach

            Notifications.showRoutine(r.title, "${r.cadence} rhythm \u00b7 time to check in.", r.id)
            store.metaSet(key, "1")
            shown++
        }

        // ---- Daily brief -------------------------------------------------------
        if (settings.dailyBriefOn && !settings.isQuiet(hour)) {
            val fireAt = today * T.DAY_MS + settings.dailyBriefHour * 3_600_000L
            val key = "notified.brief.$today"
            if (nowMs >= fireAt && nowMs - fireAt <= DIGEST_WINDOW_MS && store.metaGet(key) == null) {
                val report = Insights.report(store, today)
                Notifications.show("Today at a glance", report.headline)
                store.metaSet(key, "1")
                shown++
            }
        }

        // ---- Evening nudge -----------------------------------------------------
        if (settings.eveningNudgeOn && !settings.isQuiet(hour)) {
            val fireAt = today * T.DAY_MS + settings.eveningNudgeHour * 3_600_000L
            val key = "notified.evening.$today"
            if (nowMs >= fireAt && nowMs - fireAt <= DIGEST_WINDOW_MS && store.metaGet(key) == null) {
                val body = eveningBody(store, today)
                if (body != null) {
                    Notifications.show("Evening check-in", body)
                    shown++
                }
                store.metaSet(key, "1")
            }
        }

        // ---- Weekly review -----------------------------------------------------
        if (settings.weeklyReviewOn && !settings.isQuiet(hour) &&
            T.dowIndex(today) == settings.weeklyReviewDow
        ) {
            val fireAt = today * T.DAY_MS + settings.weeklyReviewHour * 3_600_000L
            val key = "notified.weekly.${T.weekIndex(today)}"
            if (nowMs >= fireAt && nowMs - fireAt <= DIGEST_WINDOW_MS && store.metaGet(key) == null) {
                val lines = Insights.weeklyReview(store, today)
                Notifications.show("Your week in review", lines.take(2).joinToString(" "))
                store.metaSet(key, "1")
                shown++
            }
        }

        // ---- Pinned summary ------------------------------------------------------
        if (settings.ongoingSummaryOn) {
            val report = Insights.report(store, today)
            Notifications.showOngoing(
                "Tassic \u00b7 ${T.dayName(today)}",
                report.headline,
                report.nextActions.firstOrNull()?.title
            )
        }

        // Housekeeping: per-day "already notified" keys otherwise accumulate
        // forever, and localStorage is not large.
        if (T.localMinuteOfDay() < 5) pruneMetaKeys(store, today)

        return shown
    }

    private fun eveningBody(store: Store, today: Long): String? {
        val openToday = store.todos.items.value
            .count { !it.done && (it.dueEpochDay ?: Long.MAX_VALUE) <= today }
        val streak = store.workoutStreak()
        val trained = store.workoutLogs.items.value.any { T.dayOf(it.loggedAt) == today }
        val routines = store.routines.items.value.count { store.routineDueToday(it, today) }

        return when {
            streak >= 2 && !trained -> "Your $streak-day training streak ends at midnight."
            openToday > 0 && routines > 0 ->
                "$openToday task${plural(openToday)} and $routines rhythm${plural(routines)} still open."
            openToday > 0 -> "$openToday task${plural(openToday)} still open today."
            routines > 0 -> "$routines rhythm${plural(routines)} still to keep today."
            else -> null
        }
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"

    /** Hands the next 7 days of reminders to the service worker. */
    fun syncSchedule(store: Store) {
        ReminderBridge.push(store.reminderScheduleJson())
    }

    /** Mirrors the outstanding count onto the installed app icon. */
    fun syncBadge(store: Store) {
        if (!store.settings().badgeOn) {
            Badge.clear()
            return
        }
        val today = T.today()
        val count = store.todos.items.value.count {
            !it.done && (it.dueEpochDay ?: Long.MAX_VALUE) <= today
        } + store.routines.items.value.count { store.routineDueToday(it, today) }
        Badge.set(count)
    }

    /**
     * Fires a test through the same path a real reminder takes (the service
     * worker registration, not a page-context Notification), so the result
     * actually says whether reminders work on this device.
     */
    fun sendTest(store: Store) {
        val report = Insights.report(store)
        Notifications.show("Tassic test reminder", report.headline)
        store.metaSet("notify.lastTest", T.now().toString())
    }

    private fun pruneMetaKeys(store: Store, today: Long) {
        listOf("brief", "evening").forEach { kind ->
            for (back in 8..30) {
                store.metaClear("notified.$kind.${today - back}")
            }
        }
        store.routines.items.value.forEach { r ->
            for (back in 8..30) {
                store.metaClear("notified.routine.${r.id}.${today - back}")
            }
        }
    }
}
