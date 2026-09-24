package tassic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import tassic.data.Horizon
import tassic.data.Priority
import tassic.data.Severity

/*
 * Palette extracted from ui_template.jpeg (travel-app UI kit):
 * sky-blue canvas, white rounded cards, deep navy chrome,
 * amber CTA accents, blue secondary actions, coral danger.
 */
val SkyBlue = Color(0xFFBBD8EC)
val SkyDeep = Color(0xFFA9CDE8)
val SkySoft = Color(0xFFD8EAF6)
val SkyMist = Color(0xFFEDF5FB)
val Navy = Color(0xFF0F2B4C)
val NavySoft = Color(0xFF1D3E63)
val NavyDeep = Color(0xFF08192E)
val Blue = Color(0xFF1E88C7)
val BlueBright = Color(0xFF2D9CDB)
val Amber = Color(0xFFF7C948)
val AmberDeep = Color(0xFFE0A92E)
val Coral = Color(0xFFF25767)
val Green = Color(0xFF2FB380)
val Orange = Color(0xFFF2994A)
val Violet = Color(0xFF7C6BD6)
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

/**
 * Semantic surface tokens.
 *
 * The old theme referenced raw palette constants (`CardWhite`, `Navy`, `SkySoft`)
 * directly from every component, which meant dark mode only worked where someone
 * had remembered to branch on it — cards stayed pure white on a navy canvas, and
 * body copy stayed near-black. Routing everything through one token object means
 * a component asks for "the card colour" and gets the right one for the active
 * scheme, every time.
 */
@Immutable
data class TassicTokens(
    val dark: Boolean,
    /** Solid card fill. */
    val card: Color,
    /** Slightly recessed fill for nested rows and wells. */
    val cardSunken: Color,
    /** Translucent fill for the frosted "glass" cards on the ambient backdrop. */
    val glass: Color,
    val glassBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** Hairline dividers and card outlines. */
    val hairline: Color,
    /** Neutral chip/pill background. */
    val chip: Color,
    val chipText: Color,
    /** Chrome behind the top bar and bottom nav. */
    val chrome: Color,
    val chromeText: Color,
    val accent: Color,
    val accentDeep: Color,
    val onAccent: Color,
    val canvasTop: Color,
    val canvasMid: Color,
    val canvasBottom: Color,
    /** Corner radii, so rounding is consistent rather than re-guessed per file. */
    val radiusCard: Int = 20,
    val radiusControl: Int = 14,
    val radiusChip: Int = 999
)

val LocalTokens = staticCompositionLocalOf { lightTokens(Amber, AmberDeep, Navy) }

/** True when the app is rendering its dark scheme (respects the manual override). */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** User-selected motion preference, honoured by the ambient backdrop and press effects. */
val LocalReduceMotion = staticCompositionLocalOf { false }

fun accentFor(name: String): Triple<Color, Color, Color> = when (name.lowercase()) {
    "blue" -> Triple(BlueBright, Blue, Color.White)
    "green" -> Triple(Green, Color(0xFF23916A), Color.White)
    "coral" -> Triple(Coral, Color(0xFFD23B4B), Color.White)
    "violet" -> Triple(Violet, Color(0xFF5F4FB5), Color.White)
    else -> Triple(Amber, AmberDeep, Navy)
}

fun severityColor(s: Severity): Color = when (s) {
    Severity.CRITICAL -> Coral
    Severity.WARNING -> Orange
    Severity.INFO -> Blue
    Severity.POSITIVE -> Green
}

private fun lightTokens(accent: Color, accentDeep: Color, onAccent: Color) = TassicTokens(
    dark = false,
    card = CardWhite,
    cardSunken = SkyMist,
    glass = Color.White.copy(alpha = 0.78f),
    glassBorder = Color.White.copy(alpha = 0.65f),
    textPrimary = Ink,
    textSecondary = Muted,
    textTertiary = Color(0xFF89A6BF),
    hairline = Color(0xFFD7E6F1),
    chip = SkySoft,
    chipText = Navy,
    chrome = Navy,
    chromeText = Color.White,
    accent = accent,
    accentDeep = accentDeep,
    onAccent = onAccent,
    canvasTop = SkySoft,
    canvasMid = SkyBlue,
    canvasBottom = SkyDeep
)

private fun darkTokens(accent: Color, accentDeep: Color, onAccent: Color) = TassicTokens(
    dark = true,
    card = Color(0xFF15304F),
    cardSunken = Color(0xFF0F2440),
    glass = Color(0xFF15304F).copy(alpha = 0.82f),
    glassBorder = Color.White.copy(alpha = 0.08f),
    textPrimary = Color(0xFFE7F1F9),
    textSecondary = Color(0xFF9FBBD4),
    textTertiary = Color(0xFF7492AE),
    hairline = Color(0xFF27496E),
    chip = Color(0xFF20415F),
    chipText = Color(0xFFCFE3F3),
    chrome = NavyDeep,
    chromeText = Color.White,
    accent = accent,
    accentDeep = accentDeep,
    onAccent = onAccent,
    canvasTop = Navy,
    canvasMid = NavySoft,
    canvasBottom = NavyDeep
)

/** Gradient used on hero surfaces and the primary CTA. */
fun accentGradient(t: TassicTokens): Brush =
    Brush.horizontalGradient(listOf(t.accent, t.accentDeep))

private fun lightScheme(accent: Color, onAccent: Color): ColorScheme = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = SkySoft,
    onPrimaryContainer = Navy,
    secondary = accent,
    onSecondary = onAccent,
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
    outlineVariant = Color(0xFFC9DDEB),
    scrim = Navy.copy(alpha = 0.32f)
)

private fun darkScheme(accent: Color, onAccent: Color): ColorScheme = darkColorScheme(
    primary = BlueBright,
    onPrimary = Color.White,
    primaryContainer = NavySoft,
    onPrimaryContainer = SkySoft,
    secondary = accent,
    onSecondary = onAccent,
    secondaryContainer = Color(0xFF4A3B12),
    onSecondaryContainer = Color(0xFFFBE9B8),
    tertiary = Green,
    onTertiary = Color.White,
    background = Navy,
    onBackground = Color(0xFFE7F1F9),
    surface = Color(0xFF15304F),
    onSurface = Color(0xFFE7F1F9),
    surfaceVariant = Color(0xFF20415F),
    onSurfaceVariant = Color(0xFF9FBBD4),
    error = Coral,
    onError = Color.White,
    errorContainer = Color(0xFF5C2029),
    onErrorContainer = Color(0xFFFDE0E3),
    outline = Color(0xFF5B7A99),
    outlineVariant = Color(0xFF2E4F72),
    scrim = Color.Black.copy(alpha = 0.5f)
)

/**
 * Serif display type mirroring the template's elegant headings.
 *
 * Tightened tracking on the display sizes and opened it up on the small
 * all-caps labels — the single cheapest change that separates a considered
 * interface from a default one.
 */
private fun tassicTypography(): Typography {
    val serif = FontFamily.Serif
    val sans = FontFamily.SansSerif
    return Typography(
        displayLarge = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.Bold,
            fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.8).sp
        ),
        displayMedium = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.Bold,
            fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.6).sp
        ),
        displaySmall = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.Bold,
            fontSize = 29.sp, lineHeight = 35.sp, letterSpacing = (-0.4).sp
        ),
        headlineLarge = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.Bold,
            fontSize = 27.sp, lineHeight = 33.sp, letterSpacing = (-0.3).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.Bold,
            fontSize = 23.sp, lineHeight = 29.sp, letterSpacing = (-0.2).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.Bold,
            fontSize = 19.sp, lineHeight = 25.sp, letterSpacing = (-0.1).sp
        ),
        titleLarge = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.Bold,
            fontSize = 18.sp, lineHeight = 24.sp
        ),
        titleMedium = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.SemiBold,
            fontSize = 15.5.sp, lineHeight = 21.sp, letterSpacing = (-0.1).sp
        ),
        titleSmall = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp, lineHeight = 19.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.Normal,
            fontSize = 15.sp, lineHeight = 22.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.Normal,
            fontSize = 13.5.sp, lineHeight = 20.sp
        ),
        bodySmall = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.Normal,
            fontSize = 12.sp, lineHeight = 17.sp
        ),
        labelLarge = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
        ),
        labelSmall = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp
        )
    )
}

/**
 * Theme root.
 *
 * [themeMode] follows the device (`prefers-color-scheme` in the browser, the OS
 * theme in an installed shell) unless the user has explicitly forced light or
 * dark in Settings.
 */
@Composable
fun TassicTheme(
    themeMode: String = "system",
    accentName: String = "amber",
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode.lowercase()) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val (accent, accentDeep, onAccent) = accentFor(accentName)
    val tokens = if (dark) darkTokens(accent, accentDeep, onAccent) else lightTokens(accent, accentDeep, onAccent)

    CompositionLocalProvider(
        LocalTokens provides tokens,
        LocalDarkTheme provides dark,
        LocalReduceMotion provides reduceMotion
    ) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme(accent, onAccent) else lightScheme(accent, onAccent),
            typography = tassicTypography(),
            content = content
        )
    }
}
