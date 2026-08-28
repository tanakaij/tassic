package tassic.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import tassic.ui.theme.LocalReduceMotion
import tassic.ui.theme.LocalTokens

/**
 * Lightweight chart primitives drawn straight onto a Compose [Canvas].
 *
 * Deliberately dependency-free: the app targets Kotlin/Wasm and ships as a
 * static bundle on GitHub Pages, so pulling a charting library in for four
 * shapes would cost more download than the whole feature is worth.
 */

// ------------------------------------------------------------------ ring

/**
 * Circular progress ring with an optional centre slot.
 *
 * Used for the momentum score — a single number that deserves to look like the
 * most important thing on the screen.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    diameter: Int = 120,
    thickness: Int = 10,
    color: Color = LocalTokens.current.accent,
    trackColor: Color = LocalTokens.current.hairline,
    center: @Composable () -> Unit = {}
) {
    val reduceMotion = LocalReduceMotion.current
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(if (reduceMotion) 0 else 900),
        label = "ring"
    )

    Box(modifier.size(diameter.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter.dp)) {
            val stroke = thickness.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )

            if (animated > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(color.copy(alpha = 0.55f), color, color)
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
        }
        center()
    }
}

// -------------------------------------------------------------- sparkline

/**
 * Filled sparkline over normalised 0..1 values, oldest first.
 *
 * Smoothed with quadratic segments through midpoints, which reads far better
 * than a polyline at this size without needing a real spline implementation.
 */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: Int = 56,
    color: Color = LocalTokens.current.accentDeep,
    showFill: Boolean = true
) {
    if (values.size < 2) {
        Box(modifier.fillMaxWidth().height(height.dp))
        return
    }
    val reduceMotion = LocalReduceMotion.current
    val reveal by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(if (reduceMotion) 0 else 800),
        label = "spark"
    )

    Canvas(modifier.fillMaxWidth().height(height.dp)) {
        val w = size.width
        val h = size.height
        val pad = 3f
        val stepX = w / (values.size - 1).toFloat()

        fun px(i: Int) = i * stepX
        fun py(v: Float) = h - pad - (v.coerceIn(0f, 1f) * (h - pad * 2))

        // A polyline through every point, plus one interpolated midpoint per
        // segment. Cheaper and more portable than a real spline, and at this
        // size the eye reads it as a curve anyway.
        val line = Path()
        line.moveTo(px(0), py(values[0]))
        for (i in 1 until values.size) {
            val midX = (px(i - 1) + px(i)) / 2f
            val midY = (py(values[i - 1]) + py(values[i])) / 2f
            line.lineTo(midX, midY)
            line.lineTo(px(i), py(values[i]))
        }

        if (showFill) {
            val fill = Path()
            fill.addPath(line)
            fill.lineTo(w, h)
            fill.lineTo(0f, h)
            fill.close()
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))
                )
            )
        }

        drawPath(
            path = line,
            color = color.copy(alpha = reveal),
            style = Stroke(width = 2.5f.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )

        // Terminal dot so the "now" end of the series is unambiguous.
        drawCircle(
            color = color,
            radius = 3.5f.dp.toPx(),
            center = Offset(px(values.size - 1), py(values.last()))
        )
    }
}

// ------------------------------------------------------------------ bars

/** Simple labelled bar row — used for domain scores. */
@Composable
fun BarRow(
    label: String,
    value: Int,
    max: Int = 100,
    caption: String? = null,
    delta: Int? = null,
    color: Color = LocalTokens.current.accentDeep,
    modifier: Modifier = Modifier
) {
    val t = LocalTokens.current
    val reduceMotion = LocalReduceMotion.current
    val fraction by animateFloatAsState(
        targetValue = if (max <= 0) 0f else (value.toFloat() / max).coerceIn(0f, 1f),
        animationSpec = tween(if (reduceMotion) 0 else 700),
        label = "bar"
    )

    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = t.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (delta != null && delta != 0) {
                Text(
                    (if (delta > 0) "+" else "") + delta,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (delta > 0) tassic.ui.theme.Green else tassic.ui.theme.Coral
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                "$value%",
                style = MaterialTheme.typography.labelLarge,
                color = color
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(t.hairline)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color)))
            )
        }
        if (caption != null) {
            Spacer(Modifier.height(4.dp))
            Text(caption, style = MaterialTheme.typography.bodySmall, color = t.textSecondary)
        }
    }
}

// --------------------------------------------------------------- heatmap

/**
 * Contribution-graph style heatmap of the last N days, oldest first.
 *
 * Four intensity steps rather than a continuous ramp — discrete buckets are far
 * easier to read at this cell size than a smooth gradient.
 */
@Composable
fun ActivityHeatmap(
    counts: List<Int>,
    modifier: Modifier = Modifier,
    columns: Int = 7,
    cell: Int = 26,
    color: Color = LocalTokens.current.accentDeep
) {
    val t = LocalTokens.current
    if (counts.isEmpty()) return
    val peak = (counts.maxOrNull() ?: 0).coerceAtLeast(1)

    fun alphaFor(n: Int): Float = when {
        n <= 0 -> 0f
        n <= peak * 0.25 -> 0.28f
        n <= peak * 0.5 -> 0.5f
        n <= peak * 0.75 -> 0.75f
        else -> 1f
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        counts.chunked(columns).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { n ->
                    val a = alphaFor(n)
                    Box(
                        Modifier
                            .size(cell.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                if (a == 0f) t.hairline.copy(alpha = 0.55f)
                                else color.copy(alpha = a)
                            )
                    )
                }
                // Pad a short final row so the grid stays aligned.
                repeat(columns - week.size) {
                    Box(Modifier.size(cell.dp))
                }
            }
        }
    }
}

/** Legend strip for [ActivityHeatmap]. */
@Composable
fun HeatmapLegend(
    modifier: Modifier = Modifier,
    color: Color = LocalTokens.current.accentDeep
) {
    val t = LocalTokens.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("Less", style = MaterialTheme.typography.labelSmall, color = t.textTertiary)
        Spacer(Modifier.width(6.dp))
        listOf(0f, 0.28f, 0.5f, 0.75f, 1f).forEach { a ->
            Box(
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (a == 0f) t.hairline.copy(alpha = 0.55f) else color.copy(alpha = a))
            )
            Spacer(Modifier.width(3.dp))
        }
        Spacer(Modifier.width(3.dp))
        Text("More", style = MaterialTheme.typography.labelSmall, color = t.textTertiary)
    }
}
