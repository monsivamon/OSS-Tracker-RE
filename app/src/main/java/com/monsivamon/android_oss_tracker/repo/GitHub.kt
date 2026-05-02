package com.monsivamon.android_oss_tracker.repo

import org.json.JSONArray

class GitHub : CommonRepo() {

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

    override fun parseReleaseEntry(entry: org.json.JSONObject): LatestVersionData {
        val rawName = entry.optString("name").takeIf { it.isNotBlank() }
            ?: entry.optString("tag_name").takeIf { it.isNotBlank() }
            ?: "unknown"
        val version = cleanVersionName(rawName) ?: rawName
        val url = entry.optString("html_url")
        val date = entry.optString("published_at")

        val assetList = mutableListOf<AssetInfo>()
        val assetsArray = entry.optJSONArray("assets")
        if (assetsArray != null) {
            for (i in 0 until assetsArray.length()) {
                val assetObj = assetsArray.getJSONObject(i)
                val downloadUrl = assetObj.optString("browser_download_url")
                    .takeIf { it.isNotBlank() } ?: continue
                assetList.add(
                    AssetInfo(
                        name = assetObj.optString("name", "unknown"),
                        downloadUrl = downloadUrl,
                        size = assetObj.optLong("size", 0L)
                    )
                )
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