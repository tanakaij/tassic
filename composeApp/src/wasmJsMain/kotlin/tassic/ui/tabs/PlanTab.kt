package tassic.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tassic.data.Agenda
import tassic.data.AgendaEntry
import tassic.data.Coach
import tassic.data.DayPlan
import tassic.data.Graph
import tassic.data.Habit
import tassic.data.Nlp
import tassic.data.T
import tassic.data.toggleAgendaEntry
import tassic.ui.Tab
import tassic.ui.components.AnimatedNumber
import tassic.ui.components.EmptyState
import tassic.ui.components.GhostButton
import tassic.ui.components.GlassCard
import tassic.ui.components.HabitEditorSheet
import tassic.ui.components.HabitRow
import tassic.ui.components.HabitTrace
import tassic.ui.components.InkCard
import tassic.ui.components.MetricTile
import tassic.ui.components.Pill
import tassic.ui.components.ProgressRing
import tassic.ui.components.QuickCaptureSheet
import tassic.ui.components.SectionTitle
import tassic.ui.components.SegmentedControl
import tassic.ui.components.SoftDivider
import tassic.ui.components.SunkenBox
import tassic.ui.components.WeekPrioritiesCard
import tassic.ui.components.WeeklyPlanSheet
import tassic.ui.components.TabScaffold
import tassic.ui.components.TassicCard
import tassic.ui.components.habitColor
import tassic.ui.components.pressable
import tassic.ui.components.rememberFeedback
import tassic.ui.components.rememberState
import tassic.ui.theme.AmberDeep
import tassic.ui.theme.Blue
import tassic.ui.theme.Coral
import tassic.ui.theme.Green
import tassic.ui.theme.LocalTokens
import tassic.ui.theme.Violet

/**
 * The plan.
 *
 * Everything the user has committed to — timed tasks, habits, faith rhythms,
 * training and practice — assembled into one day, one week and one habit board.
 * Before this, each of those lived on the screen that owned its table, and the
 * only way to know what a day held was to visit four tabs and hold the answer
 * in your head.
 *
 * The three segments answer three different questions, deliberately: *what does
 * today look like* (Day), *where is the week loaded* (Week), and *what is
 * actually holding* (Habits).
 */
@Composable
fun PlanTab(onOpenTab: (Tab) -> Unit = {}) {
    val store = Graph.store
    val settings by store.settingsState.collectAsState()
    val todos by store.todos.items.collectAsState()
    val habits by store.habits.items.collectAsState()
    val activity by store.activity.items.collectAsState()
    val routines by store.routines.items.collectAsState()
    val workouts by store.workouts.items.collectAsState()

    val today = T.today()
    var view by rememberState("Day")
    var dayOffset by rememberState(0)
    var weekOffset by rememberState(0)
    var captureOpen by rememberState(false)
    var habitSheetOpen by rememberState(false)
    var habitEdit by rememberState<Habit?>(null)
    var weekPlanOpen by rememberState(false)

    val day = today + dayOffset
    val plan = remember(todos, habits, activity, routines, workouts, day) {
        Agenda.plan(store, day)
    }

    TabScaffold(
        fabIcon = if (view == "Habits") Icons.Filled.Add else Icons.Filled.Bolt,
        fabLabel = if (view == "Habits") "New habit" else "Capture",
        onFab = if (view == "Habits") {
            ({ habitEdit = null; habitSheetOpen = true })
        } else {
            ({ captureOpen = true })
        }
    ) {
        // Habits is dropped when the module is off, rather than offering a
        // segment that opens onto an empty screen.
        val segments = if (settings.hasModule("HABITS")) {
            listOf("Day", "Week", "Habits")
        } else {
            listOf("Day", "Week")
        }
        if (view !in segments) view = "Day"

        SegmentedControl(
            options = segments,
            selected = view,
            badge = {
                when (it) {
                    "Day" -> "${plan.allEntries.count { e -> !e.done }}"
                    "Habits" -> "${store.habitsDueToday().count { h -> !store.habitDoneOn(h, today) }}"
                    else -> null
                }
            },
            onSelect = { view = it }
        )

        when (view) {
            "Day" -> DayView(
                plan = plan,
                day = day,
                today = today,
                onShiftDay = { dayOffset += it },
                onResetDay = { dayOffset = 0 },
                onOpenTab = onOpenTab
            )
            "Week" -> WeekView(
                weekOffset = weekOffset,
                onShiftWeek = { weekOffset += it },
                onEditWeekPlan = { weekPlanOpen = true },
                onPickDay = { picked ->
                    dayOffset = (picked - today).toInt()
                    view = "Day"
                }
            )
            else -> HabitsView(
                onEdit = { habitEdit = it; habitSheetOpen = true },
                onNew = { habitEdit = null; habitSheetOpen = true }
            )
        }

        if (captureOpen) QuickCaptureSheet(onDismiss = { captureOpen = false })
        if (habitSheetOpen) HabitEditorSheet(habitEdit) { habitSheetOpen = false }
        if (weekPlanOpen) WeeklyPlanSheet { weekPlanOpen = false }
    }
}

// ------------------------------------------------------------------- day view

@Composable
private fun DayView(
    plan: DayPlan,
    day: Long,
    today: Long,
    onShiftDay: (Int) -> Unit,
    onResetDay: () -> Unit,
    onOpenTab: (Tab) -> Unit
) {
    val store = Graph.store
    val settings by store.settingsState.collectAsState()
    val t = LocalTokens.current
    val feedback = rememberFeedback()

    // ---- date navigator ---------------------------------------------------
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") { onShiftDay(-1) }
        Column(
            Modifier.weight(1f).pressable { onResetDay() },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                when (day - today) {
                    0L -> "Today"
                    1L -> "Tomorrow"
                    -1L -> "Yesterday"
                    else -> T.dayNameFullOf(day)
                },
                style = MaterialTheme.typography.titleLarge,
                color = t.textPrimary
            )
            Text(T.dateLabel(day), style = MaterialTheme.typography.bodySmall, color = t.textSecondary)
        }
        NavArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day") { onShiftDay(1) }
    }

    // ---- the day at a glance -----------------------------------------------
    InkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (day == today) "TODAY" else "PLANNED",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    Coach.companionLine(store, plan, day),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
            Spacer(Modifier.width(12.dp))
            ProgressRing(
                progress = plan.progress,
                diameter = 68,
                thickness = 7,
                color = t.accent,
                trackColor = Color.White.copy(alpha = 0.18f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedNumber(
                        value = plan.doneCount,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        "of ${plan.totalCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }

    // ---- fit ---------------------------------------------------------------
    if (settings.planningHintsOn) {
        val hint = Coach.scheduleHint(plan, if (day == today) T.localMinuteOfDay() else 7 * 60)
        if (hint != null) {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background((if (plan.fits) Green else Coral).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Timer,
                            contentDescription = null,
                            tint = if (plan.fits) Green else Coral,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(hint, style = MaterialTheme.typography.bodyMedium, color = t.textPrimary)
                }
            }
        }
    }

    // ---- clashes -------------------------------------------------------------
    if (settings.conflictWarningsOn && plan.clashes.isNotEmpty()) {
        TassicCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Coral, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Booked over each other",
                    style = MaterialTheme.typography.titleSmall,
                    color = t.textPrimary
                )
            }
            Spacer(Modifier.height(6.dp))
            plan.clashes.take(3).forEach { clash ->
                Text(
                    "${clash.first.title} and ${clash.second.title} both want " +
                        Agenda.clockLabel(clash.second.startMinutes ?: 0) + ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.textSecondary,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }

    // ---- timeline --------------------------------------------------------------
    SectionTitle(
        eyebrow = "Schedule",
        title = "Timeline",
        subtitle = if (plan.timed.isEmpty()) "Nothing pinned to a time" else "${plan.timed.size} timed"
    )

    if (plan.timed.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.EventNote,
            title = "No fixed points today",
            hint = "Give a task a time — \"call the bank friday 10am\" — and it lands here."
        )
    } else {
        TassicCard {
            plan.timed.forEachIndexed { index, entry ->
                TimelineRow(
                    entry = entry,
                    isLast = index == plan.timed.lastIndex,
                    onToggle = {
                        store.toggleAgendaEntry(entry)
                        feedback.confirm(if (entry.done) "Reopened" else "Done")
                    }
                )
            }
        }
    }

    // ---- free time -----------------------------------------------------------
    if (plan.freeSlots.isNotEmpty() && day >= today) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            plan.freeSlots.take(3).forEach { slot ->
                MetricTile(
                    value = Nlp.durationLabel(slot.minutes),
                    label = "free from ${Agenda.clockLabel(slot.startMinutes)}",
                    tint = Blue,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // ---- anytime ---------------------------------------------------------------
    SectionTitle(
        eyebrow = "Flexible",
        title = "Anytime today",
        subtitle = "${plan.anytime.count { !it.done }} open"
    )
    if (plan.anytime.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Check,
            title = "Nothing loose",
            hint = "Everything on the board has a time or is already done."
        )
    } else {
        TassicCard {
            plan.anytime.forEachIndexed { index, entry ->
                EntryRow(entry) {
                    store.toggleAgendaEntry(entry)
                    feedback.confirm(if (entry.done) "Reopened" else "Done")
                }
                if (index != plan.anytime.lastIndex) SoftDivider()
            }
        }
    }

    // ---- suggested run -----------------------------------------------------------
    if (settings.planningHintsOn && day == today) {
        val placements = remember(plan) { Agenda.suggestPlacements(plan) }
        if (placements.isNotEmpty()) {
            TassicCard {
                SectionTitle(
                    eyebrow = "Suggestion",
                    title = "A way it could fit",
                    subtitle = "Longest first into the gaps you have — nothing is scheduled for you"
                )
                Spacer(Modifier.height(8.dp))
                placements.take(5).forEach { (entry, start) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            Agenda.clockLabel(start),
                            style = MaterialTheme.typography.labelLarge,
                            color = t.accentDeep,
                            modifier = Modifier.width(54.dp)
                        )
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = t.textPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Pill(Nlp.durationLabel(entry.durationMinutes))
                    }
                }
            }
        }
    }

    if (day == today) {
        GhostButton("Open Insights for the four-week picture", { onOpenTab(Tab.INSIGHTS) })
    }
}

@Composable
private fun NavArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val t = LocalTokens.current
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(t.cardSunken)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = t.textSecondary, modifier = Modifier.size(20.dp))
    }
}

/** A timed entry: clock gutter, connector rail, then the content. */
@Composable
private fun TimelineRow(entry: AgendaEntry, isLast: Boolean, onToggle: () -> Unit) {
    val t = LocalTokens.current
    val tint = accentColor(entry.accent)
    Row(Modifier.fillMaxWidth()) {
        Column(
            Modifier.width(50.dp).padding(top = 2.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                Agenda.clockLabel(entry.startMinutes ?: 0),
                style = MaterialTheme.typography.labelLarge,
                color = if (entry.done) t.textTertiary else t.textPrimary
            )
            Text(
                Nlp.durationLabel(entry.durationMinutes),
                style = MaterialTheme.typography.labelSmall,
                color = t.textTertiary
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(if (entry.done) tint else Color.Transparent)
                    .border(2.dp, tint, CircleShape)
                    .pressable(onClick = onToggle)
            )
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(46.dp)
                        .background(t.hairline)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 14.dp)
                .clip(RoundedCornerShape(t.radiusControl.dp))
                .then(if (entry.readOnly) Modifier else Modifier.pressable(onClick = onToggle))
                .padding(vertical = 2.dp)
        ) {
            Text(
                entry.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (entry.done) t.textTertiary else t.textPrimary,
                textDecoration = if (entry.done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(Agenda.kindLabel(entry.kind), bg = tint.copy(alpha = if (t.dark) 0.22f else 0.14f), fg = tint)
                if (entry.subtitle.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        entry.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** An untimed entry — the same information without a clock position. */
@Composable
private fun EntryRow(entry: AgendaEntry, onToggle: () -> Unit) {
    val t = LocalTokens.current
    val tint = accentColor(entry.accent)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (entry.readOnly) Modifier else Modifier.pressable(onClick = onToggle))
            .padding(vertical = 9.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Imported events and birthdays get a marker rather than a checkbox: a
        // tick target that silently refuses to tick is worse than none.
        Box(
            Modifier
                .size(23.dp)
                .clip(RoundedCornerShape(if (entry.readOnly) 999.dp else 8.dp))
                .background(if (entry.done) tint else if (entry.readOnly) tint.copy(alpha = 0.22f) else Color.Transparent)
                .border(
                    2.dp,
                    if (entry.done || entry.readOnly) tint else t.hairline,
                    RoundedCornerShape(if (entry.readOnly) 999.dp else 8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (entry.done && !entry.readOnly) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (entry.done) t.textTertiary else t.textPrimary,
                textDecoration = if (entry.done) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.subtitle.isNotBlank()) {
                Text(
                    entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = t.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Pill(Agenda.kindLabel(entry.kind), bg = tint.copy(alpha = if (t.dark) 0.22f else 0.14f), fg = tint)
    }
}

// ------------------------------------------------------------------ week view

@Composable
private fun WeekView(
    weekOffset: Int,
    onShiftWeek: (Int) -> Unit,
    onEditWeekPlan: () -> Unit,
    onPickDay: (Long) -> Unit
) {
    val store = Graph.store
    val settings by store.settingsState.collectAsState()
    val todos by store.todos.items.collectAsState()
    val activity by store.activity.items.collectAsState()
    val t = LocalTokens.current
    val today = T.today()

    val start = remember(weekOffset, settings.weekStartsMonday) {
        Agenda.weekStart(today, settings.weekStartsMonday) + weekOffset * 7L
    }
    val summaries = remember(start, todos, activity) { Agenda.week(store, start) }
    val peak = (summaries.maxOfOrNull { it.total } ?: 0).coerceAtLeast(1)

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        NavArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous week") { onShiftWeek(-1) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (weekOffset) {
                    0 -> "This week"
                    1 -> "Next week"
                    -1 -> "Last week"
                    else -> "Week of ${T.shortDate(start)}"
                },
                style = MaterialTheme.typography.titleLarge,
                color = t.textPrimary
            )
            Text(
                "${T.shortDate(start)} – ${T.shortDate(start + 6)}",
                style = MaterialTheme.typography.bodySmall,
                color = t.textSecondary
            )
        }
        NavArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next week") { onShiftWeek(1) }
    }

    // What the week is *for* comes before what's in it. Putting the load chart
    // first made the week read as a capacity problem rather than a question of
    // whether the right things are moving.
    WeekPrioritiesCard(weekIndex = T.weekIndex(start), onEdit = onEditWeekPlan)

    val verdict = remember(start, todos, activity) {
        if (weekOffset == 0) Coach.weekVerdict(store, today) else null
    }
    if (verdict != null) {
        GlassCard {
            Text("Against your priorities", style = MaterialTheme.typography.labelSmall, color = t.textTertiary)
            Spacer(Modifier.height(2.dp))
            Text(verdict, style = MaterialTheme.typography.bodyMedium, color = t.textPrimary)
        }
    }

    TassicCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            summaries.forEach { summary ->
                val isToday = summary.day == today
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .pressable { onPickDay(summary.day) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Heights are computed rather than expressed as a fill
                    // fraction: a zero-progress day would ask for a 0f
                    // fraction, and an empty week for a zero-height bar.
                    val barHeight = 16f + (summary.total.toFloat() / peak.toFloat()) * 54f
                    val fillHeight = barHeight * summary.progress.coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .width(20.dp)
                            .height(barHeight.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(t.hairline),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (fillHeight > 0f) {
                            Box(
                                Modifier
                                    .width(20.dp)
                                    .height(fillHeight.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (summary.progress >= 1f) Green else t.accent)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        T.dayName(summary.day).take(1),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isToday) t.accentDeep else t.textSecondary
                    )
                    Text(
                        "${summary.total}",
                        style = MaterialTheme.typography.labelSmall,
                        color = t.textTertiary
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Tap a column to open that day. Bar height is how much is on the board; the fill is how much is closed.",
            style = MaterialTheme.typography.bodySmall,
            color = t.textSecondary
        )
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricTile("${summaries.sumOf { it.done }}", "closed", tint = Green, modifier = Modifier.weight(1f))
        MetricTile("${summaries.sumOf { it.total - it.done }}", "open", tint = Blue, modifier = Modifier.weight(1f))
        MetricTile(
            Nlp.durationLabel(summaries.sumOf { it.loadMinutes }),
            "left this week",
            tint = t.accentDeep,
            modifier = Modifier.weight(1f)
        )
    }

    // ---- the heaviest day is worth naming ------------------------------------
    val heaviest = summaries.filter { it.day >= today }.maxByOrNull { it.loadMinutes }
    if (heaviest != null && heaviest.loadMinutes > 0) {
        GlassCard {
            Text(
                "Heaviest day ahead",
                style = MaterialTheme.typography.labelSmall,
                color = t.textTertiary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${T.dayNameFullOf(heaviest.day)} carries ${Nlp.durationLabel(heaviest.loadMinutes)} across ${heaviest.total - heaviest.done} open item(s).",
                style = MaterialTheme.typography.bodyMedium,
                color = t.textPrimary
            )
        }
    }

    // ---- habit grid --------------------------------------------------------------
    val habits = store.activeHabits()
    if (habits.isNotEmpty()) {
        SectionTitle(eyebrow = "Consistency", title = "Habits this week")
        TassicCard {
            habits.forEach { habit ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        habit.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.textPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (0..6).forEach { offset ->
                            val d = start + offset
                            val due = store.habitDueOn(habit, d)
                            val kept = due && store.habitDoneOn(habit, d)
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        when {
                                            kept -> habitColor(habit.color)
                                            due -> t.hairline
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        if (due) Color.Transparent else t.hairline.copy(alpha = 0.6f),
                                        RoundedCornerShape(5.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- habits view

@Composable
private fun HabitsView(onEdit: (Habit) -> Unit, onNew: () -> Unit) {
    val store = Graph.store
    val t = LocalTokens.current
    val feedback = rememberFeedback()
    val habits by store.habits.items.collectAsState()
    val activity by store.activity.items.collectAsState()
    val today = T.today()

    val pulses = remember(habits, activity, today) { Coach.allPulses(store, today) }
    val dueToday = pulses.filter { store.habitDueOn(it.habit, today) }
    val keptToday = dueToday.count { it.doneToday }
    val insights = remember(habits, activity, today) { Coach.habitInsights(store, today) }

    InkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("HABITS", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
                Spacer(Modifier.height(4.dp))
                Text(
                    if (dueToday.isEmpty()) {
                        "Nothing due today."
                    } else {
                        "$keptToday of ${dueToday.size} kept today."
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                val best = pulses.maxByOrNull { it.streak }
                if (best != null && best.streak > 0) {
                    Text(
                        "Longest run: ${best.habit.name} at ${best.streak} days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            }
            ProgressRing(
                progress = if (dueToday.isEmpty()) 0f else keptToday.toFloat() / dueToday.size.toFloat(),
                diameter = 64,
                thickness = 7,
                color = t.accent,
                trackColor = Color.White.copy(alpha = 0.18f)
            ) {
                Text(
                    "$keptToday",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }
        }
    }

    if (pulses.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Repeat,
            title = "No habits yet",
            hint = "Habits are the repeating positives — read 20 minutes, stretch, call home on Sundays.",
            actionText = "Add your first habit",
            onAction = onNew
        )
        return
    }

    SectionTitle(eyebrow = "Today", title = "Keep the run going", subtitle = "Tap the square to log")
    TassicCard {
        pulses.forEachIndexed { index, pulse ->
            HabitRow(
                pulse = pulse,
                onTick = {
                    store.tickHabit(pulse.habit)
                    feedback.confirm("${pulse.habit.name} logged")
                },
                onUntick = { store.untickHabit(pulse.habit) },
                onEdit = { onEdit(pulse.habit) }
            )
            if (index != pulses.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }

    SectionTitle(eyebrow = "Four weeks", title = "What's actually holding")
    TassicCard {
        pulses.forEach { pulse ->
            Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pulse.habit.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.textPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (pulse.dueLast28 == 0) "—" else "${pulse.consistency}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = when {
                            pulse.consistency >= 80 -> Green
                            pulse.consistency >= 45 -> AmberDeep
                            else -> Coral
                        }
                    )
                }
                Spacer(Modifier.height(6.dp))
                HabitTrace(
                    history = store.habitHistory(pulse.habit, 28, today),
                    tint = habitColor(pulse.habit.color)
                )
            }
        }
    }

    if (insights.isNotEmpty()) {
        SectionTitle(eyebrow = "Observed", title = "Patterns in your habit log")
        insights.take(4).forEach { insight ->
            GlassCard {
                Text(insight.title, style = MaterialTheme.typography.titleSmall, color = t.textPrimary)
                Spacer(Modifier.height(3.dp))
                Text(insight.detail, style = MaterialTheme.typography.bodySmall, color = t.textSecondary)
            }
        }
    }

    SunkenBox {
        Text(
            "Habit ticks are written to the same activity log as tasks and training, so they count toward momentum and show up in the heatmap on Insights.",
            style = MaterialTheme.typography.bodySmall,
            color = t.textSecondary
        )
    }
}

private fun accentColor(key: String): Color = when (key) {
    "green" -> Green
    "amber" -> AmberDeep
    "coral" -> Coral
    "violet" -> Violet
    else -> Blue
}
