package com.monsivamon.android_oss_tracker.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monsivamon.android_oss_tracker.PersistentState
import com.monsivamon.android_oss_tracker.repo.AppCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Main screen presenting the list of repositories the user is currently tracking.
 *
 * All heavy I/O (reading/writing [SharedPreferences]) is offloaded to
 * [Dispatchers.IO] so that the UI thread is never blocked, which prevents the
 * notorious `userfaultfd` timeouts seen on Android 14 with CMC GC.
 */
@Composable
fun AppsScreen() {
    val ctx = LocalContext.current
    val sharedPreferences = remember {
        ctx.getSharedPreferences(PersistentState.STATE_FILENAME, Context.MODE_PRIVATE)
    }

    val verticalScroll = rememberScrollState()
    val repoUrls = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        val saved = withContext(Dispatchers.IO) {
            PersistentState.getSavedTrackers(sharedPreferences)
        }
        repoUrls.clear()
        repoUrls.addAll(saved)
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
            val hasSavedData = remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                hasSavedData.value = withContext(Dispatchers.IO) {
                    PersistentState.getSavedTrackers(sharedPreferences).isNotEmpty()
                }
            }
            if (hasSavedData.value) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 32.dp)
                )
            } else {
                Text(
                    text = "You aren't tracking any application repositories.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp)
                )
            }
        } else {
            repoUrls.forEach { url ->
                RenderItem(
                    repoUrl = url,
                    onDelete = onTrackerDelete
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}