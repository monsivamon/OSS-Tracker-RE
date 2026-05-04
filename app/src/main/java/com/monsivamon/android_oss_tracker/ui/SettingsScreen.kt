package com.monsivamon.android_oss_tracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monsivamon.android_oss_tracker.OSSApp
import com.monsivamon.android_oss_tracker.util.AppSettings
import com.monsivamon.android_oss_tracker.util.ThemeMode
import com.monsivamon.android_oss_tracker.util.ThemeState

/**
 * Application settings screen.
 *
 * Organized into six logical sections:
 * 1. Theme – Light / Dark / System appearance.
 * 2. Tracking – Pre‑release inclusion, install behaviour, automatic update checks.
 * 3. Hide Bottom Menu – Controls visibility of the New and History tabs.
 * 4. Repo List Manager – Import / export tracked repository URLs.
 * 5. Reset & Reboot – Factory‑reset the app and restart.
 * 6. About – Application metadata and credits.
 */
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        // ── ① Theme ────────────────────────────────────
        SectionHeader("Theme")

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
                    border = if (!selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
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
                        modifier = Modifier.padding(vertical = 10.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        SectionDivider()

        // ── ② Tracking ─────────────────────────────────
        SectionHeader("Tracking")

        SettingSwitch(
            label = "Track pre-releases",
            checked = AppSettings.trackPreReleases,
            onCheckedChange = { AppSettings.setAppTrackPreReleases(it) }
        )
        SettingSwitch(
            label = "Install after download",
            checked = AppSettings.installAfterDownload,
            onCheckedChange = { AppSettings.setAppInstallAfterDownload(it) }
        )
        SettingSwitch(
            label = "Auto check for updates",
            checked = AppSettings.autoUpdateEnabled,
            onCheckedChange = { enabled ->
                AppSettings.setAppAutoUpdateEnabled(enabled)
                (ctx.applicationContext as? OSSApp)?.scheduleAutoUpdateCheck()
            }
        )
        if (AppSettings.autoUpdateEnabled) {
            Text(
                text = "Checked approximately every 24 hours",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
            )
        }

        SectionDivider()

        // ── ③ Hide Bottom Menu ─────────────────────────
        SectionHeader("Hide Bottom Menu")

        SettingSwitch(
            label = "New",
            checked = !AppSettings.showNewTab,
            onCheckedChange = { AppSettings.setAppShowNewTab(!it) }
        )
        SettingSwitch(
            label = "History",
            checked = !AppSettings.showHistoryTab,
            onCheckedChange = { AppSettings.setAppShowHistoryTab(!it) }
        )

        SectionDivider()

        // ── ④ Repo List Manager ─────────────────────────
        SectionHeader("Repo List Manager")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) { RepoListImporter() }
            Box(modifier = Modifier.weight(1f)) { RepoListExporter() }
        }

        SectionDivider()

        // ── ⑤ Reset & Reboot  +  About ──────────────────
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) { RepoDeleteAll() }
            Box(modifier = Modifier.weight(1f)) { AboutAppDialog() }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/** Renders a section heading with standard styling. */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

/** Renders a horizontal divider with consistent margins. */
@Composable
private fun SectionDivider() {
    Spacer(modifier = Modifier.height(12.dp))
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
    Spacer(modifier = Modifier.height(12.dp))
}

/** A labelled switch row used throughout the settings screen. */
@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}