package com.monsivamon.android_oss_tracker.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * Central, observable registry that tracks the download status of every
 * URL requested through [ApkDownloadService].
 *
 * Compose screens collect [states] as a StateFlow and react in real time.
 */
object DownloadStateManager {
    data class DownloadProgress(val bytesDownloaded: Long, val totalBytes: Long) {
        val percent: Int get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
    }

    sealed class DownloadStatus {
        object Idle : DownloadStatus()
        data class Downloading(val progress: DownloadProgress) : DownloadStatus()
        /** Paused keeps the last snapshot so the UI renders a frozen progress bar. */
        data class Paused(val progress: DownloadProgress) : DownloadStatus()
        data class Completed(val apkFile: File) : DownloadStatus()
        data class Failed(val error: String) : DownloadStatus()
    }

    private val _states = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val states: StateFlow<Map<String, DownloadStatus>> = _states

    fun updateStatus(downloadUrl: String, status: DownloadStatus) {
        _states.update { it + (downloadUrl to status) }
    }
}