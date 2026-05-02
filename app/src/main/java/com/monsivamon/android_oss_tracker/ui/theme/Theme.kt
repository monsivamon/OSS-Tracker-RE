package com.monsivamon.android_oss_tracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.monsivamon.android_oss_tracker.util.ThemeMode
import com.monsivamon.android_oss_tracker.util.ThemeState

/**
 * Dark colour palette used when dark mode is active.
 */
private val DarkColorPalette = darkColorScheme(
    primary = Purple200,
    tertiary = Purple700,
    secondary = Teal200
)

/**
 * Light colour palette used when light mode is active.
 */
private val LightColorPalette = lightColorScheme(
    primary = Purple500,
    tertiary = Purple700,
    secondary = Teal200
)

/**
 * Top‑level theme composable that applies the appropriate Material 3 colour
 * scheme based on the current theme preference.
 *
 * The resolved dark/light state is determined by [ThemeState.currentMode]:
 * - [ThemeMode.DARK]  → always dark
 * - [ThemeMode.LIGHT] → always light
 * - [ThemeMode.SYSTEM] → follows the device's system setting via [isSystemInDarkTheme].
 *
 * The theme also provides the custom [Typography] and [Shapes] defined in the
 * project.
 */
@Composable
fun AndroidossreleasetrackerTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (ThemeState.currentMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorPalette else LightColorPalette,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}