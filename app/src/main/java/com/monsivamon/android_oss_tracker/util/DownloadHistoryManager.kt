package com.monsivamon.android_oss_tracker.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class DownloadHistoryEntry(
    val assetName: String,
    val repoName: String,
    val downloadUrl: String,
    val timestampMillis: Long,
    val success: Boolean
)

/**
 * Lightweight persistence manager for download history.
 * Entries are stored as a JSON array inside a dedicated SharedPreferences file.
 */
object DownloadHistoryManager {
    private const val PREFS_NAME = "download_history"
    private const val KEY_HISTORY = "history_json"

    fun addEntry(context: Context, entry: DownloadHistoryEntry) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = getHistoryJson(prefs)
        array.put(JSONObject().apply {
            put("assetName", entry.assetName)
            put("repoName", entry.repoName)
            put("downloadUrl", entry.downloadUrl)
            put("timestamp", entry.timestampMillis)
            put("success", entry.success)
        })
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    fun getHistory(context: Context): List<DownloadHistoryEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = getHistoryJson(prefs)
        val list = mutableListOf<DownloadHistoryEntry>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(DownloadHistoryEntry(
                assetName = obj.getString("assetName"),
                repoName = obj.getString("repoName"),
                downloadUrl = obj.getString("downloadUrl"),
                timestampMillis = obj.getLong("timestamp"),
                success = obj.getBoolean("success")
            ))
        }
        return list
    }

    private fun getHistoryJson(prefs: android.content.SharedPreferences): JSONArray {
        val json = prefs.getString(KEY_HISTORY, null) ?: return JSONArray()
        return try { JSONArray(json) } catch (e: Exception) { JSONArray() }
    }
}