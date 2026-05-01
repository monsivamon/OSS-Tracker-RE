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
import com.monsivamon.android_oss_tracker.util.DownloadStateManager
import com.monsivamon.android_oss_tracker.util.DownloadStateManager.DownloadProgress
import com.monsivamon.android_oss_tracker.util.DownloadStateManager.DownloadStatus
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dedicated foreground service responsible for fetching APK binaries.
 *
 * ## Key design decisions
 * - **No notification spam** – progress is published exclusively through
 *   [DownloadStateManager]; the foreground notification is a minimal, static
 *   placeholder that satisfies Android's requirement for foreground services.
 * - **Concurrent downloads** – an [AtomicInteger] tracks active transfers.
 *   The service is only stopped (and the notification dismissed) once *all*
 *   downloads have finished or failed.
 * - **Main-thread safety** – all I/O and network calls are dispatched on
 *   [Dispatchers.IO]; UI callbacks (`Toast`, notification posting) are switched
 *   to [Dispatchers.Main] when necessary.
 * - **Scoped storage compatibility** – output files are written to
 *   [getExternalFilesDir] (API 29+) with a transparent fallback to the internal
 *   cache directory.
 */
class ApkDownloadService : Service() {

    private val foregroundNotificationId = 1001
    private val channelId = "apk_download_engine_channel"

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    /** Number of download coroutines currently executing. */
    private val activeDownloadCount = AtomicInteger(0)

    /**
     * OkHttp client shared across all downloads.
     * Initialised lazily on [Dispatchers.IO] so that the main thread is never
     * blocked during service startup.
     */
    private val client by lazy {
        runBlocking(Dispatchers.IO) {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .build()
        }
    }

    override fun onCreate() {
        super.onCreate()
        initializeNotificationChannel()
    }

    /**
     * Every call to [startForegroundService] with a `DOWNLOAD_URL` extra will
     * spawn an independent download coroutine.
     *
     * If the URL is missing or empty, a toast is shown and the service stops
     * immediately (unless other downloads are still running).
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloadUrl = intent?.getStringExtra("DOWNLOAD_URL") ?: ""
        val fileName = intent?.getStringExtra("FILE_NAME") ?: "update.apk"

        if (downloadUrl.isNullOrEmpty()) {
            serviceScope.launch(Dispatchers.Main) {
                Toast.makeText(applicationContext, "DL Error: URL is missing or empty!", Toast.LENGTH_LONG).show()
            }
            stopSelf()
            return START_NOT_STICKY
        }

        DownloadStateManager.updateStatus(downloadUrl, DownloadStatus.Downloading(DownloadProgress(0, 0)))

        // Bare-minimum foreground notification (never updated with progress)
        val simpleNotification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading …")
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(foregroundNotificationId, simpleNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(foregroundNotificationId, simpleNotification)
        }

        activeDownloadCount.incrementAndGet()

        serviceScope.launch {
            try {
                executeNetworkStream(downloadUrl, fileName)
            } finally {
                if (activeDownloadCount.decrementAndGet() == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }

        return START_REDELIVER_INTENT
    }

    /**
     * Performs the actual HTTP transfer, writing the response body to a
     * temporary file and broadcasting status updates via [DownloadStateManager].
     */
    private suspend fun executeNetworkStream(url: String, fileName: String) {
        val request = Request.Builder().url(url).build()

        try {
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            if (!response.isSuccessful) throw Exception("HTTP $response.code")

            val body = response.body ?: throw Exception("Empty response body")
            val totalBytes = body.contentLength()

            val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: cacheDir
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val outputFile = File(downloadsDir, fileName)

            withContext(Dispatchers.IO) {
                body.use { responseBody ->
                    FileOutputStream(outputFile).use { outputStream ->
                        val inputStream = responseBody.byteStream()
                        var bytesCopied: Long = 0
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
                            bytes = inputStream.read(buffer)
                        }
                        // Push final progress snapshot
                        DownloadStateManager.updateStatus(
                            url,
                            DownloadStatus.Downloading(DownloadProgress(bytesCopied, totalBytes))
                        )
                    }
                }
            }

            DownloadStateManager.updateStatus(url, DownloadStatus.Completed(outputFile))

        } catch (e: Exception) {
            e.printStackTrace()
            DownloadStateManager.updateStatus(url, DownloadStatus.Failed(e.message ?: "Unknown error"))
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "DL Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Creates the notification channel used by the foreground notification.
     * Channels must be created on Android 8.0+ before a notification can be posted.
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