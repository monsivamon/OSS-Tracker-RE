package com.monsivamon.android_oss_tracker

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast

/**
 * Manages the persistent storage of tracked repository URLs using SharedPreferences.
 * Provides utility functions to add, retrieve, and remove trackers.
 */
object PersistentState {

    const val STATE_FILENAME = "PersistedState"
    private const val APP_TRACKERS = "app_trackers"

    // The default repository loaded when the app is installed for the first time.
    private val defaultTrackers = setOf("https://github.com/monsivamon/OSS-Tracker-RE")

    /**
     * Retrieves the current set of saved repository URLs.
     * * @param sharedPreferences The SharedPreferences instance.
     * @return A set of repository URLs. Returns the default tracker if none exist.
     */
    fun getSavedTrackers(sharedPreferences: SharedPreferences): Set<String> {
        return sharedPreferences.getStringSet(APP_TRACKERS, defaultTrackers) ?: defaultTrackers
    }

    /**
     * Adds a single repository to the tracked list and displays a confirmation Toast.
     * * @param ctx The application context for displaying the Toast.
     * @param sharedPreferences The SharedPreferences instance.
     * @param appName The human-readable name of the application.
     * @param repo The GitHub/GitLab repository URL to add.
     */
    fun addTracker(ctx: Context, sharedPreferences: SharedPreferences, appName: String, repo: String) {
        val existing = sharedPreferences.getStringSet(APP_TRACKERS, defaultTrackers) ?: defaultTrackers
        val newList = existing.toMutableSet()

        newList.add(repo)
        newList.remove("") // Ensure no empty strings are persisted

        sharedPreferences.edit().putStringSet(APP_TRACKERS, newList).apply()
        Toast.makeText(ctx, "Added $appName to your trackers", Toast.LENGTH_LONG).show()
    }

    /**
     * Bulk adds a list of repositories to the tracked list and displays a confirmation Toast.
     * * @param ctx The application context for displaying the Toast.
     * @param sharedPreferences The SharedPreferences instance.
     * @param repos A list of repository URLs to add.
     */
    fun addTrackers(ctx: Context, sharedPreferences: SharedPreferences, repos: List<String>) {
        val existing = sharedPreferences.getStringSet(APP_TRACKERS, defaultTrackers) ?: defaultTrackers
        val newList = existing.toMutableSet()

        newList.addAll(repos)

        sharedPreferences.edit().putStringSet(APP_TRACKERS, newList).apply()
        Toast.makeText(ctx, "Added ${repos.size} trackers", Toast.LENGTH_LONG).show()
    }

    /**
     * Removes a specific repository from the tracked list and displays a confirmation Toast.
     * * @param ctx The application context for displaying the Toast.
     * @param sharedPreferences The SharedPreferences instance.
     * @param appName The human-readable name of the application.
     * @param repo The GitHub/GitLab repository URL to remove.
     */
    fun removeTracker(ctx: Context, sharedPreferences: SharedPreferences, appName: String, repo: String) {
        val existing = sharedPreferences.getStringSet(APP_TRACKERS, defaultTrackers) ?: defaultTrackers
        val newList = existing.toMutableSet()

        newList.remove(repo)

        sharedPreferences.edit().putStringSet(APP_TRACKERS, newList).apply()
        Toast.makeText(ctx, "Deleted $appName from your trackers", Toast.LENGTH_LONG).show()
    }

    /**
     * Clears all tracked repositories, resetting the storage to an empty state.
     * * @param ctx The application context for displaying the Toast.
     * @param sharedPreferences The SharedPreferences instance.
     */
    fun removeAllTrackers(ctx: Context, sharedPreferences: SharedPreferences) {
        val emptyList = emptySet<String>()

        sharedPreferences.edit().putStringSet(APP_TRACKERS, emptyList).apply()
        Toast.makeText(ctx, "Deleted all trackers", Toast.LENGTH_LONG).show()
    }
}