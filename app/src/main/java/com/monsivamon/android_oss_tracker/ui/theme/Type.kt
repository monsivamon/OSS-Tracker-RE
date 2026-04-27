package com.monsivamon.android_oss_tracker.ui.theme

import androidx.compose.material3.Typography // ← 変更
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
        bodyLarge = TextStyle( // ← body1 から bodyLarge に変更
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp
        )
)