package com.monsivamon.android_oss_tracker.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.monsivamon.android_oss_tracker.repo.AssetInfo
import com.monsivamon.android_oss_tracker.repo.LatestVersionData
import com.monsivamon.android_oss_tracker.repo.RepoMetaData
import com.monsivamon.android_oss_tracker.util.DownloadStateManager
import com.monsivamon.android_oss_tracker.util.DownloadStateManager.DownloadStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Displays a repository that has been successfully loaded, including
 * separate cards for the latest stable and pre‑release versions.
 *
 * Each card is collapsible and contains per‑asset download controls
 * with pause, resume, cancel, and one‑tap install.
 */
@Composable
fun LoadedTracker(metaData: RepoMetaData) {
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allDownloadStates by DownloadStateManager.states.collectAsState()

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = metaData.appName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        metaData.latestRelease.value?.let { release ->
            ReleaseSection(
                label = "Stable Release",
                versionData = release,
                allDownloadStates = allDownloadStates,
                onDownload = { asset -> startDownload(ctx, metaData.appName, asset, "Stable", release.version) },
                onPause    = { asset -> pauseDownload(ctx, asset) },
                onResume   = { asset -> resumeDownload(ctx, asset) },
                onInstall  = { file, cb -> installApk(ctx, file, coroutineScope, cb) }
            )
        }

        metaData.latestPreRelease.value?.let { pre ->
            ReleaseSection(
                label = "Pre‑release",
                versionData = pre,
                allDownloadStates = allDownloadStates,
                onDownload = { asset -> startDownload(ctx, metaData.appName, asset, "Pre-release", pre.version) },
                onPause    = { asset -> pauseDownload(ctx, asset) },
                onResume   = { asset -> resumeDownload(ctx, asset) },
                onInstall  = { file, cb -> installApk(ctx, file, coroutineScope, cb) }
            )
        }
    }
}

/** A single collapsible release card (Stable or Pre‑release). */
@Composable
private fun ReleaseSection(
    label: String,
    versionData: LatestVersionData,
    allDownloadStates: Map<String, DownloadStatus>,
    onDownload: (AssetInfo) -> Unit,
    onPause: (AssetInfo) -> Unit,
    onResume: (AssetInfo) -> Unit,
    onInstall: (java.io.File, () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(label, style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(versionData.version, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium)
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    versionData.assets.forEach { asset ->
                        val status = allDownloadStates[asset.downloadUrl]
                        AssetDownloadRow(
                            asset = asset,
                            status = status,
                            onDownload = { onDownload(asset) },
                            onPause = onPause,
                            onResume = onResume,
                            onInstall = onInstall
                        )
                    }
                }
            }
        }
    }
}

/** Per‑asset download / pause / resume / install controls. */
@Composable
private fun AssetDownloadRow(
    asset: AssetInfo,
    status: DownloadStatus?,
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
                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
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
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Resume")
                }
            }
        }
        is DownloadStatus.Completed -> {
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
        }
        is DownloadStatus.Failed -> {
            Text("Download failed: ${status.error}", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        null, is DownloadStatus.Idle -> {
            FilledTonalButton(onClick = onDownload,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)) {
                Text("Download: ${asset.name}")
            }
        }
    }
}

// ── Internal helpers ─────────────────────────────────────────────

private fun startDownload(
    context: android.content.Context, repoName: String,
    asset: AssetInfo, releaseType: String, releaseVersion: String
) {
    val intent = Intent(context, ApkDownloadService::class.java).apply {
        putExtra("DOWNLOAD_URL", asset.downloadUrl)
        putExtra("FILE_NAME", asset.name)
        putExtra("REPO_NAME", repoName)
        putExtra("RELEASE_TYPE", releaseType)
        putExtra("RELEASE_VERSION", releaseVersion)
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