@file:OptIn(ExperimentalMaterial3Api::class)

package tassic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tassic.data.Capture
import tassic.data.CaptureKind
import tassic.data.Graph
import tassic.data.Nlp
import tassic.data.T
import tassic.data.commitCapture
import tassic.ui.theme.Blue
import tassic.ui.theme.Coral
import tassic.ui.theme.Green
import tassic.ui.theme.LocalTokens
import tassic.ui.theme.Violet

/**
 * Quick capture.
 *
 * One field, one Save, and a parser that reads the dates, times, repeats,
 * priorities, tags and estimates straight out of the sentence. The chips under
 * the field are not decoration — they are the app showing its work, so nothing
 * is filed somewhere the user didn't expect. Anything the parser doesn't
 * understand stays in the title rather than being quietly discarded.
 */
@Composable
fun QuickCaptureSheet(
    onDismiss: () -> Unit,
    initialText: String = "",
    onSaved: (String) -> Unit = {}
) {
    val store = Graph.store
    val settings by store.settingsState.collectAsState()
    val t = LocalTokens.current
    val feedback = rememberFeedback()

    var text by rememberState(initialText)
    var kindOverride by rememberState<CaptureKind?>(null)
    val focusRequester = remember { FocusRequester() }

    val defaultKind = remember(settings.captureDefaultKind) {
        runCatching { CaptureKind.valueOf(settings.captureDefaultKind.uppercase()) }
            .getOrDefault(CaptureKind.TASK)
    }

    val parsed: Capture = remember(text, kindOverride, settings.smartCaptureOn) {
        val base = Nlp.parse(
            raw = text,
            today = T.today(),
            defaultKind = defaultKind,
            smart = settings.smartCaptureOn,
            defaultReminderLead = settings.defaultReminderLeadMinutes
        )
        if (kindOverride != null) base.copy(kind = kindOverride!!) else base
    }

    // The sheet exists to be typed into, so it opens with the caret already in
    // the field. A frame of delay lets the sheet finish its entrance animation
    // first — requesting focus mid-transition drops the request on some engines.
    LaunchedEffect(Unit) {
        delay(220)
        runCatching { focusRequester.requestFocus() }
    }

    fun save() {
        if (parsed.isEmpty) return
        val message = store.commitCapture(parsed)
        feedback.confirm(message)
        onSaved(message)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = t.card,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 2.dp)
                    .width(44.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(t.hairline)
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Capture", style = MaterialTheme.typography.headlineSmall, color = t.textPrimary)
                    Text(
                        "Write it the way you'd say it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textSecondary
                    )
                }
                IconActionBtn(Icons.Filled.Close, "Close", tint = t.textSecondary, onClick = onDismiss)
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Gym tomorrow 7am every weekday ~45m !high #health") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(t.radiusControl.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = t.accentDeep,
                    unfocusedBorderColor = t.hairline,
                    cursorColor = t.accentDeep,
                    focusedTextColor = t.textPrimary,
                    unfocusedTextColor = t.textPrimary
                )
            )

            // ---- what the parser understood --------------------------------
            if (parsed.chips.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    parsed.chips.forEach { chip -> ParseChip(chip.kind, chip.label) }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("FILE IT AS", style = MaterialTheme.typography.labelSmall, color = t.textTertiary)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CaptureKind.entries.forEach { kind ->
                    val selected = kind == parsed.kind
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) t.chrome else t.cardSunken)
                            .pressable { kindOverride = kind }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            Nlp.kindLabel(kind),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) t.chromeText else t.textSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SunkenBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = t.accentDeep,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Nlp.summary(parsed),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textSecondary
                    )
                }
            }

            if (text.isBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("TRY", style = MaterialTheme.typography.labelSmall, color = t.textTertiary)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "Call the bank friday 10am",
                    "Practice modes every weekday ~30m",
                    "buy audio interface \$149 !high",
                    "note: E-shape run works better from the 5th string"
                ).forEach { example ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .pressable { text = example }
                            .padding(vertical = 6.dp, horizontal = 8.dp)
                    ) {
                        Text(
                            example,
                            style = MaterialTheme.typography.bodySmall,
                            color = t.textSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                GhostButton("Cancel", onDismiss)
                PrimaryButton("Save", { save() }, enabled = !parsed.isEmpty)
            }
        }
    }
}

/** One parsed-attribute chip, coloured by what kind of thing was recognised. */
@Composable
private fun ParseChip(kind: String, label: String) {
    val t = LocalTokens.current
    val tint = when (kind) {
        "date", "time" -> Blue
        "repeat" -> Violet
        "priority" -> Coral
        "estimate" -> t.accentDeep
        "price" -> Green
        "reminder" -> t.accentDeep
        "kind" -> t.textSecondary
        else -> t.textSecondary
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = if (t.dark) 0.20f else 0.13f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
