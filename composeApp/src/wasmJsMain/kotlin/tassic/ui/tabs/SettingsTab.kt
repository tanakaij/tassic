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
import tassic.data.Graph
import tassic.data.Reminders
import tassic.data.T
import tassic.platform.Notifications
import tassic.platform.awaitOrNull
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
            options = listOf("Reminders", "Appearance", "Diagnostics"),
            selected = view,
            onSelect = { view = it }
        )

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
