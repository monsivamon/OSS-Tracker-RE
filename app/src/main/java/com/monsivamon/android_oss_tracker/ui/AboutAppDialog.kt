package com.monsivamon.android_oss_tracker.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

/**
 * Displays application metadata, developer credits, and a link to the
 * project's source‑code repository.
 */
@Composable
fun AboutAppDialog() {
    val showDialog = remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    FilledTonalButton(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        onClick = { showDialog.value = true }
    ) {
        Text("About App", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 4.dp))
    }

    if (showDialog.value) {
        AlertDialog(
            onDismissRequest = { showDialog.value = false },
            title = null,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("OSS Tracker RE", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("v0.2.1", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Track and download the latest APKs from open‑source projects on GitHub and GitLab.",
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)

                    Spacer(modifier = Modifier.height(24.dp))
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                        Text("Developer", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("monsivamon", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface)

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Original Author", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("jroddev", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW,
                                "https://github.com/monsivamon/OSS-Tracker-RE".toUri())
                            ctx.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Source Code (GitHub)") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog.value = false }) { Text("Close") }
            }
        )
    }
}