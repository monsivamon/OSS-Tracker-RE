package com.monsivamon.android_oss_tracker.repo

import com.android.volley.RequestQueue
import arrow.core.Either
import org.json.JSONObject
import java.net.URL

/**
 * F-Droid implementation of [Repo].
 *
 * Uses the official F-Droid v1 API (`api/v1/packages`) to retrieve
 * package version information and APK download URLs.
 */
class FDroid : Repo {
    override val providerName = "F-Droid"

    override fun getOrgName(repoUrl: String): String = "F-Droid"

    /**
     * Extracts the F-Droid package name from the URL path.
     * For example, `https://f-droid.org/ja/packages/de.marmaro.krt.ffupdater/`
     * yields `de.marmaro.krt.ffupdater`.
     */
    override fun getApplicationName(repoUrl: String): String {
        val path = URL(repoUrl).path.trimEnd('/')
        return path.substringAfterLast("packages/").substringBefore("/")
    }

    /**
     * Returns an empty string because the F-Droid API does not provide
     * a direct static icon URL.
     */
    override fun getIconUrl(repoUrl: String, branch: String, androidRoot: String): String {
        return ""
    }

    override fun getUrlOfRawFile(org: String, app: String, branch: String, filepath: String): String = ""

    override suspend fun fetchBranchName(org: String, app: String, requestQueue: RequestQueue): Either<String, Error> {
        return Either.Left("main")
    }

    override suspend fun tryDetermineAndroidRoot(org: String, app: String, branch: String, requestQueue: RequestQueue): String {
        return ""
    }

    override suspend fun fetchReleases(org: String, app: String, requestQueue: RequestQueue): Either<List<LatestVersionData>, Error> {
        val packageName = getApplicationName("https://f-droid.org/packages/$app/")
        val url = "https://f-droid.org/api/v1/packages/$packageName"

        return when (val response = ApiUtils.getJsonObject(url, requestQueue)) {
            is Either.Left -> {
                try {
                    val releases = parseFDroidApi(response.value, packageName)
                    if (releases.isNotEmpty()) {
                        Either.Left(releases)
                    } else {
                        Either.Right(Error("No releases found via F-Droid API"))
                    }
                } catch (e: Exception) {
                    Either.Right(Error("Failed to parse F-Droid API: ${e.message}"))
                }
            }
            is Either.Right -> Either.Right(Error(response.value.message ?: "Network error"))
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

        // The root object contains "suggestedVersionCode", the version F-Droid recommends as stable
        val suggestedCode = data.optInt("suggestedVersionCode", -1)

        val tempMap = mutableListOf<Pair<Int, LatestVersionData>>()

        for (i in 0 until packagesArray.length()) {
            val pkg = packagesArray.getJSONObject(i)
            val versionName = pkg.optString("versionName", "unknown")
            val versionCode = pkg.optInt("versionCode", 0)

            // F-Droid API v1 does not include release dates; use a placeholder
            val date = "F-Droid no data"

            // Standard F-Droid APK download URL format
            val downloadUrl = "https://f-droid.org/repo/${packageName}_${versionCode}.apk"
            val assetName = "${packageName}_${versionCode}.apk"

            // Versions matching the suggested code are stable; others are pre-release
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

        // Return releases sorted by version code descending (newest first)
        return tempMap.sortedByDescending { it.first }.map { it.second }
    }
}