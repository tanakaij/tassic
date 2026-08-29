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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PushPin
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tassic.data.ContactStatus
import tassic.data.Graph
import tassic.data.People
import tassic.data.Person
import tassic.data.T
import tassic.ui.components.EmptyState
import tassic.ui.components.IconActionBtn
import tassic.ui.components.InkCard
import tassic.ui.components.LockGate
import tassic.ui.components.MetricTile
import tassic.ui.components.PersonSheet
import tassic.ui.components.Pill
import tassic.ui.components.SectionTitle
import tassic.ui.components.SegmentedControl
import tassic.ui.components.SoftDivider
import tassic.ui.components.SunkenBox
import tassic.ui.components.TabScaffold
import tassic.ui.components.TassicCard
import tassic.ui.components.pressable
import tassic.ui.components.rememberFeedback
import tassic.ui.components.rememberState
import tassic.ui.theme.AmberDeep
import tassic.ui.theme.Blue
import tassic.ui.theme.Coral
import tassic.ui.theme.Green
import tassic.ui.theme.LocalTokens

/**
 * People.
 *
 * The app tracked tasks, training, money, faith and music, and had nothing at
 * all for the people all of it is usually for. Relationships fail quietly —
 * nobody notices the month they stopped calling — which is exactly what a
 * tracker is good at catching.
 *
 * The tone is the hard part. Nobody wants software scoring their friendships,
 * so this states facts and stops: how long it's been, what rhythm you said you
 * wanted, whose birthday is coming. No streaks, no grades, no guilt copy.
 */
@Composable
fun PeopleTab() {
    val store = Graph.store
    val settings by store.settingsState.collectAsState()
    val people by store.people.items.collectAsState()
    val activity by store.activity.items.collectAsState()
    val t = LocalTokens.current
    val feedback = rememberFeedback()
    val today = T.today()

    var view by rememberState("Everyone")
    var sheetOpen by rememberState(false)
    var editing by rememberState<Person?>(null)

    val statuses = remember(people, activity, today) { People.all(store, today) }
    val overdue = statuses.filter { it.overdue }
    val birthdays = remember(people, today) { People.upcomingBirthdays(store, today) }

    LockGate("PEOPLE") {
        TabScaffold(
            fabIcon = Icons.Filled.Add,
            fabLabel = "Add person",
            onFab = { editing = null; sheetOpen = true }
        ) {
            InkCard {
                Text("PEOPLE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        statuses.isEmpty() -> "Nobody added yet."
                        overdue.isEmpty() && birthdays.isEmpty() ->
                            "${statuses.size} people, everyone inside the rhythm you set."
                        overdue.isNotEmpty() ->
                            "${overdue.size} of ${statuses.size} past the rhythm you set."
                        else -> "${birthdays.size} birthday(s) coming up."
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("${statuses.size}", "people", tint = Blue, modifier = Modifier.weight(1f))
                    MetricTile("${overdue.size}", "overdue", tint = if (overdue.isEmpty()) Green else Coral, modifier = Modifier.weight(1f))
                    MetricTile("${birthdays.size}", "birthdays soon", tint = AmberDeep, modifier = Modifier.weight(1f))
                }
            }

            if (statuses.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Group,
                    title = "No one here yet",
                    hint = "Add the handful of people you actually want to stay close to. A rhythm is optional — a birthday alone is reason enough.",
                    actionText = "Add someone",
                    onAction = { editing = null; sheetOpen = true }
                )
                return@TabScaffold
            }

            if (birthdays.isNotEmpty()) {
                SectionTitle(eyebrow = "Coming up", title = "Birthdays")
                TassicCard {
                    birthdays.forEachIndexed { index, status ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(AmberDeep.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Cake, contentDescription = null, tint = AmberDeep, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(status.person.name, style = MaterialTheme.typography.titleSmall, color = t.textPrimary)
                                Text(
                                    when (status.daysToBirthday) {
                                        0 -> "Today"
                                        1 -> "Tomorrow"
                                        else -> "In ${status.daysToBirthday} days · ${
                                            People.nextBirthday(status.person, today)?.let { T.shortDate(it) } ?: ""
                                        }"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = t.textSecondary
                                )
                            }
                        }
                        if (index != birthdays.lastIndex) SoftDivider()
                    }
                }
            }

            SegmentedControl(
                options = listOf("Everyone", "Overdue", "Family"),
                selected = view,
                badge = { if (it == "Overdue" && overdue.isNotEmpty()) "${overdue.size}" else null },
                onSelect = { view = it }
            )

            val shown = when (view) {
                "Overdue" -> overdue
                "Family" -> statuses.filter { it.person.relationship.equals("Family", ignoreCase = true) }
                else -> statuses
            }

            if (shown.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Group,
                    title = if (view == "Overdue") "Everyone's current" else "Nobody in this group",
                    hint = if (view == "Overdue") {
                        "Every rhythm you set is being kept."
                    } else {
                        "Set someone's relationship to Family and they'll appear here."
                    }
                )
            } else {
                TassicCard {
                    shown.forEachIndexed { index, status ->
                        PersonRow(
                            status = status,
                            onLog = {
                                store.logContact(status.person)
                                feedback.confirm("Logged a conversation with ${status.person.name}")
                            },
                            onEdit = { editing = status.person; sheetOpen = true }
                        )
                        if (index != shown.lastIndex) SoftDivider()
                    }
                }
            }

            if (!settings.lockPeople && settings.lockReady) {
                SunkenBox {
                    Text(
                        "Notes here aren't behind the PIN. Turn on Settings → Companion → Lock people if that matters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textSecondary
                    )
                }
            }

            if (sheetOpen) PersonSheet(editing) { sheetOpen = false }
        }
    }
}

@Composable
private fun PersonRow(
    status: ContactStatus,
    onLog: () -> Unit,
    onEdit: () -> Unit
) {
    val t = LocalTokens.current
    val person = status.person
    val initials = person.name.trim().split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.take(1).uppercase() }
        .ifBlank { "?" }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (status.overdue) Coral.copy(alpha = 0.16f) else Blue.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initials,
                style = MaterialTheme.typography.titleSmall,
                color = if (status.overdue) Coral else Blue
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    person.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = t.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (person.pinned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        tint = t.accentDeep,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Text(
                People.caption(status),
                style = MaterialTheme.typography.bodySmall,
                color = if (status.overdue) Coral else t.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Pill(
            "Log",
            bg = t.accent.copy(alpha = 0.9f),
            fg = t.onAccent,
            modifier = Modifier.clip(RoundedCornerShape(999.dp)).pressable(onClick = onLog)
        )
        IconActionBtn(Icons.Filled.Edit, "Edit ${person.name}", tint = t.textTertiary, onClick = onEdit)
    }
}
