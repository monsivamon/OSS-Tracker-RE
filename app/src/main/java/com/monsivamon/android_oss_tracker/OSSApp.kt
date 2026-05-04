package com.monsivamon.android_oss_tracker

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.monsivamon.android_oss_tracker.util.AppSettings
import com.monsivamon.android_oss_tracker.util.AutoUpdateWorker
import com.monsivamon.android_oss_tracker.util.ThemeState
import java.util.concurrent.TimeUnit

/**
 * Custom [Application] subclass that initialises shared services
 * (Volley, theming, settings) and schedules the automatic update check
 * when the process starts or when the user changes the auto‑update
 * preferences.
 */
class OSSApp : Application() {

    companion object {
        lateinit var requestQueue: RequestQueue
            private set
    }

    override fun onCreate() {
        super.onCreate()
        requestQueue = Volley.newRequestQueue(this)
        ThemeState.init(this)
        AppSettings.init(this)
        scheduleAutoUpdateCheck()
    }

    /**
     * Schedules or cancels the automatic update check based on the current
     * [AppSettings.autoUpdateEnabled] and [AppSettings.updateIntervalHours].
     *
     * Call this method whenever the auto‑update setting or the check interval
     * is changed.
     */
    fun scheduleAutoUpdateCheck() {
        if (AppSettings.autoUpdateEnabled) {
            val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()
            val workRequest = PeriodicWorkRequestBuilder<AutoUpdateWorker>(
                AppSettings.updateIntervalHours.toLong(), TimeUnit.HOURS
            ).setConstraints(constraints).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "auto_update_check", ExistingPeriodicWorkPolicy.UPDATE, workRequest
            )
        } else {
            WorkManager.getInstance(this).cancelUniqueWork("auto_update_check")
        }
    }
}