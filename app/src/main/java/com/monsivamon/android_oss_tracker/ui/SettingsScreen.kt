package com.monsivamon.android_oss_tracker.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monsivamon.android_oss_tracker.PersistentState
import com.monsivamon.android_oss_tracker.repo.AppCache
import com.monsivamon.android_oss_tracker.util.FileHelpers

/**
 * Provides a UI component to import a list of repository URLs from a local text file.
 * Automatically parses the file and updates the persistent storage and application cache.
 */
@Composable
fun RepoListImporter() {
    val ctx = LocalContext.current
    val sharedPreferences = ctx.getSharedPreferences(
        PersistentState.STATE_FILENAME,
        Context.MODE_PRIVATE
    )
    val reader = FileHelpers.readFile({ data ->
        println("Read file content: $data")
        if (data.isNotEmpty()) {
            PersistentState.addTrackers(ctx, sharedPreferences, data.lines())

            // Clear the outdated cache to force a refresh on the next load
            AppCache.cachedRepos.clear()
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

/**
 * Provides a UI component to export the currently tracked repository URLs to a local text file.
 */
@Composable
fun RepoListExporter() {
    val ctx = LocalContext.current
    val sharedPreferences = ctx.getSharedPreferences(
        PersistentState.STATE_FILENAME,
        Context.MODE_PRIVATE
    )
    val data = PersistentState.getSavedTrackers(sharedPreferences).joinToString("\n")
    val writer = FileHelpers.openWritableTextFile({ uri ->
        FileHelpers.writeToFile(uri, data, ctx)
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

/**
 * Provides a UI component and confirmation dialog to delete all tracked repositories.
 * Clears both persistent storage and in-memory cache upon confirmation.
 */
@Composable
fun RepoDeleteAll() {
    val ctx = LocalContext.current
    val sharedPreferences = ctx.getSharedPreferences(
        PersistentState.STATE_FILENAME,
        Context.MODE_PRIVATE
    )
    val showDeleteAllPopup = remember { mutableStateOf(false) }

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
                        PersistentState.removeAllTrackers(ctx, sharedPreferences)
                        AppCache.cachedRepos.clear()
                        showDeleteAllPopup.value = false
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

/**
 * Displays an informational dialog containing application details, version,
 * developer credits, and a link to the project's source code.
 */
@Composable
fun AboutAppDialog() {
    val showDialog = remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    FilledTonalButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        onClick = { showDialog.value = true }
    ) {
        Text(
            text = "About App",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 4.dp)
        )
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

                    Text(
                        text = "OSS Tracker RE",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "v0.1.8",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "This is an application for tracking and downloading the latest APKs from open-source software (GitHub, GitLab).",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Developer",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "monsivamon",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Original Author",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "jroddev",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/monsivamon/OSS-Tracker-RE"))
                            ctx.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Source Code (GitHub)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog.value = false }) {
                    Text("Close")
                }
            }
        )
    }
}

/**
 * Displays the main settings screen, organizing the importer, exporter,
 * deletion controls, and application information.
 */
@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        )

        RepoListImporter()
        RepoListExporter()
        RepoDeleteAll()

        Spacer(modifier = Modifier.height(24.dp))

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        AboutAppDialog()
    }
}