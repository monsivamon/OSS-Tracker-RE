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
import com.monsivamon.android_oss_tracker.repo.AppCache
import com.monsivamon.android_oss_tracker.util.FileHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Imports a list of repository URLs from a plain-text file chosen by the user.
 * The file is read on a background thread; parsing and cache invalidation are
 * performed on [Dispatchers.IO] to keep the UI responsive.
 */
@Composable
fun RepoListImporter() {
    val ctx = LocalContext.current
    val sharedPreferences = remember {
        ctx.getSharedPreferences(PersistentState.STATE_FILENAME, Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()

    val reader = FileHelpers.readFile({ data ->
        println("Read file content: $data")
        if (data.isNotEmpty()) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    PersistentState.addTrackers(ctx, sharedPreferences, data.lines())
                    AppCache.cachedRepos.clear()
                }
                Toast.makeText(ctx, "Repo list imported successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }, {
        Toast.makeText(ctx, "Could not import repo list", Toast.LENGTH_SHORT).show()
    })

    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        onClick = { reader.launch(arrayOf("text/plain")) }
    ) {
        Text(
            text = "Import Repo List",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}