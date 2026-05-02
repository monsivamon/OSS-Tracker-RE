package com.monsivamon.android_oss_tracker.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.monsivamon.android_oss_tracker.repo.RepoMetaData
import com.monsivamon.android_oss_tracker.util.DownloadStateManager
import com.monsivamon.android_oss_tracker.util.DownloadStateManager.DownloadStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Renders the UI for a successfully loaded repository, including per-asset
 * download controls with pause/resume support.
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
        Spacer(modifier = Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(6.dp)) {
                Text(
                    text = metaData.latestVersion.value ?: "Unknown",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            val date = metaData.latestVersionDate.value
            if (!date.isNullOrEmpty()) {
                Text(text = date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val assets = metaData.latestAssets.value
        if (assets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            assets.forEach { asset ->
                val downloadStatus = allDownloadStates[asset.downloadUrl]

                when (downloadStatus) {
                    is DownloadStatus.Downloading -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Downloading: ${asset.name}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            LinearProgressIndicator(
                                progress = { downloadStatus.progress.percent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${downloadStatus.progress.percent}%",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            FilledTonalButton(
                                onClick = {
                                    val intent = Intent(ctx, ApkDownloadService::class.java).apply {
                                        action = ApkDownloadService.ACTION_PAUSE
                                        putExtra("DOWNLOAD_URL", asset.downloadUrl)
                                    }
                                    ctx.startService(intent)
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
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
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            LinearProgressIndicator(
                                progress = { downloadStatus.progress.percent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${downloadStatus.progress.percent}%",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    val intent = Intent(ctx, ApkDownloadService::class.java).apply {
                                        action = ApkDownloadService.ACTION_RESUME
                                        putExtra("DOWNLOAD_URL", asset.downloadUrl)
                                    }
                                    ctx.startService(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resume")
                            }
                        }
                    }

                    is DownloadStatus.Completed -> {
                        var installing by remember { mutableStateOf(false) }
                        if (installing) {
                            Text(
                                text = "Installing...",
                                modifier = Modifier.padding(vertical = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Button(
                                onClick = {
                                    installing = true
                                    val apkUri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", downloadStatus.apkFile)
                                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    ctx.startActivity(installIntent)
                                    coroutineScope.launch {
                                        delay(3000L)
                                        DownloadStateManager.updateStatus(asset.downloadUrl, DownloadStatus.Idle)
                                        installing = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Tap to install: ${asset.name}")
                            }
                        }
                    }

                    is DownloadStatus.Failed -> {
                        Text(
                            text = "Download failed: ${downloadStatus.error}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    else -> {
                        FilledTonalButton(
                            onClick = {
                                val intent = Intent(ctx, ApkDownloadService::class.java).apply {
                                    putExtra("DOWNLOAD_URL", asset.downloadUrl)
                                    putExtra("FILE_NAME", asset.name)
                                    putExtra("REPO_NAME", metaData.appName)
                                }
                                ctx.startForegroundService(intent)
                                Toast.makeText(ctx, "Download started...", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Download: ${asset.name}")
                        }
                    }
                }
            }
        }
    }
}