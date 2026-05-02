package com.monsivamon.android_oss_tracker.ui

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.monsivamon.android_oss_tracker.OSSApp
import com.monsivamon.android_oss_tracker.repo.AppCache
import com.monsivamon.android_oss_tracker.repo.MetaDataState
import com.monsivamon.android_oss_tracker.repo.RepoMetaData
import com.monsivamon.android_oss_tracker.util.DownloadStateManager
import com.monsivamon.android_oss_tracker.util.DownloadStateManager.DownloadStatus

/**
 * A single card that represents one tracked repository.
 *
 * Tapping the refresh icon cancels every in‑flight or paused download for
 * this repository, resets completed / failed assets back to Idle, and
 * then refreshes the repository metadata from the network.
 * The delete icon removes the repository from persistent storage and the
 * in‑memory cache.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderItem(
    repoUrl: String,
    onDelete: (String, String) -> Unit
) {
    val context = LocalContext.current
    val requestQueue = remember { OSSApp.requestQueue }
    val metaData = remember(repoUrl) {
        AppCache.cachedRepos.getOrPut(repoUrl) {
            RepoMetaData(repoUrl, requestQueue).apply { refreshNetwork() }
        }
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        onClick = { /* Optional: open in external browser */ }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().padding(end = 48.dp)) {
                Crossfade(
                    targetState = if (metaData.latestVersion.value != null)
                        MetaDataState.Loaded else metaData.state.value,
                    animationSpec = tween(durationMillis = 300),
                    label = "SmoothStateTransition"
                ) { state ->
                    when (state) {
                        MetaDataState.Unsupported -> UnsupportedTracker(metaData)
                        MetaDataState.Loading -> LoadingTracker(metaData)
                        MetaDataState.Errored -> ErroredTracker(metaData)
                        MetaDataState.Loaded -> LoadedTracker(metaData)
                    }
                }
            }

            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = {
                    // Reset every asset's download state before re‑fetching metadata.
                    val currentAssets = metaData.latestAssets.value
                    currentAssets.forEach { asset ->
                        val status = DownloadStateManager.states.value[asset.downloadUrl]
                        when (status) {
                            is DownloadStatus.Downloading, is DownloadStatus.Paused -> {
                                // Active transfers must be cancelled through the service.
                                val cancelIntent = Intent(context, ApkDownloadService::class.java).apply {
                                    action = ApkDownloadService.ACTION_CANCEL
                                    putExtra("DOWNLOAD_URL", asset.downloadUrl)
                                }
                                context.startService(cancelIntent)
                            }
                            is DownloadStatus.Completed, is DownloadStatus.Failed -> {
                                // Completed / failed assets bypass the service.
                                DownloadStateManager.updateStatus(asset.downloadUrl, DownloadStatus.Idle)
                            }
                            else -> { /* Idle assets remain untouched */ }
                        }
                    }
                    metaData.refreshNetwork()
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Repository",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onDelete(metaData.appName, metaData.repoUrl) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Tracker",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}