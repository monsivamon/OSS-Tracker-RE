package com.monsivamon.android_oss_tracker.repo

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * GitLab API v4 implementation of [CommonRepo].
 *
 * Targets gitlab.com by default. For self‑hosted GitLab instances the base URL
 * must be adjusted.
 */
class GitLab : CommonRepo() {

    override val providerName = "GitLab"

    /**
     * Returns the URL-encoded project identifier `org%2Fapp`.
     */
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

    /**
     * Parses a JSON array of releases into a list of [LatestVersionData].
     */
    override fun parseReleasesJsonArray(data: JSONArray): List<LatestVersionData> =
        (0 until data.length()).map { i -> parseReleaseEntry(data.getJSONObject(i)) }

    /**
     * Parses a single release JSON object, extracting the version, URL, date,
     * and attached assets (links).
     */
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