package com.monsivamon.android_oss_tracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 shape definitions used throughout the application.
 *
 * - Small and medium components share a subtle 4 dp corner radius.
 * - Large components (e.g. dialogs) have sharp, 0 dp corners.
 */
val Shapes = Shapes(
        small  = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(4.dp),
        large  = RoundedCornerShape(0.dp)
)