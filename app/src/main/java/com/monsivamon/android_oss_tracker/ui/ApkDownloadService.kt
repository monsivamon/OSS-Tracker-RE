package com.monsivamon.android_oss_tracker.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.monsivamon.android_oss_tracker.util.DownloadHistoryEntry
import com.monsivamon.android_oss_tracker.util.DownloadHistoryManager
import com.monsivamon.android_oss_tracker.util.DownloadStateManager
import com.monsivamon.android_oss_tracker.util.DownloadStateManager.DownloadProgress
import com.monsivamon.android_oss_tracker.util.DownloadStateManager.DownloadStatus
import com.monsivamon.android_oss_tracker.util.ErrorType
import com.monsivamon.android_oss_tracker.util.AppSettings
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
 * Foreground service that performs APK downloads.
 *
 * Supports pause / resume / cancel, writes the file to the public Downloads
 * folder when “Download Only” mode is active, and records detailed history
 * entries including the hosting provider, release type, and error category.
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

    private val activeDownloadCount = AtomicInteger(0)
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val pausedJobs = ConcurrentHashMap<String, PausedJobData>()
    private val pausedByUser = ConcurrentHashMap<String, Boolean>()

    /** Shared OkHttp client, created lazily on a background thread. */
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
        val releaseVersion: String,
        val provider: String
    )

    override fun onCreate() {
        super.onCreate()
        initializeNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action       = intent?.action ?: ""
        val downloadUrl  = intent?.getStringExtra("DOWNLOAD_URL") ?: ""
        val fileName     = intent?.getStringExtra("FILE_NAME") ?: "update.apk"
        val repoName     = intent?.getStringExtra("REPO_NAME") ?: "Unknown"
        val releaseType  = intent?.getStringExtra("RELEASE_TYPE") ?: "Stable"
        var releaseVer   = intent?.getStringExtra("RELEASE_VERSION") ?: ""
        val provider     = intent?.getStringExtra("PROVIDER") ?: "Unknown"

        if (releaseVer.isBlank()) {
            releaseVer = extractVersionFromUrl(downloadUrl)
        }

        Log.d(TAG, "Starting: $fileName repo=$repoName type=$releaseType ver=$releaseVer provider=$provider")

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
            else -> startNewDownload(downloadUrl, fileName, repoName, releaseType, releaseVer, provider)
        }
        return START_REDELIVER_INTENT
    }

    // ── Download lifecycle ────────────────────────────────────────

    private fun startNewDownload(url: String, fileName: String, repoName: String,
                                 releaseType: String, releaseVersion: String, provider: String) {
        DownloadStateManager.updateStatus(url, DownloadStatus.Downloading(DownloadProgress(0, 0)))
        updateForegroundNotification("Downloading …")
        activeDownloadCount.incrementAndGet()

        serviceScope.launch {
            try {
                executeNetworkStream(url, fileName, repoName, 0, false,
                    releaseType, releaseVersion, provider)
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
                    paused.bytesDownloaded, true, paused.releaseType, paused.releaseVersion, paused.provider)
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

    // ── Network stream ────────────────────────────────────────────

    /**
     * Downloads the file and writes it to the appropriate location:
     * - When [AppSettings.installAfterDownload] is true: app‑private external storage.
     * - When false and API ≥ 29: the public Downloads folder via MediaStore.
     * - When false and API < 29: the public Downloads folder via direct file access.
     */
    private suspend fun executeNetworkStream(
        url: String, fileName: String, repoName: String,
        startByte: Long, append: Boolean,
        releaseType: String, releaseVersion: String,
        provider: String
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

            // Determine output file and stream
            val outputFile: File
            val outputStream: FileOutputStream

            if (AppSettings.installAfterDownload) {
                // App‑specific storage
                val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: cacheDir
                if (!dir.exists()) dir.mkdirs()
                outputFile = File(dir, fileName)
                outputStream = FileOutputStream(outputFile, append)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Public Downloads via MediaStore (no extra permissions needed)
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw Exception("Failed to create MediaStore entry")
                outputFile = File("") // dummy; the file goes through the content resolver
                outputStream = contentResolver.openOutputStream(uri, "wa")!! as FileOutputStream
            } else {
                // Legacy public storage (API < 29)
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                outputFile = File(dir, fileName)
                outputStream = FileOutputStream(outputFile, append)
            }

            // Copy the response body to the output stream
            withContext(Dispatchers.IO) {
                body.use { responseBody ->
                    outputStream.use { out ->
                        val input = responseBody.byteStream()
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
                            pausedJobs[url] = PausedJobData(
                                fileName, copied, repoName, releaseType, releaseVersion, provider
                            )
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
                success = true, releaseType = releaseType, version = releaseVersion,
                provider = provider
            ))
            DownloadStateManager.updateStatus(url, DownloadStatus.Completed(outputFile))

        } catch (e: Exception) {
            if (pausedByUser[url] == true) return
            Log.e(TAG, "Download failed", e)
            val errorType = ErrorType.classify(e)
            DownloadHistoryManager.addEntry(this, DownloadHistoryEntry(
                assetName = fileName, repoName = repoName, downloadUrl = url,
                timestampMillis = System.currentTimeMillis(),
                success = false, releaseType = releaseType, version = releaseVersion,
                errorType = errorType.name, provider = provider
            ))
            DownloadStateManager.updateStatus(url, DownloadStatus.Failed(e.message ?: "Unknown error"))
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "DL Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } finally {
            activeCalls.remove(url)
        }
    }

    // ── Notification helpers ──────────────────────────────────────

    /**
     * Updates (or creates) the foreground notification, ensuring that
     * the required [ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC] is
     * supplied on API 29+.
     */
    private fun updateForegroundNotification(title: String, iconRes: Int = android.R.drawable.stat_sys_download) {
        val notification = buildSimpleNotification(title, iconRes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(foregroundNotificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
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
     * Fallback that extracts a version number from a download URL.
     * Supports common GitHub / GitLab URL patterns.
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