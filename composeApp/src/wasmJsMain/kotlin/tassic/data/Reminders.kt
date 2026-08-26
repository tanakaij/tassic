package tassic.data

import tassic.platform.Notifications

/**
 * In-app reminder trigger for To-Dos with a due date/time and a reminder
 * lead time set. Polled from a [kotlinx.coroutines.delay]-based loop in
 * `App.kt` (same pattern already used for Faith routine reminders) - this
 * only fires while the app/tab is open, since a pure PWA with no backend
 * push server can't wake the device the way a native OS reminder can.
 */
object Reminders {

    /** Anything overdue by more than this is stale: mark fired without alerting. */
    private const val CATCH_UP_WINDOW_MS = 12L * 60L * 60L * 1000L

    /** Fires (and marks fired) any due-now todo reminders. Safe to call repeatedly. */
    fun checkTodoReminders(store: Store) {
        // Was `Notifications.permission() != "granted"`, which quietly abandoned
        // every reminder on installs where permission had never been requested.
        // Only bail when the user has actually declined or the API is missing.
        val perm = Notifications.permission()
        if (perm == "denied" || perm == "unsupported") return

        // Reminder times are wall-clock times the user typed, so they have to be
        // compared against local time. Comparing them against the raw UTC epoch
        // meant every reminder fired offset by the device's timezone - two hours
        // late in UTC+2, and never on the intended calendar day near midnight.
        val nowMs = T.localNow()

        store.todos.items.value.forEach { t ->
            val day = t.dueEpochDay ?: return@forEach
            val lead = t.reminderMinutesBefore ?: return@forEach
            if (t.done || t.reminderFired) return@forEach

            // Date-only tasks (no specific time) get a 9am default so "remind
            // me the day of" still means something concrete.
            val timeMinutes = t.dueTimeMinutes ?: (9 * 60)
            val dueMs = day * T.DAY_MS + timeMinutes * 60_000L
            val fireAtMs = dueMs - lead * 60_000L
            if (nowMs < fireAtMs) return@forEach

            // The app was closed when this fell due: still surface it if it only
            // just slipped past, otherwise retire it silently rather than dumping
            // a pile of stale alerts on the next launch.
            if (nowMs - fireAtMs > CATCH_UP_WINDOW_MS) {
                store.updateTodo(t.copy(reminderFired = true))
                return@forEach
            }

            val whenLabel = if (t.dueTimeMinutes != null) {
                "Due ${T.dateLabel(day)} at ${T.timeLabel(timeMinutes * 60_000L)}"
            } else {
                "Due ${T.dateLabel(day)}"
            }
            Notifications.show("Reminder: ${t.title}", whenLabel)
            store.updateTodo(t.copy(reminderFired = true))
        }
    }
}
