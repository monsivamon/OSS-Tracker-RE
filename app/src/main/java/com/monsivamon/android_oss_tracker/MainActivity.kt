package com.monsivamon.android_oss_tracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.monsivamon.android_oss_tracker.ui.MainContainer
import com.monsivamon.android_oss_tracker.ui.theme.AndroidossreleasetrackerTheme
import com.monsivamon.android_oss_tracker.util.AppSettings
import com.monsivamon.android_oss_tracker.util.ThemeMode
import com.monsivamon.android_oss_tracker.util.ThemeState

/**
 * The main activity of the application.
 *
 * Enables edge-to-edge display, sets up the Compose-based UI with the app's theme,
 * and manages a gradient background that adapts to the selected theme mode.
 * Also triggers the runtime notification permission request on supported Android versions.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Delete all accumulated APK files in the app's internal directories on startup
        Thread {
            try {
                val targetDirs = listOf(
                    getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS),
                    cacheDir
                )
                targetDirs.forEach { dir ->
                    dir?.listFiles()?.forEach { file ->
                        if (file.name.endsWith(".apk", ignoreCase = true)) {
                            file.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()

        setContent {
            AndroidossreleasetrackerTheme {
                RequestNotificationPermission()

                // Determine whether the current theme is in dark mode
                val isDark = when (ThemeState.currentMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }

                // Pastel gradient palette definitions used for the background
                val pastelPalettes = remember {
                    listOf(
                        listOf(Color(0xFFA18CD1), Color(0xFFFBC2EB)),
                        listOf(Color(0xFF84FAB0), Color(0xFF8FD3F4)),
                        listOf(Color(0xFFFCCB90), Color(0xFFD57EEB)),
                        listOf(Color(0xFFE0C3FC), Color(0xFF8EC5FC)),
                        listOf(Color(0xFFF6D365), Color(0xFFFDA085)),
                        listOf(Color(0xFF5EE7DF), Color(0xFFB490CA)),
                        listOf(Color(0xFFD299C2), Color(0xFFFEF9D7)),
                        listOf(Color(0xFFEBC0FD), Color(0xFFD9DED8)),
                        listOf(Color(0xFF96FBC4), Color(0xFFF9F586)),
                        listOf(Color(0xFFA1C4FD), Color(0xFFC2E9FB)),
                        listOf(Color(0xFFFF9A9E), Color(0xFFFECFEF)),
                        listOf(Color(0xFFFDFBFB), Color(0xFFEBEDEE)),
                        listOf(Color(0xFFCD9CF2), Color(0xFFF6F3FF)),
                        listOf(Color(0xFFE9DEFA), Color(0xFFFBFCDB)),
                        listOf(Color(0xFFACCBEE), Color(0xFFE7F0FD)),
                        listOf(Color(0xFFFFECD2), Color(0xFFFCB69F))
                    )
                }

                // Select the gradient colors based on the saved user preference
                val colors = pastelPalettes.getOrElse(AppSettings.backgroundThemeIndex) { pastelPalettes[0] }
                val gradientBackground = Brush.verticalGradient(colors)

                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                    Box(modifier = Modifier.fillMaxSize().background(gradientBackground)) {
                        // Apply a dark scrim on top of the gradient when dark mode is active
                        // to ensure sufficient contrast for white text and buttons
                        if (isDark) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
                        }

                        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                            MainContainer()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Requests the POST_NOTIFICATIONS permission on Android 13 (API 33) and above.
 *
 * If the permission is not already granted, a permission request dialog is launched
 * automatically when this composable enters the composition.
 */
@Composable
fun RequestNotificationPermission() {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val isGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) { if (!isGranted) launcher.launch(permission) }
    }
}