package com.monsivamon.android_oss_tracker.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.monsivamon.android_oss_tracker.OSSApp
import com.monsivamon.android_oss_tracker.PersistentState
import com.monsivamon.android_oss_tracker.repo.MetaDataState
import com.monsivamon.android_oss_tracker.repo.RepoMetaData
import com.monsivamon.android_oss_tracker.util.AppSettings

/**
 * Preview card for a repository URL that has not yet been added to the
 * tracked list.  Fetches metadata and displays the latest stable and
 * pre‑release versions together with their assets.
 *
 * Tapping the ⊕ icon adds the repository to persistent storage.
 */
@Composable
fun TrackerPreview(repoUrl: String, onAdd: (String, String) -> Unit) {
    val requestQueue = remember { OSSApp.requestQueue }
    val metaData = remember { RepoMetaData(repoUrl, requestQueue) }
    val ctx = LocalContext.current

    LaunchedEffect(repoUrl) { metaData.refreshNetwork() }
    val trackPreReleases = AppSettings.trackPreReleases
    LaunchedEffect(trackPreReleases) { metaData.refreshNetwork() }

    val stable = metaData.latestRelease.value
    val pre    = metaData.latestPreRelease.value
    val isValid = remember { mutableStateOf(false) }
    if (!isValid.value && (stable != null || pre != null)) isValid.value = true

    val fallbackText = when (metaData.state.value) {
        MetaDataState.Unsupported -> "<unsupported>"
        MetaDataState.Loading     -> "<loading>"
        MetaDataState.Errored     -> "<error>"
        MetaDataState.Loaded      -> if (stable == null && pre == null) "<no release>" else ""
    }

    val (providerLabel, providerColor) = when (metaData.repo) {
        is com.monsivamon.android_oss_tracker.repo.GitHub -> "GitHub" to Color(0xFF24292F)
        is com.monsivamon.android_oss_tracker.repo.GitLab -> "GitLab" to Color(0xFFE24329)
        else -> "" to Color.Transparent
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {

                // Repository name + provider badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        metaData.appName, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (providerLabel.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(4.dp)).background(providerColor)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(providerLabel, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Stable release info
                stable?.let { s ->
                    Text("Stable Release", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable { openInBrowser(ctx, s.url) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(s.version, style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(s.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (s.assets.isNotEmpty()) {
                        Text("Available files:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        s.assets.forEach { Text("• ${it.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, start = 8.dp)) }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Pre-release info
                pre?.let { p ->
                    Text("Pre‑release", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .clickable { openInBrowser(ctx, p.url) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(p.version, style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(p.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (p.assets.isNotEmpty()) {
                        Text("Available files:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        p.assets.forEach { Text("• ${it.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, start = 8.dp)) }
                    }
                }

                if (stable == null && pre == null && metaData.state.value == MetaDataState.Loaded) {
                    Text(fallbackText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            FilledIconButton(
                enabled = isValid.value,
                onClick = { onAdd(repoUrl, metaData.appName) },
                modifier = Modifier.padding(start = 16.dp).size(48.dp)
            ) { Icon(Icons.Default.Add, "Add Repository") }
        }
    }
}

private fun openInBrowser(context: Context, url: String) {
    if (url.isNotBlank()) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { }
    }
}

/**
 * Screen where the user enters a GitHub / GitLab URL, tests it against the
 * API, and optionally adds it to the tracked list.
 */
@Composable
fun NewTrackerScreen(onNewTrackerAdded: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val sharedPrefs = remember { ctx.getSharedPreferences(PersistentState.STATE_FILENAME, Context.MODE_PRIVATE) }
    val focus = LocalFocusManager.current
    val url = remember { mutableStateOf("") }
    val tested = remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Add a new App Tracker", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))

        OutlinedTextField(
            value = url.value,
            onValueChange = { url.value = it.trim(); tested.value = false },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Project Repository URL") },
            placeholder = { Text("https://github.com/monsivamon/OSS-Tracker-RE") },
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done, keyboardType = KeyboardType.Uri),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            singleLine = true,
            trailingIcon = {
                if (url.value.isNotEmpty()) {
                    IconButton(onClick = { url.value = ""; tested.value = false; focus.clearFocus() }) {
                        Icon(Icons.Default.Close, "Clear text")
                    }
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        if (tested.value) {
            TrackerPreview(
                repoUrl = url.value,
                onAdd = { repo, name ->
                    PersistentState.addTracker(sharedPrefs, repo)
                    Toast.makeText(ctx, "Added $name to your trackers", Toast.LENGTH_LONG).show()
                    url.value = ""
                    tested.value = false
                    onNewTrackerAdded?.invoke()
                }
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { if (url.value.isNotBlank()) { tested.value = true; focus.clearFocus() } },
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp).height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Test Repository", style = MaterialTheme.typography.titleMedium) }
    }
}