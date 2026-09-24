package tassic.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import tassic.ui.theme.Coral
import tassic.ui.theme.LocalReduceMotion
import tassic.ui.theme.LocalTokens
import tassic.ui.theme.NavySoft

/**
 * Soft, continuously-flowing colour wash behind the app content — a smooth
 * "ambient gradient" wallpaper feel similar to Apple Music's now-playing
 * background, built entirely from the existing brand palette so it stays
 * on-brand rather than looking like a generic stock gradient.
 *
 * Unlike a set of hard-edged blurred circles, every colour patch here is a
 * radial gradient that fades all the way to transparent, is drawn far
 * larger than its "core", and drifts along a slow, eased, circular path —
 * so patches melt into one another and into the base colour with no visible
 * seams, and the motion itself reads as smooth rather than a back-and-forth
 * bounce.
 *
 * Sits behind a transparent Scaffold + header; individual cards remain
 * solid white on top, so readability is unaffected.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    // Reads the resolved token set rather than the raw system setting, so a
    // user who forces light or dark in Settings gets a matching wallpaper
    // instead of one that follows the OS independently of the rest of the UI.
    val t = LocalTokens.current
    val reduceMotion = LocalReduceMotion.current
    val dark = t.dark
    val baseA = t.canvasTop
    val baseB = t.canvasMid
    val baseC = t.canvasBottom
    val canvasBase = t.canvasMid
    val warmBlob = t.canvasTop
    val accent = t.accent

    val transition = rememberInfiniteTransition(label = "ambient")

    // A single, slowly-advancing, linear "clock" driving every blob's
    // position via sin/cos. Because each blob reads a different phase and
    // period of the same clock (instead of its own reversing tween), the
    // whole scene flows continuously in one direction with no jarring
    // direction-change moment — the hallmark of the Apple Music look.
    val animatedClock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(60_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambientClock"
    )
    // Reduce-motion parks the wash at a fixed, pleasant phase rather than
    // removing it — the depth stays, the movement doesn't.
    val clock = if (reduceMotion) 0.18f else animatedClock

    Canvas(
        modifier
            .fillMaxSize()
            .background(canvasBase)
    ) {
        val w = size.width
        val h = size.height
        val diag = kotlin.math.hypot(w, h)

        // Base diagonal wash so even the "empty" canvas has a gentle tonal
        // shift instead of a flat fill — this is what keeps the whole thing
        // reading as one continuous gradient rather than blobs-on-a-colour.
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(baseA, baseB, baseC),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )

        fun orbit(cx: Float, cy: Float, radius: Float, phase: Float, speed: Float): Offset {
            val t = (clock * speed + phase) * 2f * PI.toFloat()
            return Offset(cx + cos(t) * radius, cy + sin(t) * radius)
        }

        softBlob(orbit(w * 0.30f, h * 0.28f, w * 0.16f, 0.00f, 1.0f), diag * 0.55f, warmBlob, 0.95f)
        softBlob(orbit(w * 0.82f, h * 0.18f, w * 0.14f, 0.33f, 0.8f), diag * 0.42f, accent, if (dark) 0.22f else 0.40f)
        softBlob(orbit(w * 0.88f, h * 0.75f, w * 0.18f, 0.60f, 1.2f), diag * 0.48f, Coral, if (dark) 0.16f else 0.28f)
        softBlob(orbit(w * 0.16f, h * 0.82f, w * 0.15f, 0.15f, 0.9f), diag * 0.50f, baseC, 0.55f)
        softBlob(orbit(w * 0.55f, h * 0.55f, w * 0.10f, 0.80f, 0.6f), diag * 0.38f, NavySoft, if (dark) 0.35f else 0.16f)
    }
}

/** Draws one colour patch as a radial gradient fading fully to transparent — no hard edge to blur away. */
internal fun DrawScope.softBlob(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
            center = center,
            radius = radius,
            tileMode = TileMode.Clamp
        )
    )
}
