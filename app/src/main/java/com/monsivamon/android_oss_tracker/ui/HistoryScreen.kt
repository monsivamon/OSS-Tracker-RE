package com.monsivamon.android_oss_tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.android_oss_tracker.util.DownloadHistoryManager
import com.monsivamon.android_oss_tracker.util.ErrorType
import java.text.SimpleDateFormat
import java.util.*

/**
 * Chronological list of every recorded download attempt.
 *
 * Each row shows a green/red status dot, the asset name, the repository
 * name combined with the release version and type, an optional error
 * description, a provider badge, and a timestamp.
 */
@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    val history = remember { DownloadHistoryManager.getHistory(ctx) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Download History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))

        if (history.isEmpty()) Text("No downloads recorded yet.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 32.dp))
        else LazyColumn { items(history.sortedByDescending { it.timestampMillis }) { HistoryItem(it) } }
    }
}

@Composable
private fun HistoryItem(entry: com.monsivamon.android_oss_tracker.util.DownloadHistoryEntry) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(10.dp), color = if (entry.success) Color(0xFF4CAF50) else Color(0xFFF44336), shape = MaterialTheme.shapes.extraLarge) {}
            Spacer(Modifier.width(8.dp))
            Text(entry.assetName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Surface(color = if (entry.success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                Text(if (entry.success) "Done" else "Failed", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        if (!entry.success && entry.errorType.isNotBlank()) {
            val err = try { ErrorType.valueOf(entry.errorType) } catch (_: Exception) { ErrorType.UNKNOWN }
            Text(err.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 18.dp))
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Provider badge
            if (entry.provider.isNotBlank()) {
                val (pl, pc) = when (entry.provider) { "GitHub" -> "GitHub" to Color(0xFF24292F); "GitLab" -> "GitLab" to Color(0xFFE24329); else -> entry.provider to Color.Gray }
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(pc).padding(horizontal = 6.dp, vertical = 2.dp), contentAlignment = Alignment.Center) { Text(pl, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.width(4.dp))
            }
            Text(entry.repoName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (entry.version.isNotBlank()) { Spacer(Modifier.width(4.dp)); Text(entry.version, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.width(6.dp))
            Surface(color = if (entry.releaseType == "Pre-release") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                Text(entry.releaseType, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
            }
        }
        Text(fmt.format(Date(entry.timestampMillis)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}