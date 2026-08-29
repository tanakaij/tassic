package tassic.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Today
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tassic.data.Coach
import tassic.data.Graph
import tassic.data.Habit
import tassic.data.Insights
import tassic.data.PracticeKind
import tassic.data.RecoveryHabit
import tassic.data.T
import tassic.data.TodoItem
import tassic.data.WorkoutItem
import tassic.ui.Tab
import tassic.ui.components.AnimatedNumber
import tassic.ui.components.CheckRow
import tassic.ui.components.DestructiveButton
import tassic.ui.components.EmptyState
import tassic.ui.components.GhostButton
import tassic.ui.components.IconActionBtn
import tassic.ui.components.ItemMenu
import tassic.ui.components.LockGate
import tassic.ui.components.Pill
import tassic.ui.components.rememberState
import tassic.ui.components.SectionHeader
import tassic.ui.components.SegmentedControl
import tassic.ui.components.SelectChips
import tassic.ui.components.HeroCard
import tassic.ui.components.ProgressRing
import tassic.ui.components.Sparkline
import tassic.ui.components.StatTile
import tassic.ui.components.TabScaffold
import tassic.ui.components.TassicCard
import tassic.ui.components.TodoSheet
import tassic.ui.components.WorkoutSheet
import tassic.ui.components.HabitSheet
import tassic.ui.components.HabitEditorSheet
import tassic.ui.components.HabitRow
import tassic.ui.components.TodaySignals
import tassic.ui.components.RelapseSheet
import tassic.ui.components.ShapeSheet
import tassic.ui.theme.surfaceSoft
import tassic.ui.theme.textMuted
import tassic.ui.theme.textInk
import tassic.ui.theme.AmberDeep
import tassic.ui.theme.Blue
import tassic.ui.theme.Green
import tassic.ui.theme.Orange
import tassic.ui.theme.LocalTokens

@Composable
fun TodayTab(onOpenTab: (Tab) -> Unit = {}) {
    val store = Graph.store
    val practice by store.practice.items.collectAsState()
    val todos by store.todos.items.collectAsState()
    val workouts by store.workouts.items.collectAsState()
    val habits by store.recovery.items.collectAsState()
    val activity by store.activity.items.collectAsState()
    val today = T.today()

    var todoOpen by rememberState(false)
    var todoEdit by rememberState<TodoItem?>(null)
    var workoutOpen by rememberState(false)
    var workoutEdit by rememberState<WorkoutItem?>(null)
    var habitOpen by rememberState(false)
    var habitEdit by rememberState<RecoveryHabit?>(null)
    var relapseFor by rememberState<RecoveryHabit?>(null)
    var trackerOpen by rememberState(false)
    var trackerEdit by rememberState<Habit?>(null)
    var shapeEdit by rememberState<tassic.data.PracticeItem?>(null)

    val shape = practice
        .filter { it.kind == PracticeKind.SHAPE && it.section == "guitar" }
        .firstOrNull { T.tagMatches(it.dayTag, today) }
    val subtasks = practice.filter { it.kind == PracticeKind.SUBTASK && shape != null && it.parentId == shape.id }

    val modes = practice.filter { it.kind == PracticeKind.MODE }.sortedBy { it.sortOrder }
    val mode = modes.getOrNull((T.dayOfYear(today) - 1).mod(modes.size))
    val keys = practice.filter { it.kind == PracticeKind.KEY }.sortedBy { it.sortOrder }
    val key = keys.getOrNull(T.dayOfYear(today).mod(keys.size))
    val modulesDone = practice.count { it.kind == PracticeKind.MODULE && it.doneEpochDay == today }

    val dueWorkouts = workouts.filter { T.tagMatches(it.dayTag, today) }
    val openTodos = todos.filter { !it.done }
        .sortedWith(compareBy({ it.dueEpochDay ?: Long.MAX_VALUE }, { -it.createdAt }))
    val recentlyDone = todos.filter { it.done }.take(3)
    val activeHabits = habits.filter { it.active }

    // Segmented view switcher (mirrors the pattern on Life & Goals / Music):
    // one screen's worth of cards per segment instead of five stacked cards
    // requiring a long scroll. Counts are live so the pills double as an
    // at-a-glance summary of each area.
    // Segments follow the modules the user actually turned on, so someone who
    // doesn't play an instrument never sees a CAGED shape and someone with no
    // recovery habit never sees a days-clean counter.
    val settings by store.settingsState.collectAsState()
    val views = listOfNotNull(
        "Practice".takeIf { settings.hasModule("MUSIC") },
        // Gated on both the module and the "habits on Today" preference. The
        // preference existed from the first build and was read by nothing.
        "Habits".takeIf { settings.hasModule("HABITS") && settings.habitsOnToday },
        "Fitness".takeIf { settings.hasModule("FITNESS") },
        "Recovery".takeIf { settings.hasModule("RECOVERY") },
        "Tasks".takeIf { settings.hasModule("TASKS") }
    ).ifEmpty { listOf("Tasks") }
    var view by rememberState(views.first())
    // The initial value is captured once, so turning a module off later could
    // leave `view` pointing at a segment that no longer exists — the switcher
    // would show nothing selected and the page would render empty.
    if (view !in views) view = views.first()
    val habitsDue = store.habitsDueToday(today)
    val habitsKept = habitsDue.count { store.habitDoneOn(it, today) }
    val viewCounts = mapOf(
        "Practice" to (subtasks.count { it.doneEpochDay == today }.let { "$it/${subtasks.size}" }),
        "Habits" to "$habitsKept/${habitsDue.size}",
        "Fitness" to "${dueWorkouts.count { it.doneEpochDay == today }}/${dueWorkouts.size}",
        "Recovery" to "${activeHabits.size}",
        "Tasks" to "${openTodos.size}"
    )

    // The brief is a pure function of the tables it reads, so keying the memo
    // on them keeps it live without recomputing on every frame.
    TabScaffold(
        fabIcon = when (view) {
            "Fitness" -> Icons.Filled.Add
            "Habits" -> Icons.Filled.Add
            "Recovery" -> Icons.Filled.Add
            "Tasks" -> Icons.Filled.Add
            else -> null
        },
        fabLabel = when (view) {
            "Fitness" -> "New Exercise"
            "Habits" -> "New Habit"
            "Recovery" -> "Track Habit"
            "Tasks" -> "New Task"
            else -> null
        },
        onFab = when (view) {
            "Fitness" -> ({ workoutEdit = null; workoutOpen = true })
            "Habits" -> ({ trackerEdit = null; trackerOpen = true })
            "Recovery" -> ({ habitEdit = null; habitOpen = true })
            "Tasks" -> ({ todoEdit = null; todoOpen = true })
            else -> null
        }
    ) {
        // ---- Daily brief -------------------------------------------------
        // The old Today tab opened straight into a checklist, which told you
        // what was on the list but nothing about how the day or the week was
        // actually going. This is the one card that answers that.
        // Keyed on the tables it reads so it recomputes when data changes
        // rather than on every recomposition — the engine walks the whole
        // activity log, which is too much work to redo per frame.
        val report = remember(todos, workouts, practice, habits, activity, today) {
            Insights.report(store, today)
        }
        val tokens = LocalTokens.current
        HeroCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${Insights.greeting(T.localHour())}, here's your day",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.onAccent.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        report.headline,
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.onAccent
                    )
                }
                Spacer(Modifier.width(12.dp))
                ProgressRing(
                    progress = report.momentum / 100f,
                    diameter = 74,
                    thickness = 8,
                    color = tokens.onAccent,
                    trackColor = tokens.onAccent.copy(alpha = 0.20f)
                ) {
                    AnimatedNumber(
                        value = report.momentum,
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.onAccent
                    )
                }
            }

            val top = report.nextActions.firstOrNull()
            if (top != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .background(tokens.onAccent.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "START WITH",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.onAccent.copy(alpha = 0.65f)
                        )
                        Text(
                            top.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = tokens.onAccent
                        )
                        Text(
                            top.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.onAccent.copy(alpha = 0.75f)
                        )
                    }
                    if (report.spark.size >= 4) {
                        Box(Modifier.width(78.dp)) {
                            Sparkline(
                                values = report.spark.takeLast(14),
                                height = 34,
                                color = tokens.onAccent,
                                showFill = false
                            )
                        }
                    }
                }
            }
        }

        // The hero above covers tasks, training and practice. These are the
        // domains its engine predates — calendar, habits, reading, people,
        // week priorities, good deeds — capped at three and hidden entirely
        // when none apply.
        val signals = remember(todos, habits, activity, practice, workouts, today) {
            Coach.signals(store, today)
        }
        TodaySignals(signals) { name ->
            Tab.entries.firstOrNull { it.name == name }?.let(onOpenTab)
        }

        SegmentedControl(
            options = views,
            selected = view,
            badge = { viewCounts[it] },
            onSelect = { view = it }
        )

        // ---- Focus of the Day (CAGED) ------------------------------------
        if (view == "Practice") {
        TassicCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Focus of the Day", style = MaterialTheme.typography.headlineSmall, color = textInk)
                    Text("${T.dayName(today)} · ${T.dateLabel(today)}", style = MaterialTheme.typography.bodySmall, color = textMuted)
                }
                Pill("CAGED", bg = surfaceSoft)
            }
            if (shape == null) {
                EmptyState(
                    icon = Icons.Filled.Today,
                    title = "No shape scheduled",
                    hint = "Add a shape preset in the Music Studio.",
                    actionText = "Open Studio",
                    onAction = { onOpenTab(Tab.MUSIC) }
                )
            } else {
                Text(shape.title, style = MaterialTheme.typography.titleLarge, color = textInk, modifier = Modifier.padding(top = 8.dp))
                if (shape.detail.isNotBlank()) {
                    Text(shape.detail, style = MaterialTheme.typography.bodySmall, color = textMuted)
                }
                subtasks.forEach { sub ->
                    CheckRow(
                        title = sub.title,
                        checked = sub.doneEpochDay == today,
                        onChecked = { store.togglePracticeDone(sub) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
                    GhostButton("Edit shape", { shapeEdit = shape })
                    GhostButton("Music Studio", { onOpenTab(Tab.MUSIC) }, trailingIcon = Icons.AutoMirrored.Filled.ArrowForward)
                }
            }
        }

        // ---- Keyboard daily rotation --------------------------------------
        TassicCard {
            SectionHeader(
                title = "Keyboard",
                subtitle = "Mode rotation · 12-key cycle · modules",
                trailing = {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Blue)
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(mode?.title ?: "-", "Mode today", Blue, modifier = Modifier.weight(1f))
                StatTile(key?.title ?: "-", "Key focus", AmberDeep, modifier = Modifier.weight(1f))
                StatTile("$modulesDone", "Modules done", Green, modifier = Modifier.weight(1f))
            }
        }
        }

        // ---- Habits ---------------------------------------------------------
        // The repeating positives. They sit here rather than only on Plan
        // because a habit you have to navigate to is a habit you'll forget —
        // the whole mechanism depends on being in front of you on the screen
        // you already open first.
        if (view == "Habits") {
            val pulses = remember(activity, today) { Coach.allPulses(store, today) }
            TassicCard {
                SectionHeader(
                    title = "Habits",
                    subtitle = if (habitsDue.isEmpty()) "Nothing due today" else "$habitsKept of ${habitsDue.size} kept today",
                    trailing = {
                        Icon(Icons.Filled.Repeat, contentDescription = null, tint = Blue)
                    }
                )
                if (pulses.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.Repeat,
                        title = "No habits yet",
                        hint = "Read 20 minutes, stretch, call home on Sundays — the small repeating things.",
                        actionText = "Add a habit",
                        onAction = { trackerEdit = null; trackerOpen = true }
                    )
                } else {
                    pulses.forEachIndexed { index, pulse ->
                        HabitRow(
                            pulse = pulse,
                            onTick = { store.tickHabit(pulse.habit) },
                            onUntick = { store.untickHabit(pulse.habit) },
                            onEdit = { trackerEdit = pulse.habit; trackerOpen = true }
                        )
                        if (index != pulses.lastIndex) Spacer(Modifier.height(8.dp))
                    }
                }
            }
            GhostButton("See four-week consistency in Plan", { onOpenTab(Tab.PLAN) })
        }

        // ---- Calisthenics ---------------------------------------------------
        if (view == "Fitness") {
        TassicCard {
            SectionHeader(
                title = "Calisthenics",
                subtitle = "Equipment-free bodyweight",
                trailing = { StatTile("${store.workoutStreak()}", "day streak", Orange) }
            )
            if (dueWorkouts.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.FitnessCenter,
                    title = "Nothing scheduled",
                    hint = "Rest day - or add an exercise below."
                )
            } else {
                dueWorkouts.forEach { w ->
                    var menu by rememberState(false)
                    CheckRow(
                        title = w.name,
                        subtitle = "${w.sets} × ${w.reps} ${w.unit}",
                        checked = w.doneEpochDay == today,
                        onChecked = { store.toggleWorkoutDone(w) },
                        trailing = {
                            IconActionBtn(Icons.Filled.MoreVert, "Options") { menu = true }
                            ItemMenu(
                                expanded = menu,
                                onDismiss = { menu = false },
                                onEdit = { workoutEdit = w; workoutOpen = true },
                                onDelete = { store.deleteWorkout(w.id) }
                            )
                        }
                    )
                }
            }
            GhostButton("+ Add exercise", { workoutEdit = null; workoutOpen = true })
        }
        }

        // ---- Recovery ---------------------------------------------------------
        // Behind the same PIN as the recovery history in the journal: a
        // days-clean counter on the default screen is the most exposed thing
        // in the app.
        if (view == "Recovery") {
            LockGate("RECOVERY") {
            Column {
        SectionHeader(title = "Recovery", subtitle = "Days clean, tracked honestly")
        if (habits.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.SelfImprovement,
                title = "No habits tracked",
                hint = "Track something you're breaking free from.",
                actionText = "+ Track a habit",
                onAction = { habitEdit = null; habitOpen = true }
            )
        }
        habits.filter { it.active }.forEach { h ->
            TassicCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(h.name, style = MaterialTheme.typography.titleMedium, color = textInk)
                        Text(
                            "Best ${h.bestStreak} · ${h.relapses} reset${if (h.relapses == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall, color = textMuted
                        )
                    }
                    StatTile("${store.daysClean(h)}", "days clean", Green)
                    Spacer(Modifier.width(8.dp))
                    IconActionBtn(Icons.Filled.Edit, "Edit habit") { habitEdit = h; habitOpen = true }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    DestructiveButton("Log relapse", { relapseFor = h })
                    GhostButton("Reset counter", {
                        store.updateRecoveryHabit(h.copy(startEpochDay = today))
                    })
                }
            }
        }
        if (habits.isNotEmpty()) {
            GhostButton("+ Track another habit", { habitEdit = null; habitOpen = true })
        }
            }
            }
        }

        // ---- To-Dos ------------------------------------------------------------
        if (view == "Tasks") {
        TassicCard {
            SectionHeader(
                title = "To-Dos",
                subtitle = "${openTodos.size} open",
                trailing = {
                    IconActionBtn(Icons.Filled.Add, "New task", tint = textInk) { todoEdit = null; todoOpen = true }
                }
            )
            if (openTodos.isEmpty() && recentlyDone.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Today,
                    title = "All clear",
                    hint = "Tap New Task to plan your day."
                )
            }
            openTodos.forEach { t ->
                var menu by rememberState(false)
                CheckRow(
                    title = t.title,
                    subtitle = buildString {
                        t.dueEpochDay?.let { day ->
                            append("Due ${T.relativeDays(day, today)}")
                            t.dueTimeMinutes?.let { mins -> append(" · ${T.timeLabel(mins * 60_000L)}") }
                        }
                        if (t.recurrence.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(store.recurrenceLabel(t.recurrence))
                        }
                        if (t.tags.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append(t.tags.joinToString(", "))
                        }
                    }.ifBlank { null },
                    checked = false,
                    onChecked = { store.toggleTodo(t) },
                    tint = Blue,
                    trailing = {
                        if (t.reminderMinutesBefore != null) {
                            // Drawn as a vector icon rather than the "🔔" emoji: the
                            // bundled web/wasm font doesn't reliably cover emoji glyphs,
                            // which rendered as a tofu box.
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = "Reminder set",
                                tint = textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Pill(t.priority.name.lowercase().replaceFirstChar { it.uppercase() }, bg = surfaceSoft)
                        Spacer(Modifier.width(4.dp))
                        IconActionBtn(Icons.Filled.MoreVert, "Options") { menu = true }
                        ItemMenu(
                            expanded = menu,
                            onDismiss = { menu = false },
                            onEdit = { todoEdit = t; todoOpen = true },
                            onDelete = { store.deleteTodo(t.id) }
                        )
                    }
                )
            }
            recentlyDone.forEach { t ->
                CheckRow(
                    title = t.title,
                    checked = true,
                    onChecked = { store.toggleTodo(t) },
                    tint = Green
                )
            }
        }
        }

        // ---- Sheets ---------------------------------------------------------------
        if (todoOpen) TodoSheet(todoEdit) { todoOpen = false }
        if (workoutOpen) WorkoutSheet(workoutEdit) { workoutOpen = false }
        if (habitOpen) HabitSheet(habitEdit) { habitOpen = false }
        if (trackerOpen) HabitEditorSheet(trackerEdit) { trackerOpen = false }
        relapseFor?.let { h -> RelapseSheet(h) { relapseFor = null } }
        shapeEdit?.let { s -> ShapeSheet(s) { shapeEdit = null } }
    }
}
