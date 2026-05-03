package com.monsivamon.android_oss_tracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.monsivamon.android_oss_tracker.util.ThemeMode
import com.monsivamon.android_oss_tracker.util.ThemeState

private val DarkColorPalette  = darkColorScheme(primary = Purple200, tertiary = Purple700, secondary = Teal200)
private val LightColorPalette = lightColorScheme(primary = Purple500, tertiary = Purple700, secondary = Teal200)

/**
 * Root theme that respects the persisted [ThemeMode]:
 * - [ThemeMode.DARK]   – always dark
 * - [ThemeMode.LIGHT]  – always light
 * - [ThemeMode.SYSTEM] – follows [isSystemInDarkTheme]
 */
@Composable
fun AndroidossreleasetrackerTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (ThemeState.currentMode) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> systemDark
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorPalette else LightColorPalette,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}