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
 * In‑memory cache that prevents redundant network calls for the same
 * repository during a single process lifetime.
 */
object AppCache {
    val cachedRepos = mutableMapOf<String, RepoMetaData>()
}

/** Lifecycle states for repository metadata retrieval. */
enum class MetaDataState { Unsupported, Loading, Errored, Loaded }

/** A downloadable file (typically an APK) attached to a release. */
data class AssetInfo(
    val name: String,
    val downloadUrl: String,
    val size: Long
)

/** Parsed data for a single release. */
data class LatestVersionData(
    val version: String,
    val url: String,
    val date: String,
    val assets: List<AssetInfo> = emptyList()
)

/**
 * Reactive state and network logic for a single tracked repository.
 *
 * The latest **stable** and **pre‑release** versions are stored
 * independently so the UI can display both without mixing their
 * assets.  Whether pre‑releases are fetched is controlled by
 * [AppSettings.trackPreReleases].
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
     * Fetches all releases, separates stable from pre‑release, and
     * caches the newest of each.  Pre‑releases are suppressed when
     * [AppSettings.trackPreReleases] is `false`.
     */
    fun refreshNetwork() {
        state.value = MetaDataState.Loading
        errors.clear()

        refreshJob?.cancel()
        refreshJob = CoroutineScope(Dispatchers.Main).launch {
            when (val result = repo.fetchReleases(orgName, appName, requestQueue)) {
                is Either.Left -> {
                    val all = result.value
                    val stable = all.filter { !isPreRelease(it.version) }
                    val pre    = all.filter { isPreRelease(it.version) }
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

    /** Heuristic that classifies a version string as a pre‑release. */
    private fun isPreRelease(version: String): Boolean {
        val v = version.lowercase()
        return listOf("dev", "alpha", "beta", "rc", "pre").any { v.contains(it) }
    }
}

/**
 * Contract for a repository hosting provider (GitHub, GitLab, …).
 */
interface Repo {
    /** Human‑readable name of the hosting service, e.g. “GitHub”. */
    val providerName: String

    fun getOrgName(repoUrl: String): String
    fun getApplicationName(repoUrl: String): String
    fun getIconUrl(repoUrl: String, branch: String, androidRoot: String): String
    fun getUrlOfRawFile(org: String, app: String, branch: String, filepath: String): String

    suspend fun fetchBranchName(org: String, app: String, requestQueue: RequestQueue): Either<String, Error>
    suspend fun fetchReleases(org: String, app: String, requestQueue: RequestQueue): Either<List<LatestVersionData>, Error>
    suspend fun tryDetermineAndroidRoot(org: String, app: String, branch: String, requestQueue: RequestQueue): String

    /** Resolves the correct [Repo] implementation from a URL. */
    object Helper {
        fun new(repoUrl: String): Repo {
            val host = runCatching { URL(repoUrl).host.lowercase() }.getOrDefault("")
            return when {
                host.contains("github.com") -> GitHub()
                host.contains("gitlab")     -> GitLab()
                else -> {
                    println("[Repo.Helper] Unknown host '$host', defaulting to GitHub")
                    GitHub()
                }
            }
        }
    }
}

/**
 * Shared logic for repository providers that expose a REST‑ful API.
 */
abstract class CommonRepo : Repo {

    abstract fun getFileMetaDataUrl(org: String, app: String, branch: String, file: String): String
    abstract fun getRepoMetaDataUrl(org: String, app: String): String
    abstract fun getReadmeUrl(org: String, app: String): String
    abstract fun getReleasesUrl(org: String, app: String): String
    abstract fun getRssFeedUrl(org: String, app: String): String
    protected abstract fun parseReleasesJsonArray(data: JSONArray): List<LatestVersionData>

    private val versionPattern = Regex("v?([0-9]+\\S*)")

    /** Strips an optional leading “v” and returns the first version‑like substring. */
    fun cleanVersionName(raw: String): String? =
        versionPattern.find(raw)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    /**
     * Compares two cleaned version strings segment by segment.
     * Numeric segments are compared numerically, others lexicographically.
     */
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