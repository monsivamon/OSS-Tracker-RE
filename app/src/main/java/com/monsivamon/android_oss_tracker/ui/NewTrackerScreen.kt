package com.monsivamon.android_oss_tracker.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.monsivamon.android_oss_tracker.OSSApp
import com.monsivamon.android_oss_tracker.PersistentState
import com.monsivamon.android_oss_tracker.repo.Direct
import com.monsivamon.android_oss_tracker.repo.MetaDataState
import com.monsivamon.android_oss_tracker.repo.RepoMetaData
import com.monsivamon.android_oss_tracker.util.AppSettings
import java.net.URL

/**
 * A preview card shown before the user adds a repository.
 *
 * @param repoUrl    The URL of the repository or direct APK link.
 * @param customName An optional custom display name to show instead of the default.
 * @param onAdd      Called with the canonical repository URL and current display name when the user confirms.
 */
@Composable
fun TrackerPreview(
    repoUrl: String,
    customName: String? = null,
    onAdd: (String, String) -> Unit
) {
    val requestQueue = remember { OSSApp.requestQueue }
    val metaData = remember { RepoMetaData(repoUrl, requestQueue) }
    val ctx = LocalContext.current

    // Immediately apply the custom name so the preview is up‑to‑date
    LaunchedEffect(customName) {
        metaData.customName = customName
    }

    LaunchedEffect(repoUrl) { metaData.refreshNetwork() }
    val trackPreReleases = AppSettings.trackPreReleases
    LaunchedEffect(trackPreReleases) { metaData.refreshNetwork() }

    val stable = metaData.latestRelease.value
    val pre    = metaData.latestPreRelease.value
    val isValid = remember { mutableStateOf(false) }
    if (!isValid.value && (stable != null || pre != null)) isValid.value = true

    // Generate a user‑friendly error message when the add button is disabled
    val errorMessage = when {
        metaData.state.value == MetaDataState.Unsupported ->
            "Unsupported URL. Make sure it is a GitHub, GitLab, Codeberg, F‑Droid repository, or a direct APK link."
        metaData.state.value == MetaDataState.Errored && metaData.errors.isNotEmpty() ->
            metaData.errors.first()
        metaData.state.value == MetaDataState.Loaded && stable == null && pre == null ->
            "No APK releases found in this repository."
        else -> null
    }

    val fallbackText = when (metaData.state.value) {
        MetaDataState.Unsupported -> "<unsupported>"
        MetaDataState.Loading     -> "<loading>"
        MetaDataState.Errored     -> "<error>"
        MetaDataState.Loaded      -> if (stable == null && pre == null) "<no release>" else ""
    }

    val (providerLabel, providerColor) = when (metaData.repo.providerName) {
        "GitHub"   -> "GitHub"   to Color(0xFF24292F)
        "GitLab"   -> "GitLab"   to Color(0xFFE24329)
        "Codeberg" -> "Codeberg" to Color(0xFF2185D0)
        "F-Droid"  -> "F-Droid"  to Color(0xFF1976D2)
        "Direct"   -> "Direct"   to Color(0xFF607D8B)
        else       -> metaData.repo.providerName to Color(0xFF757575)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {

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

                pre?.let { p ->
                    Text("Pre-release", style = MaterialTheme.typography.titleSmall,
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

                // Show a specific error message when the add button is disabled
                if (errorMessage != null && !isValid.value) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Normal
                    )
                }

                if (stable == null && pre == null && metaData.state.value == MetaDataState.Loaded) {
                    Text(fallbackText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            FilledIconButton(
                enabled = isValid.value,
                onClick = {
                    // Compute the canonical repository URL to avoid duplicate entries
                    val canonicalUrl = when {
                        metaData.repo is Direct -> repoUrl
                        metaData.repo.providerName == "F-Droid" -> {
                            val pkg = metaData.repo.getApplicationName(repoUrl)
                            "https://f-droid.org/packages/$pkg"
                        }
                        else -> {
                            try {
                                val host = URL(repoUrl).host
                                "https://$host/${metaData.repo.getOrgName(repoUrl)}/${metaData.repo.getApplicationName(repoUrl)}"
                            } catch (_: Exception) {
                                repoUrl
                            }
                        }
                    }
                    onAdd(canonicalUrl, metaData.appName)
                },
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
 * Screen for adding a new tracker.
 *
 * The user pastes a repository URL or direct APK link, optionally provides
 * a custom display name, and taps "Test Repository" to see a preview.
 * The custom name is saved via [PersistentState.setCustomName] when the
 * tracker is added, and the preview updates in real time as the name is typed.
 * Duplicate URLs are rejected based on the canonical form of the repository.
 */
@Composable
fun NewTrackerScreen(
    onNewTrackerAdded: (() -> Unit)? = null,
    onNavigateToApps: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val sharedPrefs = remember { ctx.getSharedPreferences(PersistentState.STATE_FILENAME, Context.MODE_PRIVATE) }
    val focus = LocalFocusManager.current

    val url = remember { mutableStateOf("") }
    val customNameInput = remember { mutableStateOf("") }
    val tested = remember { mutableStateOf(false) }
    val finalTestUrl = remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                IconButton(onClick = {
                    focus.clearFocus()
                    onNavigateToApps()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Apps",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Add Tracker",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Box(modifier = Modifier.weight(1f))
        }

        OutlinedTextField(
            value = url.value,
            onValueChange = {
                url.value = it.trim()
                tested.value = false   // URL changed → reset preview
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Repository or APK URL") },
            placeholder = { Text("https://github.com/monsivamon/OSS-Tracker-RE") },
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done, keyboardType = KeyboardType.Uri),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            singleLine = true,
            trailingIcon = {
                if (url.value.isNotEmpty()) {
                    IconButton(onClick = {
                        url.value = ""
                        customNameInput.value = ""
                        tested.value = false
                        focus.clearFocus()
                    }) {
                        Icon(Icons.Default.Close, "Clear text")
                    }
                }
            }
        )

        // Custom name field – always visible, editing does NOT hide the preview
        OutlinedTextField(
            value = customNameInput.value,
            onValueChange = {
                customNameInput.value = it
                // Preview stays visible; name updates reactively via customName parameter
            },
            label = { Text("App Name (Optional)") },
            placeholder = { Text("e.g. My App") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Spacer(Modifier.height(16.dp))

        if (tested.value && finalTestUrl.value.isNotEmpty()) {
            TrackerPreview(
                repoUrl = finalTestUrl.value,
                customName = customNameInput.value.ifBlank { null },
                onAdd = { canonicalUrl, defaultName ->
                    // Prevent duplicate URLs using synchronous check on the canonical URL
                    if (PersistentState.isTracked(sharedPrefs, canonicalUrl)) {
                        Toast.makeText(ctx, "This URL is already tracked.", Toast.LENGTH_SHORT).show()
                        return@TrackerPreview
                    }

                    // Persist the custom name if the user provided one
                    if (customNameInput.value.isNotBlank()) {
                        PersistentState.setCustomName(ctx, canonicalUrl, customNameInput.value.trim())
                    }
                    PersistentState.addTracker(sharedPrefs, canonicalUrl)
                    val displayName = customNameInput.value.ifBlank { defaultName }
                    Toast.makeText(ctx, "Added $displayName to your trackers", Toast.LENGTH_LONG).show()
                    url.value = ""
                    customNameInput.value = ""
                    tested.value = false
                    onNewTrackerAdded?.invoke()
                }
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                if (url.value.isNotBlank()) {
                    finalTestUrl.value = url.value   // clean URL, without any custom name parameter
                    tested.value = true
                    focus.clearFocus()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp).height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Test Repository", style = MaterialTheme.typography.titleMedium) }
    }
}