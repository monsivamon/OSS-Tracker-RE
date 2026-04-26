package com.monsivamon.android_oss_tracker.ui

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.volley.RequestQueue
import com.monsivamon.android_oss_tracker.PersistentState
import com.monsivamon.android_oss_tracker.repo.AppCache
import com.monsivamon.android_oss_tracker.repo.MetaDataState
import com.monsivamon.android_oss_tracker.repo.RepoMetaData

/**
 * Displays the main screen containing the list of tracked OSS repositories.
 * Manages the scroll state, initialization of saved trackers, and deletion events.
 *
 * @param sharedPreferences The SharedPreferences instance for persisting tracker data.
 * @param requestQueue The Volley RequestQueue for handling network operations.
 */
@Composable
fun AppsScreen(
    sharedPreferences: SharedPreferences,
    requestQueue: RequestQueue
) {
    val ctx = LocalContext.current
    val verticalScroll = rememberScrollState()
    val repoUrls = remember {
        val set = mutableStateListOf<String>()
        set.addAll(PersistentState.getSavedTrackers(sharedPreferences))
        set
    }

    val onTrackerDelete = { appName: String, repo: String ->
        PersistentState.removeTracker(ctx, sharedPreferences, appName, repo)
        repoUrls.remove(repo)
        AppCache.cachedRepos.remove(repo)
        Unit
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(verticalScroll)
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Application Trackers",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        )

        if (repoUrls.isEmpty()) {
            Text(
                text = "You aren't tracking any application repositories.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp)
            )
        } else {
            repoUrls.forEach { url ->
                RenderItem(
                    requestQueue = requestQueue,
                    repoUrl = url,
                    onDelete = onTrackerDelete
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Displays a placeholder UI for repositories that cannot be parsed or are unsupported.
 */
@Composable
fun UnsupportedTracker(metaData: RepoMetaData) {
    Text(
        text = "Unsupported or unparseable repository:\n${metaData.repoUrl}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp)
    )
}

/**
 * Displays a loading state UI while fetching metadata for a repository.
 */
@Composable
fun LoadingTracker(metaData: RepoMetaData) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(16.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = "Loading ${metaData.appName}...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Displays an error state UI, providing an expandable section to view detailed error logs.
 */
@Composable
fun ErroredTracker(metaData: RepoMetaData) {
    val showErrors = remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = metaData.repoUrl,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 48.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { showErrors.value = !showErrors.value },
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = if (showErrors.value) "Hide Details" else "View Errors")
        }

        if (showErrors.value) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    metaData.errors.forEach { errorMsg ->
                        Text(
                            text = "• $errorMsg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders the comprehensive UI for a successfully loaded tracker.
 * Includes the application name, version badge, release date, and interactive download buttons.
 */
@Composable
fun LoadedTracker(metaData: RepoMetaData) {
    val ctx = LocalContext.current

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)) {

        Text(
            text = metaData.appName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
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
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val assets = metaData.latestAssets.value
        if (assets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(8.dp))

            assets.forEach { asset ->
                FilledTonalButton(
                    onClick = {
                        val request = android.app.DownloadManager.Request(Uri.parse(asset.downloadUrl)).apply {
                            setTitle(asset.name)
                            setDescription("Downloading from GitHub")
                            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, asset.name)
                        }
                        val dm = ctx.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                        dm.enqueue(request)
                        android.widget.Toast.makeText(ctx, "Download started: ${asset.name}", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Download: ${asset.name}",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

/**
 * Represents a single interactive card item in the tracker list.
 * Handles data caching, network refresh triggers, and user routing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderItem(
    requestQueue: RequestQueue,
    repoUrl: String,
    onDelete: (String, String) -> Unit
) {
    val ctx = LocalContext.current

    val metaData = remember(repoUrl) {
        AppCache.cachedRepos.getOrPut(repoUrl) {
            RepoMetaData(repoUrl, requestQueue).apply {
                refreshNetwork()
            }
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        onClick = {
            val link = metaData.latestVersionUrl.value ?: metaData.repoUrl
            val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            ctx.startActivity(urlIntent)
        }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {

            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(end = 48.dp)) {
                if (metaData.latestVersion.value != null) {
                    LoadedTracker(metaData)
                } else {
                    when (metaData.state.value) {
                        MetaDataState.Unsupported -> UnsupportedTracker(metaData)
                        MetaDataState.Loading -> LoadingTracker(metaData)
                        MetaDataState.Errored -> ErroredTracker(metaData)
                        MetaDataState.Loaded -> LoadedTracker(metaData)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = { metaData.refreshNetwork() }) {
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