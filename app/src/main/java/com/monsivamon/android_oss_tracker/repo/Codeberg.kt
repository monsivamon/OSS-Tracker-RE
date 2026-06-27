package com.monsivamon.android_oss_tracker.repo

import arrow.core.Either
import com.android.volley.AuthFailureError
import com.android.volley.NetworkError
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.json.JSONArray
import org.json.JSONObject

/**
 * Codeberg (Gitea) implementation of [CommonRepo].
 *
 * Uses the Codeberg REST API (https://codeberg.org/api/v1).
 * Unlike GitHub, public endpoints on Codeberg do **not** require
 * authentication; sending a token results in a 401 error.  Therefore
 * [fetchReleases] and [fetchBranchName] bypass [ApiUtils] and perform
 * unauthenticated requests directly via Volley.
 */
class Codeberg : CommonRepo() {

    override val providerName = "Codeberg"

    // ---------- Version comparison ----------

    /**
     * Compares two version strings (e.g. "3.42.0") in descending order.
     *
     * Duplicated here because [CommonRepo.compareVersions] is private
     * and we need to call it from [fetchReleases] after overriding.
     */
    private fun compareVersions(a: String, b: String): Int {
        val partsA = a.split(".", "-")
        val partsB = b.split(".", "-")
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val segA = partsA.getOrNull(i) ?: return -1
            val segB = partsB.getOrNull(i) ?: return 1
            val numA = segA.toIntOrNull()
            val numB = segB.toIntOrNull()
            when {
                numA != null && numB != null -> {
                    if (numA != numB) return numA.compareTo(numB)
                }
                numA != null -> return 1
                numB != null -> return -1
                else -> {
                    val cmp = segA.compareTo(segB)
                    if (cmp != 0) return cmp
                }
            }
        }
        return 0
    }

    // ---------- URL helpers ----------

    override fun getUrlOfRawFile(org: String, app: String, branch: String, filepath: String) =
        "https://codeberg.org/$org/$app/raw/branch/$branch/$filepath"

    override fun getFileMetaDataUrl(org: String, app: String, branch: String, file: String) =
        "https://codeberg.org/api/v1/repos/$org/$app/contents/$file?ref=$branch"

    override fun getRepoMetaDataUrl(org: String, app: String) =
        "https://codeberg.org/api/v1/repos/$org/$app"

    override fun getReadmeUrl(org: String, app: String) =
        "https://codeberg.org/$org/$app/raw/HEAD/README.md"

    override fun getReleasesUrl(org: String, app: String) =
        "https://codeberg.org/api/v1/repos/$org/$app/releases"

    override fun getRssFeedUrl(org: String, app: String) =
        "https://codeberg.org/$org/$app/releases.rss"

    override fun getIconUrl(repoUrl: String, branch: String, androidRoot: String): String {
        val org = getOrgName(repoUrl)
        val app = getApplicationName(repoUrl)
        return "https://codeberg.org/$org/$app/raw/branch/$branch/$androidRoot/src/main/res/mipmap-mdpi/ic_launcher.png"
    }

    // ---------- API calls (all unauthenticated) ----------

    /**
     * Fetches the default branch name without authentication.
     */
    override suspend fun fetchBranchName(
        org: String,
        app: String,
        requestQueue: RequestQueue
    ): Either<String, Error> {
        val url = getRepoMetaDataUrl(org, app)

        return suspendCoroutine { cont ->
            val request = object : JsonObjectRequest(
                Method.GET, url, null,
                { response -> cont.resume(Either.Left(response.optString("default_branch", "main"))) },
                { error ->
                    val message = when (error) {
                        is AuthFailureError -> "Authentication error (401). A token should not be sent to Codeberg."
                        is NetworkError     -> "Network error: ${error.message}"
                        else                -> "Error: ${error.message}"
                    }
                    cont.resume(Either.Right(Error(message)))
                }
            ) {
                override fun getHeaders(): MutableMap<String, String> = java.util.HashMap()
            }
            requestQueue.add(request)
        }
    }

    /**
     * Fetches releases directly from the Codeberg API **without**
     * any authentication headers.  This avoids the 401 response that
     * would occur if a GitHub token were blindly forwarded.
     */
    override suspend fun fetchReleases(
        org: String,
        app: String,
        requestQueue: RequestQueue
    ): Either<List<LatestVersionData>, Error> {
        val url = getReleasesUrl(org, app)

        return suspendCoroutine { continuation ->
            val request = object : JsonArrayRequest(
                Method.GET, url, null,
                { response ->
                    try {
                        val all = parseReleasesJsonArray(response)
                        val sorted = all.sortedWith { a, b ->
                            -compareVersions(a.version, b.version)
                        }
                        continuation.resume(Either.Left(sorted))
                    } catch (e: Exception) {
                        continuation.resume(
                            Either.Right(Error("Failed to parse releases: ${e.message}"))
                        )
                    }
                },
                { error ->
                    val message = when (error) {
                        is AuthFailureError ->
                            "Authentication error (401). A token should not be sent to Codeberg."
                        is NetworkError ->
                            "Network error: ${error.message}"
                        else ->
                            "Error: ${error.message}"
                    }
                    continuation.resume(Either.Right(Error(message)))
                }
            ) {
                /** No Authorization header – Codeberg public API rejects tokens. */
                override fun getHeaders(): MutableMap<String, String> = java.util.HashMap()
            }
            requestQueue.add(request)
        }
    }

    // ---------- Parsing ----------

    override fun parseReleasesJsonArray(data: JSONArray): List<LatestVersionData> =
        (0 until data.length()).map { i -> parseReleaseEntry(data.getJSONObject(i)) }

    /**
     * Parses a single release JSON object as returned by the Codeberg/Gitea API.
     */
    private fun parseReleaseEntry(entry: JSONObject): LatestVersionData {
        val rawName = entry.optString("name").takeIf { it.isNotBlank() }
            ?: entry.optString("tag_name").takeIf { it.isNotBlank() }
            ?: "unknown"

        val version = cleanVersionName(rawName) ?: rawName
        val url = entry.optString("html_url").takeIf { it.isNotBlank() }
            ?: entry.optString("url").takeIf { it.isNotBlank() }
            ?: ""
        val date = entry.optString("published_at")
        val isPre = entry.optBoolean("prerelease", false)

        val assetsArray = entry.optJSONArray("assets")
        val assetList = if (assetsArray != null) {
            (0 until assetsArray.length()).mapNotNull { i ->
                val a = assetsArray.getJSONObject(i)
                val downloadUrl = a.optString("browser_download_url").takeIf { it.isNotBlank() }
                downloadUrl?.let {
                    AssetInfo(
                        name = a.optString("name", "unknown"),
                        downloadUrl = it,
                        size = a.optLong("size", 0L)
                    )
                }
            }
        } else emptyList()

        return LatestVersionData(
            version = version,
            url = url,
            date = date,
            assets = assetList,
            isPreRelease = isPre
        )
    }
}