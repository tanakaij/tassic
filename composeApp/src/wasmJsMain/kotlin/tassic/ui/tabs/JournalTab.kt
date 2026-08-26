package tassic.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import tassic.data.Graph
import tassic.data.HabitLog
import tassic.data.JournalEntry
import tassic.data.RecoveryHabit
import tassic.data.T
import tassic.platform.AudioRecorder
import tassic.platform.AudioStore
import tassic.platform.awaitOrNull
import tassic.ui.components.EmptyState
import tassic.ui.components.GhostButton
import tassic.ui.components.IconActionBtn
import tassic.ui.components.ItemMenu
import tassic.ui.components.JournalComposerSheet
import tassic.ui.components.Pill
import tassic.ui.components.SelectChips
import tassic.ui.components.StatTile
import tassic.ui.components.rememberSheetScope
import tassic.ui.components.rememberState
import tassic.ui.components.SectionHeader
import tassic.ui.components.TabScaffold
import tassic.ui.components.TassicCard
import tassic.ui.theme.Coral
import tassic.ui.theme.Green
import tassic.ui.theme.Ink
import tassic.ui.theme.MoodColors
import tassic.ui.theme.Muted
import tassic.ui.theme.Navy

@Composable
fun JournalTab() {
    val store = Graph.store
    val entries by store.journal.items.collectAsState()
    val habits by store.recovery.items.collectAsState()
    val habitLogs by store.habitLogs.items.collectAsState()
    val feedback = rememberSheetScope()

    var filter by rememberState("All")
    var composerOpen by rememberState(false)
    var composerEdit by rememberState<JournalEntry?>(null)

    // Segmented view switcher, same pattern as the other tabs — but only
    // when there's actually a second view to switch to. With no habits
    // tracked there's no Recovery History section at all (see below), so
    // showing a switcher with a single "Journal" pill would just be noise.
    var view by rememberState("Journal")
    val hasRecovery = habits.isNotEmpty()

    TabScaffold(
        fabIcon = Icons.Filled.Add,
        fabLabel = "New Entry",
        onFab = { composerEdit = null; composerOpen = true }
    ) {
        if (hasRecovery) {
            SelectChips(listOf("Journal", "Recovery"), view) { view = it }
        }
        val showJournal = !hasRecovery || view == "Journal"
        val showRecovery = hasRecovery && view == "Recovery"

        // ---- Recovery history ------------------------------------------------
        if (showRecovery) {
            SectionHeader("Recovery History", "Streaks & trigger reflections")
            habits.forEach { h ->
                RecoveryHistoryCard(
                    h,
                    habitLogs.filter { it.habitId == h.id && it.event == "RELAPSE" }.sortedByDescending { it.loggedAt }
                )
            }
        }

        // ---- Entries -----------------------------------------------------------
        if (showJournal) {
        SectionHeader("Journal", "${entries.size} multimodal entries")
        SelectChips(listOf("All", "Voice", "Text"), filter) { filter = it }
        val filtered = entries.filter {
            when (filter) {
                "Voice" -> it.audioId != null
                "Text" -> it.audioId == null
                else -> true
            }
        }
        if (filtered.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.AutoStories,
                title = "Nothing here yet",
                hint = "Capture a thought, a mood, or a voice note."
            )
        }
        filtered.forEach { e ->
            EntryCard(
                entry = e,
                onEdit = { composerEdit = e; composerOpen = true },
                onDelete = { store.deleteJournal(e) }
            )
        }
        }

        // ---- Sheet ---------------------------------------------------------------
        if (composerOpen) {
            JournalComposerSheet(composerEdit, prefillTag = "") { composerOpen = false }
        }
    }
}

@Composable
private fun RecoveryHistoryCard(habit: RecoveryHabit, logs: List<HabitLog>) {
    TassicCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.titleMedium, color = Navy)
                Text("Started ${T.dateLabel(habit.startEpochDay)}", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            StatTile("${Graph.store.daysClean(habit)}", "clean", Green)
            Spacer(Modifier.width(8.dp))
            StatTile("${habit.bestStreak}", "best", tassic.ui.theme.Blue)
            Spacer(Modifier.width(8.dp))
            StatTile("${habit.relapses}", "resets", Coral)
        }
        Text(
            "Trigger Log",
            style = MaterialTheme.typography.titleSmall,
            color = Ink,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )
        if (logs.isEmpty()) {
            Text("No relapses logged - keep going.", style = MaterialTheme.typography.bodySmall, color = Green)
        } else {
            logs.take(6).forEach { log ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .width(8.dp)
                            .height(8.dp)
                            .background(Coral, androidx.compose.foundation.shape.CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(T.fullLabel(log.loggedAt), style = MaterialTheme.typography.labelSmall, color = Coral)
                        Text(log.triggerNote, style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryCard(
    entry: JournalEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val cs = rememberCoroutineScope()
    val feedback = rememberSheetScope()
    TassicCard {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(MoodColors[(entry.mood - 1).coerceIn(0, 4)])
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (entry.title.isNotBlank()) {
                            Text(entry.title, style = MaterialTheme.typography.titleLarge, color = Ink)
                        }
                        Text(T.fullLabel(entry.createdAt), style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                    var menu by rememberState(false)
                    IconActionBtn(Icons.Filled.MoreVert, "Options") { menu = true }
                    ItemMenu(
                        expanded = menu,
                        onDismiss = { menu = false },
                        onEdit = onEdit,
                        onDelete = onDelete
                    )
                }
                if (entry.body.isNotBlank()) {
                    RichBody(entry.body, modifier = Modifier.padding(top = 6.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    entry.audioId?.let { id ->
                        GhostButton("Play voice note", {
                            cs.launch {
                                val url = AudioStore.get(id).awaitOrNull()?.toString()
                                if (url != null) AudioRecorder.play(url)
                                else feedback.launchSnackbar("Clip missing from storage")
                            }
                        })
                    }
                    entry.tags.forEach { tag -> Pill(tag, bg = tassic.ui.theme.SkySoft) }
                }
            }
        }
    }
}

/** Minimal rich text: `# heading`, `- bullet`, blank-line spacing. */
@Composable
private fun RichBody(body: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        body.lines().forEach { line ->
            when {
                line.startsWith("# ") -> Text(
                    line.removePrefix("# "),
                    style = MaterialTheme.typography.titleMedium,
                    color = Navy,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
                line.startsWith("- ") -> Row {
                    // Drawn as a shape rather than a "•" glyph: bundled fonts on the
                    // web/wasm target don't reliably cover the bullet code point, which
                    // rendered as a tofu box.
                    Box(
                        Modifier
                            .padding(top = 8.dp, end = 8.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Muted)
                    )
                    Text(
                        line.removePrefix("- "),
                        style = MaterialTheme.typography.bodyMedium, color = Ink
                    )
                }
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                else -> Text(line, style = MaterialTheme.typography.bodyMedium, color = Ink)
            }
        }
    }
}
