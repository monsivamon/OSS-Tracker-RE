package com.monsivamon.android_oss_tracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monsivamon.android_oss_tracker.OSSApp
import com.monsivamon.android_oss_tracker.util.AppSettings
import com.monsivamon.android_oss_tracker.util.ThemeMode
import com.monsivamon.android_oss_tracker.util.ThemeState

/**
 * Application settings screen.
 *
 * Organized into the following sections:
 * 1. Theme – Light / Dark / System appearance.
 * 2. Tracking – Pre‑release inclusion, install behaviour, automatic update checks.
 * 3. API Configuration – GitHub personal access token input (bypasses API rate limits).
 * 4. Repo List Manager – Import / export tracked repository URLs.
 * 5. Reset & Reboot / About – Factory‑reset and application metadata.
 * 6. Developer Options (collapsible) – Advanced controls such as hiding bottom‑bar tabs.
 */
@Composable
fun SettingsScreen(
    onNavigateToApps: () -> Unit = {}
) {
    val ctx = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // ── Header with back navigation ─────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                IconButton(onClick = onNavigateToApps) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Apps",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(modifier = Modifier.weight(1f))
        }

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

        var trackPreReleases by remember { mutableStateOf(AppSettings.trackPreReleases) }
        SettingSwitch(
            label = "Track pre-releases",
            checked = trackPreReleases,
            onCheckedChange = {
                trackPreReleases = it
                AppSettings.setAppTrackPreReleases(it)
            }
        )

        var installAfterDownload by remember { mutableStateOf(AppSettings.installAfterDownload) }
        SettingSwitch(
            label = "Install after download",
            checked = installAfterDownload,
            onCheckedChange = {
                installAfterDownload = it
                AppSettings.setAppInstallAfterDownload(it)
            }
        )

        var autoUpdateEnabled by remember { mutableStateOf(AppSettings.autoUpdateEnabled) }
        SettingSwitch(
            label = "Auto check for updates",
            checked = autoUpdateEnabled,
            onCheckedChange = { enabled ->
                autoUpdateEnabled = enabled
                AppSettings.setAppAutoUpdateEnabled(enabled)
                (ctx.applicationContext as? OSSApp)?.scheduleAutoUpdateCheck()
            }
        )
        if (autoUpdateEnabled) {
            Text(
                text = "Checked approximately every 24 hours",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
            )
        }

        SectionDivider()

        // ── ③ API Configuration (GitHub token) ─────────
        SectionHeader("API Configuration")

        var githubToken by remember { mutableStateOf(AppSettings.githubToken) }
        var isTokenVisible by remember { mutableStateOf(false) }

        OutlinedTextField(
            value = githubToken,
            onValueChange = {
                githubToken = it
                // Persist the token immediately on every keystroke
                AppSettings.setAppGithubToken(it)
            },
            label = { Text("GitHub Personal Access Token") },
            placeholder = { Text("ghp_xxxxxxxxxxxxxxxxxxxx") },
            singleLine = true,
            visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                    Icon(
                        imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle Token Visibility"
                    )
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
        Text(
            text = "Required to bypass GitHub's 60 requests/hour API limit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
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

        SectionDivider()

        // ── ⑥ Developer Options (collapsible) ───────────
        var devOptionsExpanded by remember { mutableStateOf(AppSettings.developerOptionsExpanded) }
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        devOptionsExpanded = !devOptionsExpanded
                        AppSettings.setAppDeveloperOptionsExpanded(devOptionsExpanded)
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Developer Options",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (devOptionsExpanded) "▲" else "▼",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = devOptionsExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Text(
                        text = "Hide Bottom Menu",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    var hideNew by remember { mutableStateOf(!AppSettings.showNewTab) }
                    SettingSwitch(
                        label = "New",
                        checked = hideNew,
                        onCheckedChange = {
                            hideNew = it
                            AppSettings.setAppShowNewTab(!it)
                        }
                    )

                    var hideHistory by remember { mutableStateOf(!AppSettings.showHistoryTab) }
                    SettingSwitch(
                        label = "History",
                        checked = hideHistory,
                        onCheckedChange = {
                            hideHistory = it
                            AppSettings.setAppShowHistoryTab(!it)
                        }
                    )
                }
            }
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

/**
 * A labelled switch row used throughout the settings screen.
 *
 * The [Switch] is wrapped in [key] so that its animation resets
 * cleanly when the checked state is toggled.
 */
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

        key(checked) {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}