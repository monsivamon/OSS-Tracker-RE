package com.monsivamon.android_oss_tracker.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File

object DownloadStateManager {
    data class DownloadProgress(val bytesDownloaded: Long, val totalBytes: Long) {
        val percent: Int get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
    }

    sealed class DownloadStatus {
        object Idle : DownloadStatus()
        data class Downloading(val progress: DownloadProgress) : DownloadStatus()
        data class Completed(val apkFile: File) : DownloadStatus()
        data class Failed(val error: String) : DownloadStatus()
    }

    private val _states = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val states: StateFlow<Map<String, DownloadStatus>> = _states

    fun updateStatus(downloadUrl: String, status: DownloadStatus) {
        _states.update { it + (downloadUrl to status) }
    }
}