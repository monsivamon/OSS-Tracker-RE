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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.monsivamon.android_oss_tracker.OSSApp
import com.monsivamon.android_oss_tracker.PersistentState
import com.monsivamon.android_oss_tracker.repo.AppCache
import com.monsivamon.android_oss_tracker.repo.MetaDataState
import com.monsivamon.android_oss_tracker.repo.RepoMetaData
import com.monsivamon.android_oss_tracker.util.AppSettings
import com.monsivamon.android_oss_tracker.util.DownloadStateManager
import com.monsivamon.android_oss_tracker.util.DownloadStateManager.DownloadStatus

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
        AppCache.cachedRepos.getOrPut(repoUrl) {
            RepoMetaData(repoUrl, requestQueue).apply { refreshNetwork() }
        }
    }

    LaunchedEffect(repoUrl) {
        metaData.customName = PersistentState.getCustomName(context, repoUrl)
    }

    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }

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
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                        MetaDataState.Unsupported -> UnsupportedTracker(
                            metaData,
                            onDelete = { onDelete(metaData.appName, metaData.repoUrl) }
                        )
                        MetaDataState.Loading -> LoadingTracker(
                            metaData,
                            onDelete = { onDelete(metaData.appName, metaData.repoUrl) }
                        )
                        MetaDataState.Errored -> ErroredTracker(
                            metaData,
                            onDelete = { onDelete(metaData.appName, metaData.repoUrl) }
                        )
                        MetaDataState.Loaded -> LoadedTracker(
                            metaData,
                            onDelete = { onDelete(metaData.appName, metaData.repoUrl) }
                        )
                    }
                }
            }

            // Right‑side column (without delete)
            Column(
                Modifier.align(Alignment.TopEnd).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = { onMoveUp(repoUrl) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowUpward, "Move up", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = { onMoveDown(repoUrl) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowDownward, "Move down", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                }
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
                IconButton(onClick = {
                    editName = metaData.appName
                    showEditDialog = true
                }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Edit, "Edit name", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                }
            }
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit display name") },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = editName.trim()
                    if (newName.isNotEmpty()) {
                        PersistentState.setCustomName(context, repoUrl, newName)
                        metaData.customName = newName
                    } else {
                        PersistentState.setCustomName(context, repoUrl, "")
                        metaData.customName = null
                    }
                    showEditDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }
}