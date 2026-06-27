package com.monsivamon.android_oss_tracker.repo

import arrow.core.Either
import com.android.volley.RequestQueue

/**
 * Handles direct APK downloads.
 * Supports appending "?name=CustomAppName" to the URL to set a custom display name.
 */
class Direct : Repo {
    override val providerName: String = "Direct"

    // Store the complete original URL (including ?name=) in orgName to prevent data loss.
    override fun getOrgName(repoUrl: String): String = repoUrl

    // Extract the custom name from the query parameter, or fallback to the file name.
    override fun getApplicationName(repoUrl: String): String {
        val customName = repoUrl.substringAfter("?name=", "")
        return customName.ifEmpty { repoUrl.substringBefore("?").substringAfterLast("/").removeSuffix(".apk") }
    }

    override fun getIconUrl(repoUrl: String, branch: String, androidRoot: String): String = ""
    override fun getUrlOfRawFile(org: String, app: String, branch: String, filepath: String): String = ""
    override suspend fun fetchBranchName(org: String, app: String, requestQueue: RequestQueue): Either<String, Error> = Either.Left("main")
    override suspend fun tryDetermineAndroidRoot(org: String, app: String, branch: String, requestQueue: RequestQueue): String = ""

    override suspend fun fetchReleases(org: String, app: String, requestQueue: RequestQueue): Either<List<LatestVersionData>, Error> {
        // 'org' holds the complete original URL. We strip the query to get the actual download URL.
        val downloadUrl = org.substringBefore("?")
        val fileName = downloadUrl.substringAfterLast("/")

        val assets = listOf(AssetInfo(fileName, downloadUrl, 0L))

        // Set 'url' to an empty string ("") to prevent browser redirection when the badge is clicked.
        return Either.Left(listOf(LatestVersionData("latest", "", "Direct Link", assets, false)))
    }
}