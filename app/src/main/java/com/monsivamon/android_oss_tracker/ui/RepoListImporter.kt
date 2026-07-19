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
import com.monsivamon.android_oss_tracker.util.AppSettings
import com.monsivamon.android_oss_tracker.util.FileHelpers
import com.monsivamon.android_oss_tracker.util.ThemeMode
import com.monsivamon.android_oss_tracker.util.ThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Allows the user to import a backup file (CSV) or a legacy plain‑text
 * list of repository URLs.  If the file appears to be a valid CSV backup
 * (header is exactly "Type,Key,Value"), it restores repositories, custom names,
 * the GitHub token, all application settings, and the display order.
 * Otherwise it falls back to reading the file as a simple URL list.
 * A brief "Success" toast is shown on completion.
 */
@Composable
fun RepoListImporter() {
    val ctx = LocalContext.current
    val sharedPreferences = remember {
        ctx.getSharedPreferences(PersistentState.STATE_FILENAME, Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()

    val reader = FileHelpers.readFile({ data ->
        if (data.isNotEmpty()) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val trimmed = data.trimStart()
                        if (trimmed.startsWith("Type,Key,Value")) {
                            // CSV backup format
                            restoreFromCsv(ctx, sharedPreferences, data)
                        } else if (trimmed.startsWith("Type,")) {
                            // Header looks like a CSV but not the expected one
                            throw IllegalArgumentException("Unsupported CSV format")
                        } else {
                            // Legacy plain‑text list (one URL per line)
                            val lines = data.lines().filter { it.isNotBlank() && it.startsWith("http") }
                            if (lines.isEmpty()) {
                                throw IllegalArgumentException("No valid URLs found in the file")
                            }
                            PersistentState.addTrackers(sharedPreferences, lines)
                            AppCache.cachedRepos.clear()
                        }
                    }
                    Toast.makeText(ctx, "Success", Toast.LENGTH_SHORT).show()
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(ctx, e.message ?: "Invalid backup file", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }, {
        Toast.makeText(ctx, "Could not read the backup file", Toast.LENGTH_SHORT).show()
    })

    OutlinedButton(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        onClick = { reader.launch(arrayOf("text/csv", "text/plain", "*/*")) }
    ) {
        Text(
            "Restore",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

/**
 * Parses CSV data produced by the Backup function and applies the stored settings,
 * including repository order.
 */
private fun restoreFromCsv(context: Context, prefs: android.content.SharedPreferences, data: String) {
    val lines = data.lines().filter { it.isNotBlank() }
    if (lines.size < 2) return // Header only

    var repoOrderList: List<String>? = null

    // Skip header line (index 0)
    for (i in 1 until lines.size) {
        val fields = parseCsvLine(lines[i])
        if (fields.size < 3) continue
        val type = fields[0].trim()
        val key = fields[1].trim()
        val value = fields[2].trim()

        when (type) {
            "repo" -> {
                if (key.isNotEmpty()) {
                    PersistentState.addTracker(prefs, key)
                    if (value.isNotEmpty()) {
                        PersistentState.setCustomName(context, key, value)
                    }
                }
            }
            "setting" -> {
                when (key) {
                    "token" -> AppSettings.setAppGithubToken(value)
                    "trackPreReleases" -> AppSettings.setAppTrackPreReleases(value.toBoolean())
                    "installAfterDownload" -> AppSettings.setAppInstallAfterDownload(value.toBoolean())
                    "autoUpdateEnabled" -> AppSettings.setAppAutoUpdateEnabled(value.toBoolean())
                    "backgroundThemeIndex" -> AppSettings.setAppBackgroundThemeIndex(value.toInt())
                    "themeMode" -> {
                        ThemeMode.entries.find { it.name.equals(value, ignoreCase = true) }
                            ?.let { ThemeState.setThemeMode(it) }
                    }
                    "showNewTab" -> AppSettings.setAppShowNewTab(value.toBoolean())
                    "showHistoryTab" -> AppSettings.setAppShowHistoryTab(value.toBoolean())
                    "repoOrder" -> {
                        // The value is a comma‑separated list of URLs; already unescaped by parseCsvLine
                        repoOrderList = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    }
                }
            }
        }
    }

    // Apply repository order if present
    if (repoOrderList != null) {
        PersistentState.saveOrder(prefs, repoOrderList)
    }

    // Clear the in‑memory cache so the UI will reload everything
    AppCache.cachedRepos.clear()
}

/**
 * Parses a single CSV line, handling quoted fields.
 */
private fun parseCsvLine(line: String): List<String> {
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    for (char in line) {
        when {
            char == '"' -> {
                inQuotes = !inQuotes
            }
            char == ',' && !inQuotes -> {
                fields.add(current.toString())
                current.clear()
            }
            else -> current.append(char)
        }
    }
    fields.add(current.toString())
    // Remove surrounding quotes from each field if present
    return fields.map { field ->
        if (field.startsWith("\"") && field.endsWith("\"")) {
            field.substring(1, field.length - 1).replace("\"\"", "\"")
        } else field
    }
}