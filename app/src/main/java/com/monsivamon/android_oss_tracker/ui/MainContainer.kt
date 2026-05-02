package com.monsivamon.android_oss_tracker.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import kotlinx.coroutines.launch

/**
 * Describes a top-level destination in the bottom navigation bar.
 */
enum class AppDestination(val title: String, val icon: ImageVector) {
    APPS("Apps", Icons.AutoMirrored.Filled.List),
    NEW("New", Icons.Default.Add),
    HISTORY("History", Icons.Default.DateRange),
    SETTINGS("Settings", Icons.Default.Settings)
}

/**
 * Root composable that assembles the gesture-driven pager and the bottom
 * navigation bar into the primary application shell.
 *
 * Navigation state is owned by [rememberPagerState] which synchronises
 * the [HorizontalPager] offset with the selected indicator in the
 * [NavigationBar].
 */
@Composable
fun MainContainer() {
    val destinations = AppDestination.entries.toTypedArray()
    val pagerState = rememberPagerState(pageCount = { destinations.size })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        icon = {
                            Icon(destination.icon, contentDescription = destination.title)
                        },
                        label = { Text(destination.title) },
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { page ->
            when (page) {
                0 -> AppsScreen()
                1 -> NewTrackerScreen()
                2 -> HistoryScreen()
                3 -> SettingsScreen()
            }
        }
    }
}