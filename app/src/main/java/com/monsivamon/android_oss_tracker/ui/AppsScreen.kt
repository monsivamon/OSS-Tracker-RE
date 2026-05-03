package com.monsivamon.android_oss_tracker.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
 * Primary screen listing every repository the user is currently tracking.
 *
 * A real‑time text filter with a clear button allows rapid narrowing of the
 * list, and every tracked item is rendered as a card that supports refresh
 * and delete actions.
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

    var searchQuery by remember { mutableStateOf("") }

    val onTrackerDelete = { appName: String, repo: String ->
        PersistentState.removeTracker(ctx, sharedPreferences, appName, repo)
        repoUrls.remove(repo)
        AppCache.cachedRepos.remove(repo)
        Unit
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(verticalScroll).padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Application Trackers",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
        )

        // Search / filter with clear button
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            placeholder = { Text("Filter repositories…") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear filter")
                    }
                }
            }
        )

        val filteredUrls = if (searchQuery.isBlank()) repoUrls
        else repoUrls.filter { it.contains(searchQuery, ignoreCase = true) }

        if (filteredUrls.isEmpty()) {
            Text(
                text = "No matching repositories.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
            )
        } else {
            filteredUrls.forEach { url ->
                RenderItem(repoUrl = url, onDelete = onTrackerDelete)
                Spacer(modifier = Modifier.height(12.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}