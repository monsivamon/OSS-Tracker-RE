package com.monsivamon.android_oss_tracker.ui

import android.content.Context
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monsivamon.android_oss_tracker.PersistentState
import com.monsivamon.android_oss_tracker.repo.AppCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Primary screen listing every tracked repository.
 *
 * Provides pull‑to‑refresh for a full metadata reload, real‑time text
 * filtering, drag‑free reordering via ▲/▼ buttons, and quick shortcuts
 * to the Settings and Add screens.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    onNavigateToNew: (() -> Unit)? = null,
    onNavigateToSettings: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences(PersistentState.STATE_FILENAME, Context.MODE_PRIVATE) }
    val repoUrls = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        PersistentState.initializeDefaultTrackers(prefs)
        val saved = withContext(Dispatchers.IO) { PersistentState.getSavedTrackers(prefs) }
        repoUrls.clear()
        repoUrls.addAll(saved)
    }

    var searchQuery by remember { mutableStateOf("") }

    val onDelete: (String, String) -> Unit = { _, repo ->
        PersistentState.removeTracker(prefs, repo)
        repoUrls.remove(repo)
        AppCache.cachedRepos.remove(repo)
    }

    // ── Reordering helpers ──────────────────────────────────────
    // Replace individual remove/add with a batch copy so that
    // Compose's `animateItemPlacement` receives a clean list delta and
    // can produce a smooth translation animation.
    val onMoveUp: (String) -> Unit = { repo ->
        val idx = repoUrls.indexOf(repo)
        if (idx > 0) {
            scope.launch {
                delay(100L) // let the UI settle before modifying the list
                val newList = repoUrls.toMutableList()
                val temp = newList[idx]
                newList[idx] = newList[idx - 1]
                newList[idx - 1] = temp
                repoUrls.clear()
                repoUrls.addAll(newList)
                PersistentState.saveOrder(prefs, repoUrls.toList())
            }
        }
    }

    val onMoveDown: (String) -> Unit = { repo ->
        val idx = repoUrls.indexOf(repo)
        if (idx < repoUrls.lastIndex) {
            scope.launch {
                delay(100L)
                val newList = repoUrls.toMutableList()
                val temp = newList[idx]
                newList[idx] = newList[idx + 1]
                newList[idx + 1] = temp
                repoUrls.clear()
                repoUrls.addAll(newList)
                PersistentState.saveOrder(prefs, repoUrls.toList())
            }
        }
    }

    // ── Pull‑to‑refresh state ─────────────────────────────────
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            for (url in repoUrls) {
                AppCache.cachedRepos[url]?.refreshNetwork()
            }
            delay(500)
            isRefreshing = false
        }
    }

    val filtered = if (searchQuery.isBlank()) repoUrls.toList() else repoUrls.filter { it.contains(searchQuery, ignoreCase = true) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { isRefreshing = true },
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        indicator = {
            // Custom indicator that draws the standard pull‑to‑refresh
            // spinner and adds a contextual label (“Pull to refresh”
            // or “Checking for updates...”).
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
                if (pullRefreshState.distanceFraction > 0f || isRefreshing) {
                    val statusText = if (isRefreshing) "Checking for updates..." else "Pull to refresh"
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = 48.dp)
                    )
                }
            }
        }
    ) {
        Column(Modifier.fillMaxSize()) {

            // ── Header row (Settings gear – title – Add button) ─
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left slot – settings shortcut
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Central title
                Text(
                    text = "Application Trackers",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Right slot – add‑tracker shortcut (shown only when
                // the New tab is hidden)
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (onNavigateToNew != null) {
                        IconButton(onClick = onNavigateToNew) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Search / filter bar with clear button
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
                            Icon(Icons.Default.Close, "Clear filter")
                        }
                    }
                }
            )

            // ── Content area ─────────────────────────────────
            if (filtered.isEmpty()) {
                Text(
                    "No matching repositories.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                )
            } else {
                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    // Tiny anchor so that Compose always knows where
                    // the top of the list is – improves animation
                    // consistency when reordering the first item.
                    item(key = "TopAnchor") { Spacer(modifier = Modifier.height(1.dp)) }

                    items(filtered, key = { it }) { url ->
                        RenderItem(
                            repoUrl = url,
                            onDelete = onDelete,
                            onMoveUp = onMoveUp,
                            onMoveDown = onMoveDown,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.animateItem(placementSpec = tween(300))
                        )
                    }

                    item(key = "BottomSpacer") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}