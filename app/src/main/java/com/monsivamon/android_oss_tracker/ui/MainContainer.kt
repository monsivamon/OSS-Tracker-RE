package com.monsivamon.android_oss_tracker.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.monsivamon.android_oss_tracker.util.AppSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Top‑level destinations shown in the bottom navigation bar.
 */
enum class AppDestination(val title: String, val icon: ImageVector) {
    APPS("Apps", Icons.AutoMirrored.Filled.List),
    NEW("New", Icons.Default.Add),
    HISTORY("History", Icons.Default.DateRange),
    SETTINGS("Settings", Icons.Default.Settings)
}

/**
 * Root composable that owns the [HorizontalPager] and the bottom bar.
 *
 * The *New* and *History* tabs can be hidden via [AppSettings].  When a
 * tab is hidden a dedicated **+Add** button on the Apps screen temporarily
 * reveals the *New* tab.  Swipe is disabled while the temporary tab is
 * visible to avoid accidental navigation.
 */
@Composable
fun MainContainer() {
    val showNew = AppSettings.showNewTab
    val showHistory = AppSettings.showHistoryTab
    // Bottom bar is only shown when at least one optional tab is active.
    val showBottomBar = showNew || showHistory

    // ── Temporary New tab (triggered by +Add) ────────────
    var newTabCalledFromAdd by remember { mutableStateOf(false) }
    var showTemporaryNewTab by remember { mutableStateOf(false) }
    var scrollToNewInProgress by remember { mutableStateOf(false) }

    /** The currently visible destinations, including any temporary tab. */
    val visibleDestinations = remember(showNew, showHistory, showTemporaryNewTab) {
        buildList {
            add(AppDestination.APPS)
            if (showNew || showTemporaryNewTab) add(AppDestination.NEW)
            if (showHistory) add(AppDestination.HISTORY)
            add(AppDestination.SETTINGS)
        }
    }

    /** The destination the user is currently seeing. */
    var currentActiveDestination by remember { mutableStateOf(AppDestination.APPS) }
    val coroutineScope = rememberCoroutineScope()
    val activity = LocalContext.current as? Activity
    var showExitDialog by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(
        pageCount = { visibleDestinations.size }
    )

    /**
     * Performs a smooth scroll to [targetIndex], inserting a pseudo‑warp
     * when the distance is more than one page so that [HorizontalPager]
     * can animate naturally over a long jump.
     */
    suspend fun performSmoothScroll(targetIndex: Int) {
        if (targetIndex < 0 || targetIndex >= visibleDestinations.size) return
        val current = pagerState.currentPage
        if (kotlin.math.abs(current - targetIndex) > 1) {
            val warpIndex = if (current > targetIndex) targetIndex + 1 else targetIndex - 1
            pagerState.scrollToPage(warpIndex)
        }
        pagerState.animateScrollToPage(targetIndex)
    }

    // Synchronise the pager when the tab list changes.
    LaunchedEffect(visibleDestinations) {
        val syncIndex = visibleDestinations.indexOf(currentActiveDestination)
        if (syncIndex >= 0 && !scrollToNewInProgress) {
            pagerState.scrollToPage(syncIndex)
        } else if (scrollToNewInProgress) {
            val newIndex = visibleDestinations.indexOf(AppDestination.NEW)
            if (newIndex >= 0) {
                performSmoothScroll(newIndex)
                scrollToNewInProgress = false
            }
        }
    }

    // Keep currentActiveDestination in sync with the user's swipes.
    LaunchedEffect(pagerState.currentPage) {
        visibleDestinations.getOrNull(pagerState.currentPage)?.let {
            currentActiveDestination = it
        }
    }

    // ── Callbacks passed to child screens ──────────────

    val onNavigateToNewFromApps: () -> Unit = {
        if (!showNew) {
            scrollToNewInProgress = true
            newTabCalledFromAdd = true
            showTemporaryNewTab = true
        }
    }

    val onNewTrackerAdded: () -> Unit = {
        if (!showNew) {
            coroutineScope.launch {
                val appsIndex = visibleDestinations.indexOf(AppDestination.APPS)
                performSmoothScroll(appsIndex)
                delay(350L)
                newTabCalledFromAdd = false
                showTemporaryNewTab = false
            }
        }
    }

    // ── System back gesture ────────────────────────────
    BackHandler(enabled = true) {
        if (currentActiveDestination != AppDestination.APPS) {
            val appsIndex = visibleDestinations.indexOf(AppDestination.APPS)
            if (appsIndex >= 0) {
                coroutineScope.launch {
                    performSmoothScroll(appsIndex)
                    if (newTabCalledFromAdd) {
                        delay(350L)
                        newTabCalledFromAdd = false
                        showTemporaryNewTab = false
                    }
                }
            }
        } else {
            showExitDialog = true
        }
    }

    // Clean up the temporary New tab when the user leaves it.
    LaunchedEffect(newTabCalledFromAdd, pagerState.currentPage) {
        if (newTabCalledFromAdd && !scrollToNewInProgress) {
            if (visibleDestinations.getOrNull(pagerState.currentPage) != AppDestination.NEW) {
                newTabCalledFromAdd = false
                showTemporaryNewTab = false
            }
        }
    }

    // ── Exit confirmation dialog ───────────────────────
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Confirm Exit") },
            text = { Text("Are you sure you want to exit?") },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false; activity?.finish() }) {
                    Text("OK", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // ── UI layout ──────────────────────────────────────
    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                NavigationBar {
                    visibleDestinations.forEachIndexed { _, dest ->
                        // Don't show a temporary New tab in the bottom bar.
                        val isTemporaryNew = dest == AppDestination.NEW && showTemporaryNewTab && !showNew
                        if (!isTemporaryNew) {
                            val targetIndex = visibleDestinations.indexOf(dest)
                            NavigationBarItem(
                                icon = { Icon(dest.icon, null) },
                                label = { Text(dest.title) },
                                selected = currentActiveDestination == dest,
                                onClick = { coroutineScope.launch { performSmoothScroll(targetIndex) } }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = showBottomBar && !newTabCalledFromAdd,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) { page ->
            val actual = visibleDestinations.getOrNull(page)

            // Mask the page if an animation is still in flight so that
            // the content does not flicker.
            val destinationToRender = if (
                page == pagerState.currentPage &&
                actual != currentActiveDestination &&
                !scrollToNewInProgress &&
                !pagerState.isScrollInProgress
            ) {
                currentActiveDestination
            } else {
                actual ?: AppDestination.APPS
            }

            when (destinationToRender) {
                AppDestination.APPS -> AppsScreen(
                    onNavigateToNew = if (!showNew) { { onNavigateToNewFromApps() } } else null,
                    onNavigateToSettings = {
                        val idx = visibleDestinations.indexOf(AppDestination.SETTINGS)
                        coroutineScope.launch { performSmoothScroll(idx) }
                    }
                )
                AppDestination.NEW -> NewTrackerScreen(
                    onNewTrackerAdded = onNewTrackerAdded,
                    onNavigateToApps = {
                        coroutineScope.launch {
                            val idx = visibleDestinations.indexOf(AppDestination.APPS)
                            performSmoothScroll(idx)
                            if (newTabCalledFromAdd) {
                                delay(350L)
                                newTabCalledFromAdd = false
                                showTemporaryNewTab = false
                            }
                        }
                    }
                )
                AppDestination.HISTORY -> HistoryScreen()
                AppDestination.SETTINGS -> SettingsScreen(
                    onNavigateToApps = {
                        val idx = visibleDestinations.indexOf(AppDestination.APPS)
                        coroutineScope.launch { performSmoothScroll(idx) }
                    }
                )
            }
        }
    }
}