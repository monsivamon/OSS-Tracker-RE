package com.monsivamon.android_oss_tracker.ui

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.monsivamon.android_oss_tracker.PersistentState
import com.monsivamon.android_oss_tracker.repo.AppCache
import com.monsivamon.android_oss_tracker.util.AppSettings
import com.monsivamon.android_oss_tracker.util.ThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Exports the complete application state as a CSV file directly to the
 * public Downloads folder.  A toast confirms the saved file name.
 * Custom display names are taken from the in‑memory cache to guarantee
 * they reflect the latest UI state, and the repository order is preserved.
 */
@Composable
fun RepoListExporter() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    OutlinedButton(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        onClick = {
            scope.launch {
                try {
                    // Collect custom names from the in‑memory cache
                    val cacheNames = mutableMapOf<String, String>()
                    AppCache.cachedRepos.forEach { (url, meta) ->
                        meta.customName?.let { cacheNames[url] = it }
                    }

                    val result = withContext(Dispatchers.IO) {
                        saveBackupToDownloads(ctx, cacheNames)
                    }
                    result.onSuccess { fileName ->
                        Toast.makeText(ctx, "Saved: $fileName", Toast.LENGTH_LONG).show()
                    }.onFailure { error ->
                        Toast.makeText(ctx, "Save failed: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    ) {
        Text(
            "Backup",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

/**
 * Builds the CSV backup content and writes it to the Downloads folder
 * with the name OSS_Tracker_RE_YYMMDD.csv.
 *
 * @param cacheNames a map of repository URL → custom name from the live cache.
 * @return [Result] containing the final file name on success.
 */
private fun saveBackupToDownloads(context: Context, cacheNames: Map<String, String>): Result<String> {
    return try {
        val dateStr = SimpleDateFormat("yyMMdd", Locale.US).format(Date())
        val fileName = "OSS_Tracker_RE_$dateStr.csv"

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val file = File(downloadsDir, fileName)
        file.writeText(buildBackupCsv(context, cacheNames))

        Result.success(fileName)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Builds a CSV string containing all repositories (with custom names),
 * the GitHub token, every relevant application setting, and the current
 * repository display order.
 *
 * Custom names are taken first from [cacheNames] (the in‑memory state),
 * falling back to [PersistentState.getCustomName] so that no name is lost.
 */
private fun buildBackupCsv(context: Context, cacheNames: Map<String, String>): String {
    val sb = StringBuilder()
    sb.appendLine("Type,Key,Value")

    val sharedPrefs = context.getSharedPreferences(PersistentState.STATE_FILENAME, Context.MODE_PRIVATE)
    val urls = PersistentState.getSavedTrackers(sharedPrefs)

    // Repositories
    urls.forEach { url ->
        val customName = cacheNames[url]
            ?: PersistentState.getCustomName(context, url)
            ?: ""
        sb.appendLine("repo,${escapeCsv(url)},${escapeCsv(customName)}")
    }

    // GitHub token
    sb.appendLine("setting,token,${escapeCsv(AppSettings.githubToken)}")

    // Application settings
    sb.appendLine("setting,trackPreReleases,${AppSettings.trackPreReleases}")
    sb.appendLine("setting,installAfterDownload,${AppSettings.installAfterDownload}")
    sb.appendLine("setting,autoUpdateEnabled,${AppSettings.autoUpdateEnabled}")
    sb.appendLine("setting,backgroundThemeIndex,${AppSettings.backgroundThemeIndex}")
    sb.appendLine("setting,themeMode,${ThemeState.currentMode.name}")
    sb.appendLine("setting,showNewTab,${AppSettings.showNewTab}")
    sb.appendLine("setting,showHistoryTab,${AppSettings.showHistoryTab}")

    // Repository display order (comma-separated list of URLs)
    val orderList = PersistentState.getSavedTrackers(sharedPrefs) // already in display order
    val orderValue = orderList.joinToString(",") { escapeCsv(it) }
    sb.appendLine("setting,repoOrder,\"$orderValue\"")

    return sb.toString()
}

/**
 * Escapes a CSV value by surrounding with double quotes if it contains a comma,
 * double quote, or newline. Internal double quotes are doubled.
 */
private fun escapeCsv(value: String): String {
    return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }
}