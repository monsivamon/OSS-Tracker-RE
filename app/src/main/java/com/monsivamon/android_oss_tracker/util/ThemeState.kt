package com.monsivamon.android_oss_tracker.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Persisted theme preference.
 *
 * Call [init] once during [Application.onCreate]; afterwards the selected
 * mode is immediately written to [SharedPreferences] and can be observed
 * by Compose.
 */
object ThemeState {
    private const val PREFS_NAME = "theme_state"
    private const val KEY_THEME_MODE = "theme_mode"

    private var initialized = false
    private lateinit var appContext: Context

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedName = prefs.getString(KEY_THEME_MODE, null)
        if (savedName != null) {
            currentMode = try { ThemeMode.valueOf(savedName) } catch (_: Exception) { ThemeMode.SYSTEM }
        }
        initialized = true
    }

    var currentMode: ThemeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    fun setThemeMode(mode: ThemeMode) {
        currentMode = mode
        if (::appContext.isInitialized) {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        }
    }
}