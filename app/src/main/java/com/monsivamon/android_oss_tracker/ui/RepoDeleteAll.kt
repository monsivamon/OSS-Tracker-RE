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
 * Offers a full factory reset of the application.
 *
 * After confirmation every persistent file owned by the app is erased,
 * the process is terminated, and [MainActivity] is relaunched as a
 * brand‑new task.
 */
@Composable
fun RepoDeleteAll() {
    val ctx = LocalContext.current
    val showDialog = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Button(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        onClick = { showDialog.value = true }
    ) {
        Text("Reset & Reboot", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 4.dp))
    }

    if (showDialog.value) {
        AlertDialog(
            onDismissRequest = { showDialog.value = false },
            title = { Text("Reset & Reboot?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will erase all application data and restart the app. " +
                        "All tracked repositories, download history, and settings will be permanently removed.",
                    style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { wipeAllAppData(ctx) }
                            val intent = Intent(ctx, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            }
                            ctx.startActivity(intent)
                            Runtime.getRuntime().exit(0)
                        }
                        showDialog.value = false
                    }
                ) { Text("Reboot") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog.value = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Recursively deletes the app's internal storage, cache, external files,
 * and external cache.  Equivalent to “Clear storage” from system Settings.
 */
private fun wipeAllAppData(context: Context) {
    context.filesDir.parentFile?.takeIf { it.exists() }?.deleteRecursively()
    context.cacheDir.takeIf { it.exists() }?.deleteRecursively()
    context.getExternalFilesDir(null)?.takeIf { it.exists() }?.deleteRecursively()
    context.externalCacheDir?.takeIf { it.exists() }?.deleteRecursively()
}