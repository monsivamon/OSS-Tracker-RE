package com.monsivamon.android_oss_tracker.ui

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.monsivamon.android_oss_tracker.OSSApp
import com.monsivamon.android_oss_tracker.repo.AppCache
import com.monsivamon.android_oss_tracker.repo.MetaDataState
import com.monsivamon.android_oss_tracker.repo.RepoMetaData
import com.monsivamon.android_oss_tracker.util.AppSettings
import com.monsivamon.android_oss_tracker.util.DownloadStateManager
import com.monsivamon.android_oss_tracker.util.DownloadStateManager.DownloadStatus

/**
 * Renders a single tracked repository as a card with action buttons.
 *
 * Displays repository metadata with a crossfade animation between loading,
 * loaded, error, and unsupported states. Provides controls to reorder the item,
 * refresh its information, cancel active downloads, and delete it from the list.
 * The card border adapts its color when the "Cloud White" background theme is
 * selected to maintain sufficient contrast.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderItem(
    repoUrl: String,
    onDelete: (String, String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val requestQueue = remember { OSSApp.requestQueue }
    val metaData = remember(repoUrl) {
        AppCache.cachedRepos.getOrPut(repoUrl) { RepoMetaData(repoUrl, requestQueue).apply { refreshNetwork() } }
    }

    // Use a blue border for the "Cloud White" theme, and a white border otherwise
    val isCloudWhite = AppSettings.backgroundThemeIndex == 11
    val cardBorder = if (isCloudWhite) {
        BorderStroke(1.dp, Color(0xFF42A5F5).copy(alpha = 0.6f))
    } else {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
    }

    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = if (isCloudWhite) 0.2f else 0.15f)),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // Flat to avoid unnecessary depth
        onClick = { }
    ) {
        Box(Modifier.fillMaxWidth()) {
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

            Column(Modifier.align(Alignment.TopEnd).padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = { onMoveUp(repoUrl) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowUpward, "Move up", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = { onMoveDown(repoUrl) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowDownward, "Move down", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                }
                // Cancel any active downloads for this repository's assets, then refresh metadata
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
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Default.Refresh, "Refresh", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                    }
                }
                IconButton(onClick = { onDelete(metaData.appName, metaData.repoUrl) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}