package tassic.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tassic.data.AlbumGoal
import tassic.data.Coach
import tassic.data.Graph
import tassic.data.PracticeItem
import tassic.data.PracticeKind
import tassic.data.T
import tassic.ui.components.CheckRow
import tassic.ui.components.EmptyState
import tassic.ui.components.GhostButton
import tassic.ui.components.IconActionBtn
import tassic.ui.components.ItemMenu
import tassic.ui.components.Pill
import tassic.ui.components.PrimaryButton
import tassic.ui.components.FocusSheet
import tassic.ui.components.GlassCard
import tassic.ui.components.InkCard
import tassic.ui.components.MetricTile
import tassic.ui.components.SelectChips
import tassic.ui.components.rememberState
import tassic.ui.components.SectionHeader
import tassic.ui.components.ShapeSheet
import tassic.ui.components.TabScaffold
import tassic.ui.components.TassicCard
import tassic.ui.components.TassicProgress
import tassic.ui.components.AlbumSheet
import tassic.ui.components.PracticeItemSheet
import tassic.ui.components.SongLogSheet
import tassic.ui.theme.surfaceSoft
import tassic.ui.theme.textMuted
import tassic.ui.theme.textInk
import tassic.ui.theme.Amber
import tassic.ui.theme.AmberDeep
import tassic.ui.theme.Blue
import tassic.ui.theme.Green
import tassic.ui.theme.Muted
import tassic.ui.theme.Navy
import tassic.ui.theme.SkySoft

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusicTab() {
    val store = Graph.store
    val practice by store.practice.items.collectAsState()
    val albums by store.albums.items.collectAsState()
    val today = T.today()

    val sections = rememberSections(practice)
    var section by rememberState(if ("guitar" in sections) "guitar" else sections.firstOrNull() ?: "guitar")
    val active = if (section in sections) section else sections.firstOrNull() ?: "guitar"

    var presetOpen by rememberState(false)
    var presetEdit by rememberState<PracticeItem?>(null)
    var shapeEdit by rememberState<PracticeItem?>(null)
    var albumOpen by rememberState(false)
    var practiceOpen by rememberState(false)
    var albumEdit by rememberState<AlbumGoal?>(null)
    var songOpen by rememberState(false)

    val inSection = practice.filter { it.section == active }
    val shapes = inSection.filter { it.kind == PracticeKind.SHAPE }.sortedBy { it.sortOrder }
    val styles = inSection.filter { it.kind == PracticeKind.STYLE }.sortedBy { it.sortOrder }
    val modules = inSection.filter { it.kind == PracticeKind.MODULE }.sortedBy { it.sortOrder }
    val modes = inSection.filter { it.kind == PracticeKind.MODE }.sortedBy { it.sortOrder }
    val keys = inSection.filter { it.kind == PracticeKind.KEY }.sortedBy { it.sortOrder }
    val songs = inSection.filter { it.kind == PracticeKind.SONG }.sortedByDescending { it.doneEpochDay ?: 0 }
    val week = T.weekIndex(today)
    val songsThisWeek = songs.count { s ->
        val d = s.doneEpochDay
        d != null && T.weekIndex(d) == week
    }
    val songTarget = 1

    TabScaffold(
        fabIcon = Icons.Filled.Add,
        fabLabel = "New Preset",
        onFab = { presetEdit = null; presetOpen = true }
    ) {
        // Instrument / section switcher
        SelectChips(sections, active, label = { it.replaceFirstChar { c -> c.uppercase() } }) { section = it }

        // ---- Studio state ------------------------------------------------------
        // The studio held six lists and no sense of whether any practice was
        // actually happening. Everything below is derived from the same
        // activity log the rest of the app writes to, so it costs no new state.
        val activity by store.activity.items.collectAsState()
        val streak = remember(activity, today) { Coach.practiceStreak(store, today) }
        val minutes = remember(activity, today) { Coach.practiceMinutesThisWeek(store, today) }
        val musicNotes = remember(activity, practice, active, today) {
            Coach.musicInsights(store, active, today)
        }
        val shapeToday = shapes.firstOrNull { T.tagMatches(it.dayTag, today) }

        InkCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("STUDIO", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            shapeToday != null && shapeToday.doneEpochDay != today ->
                                "Today's shape: ${shapeToday.title}."
                            streak >= 3 -> "$streak days of practice in a row."
                            minutes > 0 -> "$minutes minutes logged this week."
                            else -> "Nothing logged yet this week."
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("$streak", "day streak", tint = Amber, modifier = Modifier.weight(1f))
                MetricTile(
                    if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m",
                    "this week",
                    tint = tassic.ui.theme.Blue,
                    modifier = Modifier.weight(1f)
                )
                MetricTile("$songsThisWeek/$songTarget", "songs", tint = Green, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            // A timed block that writes real minutes to the log, rather than
            // asking the user to estimate afterwards.
            GhostButton("Start a practice session", { practiceOpen = true })
        }

        // Which material has gone cold. The comfortable shapes rehearse
        // themselves; this is the only thing here that surfaces the rest.
        musicNotes.take(2).forEach { insight ->
            GlassCard {
                Text(insight.title, style = MaterialTheme.typography.titleSmall, color = textInk)
                Spacer(Modifier.height(3.dp))
                Text(insight.detail, style = MaterialTheme.typography.bodySmall, color = textMuted)
            }
        }

        // ---- Shape system (CAGED & custom) --------------------------------
        TassicCard {
            SectionHeader("Shape System", "Daily schedule - tap a row to expand")
            if (shapes.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.MusicNote,
                    title = "No shapes yet",
                    hint = "Add a chord-shape system via the FAB."
                )
            }
            shapes.forEach { s ->
                var menu by rememberState(false)
                val children = practice
                    .filter { it.kind == PracticeKind.SUBTASK && it.parentId == s.id }
                    .sortedBy { it.sortOrder }
                var expanded by rememberState(false)
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { expanded = !expanded }
                            .padding(vertical = 6.dp)
                    ) {
                        Pill(
                            s.dayTag,
                            bg = if (T.tagMatches(s.dayTag, today)) Amber else SkySoft,
                            fg = if (T.tagMatches(s.dayTag, today)) Navy else Muted
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = MaterialTheme.typography.titleSmall, color = textInk)
                            if (s.detail.isNotBlank()) {
                                Text(s.detail, style = MaterialTheme.typography.bodySmall, color = textMuted, maxLines = 1)
                            }
                        }
                        IconActionBtn(Icons.Filled.Edit, "Edit shape") { shapeEdit = s }
                        IconActionBtn(Icons.Filled.MoreVert, "Options") { menu = true }
                        ItemMenu(
                            expanded = menu,
                            onDismiss = { menu = false },
                            onEdit = { shapeEdit = s },
                            onDelete = { store.deletePractice(s) }
                        )
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null, tint = textMuted
                        )
                    }
                    if (expanded) {
                        children.forEach { sub ->
                            CheckRow(
                                title = sub.title,
                                subtitle = sub.detail.ifBlank { null },
                                checked = sub.doneEpochDay == today,
                                onChecked = { store.togglePracticeDone(sub) },
                                trailing = {
                                    IconActionBtn(Icons.Filled.Delete, "Delete sub-task", tint = textMuted) {
                                        store.deletePractice(sub)
                                    }
                                }
                            )
                        }
                        GhostButton("+ Add sub-task", {
                            store.addPractice(
                                PracticeItem(
                                    section = s.section, kind = PracticeKind.SUBTASK,
                                    title = "New sub-task", dayTag = s.dayTag,
                                    parentId = s.id, sortOrder = children.size, createdAt = T.now()
                                )
                            )
                        })
                    }
                }
            }
        }

        // ---- Style trackers --------------------------------------------------
        TassicCard {
            SectionHeader("Style Trackers", "Fills, runs & feel drills")
            if (styles.isEmpty()) {
                Text("No style trackers - add one via the FAB.", style = MaterialTheme.typography.bodySmall, color = textMuted)
            }
            styles.forEach { st ->
                var menu by rememberState(false)
                CheckRow(
                    title = st.title,
                    subtitle = st.detail.ifBlank { null },
                    checked = st.doneEpochDay == today,
                    onChecked = { store.togglePracticeDone(st) },
                    trailing = {
                        IconActionBtn(Icons.Filled.MoreVert, "Options") { menu = true }
                        ItemMenu(
                            expanded = menu,
                            onDismiss = { menu = false },
                            onEdit = { presetEdit = st; presetOpen = true },
                            onDelete = { store.deletePractice(st) },
                            onUp = { store.movePractice(st, up = true) },
                            onDown = { store.movePractice(st, up = false) }
                        )
                    }
                )
            }
        }

        // ---- Song tracker --------------------------------------------------------
        TassicCard {
            SectionHeader("Song Tracker", "Target: $songTarget learned song per week")
            TassicProgress(songsThisWeek / songTarget.toFloat(), color = Green)
            Text(
                "$songsThisWeek of $songTarget learned this week",
                style = MaterialTheme.typography.bodySmall, color = textMuted,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
            )
            songs.take(5).forEach { song ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    val d = song.doneEpochDay
                    Text(
                        song.title,
                        style = MaterialTheme.typography.bodyLarge, color = textInk,
                        modifier = Modifier.weight(1f)
                    )
                    if (d != null) Pill(T.shortDate(d), bg = surfaceSoft)
                    IconActionBtn(Icons.Filled.Delete, "Delete song", tint = textMuted) { store.deletePractice(song) }
                }
            }
            PrimaryButton("Log a learned song", { songOpen = true })
        }

        // ---- Piano: modes + 12 keys ---------------------------------------------
        if (active == "piano" && modes.isNotEmpty()) {
            TassicCard {
                SectionHeader("Daily Mode Rotation", "Ionian to Locrian - today's mode is highlighted")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    val currentIdx = (T.dayOfYear(today) - 1).mod(modes.size)
                    modes.forEachIndexed { i, m ->
                        val isToday = i == currentIdx
                        val done = m.doneEpochDay == today
                        ModeKeyChip(
                            label = m.title,
                            highlighted = isToday,
                            done = done,
                            onClick = { store.togglePracticeDone(m) }
                        )
                    }
                }
                Text(
                    "Tap a mode to mark it practiced today.",
                    style = MaterialTheme.typography.bodySmall, color = textMuted,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        if (active == "piano" && keys.isNotEmpty()) {
            TassicCard {
                SectionHeader("12-Key Cycle", "Weekly distribution - focus key is ringed")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val focus = keys[T.dayOfYear(today).mod(keys.size)]
                    keys.forEach { k ->
                        val isFocus = k.id == focus.id
                        val done = k.doneEpochDay == today
                        ModeKeyChip(
                            label = k.title,
                            highlighted = done,
                            ringed = isFocus,
                            onClick = { store.togglePracticeDone(k) }
                        )
                    }
                }
            }
        }

        // ---- Keyboard modules -------------------------------------------------------
        TassicCard {
            SectionHeader("Advanced Modules", "Preacher chords · tritones · voicings")
            if (modules.isEmpty()) {
                Text("No modules - add one via the FAB.", style = MaterialTheme.typography.bodySmall, color = textMuted)
            }
            modules.forEach { m ->
                var menu by rememberState(false)
                CheckRow(
                    title = m.title,
                    subtitle = m.detail.ifBlank { null },
                    checked = m.doneEpochDay == today,
                    onChecked = { store.togglePracticeDone(m) },
                    trailing = {
                        IconActionBtn(Icons.Filled.MoreVert, "Options") { menu = true }
                        ItemMenu(
                            expanded = menu,
                            onDismiss = { menu = false },
                            onEdit = { presetEdit = m; presetOpen = true },
                            onDelete = { store.deletePractice(m) },
                            onUp = { store.movePractice(m, up = true) },
                            onDown = { store.movePractice(m, up = false) }
                        )
                    }
                )
            }
        }

        // ---- Album goals ---------------------------------------------------------------
        TassicCard {
            SectionHeader(
                title = "Album Goals",
                subtitle = "Monthly gospel album tracking",
                trailing = {
                    IconActionBtn(Icons.Filled.Add, "New album", tint = textInk) { albumEdit = null; albumOpen = true }
                }
            )
            if (albums.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.QueueMusic,
                    title = "No album goals",
                    hint = "Pick an album and learn it track by track."
                )
            }
            albums.forEach { a ->
                var menu by rememberState(false)
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.album, style = MaterialTheme.typography.titleSmall, color = textInk)
                            if (a.artist.isNotBlank()) {
                                Text(a.artist, style = MaterialTheme.typography.bodySmall, color = textMuted)
                            }
                        }
                        IconActionBtn(Icons.Filled.Remove, "One less") { store.bumpAlbum(a, -1) }
                        Text("${a.learnedTracks}/${a.totalTracks}", style = MaterialTheme.typography.labelLarge, color = textInk)
                        IconActionBtn(Icons.Filled.Add, "One more") { store.bumpAlbum(a, +1) }
                        IconActionBtn(Icons.Filled.MoreVert, "Options") { menu = true }
                        ItemMenu(
                            expanded = menu,
                            onDismiss = { menu = false },
                            onEdit = { albumEdit = a; albumOpen = true },
                            onDelete = { store.deleteAlbum(a.id) }
                        )
                    }
                    TassicProgress(
                        a.learnedTracks / a.totalTracks.toFloat(),
                        color = if (a.learnedTracks >= a.totalTracks) Green else Blue,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        // ---- Sheets -----------------------------------------------------------------------
        if (presetOpen) PracticeItemSheet(presetEdit, active) { presetOpen = false }
        shapeEdit?.let { s -> ShapeSheet(s) { shapeEdit = null } }
        if (albumOpen) AlbumSheet(albumEdit) { albumOpen = false }
        if (practiceOpen) {
            FocusSheet(
                onDismiss = { practiceOpen = false },
                initialLabel = "Practice \u00b7 ${active.replaceFirstChar { it.uppercase() }}"
            )
        }
        if (songOpen) SongLogSheet { songOpen = false }
    }
}

@Composable
private fun ModeKeyChip(
    label: String,
    highlighted: Boolean,
    done: Boolean = false,
    ringed: Boolean = false,
    onClick: () -> Unit
) {
    val bg = when {
        done -> Green
        highlighted -> Amber
        else -> SkySoft
    }
    val fg = if (done) Color.White else Navy
    var chip = Modifier
        .clip(RoundedCornerShape(12.dp))
        .background(bg)
    if (ringed && !done) {
        chip = chip.border(2.dp, AmberDeep, RoundedCornerShape(12.dp))
    }
    chip = chip
        .clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 8.dp)
    Box(chip) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            fontWeight = if (highlighted || done) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun rememberSections(practice: List<PracticeItem>): List<String> {
    val found = practice.map { it.section }.distinct()
    val ordered = listOf("guitar", "piano").filter { it in found }
    return ordered + (found - "guitar" - "piano").sorted()
}
