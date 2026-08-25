@file:OptIn(ExperimentalMaterial3Api::class)

package tassic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlin.js.JsAny
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import tassic.data.CareerItem
import tassic.data.FaithRoutine
import tassic.data.Graph
import tassic.data.JournalEntry
import tassic.data.PrayerPoint
import tassic.data.Priority
import tassic.data.T
import tassic.data.WishItem
import tassic.platform.AudioRecorder
import tassic.platform.AudioStore
import tassic.platform.awaitOrNull
import tassic.ui.theme.Coral
import tassic.ui.theme.Green
import tassic.ui.theme.Muted
import tassic.ui.theme.Navy

// ---------------------------------------------------------------- faith routines

@Composable
fun RoutineSheet(edit: FaithRoutine?, onDismiss: () -> Unit) {
    val store = Graph.store
    var title by rememberState(edit?.title ?: "")
    var cadence by rememberState(edit?.cadence ?: "Daily")
    var dayTag by rememberState(edit?.dayTag?.ifEmpty { "SUN" } ?: "SUN")
    var hour by rememberState(edit?.reminderHour ?: 8)
    var reminderOn by rememberState(edit?.reminderOn ?: false)

    TassicSheet(title = if (edit == null) "New Routine" else "Edit Routine", onDismiss = onDismiss) {
        LabeledField(title, { title = it }, "Routine", placeholder = "e.g. Daily Bible Reading")
        FieldLabel("Cadence")
        SelectChips(listOf("Daily", "Weekly", "Monthly", "Custom"), cadence) { cadence = it }
        if (cadence == "Weekly") {
            FieldLabel("Day of week")
            SelectChips(listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"), dayTag) { dayTag = it }
        }
        Stepper("Reminder hour", hour, { hour = it }, range = 0..23, suffix = ":00")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Web notifications", style = MaterialTheme.typography.bodyLarge, color = Navy)
                Text(
                    "Fires while Tassic is open; scheduled push triggers are handled by sw.js",
                    style = MaterialTheme.typography.bodySmall, color = Muted
                )
            }
            Switch(
                checked = reminderOn,
                onCheckedChange = { reminderOn = it },
                colors = SwitchDefaults.colors(checkedTrackColor = Green)
            )
        }
        SheetActions(
            onSave = {
                val base = edit ?: FaithRoutine(createdAt = T.now())
                val item = base.copy(
                    title = title.trim(),
                    cadence = cadence,
                    dayTag = if (cadence == "Weekly") dayTag else "",
                    reminderHour = hour,
                    reminderOn = reminderOn
                )
                if (edit == null) store.addRoutine(item) else store.updateRoutine(item)
                onDismiss()
            },
            onCancel = onDismiss,
            saveEnabled = title.isNotBlank()
        )
    }
}

// ---------------------------------------------------------------- prayers

@Composable
fun PrayerSheet(edit: PrayerPoint?, onDismiss: () -> Unit) {
    val store = Graph.store
    var title by rememberState(edit?.title ?: "")
    var details by rememberState(edit?.details ?: "")
    var category by rememberState(edit?.category ?: "General")

    TassicSheet(title = if (edit == null) "New Prayer Point" else "Edit Prayer Point", onDismiss = onDismiss) {
        LabeledField(title, { title = it }, "Prayer request", placeholder = "What are you believing for?")
        FieldLabel("Details")
        LabeledField(details, { details = it }, "Details", singleLine = false, minLines = 3)
        LabeledField(category, { category = it }, "Category", placeholder = "Family, Health, Work…")
        SheetActions(
            onSave = {
                val base = edit ?: PrayerPoint(createdAt = T.now())
                val item = base.copy(
                    title = title.trim(),
                    details = details.trim(),
                    category = category.trim().ifEmpty { "General" }
                )
                if (edit == null) store.addPrayer(item) else store.updatePrayer(item)
                onDismiss()
            },
            onCancel = onDismiss,
            saveEnabled = title.isNotBlank()
        )
    }
}

// ---------------------------------------------------------------- wishlist

@Composable
fun WishSheet(edit: WishItem?, onDismiss: () -> Unit) {
    val store = Graph.store
    var name by rememberState(edit?.name ?: "")
    var category by rememberState(edit?.category ?: "Gear")
    var price by rememberState(edit?.price?.takeIf { it > 0.0 }?.toString() ?: "")
    var priority by rememberState(edit?.priority ?: Priority.NORMAL)
    var url by rememberState(edit?.url ?: "")

    TassicSheet(title = if (edit == null) "New Wishlist Item" else "Edit Item", onDismiss = onDismiss) {
        LabeledField(name, { name = it }, "Item", placeholder = "e.g. Audio interface (2-in)")
        LabeledField(category, { category = it }, "Category", placeholder = "Gear, Electronics, License…")
        LabeledField(price, { price = it.filter { c -> c.isDigit() || c == '.' } }, "Target price")
        FieldLabel("Priority")
        SelectChips(Priority.entries.toList(), priority) { priority = it }
        LabeledField(url, { url = it }, "Product link (optional)", placeholder = "https://…")
        SheetActions(
            onSave = {
                val base = edit ?: WishItem(createdAt = T.now())
                val item = base.copy(
                    name = name.trim(),
                    category = category.trim().ifEmpty { "Gear" },
                    price = price.toDoubleOrNull() ?: 0.0,
                    priority = priority,
                    url = url.trim()
                )
                if (edit == null) store.addWish(item) else store.updateWish(item)
                onDismiss()
            },
            onCancel = onDismiss,
            saveEnabled = name.isNotBlank()
        )
    }
}

// ---------------------------------------------------------------- career roadmap

@Composable
fun MilestoneSheet(
    edit: CareerItem?,
    paths: List<String>,
    defaultPath: String,
    defaultStage: String = "",
    onDismiss: () -> Unit
) {
    val store = Graph.store
    var path by rememberState(edit?.path ?: defaultPath)
    var stage by rememberState(edit?.stage ?: defaultStage)
    var stageOrder by rememberState(edit?.stageOrder ?: 1)
    var title by rememberState(edit?.title ?: "")
    var url by rememberState(edit?.url ?: "")

    TassicSheet(title = if (edit == null) "New Milestone" else "Edit Milestone", onDismiss = onDismiss) {
        FieldLabel("Quick paths")
        SelectChips(paths.distinct(), path) { path = it }
        LabeledField(path, { path = it }, "Career path", placeholder = "e.g. Cloud Architecture")
        LabeledField(stage, { stage = it }, "Stage", placeholder = "e.g. Stage 1 · Web Fundamentals")
        Stepper("Stage order", stageOrder, { stageOrder = it }, range = 1..50)
        LabeledField(title, { title = it }, "Milestone", placeholder = "e.g. Build a Leaflet choropleth")
        LabeledField(url, { url = it }, "Resource link (optional)", placeholder = "https://…")
        SheetActions(
            onSave = {
                val base = edit ?: CareerItem(createdAt = T.now(), sortOrder = 99)
                val item = base.copy(
                    path = path.trim().ifEmpty { "GeoDev Roadmap" },
                    stage = stage.trim(),
                    stageOrder = stageOrder,
                    title = title.trim(),
                    url = url.trim()
                )
                if (edit == null) store.addCareer(item) else store.updateCareer(item)
                onDismiss()
            },
            onCancel = onDismiss,
            saveEnabled = title.isNotBlank() && stage.isNotBlank()
        )
    }
}

// ---------------------------------------------------------------- journal composer

@Composable
fun JournalComposerSheet(
    edit: JournalEntry?,
    prefillTag: String,
    onDismiss: () -> Unit
) {
    val store = Graph.store
    val feedback = rememberSheetScope()
    val cs = androidx.compose.runtime.rememberCoroutineScope()

    var title by rememberState(edit?.title ?: "")
    var body by rememberState(edit?.body ?: "")
    var mood by rememberState(edit?.mood ?: 3)
    var tags by rememberState(
        buildList {
            edit?.tags?.let { addAll(it) }
            if (prefillTag.isNotEmpty() && !contains(prefillTag)) add(prefillTag)
        }.joinToString(", ")
    )
    var audioId by rememberState(edit?.audioId)
    var pendingUrl by rememberState<String?>(null)
    var recording by rememberState(false)
    var seconds by rememberState(0)
    var controller by rememberState<JsAny?>(null)

    val startRec: () -> Unit = {
        cs.launch {
            val mime = AudioRecorder.pickMime()?.toString()
            if (mime == null) {
                feedback.launchSnackbar("Voice recording is not supported in this browser")
                return@launch
            }
            val c = AudioRecorder.start(mime).awaitOrNull()
            if (c == null) {
                feedback.launchSnackbar("Microphone unavailable — check permission")
                return@launch
            }
            controller = c
            recording = true
            seconds = 0
        }
    }

    val stopRec: () -> Unit = {
        val c = controller
        if (c != null) {
            recording = false
            controller = null
            cs.launch {
                val url = AudioRecorder.stop(c).awaitOrNull()?.toString()
                seconds = 0
                if (url == null) {
                    feedback.launchSnackbar("Recording failed")
                    return@launch
                }
                val id = "clip_${T.now()}"
                val saved = try {
                    AudioStore.put(id, url).await<JsAny?>()
                    true
                } catch (e: Throwable) {
                    false
                }
                if (saved) {
                    audioId = id
                    pendingUrl = url
                } else {
                    feedback.launchSnackbar("Could not store the voice clip")
                }
            }
        }
    }

    val playClip: (String) -> Unit = { id ->
        cs.launch {
            val url = pendingUrl ?: AudioStore.get(id).awaitOrNull()?.toString()
            if (url != null) AudioRecorder.play(url) else feedback.launchSnackbar("Clip missing from storage")
        }
    }

    val removeClip: () -> Unit = {
        audioId?.let { AudioStore.delete(it) }
        audioId = null
        pendingUrl = null
    }

    // Recording timer; auto-stop at 3 minutes to respect storage quotas.
    androidx.compose.runtime.LaunchedEffect(recording) {
        var t = 0
        while (recording) {
            kotlinx.coroutines.delay(1000)
            t++
            seconds = t
            if (t >= 180) stopRec()
        }
    }

    TassicSheet(title = if (edit == null) "New Journal Entry" else "Edit Entry", onDismiss = onDismiss) {
        LabeledField(title, { title = it }, "Title", placeholder = "Name this moment")
        FieldLabel("Reflection (# heading, - bullet supported)")
        LabeledField(body, { body = it }, "Write freely…", singleLine = false, minLines = 6)
        FieldLabel("Mood today")
        MoodPicker(mood) { mood = it }
        LabeledField(tags, { tags = it }, "Tags", placeholder = "gratitude, portfolio…")

        FieldLabel("Voice note")
        VoiceRecorderBlock(
            supported = AudioRecorder.isSupported(),
            recording = recording,
            seconds = seconds,
            audioId = audioId,
            onStart = startRec,
            onStop = stopRec,
            onPlay = playClip,
            onRemove = removeClip
        )

        SheetActions(
            onSave = {
                val item = (edit ?: JournalEntry(createdAt = T.now())).copy(
                    title = title.trim(),
                    body = body,
                    mood = mood,
                    audioId = audioId,
                    tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                )
                if (edit == null) store.addJournal(item)
                else store.updateJournal(item, previousAudioId = edit.audioId)
                onDismiss()
            },
            onCancel = onDismiss,
            saveEnabled = title.isNotBlank() || body.isNotBlank()
        )
    }
}

@Composable
private fun VoiceRecorderBlock(
    supported: Boolean,
    recording: Boolean,
    seconds: Int,
    audioId: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPlay: (String) -> Unit,
    onRemove: () -> Unit
) {
    if (!supported) {
        Text(
            "Voice notes need a browser with MediaRecorder support (Chrome, Edge, Firefox).",
            style = MaterialTheme.typography.bodySmall, color = Muted
        )
        return
    }
    Column {
        when {
            recording -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Coral)
                )
                Text(
                    "  Recording · ${seconds}s",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Coral,
                    modifier = Modifier.weight(1f)
                )
                DestructiveButton("Stop", onStop)
            }
            audioId != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("Voice note attached", bg = Green.copy(alpha = 0.16f), fg = Green)
                GhostButton("Play", { audioId?.let(onPlay) })
                GhostButton("Remove", onRemove)
            }
            else -> SecondaryButton("Record voice note", onStart)
        }
    }
}
