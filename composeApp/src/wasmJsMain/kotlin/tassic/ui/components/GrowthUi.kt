package tassic.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tassic.data.GoodDeed
import tassic.data.Graph
import tassic.data.Growth
import tassic.data.GrowthArea
import tassic.data.GrowthPulse
import tassic.data.T
import tassic.ui.theme.AmberDeep
import tassic.ui.theme.Blue
import tassic.ui.theme.Coral
import tassic.ui.theme.Green
import tassic.ui.theme.LocalTokens
import tassic.ui.theme.Violet

/**
 * Growth surfaces.
 *
 * The visual language here is deliberately different from the rest of the app.
 * No streaks, no percentages, no red. A five-point monthly rating with the
 * middle marked "about the same" — because for most of these, most months, the
 * honest answer is "about the same", and an interface that makes that feel like
 * failure will simply stop being used truthfully.
 */

private val RATING_LABELS = listOf(
    "Worse",
    "Slipping",
    "About the same",
    "Better",
    "Much better"
)

fun ratingColor(rating: Int): Color = when (rating) {
    1 -> Coral
    2 -> AmberDeep
    3 -> Blue
    4 -> Green
    else -> Violet
}

/** The five-point picker, with the middle option framed as normal rather than neutral-bad. */
@Composable
fun RatingPicker(selected: Int, onSelect: (Int) -> Unit) {
    val t = LocalTokens.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { value ->
                val isSel = value == selected
                val tint = ratingColor(value)
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(t.radiusControl.dp))
                        .background(if (isSel) tint else tint.copy(alpha = if (t.dark) 0.16f else 0.10f))
                        .pressable { onSelect(value) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$value",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSel) Color.White else tint
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            RATING_LABELS[(selected - 1).coerceIn(0, 4)],
            style = MaterialTheme.typography.bodySmall,
            color = t.textSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

/** Twelve months of ratings as a strip. Unrated months are outlines, not gaps. */
@Composable
fun GrowthTrace(history: List<Int?>, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        history.forEach { rating ->
            Box(
                Modifier
                    .size(width = 14.dp, height = 22.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        if (rating == null) Color.Transparent else ratingColor(rating).copy(alpha = 0.85f)
                    )
                    .then(
                        if (rating == null) {
                            Modifier.border(1.dp, t.hairline, RoundedCornerShape(5.dp))
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (rating != null) {
                    Text(
                        "$rating",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------- editor

@Composable
fun GrowthAreaSheet(edit: GrowthArea?, onDismiss: () -> Unit) {
    val store = Graph.store
    val t = LocalTokens.current
    val feedback = rememberFeedback()

    var name by rememberState(edit?.name ?: "")
    var dimension by rememberState(edit?.dimension ?: "CHARACTER")
    var intention by rememberState(edit?.intention ?: "")
    var practices by rememberState(edit?.practices?.joinToString("\n") ?: "")
    var evidence by rememberState(edit?.evidence ?: "")
    var confirmDelete by rememberState(false)

    TassicSheet(
        title = if (edit == null) "Something to work on" else "Edit ${edit.name}",
        onDismiss = onDismiss
    ) {
        LabeledField(name, { name = it }, "The area", placeholder = "Patience under pressure")

        FieldLabel("Which part of life")
        SelectChips(Growth.DIMENSIONS.map { it.key }, dimension, label = { Growth.dimensionLabel(it) }) {
            dimension = it
        }
        Text(
            Growth.DIMENSIONS.firstOrNull { it.key == dimension }?.blurb.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = t.textSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        FieldLabel("Who you're trying to become")
        LabeledField(
            intention,
            { intention = it },
            "Intention",
            placeholder = "In your own words",
            singleLine = false,
            minLines = 2
        )

        FieldLabel("What you'll actually do about it")
        LabeledField(
            practices,
            { practices = it },
            "Practices, one per line",
            placeholder = "Pause before answering when irritated",
            singleLine = false,
            minLines = 3
        )
        Text(
            "Concrete and observable. \"Be kinder\" can't be kept; \"let someone else finish before I speak\" can.",
            style = MaterialTheme.typography.bodySmall,
            color = t.textSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        FieldLabel("What progress would look like")
        LabeledField(
            evidence,
            { evidence = it },
            "Evidence",
            placeholder = "Fewer sharp replies I'd take back",
            singleLine = false,
            minLines = 2
        )
        Text(
            "Written now, read at the end of the month — before you rate yourself, so the standard isn't set by whatever mood you're in that day.",
            style = MaterialTheme.typography.bodySmall,
            color = t.textSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        if (edit != null) {
            Spacer(Modifier.height(10.dp))
            GhostButton("Remove this area", { confirmDelete = true })
        }

        SheetActions(
            saveEnabled = name.isNotBlank(),
            onSave = {
                val base = edit ?: GrowthArea(createdAt = T.now(), sortOrder = store.activeGrowthAreas().size)
                val item = base.copy(
                    name = name.trim(),
                    dimension = dimension,
                    intention = intention.trim(),
                    practices = practices.lines().map { it.trim().removePrefix("- ") }.filter { it.isNotEmpty() },
                    evidence = evidence.trim()
                )
                if (edit == null) store.addGrowthArea(item) else store.updateGrowthArea(item)
                onDismiss()
            },
            onCancel = onDismiss
        )
    }

    if (confirmDelete && edit != null) {
        ConfirmDelete(
            title = "Remove ${edit.name}?",
            message = "Its monthly ratings go with it.",
            onConfirm = {
                store.deleteGrowthArea(edit.id)
                feedback.say("Removed")
                onDismiss()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

// ------------------------------------------------------------ monthly review

/**
 * The end-of-month pass over every growth area.
 *
 * Shows the evidence line the user wrote *before* the rating picker, on
 * purpose: the standard should be the one they set when they were thinking
 * clearly, not whatever they feel about themselves on the 29th.
 */
@Composable
fun GrowthReviewSheet(onDismiss: () -> Unit) {
    val store = Graph.store
    val t = LocalTokens.current
    val feedback = rememberFeedback()
    val today = T.today()
    val month = remember(today) { Growth.monthIndex(today) }
    val areas = remember(today) { store.activeGrowthAreas() }

    var ratings by rememberState(
        areas.associate { area ->
            area.id to (Growth.pulse(store, area, today).currentRating ?: 3)
        }
    )
    var notes by rememberState(
        areas.associate { area -> area.id to store.growthNoteFor(area.id, month) }
    )

    TassicSheet(title = "${Growth.monthLabel(month)} — honestly", onDismiss = onDismiss) {
        Text(
            "Once a month, against the standard you set yourself. Three means about the same, and most months for most things, three is the true answer.",
            style = MaterialTheme.typography.bodyMedium,
            color = t.textSecondary
        )

        areas.forEach { area ->
            Spacer(Modifier.height(18.dp))
            SoftDivider()
            Spacer(Modifier.height(12.dp))
            Text(area.name, style = MaterialTheme.typography.titleMedium, color = t.textPrimary)
            Pill(Growth.dimensionLabel(area.dimension))
            if (area.evidence.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                SunkenBox {
                    Text("YOU SAID PROGRESS LOOKS LIKE", style = MaterialTheme.typography.labelSmall, color = t.textTertiary)
                    Spacer(Modifier.height(2.dp))
                    Text(area.evidence, style = MaterialTheme.typography.bodyMedium, color = t.textPrimary)
                }
            }
            Spacer(Modifier.height(10.dp))
            RatingPicker(ratings[area.id] ?: 3) { value ->
                ratings = ratings + (area.id to value)
            }
            Spacer(Modifier.height(8.dp))
            LabeledField(
                notes[area.id].orEmpty(),
                { notes = notes + (area.id to it) },
                "What actually happened",
                placeholder = "Optional",
                singleLine = false,
                minLines = 2
            )
        }

        Spacer(Modifier.height(14.dp))
        SheetActions(
            saveLabel = "Save the month",
            onSave = {
                areas.forEach { area ->
                    store.rateGrowthArea(
                        areaId = area.id,
                        monthIndex = month,
                        rating = ratings[area.id] ?: 3,
                        note = notes[area.id].orEmpty()
                    )
                }
                feedback.confirm("${Growth.shortMonthLabel(month)} recorded")
                onDismiss()
            },
            onCancel = onDismiss
        )
    }
}

// ------------------------------------------------------------------- deeds

/**
 * Logging something good you did.
 *
 * After the fact, never scheduled. A good deed added to a to-do list in advance
 * becomes an errand, and the recipient field is optional because a fair amount
 * of the best of this is the kind nobody signs.
 */
@Composable
fun DeedSheet(edit: GoodDeed?, onDismiss: () -> Unit) {
    val store = Graph.store
    val t = LocalTokens.current
    val feedback = rememberFeedback()
    val people by store.people.items.collectAsState()
    val today = T.today()

    var title by rememberState(edit?.title ?: "")
    var kind by rememberState(edit?.kind ?: "PERSON")
    var recipient by rememberState(edit?.recipient ?: "")
    var notes by rememberState(edit?.notes ?: "")
    var personId by rememberState(edit?.personId)

    TassicSheet(
        title = if (edit == null) "Something good" else "Edit",
        onDismiss = onDismiss
    ) {
        LabeledField(title, { title = it }, "What you did", placeholder = "Covered a neighbour's water bill")

        FieldLabel("Who or what it was for")
        SelectChips(Growth.DEED_KINDS.map { it.first }, kind, label = { Growth.deedKindLabel(it) }) { kind = it }

        if (kind != "ANONYMOUS") {
            Spacer(Modifier.height(8.dp))
            LabeledField(recipient, { recipient = it }, "Name (optional)", placeholder = "Leave blank if you'd rather not")

            if (people.isNotEmpty()) {
                FieldLabel("Or someone you already track")
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    people.take(6).forEach { person ->
                        val sel = personId == person.id
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (sel) t.chrome else t.cardSunken)
                                .pressable {
                                    personId = if (sel) null else person.id
                                    if (!sel) recipient = person.name
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                person.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (sel) t.chromeText else t.textSecondary
                            )
                        }
                    }
                }
                Text(
                    "Linking someone also marks them as spoken to today.",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.textTertiary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        FieldLabel("Anything worth remembering")
        LabeledField(notes, { notes = it }, "Notes", placeholder = "Optional", singleLine = false, minLines = 2)

        Spacer(Modifier.height(10.dp))
        SunkenBox {
            Text(
                "There's no streak on this and there never will be. The moment it's done to keep a number alive it stops being the thing you were trying to build.",
                style = MaterialTheme.typography.bodySmall,
                color = t.textSecondary
            )
        }

        SheetActions(
            saveEnabled = title.isNotBlank(),
            onSave = {
                val base = edit ?: GoodDeed(createdAt = T.now())
                val item = base.copy(
                    title = title.trim(),
                    kind = kind,
                    recipient = if (kind == "ANONYMOUS") "" else recipient.trim(),
                    notes = notes.trim(),
                    personId = if (kind == "ANONYMOUS") null else personId,
                    epochDay = if (edit == null) today else base.epochDay,
                    monthIndex = if (edit == null) Growth.monthIndex(today) else base.monthIndex
                )
                if (edit == null) store.addDeed(item) else store.updateDeed(item)
                feedback.confirm("Logged")
                onDismiss()
            },
            onCancel = onDismiss
        )
    }
}

/** One growth area as a row: name, dimension, this month's rating and the year strip. */
@Composable
fun GrowthAreaRow(pulse: GrowthPulse, onEdit: () -> Unit) {
    val t = LocalTokens.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(t.radiusControl.dp))
            .pressable(onClick = onEdit)
            .padding(vertical = 10.dp, horizontal = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pulse.area.name, style = MaterialTheme.typography.titleSmall, color = t.textPrimary)
                Text(
                    buildString {
                        append(Growth.dimensionLabel(pulse.area.dimension))
                        if (pulse.monthsRated > 0) append(" · ${pulse.monthsRated} month(s) rated")
                        pulse.average?.let { append(" · avg ${(it * 10).toInt() / 10}.${(it * 10).toInt() % 10}") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = t.textSecondary
                )
            }
            val current = pulse.currentRating
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (current == null) t.cardSunken else ratingColor(current)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (current == null) {
                    Text("—", style = MaterialTheme.typography.titleSmall, color = t.textTertiary)
                } else {
                    Text("$current", style = MaterialTheme.typography.titleSmall, color = Color.White)
                }
            }
        }
        if (pulse.area.practices.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            pulse.area.practices.take(3).forEach { practice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = t.textTertiary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(practice, style = MaterialTheme.typography.bodySmall, color = t.textSecondary)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        GrowthTrace(pulse.history)
    }
}
