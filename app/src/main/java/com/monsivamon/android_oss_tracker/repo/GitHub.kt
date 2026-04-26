package com.monsivamon.android_oss_tracker.repo

import org.json.JSONArray

/**
 * Implementation of [CommonRepo] specifically tailored for GitHub repositories.
 * Handles the generation of GitHub API URLs and parses GitHub-specific JSON structures
 * to extract release data and application assets.
 */
class GitHub : CommonRepo() {

    /**
     * Constructs the URL to access a raw file directly from the repository.
     */
    override fun getUrlOfRawFile(org: String, app: String, branch: String, filepath: String): String {
        return "https://raw.githubusercontent.com/$org/$app/$branch/$filepath"
    }

    /**
     * Constructs the GitHub API URL to fetch metadata for a specific file or directory.
     */
    override fun getFileMetaDataUrl(
        org: String,
        app: String,
        branch: String,
        file: String
    ): String {
        return "https://api.github.com/repos/$org/$app/contents/$file"
    }

    /**
     * Constructs the GitHub API URL to fetch general repository metadata.
     */
    override fun getRepoMetaDataUrl(org: String, app: String): String {
        return "https://api.github.com/repos/$org/$app"
    }

    /**
     * Constructs the URL to fetch the raw README.md file from the master branch.
     */
    override fun getReadmeUrl(org: String, app: String): String {
        return "https://raw.githubusercontent.com/$org/$app/master/README.md"
    }

    /**
     * Constructs the GitHub API URL to fetch the list of releases.
     */
    override fun getReleasesUrl(org: String, app: String): String {
        return "https://api.github.com/repos/$org/$app/releases"
    }

    /**
     * Constructs the URL for the repository's Atom RSS feed.
     */
    override fun getRssFeedUrl(org: String, app: String): String {
        return "https://github.com/$org/$app/releases.atom"
    }

    /**
     * Parses the GitHub releases JSON array to extract the latest version information and its associated assets.
     * * @param data The JSONArray returned from the GitHub releases API endpoint.
     * @return A [LatestVersionData] object containing the parsed version, URL, date, and available assets.
     */
    override fun parseReleasesJson(data: JSONArray): LatestVersionData {
        val firstEntry = data.getJSONObject(0)

        val assetList = mutableListOf<AssetInfo>()
        val assetsArray = firstEntry.optJSONArray("assets")

        if (assetsArray != null) {
            for (i in 0 until assetsArray.length()) {
                val assetObj = assetsArray.getJSONObject(i)
                assetList.add(
                    AssetInfo(
                        name = assetObj.getString("name"),
                        downloadUrl = assetObj.getString("browser_download_url"),
                        size = assetObj.optLong("size", 0L)
                    )
                )
            }
        }

        return LatestVersionData(
            version = cleanVersionName(firstEntry.getString("name")) ?: cleanVersionName(firstEntry.getString("tag_name")) ?: "unknown",
            url = firstEntry.getString("html_url"),
            date = firstEntry.getString("published_at"),
            assets = assetList
        )
    }

    /**
     * Constructs the raw URL to fetch the application's launcher icon from the repository's source code.
     */
    override fun getIconUrl(repoUrl: String, branch: String, androidRoot: String): String {
        return "https://github.com/${getOrgName(repoUrl)}/${getApplicationName(repoUrl)}/raw/$branch/$androidRoot/src/main/res/mipmap-mdpi/ic_launcher.png"
    }
}