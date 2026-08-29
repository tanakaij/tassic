package tassic.ui.components

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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import tassic.data.Coach
import tassic.data.Graph
import tassic.data.JournalEntry
import tassic.data.T
import tassic.ui.theme.Blue
import tassic.ui.theme.Coral
import tassic.ui.theme.Green
import tassic.ui.theme.LocalTokens
import tassic.ui.theme.MoodColors

/**
 * Closing the day.
 *
 * Everything the app collects during the day is input; this is the only place
 * it gets turned back into something the user reads. The lines are generated
 * from the log rather than typed, so the review takes seconds — the moment it
 * takes minutes, it stops happening, and an unclosed day is how a tracker
 * quietly becomes a graveyard.
 *
 * Mood and energy are asked here rather than on a separate screen because this
 * is the one moment in the day when someone has just been reminded what
 * actually happened in it.
 */
@Composable
fun ReviewSheet(onDismiss: () -> Unit) {
    val store = Graph.store
    val t = LocalTokens.current
    val feedback = rememberFeedback()
    val today = T.today()

    val draft = remember(today) { Coach.review(store, today) }
    var mood by rememberState(3)
    var energy by rememberState(3)
    var note by rememberState("")
    var keepInJournal by rememberState(true)

    TassicSheet(title = "Close the day", onDismiss = onDismiss) {
        Text(
            draft.headline,
            style = MaterialTheme.typography.titleLarge,
            color = t.textPrimary
        )
        Text(
            T.dayNameFullOf(today) + " · " + T.dateLabel(today),
            style = MaterialTheme.typography.bodySmall,
            color = t.textSecondary
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("${draft.doneCount}", "logged", tint = Green, modifier = Modifier.weight(1f))
            MetricTile("${draft.openCount}", "left open", tint = Blue, modifier = Modifier.weight(1f))
            MetricTile(
                if (draft.focusMinutes > 0) "${draft.focusMinutes}m" else "—",
                "focused",
                tint = t.accentDeep,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(14.dp))
        draft.lines.forEach { line ->
            ReviewLineRow(
                icon = when (line.kind) {
                    "win" -> Icons.Filled.Check
                    "slip" -> Icons.Filled.ArrowDownward
                    "ahead" -> Icons.Filled.ArrowUpward
                    else -> Icons.Filled.WbSunny
                },
                tint = when (line.kind) {
                    "win" -> Green
                    "slip" -> Coral
                    "ahead" -> Blue
                    else -> t.textSecondary
                },
                text = line.text
            )
        }

        FieldLabel("How did it feel?")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mood", style = MaterialTheme.typography.bodyMedium, color = t.textSecondary, modifier = Modifier.width(58.dp))
            MoodPicker(mood) { mood = it }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Energy", style = MaterialTheme.typography.bodyMedium, color = t.textSecondary, modifier = Modifier.width(58.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                (1..5).forEach { level ->
                    val selected = level == energy
                    Box(
                        Modifier
                            .size(if (selected) 40.dp else 34.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MoodColors[level - 1].copy(alpha = if (selected) 1f else 0.20f))
                            .pressable { energy = level },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$level",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) androidx.compose.ui.graphics.Color.White else MoodColors[level - 1]
                        )
                    }
                }
            }
        }

        FieldLabel("Anything worth remembering?")
        LabeledField(note, { note = it }, "Note", placeholder = "Optional", singleLine = false, minLines = 2)

        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(t.radiusControl.dp))
                .background(t.cardSunken)
                .pressable { keepInJournal = !keepInJournal }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (keepInJournal) t.accent else t.hairline),
                contentAlignment = Alignment.Center
            ) {
                if (keepInJournal) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = t.onAccent, modifier = Modifier.size(13.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Keep this review in the journal",
                style = MaterialTheme.typography.bodyMedium,
                color = t.textPrimary
            )
        }

        SheetActions(
            saveLabel = "Done for today",
            onSave = {
                store.logCheckIn(mood, energy)
                if (keepInJournal) {
                    val body = buildString {
                        append(draft.journalBody)
                        if (note.isNotBlank()) {
                            append("\n")
                            append(note.trim())
                        }
                    }
                    store.addJournal(
                        JournalEntry(
                            title = "Review · ${T.shortDate(today)}",
                            body = body,
                            mood = mood,
                            tags = listOf("review"),
                            createdAt = T.now()
                        )
                    )
                }
                store.updateSettings { it.copy(lastReviewedEpochDay = today) }
                feedback.confirm("Day closed")
                onDismiss()
            },
            onCancel = onDismiss
        )
    }
}

@Composable
private fun ReviewLineRow(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, text: String) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(tint.copy(alpha = if (t.dark) 0.22f else 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = t.textPrimary)
    }
}
