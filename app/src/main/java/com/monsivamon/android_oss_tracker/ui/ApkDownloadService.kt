package com.monsivamon.android_oss_tracker.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.monsivamon.android_oss_tracker.util.DownloadHistoryManager
import com.monsivamon.android_oss_tracker.util.DownloadHistoryEntry
import com.monsivamon.android_oss_tracker.util.DownloadStateManager
import com.monsivamon.android_oss_tracker.util.DownloadStateManager.DownloadProgress
import com.monsivamon.android_oss_tracker.util.DownloadStateManager.DownloadStatus
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground service that performs APK downloads in the background.
 *
 * It supports pausing, resuming, and cancelling individual transfers while
 * maintaining a minimal, non‑intrusive notification. The actual download
 * progress is published exclusively through [DownloadStateManager] so that
 * the Compose UI can react in real time.
 */
class ApkDownloadService : Service() {

    companion object {
        const val ACTION_PAUSE = "PAUSE"
        const val ACTION_RESUME = "RESUME"
        const val ACTION_CANCEL = "CANCEL"
    }

    private val foregroundNotificationId = 1001
    private val channelId = "apk_download_engine_channel"

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    /** Number of currently active (non‑paused) downloads. */
    private val activeDownloadCount = AtomicInteger(0)

    /** In‑flight HTTP calls keyed by download URL. */
    private val activeCalls = ConcurrentHashMap<String, Call>()

    /** Metadata preserved for pause/resume. */
    private val pausedJobs = ConcurrentHashMap<String, PausedJobData>()

    /** Flags indicating that a particular URL was paused by the user. */
    private val pausedByUser = ConcurrentHashMap<String, Boolean>()

    /**
     * OkHttp client shared by all downloads.
     * Initialised lazily on a background thread to keep service startup fast.
     */
    private val client by lazy {
        runBlocking(Dispatchers.IO) {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .build()
        }
    }

    private data class PausedJobData(
        val fileName: String,
        val bytesDownloaded: Long,
        val repoName: String
    )

    override fun onCreate() {
        super.onCreate()
        initializeNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ""
        val downloadUrl = intent?.getStringExtra("DOWNLOAD_URL") ?: ""
        val fileName = intent?.getStringExtra("FILE_NAME") ?: "update.apk"
        val repoName = intent?.getStringExtra("REPO_NAME") ?: "Unknown"

        when {
            downloadUrl.isNullOrEmpty() -> {
                serviceScope.launch(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "DL Error: URL is missing or empty!", Toast.LENGTH_LONG).show()
                }
                stopSelf()
            }
            action == ACTION_PAUSE -> pauseDownload(downloadUrl)
            action == ACTION_RESUME -> resumeDownload(downloadUrl)
            action == ACTION_CANCEL -> cancelDownload(downloadUrl)
            else -> startNewDownload(downloadUrl, fileName, repoName)
        }
        return START_REDELIVER_INTENT
    }

    // ── Download control ─────────────────────────────────────────────

    private fun startNewDownload(url: String, fileName: String, repoName: String) {
        DownloadStateManager.updateStatus(url, DownloadStatus.Downloading(DownloadProgress(0, 0)))
        updateForegroundNotification("Downloading …")

        activeDownloadCount.incrementAndGet()

        serviceScope.launch {
            try {
                executeNetworkStream(url, fileName, repoName, 0, false)
            } finally {
                if (pausedByUser[url] != true) {
                    if (activeDownloadCount.decrementAndGet() == 0) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun pauseDownload(url: String) {
        pausedByUser[url] = true
        activeCalls[url]?.cancel()
        val downloading = DownloadStateManager.states.value[url] as? DownloadStatus.Downloading
        val progress = downloading?.progress ?: DownloadProgress(0, 0)
        DownloadStateManager.updateStatus(url, DownloadStatus.Paused(progress))
        updateForegroundNotification("Download paused", android.R.drawable.ic_media_pause)
    }

    private fun resumeDownload(url: String) {
        pausedByUser.remove(url)
        val pausedStatus = DownloadStateManager.states.value[url] as? DownloadStatus.Paused ?: run {
            DownloadStateManager.updateStatus(url, DownloadStatus.Failed("Resume data lost"))
            return
        }
        val startByte = pausedStatus.progress.bytesDownloaded
        val paused = pausedJobs.remove(url) ?: run {
            DownloadStateManager.updateStatus(url, DownloadStatus.Failed("Resume data lost"))
            return
        }
        val fileName = paused.fileName
        val repoName = paused.repoName

        updateForegroundNotification("Downloading …")
        activeDownloadCount.incrementAndGet()
        serviceScope.launch {
            try {
                executeNetworkStream(url, fileName, repoName, startByte, true)
            } finally {
                if (pausedByUser[url] != true) {
                    if (activeDownloadCount.decrementAndGet() == 0) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    /**
     * Cancels a download regardless of its current state (running, paused, …).
     * The underlying OkHttp call is aborted and the foreground notification is
     * dismissed as soon as no active transfers remain.
     */
    private fun cancelDownload(url: String) {
        pausedByUser[url] = true
        activeCalls[url]?.cancel()

        val currentStatus = DownloadStateManager.states.value[url]
        val wasActive = currentStatus is DownloadStatus.Downloading || currentStatus is DownloadStatus.Paused

        activeCalls.remove(url)
        pausedJobs.remove(url)
        pausedByUser.remove(url)

        if (wasActive) {
            activeDownloadCount.decrementAndGet()
        }

        DownloadStateManager.updateStatus(url, DownloadStatus.Idle)

        if (activeDownloadCount.get() == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            updateForegroundNotification("Downloading …")
        }
    }

    // ── Core network logic ───────────────────────────────────────────

    /**
     * Opens a connection to [url], streams the response body to a local file,
     * and publishes progress updates to [DownloadStateManager].
     *
     * When [append] is `true` the file is opened in append mode and a
     * `Range` header is sent to resume from [startByte].
     */
    private suspend fun executeNetworkStream(
        url: String, fileName: String, repoName: String,
        startByte: Long, append: Boolean
    ) {
        val requestBuilder = Request.Builder().url(url)
        if (startByte > 0) requestBuilder.header("Range", "bytes=$startByte-")
        val request = requestBuilder.build()

        try {
            val call = client.newCall(request)
            activeCalls[url] = call
            val response = withContext(Dispatchers.IO) { call.execute() }
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

            val body = response.body ?: throw Exception("Empty response body")
            val totalBytes = startByte + body.contentLength()

            val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: cacheDir
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val outputFile = File(downloadsDir, fileName)

            withContext(Dispatchers.IO) {
                body.use { responseBody ->
                    FileOutputStream(outputFile, append).use { outputStream ->
                        val inputStream = responseBody.byteStream()
                        var bytesCopied = startByte
                        val buffer = ByteArray(8 * 1024)
                        var bytes = inputStream.read(buffer)
                        var lastUpdateTime = System.currentTimeMillis()

                        while (bytes >= 0) {
                            outputStream.write(buffer, 0, bytes)
                            bytesCopied += bytes

                            val now = System.currentTimeMillis()
                            if (now - lastUpdateTime > 500) {
                                DownloadStateManager.updateStatus(
                                    url,
                                    DownloadStatus.Downloading(DownloadProgress(bytesCopied, totalBytes))
                                )
                                lastUpdateTime = now
                            }
                            // Always keep the latest progress for a potential resume
                            pausedJobs[url] = PausedJobData(fileName, bytesCopied, repoName)
                            bytes = inputStream.read(buffer)
                        }
                        DownloadStateManager.updateStatus(
                            url,
                            DownloadStatus.Downloading(DownloadProgress(bytesCopied, totalBytes))
                        )
                    }
                }
            }

            pausedByUser.remove(url)
            DownloadHistoryManager.addEntry(
                this, DownloadHistoryEntry(
                    assetName = fileName, repoName = repoName,
                    downloadUrl = url, timestampMillis = System.currentTimeMillis(),
                    success = true
                )
            )
            DownloadStateManager.updateStatus(url, DownloadStatus.Completed(outputFile))

        } catch (e: Exception) {
            // Ignore exceptions caused by user-requested pause or cancel
            if (pausedByUser[url] == true) return
            e.printStackTrace()
            DownloadHistoryManager.addEntry(
                this, DownloadHistoryEntry(
                    assetName = fileName, repoName = repoName,
                    downloadUrl = url, timestampMillis = System.currentTimeMillis(),
                    success = false
                )
            )
            DownloadStateManager.updateStatus(url, DownloadStatus.Failed(e.message ?: "Unknown error"))
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "DL Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } finally {
            activeCalls.remove(url)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Creates or updates the foreground notification.
     *
     * On API 29+ [ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC] is provided
     * explicitly to guarantee instant notification visibility.
     */
    private fun updateForegroundNotification(title: String, iconRes: Int = android.R.drawable.stat_sys_download) {
        val notification = buildSimpleNotification(title, iconRes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(foregroundNotificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(foregroundNotificationId, notification)
        }
    }

    /**
     * Constructs the simplest possible ongoing notification.
     */
    private fun buildSimpleNotification(title: String, iconRes: Int = android.R.drawable.stat_sys_download) =
        NotificationCompat.Builder(this, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setOngoing(true)
            .build()

    /**
     * Creates the notification channel required on Android 8.0+.
     */
    private fun initializeNotificationChannel() {
        val channel = NotificationChannel(channelId, "Download Service", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Used for background APK transfers."
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}