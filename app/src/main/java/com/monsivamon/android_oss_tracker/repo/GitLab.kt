package com.monsivamon.android_oss_tracker.repo

import org.json.JSONArray
import java.net.URLEncoder

/**
 * Implementation of [CommonRepo] specifically tailored for GitLab repositories.
 * Handles the generation of GitLab API v4 URLs and parses GitLab-specific JSON structures
 * to extract release data and application assets.
 */
class GitLab : CommonRepo() {

    /**
     * Constructs the URL to access a raw file directly from the repository.
     */
    override fun getUrlOfRawFile(org: String, app: String, branch: String, filepath: String): String {
        return "https://gitlab.com/$org/$app/-/raw/$branch/$filepath"
    }

    /**
     * Constructs the GitLab API URL to fetch metadata for a specific file or directory.
     * Uses URL encoding for the project path and file path as required by the GitLab API.
     */
    override fun getFileMetaDataUrl(
        org: String,
        app: String,
        branch: String,
        file: String
    ): String {
        val repoEncoded = URLEncoder.encode("$org/$app", "utf-8")
        val fileEncoded = URLEncoder.encode(file, "utf-8")
        return "https://gitlab.com/api/v4/projects/$repoEncoded/repository/files/$fileEncoded?ref=$branch"
    }

    /**
     * Constructs the GitLab API URL to fetch general repository metadata.
     */
    override fun getRepoMetaDataUrl(org: String, app: String): String {
        val repoEncoded = URLEncoder.encode("$org/$app", "utf-8")
        return "https://gitlab.com/api/v4/projects/$repoEncoded"
    }

    /**
     * Constructs the URL to fetch the raw README.md file from the master branch.
     * Fixed: Now correctly points to gitlab.com instead of githubusercontent.com.
     */
    override fun getReadmeUrl(org: String, app: String): String {
        return "https://gitlab.com/$org/$app/-/raw/master/README.md"
    }

    /**
     * Constructs the GitLab API URL to fetch the list of releases.
     */
    override fun getReleasesUrl(org: String, app: String): String {
        val repoEncoded = URLEncoder.encode("$org/$app", "utf-8")
        return "https://gitlab.com/api/v4/projects/$repoEncoded/releases"
    }

    /**
     * Constructs the URL for the repository's Atom RSS feed based on tags.
     */
    override fun getRssFeedUrl(org: String, app: String): String {
        return "https://gitlab.com/$org/$app/-/tags?format=atom"
    }

    /**
     * Parses the GitLab releases JSON array to extract the latest version information and its associated assets.
     * Maps the GitLab 'assets.links' array into downloadable components.
     *
     * @param data The JSONArray returned from the GitLab releases API endpoint.
     * @return A [LatestVersionData] object containing the parsed version, URL, date, and available assets.
     */
    override fun parseReleasesJson(data: JSONArray): LatestVersionData {
        val firstEntry = data.getJSONObject(0)

        // Parse downloadable assets (APKs) from GitLab's specific JSON structure
        val assetList = mutableListOf<AssetInfo>()
        val assetsObj = firstEntry.optJSONObject("assets")
        val linksArray = assetsObj?.optJSONArray("links")

        if (linksArray != null) {
            for (i in 0 until linksArray.length()) {
                val linkObj = linksArray.getJSONObject(i)
                assetList.add(
                    AssetInfo(
                        name = linkObj.getString("name"),
                        // Fallback to "url" if "direct_asset_url" is not present
                        downloadUrl = linkObj.optString("direct_asset_url", linkObj.getString("url")),
                        size = 0L // GitLab API usually does not provide file size in the links array
                    )
                )
            }
        }

        return LatestVersionData(
            version = cleanVersionName(firstEntry.getString("name")) ?: cleanVersionName(firstEntry.getString("tag_name")) ?: "unknown",
            url = firstEntry.getJSONObject("_links").getString("self"),
            date = firstEntry.getString("released_at"),
            assets = assetList
        )
    }

    /**
     * Constructs the raw URL to fetch the application's launcher icon from the repository's source code.
     */
    override fun getIconUrl(repoUrl: String, branch: String, androidRoot: String): String {
        return "https://gitlab.com/${getOrgName(repoUrl)}/${getApplicationName(repoUrl)}/-/raw/$branch/$androidRoot/src/main/res/mipmap-mdpi/ic_launcher.png"
    }
}