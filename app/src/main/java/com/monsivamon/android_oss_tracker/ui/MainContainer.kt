package com.monsivamon.android_oss_tracker.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.launch

/**
 * Describes a single top-level destination in the bottom navigation bar.
 *
 * Each entry maps to a page inside the [HorizontalPager] and defines both
 * the visual label and the icon shown in the [NavigationBar].
 */
enum class AppDestination(val title: String, val icon: ImageVector) {
    APPS("Apps", Icons.AutoMirrored.Filled.List),
    NEW("New", Icons.Default.Add),
    SETTINGS("Settings", Icons.Default.Settings)
}

/**
 * Root composable that assembles the gesture-driven pager and the bottom
 * navigation bar into the primary application shell.
 *
 * ## Architecture
 * - Navigation state is owned by [rememberPagerState] which synchronises
 *   the [HorizontalPager] offset with the selected indicator in the
 *   [NavigationBar].
 * - Tapping a bottom-bar item animates the pager smoothly via
 *   [pagerState.animateScrollToPage].
 * - No shared dependencies ([SharedPreferences], [RequestQueue]) are passed
 *   through this container; each screen accesses them through singletons or
 *   [LocalContext] when necessary.
 *
 * ## Performance
 * [HorizontalPager] keeps non-visible pages in a dormant state, so only the
 * currently visible screen actively participates in composition.  Combined
 * with the removal of parameter injection this virtually eliminates
 * unnecessary recompositions.
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
                2 -> SettingsScreen()
            }
        }
    }
}