package com.monsivamon.android_oss_tracker.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Displays the detailed information of a tracked repository when its metadata
 * has been successfully loaded.
 *
 * Shows the app name, provider badge, stable release, and pre‑release sections.
 * Automatically re‑fetches metadata when the "track pre‑releases" setting changes.
 */
@Composable
fun LoadedTracker(metaData: RepoMetaData) {
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allDownloadStates by DownloadStateManager.states.collectAsState()

    val trackPreReleases = AppSettings.trackPreReleases
    LaunchedEffect(trackPreReleases) {
        metaData.refreshNetwork()
    }

    val installAfterDownload = AppSettings.installAfterDownload
    val provider = metaData.repo.providerName

    val (providerLabel, providerColor) = when (provider) {
        "GitHub"   -> "GitHub"   to Color(0xFF24292F)
        "GitLab"   -> "GitLab"   to Color(0xFFE24329)
        "Codeberg" -> "Codeberg" to Color(0xFF2185D0)
        "F-Droid"  -> "F-Droid"  to Color(0xFF1976D2)
        "Direct"   -> "Direct"   to Color(0xFF607D8B)
        else       -> "" to Color.Transparent
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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

        metaData.latestRelease.value?.let { release ->
            ReleaseSection(
                label = "Stable Release",
                versionData = release,
                badgeColor = MaterialTheme.colorScheme.secondaryContainer,
                onBadgeColor = MaterialTheme.colorScheme.onSecondaryContainer,
                allDownloadStates = allDownloadStates,
                installAfterDownload = installAfterDownload,
                onDownload = {
                    startDownload(ctx, metaData.appName, it, "Stable", release.version, provider)
                },
                onPause = { pauseDownload(ctx, it) },
                onResume = { resumeDownload(ctx, it) },
                onInstall = { file, cb ->
                    installApk(ctx, file, coroutineScope, cb)
                }
            )
        }

        metaData.latestPreRelease.value?.let { pre ->
            ReleaseSection(
                label = "Pre‑release",
                versionData = pre,
                badgeColor = MaterialTheme.colorScheme.tertiaryContainer,
                onBadgeColor = MaterialTheme.colorScheme.onTertiaryContainer,
                allDownloadStates = allDownloadStates,
                installAfterDownload = installAfterDownload,
                onDownload = {
                    startDownload(ctx, metaData.appName, it, "Pre-release", pre.version, provider)
                },
                onPause = { pauseDownload(ctx, it) },
                onResume = { resumeDownload(ctx, it) },
                onInstall = { file, cb ->
                    installApk(ctx, file, coroutineScope, cb)
                }
            )
        }
    }
}

/**
 * A small colored badge displaying the name of the repository provider (e.g., GitHub, GitLab).
 */
@Composable
private fun ProviderBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.8f))
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

/**
 * An expandable card that displays the details of a single release (stable or pre‑release).
 *
 * Shows the version number (tappable to open the release page), release date,
 * and a list of downloadable assets. The card border adapts to the selected
 * background theme to maintain visual contrast.
 */
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
    onInstall: (File, () -> Unit) -> Unit
) {
    val ctx = LocalContext.current
    val expanded = remember { mutableStateOf(false) }

    // Use a blue border for the Cloud White theme, white border otherwise
    val isCloudWhite = AppSettings.backgroundThemeIndex == 11
    val cardBorder = if (isCloudWhite) {
        BorderStroke(1.dp, Color(0xFF42A5F5).copy(alpha = 0.5f))
    } else {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded.value = !expanded.value }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.9f))
                            .clickable {
                                if (versionData.url.isNotBlank()) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, versionData.url.toUri())
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        ctx.startActivity(intent)
                                    } catch (_: Exception) { }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = versionData.version,
                            style = MaterialTheme.typography.labelLarge,
                            color = onBadgeColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (versionData.date.isNotBlank()) {
                        Text(
                            text = versionData.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded.value) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            AnimatedVisibility(
                visible = expanded.value,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    versionData.assets.forEach { asset ->
                        val status = allDownloadStates[asset.downloadUrl]
                        AssetDownloadRow(
                            asset = asset,
                            status = status,
                            installAfterDownload = installAfterDownload,
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

/**
 * Renders the UI for a single downloadable asset, varying the controls and
 * progress indication based on its current [DownloadStatus] (idle, downloading,
 * paused, completed, or failed).
 */
@Composable
private fun AssetDownloadRow(
    asset: AssetInfo,
    status: DownloadStatus?,
    installAfterDownload: Boolean,
    onDownload: () -> Unit,
    onPause: (AssetInfo) -> Unit,
    onResume: (AssetInfo) -> Unit,
    onInstall: (File, () -> Unit) -> Unit
) {
    when (status) {
        is DownloadStatus.Downloading -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Downloading: ${asset.name}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                LinearProgressIndicator(
                    progress = { status.progress.percent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${status.progress.percent}%",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                FilledTonalButton(
                    onClick = { onPause(asset) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Pause, null, Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pause")
                }
            }
        }
        is DownloadStatus.Paused -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Paused: ${asset.name}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                LinearProgressIndicator(
                    progress = { status.progress.percent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${status.progress.percent}%",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                FilledTonalButton(
                    onClick = { onResume(asset) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Resume")
                }
            }
        }
        is DownloadStatus.Completed -> {
            if (installAfterDownload) {
                val installing = remember { mutableStateOf(false) }

                if (installing.value) {
                    Text(
                        text = "Installing...",
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Button(
                        onClick = {
                            installing.value = true
                            onInstall(status.apkFile) {
                                DownloadStateManager.updateStatus(asset.downloadUrl, DownloadStatus.Idle)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Tap to install: ${asset.name}")
                    }
                }
            } else {
                Text(
                    text = "Download Done",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
        is DownloadStatus.Failed -> {
            Text(
                text = "Download failed: ${status.error}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        null, is DownloadStatus.Idle -> {
            FilledTonalButton(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Download: ${asset.name}")
            }
        }
    }
}

/**
 * Starts downloading the specified asset by launching [ApkDownloadService]
 * as a foreground service.
 */
private fun startDownload(
    context: Context,
    repoName: String,
    asset: AssetInfo,
    releaseType: String,
    releaseVersion: String,
    provider: String
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

/**
 * Pauses an active download of the given asset.
 */
private fun pauseDownload(context: Context, asset: AssetInfo) {
    val intent = Intent(context, ApkDownloadService::class.java).apply {
        action = ApkDownloadService.ACTION_PAUSE
        putExtra("DOWNLOAD_URL", asset.downloadUrl)
    }
    context.startService(intent)
}

/**
 * Resumes a previously paused download of the given asset.
 */
private fun resumeDownload(context: Context, asset: AssetInfo) {
    val intent = Intent(context, ApkDownloadService::class.java).apply {
        action = ApkDownloadService.ACTION_RESUME
        putExtra("DOWNLOAD_URL", asset.downloadUrl)
    }
    context.startService(intent)
}

/**
 * Launches the system package installer for the downloaded APK file.
 *
 * After the intent is fired, a short delay is applied before invoking
 * [onInstallComplete] to reset the internal download status.
 */
private fun installApk(
    context: Context,
    apkFile: File,
    scope: CoroutineScope,
    onInstallComplete: () -> Unit
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