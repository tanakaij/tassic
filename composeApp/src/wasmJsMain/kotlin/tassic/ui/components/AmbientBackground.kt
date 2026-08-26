package tassic.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tassic.ui.theme.Amber
import tassic.ui.theme.Coral
import tassic.ui.theme.NavySoft
import tassic.ui.theme.SkyBlue
import tassic.ui.theme.SkyDeep
import tassic.ui.theme.SkySoft

/**
 * Soft, slowly-drifting blurred colour blobs behind the app content — an
 * "ambient gradient" wallpaper feel similar to Apple Music / iOS lock-screen
 * backgrounds, built entirely from the existing brand palette so it stays
 * on-brand rather than looking like a generic stock gradient.
 *
 * Sits behind a transparent Scaffold + header; individual cards remain
 * solid white on top, so readability is unaffected.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(26_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientDrift"
    )

    Box(modifier.fillMaxSize().background(SkyBlue)) {
        Blob(SkySoft.copy(alpha = 0.95f), 380.dp, Alignment.Center, drift, dx = 18, dy = -16)
        Blob(Amber.copy(alpha = 0.50f), 260.dp, Alignment.TopStart, drift, dx = 34, dy = -18)
        Blob(Coral.copy(alpha = 0.32f), 300.dp, Alignment.TopEnd, drift, dx = -28, dy = 22)
        Blob(SkyDeep.copy(alpha = 0.65f), 340.dp, Alignment.BottomStart, drift, dx = 18, dy = 26)
        Blob(NavySoft.copy(alpha = 0.22f), 260.dp, Alignment.BottomEnd, drift, dx = -20, dy = -24)
    }
}

@Composable
private fun BoxScope.Blob(
    color: Color,
    diameter: Dp,
    align: Alignment,
    driftT: Float,
    dx: Int,
    dy: Int
) {
    Box(
        Modifier
            .align(align)
            .offset(x = (dx * driftT).dp, y = (dy * driftT).dp)
            .size(diameter)
            .blur(90.dp)
            .background(color, CircleShape)
    )
}
