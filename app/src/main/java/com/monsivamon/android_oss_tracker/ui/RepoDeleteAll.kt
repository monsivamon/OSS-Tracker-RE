package com.monsivamon.android_oss_tracker.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monsivamon.android_oss_tracker.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Presents a confirmation dialog that performs a full factory reset of the
 * application and then restarts the launcher Activity.
 *
 * Upon confirmation every persistent file owned by the app is erased, the
 * process is terminated, and [MainActivity] is re-launched as a fresh task.
 */
@Composable
fun RepoDeleteAll() {
    val ctx = LocalContext.current
    val showDialog = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Button(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        onClick = { showDialog.value = true }
    ) {
        Text(
            text = "Reset & Reboot",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }

    if (showDialog.value) {
        AlertDialog(
            onDismissRequest = { showDialog.value = false },
            title = {
                Text(
                    text = "Reset & Reboot?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will erase all application data and restart the app. " +
                            "All tracked repositories, download history, and settings will be permanently removed.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        scope.launch {
                            // Wipe every byte of persistent data on a background thread
                            withContext(Dispatchers.IO) {
                                wipeAllAppData(ctx)
                            }
                            // Restart the app into a clean state
                            val intent = Intent(ctx, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            }
                            ctx.startActivity(intent)
                            // Ensure no residual state lingers in memory
                            Runtime.getRuntime().exit(0)
                        }
                        showDialog.value = false
                    }
                ) {
                    Text("Reboot")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog.value = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Recursively deletes every file and directory owned by the application.
 *
 * The following storage locations are purged:
 * - Internal storage (databases, shared preferences, files)
 * - Internal cache
 * - App-specific external files directory
 * - App-specific external cache directory
 *
 * The result is functionally identical to the user invoking
 * "Clear storage" from the system Settings app.
 */
private fun wipeAllAppData(context: Context) {
    // Internal storage – contains databases, shared_prefs, etc.
    val dataDir = context.filesDir.parentFile
    if (dataDir != null && dataDir.exists()) {
        dataDir.deleteRecursively()
    }

    // Internal cache
    val cacheDir = context.cacheDir
    if (cacheDir.exists()) {
        cacheDir.deleteRecursively()
    }

    // App-specific external files (scoped storage compliant area)
    val externalFilesDir = context.getExternalFilesDir(null)
    if (externalFilesDir != null && externalFilesDir.exists()) {
        externalFilesDir.deleteRecursively()
    }

    // App-specific external cache
    val externalCacheDir = context.externalCacheDir
    if (externalCacheDir != null && externalCacheDir.exists()) {
        externalCacheDir.deleteRecursively()
    }
}