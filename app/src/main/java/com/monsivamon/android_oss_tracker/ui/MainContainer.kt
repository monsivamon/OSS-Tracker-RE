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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.monsivamon.android_oss_tracker.util.AppSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Represents the four primary destinations within the app.
 *
 * Each destination has a title and an associated icon used in the bottom
 * navigation bar.
 */
enum class AppDestination(val title: String, val icon: ImageVector) {
    APPS("Apps", Icons.AutoMirrored.Filled.List),
    NEW("New", Icons.Default.Add),
    HISTORY("History", Icons.Default.DateRange),
    SETTINGS("Settings", Icons.Default.Settings)
}

/**
 * Root container that manages the horizontal pager for the app's main tabs.
 *
 * It dynamically shows or hides the "New" and "History" tabs based on user
 * preferences, handles temporary display of the New tab when adding a tracker
 * from the Apps screen, and provides a confirmation dialog when the user
 * presses back on the primary Apps screen.
 */
@Suppress("UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@Composable
fun MainContainer() {
    val showNew = AppSettings.showNewTab
    val showHistory = AppSettings.showHistoryTab
    val showBottomBar = showNew || showHistory

    var newTabCalledFromAdd by remember { mutableStateOf(false) }
    var showTemporaryNewTab by remember { mutableStateOf(false) }
    var scrollToNewInProgress by remember { mutableStateOf(false) }

    // Build the list of visible destinations based on current settings and
    // whether a temporary New tab is needed.
    val visibleDestinations = remember(showNew, showHistory, showTemporaryNewTab) {
        buildList {
            add(AppDestination.APPS)
            if (showNew || showTemporaryNewTab) add(AppDestination.NEW)
            if (showHistory) add(AppDestination.HISTORY)
            add(AppDestination.SETTINGS)
        }
    }

    var currentActiveDestination by remember { mutableStateOf(AppDestination.APPS) }
    val coroutineScope = rememberCoroutineScope()
    val activity = LocalContext.current as? Activity
    var showExitDialog by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(
        pageCount = { visibleDestinations.size }
    )

    /**
     * Smoothly scrolls to a target page, performing a warp if the distance
     * between the current page and the target is greater than one step.
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

    // Synchronise the pager position whenever the set of visible destinations changes.
    LaunchedEffect(visibleDestinations) {
        // Determine the destination that is currently active based on the pager's page
        val currentDest = visibleDestinations.getOrNull(pagerState.currentPage) ?: currentActiveDestination
        val syncIndex = visibleDestinations.indexOf(currentDest)

        if (syncIndex >= 0 && !scrollToNewInProgress) {
            // Keep the same destination after a tab visibility change
            delay(50)
            pagerState.scrollToPage(syncIndex)
        } else if (scrollToNewInProgress) {
            val newIndex = visibleDestinations.indexOf(AppDestination.NEW)
            if (newIndex >= 0) {
                performSmoothScroll(newIndex)
                scrollToNewInProgress = false
            }
        }
    }

    // Update the current destination whenever the pager settles on a new page
    LaunchedEffect(pagerState.currentPage) {
        visibleDestinations.getOrNull(pagerState.currentPage)?.let {
            currentActiveDestination = it
        }
    }

    // Called when the user taps "Add Tracker" from the Apps screen.
    // If the New tab is hidden, it triggers a temporary tab.
    val onNavigateToNewFromApps: () -> Unit = {
        if (!showNew) {
            scrollToNewInProgress = true
            newTabCalledFromAdd = true
            showTemporaryNewTab = true
        }
    }

    // Called after a new tracker has been added.
    // Removes the temporary New tab and scrolls back to the Apps tab.
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

    // Handle the system back gesture.
    // On any screen other than Apps it navigates back to Apps.
    // On Apps it shows an exit confirmation dialog.
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

    // Clean up temporary tab state if the user navigates away without adding
    LaunchedEffect(newTabCalledFromAdd, pagerState.currentPage) {
        if (newTabCalledFromAdd && !scrollToNewInProgress) {
            if (visibleDestinations.getOrNull(pagerState.currentPage) != AppDestination.NEW) {
                newTabCalledFromAdd = false
                showTemporaryNewTab = false
            }
        }
    }

    // Exit confirmation dialog
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

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            // Show the bottom bar only when at least one optional tab is visible
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                NavigationBar(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    tonalElevation = 0.dp
                ) {
                    visibleDestinations.forEachIndexed { _, dest ->
                        val isTemporaryNew = dest == AppDestination.NEW && showTemporaryNewTab && !showNew
                        if (!isTemporaryNew) {
                            val targetIndex = visibleDestinations.indexOf(dest)
                            NavigationBarItem(
                                icon = { Icon(dest.icon, null) },
                                label = { Text(dest.title) },
                                selected = currentActiveDestination == dest,
                                onClick = { coroutineScope.launch { performSmoothScroll(targetIndex) } },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Color.White.copy(alpha = 0.3f),
                                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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

            // Use the current active destination when the pager is still settling,
            // to prevent visual flashes
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