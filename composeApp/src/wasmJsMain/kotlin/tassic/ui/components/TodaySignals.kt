package tassic.ui.components

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tassic.data.Coach
import tassic.ui.theme.AmberDeep
import tassic.ui.theme.Coral
import tassic.ui.theme.LocalTokens

/**
 * The cross-domain strip under Today's hero.
 *
 * Today's hero already answers "how am I doing" — greeting, headline, momentum
 * ring, and the one thing to start with. What it could not answer was anything
 * outside tasks, training and practice, because the engine behind it predates
 * habits, the calendar, reading plans, people, week priorities and good deeds.
 * All of that lived on other tabs, so Today told you about a third of your day
 * with total confidence.
 *
 * This is the missing third, and it is deliberately the smallest possible
 * version of it. Not a second hero — a second hero is how a home screen becomes
 * a dashboard, which is impressive on the first morning and ignored by the
 * second. Three rows maximum, each one a fact with somewhere to go, and the
 * whole card disappears on a day when none of them are true. On most days it
 * will show one or two lines.
 */
@Composable
fun TodaySignals(
    signals: List<Coach.BriefSignal>,
    onOpenTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (signals.isEmpty()) return
    val t = LocalTokens.current

    GlassCard(modifier) {
        Column(Modifier.fillMaxWidth()) {
            signals.forEachIndexed { index, signal ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .pressable { onOpenTab(signal.tab) }
                        .padding(vertical = 9.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        signalIcon(signal.kind),
                        contentDescription = null,
                        tint = signalTint(signal.kind, t.textSecondary),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        signal.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = t.textTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (index != signals.lastIndex) {
                    Spacer(Modifier.height(1.dp))
                    SoftDivider()
                }
            }
        }
    }
}

private fun signalIcon(kind: String): ImageVector = when (kind) {
    "clock" -> Icons.Filled.Schedule
    "warn" -> Icons.Filled.WarningAmber
    "streak" -> Icons.Filled.LocalFireDepartment
    "habit" -> Icons.Filled.Repeat
    "book" -> Icons.AutoMirrored.Filled.MenuBook
    "cake" -> Icons.Filled.Cake
    "flag" -> Icons.Filled.Flag
    else -> Icons.Filled.Favorite
}

private fun signalTint(kind: String, fallback: Color): Color = when (kind) {
    "warn" -> Coral
    "streak", "cake" -> AmberDeep
    else -> fallback
}
