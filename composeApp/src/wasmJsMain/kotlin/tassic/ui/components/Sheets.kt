@file:OptIn(ExperimentalMaterial3Api::class)

package tassic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import tassic.data.AlbumGoal
import tassic.data.GoalItem
import tassic.data.Graph
import tassic.data.HabitLog
import tassic.data.Horizon
import tassic.data.PracticeItem
import tassic.data.PracticeKind
import tassic.data.Priority
import tassic.data.RecoveryHabit
import tassic.data.T
import tassic.data.TodoItem
import tassic.data.WorkoutItem
import tassic.ui.theme.Amber
import tassic.ui.theme.Coral
import tassic.ui.theme.Muted
import tassic.ui.theme.Navy
import tassic.ui.theme.SkyDeep

// ---------------------------------------------------------------- scaffold

/** Standard tab body: scrollable content + contextual extended FAB. */
@Composable
fun TabScaffold(
    fabIcon: ImageVector?,
    fabLabel: String?,
    onFab: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content
        )
        if (onFab != null && fabLabel != null && fabIcon != null) {
            ExtendedFloatingActionButton(
                onClick = onFab,
                icon = { Icon(fabIcon, contentDescription = null) },
                text = { Text(fabLabel) },
                containerColor = Amber,
                contentColor = Navy,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- sheet shell

/**
 * Animated modal bottom sheet: drag handle, dimmed scrim + backdrop dismiss
 * (defaults), rounded top corners, keyboard (IME) and gesture-bar safe.
 */
@Composable
fun TassicSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(44.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(SkyDeep)
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 30.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Navy)
            Text(
                "Every preset is editable — change anything, or delete it and add your own.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
            )
            content()
        }
    }
}

@Composable
fun SheetActions(
    saveLabel: String = "Save",
    saveEnabled: Boolean = true,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    destructive: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
    ) {
        GhostButton("Cancel", onCancel)
        if (destructive) {
            DestructiveButton(saveLabel, onSave, enabled = saveEnabled)
        } else {
            PrimaryButton(saveLabel, onSave, enabled = saveEnabled)
        }
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
}

// ---------------------------------------------------------------- todo

private enum class DueChoice(val label: String) { NONE("No date"), TODAY("Today"), TOMORROW("Tomorrow"), WEEK("Next week") }

@Composable
fun TodoSheet(edit: TodoItem?, onDismiss: () -> Unit) {
    val store = Graph.store
    val ed = edit
    var title by rememberState(ed?.title ?: "")
    var notes by rememberState(ed?.notes ?: "")
    var priority by rememberState(ed?.priority ?: Priority.NORMAL)
    var due by rememberState(
        when (ed?.dueEpochDay) {
            null -> DueChoice.NONE
            T.today() -> DueChoice.TODAY
            T.today() + 1 -> DueChoice.TOMORROW
            else -> DueChoice.WEEK
        }
    )
    var tags by rememberState(ed?.tags?.joinToString(", ") ?: "")

    TassicSheet(title = if (edit == null) "New Task" else "Edit Task", onDismiss = onDismiss) {
        LabeledField(title, { title = it }, "Title", placeholder = "What needs doing?")
        FieldLabel("Notes")
        LabeledField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
        FieldLabel("Priority")
        SelectChips(Priority.entries.toList(), priority) { priority = it }
        FieldLabel("Due")
        SelectChips(DueChoice.entries.toList(), due) { due = it }
        LabeledField(tags, { tags = it }, "Tags", placeholder = "comma, separated")
        SheetActions(
            onSave = {
                val dueDay = when (due) {
                    DueChoice.TODAY -> T.today()
                    DueChoice.TOMORROW -> T.today() + 1
                    DueChoice.WEEK -> T.today() + 7
                    DueChoice.NONE -> null
                }
                val base = edit ?: TodoItem(createdAt = T.now())
                val item = base.copy(
                    title = title.trim(),
                    notes = notes.trim(),
                    priority = priority,
                    dueEpochDay = dueDay,
                    tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                )
                if (edit == null) store.addTodo(item) else store.updateTodo(item)
                onDismiss()
            },
            onCancel = onDismiss,
            saveEnabled = title.isNotBlank()
        )
    }
}

// ---------------------------------------------------------------- goal

@Composable
fun GoalSheet(edit: GoalItem?, onDismiss: () -> Unit) {
    val store = Graph.store
    var title by rememberState(edit?.title ?: "")
    var description by rememberState(edit?.description ?: "")
    var horizon by rememberState(edit?.horizon ?: Horizon.SHORT)
    var category by rememberState(edit?.category ?: "General")
    var progress by rememberState(edit?.progress ?: 0)
    var targetChoice by rememberState(
        when (edit?.targetEpochDay) {
            null -> 0
            T.today() + 30 -> 30
            T.today() + 90 -> 90
            T.today() + 180 -> 180
            T.today() + 365 -> 365
            else -> -1
        }
    )

    TassicSheet(title = if (edit == null) "New Life Goal" else "Edit Goal", onDismiss = onDismiss) {
        LabeledField(title, { title = it }, "Goal", placeholder = "e.g. Land a GeoDev role")
        FieldLabel("Description")
        LabeledField(description, { description = it }, "Description", singleLine = false, minLines = 2)
        FieldLabel("Horizon")
        SelectChips(Horizon.entries.toList(), horizon) { horizon = it }
        LabeledField(category, { category = it }, "Category", placeholder = "Career, Music, Health…")
        FieldLabel("Progress — $progress%")
        Stepper("Progress", progress, { progress = it }, range = 0..100, suffix = "%")
        FieldLabel("Target date")
        SelectChips(listOf(0, 30, 90, 180, 365), targetChoice, label = {
            when (it) {
                0 -> "No target"
                30 -> "1 month"
                90 -> "3 months"
                180 -> "6 months"
                else -> "1 year"
            }
        }) { targetChoice = it }
        SheetActions(
            onSave = {
                val base = edit ?: GoalItem(createdAt = T.now())
                val item = base.copy(
                    title = title.trim(),
                    description = description.trim(),
                    horizon = horizon,
                    category = category.trim().ifEmpty { "General" },
                    progress = progress,
                    targetEpochDay = if (targetChoice > 0) T.today() + targetChoice else null
                )
                if (edit == null) store.addGoal(item) else store.updateGoal(item)
                onDismiss()
            },
            onCancel = onDismiss,
            saveEnabled = title.isNotBlank()
        )
    }
}

// ---------------------------------------------------------------- workout

@Composable
fun WorkoutSheet(edit: WorkoutItem?, onDismiss: () -> Unit) {
    val store = Graph.store
    var name by rememberState(edit?.name ?: "")
    var sets by rememberState(edit?.sets ?: 3)
    var reps by rememberState(edit?.reps ?: 10)
    var unit by rememberState(edit?.unit ?: "reps")
    var dayTag by rememberState(edit?.dayTag ?: "ALL")

    TassicSheet(title = if (edit == null) "New Exercise" else "Edit Exercise", onDismiss = onDismiss) {
        LabeledField(name, { name = it }, "Exercise", placeholder = "e.g. Archer Push-ups")
        Stepper("Sets", sets, { sets = it }, range = 1..20)
        Stepper("Reps / duration", reps, { reps = it }, range = 1..999)
        FieldLabel("Unit")
        SelectChips(listOf("reps", "seconds", "meters"), unit) { unit = it }
        FieldLabel("Training day")
        SelectChips(listOf("ALL", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN") + listOf("WEEKDAY"), dayTag) { dayTag = it }
        SheetActions(
            onSave = {
                val base = edit ?: WorkoutItem(createdAt = T.now(), sortOrder = 999)
                val item = base.copy(name = name.trim(), sets = sets, reps = reps, unit = unit, dayTag = dayTag)
                if (edit == null) store.addWorkout(item) else store.updateWorkout(item)
                onDismiss()
            },
            onCancel = onDismiss,
            saveEnabled = name.isNotBlank()
        )
    }
}

// ---------------------------------------------------------------- recovery

@Composable
fun HabitSheet(edit: RecoveryHabit?, onDismiss: () -> Unit) {
    val store = Graph.store
    var name by rememberState(edit?.name ?: "")
    TassicSheet(title = if (edit == null) "Track a Habit" else "Edit Habit", onDismiss = onDismiss) {
        LabeledField(name, { name = it }, "Habit to break", placeholder = "e.g. Late-night snacking")
        SheetActions(
            onSave = {
                val base = edit ?: RecoveryHabit(startEpochDay = T.today(), createdAt = T.now())
                if (edit == null) store.addRecoveryHabit(base.copy(name = name.trim()))
                else store.updateRecoveryHabit(base.copy(name = name.trim()))
                onDismiss()
            },
            onCancel = onDismiss,
            saveEnabled = name.isNotBlank()
        )
    }
}

@Composable
fun RelapseSheet(habit: RecoveryHabit, onDismiss: () -> Unit) {
    val store = Graph.store
    val snackbar = LocalSnackbar.current
    val scope = rememberSheetScope()
    var trigger by rememberState("")
    var reflection by rememberState("")

    TassicSheet(title = "Log a relapse", onDismiss = onDismiss) {
        Text(
            "${habit.name} · ${store.daysClean(habit)} days clean resets to day 1. Honest tracking builds real awareness.",
            style = MaterialTheme.typography.bodyMedium, color = Muted
        )
        FieldLabel("Trigger (required)")
        LabeledField(trigger, { trigger = it }, "Trigger", placeholder = "Where were you? Who were you with?")
        FieldLabel("Reflection")
        LabeledField(reflection, { reflection = it }, "What will you do differently?", singleLine = false, minLines = 3)
        SheetActions(
            saveLabel = "Log & reset",
            destructive = true,
            onSave = {
                store.logRelapse(habit, trigger.trim() + if (reflection.isNotBlank()) " — " + reflection.trim() else "")
                scope.launchSnackbar("Relapse logged. Day 1 starts now — you've got this.")
                onDismiss()
            },
            onCancel = onDismiss,
            saveEnabled = trigger.trim().length >= 3
        )
    }
}

@Composable
fun HabitLogSheet(log: HabitLog, habitName: String, onDismiss: () -> Unit) {
    TassicSheet(title = "Relapse · $habitName", onDismiss = onDismiss) {
        Text(T.fullLabel(log.loggedAt), style = MaterialTheme.typography.labelLarge, color = Coral)
        Text(log.triggerNote, style = MaterialTheme.typography.bodyLarge, color = Navy, modifier = Modifier.padding(top = 8.dp))
        SheetActions(saveLabel = "Close", onSave = onDismiss, onCancel = onDismiss)
    }
}

// ---------------------------------------------------------------- music

@Composable
fun AlbumSheet(edit: AlbumGoal?, onDismiss: () -> Unit) {
    val store = Graph.store
    var album by rememberState(edit?.album ?: "")
    var artist by rememberState(edit?.artist ?: "")
    var total by rememberState(edit?.totalTracks ?: 12)
    TassicSheet(title = if (edit == null) "New Album Goal" else "Edit Album Goal", onDismiss = onDismiss) {
        LabeledField(album, { album = it }, "Album", placeholder = "Monthly gospel album goal")
        LabeledField(artist, { artist = it }, "Artist")
        Stepper("Tracks in album", total, { total = it }, range = 1..50)
        SheetActions(
            onSave = {
                val base = edit ?: AlbumGoal(createdAt = T.now())
                val item = base.copy(album = album.trim(), artist = artist.trim(), totalTracks = total)
                if (edit == null) store.addAlbum(item) else store.updateAlbum(item)
                onDismiss()
            },
            onCancel = onDismiss,
            saveEnabled = album.isNotBlank()
        )
    }
}

@Composable
fun SongLogSheet(onDismiss: () -> Unit) {
    val store = Graph.store
    var title by rememberState("")
    TassicSheet(title = "Log a learned song", onDismiss = onDismiss) {
        LabeledField(title, { title = it }, "Song title", placeholder = "e.g. Amazing Grace (key of Eb)")
        SheetActions(
            onSave = {
                store.addPractice(
                    PracticeItem(
                        section = "guitar", kind = PracticeKind.SONG, title = title.trim(),
                        doneEpochDay = T.today(), doneCount = 1, createdAt = T.now(), sortOrder = 999
                    )
                )
                onDismiss()
            },
            onCancel = onDismiss,
            saveEnabled = title.isNotBlank()
        )
    }
}

/** Generic preset editor: styles, modules, custom shapes, custom instruments. */
@Composable
fun PracticeItemSheet(
    edit: PracticeItem?,
    defaultSection: String,
    onDismiss: () -> Unit
) {
    val store = Graph.store
    var section by rememberState(edit?.section ?: defaultSection)
    var kind by rememberState(edit?.kind ?: PracticeKind.STYLE)
    var title by rememberState(edit?.title ?: "")
    var detail by rememberState(edit?.detail ?: "")
    var dayTag by rememberState(edit?.dayTag ?: "ALL")

    TassicSheet(title = if (edit == null) "New Preset" else "Edit Preset", onDismiss = onDismiss) {
        LabeledField(title, { title = it }, "Title", placeholder = "e.g. Hybrid Picking Drills")
        FieldLabel("Detail")
        LabeledField(detail, { detail = it }, "Detail", singleLine = false, minLines = 2)
        FieldLabel("Instrument / section")
        LabeledField(section, { section = it }, "Section", placeholder = "guitar, piano, bass…")
        FieldLabel("Type")
        SelectChips(listOf(PracticeKind.SHAPE, PracticeKind.STYLE, PracticeKind.MODULE), kind) { kind = it }
        FieldLabel("Scheduled day")
        SelectChips(listOf("ALL", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN", "WEEKEND"), dayTag) { dayTag = it }
        SheetActions(
            onSave = {
                val base = edit ?: PracticeItem(createdAt = T.now(), sortOrder = 500)
                val item = base.copy(
                    section = section.trim().lowercase().ifEmpty { "guitar" },
                    kind = kind,
                    title = title.trim(),
                    detail = detail.trim(),
                    dayTag = dayTag
                )
                if (edit == null) store.addPractice(item) else store.updatePractice(item)
                onDismiss()
            },
            onCancel = onDismiss,
            saveEnabled = title.isNotBlank()
        )
    }
}

/** CAGED shape editor incl. sub-task checklist management (add / rename / reorder / delete). */
@Composable
fun ShapeSheet(edit: PracticeItem, onDismiss: () -> Unit) {
    val store = Graph.store
    val allPractice by store.practice.items.collectAsState()
    val children = allPractice.filter { it.parentId == edit.id }.sortedBy { it.sortOrder }
    var title by rememberState(edit.title)
    var dayTag by rememberState(edit.dayTag)
    var detail by rememberState(edit.detail)
    var newSub by rememberState("")

    TassicSheet(title = "Edit Shape Preset", onDismiss = onDismiss) {
        LabeledField(title, { title = it }, "Shape / focus title")
        LabeledField(detail, { detail = it }, "Detail")
        FieldLabel("Scheduled day")
        SelectChips(listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN", "WEEKEND", "ALL"), dayTag) { dayTag = it }

        FieldLabel("Sub-tasks (${children.size})")
        children.forEach { sub ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(sub.title, style = MaterialTheme.typography.bodyLarge, color = Navy, modifier = Modifier.weight(1f))
                IconActionBtn(Icons.Filled.KeyboardArrowUp, "Move up") { store.movePractice(sub, up = true) }
                IconActionBtn(Icons.Filled.KeyboardArrowDown, "Move down") { store.movePractice(sub, up = false) }
                IconActionBtn(Icons.Filled.Close, "Delete", tint = Coral) { store.deletePractice(sub) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            LabeledField(newSub, { newSub = it }, "Add sub-task", modifier = Modifier.weight(1f))
            IconActionBtn(
                Icons.Filled.Add, "Add sub-task", tint = Navy,
            ) {
                if (newSub.isNotBlank()) {
                    store.addPractice(
                        PracticeItem(
                            section = edit.section, kind = PracticeKind.SUBTASK, title = newSub.trim(),
                            dayTag = dayTag, parentId = edit.id, sortOrder = children.size, createdAt = T.now()
                        )
                    )
                    newSub = ""
                }
            }
        }
        SheetActions(
            onSave = {
                store.updatePractice(edit.copy(title = title.trim(), detail = detail.trim(), dayTag = dayTag))
                onDismiss()
            },
            onCancel = onDismiss,
            saveEnabled = title.isNotBlank()
        )
    }
}
