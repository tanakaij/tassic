package tassic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tassic.data.Graph
import tassic.data.Lock
import tassic.platform.vibrate
import tassic.ui.theme.Coral
import tassic.ui.theme.LocalTokens

/**
 * The privacy screen over the app's most personal sections.
 *
 * The realistic threat here is a person in the room, not an attacker: someone
 * picks up an unlocked phone and the recovery relapse log is one tap away. A
 * PIN on Journal, Recovery and People — rather than on the whole app, which
 * would add friction to every single use and get switched off within a week —
 * addresses that, and the copy is careful to promise exactly that and no more.
 */
@Composable
fun LockGate(section: String, content: @Composable () -> Unit) {
    val store = Graph.store
    val settings by store.settingsState.collectAsState()

    var unlockedTick by rememberState(0)
    val locked = remember(settings, unlockedTick) { Lock.isLocked(settings, section) }

    if (!locked) {
        content()
        return
    }

    PinPad(
        title = "Locked",
        subtitle = "Enter your PIN to open ${section.lowercase()}.",
        onSubmit = { pin ->
            if (Lock.verify(pin, settings)) {
                Lock.unlock(settings.lockGraceMinutes)
                unlockedTick += 1
                true
            } else {
                false
            }
        }
    )
}

/**
 * The keypad.
 *
 * A numeric pad rather than a text field: it's faster one-handed, it can't
 * summon a keyboard that covers the screen, and it makes the four-digit
 * expectation obvious without a label saying so.
 */
@Composable
fun PinPad(
    title: String,
    subtitle: String,
    confirmLabel: String = "",
    onSubmit: (String) -> Boolean
) {
    val t = LocalTokens.current
    var pin by rememberState("")
    var error by rememberState(false)

    fun press(digit: String) {
        if (pin.length >= 6) return
        error = false
        pin += digit
        if (pin.length == 4) {
            if (onSubmit(pin)) {
                pin = ""
            } else {
                error = true
                vibrate(60)
                pin = ""
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(t.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = t.accentDeep, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = t.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            if (error) "That PIN didn't match." else subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = if (error) Coral else t.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            (0 until 4).forEach { index ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (index < pin.length) t.accentDeep else t.hairline)
                )
            }
        }
        if (confirmLabel.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(confirmLabel, style = MaterialTheme.typography.labelSmall, color = t.textTertiary)
        }

        Spacer(Modifier.height(26.dp))
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "<")
        ).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (key.isEmpty()) androidx.compose.ui.graphics.Color.Transparent else t.cardSunken)
                            .then(
                                if (key.isEmpty()) {
                                    Modifier
                                } else {
                                    Modifier.pressable {
                                        if (key == "<") {
                                            pin = pin.dropLast(1)
                                            error = false
                                        } else {
                                            press(key)
                                        }
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (key) {
                            "" -> Unit
                            "<" -> Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Delete",
                                tint = t.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            else -> Text(key, style = MaterialTheme.typography.headlineSmall, color = t.textPrimary)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "The PIN keeps this section off the screen. It is not encryption — " +
                "your entries are still readable to anyone with access to this browser's storage.",
            style = MaterialTheme.typography.bodySmall,
            color = t.textTertiary,
            textAlign = TextAlign.Center
        )
    }
}
