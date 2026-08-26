package tassic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import tassic.data.Horizon
import tassic.data.Priority

/*
 * Palette extracted from ui_template.jpeg (travel-app UI kit):
 * sky-blue canvas, white rounded cards, deep navy chrome,
 * amber CTA accents, blue secondary actions, coral danger.
 */
val SkyBlue = Color(0xFFBBD8EC)
val SkyDeep = Color(0xFFA9CDE8)
val SkySoft = Color(0xFFD8EAF6)
val Navy = Color(0xFF0F2B4C)
val NavySoft = Color(0xFF1D3E63)
val Blue = Color(0xFF1E88C7)
val BlueBright = Color(0xFF2D9CDB)
val Amber = Color(0xFFF7C948)
val AmberDeep = Color(0xFFE0A92E)
val Coral = Color(0xFFF25767)
val Green = Color(0xFF2FB380)
val Orange = Color(0xFFF2994A)
val Ink = Color(0xFF123252)
val Muted = Color(0xFF5B7A99)
val CardWhite = Color(0xFFFFFFFF)

val MoodColors = listOf(Coral, Orange, Amber, Color(0xFF7BC86C), Green)

fun priorityColor(p: Priority): Color = when (p) {
    Priority.URGENT -> Coral
    Priority.HIGH -> Orange
    Priority.NORMAL -> Blue
    Priority.LOW -> Muted
}

fun horizonColor(h: Horizon): Color = when (h) {
    Horizon.SHORT -> Blue
    Horizon.MEDIUM -> AmberDeep
    Horizon.LONG -> Navy
}

private val LightColors: ColorScheme = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = SkySoft,
    onPrimaryContainer = Navy,
    secondary = Amber,
    onSecondary = Navy,
    secondaryContainer = Color(0xFFFBE9B8),
    onSecondaryContainer = Navy,
    tertiary = Green,
    onTertiary = Color.White,
    background = SkyBlue,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = SkySoft,
    onSurfaceVariant = Muted,
    error = Coral,
    onError = Color.White,
    errorContainer = Color(0xFFFDE0E3),
    onErrorContainer = Color(0xFF8C1D26),
    outline = Color(0xFF8FB0CB),
    outlineVariant = Color(0xFFC9DDEB)
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = BlueBright,
    onPrimary = Color.White,
    primaryContainer = NavySoft,
    onPrimaryContainer = SkySoft,
    secondary = Amber,
    onSecondary = Navy,
    secondaryContainer = Color(0xFF4A3B12),
    onSecondaryContainer = Color(0xFFFBE9B8),
    tertiary = Green,
    onTertiary = Color.White,
    background = Navy,
    onBackground = SkySoft,
    surface = NavySoft,
    onSurface = SkySoft,
    surfaceVariant = Color(0xFF27496E),
    onSurfaceVariant = Color(0xFFAFC7DE),
    error = Coral,
    onError = Color.White,
    errorContainer = Color(0xFF5C2029),
    onErrorContainer = Color(0xFFFDE0E3),
    outline = Color(0xFF5B7A99),
    outlineVariant = Color(0xFF2E4F72)
)

/** Serif display type mirroring the template's elegant headings. */
private fun tassicTypography(): Typography {
    val serif = FontFamily.Serif
    val sans = FontFamily.SansSerif
    return Typography(
        displaySmall = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
        headlineLarge = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
        headlineMedium = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
        headlineSmall = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
        titleLarge = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp),
        titleMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        titleSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
        labelLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
        labelMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, lineHeight = 14.sp)
    )
}

/**
 * Follows the device's system light/dark setting (`prefers-color-scheme` in
 * the browser / OS theme in the installed PWA shell), the same way an
 * OS-level widget picks up the surrounding theme automatically.
 */
@Composable
fun TassicTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = tassicTypography(),
        content = content
    )
}
