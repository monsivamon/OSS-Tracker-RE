package com.monsivamon.android_oss_tracker.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monsivamon.android_oss_tracker.OSSApp
import com.monsivamon.android_oss_tracker.util.AppSettings
import com.monsivamon.android_oss_tracker.util.ThemeMode
import com.monsivamon.android_oss_tracker.util.ThemeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Available background gradient themes.
 * Each entry consists of a display name and the two colors that form the gradient.
 */
private val THEME_OPTIONS = listOf(
    "Soft Purple" to listOf(Color(0xFFA18CD1), Color(0xFFFBC2EB)),
    "Mint Mist" to listOf(Color(0xFF84FAB0), Color(0xFF8FD3F4)),
    "Peach Lavender" to listOf(Color(0xFFFCCB90), Color(0xFFD57EEB)),
    "Sweet Dream" to listOf(Color(0xFFE0C3FC), Color(0xFF8EC5FC)),
    "Sunny Peach" to listOf(Color(0xFFF6D365), Color(0xFFFDA085)),
    "Aqua Violet" to listOf(Color(0xFF5EE7DF), Color(0xFFB490CA)),
    "Dusty Cream" to listOf(Color(0xFFD299C2), Color(0xFFFEF9D7)),
    "Pale Twilight" to listOf(Color(0xFFEBC0FD), Color(0xFFD9DED8)),
    "Lemon Garden" to listOf(Color(0xFF96FBC4), Color(0xFFF9F586)),
    "Sky Blue" to listOf(Color(0xFFA1C4FD), Color(0xFFC2E9FB)),
    "Rose Candy" to listOf(Color(0xFFFF9A9E), Color(0xFFFECFEF)),
    "Cloud White" to listOf(Color(0xFFFDFBFB), Color(0xFFEBEDEE)),
    "Lilac Snow" to listOf(Color(0xFFCD9CF2), Color(0xFFF6F3FF)),
    "Pearl Yellow" to listOf(Color(0xFFE9DEFA), Color(0xFFFBFCDB)),
    "Soft Marine" to listOf(Color(0xFFACCBEE), Color(0xFFE7F0FD)),
    "Apricot Glow" to listOf(Color(0xFFFFECD2), Color(0xFFFCB69F))
)

/**
 * Main settings screen composable.
 *
 * Displays sections for customizing the background theme, display mode,
 * tracking behavior, API configuration, backup & restore, and developer options.
 * Scroll position is restored via [AppSettings].
 */
@Composable
fun SettingsScreen(onNavigateToApps: () -> Unit = {}) {
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState(initial = AppSettings.settingsScrollPosition)

    DisposableEffect(scrollState) {
        onDispose { AppSettings.setAppSettingsScrollPosition(scrollState.value) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        SettingsHeader(onNavigateToApps)

        BackgroundThemeSection()
        Spacer(Modifier.height(16.dp))

        DisplaySection()
        SectionDivider()

        TrackingSection(ctx)
        SectionDivider()

        ApiConfigurationSection()
        SectionDivider()

        OtherManagersSection()
        SectionDivider()

        DeveloperOptionsSection(scrollState, coroutineScope)

        Spacer(modifier = Modifier.height(30.dp))
    }
}

/**
 * Header row for the settings screen.
 *
 * Contains a back button (left) and a centered "Settings" title.
 */
@Composable
private fun SettingsHeader(onNavigateBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(2f)
        )
        Box(modifier = Modifier.weight(1f))
    }
}

/**
 * Dropdown selector for the background gradient theme.
 *
 * Shows a list of predefined pastel gradient palettes inside a scrollable,
 * semi-transparent menu. The selected palette is applied immediately and
 * persisted via [AppSettings].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackgroundThemeSection() {
    var expanded by remember { mutableStateOf(false) }

    Text(
        text = "Background Theme",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 8.dp)
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        val currentTheme = THEME_OPTIONS[AppSettings.backgroundThemeIndex]

        OutlinedTextField(
            value = currentTheme.first,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            leadingIcon = {
                Box(Modifier.size(24.dp).clip(CircleShape).background(Brush.linearGradient(currentTheme.second)))
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )
        )

        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                surface = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                surfaceContainer = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            )
        ) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .heightIn(max = 300.dp)
                    .background(Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                THEME_OPTIONS.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        modifier = Modifier.background(Color.Transparent),
                        text = {
                            Text(
                                text = option.first,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                            )
                        },
                        leadingIcon = {
                            Box(
                                Modifier.size(20.dp).clip(CircleShape)
                                    .background(Brush.linearGradient(option.second))
                            )
                        },
                        onClick = {
                            AppSettings.setAppBackgroundThemeIndex(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Theme mode selector for light, dark, or system-defined appearance.
 */
@Composable
private fun DisplaySection() {
    Text(
        text = "Display",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 8.dp)
    )

    val selectedMode = ThemeState.currentMode
    val entries = ThemeMode.entries
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        entries.forEachIndexed { index, mode ->
            val selected = selectedMode == mode
            Surface(
                onClick = { ThemeState.setThemeMode(mode) },
                modifier = Modifier.weight(1f),
                shape = when (index) {
                    0 -> RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                    entries.lastIndex -> RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                    else -> RoundedCornerShape(0.dp)
                },
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            ) {
                Text(
                    text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Controls for tracking behavior and automatic update checks.
 */
@Composable
private fun TrackingSection(ctx: Context) {
    SectionHeader("Tracking")
    SettingSwitch("Track pre-releases", AppSettings.trackPreReleases) { AppSettings.setAppTrackPreReleases(it) }
    SettingSwitch("Install after download", AppSettings.installAfterDownload) { AppSettings.setAppInstallAfterDownload(it) }

    SettingSwitch(
        label = "Auto check for updates",
        checked = AppSettings.autoUpdateEnabled,
        subtitle = if (AppSettings.autoUpdateEnabled) "Checked approximately every 24 hours" else null
    ) {
        AppSettings.setAppAutoUpdateEnabled(it)
        (ctx.applicationContext as? OSSApp)?.scheduleAutoUpdateCheck()
    }
}

/**
 * Field for entering a personal GitHub token used for authenticated API requests.
 */
@Composable
private fun ApiConfigurationSection() {
    SectionHeader("API Configuration")
    var isTokenVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = AppSettings.githubToken,
        onValueChange = { AppSettings.setAppGithubToken(it) },
        label = { Text("GitHub Token") },
        placeholder = { Text("ghp_your_token_here") },
        visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                Icon(if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/**
 * Backup & Restore controls and other utility buttons.
 */
@Composable
private fun OtherManagersSection() {
    SectionHeader("Backup & Restore")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) { RepoListImporter() } // Restore button
        Box(modifier = Modifier.weight(1f)) { RepoListExporter() } // Backup button
    }

    SectionDivider()

    SectionHeader("Other")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f)) { RepoDeleteAll() }
        Box(modifier = Modifier.weight(1f)) { AboutAppDialog() }
    }
}

/**
 * Expandable section for developer-focused options such as tab visibility toggles.
 */
@Composable
private fun DeveloperOptionsSection(scrollState: ScrollState, coroutineScope: CoroutineScope) {
    var devExpanded by remember { mutableStateOf(AppSettings.developerOptionsExpanded) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    devExpanded = !devExpanded
                    AppSettings.setAppDeveloperOptionsExpanded(devExpanded)
                    if (devExpanded) {
                        coroutineScope.launch {
                            delay(150)
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                    }
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Developer Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(if (devExpanded) "▲" else "▼", color = MaterialTheme.colorScheme.onSurface)
        }

        AnimatedVisibility(visible = devExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                val hideNew = !AppSettings.showNewTab
                val hideHistory = !AppSettings.showHistoryTab

                SettingSwitch("Hide New Tab", hideNew) { isChecked ->
                    AppSettings.setAppShowNewTab(!isChecked)
                    handleDevScroll(isChecked, hideHistory, scrollState, coroutineScope)
                }

                SettingSwitch("Hide History Tab", hideHistory) { isChecked ->
                    AppSettings.setAppShowHistoryTab(!isChecked)
                    handleDevScroll(hideNew, isChecked, scrollState, coroutineScope)
                }
            }
        }
    }
}

/**
 * Adjusts the scroll position after a developer tab visibility change
 * so that the bottom of the content remains visible when needed.
 */
private fun handleDevScroll(
    hideNew: Boolean,
    hideHistory: Boolean,
    scrollState: ScrollState,
    scope: CoroutineScope
) {
    scope.launch {
        delay(150)
        when {
            !hideNew && !hideHistory -> scrollState.animateScrollTo(scrollState.maxValue)
            (hideNew && !hideHistory) || (!hideNew && hideHistory) -> {
                val target = (scrollState.maxValue - 80).coerceAtLeast(0)
                scrollState.animateScrollTo(target)
            }
            else -> scrollState.animateScrollTo(scrollState.value)
        }
    }
}

/**
 * Styled section title used throughout the settings screen.
 */
@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 8.dp))
}

/**
 * Horizontal divider that visually separates settings sections.
 */
@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
}

/**
 * Reusable switch control with a label and an optional descriptive subtitle.
 */
@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}