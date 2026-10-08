package com.wyrd.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.wyrd.app.data.preferences.AppTheme

private val DarkColorScheme = darkColorScheme(
    primary = DarkWyrdColors.primary,
    onPrimary = DarkWyrdColors.onPrimary,
    primaryContainer = DarkWyrdColors.surfaceElevated,
    onPrimaryContainer = DarkWyrdColors.primary,
    secondary = DarkWyrdColors.textSecondary,
    onSecondary = DarkWyrdColors.primary,
    background = DarkWyrdColors.background,
    onBackground = DarkWyrdColors.primary,
    surface = DarkWyrdColors.surface,
    onSurface = DarkWyrdColors.primary,
    surfaceVariant = DarkWyrdColors.surfaceElevated,
    onSurfaceVariant = DarkWyrdColors.textSecondary,
    outline = DarkWyrdColors.border
)

private val LightColorScheme = lightColorScheme(
    primary = LightWyrdColors.primary,
    onPrimary = LightWyrdColors.onPrimary,
    primaryContainer = LightWyrdColors.surfaceElevated,
    onPrimaryContainer = LightWyrdColors.primary,
    secondary = LightWyrdColors.textSecondary,
    onSecondary = LightWyrdColors.primary,
    background = LightWyrdColors.background,
    onBackground = LightWyrdColors.primary,
    surface = LightWyrdColors.surface,
    onSurface = LightWyrdColors.primary,
    surfaceVariant = LightWyrdColors.surfaceElevated,
    onSurfaceVariant = LightWyrdColors.textSecondary,
    outline = LightWyrdColors.border
)

@Composable
fun WyrdTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (appTheme) {
        AppTheme.SYSTEM -> isSystemDark
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }

    val colors = if (isDark) DarkWyrdColors else LightWyrdColors
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    window.isStatusBarContrastEnforced = false
                    window.isNavigationBarContrastEnforced = false
                }
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(LocalWyrdColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
