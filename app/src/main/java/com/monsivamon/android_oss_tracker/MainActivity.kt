package com.monsivamon.android_oss_tracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.monsivamon.android_oss_tracker.ui.MainContainer
import com.monsivamon.android_oss_tracker.ui.theme.AndroidossreleasetrackerTheme

/**
 * The single Activity that owns the entire Compose UI.
 *
 * ## Architecture
 * - **True zero-dependency tree** – the [MainContainer] receives no parameters.
 *   Every screen reaches shared services (Volley, SharedPreferences) through the
 *   [OSSApp] singleton or [LocalContext], eliminating cascading recompositions.
 * - **Theme** – a dynamic colour system via [AndroidossreleasetrackerTheme] that
 *   follows Material You on supported devices.
 * - **Permissions** – [RequestNotificationPermission] is the only runtime check,
 *   required solely by the foreground download service on Android 13+.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AndroidossreleasetrackerTheme {

                // One-time permission probe – does not block rendering.
                RequestNotificationPermission()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainContainer()
                }
            }
        }
    }
}

/**
 * Requests the `POST_NOTIFICATIONS` permission when running on Android 13+.
 *
 * The permission is mandatory for any foreground service that wants to show a
 * notification (including the minimal placeholder used by [ApkDownloadService]).
 * If the permission is denied, the app continues to function; Android will simply
 * suppress the notification without crashing.
 *
 * The check is performed **asynchronously** inside [LaunchedEffect], so it never
 * adds a frame to the cold-start deadline.
 */
@Composable
fun RequestNotificationPermission() {
    val context = LocalContext.current

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val isGranted = ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { _ ->
            // No-op: the service gracefully degrades without the permission.
        }

        LaunchedEffect(Unit) {
            if (!isGranted) {
                launcher.launch(permission)
            }
        }
    }
}