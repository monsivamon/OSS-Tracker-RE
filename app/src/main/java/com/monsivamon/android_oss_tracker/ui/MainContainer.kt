package com.monsivamon.android_oss_tracker.ui

import android.util.Log
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
import com.monsivamon.android_oss_tracker.util.AppSettings
import kotlinx.coroutines.launch

/**
 * Top‑level destinations for the bottom navigation bar.
 */
enum class AppDestination(val title: String, val icon: ImageVector) {
    APPS("Apps", Icons.AutoMirrored.Filled.List),
    NEW("New", Icons.Default.Add),
    HISTORY("History", Icons.Default.DateRange),
    SETTINGS("Settings", Icons.Default.Settings)
}

/**
 * Root composable that manages the swipeable [HorizontalPager] and the
 * bottom navigation bar.
 *
 * The visibility of the *New* and *History* tabs is controlled via
 * [AppSettings.showNewTab] and [AppSettings.showHistoryTab].  When a
 * tab is hidden the corresponding bottom‑bar item disappears, and the
 * user cannot swipe into it.  A dedicated **+Add** button on the Apps
 * screen temporarily reveals the *New* tab without toggling the
 * persistent setting.
 */
@Composable
fun MainContainer() {
    // ── Settings‑driven tab visibility ──────────────────────────
    val showNew = AppSettings.showNewTab
    val showHistory = AppSettings.showHistoryTab

    // ── Temporary New tab (triggered by +Add button) ────────────
    var newTabCalledFromAdd by remember { mutableStateOf(false) }
    var showTemporaryNewTab by remember { mutableStateOf(false) }
    var scrollToNewInProgress by remember { mutableStateOf(false) }

    /** The list of destinations that are currently visible. */
    val visibleDestinations = remember(showNew, showHistory, showTemporaryNewTab) {
        buildList {
            add(AppDestination.APPS)
            if (showNew || showTemporaryNewTab) add(AppDestination.NEW)
            if (showHistory) add(AppDestination.HISTORY)
            add(AppDestination.SETTINGS)
        }
    }

    // Keeps track of the last destination so that we can restore it
    // after the tab list changes.
    var previousDestination by remember { mutableStateOf(AppDestination.APPS) }

    val coroutineScope = rememberCoroutineScope()

    // ── Callbacks passed to child screens ──────────────────────

    val onNavigateToNewFromApps: () -> Unit = {
        if (!showNew) {
            newTabCalledFromAdd = true
            showTemporaryNewTab = true
        }
    }

    val onNewTrackerAdded: () -> Unit = {
        if (!showNew) {
            newTabCalledFromAdd = false
            showTemporaryNewTab = false
        }
    }

    // ── Re‑create the pager when the tab list changes ──────────
    //    so that the currently‑selected tab survives the transition
    //    without any visual flicker.
    key(visibleDestinations) {
        val initialPage = visibleDestinations.indexOf(previousDestination)
            .coerceIn(0, visibleDestinations.size - 1)

        val pagerState = rememberPagerState(
            pageCount = { visibleDestinations.size },
            initialPage = initialPage
        )

        // Keep previousDestination up‑to‑date with user navigation.
        LaunchedEffect(pagerState.currentPage) {
            previousDestination = visibleDestinations.getOrNull(pagerState.currentPage)
                ?: AppDestination.APPS
        }

        // Animate to the New tab when it is added via the +Add button.
        LaunchedEffect(showTemporaryNewTab) {
            if (showTemporaryNewTab && !showNew) {
                scrollToNewInProgress = true
                val newIndex = visibleDestinations.indexOf(AppDestination.NEW)
                if (newIndex >= 0) {
                    pagerState.animateScrollToPage(newIndex)
                }
            }
        }

        // Clear the animation flag once we land on the New tab.
        LaunchedEffect(pagerState.currentPage) {
            if (scrollToNewInProgress &&
                visibleDestinations.getOrNull(pagerState.currentPage) == AppDestination.NEW) {
                scrollToNewInProgress = false
            }
        }

        // If the user leaves the temporary New tab without adding a
        // repository, automatically hide it and return to Apps.
        LaunchedEffect(newTabCalledFromAdd, pagerState.currentPage) {
            if (newTabCalledFromAdd && !scrollToNewInProgress) {
                val currentDest = visibleDestinations.getOrNull(pagerState.currentPage)
                if (currentDest != AppDestination.NEW) {
                    newTabCalledFromAdd = false
                    showTemporaryNewTab = false
                }
            }
        }

        // ── UI ──────────────────────────────────────────────────
        Scaffold(
            bottomBar = {
                NavigationBar {
                    visibleDestinations.forEachIndexed { _, dest ->
                        // Don't show a temporary New tab in the bottom bar.
                        val isTemporaryNew = dest == AppDestination.NEW &&
                                showTemporaryNewTab && !showNew
                        if (!isTemporaryNew) {
                            val targetIndex = visibleDestinations.indexOf(dest)
                            NavigationBarItem(
                                icon = { Icon(dest.icon, contentDescription = dest.title) },
                                label = { Text(dest.title) },
                                selected = pagerState.currentPage == targetIndex,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(targetIndex)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            // Swipe is disabled while the temporary New tab is shown.
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !newTabCalledFromAdd,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) { page ->
                when (visibleDestinations[page]) {
                    AppDestination.APPS -> AppsScreen(
                        onNavigateToNew = if (!showNew) {
                            { onNavigateToNewFromApps() }
                        } else null
                    )
                    AppDestination.NEW -> NewTrackerScreen(
                        onNewTrackerAdded = onNewTrackerAdded
                    )
                    AppDestination.HISTORY -> HistoryScreen()
                    AppDestination.SETTINGS -> SettingsScreen()
                }
            }
        }
    }
}