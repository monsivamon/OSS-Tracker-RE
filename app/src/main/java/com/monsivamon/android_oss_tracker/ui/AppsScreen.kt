package com.monsivamon.android_oss_tracker.ui

import android.content.Context
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
 * Primary screen listing every tracked repository.
 *
 * A ⊕ button in the header opens the New‑Tracker screen even when the New
 * tab is hidden.  Each card can be reordered with arrow buttons, and
 * position changes are smoothly animated.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppsScreen(onNavigateToNew: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences(PersistentState.STATE_FILENAME, Context.MODE_PRIVATE) }
    val repoUrls = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        PersistentState.initializeDefaultTrackers(prefs)
        val saved = withContext(Dispatchers.IO) { PersistentState.getSavedTrackers(prefs) }
        repoUrls.clear(); repoUrls.addAll(saved)
    }

    var searchQuery by remember { mutableStateOf("") }

    val onDelete: (String, String) -> Unit = { _, repo ->
        PersistentState.removeTracker(prefs, repo)
        repoUrls.remove(repo); AppCache.cachedRepos.remove(repo)
    }
    val onMoveUp: (String) -> Unit = { repo ->
        val idx = repoUrls.indexOf(repo)
        if (idx > 0) { repoUrls.removeAt(idx); repoUrls.add(idx - 1, repo); PersistentState.saveOrder(prefs, repoUrls.toList()) }
    }
    val onMoveDown: (String) -> Unit = { repo ->
        val idx = repoUrls.indexOf(repo)
        if (idx < repoUrls.lastIndex) { repoUrls.removeAt(idx); repoUrls.add(idx + 1, repo); PersistentState.saveOrder(prefs, repoUrls.toList()) }
    }

    val filtered = if (searchQuery.isBlank()) repoUrls.toList() else repoUrls.filter { it.contains(searchQuery, ignoreCase = true) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Header
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text("Application Trackers", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.wrapContentWidth())
            if (onNavigateToNew != null) {
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = onNavigateToNew, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Add", style = MaterialTheme.typography.labelLarge)
                }
            } else { Spacer(Modifier.weight(1f)) }
        }
        // Search
        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), placeholder = { Text("Filter repositories…") }, singleLine = true, shape = RoundedCornerShape(12.dp), trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, "Clear filter") } })
        // List
        if (filtered.isEmpty()) Text("No matching repositories.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 32.dp))
        else {
            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { it }) { url ->
                    RenderItem(repoUrl = url, onDelete = onDelete, onMoveUp = onMoveUp, onMoveDown = onMoveDown, modifier = Modifier.animateItem(placementSpec = tween(300)))
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}