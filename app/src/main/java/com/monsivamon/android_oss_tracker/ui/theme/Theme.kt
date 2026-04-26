package com.monsivamon.android_oss_tracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme // ← 変更
import androidx.compose.material3.darkColorScheme // ← 変更
import androidx.compose.material3.lightColorScheme // ← 変更
import androidx.compose.runtime.Composable

// ↓ darkColors から darkColorScheme へ変更。 primaryVariant を tertiary に置き換え。
private val DarkColorPalette = darkColorScheme(
    primary = Purple200,
    tertiary = Purple700,
    secondary = Teal200
)

// ↓ lightColors から lightColorScheme へ変更
private val LightColorPalette = lightColorScheme(
    primary = Purple500,
    tertiary = Purple700,
    secondary = Teal200
)

@Composable
fun AndroidossreleasetrackerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colorScheme = colors, // ← colors から colorScheme へ変更
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}