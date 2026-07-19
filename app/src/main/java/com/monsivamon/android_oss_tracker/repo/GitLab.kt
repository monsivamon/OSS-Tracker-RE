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
import java.net.URLEncoder

/**
 * GitLab API v4 implementation of [CommonRepo].
 *
 * All API calls are made without authentication, because a GitHub token
 * is not valid for GitLab instances and would cause 401 errors.
 */
class GitLab : CommonRepo() {

    override val providerName = "GitLab"

    private fun projectId(org: String, app: String): String =
        URLEncoder.encode("$org/$app", "utf-8")

    override fun getUrlOfRawFile(org: String, app: String, branch: String, filepath: String) =
        "https://gitlab.com/$org/$app/-/raw/$branch/$filepath"

    override fun getFileMetaDataUrl(org: String, app: String, branch: String, file: String) =
        "https://gitlab.com/api/v4/projects/${projectId(org, app)}/repository/files/${URLEncoder.encode(file, "utf-8")}?ref=$branch"

    override fun getRepoMetaDataUrl(org: String, app: String) =
        "https://gitlab.com/api/v4/projects/${projectId(org, app)}"

    override fun getReadmeUrl(org: String, app: String) =
        "https://gitlab.com/$org/$app/-/raw/master/README.md"

    override fun getReleasesUrl(org: String, app: String) =
        "https://gitlab.com/api/v4/projects/${projectId(org, app)}/releases"

    override fun getRssFeedUrl(org: String, app: String) =
        "https://gitlab.com/$org/$app/-/tags?format=atom"

    override fun getIconUrl(repoUrl: String, branch: String, androidRoot: String): String {
        val org = getOrgName(repoUrl)
        val app = getApplicationName(repoUrl)
        return "https://gitlab.com/$org/$app/-/raw/$branch/$androidRoot/src/main/res/mipmap-mdpi/ic_launcher.png"
    }

    // ---------- Unauthenticated API calls ----------

    override suspend fun fetchBranchName(
        org: String, app: String, requestQueue: RequestQueue
    ): Either<String, Error> {
        val url = getRepoMetaDataUrl(org, app)
        return suspendCoroutine { cont ->
            val request = object : JsonObjectRequest(
                Method.GET, url, null,
                { response -> cont.resume(Either.Left(response.optString("default_branch", "main"))) },
                { error ->
                    val msg = when (error) {
                        is AuthFailureError -> "Authentication error (401)."
                        is NetworkError -> "Network error: ${error.message}"
                        else -> "Error: ${error.message}"
                    }
                    cont.resume(Either.Right(Error(msg)))
                }
            ) {
                override fun getHeaders(): MutableMap<String, String> = java.util.HashMap()
            }
            requestQueue.add(request)
        }
    }

    override suspend fun fetchReleases(
        org: String, app: String, requestQueue: RequestQueue
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
                        is AuthFailureError -> "Authentication error (401)."
                        is NetworkError -> "Network error: ${error.message}"
                        else -> "Error: ${error.message}"
                    }
                    continuation.resume(Either.Right(Error(message)))
                }
            ) {
                override fun getHeaders(): MutableMap<String, String> = java.util.HashMap()
            }
            requestQueue.add(request)
        }
    }

    // Version comparison duplicated from CommonRepo (private there)
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

    // ---------- Parsing ----------

    override fun parseReleasesJsonArray(data: JSONArray): List<LatestVersionData> =
        (0 until data.length()).map { i -> parseReleaseEntry(data.getJSONObject(i)) }

    private fun parseReleaseEntry(entry: JSONObject): LatestVersionData {
        val rawName = entry.optString("name").takeIf { it.isNotBlank() }
            ?: entry.optString("tag_name").takeIf { it.isNotBlank() }
            ?: "unknown"

        val version = cleanVersionName(rawName) ?: rawName
        val url = entry.optJSONObject("_links")?.optString("self").orEmpty()
        val date = entry.optString("released_at")

        val linksArray = entry.optJSONObject("assets")?.optJSONArray("links")
        val assetList = if (linksArray != null) {
            (0 until linksArray.length()).mapNotNull { i ->
                val link = linksArray.getJSONObject(i)
                val downloadUrl = link.optString("direct_asset_url").takeIf { it.isNotBlank() }
                    ?: link.optString("url").takeIf { it.isNotBlank() }

                downloadUrl?.let {
                    AssetInfo(
                        name = link.optString("name", "unknown"),
                        downloadUrl = it,
                        size = 0L
                    )
                }
            }
        } else emptyList()

        return LatestVersionData(version = version, url = url, date = date, assets = assetList)
    }
}