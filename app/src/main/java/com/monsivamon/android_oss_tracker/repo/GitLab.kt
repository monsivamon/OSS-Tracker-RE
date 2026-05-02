package com.monsivamon.android_oss_tracker.repo

import org.json.JSONArray

class GitLab : CommonRepo() {

    override fun getUrlOfRawFile(
        org: String, app: String, branch: String, filepath: String
    ): String = "https://gitlab.com/$org/$app/-/raw/$branch/$filepath"

    override fun getFileMetaDataUrl(
        org: String, app: String, branch: String, file: String
    ): String {
        val repoEncoded = java.net.URLEncoder.encode("$org/$app", "utf-8")
        val fileEncoded = java.net.URLEncoder.encode(file, "utf-8")
        return "https://gitlab.com/api/v4/projects/$repoEncoded/repository/files/$fileEncoded?ref=$branch"
    }

    override fun getRepoMetaDataUrl(org: String, app: String): String {
        val repoEncoded = java.net.URLEncoder.encode("$org/$app", "utf-8")
        return "https://gitlab.com/api/v4/projects/$repoEncoded"
    }

    override fun getReadmeUrl(org: String, app: String): String =
        "https://gitlab.com/$org/$app/-/raw/master/README.md"

    override fun getReleasesUrl(org: String, app: String): String {
        val repoEncoded = java.net.URLEncoder.encode("$org/$app", "utf-8")
        return "https://gitlab.com/api/v4/projects/$repoEncoded/releases"
    }

    override fun getRssFeedUrl(org: String, app: String): String =
        "https://gitlab.com/$org/$app/-/tags?format=atom"

    override fun parseReleaseEntry(entry: org.json.JSONObject): LatestVersionData {
        val rawName = entry.optString("name").takeIf { it.isNotBlank() }
            ?: entry.optString("tag_name").takeIf { it.isNotBlank() }
            ?: "unknown"
        val version = cleanVersionName(rawName) ?: rawName
        val url = entry.optJSONObject("_links")?.optString("self") ?: ""
        val date = entry.optString("released_at")

        val assetList = mutableListOf<AssetInfo>()
        val assetsObj = entry.optJSONObject("assets")
        val linksArray = assetsObj?.optJSONArray("links")
        if (linksArray != null) {
            for (i in 0 until linksArray.length()) {
                val linkObj = linksArray.getJSONObject(i)
                val downloadUrl = linkObj.optString("direct_asset_url").takeIf { it.isNotBlank() }
                    ?: linkObj.optString("url").takeIf { it.isNotBlank() }
                    ?: continue
                assetList.add(
                    AssetInfo(
                        name = linkObj.optString("name", "unknown"),
                        downloadUrl = downloadUrl,
                        size = 0L
                    )
                )
            }
        }
        return LatestVersionData(version = version, url = url, date = date, assets = assetList)
    }

    override fun getIconUrl(repoUrl: String, branch: String, androidRoot: String): String {
        val org = getOrgName(repoUrl)
        val app = getApplicationName(repoUrl)
        return "https://gitlab.com/$org/$app/-/raw/$branch/$androidRoot/src/main/res/mipmap-mdpi/ic_launcher.png"
    }
}