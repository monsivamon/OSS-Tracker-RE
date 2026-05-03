package com.monsivamon.android_oss_tracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monsivamon.android_oss_tracker.util.ThemeMode
import com.monsivamon.android_oss_tracker.util.ThemeState

/**
 * Settings screen with four logical groups:
 * - Manage trackers (import / export)
 * - Theme selection (Light / Dark / System)
 * - Reset & Reboot (factory reset the entire app)
 * - About dialog
 */
@Composable
fun SettingsScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
        )

        // ── Manage Trackers ─────────────────────────
        Text(
            text = "Manage Trackers",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        RepoListImporter()
        RepoListExporter()

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        // ── Theme ────────────────────────────────────
        Text(
            text = "Theme",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        var selectedMode by remember { mutableStateOf(ThemeState.currentMode) }
        val entries = ThemeMode.entries
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            entries.forEachIndexed { index, mode ->
                val selected = selectedMode == mode
                val isFirst = index == 0
                val isLast = index == entries.lastIndex

                Surface(
                    onClick = {
                        selectedMode = mode
                        ThemeState.setThemeMode(mode)
                    },
                    modifier = Modifier.weight(1f),
                    shape = when {
                        isFirst && isLast -> RoundedCornerShape(12.dp)
                        isFirst           -> RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                        isLast            -> RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                        else              -> RoundedCornerShape(0.dp)
                    },
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = if (selected) 2.dp else 0.dp,
                    border = if (!selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    else null
                ) {
                    Text(
                        text = when (mode) {
                            ThemeMode.LIGHT  -> "Light"
                            ThemeMode.DARK   -> "Dark"
                            ThemeMode.SYSTEM -> "System"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        // ── Reset & Reboot ─────────────────────────
        RepoDeleteAll()
        Spacer(modifier = Modifier.height(16.dp))

        // ── About ──────────────────────────────────
        AboutAppDialog()
    }
}