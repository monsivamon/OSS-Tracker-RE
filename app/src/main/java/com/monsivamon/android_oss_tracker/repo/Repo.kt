package com.monsivamon.android_oss_tracker.repo

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.android.volley.RequestQueue
import arrow.core.Either
import kotlinx.coroutines.*
import org.json.JSONArray
import java.net.URL

/**
 * Lightweight in‑memory cache for repository metadata.
 * Using this instead of repeated network calls avoids unnecessary
 * API consumption and keeps the UI instantly responsive.
 */
object AppCache {
    val cachedRepos = mutableMapOf<String, RepoMetaData>()
}

/** Lifecycle states for a repository’s metadata retrieval. */
enum class MetaDataState {
    Unsupported,
    Loading,
    Errored,
    Loaded
}

/** Represents a downloadable file (typically an APK) attached to a release. */
data class AssetInfo(
    val name: String,
    val downloadUrl: String,
    val size: Long
)

/** Parsed information about the latest (or greatest) release of a repository. */
data class LatestVersionData(
    val version: String,
    val url: String,
    val date: String,
    val assets: List<AssetInfo> = emptyList()
)

/**
 * Reactive UI state and network logic for a single tracked repository.
 *
 * Every mutable property is backed by Compose [mutableStateOf] so the UI
 * recomposes automatically when network requests finish.
 *
 * @param repoUrl  Public URL of the repository being tracked.
 * @param requestQueue  Application‑scoped Volley queue.
 */
data class RepoMetaData(
    val repoUrl: String,
    val requestQueue: RequestQueue,
) {
    // Repo.Helper.new() now always returns a non‑null implementation (fallback to GitHub)
    val repo: Repo = Repo.Helper.new(repoUrl)
    val orgName: String = repo.getOrgName(repoUrl)
    val appName: String = repo.getApplicationName(repoUrl)

    val state = mutableStateOf(MetaDataState.Unsupported)

    val latestVersion = mutableStateOf<String?>(null)
    val latestVersionDate = mutableStateOf<String?>(null)
    val latestVersionUrl = mutableStateOf<String?>(null)
    val latestAssets = mutableStateOf<List<AssetInfo>>(emptyList())
    val errors = mutableStateListOf<String>()

    // Job used to cancel any in‑flight refresh when a new one is triggered.
    private var refreshJob: Job? = null

    init {
        state.value = MetaDataState.Loading
    }

    /**
     * Launches a network call to fetch the latest release data.
     *
     * If a previous refresh is still running, it is cancelled before the
     * new one starts.  This prevents redundant API calls when the user
     * repeatedly presses the refresh button.
     */
    fun refreshNetwork() {
        state.value = MetaDataState.Loading
        errors.clear()

        refreshJob?.cancel()
        refreshJob = CoroutineScope(Dispatchers.Main).launch {
            when (val result = repo.fetchLatestVersion(orgName, appName, requestQueue)) {
                is Either.Left -> {
                    latestVersion.value = result.value.version
                    latestVersionDate.value = result.value.date
                    latestVersionUrl.value = result.value.url
                    latestAssets.value = result.value.assets
                    state.value = MetaDataState.Loaded
                }
                is Either.Right -> {
                    errors.add(result.value.message ?: "Failed to retrieve latest version")
                    state.value = MetaDataState.Errored
                }
            }
        }
    }
}

/**
 * Contract for a repository hosting provider (GitHub, GitLab, etc.).
 */
interface Repo {
    fun getOrgName(repoUrl: String): String
    fun getApplicationName(repoUrl: String): String
    fun getIconUrl(repoUrl: String, branch: String, androidRoot: String): String
    fun getUrlOfRawFile(org: String, app: String, branch: String, filepath: String): String

    suspend fun fetchBranchName(
        org: String,
        app: String,
        requestQueue: RequestQueue
    ): Either<String, Error>

    suspend fun fetchLatestVersion(
        org: String,
        app: String,
        requestQueue: RequestQueue
    ): Either<LatestVersionData, Error>

    suspend fun tryDetermineAndroidRoot(
        org: String,
        app: String,
        branch: String,
        requestQueue: RequestQueue
    ): String

    /**
     * Resolves the correct [Repo] implementation based on the URL host.
     *
     * Unknown or unrecognized hosts default to [GitHub] after logging a
     * warning to the console.
     */
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
 * Shared logic for any repository provider that follows a similar
 * REST‑ful API pattern.
 */
abstract class CommonRepo : Repo {

    abstract fun getFileMetaDataUrl(org: String, app: String, branch: String, file: String): String
    abstract fun getRepoMetaDataUrl(org: String, app: String): String
    abstract fun getReadmeUrl(org: String, app: String): String
    abstract fun getReleasesUrl(org: String, app: String): String
    abstract fun getRssFeedUrl(org: String, app: String): String

    // ── Version helpers ───────────────────────────────────────

    private val versionPattern = Regex("v?([0-9]+\\S*)")

    /**
     * Strips an optional leading “v” and extracts the first version‑like
     * substring from [raw].  Returns `null` if nothing resembling a version
     * is found.
     */
    fun cleanVersionName(raw: String): String? =
        versionPattern.find(raw)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    /**
     * Compares two version strings that have already been cleaned by
     * [cleanVersionName] (i.e. no leading “v”).
     *
     * The comparison is done segment‑by‑segment, splitting on dots
     * and hyphens.  Numeric segments are compared as integers,
     * non‑numeric segments lexicographically.
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

    // ── URL parsing utilities ─────────────────────────────────

    override fun getOrgName(repoUrl: String): String {
        val path = URL(repoUrl).path.trimEnd('/').removeSuffix(".git")
        return path.split("/").drop(1).firstOrNull() ?: ""
    }

    override fun getApplicationName(repoUrl: String): String {
        val path = URL(repoUrl).path.trimEnd('/').removeSuffix(".git")
        return path.split("/").drop(2).firstOrNull() ?: ""
    }

    // ── Network fetchers ──────────────────────────────────────

    override suspend fun fetchBranchName(
        org: String,
        app: String,
        requestQueue: RequestQueue
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

    /**
     * Retrieves the list of releases from the provider’s API and returns
     * the **highest** version according to [compareVersions].
     *
     * This ensures that “v1.25.0‑dev.12” is correctly chosen over
     * “v1.25.0‑dev.9”, even if the API array ordering is unexpected.
     */
    override suspend fun fetchLatestVersion(
        org: String,
        app: String,
        requestQueue: RequestQueue
    ): Either<LatestVersionData, Error> {
        val url = getReleasesUrl(org, app)
        return when (val response = ApiUtils.getJsonArray(url, requestQueue)) {
            is Either.Left -> {
                try {
                    val releases = response.value
                    if (releases.length() == 0) {
                        return Either.Right(Error("No releases found"))
                    }
                    // Parse every release, pick the one with the greatest version
                    val allVersions = mutableListOf<LatestVersionData>()
                    for (i in 0 until releases.length()) {
                        val entry = releases.getJSONObject(i)
                        allVersions.add(parseReleaseEntry(entry))
                    }
                    val latest = allVersions.maxWithOrNull { a, b ->
                        compareVersions(a.version, b.version)
                    } ?: throw Exception("Could not determine latest version")
                    Either.Left(latest)
                } catch (e: Exception) {
                    Either.Right(Error("Could not parse releases: ${e.message}"))
                }
            }
            is Either.Right -> Either.Right(Error(response.value))
        }
    }

    /**
     * Parses a single JSON object from the releases array into a
     * [LatestVersionData] instance.  Subclasses must implement this.
     */
    protected abstract fun parseReleaseEntry(entry: org.json.JSONObject): LatestVersionData

    override suspend fun tryDetermineAndroidRoot(
        org: String,
        app: String,
        branch: String,
        requestQueue: RequestQueue
    ): String {
        // Check the most common Android project root paths in parallel
        val candidates = listOf("app", "android/app")
        return coroutineScope {
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
}