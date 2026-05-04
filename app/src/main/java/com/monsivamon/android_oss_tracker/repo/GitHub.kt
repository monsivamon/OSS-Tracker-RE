package com.monsivamon.android_oss_tracker.repo

import org.json.JSONArray
import org.json.JSONObject

/**
 * GitHub implementation of [CommonRepo].
 *
 * Uses the public GitHub REST API (api.github.com).  Unauthenticated
 * requests are capped at 60 per hour; supply a token via [ApiUtils]
 * to raise that limit.
 */
class GitHub : CommonRepo() {

    override val providerName = "GitHub"

    override fun getUrlOfRawFile(
        org: String, app: String, branch: String, filepath: String
    ): String = "https://raw.githubusercontent.com/$org/$app/$branch/$filepath"

    override fun getFileMetaDataUrl(
        org: String, app: String, branch: String, file: String
    ): String = "https://api.github.com/repos/$org/$app/contents/$file?ref=$branch"

    override fun getRepoMetaDataUrl(org: String, app: String): String =
        "https://api.github.com/repos/$org/$app"

    override fun getReadmeUrl(org: String, app: String): String =
        "https://raw.githubusercontent.com/$org/$app/HEAD/README.md"

    override fun getReleasesUrl(org: String, app: String): String =
        "https://api.github.com/repos/$org/$app/releases"

    override fun getRssFeedUrl(org: String, app: String): String =
        "https://github.com/$org/$app/releases.atom"

    override fun parseReleasesJsonArray(data: JSONArray): List<LatestVersionData> {
        val list = mutableListOf<LatestVersionData>()
        for (i in 0 until data.length()) {
            list.add(parseReleaseEntry(data.getJSONObject(i)))
        }
        return list
    }

    private fun parseReleaseEntry(entry: JSONObject): LatestVersionData {
        val rawName = entry.optString("name").takeIf { it.isNotBlank() }
            ?: entry.optString("tag_name").takeIf { it.isNotBlank() }
            ?: "unknown"
        val version = cleanVersionName(rawName) ?: rawName
        val url  = entry.optString("html_url")
        val date = entry.optString("published_at")

        val assetList = mutableListOf<AssetInfo>()
        val assetsArray = entry.optJSONArray("assets")
        if (assetsArray != null) {
            for (i in 0 until assetsArray.length()) {
                val a = assetsArray.getJSONObject(i)
                val downloadUrl = a.optString("browser_download_url").takeIf { it.isNotBlank() } ?: continue
                assetList.add(AssetInfo(
                    name = a.optString("name", "unknown"),
                    downloadUrl = downloadUrl,
                    size = a.optLong("size", 0L)
                ))
            }
        }
        return LatestVersionData(version = version, url = url, date = date, assets = assetList)
    }

    override fun getIconUrl(repoUrl: String, branch: String, androidRoot: String): String {
        val org = getOrgName(repoUrl)
        val app = getApplicationName(repoUrl)
        return "https://github.com/$org/$app/raw/$branch/$androidRoot/src/main/res/mipmap-mdpi/ic_launcher.png"
    }
}