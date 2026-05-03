package com.monsivamon.android_oss_tracker.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A single entry in the download history.
 *
 * @property assetName       The file name of the downloaded asset.
 * @property repoName        Human‑readable repository name.
 * @property downloadUrl     Full URL used for the download.
 * @property timestampMillis Epoch millis when the download completed.
 * @property success         Whether the download finished without errors.
 * @property releaseType     "Stable" or "Pre-release".
 * @property version         The resolved version string (e.g. "1.24.0").
 * @property errorType       Machine‑readable error category (empty on success).
 */
data class DownloadHistoryEntry(
    val assetName: String,
    val repoName: String,
    val downloadUrl: String,
    val timestampMillis: Long,
    val success: Boolean,
    val releaseType: String = "Stable",
    val version: String = "",
    val errorType: String = ""
)

/**
 * Human‑readable error categories used for display in the history UI.
 */
enum class ErrorType(val label: String, val description: String) {
    CANCELLED("Cancelled", "Download was cancelled or app restarted"),
    API_LIMIT("API Rate Limit", "Too many requests – try again later"),
    ACCESS_DENIED("403 Forbidden", "Access denied – may require authentication"),
    NETWORK("Network Error", "Check your internet connection"),
    HTTP_CLIENT_ERROR("Client Error", "Invalid request (4xx)"),
    HTTP_SERVER_ERROR("Server Error", "Remote server error (5xx)"),
    STORAGE("Storage Error", "Not enough space or permission denied"),
    UNKNOWN("Unknown Error", "An unexpected error occurred");

    companion object {
        /** Classify an exception into one of the predefined error types. */
        fun classify(exception: Exception?): ErrorType {
            val message = exception?.message?.lowercase() ?: ""
            return when {
                exception is java.util.concurrent.CancellationException
                        || message.contains("cancel")     -> CANCELLED
                message.contains("403")
                        || message.contains("forbidden")  -> ACCESS_DENIED
                message.contains("429")
                        || message.contains("rate limit") -> API_LIMIT
                message.contains("no space")
                        || message.contains("enospc")
                        || message.contains("storage")    -> STORAGE
                message.contains("unknownhost")
                        || message.contains("timeout")
                        || message.contains("connect")    -> NETWORK
                message.contains("http 4")            -> HTTP_CLIENT_ERROR
                message.contains("http 5")            -> HTTP_SERVER_ERROR
                else                                 -> UNKNOWN
            }
        }
    }
}

/**
 * Lightweight persistence for [DownloadHistoryEntry] backed by a JSON
 * array stored in a dedicated [SharedPreferences] file.
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
            put("releaseType", entry.releaseType)
            put("version", entry.version)
            put("errorType", entry.errorType)
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
                assetName       = obj.getString("assetName"),
                repoName        = obj.getString("repoName"),
                downloadUrl     = obj.getString("downloadUrl"),
                timestampMillis = obj.getLong("timestamp"),
                success         = obj.getBoolean("success"),
                releaseType     = obj.optString("releaseType", "Stable"),
                version         = obj.optString("version", ""),
                errorType       = obj.optString("errorType", "")
            ))
        }
        return list
    }

    private fun getHistoryJson(prefs: android.content.SharedPreferences): JSONArray {
        val json = prefs.getString(KEY_HISTORY, null) ?: return JSONArray()
        return try { JSONArray(json) } catch (_: Exception) { JSONArray() }
    }
}