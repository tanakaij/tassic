package tassic.data

import tassic.platform.Notifications

/**
 * In-app reminder trigger for To-Dos with a due date/time and a reminder
 * lead time set. Polled from a [kotlinx.coroutines.delay]-based loop in
 * `App.kt` (same pattern already used for Faith routine reminders) — this
 * only fires while the app/tab is open, since a pure PWA with no backend
 * push server can't wake the device the way a native OS reminder can.
 */
object Reminders {

    /** Fires (and marks fired) any due-now todo reminders. Safe to call repeatedly. */
    fun checkTodoReminders(store: Store) {
        if (Notifications.permission() != "granted") return
        val nowMs = T.now()

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

            val whenLabel = if (t.dueTimeMinutes != null) {
                "Due ${T.dateLabel(day)} at ${T.timeLabel((timeMinutes * 60_000L))}"
            } else {
                "Due ${T.dateLabel(day)}"
            }
            Notifications.show("Reminder: ${t.title}", whenLabel)
            store.updateTodo(t.copy(reminderFired = true))
        }
    }
}
