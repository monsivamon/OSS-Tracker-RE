package com.monsivamon.android_oss_tracker.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Singleton object that manages all user-facing application settings.
 *
 * Settings are persisted using [android.content.SharedPreferences] and exposed as
 * Compose-observable state properties. The object must be initialized once via
 * [init] before any property is accessed.
 */
object AppSettings {
    private const val PREFS_NAME = "app_settings"

    private const val KEY_TRACK_PRE_RELEASES    = "track_pre_releases"
    private const val KEY_INSTALL_AFTER_DL      = "install_after_download"
    private const val KEY_SHOW_NEW_TAB          = "show_new_tab"
    private const val KEY_SHOW_HISTORY_TAB      = "show_history_tab"
    private const val KEY_AUTO_UPDATE_ENABLED   = "auto_update_enabled"
    private const val KEY_UPDATE_INTERVAL_HOURS = "update_interval_hours"
    private const val KEY_DEV_OPTIONS_EXPANDED  = "developer_options_expanded"
    private const val KEY_GITHUB_TOKEN          = "github_token"
    private const val KEY_BACKGROUND_THEME_INDEX = "background_theme_index"
    private const val KEY_SETTINGS_SCROLL_POSITION = "settings_scroll_position"

    private var initialized = false
    private lateinit var prefs: android.content.SharedPreferences

    /**
     * Initializes the settings instance by loading saved values from SharedPreferences.
     *
     * Must be called once, typically in the Application class.
     * Subsequent calls are ignored.
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
        backgroundThemeIndex     = prefs.getInt(KEY_BACKGROUND_THEME_INDEX, 11)
        settingsScrollPosition   = prefs.getInt(KEY_SETTINGS_SCROLL_POSITION, 0)

        initialized = true
    }

    /** Whether pre-release versions of tracked repositories should be included. */
    var trackPreReleases by mutableStateOf(false)
        private set

    /** Whether to automatically initiate installation after a download completes. */
    var installAfterDownload by mutableStateOf(true)
        private set

    /** Whether the "New" tab is visible in the UI. */
    var showNewTab by mutableStateOf(false)
        private set

    /** Whether the "History" tab is visible in the UI. */
    var showHistoryTab by mutableStateOf(false)
        private set

    /** Whether periodic background update checks are enabled. */
    var autoUpdateEnabled by mutableStateOf(true)
        private set

    /** Interval (in hours) between automatic update checks. */
    var updateIntervalHours by mutableStateOf(24)
        private set

    /** Whether the developer options section is expanded in the settings screen. */
    var developerOptionsExpanded by mutableStateOf(false)
        private set

    /** Personal GitHub access token used for authenticated API requests. */
    var githubToken by mutableStateOf("")
        private set

    /** Index of the currently selected pastel gradient palette for the background. */
    var backgroundThemeIndex by mutableStateOf(0)
        private set

    /** Scroll position of the settings screen, used to restore state. */
    var settingsScrollPosition by mutableIntStateOf(0)
        private set

    /**
     * Updates and persists the [trackPreReleases] flag.
     */
    fun setAppTrackPreReleases(enabled: Boolean) {
        trackPreReleases = enabled
        prefs.edit().putBoolean(KEY_TRACK_PRE_RELEASES, enabled).apply()
    }

    /**
     * Updates and persists the [installAfterDownload] flag.
     */
    fun setAppInstallAfterDownload(enabled: Boolean) {
        installAfterDownload = enabled
        prefs.edit().putBoolean(KEY_INSTALL_AFTER_DL, enabled).apply()
    }

    /**
     * Updates and persists the [showNewTab] visibility flag.
     */
    fun setAppShowNewTab(visible: Boolean) {
        showNewTab = visible
        prefs.edit().putBoolean(KEY_SHOW_NEW_TAB, visible).apply()
    }

    /**
     * Updates and persists the [showHistoryTab] visibility flag.
     */
    fun setAppShowHistoryTab(visible: Boolean) {
        showHistoryTab = visible
        prefs.edit().putBoolean(KEY_SHOW_HISTORY_TAB, visible).apply()
    }

    /**
     * Updates and persists the [autoUpdateEnabled] flag.
     */
    fun setAppAutoUpdateEnabled(enabled: Boolean) {
        autoUpdateEnabled = enabled
        prefs.edit().putBoolean(KEY_AUTO_UPDATE_ENABLED, enabled).apply()
    }

    /**
     * Updates and persists the automatic update check [updateIntervalHours].
     */
    fun setAppUpdateIntervalHours(hours: Int) {
        updateIntervalHours = hours
        prefs.edit().putInt(KEY_UPDATE_INTERVAL_HOURS, hours).apply()
    }

    /**
     * Updates and persists the [developerOptionsExpanded] state.
     */
    fun setAppDeveloperOptionsExpanded(expanded: Boolean) {
        developerOptionsExpanded = expanded
        prefs.edit().putBoolean(KEY_DEV_OPTIONS_EXPANDED, expanded).apply()
    }

    /**
     * Updates and persists the GitHub personal access [githubToken].
     */
    fun setAppGithubToken(token: String) {
        githubToken = token
        prefs.edit().putString(KEY_GITHUB_TOKEN, token).apply()
    }

    /**
     * Updates and persists the selected background gradient palette [backgroundThemeIndex].
     */
    fun setAppBackgroundThemeIndex(index: Int) {
        backgroundThemeIndex = index
        prefs.edit().putInt(KEY_BACKGROUND_THEME_INDEX, index).apply()
    }

    /**
     * Updates and persists the settings screen scroll position.
     */
    fun setAppSettingsScrollPosition(position: Int) {
        settingsScrollPosition = position
        prefs.edit().putInt(KEY_SETTINGS_SCROLL_POSITION, position).apply()
    }
}