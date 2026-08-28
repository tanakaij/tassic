package tassic.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tassic.data.FaithRoutine
import tassic.data.Graph
import tassic.data.PrayerPoint
import tassic.data.Reminders
import tassic.data.T
import tassic.platform.Notifications
import tassic.platform.awaitOrNull
import tassic.ui.components.EmptyState
import tassic.ui.components.GhostButton
import tassic.ui.components.IconActionBtn
import tassic.ui.components.ItemMenu
import tassic.ui.components.Pill
import tassic.ui.components.PrimaryButton
import tassic.ui.components.SecondaryButton
import tassic.ui.components.rememberSheetScope
import tassic.ui.components.rememberState
import tassic.ui.components.SectionHeader
import tassic.ui.components.SegmentedControl
import tassic.ui.components.SelectChips
import tassic.ui.components.TabScaffold
import tassic.ui.components.TassicCard
import tassic.ui.components.PrayerSheet
import tassic.ui.components.RoutineSheet
import tassic.ui.theme.Amber
import tassic.ui.theme.Green
import tassic.ui.theme.Ink
import tassic.ui.theme.Muted
import tassic.ui.theme.Navy
import tassic.ui.theme.SkySoft

@Composable
fun FaithTab() {
    val store = Graph.store
    val routines by store.routines.items.collectAsState()
    val prayers by store.prayers.items.collectAsState()
    val feedback = rememberSheetScope()
    val cs = androidx.compose.runtime.rememberCoroutineScope()
    var perm by rememberState(Notifications.permission())
    val today = T.today()

    var routineOpen by rememberState(false)
    var routineEdit by rememberState<FaithRoutine?>(null)
    var prayerOpen by rememberState(false)
    var prayerEdit by rememberState<PrayerPoint?>(null)

    // The routine-reminder poll that used to live here has moved into the
    // single app-wide loop in App.kt. Running it from inside this tab meant
    // Faith reminders only fired while the Faith tab was actually on screen.

    val active = prayers.filter { !it.answered }
    val answered = prayers.filter { it.answered }.sortedByDescending { it.answeredAt ?: 0 }

    // Segmented view switcher, same pattern as Life & Goals / Music / Today:
    // Routines, Reminders and Prayer were three separately-scrolled sections
    // stacked on one screen.
    var view by rememberState("Routines")
    // "Reminders" used to be hidden until at least one routine existed - but
    // that segment holds the ONLY "Enable notifications" button in the app, so
    // a fresh install could never grant permission and nothing ever alerted.
    // It is always available now.
    val views = listOf("Routines", "Reminders", "Prayer")
    if (view !in views) view = "Routines"
    val viewCounts = mapOf(
        "Routines" to "${routines.size}",
        "Reminders" to if (perm == "granted") "on" else "off",
        "Prayer" to "${active.size}"
    )

    TabScaffold(
        fabIcon = if (view == "Prayer") Icons.Filled.Add else null,
        fabLabel = if (view == "Prayer") "New Prayer" else null,
        onFab = if (view == "Prayer") ({ prayerEdit = null; prayerOpen = true }) else null
    ) {
        SegmentedControl(views, view, badge = { viewCounts[it] }) { view = it }

        // ---- Routines -------------------------------------------------------
        if (view == "Routines") {
        SectionHeader("Rhythms & Routines", "Fasting · reading · mountain trips")
        if (routines.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Church,
                title = "No routines",
                hint = "Add Daily Bible Reading, Thursday Fasting...",
                actionText = "+ Add routine",
                onAction = { routineEdit = null; routineOpen = true }
            )
        }
        routines.forEach { r ->
            TassicCard {
                var menu by rememberState(false)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.title, style = MaterialTheme.typography.titleMedium, color = Navy)
                            if (r.reminderOn) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Filled.AlarmOn,
                                    contentDescription = "Reminder on",
                                    tint = Amber,
                                    modifier = Modifier.padding(0.dp)
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Pill(r.cadence + if (r.cadence == "Weekly") " · ${r.dayTag}" else "", bg = SkySoft)
                            r.lastDoneEpochDay?.let {
                                Pill("Last: ${T.relativeDays(it, today)}", bg = SkySoft)
                            }
                            Pill("${r.timesCompleted}×", bg = SkySoft)
                        }
                    }
                    IconActionBtn(Icons.Filled.Edit, "Edit routine") { routineEdit = r; routineOpen = true }
                    IconActionBtn(Icons.Filled.MoreVert, "Options") { menu = true }
                    ItemMenu(
                        expanded = menu,
                        onDismiss = { menu = false },
                        onEdit = { routineEdit = r; routineOpen = true },
                        onDelete = { store.deleteRoutine(r.id) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    if (store.routineDueToday(r)) {
                        PrimaryButton("Mark done", { store.completeRoutine(r) })
                    } else {
                        Pill("Done today", bg = Green.copy(alpha = 0.16f), fg = Green)
                    }
                }
            }
        }
        if (routines.isNotEmpty()) {
            GhostButton("+ Add another routine", { routineEdit = null; routineOpen = true })
        }
        }

        // ---- Notifications ------------------------------------------------------
        if (view == "Reminders") {
        TassicCard {
            SectionHeader("Reminder Triggers", "Delivery status for this device")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Icon(Icons.Filled.Notifications, contentDescription = null, tint = Navy)
                Spacer(Modifier.width(10.dp))
                Text(
                    when (perm) {
                        "granted" -> "Notifications enabled on this device"
                        "denied" -> "Blocked in browser settings"
                        "unsupported" -> "Not supported on this browser"
                        else -> "Permission not granted yet"
                    },
                    style = MaterialTheme.typography.bodyMedium, color = Muted,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "Routine reminders now run from the app-wide scheduler, so they fire " +
                    "no matter which tab is open — and are handed to the service worker " +
                    "so they can still arrive while Tassic is closed.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                if (perm != "granted") {
                    SecondaryButton("Enable notifications", {
                        cs.launch {
                            Notifications.request().awaitOrNull()
                            perm = Notifications.permission()
                            store.metaSet("notify.permission", perm)
                            if (perm == "granted") {
                                Notifications.registerBackgroundDelivery()
                                Reminders.syncSchedule(store)
                                Notifications.show("Tassic", "Reminders are now enabled.")
                            }
                        }
                    })
                }
                GhostButton("Send test", { Reminders.sendTest(store) })
            }
            GhostButton("Full reminder settings", { feedback.launchSnackbar("Open the Settings tab for quiet hours, digests and diagnostics.") })
        }
        }

        // ---- Prayer points ---------------------------------------------------------
        if (view == "Prayer") {
        SectionHeader("Prayer Points", "${active.size} active · ${answered.size} answered")
        if (active.isEmpty() && answered.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Favorite,
                title = "No prayer points",
                hint = "Track requests and log answered prayers.",
                actionText = "New Prayer Point",
                onAction = { prayerEdit = null; prayerOpen = true }
            )
        }
        active.forEach { p ->
            TassicCard {
                var menu by rememberState(false)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.title, style = MaterialTheme.typography.titleSmall, color = Navy)
                        if (p.details.isNotBlank()) {
                            Text(p.details, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2)
                        }
                        Pill("Since ${T.shortDate(p.createdAt / T.DAY_MS)} · ${p.category}", bg = SkySoft)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconActionBtn(Icons.Filled.Favorite, "Mark answered", tint = Green) {
                            store.setPrayerAnswered(p, true)
                        }
                        IconActionBtn(Icons.Filled.MoreVert, "Options") { menu = true }
                        ItemMenu(
                            expanded = menu,
                            onDismiss = { menu = false },
                            onEdit = { prayerEdit = p; prayerOpen = true },
                            onDelete = { store.deletePrayer(p.id) }
                        )
                    }
                }
            }
        }
        if (answered.isNotEmpty()) {
            TassicCard {
                Text("Answered Prayers", style = MaterialTheme.typography.titleMedium, color = Green)
                answered.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(p.title, style = MaterialTheme.typography.bodyLarge, color = Ink)
                            p.answeredAt?.let {
                                Text("Answered ${T.fullLabel(it)}", style = MaterialTheme.typography.labelSmall, color = Muted)
                            }
                        }
                        GhostButton("Reopen", { store.setPrayerAnswered(p, false) })
                        IconActionBtn(Icons.Filled.MoreVert, "Options") { prayerEdit = p; prayerOpen = true }
                    }
                }
            }
        }
        }

        // ---- Sheets --------------------------------------------------------------
        if (routineOpen) RoutineSheet(routineEdit) { routineOpen = false }
        if (prayerOpen) PrayerSheet(prayerEdit) { prayerOpen = false }
    }
}

