package com.monsivamon.android_oss_tracker.ui

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.volley.RequestQueue
import com.monsivamon.android_oss_tracker.PersistentState
import com.monsivamon.android_oss_tracker.repo.MetaDataState
import com.monsivamon.android_oss_tracker.repo.RepoMetaData

/**
 * Displays a preview card for a specific repository URL.
 * Automatically fetches repository metadata upon composition and allows the user
 * to add the repository to their tracked list once a valid release is found.
 *
 * @param repoUrl The target GitHub repository URL.
 * @param requestQueue The Volley RequestQueue for network operations.
 * @param onAdd Callback triggered when the user confirms adding the repository.
 */
@Composable
fun TrackerPreview(
    repoUrl: String,
    requestQueue: RequestQueue,
    onAdd: (String, String) -> Unit
) {
    val metaData = remember { RepoMetaData(repoUrl, requestQueue) }

    LaunchedEffect(repoUrl) {
        metaData.refreshNetwork()
    }

    val isValid = remember { mutableStateOf(false) }

    if (!isValid.value && metaData.latestVersion.value != null) {
        isValid.value = true
    }

    val fallbackText = when(metaData.state.value) {
        MetaDataState.Unsupported -> "<unsupported>"
        MetaDataState.Loading -> "<loading>"
        MetaDataState.Errored -> "<error>"
        MetaDataState.Loaded -> "<loaded but null>"
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = metaData.appName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = metaData.latestVersion.value ?: fallbackText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = metaData.latestVersionDate.value ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val assets = metaData.latestAssets.value
                if (assets.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Available files:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    assets.forEach { asset ->
                        Text(
                            text = "• ${asset.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, start = 8.dp)
                        )
                    }
                }
            }

            FilledIconButton(
                enabled = isValid.value,
                onClick = { onAdd(repoUrl, metaData.appName) },
                modifier = Modifier
                    .padding(start = 16.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Repository"
                )
            }
        }
    }
}

/**
 * Provides the user interface for adding a new OSS tracker.
 * Includes text input for the repository URL and a preview section for validation.
 *
 * @param sharedPreferences The SharedPreferences instance for persisting the new tracker.
 * @param requestQueue The Volley RequestQueue for network operations.
 */
@Composable
fun NewTrackerScreen(
    sharedPreferences: SharedPreferences,
    requestQueue: RequestQueue
) {
    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current
    val repoInputBox = remember { mutableStateOf("") }
    val isTested = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Add a new App Tracker",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        )

        OutlinedTextField(
            value = repoInputBox.value,
            onValueChange = {
                repoInputBox.value = it.trim()
                isTested.value = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Project Repository URL") },
            placeholder = { Text("https://github.com/monsivamon/OSS-Tracker-RE") },
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions.Default.copy(
                imeAction = ImeAction.Done,
                keyboardType = KeyboardType.Uri
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isTested.value) {
            TrackerPreview(
                repoUrl = repoInputBox.value,
                requestQueue = requestQueue
            ) { repo, appName ->
                PersistentState.addTracker(ctx, sharedPreferences, appName, repo)
                Toast.makeText(ctx, "Added $appName to your trackers", Toast.LENGTH_LONG).show()
                repoInputBox.value = ""
                isTested.value = false
            }
        }

        Spacer(modifier = Modifier.weight(1.0f))

        Button(
            onClick = {
                if (repoInputBox.value.isNotBlank()) {
                    isTested.value = true
                    focusManager.clearFocus()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Test Repository",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}