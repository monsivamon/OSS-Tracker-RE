package com.monsivamon.android_oss_tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.android.volley.RequestQueue
import com.android.volley.toolbox.BasicNetwork
import com.android.volley.toolbox.DiskBasedCache
import com.android.volley.toolbox.HurlStack
import com.monsivamon.android_oss_tracker.ui.BottomNavigationBar
import com.monsivamon.android_oss_tracker.ui.NavHostContainer
import com.monsivamon.android_oss_tracker.ui.theme.AndroidossreleasetrackerTheme

/**
 * The main entry point of the application.
 * Responsible for initializing core services such as the Volley network queue,
 * accessing SharedPreferences, and setting up the Jetpack Compose UI scaffold and navigation.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Volley RequestQueue with a 1MB disk cache and basic network stack
        val cache = DiskBasedCache(cacheDir, 1024 * 1024)
        val network = BasicNetwork(HurlStack())
        val requestQueue = RequestQueue(cache, network).apply {
            start()
        }

        setContent {
            AndroidossreleasetrackerTheme {

                // Set up navigation and persistent storage dependencies
                val navController = rememberNavController()
                val sharedPreferences = getSharedPreferences(PersistentState.STATE_FILENAME, MODE_PRIVATE)

                // Main application surface adopting the Material 3 background color
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Application scaffold containing the bottom navigation and main content area
                    Scaffold(
                        bottomBar = { BottomNavigationBar(navController = navController) }
                    ) { padding ->
                        NavHostContainer(
                            navController = navController,
                            padding = padding,
                            sharedPreferences = sharedPreferences,
                            requestQueue = requestQueue
                        )
                    }
                }
            }
        }
    }
}