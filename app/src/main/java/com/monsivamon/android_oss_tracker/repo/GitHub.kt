package com.monsivamon.android_oss_tracker.repo

import org.json.JSONArray
import org.json.JSONObject

/**
 * GitHub implementation of [CommonRepo].
 *
 * Uses the public GitHub REST API (`api.github.com`). Unauthenticated requests
 * are rate‑limited to 60 per hour; supply a personal access token via [ApiUtils]
 * to raise this limit.
 */
class GitHub : CommonRepo() {

    override val providerName = "GitHub"

    override fun getUrlOfRawFile(org: String, app: String, branch: String, filepath: String) =
        "https://raw.githubusercontent.com/$org/$app/$branch/$filepath"

    override fun getFileMetaDataUrl(org: String, app: String, branch: String, file: String) =
        "https://api.github.com/repos/$org/$app/contents/$file?ref=$branch"

    override fun getRepoMetaDataUrl(org: String, app: String) =
        "https://api.github.com/repos/$org/$app"

    override fun getReadmeUrl(org: String, app: String) =
        "https://raw.githubusercontent.com/$org/$app/HEAD/README.md"

    override fun getReleasesUrl(org: String, app: String) =
        "https://api.github.com/repos/$org/$app/releases"

    override fun getRssFeedUrl(org: String, app: String) =
        "https://github.com/$org/$app/releases.atom"

    override fun getIconUrl(repoUrl: String, branch: String, androidRoot: String): String {
        val org = getOrgName(repoUrl)
        val app = getApplicationName(repoUrl)
        return "https://github.com/$org/$app/raw/$branch/$androidRoot/src/main/res/mipmap-mdpi/ic_launcher.png"
    }

    /**
     * Parses a JSON array of releases into a list of [LatestVersionData].
     */
    override fun parseReleasesJsonArray(data: JSONArray): List<LatestVersionData> =
        (0 until data.length()).map { i -> parseReleaseEntry(data.getJSONObject(i)) }

    /**
     * Parses a single release JSON object, extracting the version,
     * URL, date, assets, and the official `prerelease` flag.
     */
    private fun parseReleaseEntry(entry: JSONObject): LatestVersionData {
        val rawName = entry.optString("name").takeIf { it.isNotBlank() }
            ?: entry.optString("tag_name").takeIf { it.isNotBlank() }
            ?: "unknown"

        val version = cleanVersionName(rawName) ?: rawName
        val url = entry.optString("html_url")
        val date = entry.optString("published_at")

        // Use the official 'prerelease' field from the GitHub API
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