package com.monsivamon.android_oss_tracker.ui

import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.android.volley.RequestQueue

/**
 * Represents a single destination item within the bottom navigation bar.
 *
 * @property label The display name of the navigation item.
 * @property icon The Material Design vector icon associated with the route.
 * @property route The unique routing key used by the NavController.
 */
data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

/**
 * Defines the static configuration and ordering of the bottom navigation items.
 */
object Constants {
    val BottomNavItems = listOf(
        BottomNavItem(
            label = "Apps",
            icon = Icons.Filled.Home,
            route = "apps"
        ),
        BottomNavItem(
            label = "New",
            icon = Icons.Filled.Add,
            route = "new"
        ),
        BottomNavItem(
            label = "Settings",
            icon = Icons.Filled.Settings,
            route = "settings"
        )
    )
}

/**
 * Manages the primary navigation graph of the application.
 * Defines the routing hierarchy between the Apps, New Tracker, and Settings screens.
 *
 * @param navController The navigation controller managing the back stack.
 * @param padding The padding values provided by the parent Scaffold to prevent content overlap.
 * @param sharedPreferences The SharedPreferences instance for data persistence.
 * @param requestQueue The Volley RequestQueue for handling network operations.
 */
@Composable
fun NavHostContainer(
    navController: NavHostController,
    padding: PaddingValues,
    sharedPreferences: SharedPreferences,
    requestQueue: RequestQueue
) {
    NavHost(
        navController = navController,
        startDestination = "apps",
        modifier = Modifier.padding(paddingValues = padding)
    ) {
        composable("apps") {
            AppsScreen(sharedPreferences, requestQueue)
        }
        composable("new") {
            NewTrackerScreen(sharedPreferences, requestQueue)
        }
        composable("settings") {
            SettingsScreen()
        }
    }
}

/**
 * Displays the Material Design 3 bottom navigation bar.
 * Highlights the currently active route and handles user navigation events.
 *
 * @param navController The navigation controller used to route between destinations.
 */
@Composable
fun BottomNavigationBar(navController: NavHostController) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        Constants.BottomNavItems.forEach { navItem ->
            NavigationBarItem(
                selected = currentRoute == navItem.route,
                onClick = {
                    navController.navigate(navItem.route)
                },
                icon = {
                    Icon(
                        imageVector = navItem.icon,
                        contentDescription = navItem.label
                    )
                },
                label = {
                    Text(text = navItem.label)
                },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}