package com.wyrd.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// DitherMono design system: two inks on one ground.
// Paper #FEFCF1, black #100F0F, soft grey #CECDC4. Every other value is ink at an opacity,
// pre-composited onto `surface` so overlays (drawer, dialogs) stay opaque.
// Dark mode sits on true AMOLED black; there ink 8% lands on the brand black #100F0F.
//   fill     = ink 8%   composer, selected row, icon buttons, cards
//   hairline = ink 16%  dividers, borders
//   muted    = ink 64%  secondary text, labels, placeholders
object DitherMono {
    val Paper = Color(0xFFFEFCF1)
    val Black = Color(0xFF100F0F)
    val Grey = Color(0xFFCECDC4)
    val Amoled = Color(0xFF000000)
}

// Color Palette Definition
@Immutable
data class WyrdColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val card: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textPlaceholder: Color,
    val primary: Color,
    val onPrimary: Color,
    val isDark: Boolean
)

val DarkWyrdColors = WyrdColors(
    background = DitherMono.Amoled,         // surface (true black)
    surface = DitherMono.Amoled,            // surface (drawer, dialogs: split by hairline)
    surfaceElevated = DitherMono.Black,     // fill (ink 8% on black = #100F0F)
    card = DitherMono.Black,                // fill
    border = Color(0xFF21211F),             // hairline
    textPrimary = DitherMono.Grey,          // ink
    textSecondary = Color(0xFF84837D),      // ink-muted
    textMuted = Color(0xFF84837D),          // ink-muted
    textPlaceholder = Color(0xFF84837D),    // ink-muted
    primary = DitherMono.Grey,              // ink (send button)
    onPrimary = DitherMono.Amoled,          // surface on ink
    isDark = true
)

val LightWyrdColors = WyrdColors(
    background = DitherMono.Paper,          // surface
    surface = DitherMono.Paper,             // surface (drawer, dialogs: split by hairline)
    surfaceElevated = Color(0xFFEBE9DF),    // fill
    card = Color(0xFFEBE9DF),               // fill
    border = Color(0xFFD8D6CD),             // hairline
    textPrimary = DitherMono.Black,         // ink
    textSecondary = Color(0xFF666460),      // ink-muted
    textMuted = Color(0xFF666460),          // ink-muted
    textPlaceholder = Color(0xFF666460),    // ink-muted
    primary = DitherMono.Black,             // ink (send button)
    onPrimary = DitherMono.Paper,           // surface on ink
    isDark = false
)

val LocalWyrdColors = staticCompositionLocalOf { DarkWyrdColors }

// Top-level dynamic getters:
// These allow all existing code using Black, DarkSurface, White, etc. to automatically adapt to the current theme!
val Black: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWyrdColors.current.background

val DarkSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWyrdColors.current.surface

val DarkSurfaceElevated: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWyrdColors.current.surfaceElevated

val DarkCard: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWyrdColors.current.card

val DarkBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWyrdColors.current.border

val TextWhite: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWyrdColors.current.textPrimary

val TextGray: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWyrdColors.current.textSecondary

val TextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWyrdColors.current.textMuted

val TextPlaceholder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWyrdColors.current.textPlaceholder

val White: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWyrdColors.current.primary

val OnPrimaryColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalWyrdColors.current.onPrimary

val isAppThemeDark: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalWyrdColors.current.isDark
