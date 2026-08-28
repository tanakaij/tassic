package tassic.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tassic.ui.theme.LocalReduceMotion
import tassic.ui.theme.LocalTokens
import tassic.ui.theme.accentGradient

/**
 * Shared "premium" surface vocabulary.
 *
 * The original UI was one card style (solid white, 2dp elevation) repeated
 * everywhere, which flattens hierarchy — a hero stat and a checkbox row carried
 * identical visual weight. These give the layout a top-to-bottom order: hero,
 * glass, plain, sunken.
 */

// ------------------------------------------------------------------ press feel

/**
 * Adds a subtle press-scale to any surface.
 *
 * A tap on a card should feel like it depresses. Honours the user's
 * reduce-motion setting rather than forcing the animation on everyone.
 */
@Composable
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "pressScale"
    )
    return this
        .scale(scale)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

// ------------------------------------------------------------------- surfaces

/**
 * Frosted card that lets the ambient wallpaper read through it.
 *
 * Compose on Wasm has no cheap runtime blur, so the "frost" is a translucent
 * fill plus a light top-edge border — which is what actually sells the effect
 * over a moving gradient anyway, and costs nothing per frame.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Int = 16,
    content: @Composable ColumnScope.() -> Unit
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.radiusCard.dp)
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(t.glass, shape)
        .border(1.dp, t.glassBorder, shape)
    Column(modifier = if (onClick != null) base.pressable(onClick = onClick) else base) {
        Column(Modifier.padding(contentPadding.dp), content = content)
    }
}

/**
 * The top-of-screen hero: accent gradient, dark ink, reserved for one element
 * per screen so it keeps its weight.
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.radiusCard.dp)
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(accentGradient(t), shape)
    Column(
        modifier = if (onClick != null) base.pressable(onClick = onClick) else base
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

/** Deep navy panel used for the momentum/insight headline blocks. */
@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.radiusCard.dp)
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(
            Brush.linearGradient(listOf(t.chrome, t.chrome.copy(alpha = 0.88f))),
            shape
        )
    Column(
        modifier = if (onClick != null) base.pressable(onClick = onClick) else base
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

/** Recessed well for nested content inside a card. */
@Composable
fun SunkenBox(
    modifier: Modifier = Modifier,
    padding: Int = 12,
    content: @Composable ColumnScope.() -> Unit
) {
    val t = LocalTokens.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(t.radiusControl.dp))
            .background(t.cardSunken)
            .padding(padding.dp),
        content = content
    )
}

// --------------------------------------------------------------- section head

/**
 * Screen-level section title with an all-caps eyebrow.
 *
 * The eyebrow is what makes a stack of cards scan as a structured document
 * rather than an undifferentiated list.
 */
@Composable
fun SectionTitle(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val t = LocalTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = t.accentDeep
            )
            Spacer(Modifier.height(2.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = t.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = t.textSecondary)
            }
        }
        trailing()
    }
}

// ------------------------------------------------------------------ segmented

/**
 * Sliding segmented control.
 *
 * Replaces the row of Material FilterChips used as a view switcher: chips read
 * as multi-select filters, whereas this reads as one-of-N, which is what the
 * tabs actually are.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: String,
    modifier: Modifier = Modifier,
    badge: (String) -> String? = { null },
    onSelect: (String) -> Unit
) {
    val t = LocalTokens.current
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(t.radiusChip.dp))
                .background(t.cardSunken)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            options.forEach { opt ->
                val isSel = opt == selected
                val bg by animateFloatAsState(
                    targetValue = if (isSel) 1f else 0f,
                    animationSpec = tween(180),
                    label = "segFill"
                )
                Row(
                    Modifier
                        .clip(RoundedCornerShape(t.radiusChip.dp))
                        .background(
                            if (isSel) t.chrome.copy(alpha = bg) else Color.Transparent,
                            RoundedCornerShape(t.radiusChip.dp)
                        )
                        .pressable { onSelect(opt) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        opt,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSel) t.chromeText else t.textSecondary
                    )
                    val b = badge(opt)
                    if (b != null) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(t.radiusChip.dp))
                                .background(
                                    if (isSel) t.accent.copy(alpha = 0.9f)
                                    else t.chip
                                )
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                b,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSel) t.onAccent else t.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------- animated stats

/** Integer that counts up to its target rather than snapping — cheap polish. */
@Composable
fun AnimatedNumber(
    value: Int,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.displaySmall,
    color: Color = LocalTokens.current.textPrimary,
    suffix: String = "",
    modifier: Modifier = Modifier
) {
    val reduceMotion = LocalReduceMotion.current
    val shown by animateIntAsState(
        targetValue = value,
        animationSpec = tween(if (reduceMotion) 0 else 700),
        label = "animNumber"
    )
    Text("$shown$suffix", style = style, color = color, modifier = modifier)
}

/**
 * Compact metric tile. Optional [delta] renders a signed change with the right
 * colour, which is the difference between a number and a trend.
 */
@Composable
fun MetricTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalTokens.current.accentDeep,
    delta: Int? = null,
    icon: ImageVector? = null
) {
    val t = LocalTokens.current
    Column(
        modifier
            .clip(RoundedCornerShape(t.radiusControl.dp))
            .background(tint.copy(alpha = if (t.dark) 0.18f else 0.11f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(4.dp))
        }
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = tint,
            textAlign = TextAlign.Center
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = t.textSecondary,
            textAlign = TextAlign.Center
        )
        if (delta != null && delta != 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                (if (delta > 0) "+" else "") + delta,
                style = MaterialTheme.typography.labelSmall,
                color = if (delta > 0) tassic.ui.theme.Green else tassic.ui.theme.Coral
            )
        }
    }
}

/** Status dot + label, used for capability readouts in Settings. */
@Composable
fun StatusRow(
    label: String,
    ok: Boolean,
    detail: String? = null,
    modifier: Modifier = Modifier
) {
    val t = LocalTokens.current
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (ok) tassic.ui.theme.Green else tassic.ui.theme.Coral)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = t.textPrimary)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = t.textSecondary)
            }
        }
        Text(
            if (ok) "Ready" else "No",
            style = MaterialTheme.typography.labelSmall,
            color = if (ok) tassic.ui.theme.Green else t.textSecondary
        )
    }
}

/** Thin gradient hairline — softer than a flat Divider against the ambient backdrop. */
@Composable
fun SoftDivider(modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, t.hairline, Color.Transparent)
                )
            )
    )
}
