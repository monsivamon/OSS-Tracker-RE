package com.monsivamon.android_oss_tracker.repo

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.android.volley.RequestQueue
import arrow.core.Either
import com.monsivamon.android_oss_tracker.util.AppSettings
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

/**
 * In-memory cache that holds [RepoMetaData] instances for tracked repositories.
 *
 * Prevents redundant network requests during a single process lifetime by
 * reusing already fetched metadata objects.
 */
object AppCache {
    val cachedRepos = mutableMapOf<String, RepoMetaData>()
}

/** Lifecycle states for repository metadata retrieval. */
enum class MetaDataState { Unsupported, Loading, Errored, Loaded }

/**
 * Describes a downloadable file (typically an APK) attached to a release.
 *
 * @property name Display name of the asset.
 * @property downloadUrl Direct download URL.
 * @property size File size in bytes.
 */
data class AssetInfo(
    val name: String,
    val downloadUrl: String,
    val size: Long
)

/**
 * Parsed information for a single release.
 *
 * @property version The version string (e.g. "1.2.3").
 * @property url Public URL for the release page.
 * @property date Release publication date.
 * @property assets Downloadable assets included in this release.
 * @property isPreRelease Whether this release is a pre-release, as indicated by the
 * API or determined heuristically.
 */
data class LatestVersionData(
    val version: String,
    val url: String,
    val date: String,
    val assets: List<AssetInfo> = emptyList(),
    val isPreRelease: Boolean = false
)

/**
 * Reactive state holder and network logic for a single tracked repository.
 *
 * Exposes the latest **stable** and **pre‑release** versions independently so that
 * the UI can display both without mixing their assets.  Whether pre‑releases are
 * fetched is controlled by [AppSettings.trackPreReleases].
 *
 * @property repoUrl The full URL of the repository.
 * @property requestQueue Volley [RequestQueue] used for network calls.
 */
data class RepoMetaData(
    val repoUrl: String,
    val requestQueue: RequestQueue,
) {
    val repo: Repo = Repo.Helper.new(repoUrl)
    val orgName: String = repo.getOrgName(repoUrl)
    val appName: String = repo.getApplicationName(repoUrl)

    val state = mutableStateOf(MetaDataState.Unsupported)

    val latestRelease = mutableStateOf<LatestVersionData?>(null)
    val latestPreRelease = mutableStateOf<LatestVersionData?>(null)

    val errors = mutableStateListOf<String>()

    private var refreshJob: Job? = null

    init { state.value = MetaDataState.Loading }

    /**
     * Fetches all releases from the provider, separates stable from pre-release,
     * and updates [latestRelease] and [latestPreRelease] accordingly.
     */
    fun refreshNetwork() {
        state.value = MetaDataState.Loading
        errors.clear()

        refreshJob?.cancel()
        refreshJob = CoroutineScope(Dispatchers.Main).launch {
            when (val result = repo.fetchReleases(orgName, appName, requestQueue)) {
                is Either.Left -> {
                    val all = result.value

                    val stable = all.filter { !it.isPreRelease && !isPreReleaseString(it.version) }
                    val pre    = all.filter { it.isPreRelease || isPreReleaseString(it.version) }

                    latestRelease.value = stable.firstOrNull()
                    latestPreRelease.value = if (AppSettings.trackPreReleases) pre.firstOrNull() else null
                    state.value = MetaDataState.Loaded
                }
                is Either.Right -> {
                    errors.add(result.value.message ?: "Failed to retrieve releases")
                    state.value = MetaDataState.Errored
                }
            }
        }
    }

    private fun isPreReleaseString(version: String): Boolean {
        val v = version.lowercase()
        return listOf("dev", "alpha", "beta", "rc", "pre").any { v.contains(it) }
    }
}

/**
 * Defines the contract for a repository hosting provider.
 */
interface Repo {
    val providerName: String

    fun getOrgName(repoUrl: String): String
    fun getApplicationName(repoUrl: String): String
    fun getIconUrl(repoUrl: String, branch: String, androidRoot: String): String
    fun getUrlOfRawFile(org: String, app: String, branch: String, filepath: String): String

    suspend fun fetchBranchName(org: String, app: String, requestQueue: RequestQueue): Either<String, Error>
    suspend fun fetchReleases(org: String, app: String, requestQueue: RequestQueue): Either<List<LatestVersionData>, Error>
    suspend fun tryDetermineAndroidRoot(org: String, app: String, branch: String, requestQueue: RequestQueue): String

    /**
     * Factory that creates the appropriate [Repo] implementation based on the URL host.
     */
    object Helper {
        fun new(repoUrl: String): Repo {
            val host = runCatching { URL(repoUrl).host.lowercase() }.getOrDefault("")
            return when {
                host.contains("github.com") -> GitHub()
                host.contains("gitlab")     -> GitLab()
                host.contains("f-droid.org") -> FDroid()
                host.contains("codeberg.org") -> Codeberg()

                // --- Direct APK Link ---
                repoUrl.contains(".apk", ignoreCase = true) -> Direct()

                // --- Fallback for unsupported URLs ---
                else -> {
                    println("[Repo.Helper] Unknown host '$host', defaulting to UnknownRepo")
                    UnknownRepo()
                }
            }
        }
    }
}

/**
 * Common functionality for repository providers that expose a RESTful API.
 */
abstract class CommonRepo : Repo {

    abstract fun getFileMetaDataUrl(org: String, app: String, branch: String, file: String): String
    abstract fun getRepoMetaDataUrl(org: String, app: String): String
    abstract fun getReadmeUrl(org: String, app: String): String
    abstract fun getReleasesUrl(org: String, app: String): String
    abstract fun getRssFeedUrl(org: String, app: String): String
    protected abstract fun parseReleasesJsonArray(data: JSONArray): List<LatestVersionData>

    private val versionPattern = Regex("v?([0-9]+\\S*)")

    fun cleanVersionName(raw: String): String? =
        versionPattern.find(raw)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    private fun compareVersions(a: String, b: String): Int {
        val partsA = a.split(".", "-")
        val partsB = b.split(".", "-")
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val segA = partsA.getOrNull(i) ?: return -1
            val segB = partsB.getOrNull(i) ?: return 1
            val numA = segA.toIntOrNull()
            val numB = segB.toIntOrNull()
            when {
                numA != null && numB != null -> {
                    if (numA != numB) return numA.compareTo(numB)
                }
                numA != null -> return 1
                numB != null -> return -1
                else -> {
                    val cmp = segA.compareTo(segB)
                    if (cmp != 0) return cmp
                }
            }
        }
        return 0
    }

    override fun getOrgName(repoUrl: String): String {
        val path = URL(repoUrl).path.trimEnd('/').removeSuffix(".git")
        return path.split("/").drop(1).firstOrNull() ?: ""
    }

    override fun getApplicationName(repoUrl: String): String {
        val path = URL(repoUrl).path.trimEnd('/').removeSuffix(".git")
        return path.split("/").drop(2).firstOrNull() ?: ""
    }

    override suspend fun fetchBranchName(
        org: String, app: String, requestQueue: RequestQueue
    ): Either<String, Error> {
        val url = getRepoMetaDataUrl(org, app)
        return when (val response = ApiUtils.getJsonObject(url, requestQueue)) {
            is Either.Left -> try {
                Either.Left(response.value.getString("default_branch"))
            } catch (e: Exception) {
                Either.Right(Error("Could not parse default_branch"))
            }
            is Either.Right -> Either.Right(Error(response.value))
        }
    }

    override suspend fun fetchReleases(
        org: String, app: String, requestQueue: RequestQueue
    ): Either<List<LatestVersionData>, Error> {
        val url = getReleasesUrl(org, app)
        return when (val response = ApiUtils.getJsonArray(url, requestQueue)) {
            is Either.Left -> {
                try {
                    val all = parseReleasesJsonArray(response.value)
                    Either.Left(all.sortedWith { a, b -> -compareVersions(a.version, b.version) })
                } catch (e: Exception) {
                    Either.Right(Error("Failed to parse releases: ${e.message}"))
                }
            }
            is Either.Right -> Either.Right(Error(response.value))
        }
    }

    override suspend fun tryDetermineAndroidRoot(
        org: String, app: String, branch: String, requestQueue: RequestQueue
    ): String = coroutineScope {
        val candidates = listOf("app", "android/app")
        val deferred = candidates.map { candidate ->
            async {
                if (ApiUtils.get(
                        getFileMetaDataUrl(org, app, branch, "$candidate/build.gradle"),
                        requestQueue
                    ).isLeft()
                ) candidate else null
            }
        }
        deferred.awaitAll().firstOrNull { it != null } ?: ""
    }
}

/**
 * Fallback repository class for unsupported or malformed URLs.
 */
class UnknownRepo : Repo {
    override val providerName: String = "Unknown"
    override fun getOrgName(repoUrl: String): String = ""
    override fun getApplicationName(repoUrl: String): String = "Unsupported App"
    override fun getIconUrl(repoUrl: String, branch: String, androidRoot: String): String = ""
    override fun getUrlOfRawFile(org: String, app: String, branch: String, filepath: String): String = ""
    override suspend fun fetchBranchName(org: String, app: String, requestQueue: RequestQueue): Either<String, Error> = Either.Left("main")
    override suspend fun tryDetermineAndroidRoot(org: String, app: String, branch: String, requestQueue: RequestQueue): String = ""

    override suspend fun fetchReleases(org: String, app: String, requestQueue: RequestQueue): Either<List<LatestVersionData>, Error> {
        return Either.Right(Error("Unsupported repository URL. Please use a valid source or a Direct APK link."))
    }
}