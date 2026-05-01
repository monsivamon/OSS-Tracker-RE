package com.monsivamon.android_oss_tracker.ui

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
import androidx.compose.ui.unit.dp
import com.monsivamon.android_oss_tracker.OSSApp
import com.monsivamon.android_oss_tracker.repo.AppCache
import com.monsivamon.android_oss_tracker.repo.MetaDataState
import com.monsivamon.android_oss_tracker.repo.RepoMetaData

/**
 * Single card item that binds a repository URL to its metadata and provides
 * refresh / delete actions.
 *
 * The metadata is cached in [AppCache.cachedRepos] so that navigating away
 * and back does not trigger a new network call.  The refresh button increments
 * [refreshTrigger] which forces [LoadedTracker] to reset all download states.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderItem(
    repoUrl: String,
    onDelete: (String, String) -> Unit
) {
    val requestQueue = remember { OSSApp.requestQueue }
    val refreshTrigger = remember { mutableIntStateOf(0) }

    val metaData = remember(repoUrl) {
        AppCache.cachedRepos.getOrPut(repoUrl) {
            RepoMetaData(repoUrl, requestQueue).apply {
                refreshNetwork()
            }
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

            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(end = 48.dp)
            ) {
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
                        MetaDataState.Loaded -> LoadedTracker(metaData, refreshTrigger.intValue)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = {
                    metaData.refreshNetwork()
                    refreshTrigger.intValue++
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