package com.monsivamon.android_oss_tracker

import android.content.Context
import android.content.SharedPreferences

/**
 * PersistentState manages a list of tracked repository URLs that survives
 * process restarts.  The list is stored both as a keyed set and as an
 * explicit order so that drag‑and‑drop reordering can be preserved.
 *
 * When the stored list is empty — for example after a factory reset or on
 * first launch — a default set of repositories is automatically seeded.
 */
object PersistentState {
    const val STATE_FILENAME = "app_trackers"
    private const val KEY_REPO_URLS = "repo_urls"
    private const val KEY_ORDER = "repo_order"

    /** Default repositories that are automatically added when the list is empty. */
    private val DEFAULT_REPOS = listOf("https://github.com/monsivamon/OSS-Tracker-RE")

    fun initializeDefaultTrackers(prefs: SharedPreferences) {
        val existing = prefs.getStringSet(KEY_REPO_URLS, emptySet())
        if (existing.isNullOrEmpty()) {
            prefs.edit().putStringSet(KEY_REPO_URLS, DEFAULT_REPOS.toSet()).apply()
            saveOrder(prefs, DEFAULT_REPOS)
        }
    }

    fun getSavedTrackers(prefs: SharedPreferences): List<String> {
        val order = prefs.getString(KEY_ORDER, null)
        if (!order.isNullOrBlank()) return order.split(",").filter { it.isNotBlank() }
        val urls = prefs.getStringSet(KEY_REPO_URLS, emptySet()) ?: emptySet()
        return urls.sortedByDescending { it.lowercase() }
    }

    fun addTracker(prefs: SharedPreferences, repoUrl: String) {
        val urls = prefs.getStringSet(KEY_REPO_URLS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (urls.add(repoUrl)) {
            prefs.edit().putStringSet(KEY_REPO_URLS, urls).apply()
            val current = getSavedTrackers(prefs).toMutableList()
            if (!current.contains(repoUrl)) { current.add(0, repoUrl); saveOrder(prefs, current) }
        }
    }

    fun removeTracker(prefs: SharedPreferences, repoUrl: String) {
        val urls = prefs.getStringSet(KEY_REPO_URLS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (urls.remove(repoUrl)) {
            prefs.edit().putStringSet(KEY_REPO_URLS, urls).apply()
            val current = getSavedTrackers(prefs).toMutableList(); current.remove(repoUrl); saveOrder(prefs, current)
        }
    }

    fun addTrackers(prefs: SharedPreferences, lines: List<String>) {
        val urls = prefs.getStringSet(KEY_REPO_URLS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val current = getSavedTrackers(prefs).toMutableList()
        var changed = false
        lines.forEach { line ->
            if (line.isNotBlank() && urls.add(line.trim())) {
                changed = true
                if (!current.contains(line)) { current.add(0, line); saveOrder(prefs, current) }
            }
        }
        if (changed) prefs.edit().putStringSet(KEY_REPO_URLS, urls).apply()
    }

    fun removeAllTrackers(prefs: SharedPreferences) {
        prefs.edit().remove(KEY_REPO_URLS).remove(KEY_ORDER).apply()
    }

    fun saveOrder(prefs: SharedPreferences, orderedUrls: List<String>) {
        prefs.edit().putString(KEY_ORDER, orderedUrls.joinToString(",")).apply()
    }
}