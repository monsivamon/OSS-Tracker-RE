package com.monsivamon.android_oss_tracker.util

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.monsivamon.android_oss_tracker.MainActivity
import com.monsivamon.android_oss_tracker.PersistentState
import com.monsivamon.android_oss_tracker.OSSApp
import com.monsivamon.android_oss_tracker.repo.RepoMetaData

/**
 * Periodically checks every tracked repository for a new stable release
 * and posts a notification when one is found.
 *
 * The worker runs as a [PeriodicWorkRequest] scheduled by [OSSApp].
 * Between checks the last known stable version is persisted in
 * [SharedPreferences]; a notification is fired only when the version
 * string differs from the stored one (and the stored value is non‑empty,
 * which prevents a flood of notifications after a fresh install).
 */
class AutoUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "auto_update_channel"
        private const val PREFS_NAME = "auto_update_prefs"
        private const val KEY_PREFIX_LAST_VERSION = "last_version_"
    }

    override suspend fun doWork(): Result {
        if (!AppSettings.autoUpdateEnabled) return Result.success()

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sharedPrefs = applicationContext.getSharedPreferences(
            PersistentState.STATE_FILENAME, Context.MODE_PRIVATE
        )
        val repoUrls = PersistentState.getSavedTrackers(sharedPrefs)
        val requestQueue = OSSApp.requestQueue

        for (url in repoUrls) {
            val meta = RepoMetaData(url, requestQueue)
            meta.refreshNetwork()
            // Wait for the network call to settle (simplified polling)
            while (meta.state.value == com.monsivamon.android_oss_tracker.repo.MetaDataState.Loading) {
                kotlinx.coroutines.delay(200)
            }
            val latestStable = meta.latestRelease.value ?: continue
            val version = latestStable.version
            val key = KEY_PREFIX_LAST_VERSION + url
            val lastVersion = prefs.getString(key, "")

            if (lastVersion != null && version != lastVersion && lastVersion.isNotEmpty()) {
                showNotification(meta.appName, version)
            }
            prefs.edit().putString(key, version).apply()
        }
        return Result.success()
    }

    // ── Notification builder ──────────────────────────────────

    private fun showNotification(appName: String, version: String) {
        val channel = android.app.NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Update notifications",
            android.app.NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications about new stable releases of tracked repositories"
        }
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.createNotificationChannel(channel)

        // On Android 13+ we need the POST_NOTIFICATIONS permission
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return  // cannot show the notification without the permission
            }
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("New Update: $appName")
            .setContentText("Version $version is now available")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(
            appName.hashCode(),
            notification
        )
    }
}