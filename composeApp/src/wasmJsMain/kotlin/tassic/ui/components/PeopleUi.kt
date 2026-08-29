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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import tassic.data.Coach
import tassic.data.Graph
import tassic.data.Intention
import tassic.data.People
import tassic.data.Person
import tassic.data.T
import tassic.ui.theme.Coral
import tassic.ui.theme.Green
import tassic.ui.theme.LocalTokens

// ------------------------------------------------------------------ person

@Composable
fun PersonSheet(edit: Person?, onDismiss: () -> Unit) {
    val store = Graph.store
    val t = LocalTokens.current
    val feedback = rememberFeedback()

    var name by rememberState(edit?.name ?: "")
    var relationship by rememberState(edit?.relationship ?: "Friend")
    var cadence by rememberState(edit?.cadenceDays ?: 30)
    var hasBirthday by rememberState((edit?.birthdayMonth ?: 0) > 0)
    var month by rememberState(if ((edit?.birthdayMonth ?: 0) > 0) edit!!.birthdayMonth else 1)
    var dayOfMonth by rememberState(if ((edit?.birthdayDay ?: 0) > 0) edit!!.birthdayDay else 1)
    var notes by rememberState(edit?.notes ?: "")
    var pinned by rememberState(edit?.pinned ?: false)
    var confirmDelete by rememberState(false)

    TassicSheet(title = if (edit == null) "Add someone" else "Edit ${edit.name}", onDismiss = onDismiss) {
        LabeledField(name, { name = it }, "Name", placeholder = "Who is this?")

        FieldLabel("Relationship")
        SelectChips(People.RELATIONSHIPS, relationship) { relationship = it }

        FieldLabel("How often you'd like to be in touch")
        SelectChips(
            People.CADENCE_CHOICES,
            cadence,
            label = { People.cadenceLabel(it).replaceFirstChar { c -> c.uppercase() } }
        ) { cadence = it }
        Text(
            if (cadence == 0) {
                "No nudge — they'll only appear for their birthday."
            } else {
                "You'll see them listed as overdue after ${People.cadenceLabel(cadence)} with no logged conversation."
            },
            style = MaterialTheme.typography.bodySmall,
            color = t.textSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        FieldLabel("Birthday")
        if (hasBirthday) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    MiniStepper("Day", dayOfMonth, { dayOfMonth = it }, range = 1..31)
                    MiniStepper("Month", month, { month = it }, range = 1..12)
                }
                GhostButton("Remove", { hasBirthday = false })
            }
        } else {
            GhostButton("+ Add a birthday", { hasBirthday = true })
        }

        FieldLabel("Notes")
        LabeledField(notes, { notes = it }, "Notes", singleLine = false, minLines = 3)

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(t.radiusControl.dp))
                .background(t.cardSunken)
                .pressable { pinned = !pinned }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (pinned) t.accent else t.hairline),
                contentAlignment = Alignment.Center
            ) {
                if (pinned) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = t.onAccent, modifier = Modifier.size(13.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Text("Keep at the top of the list", style = MaterialTheme.typography.bodyMedium, color = t.textPrimary)
        }

        if (edit != null) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Log a conversation today", {
                    store.logContact(edit)
                    feedback.confirm("Logged")
                    onDismiss()
                })
                GhostButton("Remove", { confirmDelete = true })
            }
        }

        SheetActions(
            saveEnabled = name.isNotBlank(),
            onSave = {
                val base = edit ?: Person(createdAt = T.now())
                val item = base.copy(
                    name = name.trim(),
                    relationship = relationship,
                    cadenceDays = cadence,
                    birthdayMonth = if (hasBirthday) month else 0,
                    birthdayDay = if (hasBirthday) dayOfMonth else 0,
                    notes = notes.trim(),
                    pinned = pinned
                )
                if (edit == null) store.addPerson(item) else store.updatePerson(item)
                onDismiss()
            },
            onCancel = onDismiss
        )
    }

    if (confirmDelete && edit != null) {
        ConfirmDelete(
            title = "Remove ${edit.name}?",
            message = "Their notes and contact history go with them.",
            onConfirm = {
                val snapshot = edit.copy()
                store.deletePerson(edit.id)
                feedback.undoable("${edit.name} removed") {
                    store.restoreItem(store.people, snapshot)
                }
                onDismiss()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

// ------------------------------------------------------------- weekly plan

/**
 * The Sunday counterpart to the evening review.
 *
 * Three lines, hard-capped. A weekly plan with ten items is a backlog wearing a
 * plan's clothes, and the whole value of the ritual is being forced to say what
 * matters *most* — which only happens when the list is too short to dodge the
 * question.
 */
@Composable
fun WeeklyPlanSheet(onDismiss: () -> Unit) {
    val store = Graph.store
    val t = LocalTokens.current
    val feedback = rememberFeedback()
    val today = T.today()

    val weekIndex = remember(today) { Coach.targetWeek(store, today) }
    val existing = remember(weekIndex) { store.weekPlanFor(weekIndex) }
    val lastWeek = remember(weekIndex) { store.weekPlanFor(weekIndex - 1) }

    var first by rememberState(existing?.priorities?.getOrNull(0)?.title ?: "")
    var second by rememberState(existing?.priorities?.getOrNull(1)?.title ?: "")
    var third by rememberState(existing?.priorities?.getOrNull(2)?.title ?: "")
    var notes by rememberState(existing?.notes ?: "")

    TassicSheet(
        title = if (existing == null) "Plan the week" else "Edit this week's plan",
        onDismiss = onDismiss
    ) {
        Text(
            "Three things, at most. The week gets measured against these rather than against how busy it looked.",
            style = MaterialTheme.typography.bodyMedium,
            color = t.textSecondary
        )

        // Carrying last week forward matters: unfinished priorities are the
        // single most useful input to this week's, and retyping them from
        // memory is how they get quietly dropped.
        if (lastWeek != null && lastWeek.priorities.any { !it.done }) {
            Spacer(Modifier.height(12.dp))
            SunkenBox {
                Text("UNFINISHED LAST WEEK", style = MaterialTheme.typography.labelSmall, color = t.textTertiary)
                Spacer(Modifier.height(4.dp))
                lastWeek.priorities.filter { !it.done }.forEach { intention ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .pressable {
                                when {
                                    first.isBlank() -> first = intention.title
                                    second.isBlank() -> second = intention.title
                                    third.isBlank() -> third = intention.title
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            intention.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = t.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Pill("Carry over")
                    }
                }
            }
        }

        FieldLabel("This week's priorities")
        LabeledField(first, { first = it }, "First", placeholder = "The one that would make the week")
        Spacer(Modifier.height(8.dp))
        LabeledField(second, { second = it }, "Second", placeholder = "Optional")
        Spacer(Modifier.height(8.dp))
        LabeledField(third, { third = it }, "Third", placeholder = "Optional")

        FieldLabel("Anything to remember")
        LabeledField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)

        SheetActions(
            saveLabel = "Set the week",
            saveEnabled = first.isNotBlank(),
            onSave = {
                val priorities = listOf(first, second, third)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .mapIndexed { index, title ->
                        Intention(
                            title = title,
                            done = existing?.priorities?.getOrNull(index)?.done ?: false
                        )
                    }
                store.saveWeekPlan(weekIndex, priorities, notes.trim())
                feedback.confirm("Week set")
                onDismiss()
            },
            onCancel = onDismiss
        )
    }
}

/** The week's priorities as a tickable card, shown on the Plan tab. */
@Composable
fun WeekPrioritiesCard(weekIndex: Long, onEdit: () -> Unit) {
    val store = Graph.store
    val t = LocalTokens.current
    val plans by store.weekPlans.items.collectAsState()
    val plan = remember(plans, weekIndex) { store.weekPlanFor(weekIndex) }

    TassicCard {
        SectionTitle(
            eyebrow = "This week",
            title = "What matters",
            subtitle = if (plan == null) "Nothing set yet" else "${plan.priorities.count { it.done }} of ${plan.priorities.size} closed"
        )
        Spacer(Modifier.height(8.dp))
        if (plan == null || plan.priorities.isEmpty()) {
            Text(
                "Pick up to three things and the week gets judged on those rather than on how full it looked.",
                style = MaterialTheme.typography.bodySmall,
                color = t.textSecondary
            )
            Spacer(Modifier.height(8.dp))
            GhostButton("Plan the week", onEdit)
        } else {
            plan.priorities.forEachIndexed { index, intention ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .pressable { store.toggleIntention(weekIndex, index) }
                        .padding(vertical = 9.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(23.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (intention.done) Green else t.cardSunken),
                        contentAlignment = Alignment.Center
                    ) {
                        if (intention.done) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = t.textTertiary
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        intention.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (intention.done) t.textTertiary else t.textPrimary,
                        textDecoration = if (intention.done) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (plan.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(plan.notes, style = MaterialTheme.typography.bodySmall, color = t.textSecondary)
            }
            Spacer(Modifier.height(4.dp))
            GhostButton("Edit the week", onEdit)
        }
    }
}

/** The prompt bar shown when a new week needs planning. */
@Composable
fun WeeklyPlanPrompt(onOpen: () -> Unit, onDismiss: () -> Unit) {
    val t = LocalTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(t.card)
            .pressable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Coral.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Coral, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Plan the week", style = MaterialTheme.typography.titleSmall, color = t.textPrimary)
            Text(
                "Three things that would make it a good one.",
                style = MaterialTheme.typography.bodySmall,
                color = t.textSecondary
            )
        }
        Text(
            "Later",
            style = MaterialTheme.typography.labelSmall,
            color = t.textTertiary,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .pressable(onClick = onDismiss)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}
