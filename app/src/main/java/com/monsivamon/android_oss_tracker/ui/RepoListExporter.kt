package com.monsivamon.android_oss_tracker.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.monsivamon.android_oss_tracker.PersistentState
import com.monsivamon.android_oss_tracker.util.FileHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Exports the currently tracked repository URLs to a plain-text file.
 * The URL list is fetched asynchronously from [SharedPreferences] so that
 * the main thread is never blocked.
 */
@Composable
fun RepoListExporter() {
    val ctx = LocalContext.current
    val sharedPreferences = remember {
        ctx.getSharedPreferences(PersistentState.STATE_FILENAME, Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()

    val writer = FileHelpers.openWritableTextFile({ uri ->
        scope.launch {
            val data = withContext(Dispatchers.IO) {
                PersistentState.getSavedTrackers(sharedPreferences).joinToString("\n")
            }
            FileHelpers.writeToFile(uri, data, ctx)
        }
    }, {
        Toast.makeText(ctx, "Could not export repo list", Toast.LENGTH_SHORT).show()
    })

    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        onClick = { writer.launch("oss_trackers.txt") }
    ) {
        Text(
            text = "Export Repo List",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}