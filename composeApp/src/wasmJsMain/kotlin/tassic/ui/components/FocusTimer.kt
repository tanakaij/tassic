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
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tassic.data.Graph
import tassic.data.T
import tassic.platform.chime
import tassic.platform.vibratePattern
import tassic.ui.theme.Green
import tassic.ui.theme.LocalTokens

/**
 * Focus sessions.
 *
 * A tracker records what you finished; it has nothing to say about the hour you
 * spent finishing it. Timed blocks close that gap, and because each completed
 * session writes to the activity log with its length as the magnitude, "when do
 * I actually get work done" becomes answerable from real data instead of
 * memory.
 *
 * The running session's start time is parked in app meta, so closing the sheet,
 * switching tabs or reloading the page picks the same session back up rather
 * than silently losing it — the failing that makes most in-page timers useless.
 */
@Composable
fun FocusSheet(onDismiss: () -> Unit, initialLabel: String = "") {
    val store = Graph.store
    val settings by store.settingsState.collectAsState()
    val t = LocalTokens.current
    val feedback = rememberFeedback()

    var label by rememberState(
        initialLabel.ifBlank { store.metaGet("focus.label") ?: "" }
    )
    var targetMinutes by rememberState(
        store.metaGet("focus.target")?.toIntOrNull() ?: settings.focusMinutes
    )
    var startedAt by rememberState(store.metaGet("focus.startedAt")?.toLongOrNull())
    var pausedElapsed by rememberState(store.metaGet("focus.elapsed")?.toIntOrNull() ?: 0)
    var elapsedSeconds by rememberState(0)
    var finished by rememberState(false)
    // `focusBreakMinutes` was configurable from the first version of this
    // sheet and read by nothing, so the setting was decoration. A focus timer
    // without a break is half a pomodoro.
    var onBreak by rememberState(store.metaGet("focus.phase") == "break")

    val running = startedAt != null
    val phaseMinutes = if (onBreak) settings.focusBreakMinutes else targetMinutes

    // One second tick while running. Elapsed is always recomputed from the
    // stored start timestamp rather than incremented, so a throttled background
    // tab can't make the timer drift.
    LaunchedEffect(startedAt) {
        while (startedAt != null) {
            val start = startedAt ?: break
            elapsedSeconds = pausedElapsed + ((T.now() - start) / 1000L).toInt()
            if (elapsedSeconds >= phaseMinutes * 60 && !finished) {
                finished = true
                if (settings.focusAlertOn) {
                    chime()
                    vibratePattern()
                }
            }
            delay(1000)
        }
    }
    LaunchedEffect(Unit) {
        if (startedAt == null) elapsedSeconds = pausedElapsed
    }

    fun clearMeta() {
        store.metaClear("focus.phase")
        store.metaClear("focus.startedAt")
        store.metaClear("focus.elapsed")
        store.metaClear("focus.target")
        store.metaClear("focus.label")
    }

    fun start() {
        val now = T.now()
        startedAt = now
        finished = false
        store.metaSet("focus.startedAt", now.toString())
        store.metaSet("focus.elapsed", pausedElapsed.toString())
        store.metaSet("focus.target", targetMinutes.toString())
        store.metaSet("focus.label", label)
    }

    fun pause() {
        pausedElapsed = elapsedSeconds
        startedAt = null
        store.metaClear("focus.startedAt")
        store.metaSet("focus.elapsed", pausedElapsed.toString())
    }

    /**
     * Ends the work block, logs it, and rolls straight into the break.
     *
     * The break is deliberately not logged as focus time — it isn't, and
     * inflating the number would make the hour-of-day analysis in Insights
     * describe rest as work.
     */
    fun startBreak() {
        val minutes = elapsedSeconds / 60
        if (minutes >= 1) store.logFocusSession(minutes, label.ifBlank { "Focus session" })
        onBreak = true
        pausedElapsed = 0
        elapsedSeconds = 0
        finished = false
        val now = T.now()
        startedAt = now
        store.metaSet("focus.phase", "break")
        store.metaSet("focus.startedAt", now.toString())
        store.metaSet("focus.elapsed", "0")
        store.metaSet("focus.target", settings.focusBreakMinutes.toString())
        feedback.confirm("$minutes min logged \u00b7 break started")
    }

    fun finish() {
        val minutes = if (onBreak) 0 else elapsedSeconds / 60
        if (minutes >= 1) {
            store.logFocusSession(minutes, label.ifBlank { "Focus session" })
            feedback.confirm("$minutes min logged")
        } else {
            feedback.say("Session under a minute — nothing logged")
        }
        clearMeta()
        onDismiss()
    }

    val progress = (elapsedSeconds.toFloat() / (phaseMinutes * 60).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val remaining = (phaseMinutes * 60 - elapsedSeconds).coerceAtLeast(0)

    TassicSheet(title = if (onBreak) "Break" else "Focus session", onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ProgressRing(
                progress = progress,
                diameter = 168,
                thickness = 12,
                color = if (finished || onBreak) Green else t.accent
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        clock(remaining),
                        style = MaterialTheme.typography.displaySmall,
                        color = t.textPrimary
                    )
                    Text(
                        when {
                            finished && onBreak -> "Break over"
                            finished -> "Session complete"
                            onBreak -> "on a break"
                            running -> "remaining"
                            else -> "ready"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = t.textSecondary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (!running && elapsedSeconds == 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 25, 45, 60).forEach { minutes ->
                        val selected = minutes == targetMinutes
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (selected) t.chrome else t.cardSunken)
                                .pressable { targetMinutes = minutes }
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                        ) {
                            Text(
                                "${minutes}m",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) t.chromeText else t.textSecondary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            LabeledField(label, { label = it }, "What are you working on?", placeholder = "Optional")

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (running) {
                    RoundAction(Icons.Filled.Pause, "Pause") { pause() }
                } else {
                    RoundAction(Icons.Filled.PlayArrow, if (elapsedSeconds > 0) "Resume" else "Start") { start() }
                }
                // Once there's a full minute of work behind you, the break
                // becomes the primary way out — that's the whole point of the
                // setting, and burying it behind "finish" would waste it.
                if (!onBreak && elapsedSeconds >= 60 && settings.focusBreakMinutes > 0) {
                    RoundAction(Icons.Filled.FreeBreakfast, "Take a break", tintAccent = false) { startBreak() }
                }
                if (elapsedSeconds > 0 || onBreak) {
                    RoundAction(Icons.Filled.Stop, if (onBreak) "Done" else "Finish and log", tintAccent = false) {
                        finish()
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                if (onBreak) {
                    "Break time isn't logged as focus \u2014 it isn't, and counting it would make the hour-of-day analysis describe rest as work."
                } else if (elapsedSeconds > 0) {
                    "${elapsedSeconds / 60} min counted so far · logged only when you finish."
                } else {
                    "Time is written to your log when the session ends, and feeds the hour-of-day analysis in Insights."
                },
                style = MaterialTheme.typography.bodySmall,
                color = t.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tintAccent: Boolean = true,
    onClick: () -> Unit
) {
    val t = LocalTokens.current
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (tintAccent) t.accent else t.cardSunken)
            .pressable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (tintAccent) t.onAccent else t.textPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (tintAccent) t.onAccent else t.textPrimary
        )
    }
}

private fun clock(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}
