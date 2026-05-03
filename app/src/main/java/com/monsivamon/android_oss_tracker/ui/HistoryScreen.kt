package com.monsivamon.android_oss_tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monsivamon.android_oss_tracker.util.DownloadHistoryManager
import com.monsivamon.android_oss_tracker.util.ErrorType
import java.text.SimpleDateFormat
import java.util.*

/**
 * Chronological list of every recorded download attempt.
 *
 * Each row displays a green or red status dot, the asset name, the
 * repository name combined with the release version and type, an
 * optional error description, and a timestamp.
 */
@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    val history = remember { DownloadHistoryManager.getHistory(ctx) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = "Download History",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
        )

        if (history.isEmpty()) {
            Text(
                text = "No downloads recorded yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
            )
        } else {
            LazyColumn {
                items(history.sortedByDescending { it.timestampMillis }) { entry ->
                    HistoryItem(entry)
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(entry: com.monsivamon.android_oss_tracker.util.DownloadHistoryEntry) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {

        // ── Status dot + asset name + Done/Failed badge ──────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dotColor = if (entry.success) Color(0xFF4CAF50) else Color(0xFFF44336)
            Surface(modifier = Modifier.size(10.dp), shape = MaterialTheme.shapes.extraLarge,
                color = dotColor) { }
            Spacer(modifier = Modifier.width(8.dp))

            Text(text = entry.assetName, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f))

            Surface(
                color = if (entry.success) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(text = if (entry.success) "Done" else "Failed",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }

        // ── Error description (failed downloads only) ────────────
        if (!entry.success && entry.errorType.isNotBlank()) {
            val err = try { ErrorType.valueOf(entry.errorType) } catch (_: Exception) { ErrorType.UNKNOWN }
            Text(text = err.description, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 18.dp))
        }

        Spacer(modifier = Modifier.height(2.dp))

        // ── Repository name + version + release type label ────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = entry.repoName, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (entry.version.isNotBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = entry.version, style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(6.dp))
            val typeColor = if (entry.releaseType == "Pre-release")
                MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.secondaryContainer
            Surface(color = typeColor, shape = MaterialTheme.shapes.small) {
                Text(text = entry.releaseType, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
            }
        }

        // ── Timestamp ────────────────────────────────────────────
        Text(text = dateFormat.format(Date(entry.timestampMillis)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline)

        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}