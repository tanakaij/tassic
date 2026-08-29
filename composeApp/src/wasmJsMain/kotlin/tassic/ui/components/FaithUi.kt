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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tassic.data.Bible
import tassic.data.Graph
import tassic.data.MemoryVerse
import tassic.data.ReadingPlan
import tassic.data.T
import tassic.platform.chime
import tassic.platform.openUrl
import tassic.platform.vibrate
import tassic.ui.theme.AmberDeep
import tassic.ui.theme.Blue
import tassic.ui.theme.Coral
import tassic.ui.theme.Green
import tassic.ui.theme.LocalTokens
import tassic.ui.theme.Violet

/**
 * Faith surfaces.
 *
 * The Faith tab held rhythms, reminders and a prayer list — a decent scaffold,
 * but nothing in it actually helped with the two things that make up most of a
 * Christian's ordinary week: being in the Bible, and praying with some shape to
 * it. These add reading plans, verse memory, gratitude and a guided prayer
 * session.
 *
 * Two things this deliberately does not do. It ships **no scripture text** —
 * see [Bible] for the reasoning — and it makes **no theological claims of its
 * own**. It is a set of tools for a practice the user already has, not a
 * devotional writing at them. Everything it says back to them is drawn from
 * what they themselves recorded.
 */

// ---------------------------------------------------------------- reading

@Composable
fun ReadingPlanCard(plan: ReadingPlan, onPick: () -> Unit) {
    val store = Graph.store
    val t = LocalTokens.current
    val feedback = rememberFeedback()
    val today = T.today()

    val dayIndex = store.readingDayIndex(plan, today)
    val reference = plan.days.getOrNull(dayIndex).orEmpty()
    val doneToday = plan.completedDays.contains(dayIndex)
    val behind = store.readingBehind(plan, today)
    val progress = if (plan.days.isEmpty()) 0f else plan.completedDays.size.toFloat() / plan.days.size

    TassicCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(plan.name, style = MaterialTheme.typography.titleSmall, color = t.textPrimary)
                Text(
                    "Day ${dayIndex + 1} of ${plan.days.size} · ${plan.completedDays.size} read",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.textSecondary
                )
            }
            ProgressRing(progress = progress, diameter = 46, thickness = 5, color = t.accent) {
                Text(
                    "${(progress * 100).toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = t.textPrimary
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SunkenBox {
            Text("TODAY'S READING", style = MaterialTheme.typography.labelSmall, color = t.textTertiary)
            Spacer(Modifier.height(4.dp))
            Text(
                reference.ifBlank { "Plan complete" },
                style = MaterialTheme.typography.headlineSmall,
                color = t.textPrimary
            )
        }

        // Being behind is stated plainly and without weight. A reading plan that
        // shames you on day nine is a reading plan you delete on day ten, and
        // the number is only useful as information about the plan's fit.
        if (behind > 1) {
            Spacer(Modifier.height(8.dp))
            Text(
                "$behind days outstanding. Pick up from today if that's easier — the plan will still be here.",
                style = MaterialTheme.typography.bodySmall,
                color = t.textSecondary
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (reference.isNotBlank()) {
                PrimaryButton(if (doneToday) "Read \u2713" else "Mark read") {
                    store.toggleReadingDay(plan, dayIndex)
                    if (!doneToday) feedback.confirm("Day ${dayIndex + 1} marked")
                }
                GhostButton("Open passage") { openUrl(Bible.readerUrl(reference)) }
            }
            GhostButton("Change plan", onPick)
        }

        if (reference.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.OpenInNew,
                    contentDescription = null,
                    tint = t.textTertiary,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "Tassic keeps the reference; the text opens in your reader.",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.textTertiary
                )
            }
        }
    }
}

@Composable
fun ReadingPlanPickerSheet(onDismiss: () -> Unit) {
    val store = Graph.store
    val t = LocalTokens.current
    val feedback = rememberFeedback()
    var selected by rememberState<String?>(null)

    TassicSheet(title = "Choose a reading plan", onDismiss = onDismiss) {
        Text(
            "Plans are generated on your device from the structure of the canon, so they work offline and nothing is downloaded. " +
                "Only one runs at a time — two half-finished plans is the state most people are already in.",
            style = MaterialTheme.typography.bodyMedium,
            color = t.textSecondary
        )
        Spacer(Modifier.height(12.dp))

        Bible.TEMPLATES.forEach { template ->
            val on = selected == template.key
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(t.radiusControl.dp))
                    .background(if (on) t.accent.copy(alpha = if (t.dark) 0.16f else 0.10f) else t.cardSunken)
                    .border(
                        1.dp,
                        if (on) t.accent.copy(alpha = 0.5f) else Color.Transparent,
                        RoundedCornerShape(t.radiusControl.dp)
                    )
                    .pressable { selected = template.key }
                    .padding(horizontal = 12.dp, vertical = 11.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = if (on) t.accentDeep else t.textSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(template.name, style = MaterialTheme.typography.titleSmall, color = t.textPrimary)
                }
                Spacer(Modifier.height(3.dp))
                Text(template.blurb, style = MaterialTheme.typography.bodySmall, color = t.textSecondary)
                Spacer(Modifier.height(4.dp))
                Pill("${template.days} days")
            }
        }

        SheetActions(
            saveLabel = "Start",
            saveEnabled = selected != null,
            onSave = {
                val template = Bible.TEMPLATES.firstOrNull { it.key == selected }
                if (template != null) {
                    store.startReadingPlan(template)
                    feedback.confirm("${template.name} started")
                }
                onDismiss()
            },
            onCancel = onDismiss
        )
    }
}

// ------------------------------------------------------------------- verses

@Composable
fun VerseSheet(edit: MemoryVerse?, onDismiss: () -> Unit) {
    val store = Graph.store
    val t = LocalTokens.current
    var reference by rememberState(edit?.reference ?: "")
    var text by rememberState(edit?.text ?: "")
    var note by rememberState(edit?.note ?: "")
    var confirmDelete by rememberState(false)

    TassicSheet(title = if (edit == null) "Learn a verse" else "Edit verse", onDismiss = onDismiss) {
        LabeledField(reference, { reference = it }, "Reference", placeholder = "Philippians 4:6\u20137")
        FieldLabel("The verse, in your own hand")
        LabeledField(
            text,
            { text = it },
            "Type it out",
            placeholder = "Write it from your own Bible, in the translation you use",
            singleLine = false,
            minLines = 4
        )
        Text(
            "Tassic ships no Bible text, so this is yours to type — which is also the better first pass at learning it. " +
                "Use whichever translation you read.",
            style = MaterialTheme.typography.bodySmall,
            color = t.textSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        FieldLabel("Why this one")
        LabeledField(note, { note = it }, "Note", placeholder = "Optional", singleLine = false, minLines = 2)

        if (edit != null) {
            Spacer(Modifier.height(10.dp))
            GhostButton("Remove", { confirmDelete = true })
        }

        SheetActions(
            saveEnabled = reference.isNotBlank() && text.isNotBlank(),
            onSave = {
                if (edit == null) {
                    store.addVerse(MemoryVerse(reference = reference.trim(), text = text.trim(), note = note.trim()))
                } else {
                    store.updateVerse(edit.copy(reference = reference.trim(), text = text.trim(), note = note.trim()))
                }
                onDismiss()
            },
            onCancel = onDismiss
        )
    }

    if (confirmDelete && edit != null) {
        ConfirmDelete(
            title = "Remove ${edit.reference}?",
            message = "Its review history goes with it.",
            onConfirm = { store.deleteVerse(edit.id); onDismiss() },
            onDismiss = { confirmDelete = false }
        )
    }
}

/**
 * The review pass.
 *
 * Reference first, text hidden, and you say honestly whether you had it before
 * the answer appears. A flashcard that shows you the answer alongside the
 * prompt teaches recognition, which feels like knowing it and isn't.
 */
@Composable
fun VerseReviewSheet(onDismiss: () -> Unit) {
    val store = Graph.store
    val t = LocalTokens.current
    val feedback = rememberFeedback()
    val verses by store.verses.items.collectAsState()
    val today = T.today()

    val queue = remember(verses) { store.versesDue(today) }
    var index by rememberState(0)
    var revealed by rememberState(false)

    val verse = queue.getOrNull(index)

    TassicSheet(title = "Review", onDismiss = onDismiss) {
        if (verse == null) {
            Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (queue.isEmpty()) "Nothing due today" else "That's the last one",
                    style = MaterialTheme.typography.titleMedium,
                    color = t.textPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Verses come back on a widening schedule — one day, then three, a week, three weeks, two months.",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Done", onDismiss)
            }
            return@TassicSheet
        }

        Text(
            "${index + 1} of ${queue.size}",
            style = MaterialTheme.typography.labelSmall,
            color = t.textTertiary
        )
        Spacer(Modifier.height(8.dp))
        Text(verse.reference, style = MaterialTheme.typography.headlineSmall, color = t.textPrimary)
        Pill("Box ${verse.box} of 5")

        Spacer(Modifier.height(16.dp))
        if (revealed) {
            SunkenBox {
                Text(verse.text, style = MaterialTheme.typography.bodyLarge, color = t.textPrimary)
                if (verse.note.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(verse.note, style = MaterialTheme.typography.bodySmall, color = t.textSecondary)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Did you have it?", style = MaterialTheme.typography.bodyMedium, color = t.textSecondary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Not yet") {
                    store.reviewVerse(verse, remembered = false)
                    revealed = false
                    index += 1
                }
                PrimaryButton("Had it") {
                    store.reviewVerse(verse, remembered = true)
                    vibrate(10)
                    revealed = false
                    index += 1
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "A miss goes back to box one. A verse you couldn't recall isn't slightly less learned — it's unlearned, and spacing it three weeks out would only hide that.",
                style = MaterialTheme.typography.bodySmall,
                color = t.textTertiary
            )
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Say it from memory first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.textSecondary
                )
                Spacer(Modifier.height(14.dp))
                SecondaryButton("Show the verse") { revealed = true }
            }
        }
    }
}

// ---------------------------------------------------------------- gratitude

/**
 * Three lines a day.
 *
 * Capped at three on purpose: the exercise is choosing, and a list of fifteen
 * is a different and much weaker act than naming the three that actually stood
 * out.
 */
@Composable
fun GratitudeCard() {
    val store = Graph.store
    val t = LocalTokens.current
    val items by store.gratitude.items.collectAsState()
    val today = T.today()
    val todays = remember(items, today) { store.gratitudeOn(today) }
    var draft by rememberState("")

    TassicCard {
        SectionTitle(
            eyebrow = "Thanksgiving",
            title = "Three things today",
            subtitle = "${todays.size} of 3 named"
        )
        Spacer(Modifier.height(8.dp))

        todays.forEach { item ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Green.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Green, modifier = Modifier.size(11.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconActionBtn(
                    Icons.Filled.Check,
                    "Remove",
                    tint = t.textTertiary,
                    onClick = { store.deleteGratitude(item.id) }
                )
            }
        }

        if (todays.size < 3) {
            Spacer(Modifier.height(6.dp))
            LabeledField(draft, { draft = it }, "Something you're grateful for", placeholder = "Small and specific")
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Add") {
                store.addGratitude(draft)
                draft = ""
            }
        } else {
            Spacer(Modifier.height(6.dp))
            Text(
                "That's the three. Naming more isn't the exercise — choosing is.",
                style = MaterialTheme.typography.bodySmall,
                color = t.textSecondary
            )
        }
    }
}

// ------------------------------------------------------------------- prayer

private data class PrayerMovement(val label: String, val prompt: String, val minutes: Int)

/**
 * A guided prayer session.
 *
 * Structured on the old ACTS pattern — adoration, confession, thanksgiving,
 * supplication — because it is the most widely used shape across traditions and
 * it exists precisely to stop prayer collapsing into a list of requests.
 *
 * The prompts are questions, never words to pray. An app writing someone's
 * prayers for them would be presumptuous and, more to the point, useless: the
 * value is entirely in what they bring to it.
 */
@Composable
fun PrayerSessionSheet(onDismiss: () -> Unit) {
    val store = Graph.store
    val t = LocalTokens.current
    val feedback = rememberFeedback()
    val settings by store.settingsState.collectAsState()
    val prayers by store.prayers.items.collectAsState()

    val movements = remember {
        listOf(
            PrayerMovement("Adoration", "Who God is, before anything you need from him.", 3),
            PrayerMovement("Confession", "What you'd rather not say out loud.", 3),
            PrayerMovement("Thanksgiving", "What's already been given, named specifically.", 3),
            PrayerMovement("Supplication", "Your requests, and other people's before your own.", 6)
        )
    }

    var stage by rememberState(0)
    var elapsed by rememberState(0)
    var running by rememberState(true)

    val movement = movements.getOrNull(stage)
    val open = remember(prayers) { prayers.filter { !it.answered } }

    LaunchedEffect(stage, running) {
        while (running && movement != null) {
            delay(1000)
            elapsed += 1
            if (elapsed >= movement.minutes * 60) {
                if (settings.focusAlertOn) chime()
                if (stage < movements.lastIndex) {
                    stage += 1
                    elapsed = 0
                } else {
                    running = false
                }
            }
        }
    }

    val total = movements.sumOf { it.minutes }

    TassicSheet(title = "Pray", onDismiss = onDismiss) {
        if (movement == null || !running && stage == movements.lastIndex && elapsed >= movements.last().minutes * 60) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Amen.", style = MaterialTheme.typography.headlineSmall, color = t.textPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "$total minutes. Nothing of what you prayed is recorded — only that you did.",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Done") {
                    store.logPrayerSession(total)
                    feedback.confirm("$total minutes logged")
                    onDismiss()
                }
            }
            return@TassicSheet
        }

        val fraction = (elapsed.toFloat() / (movement.minutes * 60).toFloat()).coerceIn(0f, 1f)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ProgressRing(progress = fraction, diameter = 132, thickness = 10, color = accentForStage(stage)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        clockLabel(movement.minutes * 60 - elapsed),
                        style = MaterialTheme.typography.headlineSmall,
                        color = t.textPrimary
                    )
                    Text(
                        "${stage + 1} of ${movements.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = t.textSecondary
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(movement.label, style = MaterialTheme.typography.headlineSmall, color = t.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                movement.prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = t.textSecondary,
                textAlign = TextAlign.Center
            )
        }

        // The user's own prayer list, surfaced at the point it's actually
        // useful, with a tick that records only that it was prayed over.
        if (stage == movements.lastIndex && open.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionTitle(eyebrow = "Your list", title = "Carrying")
            open.take(8).forEach { point ->
                val prayedToday = point.lastPrayedEpochDay == T.today()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .pressable { store.prayedFor(point) }
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (prayedToday) Violet else t.cardSunken),
                        contentAlignment = Alignment.Center
                    ) {
                        if (prayedToday) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            point.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = t.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (point.prayedCount > 0) {
                            Text(
                                "Prayed ${point.prayedCount} time(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = t.textTertiary
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(if (running) "Pause" else "Resume") { running = !running }
            if (stage < movements.lastIndex) {
                GhostButton("Next") { stage += 1; elapsed = 0 }
            }
            GhostButton("End and log") {
                val minutes = (movements.take(stage).sumOf { it.minutes } + elapsed / 60).coerceAtLeast(0)
                store.logPrayerSession(minutes)
                feedback.confirm(if (minutes > 0) "$minutes minutes logged" else "Ended")
                onDismiss()
            }
        }
    }
}

private fun accentForStage(stage: Int): Color = when (stage) {
    0 -> AmberDeep
    1 -> Coral
    2 -> Green
    else -> Blue
}

private fun clockLabel(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "${(safe / 60).toString().padStart(2, '0')}:${(safe % 60).toString().padStart(2, '0')}"
}
