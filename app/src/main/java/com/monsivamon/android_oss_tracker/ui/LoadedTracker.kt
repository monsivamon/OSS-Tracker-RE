package com.monsivamon.android_oss_tracker.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.monsivamon.android_oss_tracker.repo.AssetInfo
import com.monsivamon.android_oss_tracker.repo.LatestVersionData
import com.monsivamon.android_oss_tracker.repo.RepoMetaData
import com.monsivamon.android_oss_tracker.util.AppSettings
import com.monsivamon.android_oss_tracker.util.DownloadStateManager
import com.monsivamon.android_oss_tracker.util.DownloadStateManager.DownloadStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Displays the metadata and downloadable assets of a repository that has
 * been successfully fetched.
 *
 * When "Install after download" is disabled in [AppSettings], completed
 * downloads show a static "Download Done" label instead of the install button.
 */
@Composable
fun LoadedTracker(metaData: RepoMetaData) {
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allDownloadStates by DownloadStateManager.states.collectAsState()

    // React to changes in the "track pre-releases" setting.
    val trackPreReleases = AppSettings.trackPreReleases
    LaunchedEffect(trackPreReleases) { metaData.refreshNetwork() }

    val installAfterDownload = AppSettings.installAfterDownload
    val provider = metaData.repo.providerName
    val (providerLabel, providerColor) = when (provider) {
        "GitHub" -> "GitHub" to Color(0xFF24292F)
        "GitLab" -> "GitLab" to Color(0xFFE24329)
        else    -> "" to Color.Transparent
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // Repository name + provider badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = metaData.appName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (providerLabel.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                ProviderBadge(providerLabel, providerColor)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Stable release card
        metaData.latestRelease.value?.let { release ->
            ReleaseSection(
                label = "Stable Release",
                versionData = release,
                badgeColor = MaterialTheme.colorScheme.secondaryContainer,
                onBadgeColor = MaterialTheme.colorScheme.onSecondaryContainer,
                allDownloadStates = allDownloadStates,
                installAfterDownload = installAfterDownload,
                onDownload = { startDownload(ctx, metaData.appName, it, "Stable", release.version, provider) },
                onPause    = { pauseDownload(ctx, it) },
                onResume   = { resumeDownload(ctx, it) },
                onInstall  = { file, cb -> installApk(ctx, file, coroutineScope, cb) }
            )
        }

        // Pre-release card
        metaData.latestPreRelease.value?.let { pre ->
            ReleaseSection(
                label = "Pre‑release",
                versionData = pre,
                badgeColor = MaterialTheme.colorScheme.tertiaryContainer,
                onBadgeColor = MaterialTheme.colorScheme.onTertiaryContainer,
                allDownloadStates = allDownloadStates,
                installAfterDownload = installAfterDownload,
                onDownload = { startDownload(ctx, metaData.appName, it, "Pre-release", pre.version, provider) },
                onPause    = { pauseDownload(ctx, it) },
                onResume   = { resumeDownload(ctx, it) },
                onInstall  = { file, cb -> installApk(ctx, file, coroutineScope, cb) }
            )
        }
    }
}

// ── Internal composables ─────────────────────────────────────────────

@Composable
private fun ProviderBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ReleaseSection(
    label: String,
    versionData: LatestVersionData,
    badgeColor: Color,
    onBadgeColor: Color,
    allDownloadStates: Map<String, DownloadStatus>,
    installAfterDownload: Boolean,
    onDownload: (AssetInfo) -> Unit,
    onPause: (AssetInfo) -> Unit,
    onResume: (AssetInfo) -> Unit,
    onInstall: (java.io.File, () -> Unit) -> Unit
) {
    val ctx = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Header – tap to expand / collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        label, style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold
                    )
                    // Version badge – tap opens release page in browser
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor)
                            .clickable {
                                if (versionData.url.isNotBlank()) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, versionData.url.toUri())
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        ctx.startActivity(intent)
                                    } catch (_: Exception) { /* ignore errors opening the browser */ }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            versionData.version, style = MaterialTheme.typography.labelLarge,
                            color = onBadgeColor, fontWeight = FontWeight.Medium
                        )
                    }
                    if (versionData.date.isNotBlank()) {
                        Text(
                            versionData.date, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            // Collapsible asset list
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    versionData.assets.forEach { asset ->
                        val status = allDownloadStates[asset.downloadUrl]
                        AssetDownloadRow(
                            asset, status, installAfterDownload,
                            { onDownload(asset) }, onPause, onResume, onInstall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetDownloadRow(
    asset: AssetInfo,
    status: DownloadStatus?,
    installAfterDownload: Boolean,
    onDownload: () -> Unit,
    onPause: (AssetInfo) -> Unit,
    onResume: (AssetInfo) -> Unit,
    onInstall: (java.io.File, () -> Unit) -> Unit
) {
    when (status) {
        is DownloadStatus.Downloading -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Downloading: ${asset.name}", style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 4.dp))
                LinearProgressIndicator(progress = { status.progress.percent / 100f },
                    modifier = Modifier.fillMaxWidth())
                Text("${status.progress.percent}%", style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
                FilledTonalButton(onClick = { onPause(asset) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Pause, null, Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pause")
                }
            }
        }
        is DownloadStatus.Paused -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Paused: ${asset.name}", style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 4.dp))
                LinearProgressIndicator(progress = { status.progress.percent / 100f },
                    modifier = Modifier.fillMaxWidth())
                Text("${status.progress.percent}%", style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
                FilledTonalButton(onClick = { onResume(asset) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Resume")
                }
            }
        }
        is DownloadStatus.Completed -> {
            if (installAfterDownload) {
                var installing by remember { mutableStateOf(false) }
                if (installing) {
                    Text("Installing...", modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                } else {
                    Button(onClick = {
                        installing = true
                        onInstall(status.apkFile) {
                            DownloadStateManager.updateStatus(asset.downloadUrl, DownloadStatus.Idle)
                        }
                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text("Tap to install: ${asset.name}")
                    }
                }
            } else {
                Text("Download Done", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        is DownloadStatus.Failed -> {
            Text("Download failed: ${status.error}", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        null, is DownloadStatus.Idle -> {
            FilledTonalButton(onClick = onDownload,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)) { Text("Download: ${asset.name}") }
        }
    }
}

// ── Download helpers ──────────────────────────────────────────────────

private fun startDownload(
    context: android.content.Context, repoName: String, asset: AssetInfo,
    releaseType: String, releaseVersion: String, provider: String
) {
    val intent = Intent(context, ApkDownloadService::class.java).apply {
        putExtra("DOWNLOAD_URL", asset.downloadUrl)
        putExtra("FILE_NAME", asset.name)
        putExtra("REPO_NAME", repoName)
        putExtra("RELEASE_TYPE", releaseType)
        putExtra("RELEASE_VERSION", releaseVersion)
        putExtra("PROVIDER", provider)
    }
    context.startForegroundService(intent)
    Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
}

private fun pauseDownload(context: android.content.Context, asset: AssetInfo) {
    val intent = Intent(context, ApkDownloadService::class.java).apply {
        action = ApkDownloadService.ACTION_PAUSE
        putExtra("DOWNLOAD_URL", asset.downloadUrl)
    }
    context.startService(intent)
}

private fun resumeDownload(context: android.content.Context, asset: AssetInfo) {
    val intent = Intent(context, ApkDownloadService::class.java).apply {
        action = ApkDownloadService.ACTION_RESUME
        putExtra("DOWNLOAD_URL", asset.downloadUrl)
    }
    context.startService(intent)
}

private fun installApk(
    context: android.content.Context, apkFile: java.io.File,
    scope: kotlinx.coroutines.CoroutineScope, onInstallComplete: () -> Unit
) {
    val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
    val installIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(apkUri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(installIntent)
    scope.launch {
        delay(3000L)
        onInstallComplete()
    }
}