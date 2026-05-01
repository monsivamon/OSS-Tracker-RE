package com.monsivamon.android_oss_tracker.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monsivamon.android_oss_tracker.PersistentState
import com.monsivamon.android_oss_tracker.repo.AppCache
import kotlinx.coroutines.launch

/**
 * Displays a confirmation dialog before permanently removing every tracked
 * repository from both persistent storage and the in-memory cache.
 *
 * The deletion itself is executed on the **main thread** because
 * [PersistentState.removeAllTrackers] internally shows a [Toast].
 * [SharedPreferences] writes use [SharedPreferences.Editor.apply], which is
 * already asynchronous and does not block the UI.
 */
@Composable
fun RepoDeleteAll() {
    val ctx = LocalContext.current
    val sharedPreferences = remember {
        ctx.getSharedPreferences(PersistentState.STATE_FILENAME, Context.MODE_PRIVATE)
    }
    val showDeleteAllPopup = remember { mutableStateOf(false) }
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
        onClick = { showDeleteAllPopup.value = true }
    ) {
        Text(
            text = "Delete All Trackers",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }

    if (showDeleteAllPopup.value) {
        AlertDialog(
            onDismissRequest = { showDeleteAllPopup.value = false },
            title = {
                Text(
                    text = "Delete all trackers?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This action cannot be undone. All your saved repository trackers will be permanently removed.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        // Runs on the main thread so the Toast inside
                        // removeAllTrackers can access the Looper.
                        scope.launch {
                            PersistentState.removeAllTrackers(ctx, sharedPreferences)
                            AppCache.cachedRepos.clear()
                            showDeleteAllPopup.value = false
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAllPopup.value = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}