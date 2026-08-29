package tassic.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tassic.data.CalendarFeed
import tassic.data.Graph
import tassic.data.Lock
import tassic.data.Reminders
import tassic.data.T
import tassic.platform.Notifications
import tassic.platform.awaitOrNull
import tassic.platform.downloadText
import tassic.platform.fetchText
import tassic.platform.pickTextFile
import tassic.platform.shareText
import tassic.ui.components.ConfirmDelete
import tassic.ui.components.LabeledField
import tassic.ui.components.MODULE_OPTIONS
import tassic.ui.components.PinPad
import tassic.ui.components.DestructiveButton
import tassic.ui.components.GhostButton
import tassic.ui.components.MiniStepper
import tassic.ui.components.Pill
import tassic.ui.components.SecondaryButton
import tassic.ui.components.SectionTitle
import tassic.ui.components.SegmentedControl
import tassic.ui.components.SoftDivider
import tassic.ui.components.StatusRow
import tassic.ui.components.Stepper
import tassic.ui.components.SunkenBox
import tassic.ui.components.TabScaffold
import tassic.ui.components.TassicCard
import tassic.ui.components.pressable
import tassic.ui.components.rememberFeedback
import tassic.ui.components.rememberSheetScope
import tassic.ui.components.rememberState
import tassic.ui.theme.LocalTokens
import tassic.ui.theme.accentFor

/**
 * Settings, and — more importantly — notification diagnostics.
 *
 * "Notifications don't work" is nearly impossible to debug from the outside,
 * because a PWA can fail to deliver for half a dozen unrelated reasons:
 * permission never granted, no service worker controlling the page, the app not
 * installed, periodic background sync unavailable on the platform, or quiet
 * hours quietly swallowing everything. This screen names which one it is.
 */
@Composable
fun SettingsTab() {
    val store = Graph.store
    val settings by store.settingsState.collectAsState()
    val t = LocalTokens.current
    val feedback = rememberSheetScope()
    val cs = androidx.compose.runtime.rememberCoroutineScope()

    var perm by rememberState(Notifications.permission())
    var caps by rememberState(Notifications.capabilitiesJson())
    var view by rememberState("Reminders")

    fun capOn(key: String): Boolean = caps.contains("\"$key\":true")

    TabScaffold(fabIcon = null, fabLabel = null, onFab = null) {
        SegmentedControl(
            options = listOf("Reminders", "Appearance", "Companion", "Modules", "Privacy", "Data", "Diagnostics"),
            selected = view,
            onSelect = { view = it }
        )

        // ------------------------------------------------------ companion
        if (view == "Companion") {
            TassicCard {
                SectionTitle("Close the day", "The evening review")
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    "Daily review prompt",
                    "Appears above the navigation bar after the hour below, and disappears once the day is closed.",
                    settings.dailyReviewOn
                ) { store.updateSettings { s -> s.copy(dailyReviewOn = it) } }
                if (settings.dailyReviewOn) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Prompt from",
                            style = MaterialTheme.typography.bodyMedium,
                            color = t.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        MiniStepper(
                            "Hour",
                            settings.dailyReviewHour,
                            { store.updateSettings { s -> s.copy(dailyReviewHour = it) } },
                            range = 0..23
                        )
                    }
                    if (settings.lastReviewedEpochDay >= T.today()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Today is already closed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tassic.ui.theme.Green
                        )
                    }
                }
            }

            TassicCard {
                SectionTitle("Plan the week", "The Sunday counterpart to the daily review")
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    "Weekly planning prompt",
                    "Asks for up to three priorities, then measures the week against those rather than against how busy it looked.",
                    settings.weeklyPlanOn
                ) { store.updateSettings { s -> s.copy(weeklyPlanOn = it) } }
                if (settings.weeklyPlanOn) {
                    Spacer(Modifier.height(8.dp))
                    Text("Ask on", style = MaterialTheme.typography.labelMedium, color = t.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        T.DAY_TAGS.forEachIndexed { index, tag ->
                            val sel = settings.weeklyPlanDow == index
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (sel) t.chrome else t.cardSunken)
                                    .pressable { store.updateSettings { s -> s.copy(weeklyPlanDow = index) } }
                                    .padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    tag.take(1),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sel) t.chromeText else t.textSecondary
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "From",
                            style = MaterialTheme.typography.bodyMedium,
                            color = t.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        MiniStepper(
                            "Hour",
                            settings.weeklyPlanHour,
                            { store.updateSettings { s -> s.copy(weeklyPlanHour = it) } },
                            range = 0..23
                        )
                    }
                }
            }

            TassicCard {
                SectionTitle("Planning", "How much the app volunteers")
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    "Schedule fit",
                    "Compares what's left against the gaps you actually have, and says when it doesn't fit.",
                    settings.planningHintsOn
                ) { store.updateSettings { s -> s.copy(planningHintsOn = it) } }
                SoftDivider()
                ToggleRow(
                    "Clash warnings",
                    "Flags two timed commitments booked over each other.",
                    settings.conflictWarningsOn
                ) { store.updateSettings { s -> s.copy(conflictWarningsOn = it) } }
                SoftDivider()
                ToggleRow(
                    "Habits on the day plan",
                    "Lists habits due today alongside tasks and rhythms.",
                    settings.habitsOnToday
                ) { store.updateSettings { s -> s.copy(habitsOnToday = it) } }
            }

            TassicCard {
                SectionTitle("Quick capture", "What one line turns into")
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    "Read dates and repeats from the text",
                    "\"gym tomorrow 7am every weekday ~45m\" becomes a scheduled, repeating, timed task. " +
                        "Turn this off to save exactly what you typed.",
                    settings.smartCaptureOn
                ) { store.updateSettings { s -> s.copy(smartCaptureOn = it) } }
                Spacer(Modifier.height(8.dp))
                Text("Default type", style = MaterialTheme.typography.labelMedium, color = t.textSecondary)
                Spacer(Modifier.height(6.dp))
                SegmentedControl(
                    options = listOf("TASK", "NOTE", "JOURNAL"),
                    selected = settings.captureDefaultKind,
                    onSelect = { store.updateSettings { s -> s.copy(captureDefaultKind = it) } }
                )
            }

            TassicCard {
                SectionTitle("Focus sessions", "Timed blocks")
                Spacer(Modifier.height(6.dp))
                Stepper(
                    "Session length",
                    settings.focusMinutes,
                    { store.updateSettings { s -> s.copy(focusMinutes = it) } },
                    range = 5..180,
                    suffix = "m"
                )
                Stepper(
                    "Break length",
                    settings.focusBreakMinutes,
                    { store.updateSettings { s -> s.copy(focusBreakMinutes = it) } },
                    range = 1..60,
                    suffix = "m"
                )
                SoftDivider()
                ToggleRow(
                    "Chime and vibrate at the end",
                    "A short tone synthesised in the browser — nothing to download.",
                    settings.focusAlertOn
                ) { store.updateSettings { s -> s.copy(focusAlertOn = it) } }
            }
        }

        // -------------------------------------------------------- privacy
        if (view == "Privacy") {
            PrivacySection()
        }

        // -------------------------------------------------------- modules
        if (view == "Modules") {
            ModulesSection()
        }

        // ----------------------------------------------------------- data
        if (view == "Data") {
            DataSection()
            CalendarSection()
        }

        // ------------------------------------------------------ reminders
        if (view == "Reminders") {
            TassicCard {
                SectionTitle("Delivery", "Notification permission")
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (perm == "granted") tassic.ui.theme.Green.copy(alpha = 0.16f)
                                else tassic.ui.theme.Coral.copy(alpha = 0.16f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = if (perm == "granted") tassic.ui.theme.Green else tassic.ui.theme.Coral,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (perm) {
                                "granted" -> "Notifications enabled"
                                "denied" -> "Blocked by the browser"
                                "unsupported" -> "Not supported here"
                                else -> "Permission not requested yet"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = t.textPrimary
                        )
                        Text(
                            when (perm) {
                                "granted" -> "Reminders can reach you even with Tassic closed, where the platform allows it."
                                "denied" -> "Re-enable notifications for this site in your browser's site settings — the app can't prompt again once blocked."
                                "unsupported" -> "This browser has no Notification API."
                                else -> "Nothing will alert you until this is granted."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = t.textSecondary
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (perm != "granted") {
                        SecondaryButton("Enable", {
                            cs.launch {
                                Notifications.request().awaitOrNull()
                                perm = Notifications.permission()
                                store.metaSet("notify.permission", perm)
                                caps = Notifications.capabilitiesJson()
                                if (perm == "granted") {
                                    Notifications.registerBackgroundDelivery()
                                    Reminders.syncSchedule(store)
                                    feedback.launchSnackbar("Reminders enabled.")
                                } else {
                                    feedback.launchSnackbar("Permission was not granted.")
                                }
                            }
                        })
                    }
                    GhostButton("Send test", {
                        Reminders.sendTest(store)
                        Notifications.kickScheduler()
                        feedback.launchSnackbar("Test sent — check your notification shade.")
                    })
                }
            }

            TassicCard {
                SectionTitle("Rules", "When Tassic may interrupt you")
                Spacer(Modifier.height(6.dp))

                ToggleRow(
                    "Task & routine reminders",
                    "Fire at the lead time you set on each item.",
                    settings.remindersOn
                ) { store.updateSettings { s -> s.copy(remindersOn = it) } }

                SoftDivider()

                ToggleRow(
                    "Quiet hours",
                    "Nothing fires between ${pad(settings.quietStartHour)}:00 and ${pad(settings.quietEndHour)}:00. " +
                        "Suppressed reminders are held, not dropped.",
                    settings.quietHoursOn
                ) { store.updateSettings { s -> s.copy(quietHoursOn = it) } }

                if (settings.quietHoursOn) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        MiniStepper(
                            "From", settings.quietStartHour,
                            { store.updateSettings { s -> s.copy(quietStartHour = it) } },
                            0..23
                        )
                        MiniStepper(
                            "To", settings.quietEndHour,
                            { store.updateSettings { s -> s.copy(quietEndHour = it) } },
                            0..23
                        )
                    }
                }

                SoftDivider()
                Spacer(Modifier.height(6.dp))
                Stepper(
                    "Snooze length",
                    settings.snoozeMinutes,
                    { store.updateSettings { s -> s.copy(snoozeMinutes = it) } },
                    range = 5..120,
                    suffix = "m"
                )
                Stepper(
                    "Default reminder lead",
                    settings.defaultReminderLeadMinutes,
                    { store.updateSettings { s -> s.copy(defaultReminderLeadMinutes = it) } },
                    range = 0..1440,
                    suffix = "m"
                )
            }

            TassicCard {
                SectionTitle("Digests", "Scheduled summaries")
                Spacer(Modifier.height(6.dp))

                ToggleRow(
                    "Morning brief",
                    "A one-line summary of the day at ${pad(settings.dailyBriefHour)}:00.",
                    settings.dailyBriefOn
                ) { store.updateSettings { s -> s.copy(dailyBriefOn = it) } }
                if (settings.dailyBriefOn) {
                    Row(Modifier.padding(vertical = 4.dp)) {
                        MiniStepper(
                            "Hour", settings.dailyBriefHour,
                            { store.updateSettings { s -> s.copy(dailyBriefHour = it) } },
                            0..23
                        )
                    }
                }

                SoftDivider()
                ToggleRow(
                    "Evening check-in",
                    "Flags streaks about to break and anything still open.",
                    settings.eveningNudgeOn
                ) { store.updateSettings { s -> s.copy(eveningNudgeOn = it) } }
                if (settings.eveningNudgeOn) {
                    Row(Modifier.padding(vertical = 4.dp)) {
                        MiniStepper(
                            "Hour", settings.eveningNudgeHour,
                            { store.updateSettings { s -> s.copy(eveningNudgeHour = it) } },
                            0..23
                        )
                    }
                }

                SoftDivider()
                ToggleRow(
                    "Weekly review",
                    "Your week summarised on ${T.dayNameFull(settings.weeklyReviewDow)}s.",
                    settings.weeklyReviewOn
                ) { store.updateSettings { s -> s.copy(weeklyReviewOn = it) } }
                if (settings.weeklyReviewOn) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        T.DAY_TAGS.forEachIndexed { index, tag ->
                            val sel = index == settings.weeklyReviewDow
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (sel) t.chrome else t.cardSunken)
                                    .pressable { store.updateSettings { s -> s.copy(weeklyReviewDow = index) } }
                                    .padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    tag.take(1),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sel) t.chromeText else t.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            TassicCard {
                SectionTitle("Home surfaces", "Outside the app")
                Spacer(Modifier.height(6.dp))

                ToggleRow(
                    "App icon badge",
                    if (capOn("badging")) "Shows how many things are due on the installed icon."
                    else "Not supported by this browser — install the app to enable it.",
                    settings.badgeOn
                ) {
                    store.updateSettings { s -> s.copy(badgeOn = it) }
                    Reminders.syncBadge(store)
                }

                SoftDivider()
                ToggleRow(
                    "Pinned Today summary",
                    "A silent, sticky notification with today's numbers. On Android this is " +
                        "the closest a PWA gets to a real home-screen widget, since the Widgets " +
                        "API only has a host on Windows.",
                    settings.ongoingSummaryOn
                ) {
                    store.updateSettings { s -> s.copy(ongoingSummaryOn = it) }
                    if (!it) Notifications.clearOngoing() else Reminders.tick(store)
                }
            }
        }

        // ----------------------------------------------------- appearance
        if (view == "Appearance") {
            TassicCard {
                SectionTitle("Theme", "Light, dark or follow the device")
                Spacer(Modifier.height(10.dp))
                SegmentedControl(
                    options = listOf("system", "light", "dark"),
                    selected = settings.themeMode,
                    onSelect = { store.updateSettings { s -> s.copy(themeMode = it) } }
                )
            }

            TassicCard {
                SectionTitle("Accent", "Colours the CTAs, ring and highlights")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    listOf("amber", "blue", "green", "coral", "violet").forEach { name ->
                        val (c, _, _) = accentFor(name)
                        val sel = settings.accent == name
                        Box(
                            Modifier
                                .size(if (sel) 44.dp else 38.dp)
                                .clip(CircleShape)
                                .background(c)
                                .pressable { store.updateSettings { s -> s.copy(accent = name) } },
                            contentAlignment = Alignment.Center
                        ) {
                            if (sel) {
                                Box(
                                    Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.9f))
                                )
                            }
                        }
                    }
                }
            }

            TassicCard {
                SectionTitle("Motion", "Comfort and battery")
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    "Ambient background",
                    "The slow colour wash behind the cards.",
                    settings.ambientBackground
                ) { store.updateSettings { s -> s.copy(ambientBackground = it) } }
                SoftDivider()
                ToggleRow(
                    "Reduce motion",
                    "Parks the wallpaper and skips press/count-up animations.",
                    settings.reduceMotion
                ) { store.updateSettings { s -> s.copy(reduceMotion = it) } }
            }

            TassicCard {
                SectionTitle("Targets", "Used by the insight engine")
                Spacer(Modifier.height(6.dp))
                Stepper(
                    "Songs per week",
                    settings.songsPerWeekTarget,
                    { store.updateSettings { s -> s.copy(songsPerWeekTarget = it) } },
                    range = 0..14
                )
                Stepper(
                    "Training sessions per week",
                    settings.workoutsPerWeekTarget,
                    { store.updateSettings { s -> s.copy(workoutsPerWeekTarget = it) } },
                    range = 1..14
                )
                SoftDivider()
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    "Only show what needs attention",
                    "Hides positive and informational insights.",
                    settings.insightsCriticalOnly
                ) { store.updateSettings { s -> s.copy(insightsCriticalOnly = it) } }
            }
        }

        // ---------------------------------------------------- diagnostics
        if (view == "Diagnostics") {
            TassicCard {
                SectionTitle("Delivery paths", "What this device actually supports")
                Spacer(Modifier.height(4.dp))

                StatusRow("Notification API", capOn("notifications"))
                StatusRow(
                    "Permission granted",
                    perm == "granted",
                    if (perm == "granted") null else "Currently: $perm"
                )
                StatusRow(
                    "Service worker controlling this page",
                    capOn("controlled"),
                    if (capOn("controlled")) null
                    else "Reload once — a worker only takes control after its first activation."
                )
                StatusRow(
                    "Installed as an app",
                    capOn("installed"),
                    if (capOn("installed")) null
                    else "Background delivery is much more reliable once installed to the home screen."
                )
                StatusRow(
                    "Periodic background sync",
                    capOn("periodicSync"),
                    if (capOn("periodicSync")) "Lets the worker wake and deliver while closed."
                    else "Unavailable here — reminders will only fire while Tassic is open or on the next launch."
                )
                StatusRow("Background sync", capOn("backgroundSync"))
                StatusRow("App icon badging", capOn("badging"))
                StatusRow(
                    "Widgets API host",
                    capOn("widgets"),
                    if (capOn("widgets")) "The Today widget can be pinned."
                    else "No widget host on this platform. The pinned Today summary is the alternative."
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Re-check", {
                        caps = Notifications.capabilitiesJson()
                        perm = Notifications.permission()
                        feedback.launchSnackbar("Capabilities refreshed.")
                    })
                    GhostButton("Re-register delivery", {
                        Notifications.registerBackgroundDelivery()
                        Reminders.syncSchedule(store)
                        Notifications.kickScheduler()
                        feedback.launchSnackbar("Schedule handed to the service worker.")
                    })
                }
            }

            TassicCard {
                SectionTitle("Why reminders can be late", "Honest limits")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tassic is a web app with no server behind it, so nothing can wake your " +
                        "phone at an exact minute the way a native alarm does. Reminders are " +
                        "delivered on a best-effort basis: instantly while the app is open, and " +
                        "otherwise whenever the browser next wakes the service worker — a " +
                        "background sync, opening any Tassic surface, or tapping a previous " +
                        "notification.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.textSecondary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Installing Tassic to your home screen materially improves this. Anything " +
                        "more than six hours late is retired silently rather than arriving as a " +
                        "pile of stale alerts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.textSecondary
                )
            }

            TassicCard {
                SectionTitle("Storage", "What's on this device")
                Spacer(Modifier.height(8.dp))
                SunkenBox {
                    DiagLine("Activity events", "${store.activity.items.value.size}")
                    DiagLine("Tasks", "${store.todos.items.value.size}")
                    DiagLine("Journal entries", "${store.journal.items.value.size}")
                    DiagLine("Training logs", "${store.workoutLogs.items.value.size}")
                    DiagLine("Scheduled reminders", "${store.scheduledReminderCount()}")
                    DiagLine(
                        "Last test sent",
                        store.metaGet("notify.lastTest")?.toLongOrNull()
                            ?.let { T.fullLabel(it) } ?: "never"
                    )
                }
            }
        }
    }
}

private fun pad(h: Int) = h.toString().padStart(2, '0')

/**
 * Backup, restore and erase.
 *
 * The app's whole database lives in localStorage, which is not durable storage:
 * a browser can evict it, "clear site data" wipes it, and nothing carries it to
 * a new device. Until now there was no way to get any of it out — which meant
 * every entry a person made was one browser decision away from gone.
 *
 * Export writes one JSON file containing every table plus settings. Restore
 * offers both modes, because they solve different problems: *replace* is moving
 * to a new device, *merge* is combining two devices without either one winning.
 */
@Composable
private fun DataSection() {
    val store = Graph.store
    val settings by store.settingsState.collectAsState()
    val t = LocalTokens.current
    val feedback = rememberFeedback()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var confirmErase by rememberState(false)
    var busy by rememberState(false)

    fun stamp(): String {
        val day = T.today()
        val (y, m, d) = T.civilFromDays(day)
        return "$y-${pad(m.toInt())}-${pad(d.toInt())}"
    }

    fun exportNow() {
        val json = store.exportJson()
        downloadText("tassic-backup-${stamp()}.json", json, "application/json")
        store.updateSettings { it.copy(lastBackupAt = T.now()) }
        feedback.say("Backup downloaded · ${store.countRows()} rows")
    }

    fun importNow(merge: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val raw = pickTextFile("application/json,.json").awaitOrNull()?.toString()
            busy = false
            if (raw.isNullOrBlank()) {
                feedback.say("No file chosen")
                return@launch
            }
            val rows = store.importJson(raw, merge)
            if (rows == null) {
                feedback.say("That file isn't a Tassic backup")
            } else {
                feedback.say(if (merge) "Merged · $rows rows now" else "Restored · $rows rows")
            }
        }
    }

    TassicCard {
        SectionTitle("Backup", "One file, everything in it")
        Spacer(Modifier.height(8.dp))
        Text(
            "Tassic keeps your data on this device only — nothing is uploaded anywhere. " +
                "That's a privacy guarantee and a risk in the same sentence, so keep a copy somewhere you trust.",
            style = MaterialTheme.typography.bodyMedium,
            color = t.textSecondary
        )
        Spacer(Modifier.height(12.dp))
        SunkenBox {
            DiagLine("Rows stored", "${store.countRows()}")
            DiagLine(
                "Last export",
                if (settings.lastBackupAt == 0L) "never" else T.fullLabel(settings.lastBackupAt)
            )
        }
        if (settings.lastBackupAt == 0L ||
            T.now() - settings.lastBackupAt > settings.backupReminderDays * T.DAY_MS
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "It's been more than ${settings.backupReminderDays} days since the last export.",
                style = MaterialTheme.typography.bodySmall,
                color = tassic.ui.theme.Coral
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Export a backup", { exportNow() })
            GhostButton("Share instead", {
                scope.launch {
                    val result = shareText("Tassic backup", store.exportJson()).awaitOrNull()?.toString()
                    feedback.say(
                        when (result) {
                            "shared" -> "Sent to the share sheet"
                            "copied" -> "Copied to the clipboard"
                            else -> "Sharing isn't available here — use Export instead"
                        }
                    )
                }
            })
        }
    }

    TassicCard {
        SectionTitle("Restore", "From a file you exported")
        Spacer(Modifier.height(8.dp))
        Text(
            "Replace swaps everything on this device for the file's contents, settings included. " +
                "Merge keeps what's here and adds the file's rows alongside it under fresh ids — " +
                "use that when two devices both have entries worth keeping.",
            style = MaterialTheme.typography.bodyMedium,
            color = t.textSecondary
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Merge in a file", { importNow(merge = true) })
            GhostButton("Replace everything", { importNow(merge = false) })
        }
    }

    TassicCard {
        SectionTitle("Erase", "Start over")
        Spacer(Modifier.height(8.dp))
        Text(
            "Clears every task, goal, habit, journal entry, prayer point and log on this device. " +
                "There is no undo, so export first.",
            style = MaterialTheme.typography.bodyMedium,
            color = t.textSecondary
        )
        Spacer(Modifier.height(12.dp))
        DestructiveButton("Erase all data", { confirmErase = true })
    }

    if (confirmErase) {
        ConfirmDelete(
            title = "Erase everything?",
            message = "${store.countRows()} rows will be deleted from this device. This cannot be undone.",
            onConfirm = {
                store.eraseAll()
                feedback.say("Everything erased")
            },
            onDismiss = { confirmErase = false }
        )
    }
}

@Composable
private fun DiagLine(label: String, value: String) {
    val t = LocalTokens.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = t.textSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = MaterialTheme.typography.labelLarge, color = t.textPrimary)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = t.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = t.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = t.onAccent,
                checkedTrackColor = t.accent,
                uncheckedThumbColor = t.card,
                uncheckedTrackColor = t.hairline
            )
        )
    }
}

/**
 * The PIN screen.
 *
 * Setting a PIN is a two-step flow rather than a single field because a typo in
 * a hidden four-digit code that then guards your journal is a bad afternoon.
 * The copy is deliberately careful about what this does and doesn't do — an
 * overstated privacy claim changes what someone is willing to write down, which
 * makes it the more damaging kind of inaccuracy.
 */
@Composable
private fun PrivacySection() {
    val store = Graph.store
    val settings by store.settingsState.collectAsState()
    val t = LocalTokens.current
    val feedback = rememberFeedback()

    var settingPin by rememberState(false)
    var firstEntry by rememberState("")
    var confirmClear by rememberState(false)

    TassicCard {
        SectionTitle("Lock", "A PIN over the personal sections")
        Spacer(Modifier.height(8.dp))
        Text(
            "The journal holds relapse logs and prayer points; the people list holds notes about family. " +
                "A PIN keeps those off the screen when someone else is holding your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = t.textSecondary
        )
        Spacer(Modifier.height(10.dp))
        SunkenBox {
            Text(
                "This is a screen, not encryption. Your entries stay as readable JSON in this browser's storage, " +
                    "so anyone with developer tools or access to the device profile can read them regardless of the PIN. " +
                    "It stops the person next to you, and that is the whole of the claim.",
                style = MaterialTheme.typography.bodySmall,
                color = t.textSecondary
            )
        }

        Spacer(Modifier.height(12.dp))
        if (!settings.lockReady) {
            SecondaryButton("Set a PIN", { settingPin = true; firstEntry = "" })
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Change PIN", { settingPin = true; firstEntry = "" })
                GhostButton("Turn off", { confirmClear = true })
            }
        }
    }

    if (settings.lockReady) {
        TassicCard {
            SectionTitle("What's behind it", "Pick per section")
            Spacer(Modifier.height(6.dp))
            ToggleRow("Journal", "Entries, photos and voice notes.", settings.lockJournal) {
                store.updateSettings { s -> s.copy(lockJournal = it) }
            }
            SoftDivider()
            ToggleRow("Recovery history", "Days clean and the trigger log.", settings.lockRecovery) {
                store.updateSettings { s -> s.copy(lockRecovery = it) }
            }
            SoftDivider()
            ToggleRow("People", "Names, notes and contact history.", settings.lockPeople) {
                store.updateSettings { s -> s.copy(lockPeople = it) }
            }
            Spacer(Modifier.height(8.dp))
            Stepper(
                "Stay unlocked for",
                settings.lockGraceMinutes,
                { store.updateSettings { s -> s.copy(lockGraceMinutes = it) } },
                range = 1..120,
                suffix = "m"
            )
            Spacer(Modifier.height(4.dp))
            GhostButton("Lock now", {
                Lock.lockNow()
                feedback.say("Locked")
            })
        }
    }

    if (settingPin) {
        TassicCard {
            PinPad(
                title = if (firstEntry.isEmpty()) "Choose a PIN" else "Enter it again",
                subtitle = if (firstEntry.isEmpty()) {
                    "Four digits. There is no recovery if you forget it — the sections just stay locked."
                } else {
                    "Once more, to be sure."
                },
                onSubmit = { pin ->
                    if (firstEntry.isEmpty()) {
                        firstEntry = pin
                        // Returning false would flash "didn't match"; the pad
                        // clears either way, and the title change is the signal.
                        true
                    } else if (pin == firstEntry) {
                        val salt = Lock.newSalt()
                        store.updateSettings { s ->
                            s.copy(
                                lockEnabled = true,
                                lockSalt = salt,
                                lockPinHash = Lock.hash(pin, salt)
                            )
                        }
                        Lock.unlock(settings.lockGraceMinutes)
                        settingPin = false
                        firstEntry = ""
                        feedback.say("PIN set")
                        true
                    } else {
                        firstEntry = ""
                        false
                    }
                }
            )
            GhostButton("Cancel", { settingPin = false; firstEntry = "" })
        }
    }

    if (confirmClear) {
        ConfirmDelete(
            title = "Turn off the lock?",
            message = "Journal, recovery and people become reachable without a PIN.",
            onConfirm = {
                store.updateSettings { s -> s.copy(lockEnabled = false, lockPinHash = "", lockSalt = "") }
                Lock.lockNow()
                feedback.say("Lock turned off")
            },
            onDismiss = { confirmClear = false }
        )
    }
}

/**
 * Module toggles.
 *
 * Switching a module off hides its section and stops its rows appearing on the
 * plan; it does not delete anything, which is the behaviour people expect from
 * a toggle and the one that makes it safe to experiment with.
 */
@Composable
private fun ModulesSection() {
    val store = Graph.store
    val settings by store.settingsState.collectAsState()
    val todos by store.todos.items.collectAsState()
    val t = LocalTokens.current
    val feedback = rememberFeedback()

    var confirmClear by rememberState<String?>(null)

    TassicCard {
        SectionTitle("Modules", "What Tassic keeps track of")
        Spacer(Modifier.height(8.dp))
        Text(
            "Switching one off hides its section and keeps its rows off the day plan. Nothing is deleted by the switch, " +
                "so turning it back on restores everything exactly as it was — use Clear below if you actually want the data gone.",
            style = MaterialTheme.typography.bodyMedium,
            color = t.textSecondary
        )
    }

    MODULE_OPTIONS.forEach { option ->
        val on = settings.hasModule(option.key)
        val count = store.moduleItemCount(option.key)

        TassicCard {
            ToggleRow(
                option.title + if (option.required) " (core)" else "",
                option.blurb + if (count > 0) " · $count item(s)" else "",
                on
            ) { checked ->
                if (option.required) return@ToggleRow
                store.updateSettings { s ->
                    val current = if (s.modules.isEmpty()) MODULE_OPTIONS.map { it.key } else s.modules
                    s.copy(
                        modules = if (checked) (current + option.key).distinct() else current - option.key
                    )
                }
            }

            // An enabled module with nothing in it is the state where people
            // conclude a feature is broken, so the two ways out of it — take the
            // starter set, or add your own — are offered right here rather than
            // left to be discovered on the tab.
            if (on) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (count == 0 && hasStarterSet(option.key)) {
                        SecondaryButton("Add starter items", {
                            val added = store.seedModule(option.key)
                            feedback.say(
                                if (added > 0) "$added item(s) added" else "Nothing to add"
                            )
                        })
                    }
                    if (count > 0) {
                        GhostButton("Clear data", { confirmClear = option.key })
                    }
                }
                if (count == 0 && !hasStarterSet(option.key)) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Empty — add your first entry from the ${option.title} section.",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textTertiary
                    )
                }
            }
        }
    }

    val clearing = confirmClear
    if (clearing != null) {
        val label = MODULE_OPTIONS.firstOrNull { it.key == clearing }?.title ?: clearing
        ConfirmDelete(
            title = "Clear $label?",
            message = "${store.moduleItemCount(clearing)} row(s) will be deleted from this device. This cannot be undone — export a backup first if you might want them.",
            onConfirm = {
                val removed = store.clearModule(clearing)
                feedback.say("$removed row(s) cleared")
            },
            onDismiss = { confirmClear = null }
        )
    }
}

/** Modules that ship an editable starter set; the rest begin genuinely empty. */
private fun hasStarterSet(key: String): Boolean = key.uppercase() in setOf(
    "TASKS", "GOALS", "HABITS", "GROWTH", "FITNESS", "CAREER", "WISHLIST", "RECOVERY", "MUSIC", "FAITH"
)

/**
 * Calendar subscriptions.
 *
 * File import is offered first and URL import second, which is the opposite of
 * what looks natural — but most calendar providers send no CORS headers, so a
 * pasted URL will simply be refused by the browser. Leading with the path that
 * works, and saying plainly why the other one might not, is better than a
 * feature that fails for most people with no explanation.
 */
@Composable
private fun CalendarSection() {
    val store = Graph.store
    val settings by store.settingsState.collectAsState()
    val feeds by store.calendars.items.collectAsState()
    val t = LocalTokens.current
    val feedback = rememberFeedback()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var url by rememberState("")
    var busy by rememberState(false)

    fun importFrom(feedName: String, existingId: Long?, raw: String, sourceUrl: String) {
        val feedId = existingId ?: store.addCalendar(
            CalendarFeed(name = feedName, url = sourceUrl, createdAt = T.now())
        ).id
        val count = store.importIcs(feedId, raw)
        if (count == null) {
            if (existingId == null) store.deleteCalendar(feedId)
            feedback.say("No events found in that file")
        } else {
            feedback.say("Imported $count event(s)")
        }
    }

    TassicCard {
        SectionTitle("Calendar", "So the plan knows about your meetings")
        Spacer(Modifier.height(8.dp))
        Text(
            "Without this, the day plan only knows what you typed into Tassic — which is how it ends up " +
                "describing a free afternoon that actually holds two meetings.",
            style = MaterialTheme.typography.bodyMedium,
            color = t.textSecondary
        )
        Spacer(Modifier.height(12.dp))
        SecondaryButton(if (busy) "Working…" else "Import an .ics file", {
            if (!busy) {
                busy = true
                scope.launch {
                    val raw = pickTextFile("text/calendar,.ics").awaitOrNull()?.toString()
                    busy = false
                    if (raw.isNullOrBlank()) {
                        feedback.say("No file chosen")
                    } else {
                        importFrom("Calendar", null, raw, "")
                    }
                }
            }
        })

        Spacer(Modifier.height(14.dp))
        LabeledField(url, { url = it }, "Or subscribe to a URL", placeholder = "https://…/basic.ics")
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Fetch", {
                if (url.isNotBlank() && !busy) {
                    busy = true
                    scope.launch {
                        val raw = fetchText(url.trim()).awaitOrNull()?.toString()
                        busy = false
                        if (raw.isNullOrBlank()) {
                            feedback.say("Couldn't read that URL — most providers block browser access. Export the file instead.")
                        } else {
                            importFrom("Subscribed calendar", null, raw, url.trim())
                            url = ""
                        }
                    }
                }
            })
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "A URL only works if the provider allows cross-origin reads. Tassic won't route your calendar " +
                "through a third-party proxy to get around that.",
            style = MaterialTheme.typography.bodySmall,
            color = t.textTertiary
        )
    }

    if (feeds.isNotEmpty()) {
        TassicCard {
            SectionTitle("Imported", "${feeds.size} feed(s)")
            Spacer(Modifier.height(6.dp))
            feeds.forEachIndexed { index, feed ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(feed.name, style = MaterialTheme.typography.titleSmall, color = t.textPrimary)
                        Text(
                            "${feed.eventCount} events · " +
                                if (feed.lastSyncedAt == 0L) "never synced" else T.fullLabel(feed.lastSyncedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = t.textSecondary
                        )
                    }
                    if (feed.url.isNotBlank()) {
                        GhostButton("Refresh", {
                            scope.launch {
                                val raw = fetchText(feed.url).awaitOrNull()?.toString()
                                if (raw.isNullOrBlank()) {
                                    feedback.say("Couldn't reach that URL")
                                } else {
                                    importFrom(feed.name, feed.id, raw, feed.url)
                                }
                            }
                        })
                    }
                    GhostButton(if (feed.enabled) "Hide" else "Show", {
                        store.updateCalendar(feed.copy(enabled = !feed.enabled))
                    })
                    GhostButton("Remove", {
                        store.deleteCalendar(feed.id)
                        feedback.say("${feed.name} removed")
                    })
                }
                if (index != feeds.lastIndex) SoftDivider()
            }
        }

        TassicCard {
            SectionTitle("How events are used", "On the plan")
            Spacer(Modifier.height(6.dp))
            ToggleRow(
                "Show on the day plan",
                "Imported events appear on the timeline but can't be ticked off here — Tassic isn't the system of record for someone else's invite.",
                settings.calendarOnPlan
            ) { store.updateSettings { s -> s.copy(calendarOnPlan = it) } }
            SoftDivider()
            ToggleRow(
                "Count against free time",
                "Treats a meeting as occupied time when working out what will fit.",
                settings.calendarBlocksTime
            ) { store.updateSettings { s -> s.copy(calendarBlocksTime = it) } }
        }
    }
}
