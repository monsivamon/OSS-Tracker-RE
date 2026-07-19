package com.monsivamon.android_oss_tracker.repo

import arrow.core.Either
import com.android.volley.RequestQueue

/**
 * Handles direct APK downloads.
 *
 * The repository URL is the direct link to the APK file.
 * A custom display name can be assigned via [PersistentState.setCustomName],
 * otherwise the filename (without extension) is used.
 */
class Direct : Repo {
    override val providerName: String = "Direct"

    /**
     * The "org name" is the complete URL so that [fetchReleases]
     * can extract the actual download URL by removing query parameters.
     */
    override fun getOrgName(repoUrl: String): String = repoUrl

    /**
     * Returns the application name derived from the APK file name.
     * Any query string is stripped; only the last path segment is used.
     */
    override fun getApplicationName(repoUrl: String): String {
        val path = repoUrl.substringBefore("?").substringAfterLast("/")
        return path.removeSuffix(".apk")
    }

    override fun getIconUrl(repoUrl: String, branch: String, androidRoot: String): String = ""
    override fun getUrlOfRawFile(org: String, app: String, branch: String, filepath: String): String = ""
    override suspend fun fetchBranchName(org: String, app: String, requestQueue: RequestQueue): Either<String, Error> = Either.Left("main")
    override suspend fun tryDetermineAndroidRoot(org: String, app: String, branch: String, requestQueue: RequestQueue): String = ""

    override suspend fun fetchReleases(org: String, app: String, requestQueue: RequestQueue): Either<List<LatestVersionData>, Error> {
        // 'org' holds the complete original URL. Strip the query to get the actual download URL.
        val downloadUrl = org.substringBefore("?")
        val fileName = downloadUrl.substringAfterLast("/")

        val assets = listOf(AssetInfo(fileName, downloadUrl, 0L))

        return Either.Left(listOf(LatestVersionData("latest", "", "Direct Link", assets, false)))
    }
}