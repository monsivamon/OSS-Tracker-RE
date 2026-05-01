package com.monsivamon.android_oss_tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.monsivamon.android_oss_tracker.repo.RepoMetaData

/** Placeholder shown when a repository cannot be parsed or is unsupported. */
@Composable
fun UnsupportedTracker(metaData: RepoMetaData) {
    Text(
        text = "Unsupported or unparseable repository:\n${metaData.repoUrl}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp)
    )
}

/** Loading indicator displayed while metadata is being fetched. */
@Composable
fun LoadingTracker(metaData: RepoMetaData) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(16.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = "Loading ${metaData.appName}...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Error view with expandable details for each recorded error message. */
@Composable
fun ErroredTracker(metaData: RepoMetaData) {
    val showErrors = remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = metaData.repoUrl,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 48.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { showErrors.value = !showErrors.value },
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = if (showErrors.value) "Hide Details" else "View Errors")
        }

        if (showErrors.value) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    metaData.errors.forEach { errorMsg ->
                        Text(
                            text = "• $errorMsg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}