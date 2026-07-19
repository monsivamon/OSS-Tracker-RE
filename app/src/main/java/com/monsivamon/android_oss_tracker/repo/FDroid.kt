package com.monsivamon.android_oss_tracker.repo

import arrow.core.Either
import com.android.volley.*
import com.android.volley.toolbox.JsonObjectRequest
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.json.JSONObject
import java.net.URL

/**
 * F-Droid implementation of [Repo].
 *
 * Uses the official F-Droid v1 API (`api/v1/packages`) to retrieve
 * package version information and APK download URLs.
 * All requests are sent without authentication headers to avoid
 * 401 errors caused by a GitHub token being forwarded.
 */
class FDroid : Repo {
    override val providerName = "F-Droid"

    override fun getOrgName(repoUrl: String): String = "F-Droid"

    /**
     * Extracts the F-Droid package name from a URL or plain package name.
     *
     * If the input is a valid URL containing `/packages/`, the package name
     * is extracted from the path.  Otherwise the input string is returned
     * as-is (e.g. when a package name is passed directly).
     */
    override fun getApplicationName(repoUrl: String): String {
        return try {
            val url = URL(repoUrl)
            val path = url.path.trimEnd('/')
            if (path.contains("/packages/")) {
                path.substringAfterLast("packages/").substringBefore("/")
            } else {
                repoUrl   // fallback for URLs that don't contain /packages/
            }
        } catch (e: Exception) {
            // Not a valid URL – probably a raw package name
            repoUrl
        }
    }

    override fun getIconUrl(repoUrl: String, branch: String, androidRoot: String): String = ""
    override fun getUrlOfRawFile(org: String, app: String, branch: String, filepath: String): String = ""

    override suspend fun fetchBranchName(org: String, app: String, requestQueue: RequestQueue): Either<String, Error> {
        return Either.Left("main")
    }

    override suspend fun tryDetermineAndroidRoot(org: String, app: String, branch: String, requestQueue: RequestQueue): String = ""

    /**
     * Fetches package information from the F-Droid API **without** authentication.
     *
     * The [app] parameter is expected to be the F-Droid package name (already
     * extracted by [getApplicationName] when the repository was added).
     */
    override suspend fun fetchReleases(org: String, app: String, requestQueue: RequestQueue): Either<List<LatestVersionData>, Error> {
        // 'app' is already the package name – no need to re-parse
        val packageName = app
        if (packageName.isBlank()) {
            return Either.Right(Error("Could not determine F-Droid package name."))
        }

        val url = "https://f-droid.org/api/v1/packages/$packageName"

        return suspendCoroutine { continuation ->
            val request = object : JsonObjectRequest(
                Method.GET, url, null,
                { response ->
                    try {
                        val releases = parseFDroidApi(response, packageName)
                        if (releases.isNotEmpty()) {
                            continuation.resume(Either.Left(releases))
                        } else {
                            continuation.resume(Either.Right(Error("No releases found via F-Droid API")))
                        }
                    } catch (e: Exception) {
                        continuation.resume(Either.Right(Error("Failed to parse F-Droid API: ${e.message}")))
                    }
                },
                { error ->
                    val message = when {
                        error.networkResponse?.statusCode == 403 ->
                            "F-Droid API returned 403 Forbidden for package '$packageName'. Check the package name."
                        error.networkResponse?.statusCode == 404 ->
                            "Package '$packageName' not found on F-Droid."
                        error is AuthFailureError ->
                            "Authentication error (401). A token should not be sent to F-Droid."
                        error is NetworkError ->
                            "Network error: ${error.message}"
                        else ->
                            "Error (${error.networkResponse?.statusCode ?: "?"}): ${error.message}"
                    }
                    continuation.resume(Either.Right(Error(message)))
                }
            ) {
                override fun getHeaders(): MutableMap<String, String> = java.util.HashMap()
            }
            // Disable retries to avoid multiple identical error logs
            request.retryPolicy = DefaultRetryPolicy(
                10000,   // 10 seconds timeout
                0,       // no retries
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
            requestQueue.add(request)
        }
    }

    /**
     * Parses the F-Droid v1 API response into a list of [LatestVersionData].
     *
     * Versions whose code matches the `suggestedVersionCode` are treated as stable;
     * all others are marked as pre‑releases.
     */
    private fun parseFDroidApi(data: JSONObject, packageName: String): List<LatestVersionData> {
        val packagesArray = data.optJSONArray("packages") ?: return emptyList()
        val suggestedCode = data.optInt("suggestedVersionCode", -1)

        val tempMap = mutableListOf<Pair<Int, LatestVersionData>>()

        for (i in 0 until packagesArray.length()) {
            val pkg = packagesArray.getJSONObject(i)
            val versionName = pkg.optString("versionName", "unknown")
            val versionCode = pkg.optInt("versionCode", 0)
            val date = "F-Droid no data"

            val downloadUrl = "https://f-droid.org/repo/${packageName}_${versionCode}.apk"
            val assetName = "${packageName}_${versionCode}.apk"
            val isPre = versionCode != suggestedCode

            tempMap.add(
                versionCode to LatestVersionData(
                    version = versionName,
                    url = "https://f-droid.org/packages/$packageName/",
                    date = date,
                    assets = listOf(AssetInfo(name = assetName, downloadUrl = downloadUrl, size = 0L)),
                    isPreRelease = isPre
                )
            )
        }

        return tempMap.sortedByDescending { it.first }.map { it.second }
    }
}