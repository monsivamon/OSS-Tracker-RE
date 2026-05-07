package com.monsivamon.android_oss_tracker.ui

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
 * Flat card representing one tracked repository.
 *
 * Action buttons in the top‑right corner provide:
 * - ▲/▼ for reordering,
 * - A refresh icon that cancels any in‑flight downloads and re‑fetches metadata,
 * - A delete icon that removes the repository from the list.
 *
 * During a pull‑to‑refresh or while metadata is loading, the refresh icon
 * is replaced by a [CircularProgressIndicator].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderItem(
    repoUrl: String,
    onDelete: (String, String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    /** `true` while the parent screen is performing a pull‑to‑refresh. */
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val requestQueue = remember { OSSApp.requestQueue }
    val metaData = remember(repoUrl) {
        AppCache.cachedRepos.getOrPut(repoUrl) { RepoMetaData(repoUrl, requestQueue).apply { refreshNetwork() } }
    }

    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = { }
    ) {
        Box(Modifier.fillMaxWidth()) {
            // Main content area – the right padding reserves space for the action buttons
            Box(Modifier.fillMaxWidth().padding(end = 52.dp)) {
                Crossfade(
                    targetState = if (metaData.latestRelease.value != null || metaData.latestPreRelease.value != null)
                        MetaDataState.Loaded else metaData.state.value,
                    animationSpec = tween(300),
                    label = "StateTransition"
                ) { state ->
                    when (state) {
                        MetaDataState.Unsupported -> UnsupportedTracker(metaData)
                        MetaDataState.Loading    -> LoadingTracker(metaData)
                        MetaDataState.Errored    -> ErroredTracker(metaData)
                        MetaDataState.Loaded     -> LoadedTracker(metaData)
                    }
                }
            }

            // Action buttons (top‑right corner)
            Column(Modifier.align(Alignment.TopEnd).padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Move up
                IconButton(onClick = { onMoveUp(repoUrl) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowUpward, "Move up", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
                // Move down
                IconButton(onClick = { onMoveDown(repoUrl) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowDownward, "Move down", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
                // Refresh – shows a spinner while the repository is being reloaded
                IconButton(onClick = {
                    val all = metaData.latestRelease.value?.assets.orEmpty() + metaData.latestPreRelease.value?.assets.orEmpty()
                    all.forEach { asset ->
                        when (DownloadStateManager.states.value[asset.downloadUrl]) {
                            is DownloadStatus.Downloading, is DownloadStatus.Paused -> {
                                val i = Intent(context, ApkDownloadService::class.java).apply {
                                    action = ApkDownloadService.ACTION_CANCEL
                                    putExtra("DOWNLOAD_URL", asset.downloadUrl)
                                }
                                context.startService(i)
                            }
                            is DownloadStatus.Completed, is DownloadStatus.Failed ->
                                DownloadStateManager.updateStatus(asset.downloadUrl, DownloadStatus.Idle)
                            else -> {}
                        }
                    }
                    metaData.refreshNetwork()
                }, modifier = Modifier.size(40.dp)) {
                    if (isRefreshing || metaData.state.value == MetaDataState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(Icons.Default.Refresh, "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    }
                }
                // Delete
                IconButton(onClick = { onDelete(metaData.appName, metaData.repoUrl) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}