package com.monsivamon.android_oss_tracker

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

object PersistentState {
    const val STATE_FILENAME = "app_trackers"
    private const val KEY_REPO_URLS = "repo_urls"
    private const val KEY_ORDER = "repo_order"
    private const val KEY_CUSTOM_NAMES = "custom_names"

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

    fun isTracked(prefs: SharedPreferences, repoUrl: String): Boolean {
        val urls = prefs.getStringSet(KEY_REPO_URLS, emptySet()) ?: emptySet()
        return repoUrl in urls
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
            val current = getSavedTrackers(prefs).toMutableList()
            current.remove(repoUrl)
            saveOrder(prefs, current)
            removeCustomName(prefs, repoUrl)
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
        prefs.edit().remove(KEY_REPO_URLS).remove(KEY_ORDER).remove(KEY_CUSTOM_NAMES).apply()
    }

    fun saveOrder(prefs: SharedPreferences, orderedUrls: List<String>) {
        prefs.edit().putString(KEY_ORDER, orderedUrls.joinToString(",")).apply()
    }

    fun setCustomName(context: Context, repoUrl: String, name: String) {
        val prefs = context.getSharedPreferences(STATE_FILENAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CUSTOM_NAMES, "{}") ?: "{}"
        val map = JSONObject(json)
        map.put(repoUrl, name)
        prefs.edit().putString(KEY_CUSTOM_NAMES, map.toString()).apply()
    }

    fun getCustomName(context: Context, repoUrl: String): String? {
        val prefs = context.getSharedPreferences(STATE_FILENAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CUSTOM_NAMES, "{}") ?: "{}"
        val map = JSONObject(json)
        return if (map.has(repoUrl)) map.getString(repoUrl) else null
    }

    private fun removeCustomName(prefs: SharedPreferences, repoUrl: String) {
        val json = prefs.getString(KEY_CUSTOM_NAMES, "{}") ?: "{}"
        val map = JSONObject(json)
        map.remove(repoUrl)
        prefs.edit().putString(KEY_CUSTOM_NAMES, map.toString()).apply()
    }
}