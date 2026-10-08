package com.wyrd.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Fixed accent colors
val AccentRed = Color(0xFFFF453A)
val AccentGreen = Color(0xFF32D74B)

// Color Palette Definition
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
    background = Color(0xFF000000),         // Pure AMOLED Black
    surface = Color(0xFF0A0A0A),            // Drawer & dialogs
    surfaceElevated = Color(0xFF141414),    // Input capsule, elevated cards
    card = Color(0xFF1A1A1A),               // User message bubble, code card
    border = Color(0xFF262626),             // Subtle border
    textPrimary = Color(0xFFCECDC4),        // Warm bone white
    textSecondary = Color(0xFF8E8E93),      // Secondary gray
    textMuted = Color(0xFF636366),          // Muted metadata
    textPlaceholder = Color(0xFF7A7A7A),    // Input placeholder
    primary = Color(0xFFCECDC4),            // Primary action button (send button)
    onPrimary = Color(0xFF000000),          // Icon/text on primary button
    isDark = true
)

val LightWyrdColors = WyrdColors(
    background = Color(0xFFF9F9F7),         // Warm brutalist parchment / off-white
    surface = Color(0xFFF0EFEA),            // Drawer & dialogs
    surfaceElevated = Color(0xFFE8E7E1),    // Input capsule, elevated cards
    card = Color(0xFFDFDED8),               // User message bubble, code card
    border = Color(0xFFCECDC4),             // Subtle border
    textPrimary = Color(0xFF141414),        // Deep charcoal/black
    textSecondary = Color(0xFF636366),      // Secondary gray
    textMuted = Color(0xFF8E8E93),          // Muted metadata
    textPlaceholder = Color(0xFF9E9E9B),    // Input placeholder
    primary = Color(0xFF141414),            // Primary action button (deep black send button)
    onPrimary = Color(0xFFF9F9F7),          // Icon/text on primary button
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
