package tassic.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Theme-aware replacements for the fixed brand colours.
 *
 * `Navy`, `Ink`, `Muted` and `SkySoft` are constants — #0F2B4C, #123252,
 * #5B7A99, #D8EAF6 — chosen when the app only had a light theme. Five screens
 * still used them for body text after dark mode arrived, which meant Faith,
 * Journal, Life, Music and Today rendered near-black text on a near-black
 * background: legible in the light theme, unreadable in the dark one, and
 * completely unresponsive to the accent picker.
 *
 * These are composable property getters rather than plain constants, so they
 * read the active [LocalTokens] at the point of use. That makes the fix a
 * one-for-one substitution at each call site instead of a rewrite that would
 * need `LocalTokens.current` threaded into a few dozen private composables.
 *
 * The constants themselves stay where they are. They are still correct as
 * *brand* colours — the navy in the logo, the sky in the light canvas — and the
 * theme is built out of them. What was wrong was using a fixed brand colour
 * where a semantic role (primary text, secondary text, sunken surface) was
 * meant.
 */

/** Primary body and heading text. Replaces literal `Navy` / `Ink` on text. */
val textInk: Color
    @Composable get() = LocalTokens.current.textPrimary

/** Secondary and caption text. Replaces literal `Muted`. */
val textMuted: Color
    @Composable get() = LocalTokens.current.textSecondary

/** The faint pill and inset background. Replaces literal `SkySoft`. */
val surfaceSoft: Color
    @Composable get() = LocalTokens.current.cardSunken

/** Hairline rules and dividers drawn as filled shapes. */
val ruleSoft: Color
    @Composable get() = LocalTokens.current.hairline
