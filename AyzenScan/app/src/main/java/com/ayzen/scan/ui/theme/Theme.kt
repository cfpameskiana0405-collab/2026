package com.ayzen.scan.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8B6BFF),
    onPrimary = Color.White,
    secondary = Color(0xFF22D3EE),
    onSecondary = Color(0xFF00232B),
    tertiary = Color(0xFFFF5C9D),
    background = Color(0xFF080B1A),
    onBackground = Color(0xFFEDEFFF),
    surface = Color(0xFF111631),
    onSurface = Color(0xFFEDEFFF),
    surfaceVariant = Color(0xFF1B2145),
    onSurfaceVariant = Color(0xFFA9B0D6),
    outline = Color(0xFF3A4275),
    error = Color(0xFFFF6B7A)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B3FE0),
    onPrimary = Color.White,
    secondary = Color(0xFF0891B2),
    onSecondary = Color.White,
    tertiary = Color(0xFFDB2777),
    background = Color(0xFFF5F6FF),
    onBackground = Color(0xFF11142B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF11142B),
    surfaceVariant = Color(0xFFE9EBFA),
    onSurfaceVariant = Color(0xFF555C85),
    outline = Color(0xFFB8BEE0),
    error = Color(0xFFD93A4F)
)

private val AyzenTypography = Typography().let {
    it.copy(
        headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun AyzenTheme(themeMode: Int, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AyzenTypography,
        content = content
    )
}
