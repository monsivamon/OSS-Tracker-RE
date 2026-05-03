package com.monsivamon.android_oss_tracker.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.monsivamon.android_oss_tracker.util.DownloadHistoryEntry
import com.monsivamon.android_oss_tracker.util.DownloadHistoryManager
import com.monsivamon.android_oss_tracker.util.DownloadStateManager
import com.monsivamon.android_oss_tracker.util.DownloadStateManager.DownloadProgress
import com.monsivamon.android_oss_tracker.util.DownloadStateManager.DownloadStatus
import com.monsivamon.android_oss_tracker.util.ErrorType
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
 * Foreground service that downloads APK binaries in the background.
 *
 * Supports **pause**, **resume**, and **cancel** per download URL.
 * Progress is published exclusively via [DownloadStateManager]; the
 * system notification is kept minimal and is dismissed automatically
 * once all transfers complete.
 */
class ApkDownloadService : Service() {

    companion object {
        const val ACTION_PAUSE  = "PAUSE"
        const val ACTION_RESUME = "RESUME"
        const val ACTION_CANCEL = "CANCEL"
        private const val TAG = "ApkDownloadService"
    }

    private val foregroundNotificationId = 1001
    private val channelId = "apk_download_engine_channel"

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    /** Number of currently active (non‑paused) transfers. */
    private val activeDownloadCount = AtomicInteger(0)

    /** In‑flight HTTP calls keyed by download URL. */
    private val activeCalls = ConcurrentHashMap<String, Call>()

    /** Metadata preserved for pause / resume. */
    private val pausedJobs = ConcurrentHashMap<String, PausedJobData>()

    /** Marks URLs that were explicitly paused by the user. */
    private val pausedByUser = ConcurrentHashMap<String, Boolean>()

    /** Shared OkHttp client, lazily created on a background thread. */
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
        val repoName: String,
        val releaseType: String,
        val releaseVersion: String
    )

    override fun onCreate() {
        super.onCreate()
        initializeNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action      = intent?.action ?: ""
        val downloadUrl = intent?.getStringExtra("DOWNLOAD_URL") ?: ""
        val fileName    = intent?.getStringExtra("FILE_NAME") ?: "update.apk"
        val repoName    = intent?.getStringExtra("REPO_NAME") ?: "Unknown"
        val releaseType = intent?.getStringExtra("RELEASE_TYPE") ?: "Stable"
        var releaseVer  = intent?.getStringExtra("RELEASE_VERSION") ?: ""

        if (releaseVer.isBlank()) {
            releaseVer = extractVersionFromUrl(downloadUrl)
        }

        Log.d(TAG, "Starting: $fileName repo=$repoName type=$releaseType ver=$releaseVer")

        when {
            downloadUrl.isNullOrEmpty() -> {
                serviceScope.launch(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "DL Error: URL missing", Toast.LENGTH_LONG).show()
                }
                stopSelf()
            }
            action == ACTION_PAUSE  -> pauseDownload(downloadUrl)
            action == ACTION_RESUME -> resumeDownload(downloadUrl)
            action == ACTION_CANCEL -> cancelDownload(downloadUrl)
            else -> startNewDownload(downloadUrl, fileName, repoName, releaseType, releaseVer)
        }
        return START_REDELIVER_INTENT
    }

    // ── Download control ─────────────────────────────────────────

    private fun startNewDownload(url: String, fileName: String, repoName: String,
                                 releaseType: String, releaseVersion: String) {
        DownloadStateManager.updateStatus(url, DownloadStatus.Downloading(DownloadProgress(0, 0)))
        updateForegroundNotification("Downloading …")
        activeDownloadCount.incrementAndGet()

        serviceScope.launch {
            try {
                executeNetworkStream(url, fileName, repoName, 0, false, releaseType, releaseVersion)
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
        val progress = (DownloadStateManager.states.value[url] as? DownloadStatus.Downloading)
            ?.progress ?: DownloadProgress(0, 0)
        DownloadStateManager.updateStatus(url, DownloadStatus.Paused(progress))
        updateForegroundNotification("Download paused", android.R.drawable.ic_media_pause)
    }

    private fun resumeDownload(url: String) {
        pausedByUser.remove(url)
        val paused = pausedJobs.remove(url) ?: run {
            DownloadStateManager.updateStatus(url, DownloadStatus.Failed("Resume data lost"))
            return
        }
        updateForegroundNotification("Downloading …")
        activeDownloadCount.incrementAndGet()
        serviceScope.launch {
            try {
                executeNetworkStream(url, paused.fileName, paused.repoName,
                    paused.bytesDownloaded, true, paused.releaseType, paused.releaseVersion)
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

    private fun cancelDownload(url: String) {
        pausedByUser[url] = true
        activeCalls[url]?.cancel()
        val currentStatus = DownloadStateManager.states.value[url]
        val wasActive = currentStatus is DownloadStatus.Downloading || currentStatus is DownloadStatus.Paused
        activeCalls.remove(url)
        pausedJobs.remove(url)
        pausedByUser.remove(url)
        if (wasActive) activeDownloadCount.decrementAndGet()
        DownloadStateManager.updateStatus(url, DownloadStatus.Idle)
        if (activeDownloadCount.get() == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            updateForegroundNotification("Downloading …")
        }
    }

    // ── Core transfer logic ──────────────────────────────────────

    private suspend fun executeNetworkStream(
        url: String, fileName: String, repoName: String,
        startByte: Long, append: Boolean,
        releaseType: String, releaseVersion: String
    ) {
        val request = Request.Builder().url(url).apply {
            if (startByte > 0) header("Range", "bytes=$startByte-")
        }.build()

        try {
            val call = client.newCall(request)
            activeCalls[url] = call
            val response = withContext(Dispatchers.IO) { call.execute() }
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            val body = response.body ?: throw Exception("Empty body")
            val totalBytes = startByte + body.contentLength()

            val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: cacheDir
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val outputFile = File(downloadsDir, fileName)

            withContext(Dispatchers.IO) {
                body.use { rb ->
                    FileOutputStream(outputFile, append).use { out ->
                        val input = rb.byteStream()
                        var copied = startByte
                        val buf = ByteArray(8 * 1024)
                        var bytes = input.read(buf)
                        var lastUpdate = System.currentTimeMillis()
                        while (bytes >= 0) {
                            out.write(buf, 0, bytes)
                            copied += bytes
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 500) {
                                DownloadStateManager.updateStatus(
                                    url, DownloadStatus.Downloading(DownloadProgress(copied, totalBytes)))
                                lastUpdate = now
                            }
                            pausedJobs[url] = PausedJobData(fileName, copied, repoName, releaseType, releaseVersion)
                            bytes = input.read(buf)
                        }
                        DownloadStateManager.updateStatus(
                            url, DownloadStatus.Downloading(DownloadProgress(copied, totalBytes)))
                    }
                }
            }

            pausedByUser.remove(url)
            DownloadHistoryManager.addEntry(this, DownloadHistoryEntry(
                assetName = fileName, repoName = repoName, downloadUrl = url,
                timestampMillis = System.currentTimeMillis(),
                success = true, releaseType = releaseType, version = releaseVersion))
            DownloadStateManager.updateStatus(url, DownloadStatus.Completed(outputFile))

        } catch (e: Exception) {
            if (pausedByUser[url] == true) return
            Log.e(TAG, "Download failed", e)
            val errorType = ErrorType.classify(e)
            DownloadHistoryManager.addEntry(this, DownloadHistoryEntry(
                assetName = fileName, repoName = repoName, downloadUrl = url,
                timestampMillis = System.currentTimeMillis(),
                success = false, releaseType = releaseType, version = releaseVersion,
                errorType = errorType.name))
            DownloadStateManager.updateStatus(url, DownloadStatus.Failed(e.message ?: "Unknown error"))
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "DL Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } finally {
            activeCalls.remove(url)
        }
    }

    // ── Notification helpers ─────────────────────────────────────

    private fun updateForegroundNotification(title: String, iconRes: Int = android.R.drawable.stat_sys_download) {
        val notification = buildSimpleNotification(title, iconRes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(foregroundNotificationId, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(foregroundNotificationId, notification)
        }
    }

    private fun buildSimpleNotification(title: String, iconRes: Int) =
        NotificationCompat.Builder(this, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setOngoing(true)
            .build()

    private fun initializeNotificationChannel() {
        val channel = NotificationChannel(channelId, "Download Service",
            NotificationManager.IMPORTANCE_LOW).apply {
            description = "Used for background APK transfers."
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    /**
     * Fallback that extracts a version number from the download URL.
     *
     * Supported patterns:
     * - `/releases/download/v1.2.3/...`
     * - `/releases/tag/v1.2.3`
     * - File name segment containing a version (e.g. `microg-6.1.4.apk`)
     */
    private fun extractVersionFromUrl(url: String): String {
        Regex("/releases/download/(v?[0-9]+[^/]*)").find(url)?.let { return it.groupValues[1] }
        Regex("/releases/tag/(v?[0-9]+\\S*)").find(url)?.let { return it.groupValues[1] }
        val fileName = url.substringAfterLast("/")
        Regex("(?:^|[.-])(v?[0-9]+(?:\\.[0-9]+)*(?:-\\S+)?)").findAll(fileName).forEach { m ->
            val candidate = m.groupValues[1]
            if (candidate.any { it.isDigit() } && candidate.length >= 3) return candidate.trimStart('-', '.')
        }
        return ""
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}