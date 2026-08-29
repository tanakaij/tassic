package tassic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tassic.data.Graph
import tassic.data.Search
import tassic.data.SearchHit
import tassic.ui.theme.LocalTokens

/** What the palette asked the app shell to do once it closes. */
enum class PaletteAction { CAPTURE, FOCUS, REVIEW, CHECK_IN, WEEK, BACKUP }

/**
 * Search everything, and start anything.
 *
 * Two problems, one surface. The first is retrieval: across tasks, goals,
 * journal entries, prayer points, practice items, roadmap steps and a wishlist,
 * "I know I wrote that down" had no answer except remembering which tab it was
 * in. The second is that the app's most useful actions — capture, focus,
 * review — were each buried a tab and a tap deep.
 *
 * Empty query shows actions and destinations; typing switches to results
 * grouped by where they live, so the answer always carries its context.
 */
@Composable
fun CommandPalette(
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onAction: (PaletteAction) -> Unit
) {
    val store = Graph.store
    val t = LocalTokens.current
    val todos by store.todos.items.collectAsState()
    val journal by store.journal.items.collectAsState()
    var query by rememberState("")
    val focusRequester = remember { FocusRequester() }

    val results = remember(query, todos, journal) {
        if (query.trim().length < 2) null else Search.run(store, query)
    }

    LaunchedEffect(Unit) {
        delay(140)
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(if (t.dark) Color.Black.copy(alpha = 0.62f) else t.chrome.copy(alpha = 0.46f))
            // Tapping the scrim closes it, with no ripple: a full-screen
            // indication flash reads as a rendering fault rather than a button.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // ---- search field -----------------------------------------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(t.card)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = t.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search everything", color = t.textTertiary) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = t.accentDeep,
                        focusedTextColor = t.textPrimary,
                        unfocusedTextColor = t.textPrimary
                    )
                )
                IconActionBtn(Icons.Filled.Close, "Close search", tint = t.textSecondary, onClick = onDismiss)
            }

            Spacer(Modifier.height(10.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(t.card)
                    // Swallows taps that land on the panel's own padding, which
                    // would otherwise reach the scrim behind it and close the
                    // palette mid-scroll.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp)
            ) {
                if (results == null) {
                    PaletteGroupLabel("Do something")
                    PaletteRow(Icons.Filled.Bolt, "Capture a thought", "Task, note, goal or wish — one line") {
                        onAction(PaletteAction.CAPTURE)
                    }
                    PaletteRow(Icons.Filled.Timer, "Start a focus session", "Timed block, logged when it ends") {
                        onAction(PaletteAction.FOCUS)
                    }
                    PaletteRow(Icons.Filled.SelfImprovement, "Log a check-in", "Mood and energy, two taps") {
                        onAction(PaletteAction.CHECK_IN)
                    }
                    PaletteRow(Icons.Filled.Check, "Close the day", "Review what landed and what slipped") {
                        onAction(PaletteAction.REVIEW)
                    }
                    PaletteRow(Icons.Filled.Flag, "Plan the week", "Three things that would make it a good one") {
                        onAction(PaletteAction.WEEK)
                    }

                    PaletteGroupLabel("Go to")
                    PaletteRow(Icons.Filled.Today, "Today", "Focus, training, recovery, tasks") { onNavigate("TODAY") }
                    PaletteRow(Icons.Filled.EventNote, "Plan", "Day timeline, week and habits") { onNavigate("PLAN") }
                    PaletteRow(Icons.Filled.Insights, "Insights", "Momentum, patterns, week in review") { onNavigate("INSIGHTS") }
                    PaletteRow(Icons.Filled.AutoStories, "Journal", "Entries, voice notes, recovery history") { onNavigate("JOURNAL") }
                    PaletteRow(Icons.Filled.Flag, "Life & Goals", "Goals, wishlist, roadmap") { onNavigate("LIFE") }
                    PaletteRow(Icons.Filled.Group, "People", "Birthdays and keeping in touch") { onNavigate("PEOPLE") }
                    PaletteRow(Icons.Filled.SelfImprovement, "Growth", "Areas to work on and good deeds") { onNavigate("GROWTH") }
                    PaletteRow(Icons.Filled.MusicNote, "Music Studio", "CAGED, modes, modules, albums") { onNavigate("MUSIC") }
                    PaletteRow(Icons.Filled.Church, "Faith", "Rhythms and prayer points") { onNavigate("FAITH") }
                    PaletteRow(Icons.Filled.Settings, "Settings", "Reminders, appearance, backup") { onNavigate("SETTINGS") }
                } else if (results.isEmpty) {
                    Column(
                        Modifier.fillMaxWidth().padding(30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No matches", style = MaterialTheme.typography.titleMedium, color = t.textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Nothing in tasks, goals, journal, habits, prayer, practice or the wishlist contains every word.",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.textSecondary
                        )
                        Spacer(Modifier.height(12.dp))
                        GhostButton("Capture \"${query.trim()}\" instead", { onAction(PaletteAction.CAPTURE) })
                    }
                } else {
                    results.grouped().forEach { (kind, hits) ->
                        PaletteGroupLabel("${Search.kindLabel(kind)} · ${hits.size}")
                        hits.take(6).forEach { hit -> HitRow(hit) { onNavigate(hit.tab) } }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun PaletteGroupLabel(text: String) {
    val t = LocalTokens.current
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = t.textTertiary,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun PaletteRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val t = LocalTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .pressable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(t.cardSunken),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = t.accentDeep, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = t.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = t.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HitRow(hit: SearchHit, onClick: () -> Unit) {
    val t = LocalTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .pressable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(t.cardSunken),
            contentAlignment = Alignment.Center
        ) {
            Icon(hitIcon(hit.kind), contentDescription = null, tint = t.textSecondary, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                hit.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (hit.done) t.textTertiary else t.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (hit.subtitle.isNotBlank()) {
                Text(
                    hit.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = t.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun hitIcon(kind: String): ImageVector = when (kind) {
    "task" -> Icons.Filled.Check
    "goal" -> Icons.Filled.Flag
    "habit" -> Icons.Filled.Repeat
    "journal" -> Icons.Filled.AutoStories
    "prayer", "routine" -> Icons.Filled.Church
    "wish" -> Icons.Filled.ShoppingBag
    "practice", "album" -> Icons.Filled.MusicNote
    "career" -> Icons.Filled.Insights
    "person" -> Icons.Filled.Group
    "event" -> Icons.Filled.EventNote
    "intention" -> Icons.Filled.Flag
    "growth", "deed" -> Icons.Filled.SelfImprovement
    "recovery" -> Icons.Filled.SelfImprovement
    else -> Icons.Filled.Search
}
