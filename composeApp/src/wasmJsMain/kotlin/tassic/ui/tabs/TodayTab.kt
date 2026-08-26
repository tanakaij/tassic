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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Today
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
import tassic.data.Graph
import tassic.data.PracticeKind
import tassic.data.RecoveryHabit
import tassic.data.T
import tassic.data.TodoItem
import tassic.data.WorkoutItem
import tassic.ui.Tab
import tassic.ui.components.CheckRow
import tassic.ui.components.DestructiveButton
import tassic.ui.components.EmptyState
import tassic.ui.components.GhostButton
import tassic.ui.components.IconActionBtn
import tassic.ui.components.ItemMenu
import tassic.ui.components.Pill
import tassic.ui.components.rememberState
import tassic.ui.components.SectionHeader
import tassic.ui.components.StatTile
import tassic.ui.components.TabScaffold
import tassic.ui.components.TassicCard
import tassic.ui.components.TodoSheet
import tassic.ui.components.WorkoutSheet
import tassic.ui.components.HabitSheet
import tassic.ui.components.RelapseSheet
import tassic.ui.components.ShapeSheet
import tassic.ui.theme.AmberDeep
import tassic.ui.theme.Blue
import tassic.ui.theme.Green
import tassic.ui.theme.Ink
import tassic.ui.theme.Muted
import tassic.ui.theme.Navy
import tassic.ui.theme.Orange
import tassic.ui.theme.SkySoft

@Composable
fun TodayTab(onOpenTab: (Tab) -> Unit = {}) {
    val store = Graph.store
    val practice by store.practice.items.collectAsState()
    val todos by store.todos.items.collectAsState()
    val workouts by store.workouts.items.collectAsState()
    val habits by store.recovery.items.collectAsState()
    val today = T.today()

    var todoOpen by rememberState(false)
    var todoEdit by rememberState<TodoItem?>(null)
    var workoutOpen by rememberState(false)
    var workoutEdit by rememberState<WorkoutItem?>(null)
    var habitOpen by rememberState(false)
    var habitEdit by rememberState<RecoveryHabit?>(null)
    var relapseFor by rememberState<RecoveryHabit?>(null)
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

    TabScaffold(
        fabIcon = Icons.Filled.Add,
        fabLabel = "New Task",
        onFab = { todoEdit = null; todoOpen = true }
    ) {
        // ---- Focus of the Day (CAGED) ------------------------------------
        TassicCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Focus of the Day", style = MaterialTheme.typography.headlineSmall, color = Navy)
                    Text("${T.dayName(today)} · ${T.dateLabel(today)}", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Pill("CAGED", bg = SkySoft)
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
                Text(shape.title, style = MaterialTheme.typography.titleLarge, color = Ink, modifier = Modifier.padding(top = 8.dp))
                if (shape.detail.isNotBlank()) {
                    Text(shape.detail, style = MaterialTheme.typography.bodySmall, color = Muted)
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
                    GhostButton("Music Studio →", { onOpenTab(Tab.MUSIC) })
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
                StatTile(mode?.title ?: "—", "Mode today", Blue, modifier = Modifier.weight(1f))
                StatTile(key?.title ?: "—", "Key focus", AmberDeep, modifier = Modifier.weight(1f))
                StatTile("$modulesDone", "Modules done", Green, modifier = Modifier.weight(1f))
            }
        }

        // ---- Calisthenics ---------------------------------------------------
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
                    hint = "Rest day — or add an exercise below."
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

        // ---- Recovery ---------------------------------------------------------
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
                        Text(h.name, style = MaterialTheme.typography.titleMedium, color = Navy)
                        Text(
                            "Best ${h.bestStreak} · ${h.relapses} reset${if (h.relapses == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall, color = Muted
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

        // ---- To-Dos ------------------------------------------------------------
        TassicCard {
            SectionHeader(
                title = "To-Dos",
                subtitle = "${openTodos.size} open",
                trailing = {
                    IconActionBtn(Icons.Filled.Add, "New task", tint = Navy) { todoEdit = null; todoOpen = true }
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
                            if (t.reminderMinutesBefore != null) append(" · 🔔")
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
                        Pill(t.priority.name.lowercase().replaceFirstChar { it.uppercase() }, bg = SkySoft)
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

        // ---- Sheets ---------------------------------------------------------------
        if (todoOpen) TodoSheet(todoEdit) { todoOpen = false }
        if (workoutOpen) WorkoutSheet(workoutEdit) { workoutOpen = false }
        if (habitOpen) HabitSheet(habitEdit) { habitOpen = false }
        relapseFor?.let { h -> RelapseSheet(h) { relapseFor = null } }
        shapeEdit?.let { s -> ShapeSheet(s) { shapeEdit = null } }
    }
}
