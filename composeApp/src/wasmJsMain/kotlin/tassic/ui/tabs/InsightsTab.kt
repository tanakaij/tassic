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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import tassic.data.Coach
import tassic.data.Graph
import tassic.data.Growth
import tassic.data.Insight
import tassic.data.People
import tassic.data.Insights as Engine
import tassic.data.Severity
import tassic.data.T
import tassic.ui.Tab
import tassic.ui.components.ActivityHeatmap
import tassic.ui.components.AnimatedNumber
import tassic.ui.components.BarRow
import tassic.ui.components.EmptyState
import tassic.ui.components.GhostButton
import tassic.ui.components.HeatmapLegend
import tassic.ui.components.InkCard
import tassic.ui.components.MetricTile
import tassic.ui.components.Pill
import tassic.ui.components.ProgressRing
import tassic.ui.components.SectionTitle
import tassic.ui.components.SegmentedControl
import tassic.ui.components.Sparkline
import tassic.ui.components.SunkenBox
import tassic.ui.components.TabScaffold
import tassic.ui.components.TassicCard
import tassic.ui.components.rememberState
import tassic.ui.theme.Blue
import tassic.ui.theme.Green
import tassic.ui.theme.LocalTokens
import tassic.ui.theme.severityColor

/**
 * The analytics surface.
 *
 * Everything here is derived from rows the user created — nothing is invented,
 * and each observation states the evidence behind it. That matters more than
 * usual for an app that tracks recovery and mood: a confident-sounding claim
 * with no visible basis would be worse than no claim at all.
 */
@Composable
fun InsightsTab(onOpenTab: (Tab) -> Unit = {}) {
    val store = Graph.store
    val t = LocalTokens.current

    // Observing the tables keeps the report live as the user ticks things off
    // elsewhere; the engine itself is pure, so recomputing is cheap and safe.
    val activity by store.activity.items.collectAsState()
    val todos by store.todos.items.collectAsState()
    val goals by store.goals.items.collectAsState()
    val workoutLogs by store.workoutLogs.items.collectAsState()
    val journal by store.journal.items.collectAsState()
    val habits by store.habits.items.collectAsState()
    val people by store.people.items.collectAsState()
    val growth by store.growthCheckins.items.collectAsState()
    val settings by store.settingsState.collectAsState()

    val today = T.today()
    // Both are pure functions of the tables above, so keying the memo on those
    // tables keeps the screen live without re-walking the log every frame.
    val report = remember(activity, todos, goals, workoutLogs, journal, settings, today) {
        Engine.report(store, today)
    }
    val review = remember(activity, todos, workoutLogs, journal, today) {
        Engine.weeklyReview(store, today)
    }

    // The engine in Insights.kt predates habits, people, growth and focus
    // sessions, so this screen — the one place a user would go looking for
    // synthesis — knew nothing about any of them. Rather than fold four new
    // domains into a 1200-line analyser, the newer engines are merged in here
    // and sorted by the same weight scale the original uses.
    val extraInsights = remember(activity, habits, people, growth, today) {
        (Coach.habitInsights(store, today) +
            Coach.peopleInsights(store, today) +
            Growth.insights(store, today))
            .sortedByDescending { it.weight }
    }

    var view by rememberState("Overview")
    val views = listOf("Overview", "Signals", "Rhythm", "Review")

    val allInsights = remember(report, extraInsights) {
        (report.insights + extraInsights).sortedByDescending { it.weight }
    }
    val visibleInsights = if (settings.insightsCriticalOnly) {
        allInsights.filter { it.severity == Severity.CRITICAL || it.severity == Severity.WARNING }
    } else {
        allInsights
    }
    val needsAttention = allInsights.count {
        it.severity == Severity.CRITICAL || it.severity == Severity.WARNING
    }

    TabScaffold(fabIcon = null, fabLabel = null, onFab = null) {
        SegmentedControl(
            options = views,
            selected = view,
            badge = { if (it == "Signals" && needsAttention > 0) "$needsAttention" else null },
            onSelect = { view = it }
        )

        if (view == "Overview") {
            // ---- Momentum -----------------------------------------------------
            InkCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(
                        progress = report.momentum / 100f,
                        diameter = 108,
                        thickness = 11,
                        color = t.accent,
                        trackColor = Color.White.copy(alpha = 0.14f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedNumber(
                                value = report.momentum,
                                suffix = "",
                                style = MaterialTheme.typography.displaySmall,
                                color = Color.White
                            )
                            Text(
                                "momentum",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            report.headline,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill(
                                text = if (report.momentumDelta >= 0) {
                                    "+${report.momentumDelta} vs last week"
                                } else {
                                    "${report.momentumDelta} vs last week"
                                },
                                bg = if (report.momentumDelta >= 0) {
                                    Green.copy(alpha = 0.22f)
                                } else {
                                    tassic.ui.theme.Coral.copy(alpha = 0.22f)
                                },
                                fg = Color.White
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${report.activeStreak}-day active streak \u00b7 best ${report.bestActiveStreak}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // ---- Today at a glance --------------------------------------------
            TassicCard {
                SectionTitle("Right now", "Today at a glance")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile(
                        "${report.doneToday}", "done today",
                        modifier = Modifier.weight(1f), tint = Green
                    )
                    MetricTile(
                        "${report.dueToday}", "still due",
                        modifier = Modifier.weight(1f), tint = Blue
                    )
                    MetricTile(
                        if (report.loadMinutes >= 60) "${report.loadMinutes / 60}h${report.loadMinutes % 60}"
                        else "${report.loadMinutes}m",
                        "est. load",
                        modifier = Modifier.weight(1f), tint = t.accentDeep
                    )
                }

                if (report.nextActions.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "SUGGESTED ORDER",
                        style = MaterialTheme.typography.labelSmall,
                        color = t.textTertiary
                    )
                    Spacer(Modifier.height(6.dp))
                    report.nextActions.forEachIndexed { index, action ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(t.accent.copy(alpha = 0.9f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = t.onAccent
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    action.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = t.textPrimary
                                )
                                Text(
                                    action.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = t.textSecondary
                                )
                            }
                            Pill(text = action.domain)
                        }
                    }
                }
            }

            // ---- Trend --------------------------------------------------------
            TassicCard {
                SectionTitle("Trend", "Last 28 days")
                Spacer(Modifier.height(6.dp))
                Sparkline(values = report.spark, height = 64, color = t.accentDeep)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActivityHeatmap(
                        counts = report.heat,
                        columns = 7,
                        cell = 24,
                        color = t.accentDeep,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                HeatmapLegend()
            }

            // ---- Domain balance ------------------------------------------------
            if (report.domains.isNotEmpty()) {
                TassicCard {
                    SectionTitle("Balance", "How each area is holding up")
                    Spacer(Modifier.height(4.dp))
                    report.domains.forEach { d ->
                        BarRow(
                            label = d.domain,
                            value = d.score,
                            caption = d.caption,
                            delta = d.delta,
                            color = domainColor(d.domain, t.accentDeep)
                        )
                    }
                }
            }
        }

        // ---- Signals ------------------------------------------------------------
        if (view == "Signals") {
            SectionTitle(
                "Intelligence",
                "What your data is saying",
                "${allInsights.size} observation${if (allInsights.size == 1) "" else "s"}"
            )

            if (visibleInsights.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Insights,
                    title = "Nothing to flag",
                    hint = "Keep logging — patterns need about a fortnight of data before " +
                        "they mean anything."
                )
            }

            visibleInsights.forEach { insight ->
                InsightCard(insight, onOpenTab)
            }
        }

        // ---- Rhythm ---------------------------------------------------------------
        // Habits, focus, people and growth: four things the app now tracks
        // daily and previously reported on nowhere but their own tabs.
        if (view == "Rhythm") {
            val pulses = remember(habits, activity, today) { Coach.allPulses(store, today) }
            val dueToday = pulses.filter { store.habitDueOn(it.habit, today) }
            val focusWeek = remember(activity, today) {
                (0..6).sumOf { back -> store.focusMinutesOn(today - back) }
            }
            val window = remember(activity, today) { Coach.productiveWindow(store, today) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile(
                    "${dueToday.count { it.doneToday }}/${dueToday.size}",
                    "habits today",
                    tint = Green,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    if (focusWeek >= 60) "${focusWeek / 60}h ${focusWeek % 60}m" else "${focusWeek}m",
                    "focused this week",
                    tint = Blue,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    "${Growth.monthsWithDeeds(store, today)}",
                    "months giving",
                    tint = tassic.ui.theme.Violet,
                    modifier = Modifier.weight(1f)
                )
            }

            if (window != null) {
                SunkenBox {
                    Text(
                        "You close most things between ${window.first}:00 and ${window.second}:00. " +
                            "Worth protecting that block rather than filling it with meetings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textSecondary
                    )
                }
            }

            if (pulses.isNotEmpty()) {
                SectionTitle("Consistency", "Habits over four weeks")
                TassicCard {
                    pulses.sortedByDescending { it.consistency }.forEach { pulse ->
                        BarRow(
                            label = pulse.habit.name,
                            value = if (pulse.dueLast28 == 0) 0 else pulse.consistency,
                            max = 100,
                            caption = if (pulse.dueLast28 == 0) {
                                "not due yet"
                            } else {
                                "${pulse.keptLast28} of ${pulse.dueLast28} days"
                            },
                            color = when {
                                pulse.consistency >= 80 -> Green
                                pulse.consistency >= 45 -> tassic.ui.theme.AmberDeep
                                else -> tassic.ui.theme.Coral
                            }
                        )
                    }
                }
            }

            val overdue = remember(people, today) { People.overdue(store, today) }
            val birthdays = remember(people, today) { People.upcomingBirthdays(store, today) }
            if (overdue.isNotEmpty() || birthdays.isNotEmpty()) {
                SectionTitle("People", "Who you said you'd keep close")
                TassicCard {
                    birthdays.take(3).forEach { status ->
                        StatLine(status.person.name, "birthday in ${status.daysToBirthday} day(s)")
                    }
                    overdue.take(4).forEach { status ->
                        StatLine(
                            status.person.name,
                            status.daysSince?.let { "$it days" } ?: "no contact logged"
                        )
                    }
                }
            }

            val growthPulses = remember(growth, today) { Growth.allPulses(store, today) }
            if (growthPulses.any { it.monthsRated > 0 }) {
                SectionTitle("Growth", "Monthly self-assessment")
                TassicCard {
                    growthPulses.forEach { pulse ->
                        StatLine(
                            pulse.area.name,
                            pulse.currentRating?.let { "$it of 5 this month" } ?: "not rated yet"
                        )
                    }
                }
            }

            if (pulses.isEmpty() && focusWeek == 0 && people.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Insights,
                    title = "Nothing to compare yet",
                    hint = "Habits, focus sessions, people and growth ratings all report here once there's a fortnight of them."
                )
            }
        }

        // ---- Weekly review -------------------------------------------------------
        if (view == "Review") {
            TassicCard {
                SectionTitle("Week in review", "Last 7 days")
                Spacer(Modifier.height(8.dp))
                review.forEach { line ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier
                                .padding(top = 7.dp)
                                .size(5.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(t.accentDeep)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            line,
                            style = MaterialTheme.typography.bodyLarge,
                            color = t.textPrimary
                        )
                    }
                }
            }

            TassicCard {
                SectionTitle("Records", "Personal bests")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile(
                        "${report.bestActiveStreak}", "best streak",
                        modifier = Modifier.weight(1f), tint = t.accentDeep
                    )
                    MetricTile(
                        "${store.workoutStreak()}", "training streak",
                        modifier = Modifier.weight(1f), tint = Green
                    )
                    MetricTile(
                        "${activity.size}", "events logged",
                        modifier = Modifier.weight(1f), tint = Blue
                    )
                }
                Spacer(Modifier.height(12.dp))
                SunkenBox {
                    Text(
                        "Totals",
                        style = MaterialTheme.typography.labelSmall,
                        color = t.textTertiary
                    )
                    Spacer(Modifier.height(6.dp))
                    StatLine("Tasks completed", "${todos.count { it.completedAt != null }}")
                    StatLine("Training sets logged", "${workoutLogs.size}")
                    StatLine("Journal entries", "${journal.size}")
                    StatLine("Goals at 100%", "${goals.count { it.progress >= 100 }}")
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
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
private fun InsightCard(insight: Insight, onOpenTab: (Tab) -> Unit) {
    val t = LocalTokens.current
    val tint = severityColor(insight.severity)

    TassicCard {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(tint.copy(alpha = if (t.dark) 0.22f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    severityIcon(insight.severity),
                    contentDescription = insight.severity.name,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        insight.domain.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = tint
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    insight.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = t.textPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    insight.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.textSecondary
                )
                if (insight.actionLabel != null && insight.actionTab != null) {
                    val target = Tab.entries.firstOrNull { it.name == insight.actionTab }
                    if (target != null) {
                        Spacer(Modifier.height(4.dp))
                        GhostButton(
                            insight.actionLabel,
                            { onOpenTab(target) },
                            trailingIcon = Icons.AutoMirrored.Filled.ArrowForward
                        )
                    }
                }
            }
        }
    }
}

private fun severityIcon(s: Severity): ImageVector = when (s) {
    Severity.CRITICAL -> Icons.Filled.Error
    Severity.WARNING -> Icons.Filled.Warning
    Severity.INFO -> Icons.Filled.Info
    Severity.POSITIVE -> Icons.Filled.CheckCircle
}

private fun domainColor(domain: String, fallback: Color): Color = when (domain) {
    "Practice" -> Blue
    "Fitness" -> tassic.ui.theme.Orange
    "Tasks" -> tassic.ui.theme.Violet
    "Faith" -> tassic.ui.theme.AmberDeep
    "Recovery" -> Green
    "habits" -> Green
    "people" -> Blue
    "growth" -> tassic.ui.theme.Violet
    else -> fallback
}
