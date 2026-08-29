package tassic.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tassic.data.Agenda
import tassic.data.Graph
import tassic.data.Habit
import tassic.data.HabitPulse
import tassic.data.T
import tassic.ui.theme.AmberDeep
import tassic.ui.theme.Blue
import tassic.ui.theme.Coral
import tassic.ui.theme.Green
import tassic.ui.theme.LocalTokens
import tassic.ui.theme.Violet

/**
 * Habit surfaces.
 *
 * The design problem with a habit list is that it has to carry three facts at
 * once — is it done today, how long is the run, and is it actually holding — in
 * a row narrow enough that ten of them fit on a phone. Hence: a tap target on
 * the left that is the whole answer to "done?", the name and cadence in the
 * middle, and a four-week trace on the right that shows the truth a streak
 * number can hide (a 3-day streak on top of a month of misses is not progress).
 */

fun habitIcon(key: String): ImageVector = when (key.lowercase()) {
    "water" -> Icons.Filled.WaterDrop
    "book" -> Icons.Filled.AutoStories
    "run" -> Icons.Filled.DirectionsRun
    "sun" -> Icons.Filled.WbSunny
    "moon" -> Icons.Filled.NightsStay
    "heart" -> Icons.Filled.Favorite
    "pen" -> Icons.Filled.Create
    "music" -> Icons.Filled.MusicNote
    "pray" -> Icons.Filled.Church
    "fire" -> Icons.Filled.LocalFireDepartment
    else -> Icons.Filled.AutoAwesome
}

fun habitColor(key: String): Color = when (key.lowercase()) {
    "green" -> Green
    "amber" -> AmberDeep
    "coral" -> Coral
    "violet" -> Violet
    else -> Blue
}

val HABIT_ICONS = listOf("spark", "water", "book", "run", "sun", "moon", "heart", "pen", "music", "pray", "fire")
val HABIT_COLORS = listOf("blue", "green", "amber", "coral", "violet")

/**
 * One habit, with its tick target, streak and four-week trace.
 *
 * Counted habits (8 glasses) tick up on tap and show progress inside the
 * target; plain habits toggle. Long-press equivalents don't exist on the web,
 * so an explicit minus button appears once there's something to undo.
 */
@Composable
fun HabitRow(
    pulse: HabitPulse,
    onTick: () -> Unit,
    onUntick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalTokens.current
    val habit = pulse.habit
    val tint = habitColor(habit.color)
    val target = habit.targetPerDay.coerceAtLeast(1)
    val fraction = (pulse.countToday.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    val scale by animateFloatAsState(
        targetValue = if (pulse.doneToday) 1f else 0.97f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "habitScale"
    )

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(t.radiusControl.dp))
            .background(if (pulse.doneToday) tint.copy(alpha = if (t.dark) 0.16f else 0.10f) else t.cardSunken)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ---- tick target ---------------------------------------------------
        Box(
            Modifier
                .size(42.dp)
                .scale(scale)
                .clip(RoundedCornerShape(14.dp))
                .background(if (pulse.doneToday) tint else Color.Transparent)
                .border(
                    2.dp,
                    if (pulse.doneToday) tint else tint.copy(alpha = 0.45f),
                    RoundedCornerShape(14.dp)
                )
                .pressable { if (pulse.doneToday && target == 1) onUntick() else onTick() },
            contentAlignment = Alignment.Center
        ) {
            when {
                pulse.doneToday -> Icon(
                    Icons.Filled.Check,
                    contentDescription = "Kept today",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                target > 1 -> Text(
                    "${pulse.countToday}",
                    style = MaterialTheme.typography.titleMedium,
                    color = tint
                )
                else -> Icon(
                    habitIcon(habit.icon),
                    contentDescription = habit.name,
                    tint = tint,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                habit.name,
                style = MaterialTheme.typography.titleSmall,
                color = t.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pulse.streak > 0) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = AmberDeep,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "${pulse.streak}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberDeep
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    buildString {
                        append(Agenda.habitCadenceLabel(habit))
                        if (target > 1) append(" · ${pulse.countToday}/$target ${habit.unit}".trimEnd())
                        if (pulse.dueLast28 >= 5) append(" · ${pulse.consistency}%")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = t.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (target > 1) {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(t.hairline)
                ) {
                    // fillMaxWidth(0f) is not a meaningful request, so an
                    // untouched counter draws no bar at all rather than a
                    // zero-width one.
                    if (fraction > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(4.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(tint)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        if (target > 1 && pulse.countToday > 0) {
            IconActionBtn(Icons.Filled.Remove, "Remove one", tint = t.textTertiary, onClick = onUntick)
        }
        IconActionBtn(Icons.Filled.Edit, "Edit habit", tint = t.textTertiary, onClick = onEdit)
    }
}

/**
 * Four weeks of a habit as a strip of cells.
 *
 * Not-due days are drawn as faint outlines rather than being skipped, so the
 * weekly rhythm of a weekday-only habit stays visible instead of compressing
 * into a misleadingly solid block.
 */
@Composable
fun HabitTrace(history: List<Boolean?>, tint: Color, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        history.forEach { state ->
            Box(
                Modifier
                    .size(width = 7.dp, height = 16.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when (state) {
                            true -> tint
                            false -> t.hairline
                            null -> Color.Transparent
                        }
                    )
                    .then(
                        if (state == null) {
                            Modifier.border(1.dp, t.hairline.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}

// ------------------------------------------------------------------- editor

private val CADENCES = listOf("DAILY", "WEEKDAYS", "WEEKEND", "CUSTOM", "WEEKLY_COUNT")
private val TIMES_OF_DAY = listOf("ANY", "MORNING", "AFTERNOON", "EVENING")

@Composable
fun HabitEditorSheet(edit: Habit?, onDismiss: () -> Unit) {
    val store = Graph.store
    val t = LocalTokens.current

    var name by rememberState(edit?.name ?: "")
    var icon by rememberState(edit?.icon ?: "spark")
    var color by rememberState(edit?.color ?: "blue")
    var cadence by rememberState(edit?.cadence ?: "DAILY")
    var days by rememberState(edit?.daysOfWeek ?: listOf(0, 1, 2, 3, 4))
    var timesPerWeek by rememberState(edit?.timesPerWeek ?: 3)
    var target by rememberState(edit?.targetPerDay ?: 1)
    var unit by rememberState(edit?.unit ?: "")
    var timeOfDay by rememberState(edit?.timeOfDay ?: "ANY")
    var reminderOn by rememberState(edit?.reminderOn ?: false)
    var reminderHour by rememberState(edit?.reminderHour ?: 8)

    TassicSheet(title = if (edit == null) "New habit" else "Edit habit", onDismiss = onDismiss) {
        LabeledField(name, { name = it }, "Habit", placeholder = "Read 20 minutes")

        FieldLabel("Look")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            HABIT_COLORS.forEach { key ->
                val c = habitColor(key)
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(c.copy(alpha = if (color == key) 1f else 0.28f))
                        .pressable { color = key },
                    contentAlignment = Alignment.Center
                ) {
                    if (color == key) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SelectChips(HABIT_ICONS, icon, label = { it.replaceFirstChar { c -> c.uppercase() } }) { icon = it }

        FieldLabel("Cadence")
        SelectChips(CADENCES, cadence, label = { cadenceLabel(it) }) { cadence = it }

        if (cadence == "CUSTOM") {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                T.DAY_TAGS.forEachIndexed { index, tag ->
                    val selected = days.contains(index)
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) t.chrome else t.cardSunken)
                            .pressable {
                                days = if (selected) days - index else (days + index).sorted()
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            tag.take(1),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) t.chromeText else t.textSecondary
                        )
                    }
                }
            }
        }

        if (cadence == "WEEKLY_COUNT") {
            Stepper("Days per week", timesPerWeek, { timesPerWeek = it }, range = 1..7)
        }

        FieldLabel("Daily target")
        Stepper("Repetitions that count as done", target, { target = it }, range = 1..30)
        if (target > 1) {
            LabeledField(unit, { unit = it }, "Unit", placeholder = "glasses, pages, minutes")
        }

        FieldLabel("Where it sits in the day")
        SelectChips(TIMES_OF_DAY, timeOfDay, label = { it.lowercase().replaceFirstChar { c -> c.uppercase() } }) {
            timeOfDay = it
        }

        FieldLabel("Reminder")
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton(if (reminderOn) "Reminder on" else "Reminder off", { reminderOn = !reminderOn })
            if (reminderOn) {
                Spacer(Modifier.width(8.dp))
                MiniStepper("Hour", reminderHour, { reminderHour = it }, range = 0..23)
            }
        }

        SheetActions(
            saveEnabled = name.isNotBlank(),
            onSave = {
                val base = edit ?: Habit(createdAt = T.now(), sortOrder = store.activeHabits().size)
                val item = base.copy(
                    name = name.trim(),
                    icon = icon,
                    color = color,
                    cadence = cadence,
                    daysOfWeek = if (cadence == "CUSTOM") days.sorted() else emptyList(),
                    timesPerWeek = timesPerWeek,
                    targetPerDay = target,
                    unit = if (target > 1) unit.trim() else "",
                    timeOfDay = timeOfDay,
                    reminderOn = reminderOn,
                    reminderHour = reminderHour
                )
                if (edit == null) store.addHabit(item) else store.updateHabit(item)
                onDismiss()
            },
            onCancel = onDismiss
        )
    }
}

private fun cadenceLabel(key: String): String = when (key) {
    "DAILY" -> "Every day"
    "WEEKDAYS" -> "Weekdays"
    "WEEKEND" -> "Weekends"
    "CUSTOM" -> "Pick days"
    "WEEKLY_COUNT" -> "N× a week"
    else -> key
}
