package com.monsivamon.android_oss_tracker.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Centralised application settings persisted in [SharedPreferences].
 *
 * Every preference is observable by Compose and is written to storage
 * immediately when changed.  Call [init] once during [Application.onCreate]
 * to load the current values.
 *
 * ### Preference categories
 * - **Tracking** – pre-release visibility, install behaviour, automatic update checks.
 * - **Interface** – bottom-bar tab visibility, developer options expansion.
 * - **API** – GitHub Personal Access Token for authenticated requests.
 */
object AppSettings {
    private const val PREFS_NAME = "app_settings"

    // ── Storage keys ────────────────────────────────────────────
    private const val KEY_TRACK_PRE_RELEASES    = "track_pre_releases"
    private const val KEY_INSTALL_AFTER_DL      = "install_after_download"
    private const val KEY_SHOW_NEW_TAB          = "show_new_tab"
    private const val KEY_SHOW_HISTORY_TAB      = "show_history_tab"
    private const val KEY_AUTO_UPDATE_ENABLED   = "auto_update_enabled"
    private const val KEY_UPDATE_INTERVAL_HOURS = "update_interval_hours"
    private const val KEY_DEV_OPTIONS_EXPANDED  = "developer_options_expanded"
    private const val KEY_GITHUB_TOKEN          = "github_token"

    private var initialized = false
    private lateinit var prefs: android.content.SharedPreferences

    /**
     * Loads all preferences from storage.  Must be called once before
     * any property is read, typically from [Application.onCreate].
     */
    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        trackPreReleases         = prefs.getBoolean(KEY_TRACK_PRE_RELEASES, false)
        installAfterDownload     = prefs.getBoolean(KEY_INSTALL_AFTER_DL, true)
        showNewTab               = prefs.getBoolean(KEY_SHOW_NEW_TAB, false)
        showHistoryTab           = prefs.getBoolean(KEY_SHOW_HISTORY_TAB, false)
        autoUpdateEnabled        = prefs.getBoolean(KEY_AUTO_UPDATE_ENABLED, true)
        updateIntervalHours      = prefs.getInt(KEY_UPDATE_INTERVAL_HOURS, 24)
        developerOptionsExpanded = prefs.getBoolean(KEY_DEV_OPTIONS_EXPANDED, false)
        githubToken              = prefs.getString(KEY_GITHUB_TOKEN, "") ?: ""

        initialized = true
    }

    // ── Observable properties ──────────────────────────────────

    /** Whether pre‑release versions are fetched and displayed. */
    var trackPreReleases by mutableStateOf(false)
        private set

    /** When `true`, the “Tap to install” button is shown after a download completes. */
    var installAfterDownload by mutableStateOf(true)
        private set

    /** Whether the *New* tab is visible in the bottom navigation bar. */
    var showNewTab by mutableStateOf(false)
        private set

    /** Whether the *History* tab is visible in the bottom navigation bar. */
    var showHistoryTab by mutableStateOf(false)
        private set

    /** Whether the automatic update check is enabled. */
    var autoUpdateEnabled by mutableStateOf(true)
        private set

    /** Interval (in hours) at which the automatic update check runs. */
    var updateIntervalHours by mutableStateOf(24)
        private set

    /** Whether the Developer Options section is expanded in Settings. */
    var developerOptionsExpanded by mutableStateOf(false)
        private set

    /** GitHub Personal Access Token used for API authentication. */
    var githubToken by mutableStateOf("")
        private set

    // ── Setters (persisted immediately) ─────────────────────────

    fun setAppTrackPreReleases(enabled: Boolean) {
        trackPreReleases = enabled
        prefs.edit().putBoolean(KEY_TRACK_PRE_RELEASES, enabled).apply()
    }

    fun setAppInstallAfterDownload(enabled: Boolean) {
        installAfterDownload = enabled
        prefs.edit().putBoolean(KEY_INSTALL_AFTER_DL, enabled).apply()
    }

    fun setAppShowNewTab(visible: Boolean) {
        showNewTab = visible
        prefs.edit().putBoolean(KEY_SHOW_NEW_TAB, visible).apply()
    }

    fun setAppShowHistoryTab(visible: Boolean) {
        showHistoryTab = visible
        prefs.edit().putBoolean(KEY_SHOW_HISTORY_TAB, visible).apply()
    }

    fun setAppAutoUpdateEnabled(enabled: Boolean) {
        autoUpdateEnabled = enabled
        prefs.edit().putBoolean(KEY_AUTO_UPDATE_ENABLED, enabled).apply()
    }

    fun setAppUpdateIntervalHours(hours: Int) {
        updateIntervalHours = hours
        prefs.edit().putInt(KEY_UPDATE_INTERVAL_HOURS, hours).apply()
    }

    fun setAppDeveloperOptionsExpanded(expanded: Boolean) {
        developerOptionsExpanded = expanded
        prefs.edit().putBoolean(KEY_DEV_OPTIONS_EXPANDED, expanded).apply()
    }

    /**
     * Stores the GitHub token and immediately persists it to
     * [SharedPreferences].
     */
    fun setAppGithubToken(token: String) {
        githubToken = token
        prefs.edit().putString(KEY_GITHUB_TOKEN, token).apply()
    }
}