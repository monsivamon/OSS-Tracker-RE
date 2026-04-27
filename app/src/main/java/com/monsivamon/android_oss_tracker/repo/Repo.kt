package com.monsivamon.android_oss_tracker.repo

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.android.volley.RequestQueue
import arrow.core.Either
import kotlinx.coroutines.*
import org.json.JSONArray
import java.net.URL

/**
 * An in-memory cache to store and retrieve repository metadata.
 * Prevents redundant network requests across UI recompositions.
 */
object AppCache {
    val cachedRepos = mutableMapOf<String, RepoMetaData>()
}

/**
 * Represents the current lifecycle state of a repository's metadata fetching process.
 */
enum class MetaDataState {
    Unsupported,
    Loading,
    Errored,
    Loaded
}

/**
 * Data class representing a downloadable asset (e.g., an APK file) associated with a release.
 */
data class AssetInfo(
    val name: String,
    val downloadUrl: String,
    val size: Long
)

/**
 * Data class holding the parsed details of the latest repository release.
 */
data class LatestVersionData(
    val version: String,
    val url: String,
    val date: String,
    val assets: List<AssetInfo> = emptyList()
)

/**
 * Holds the reactive UI state and network logic for a single tracked repository.
 * Integrates directly with Jetpack Compose via [mutableStateOf].
 *
 * @property repoUrl The remote repository URL being tracked.
 * @property requestQueue The Volley RequestQueue for handling network operations.
 */
data class RepoMetaData(
    val repoUrl: String,
    val requestQueue: RequestQueue,
) {
    val repo: Repo? = Repo.Helper.new(repoUrl)
    var orgName: String
    var appName: String
    val state = mutableStateOf(MetaDataState.Unsupported)

    val latestVersion = mutableStateOf<String?>(null)
    val latestVersionDate = mutableStateOf<String?>(null)
    val latestVersionUrl = mutableStateOf<String?>(null)
    val latestAssets = mutableStateOf<List<AssetInfo>>(emptyList())
    val errors = mutableStateListOf<String>()

    init {
        if (repo == null) {
            state.value = MetaDataState.Unsupported
            orgName = ""
            appName = ""
        } else {
            state.value = MetaDataState.Loading
            orgName = repo.getOrgName(repoUrl)
            appName = repo.getApplicationName(repoUrl)
        }
    }

    /**
     * Triggers an asynchronous network request to fetch the latest release data.
     * Updates the reactive state variables upon successful completion or failure.
     */
    fun refreshNetwork() {
        if (repo == null) return

        state.value = MetaDataState.Loading
        errors.clear()

        val scope = CoroutineScope(Job() + Dispatchers.Main)
        scope.launch {
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
 * Interface defining the contract for repository service providers (e.g., GitHub, GitLab).
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
     * Factory object to instantiate the correct [Repo] implementation based on the provided URL.
     */
    object Helper {
        fun new(repoUrl: String): Repo =
            if (repoUrl.contains("github")) {
                GitHub()
            } else {
                GitLab()
            }
    }
}

/**
 * Abstract base class providing shared logic and URL parsing utilities
 * for different repository providers.
 */
abstract class CommonRepo : Repo {

    abstract fun getFileMetaDataUrl(org: String, app: String, branch: String, file: String): String
    abstract fun getRepoMetaDataUrl(org: String, app: String): String
    abstract fun getReadmeUrl(org: String, app: String): String
    abstract fun getReleasesUrl(org: String, app: String): String
    abstract fun getRssFeedUrl(org: String, app: String): String
    abstract fun parseReleasesJson(data: JSONArray): LatestVersionData

    val VERSION_NAME_REGEX = "([0-9]+\\S*)".toRegex()

    /**
     * Extracts a clean version string from raw release tags (e.g., "v1.2.3" -> "1.2.3").
     */
    fun cleanVersionName(input: String): String? =
        VERSION_NAME_REGEX.find(input)?.groups?.get(0)?.value

    override fun getOrgName(repoUrl: String): String {
        val url = URL(repoUrl)
        return url.path.split("/")[1]
    }

    override fun getApplicationName(repoUrl: String): String {
        val url = URL(repoUrl)
        return url.path.split("/")[2]
    }

    override suspend fun fetchBranchName(
        org: String,
        app: String,
        requestQueue: RequestQueue
    ): Either<String, Error> {
        val url = getRepoMetaDataUrl(org, app)
        return when (val response = ApiUtils.getJsonObject(url, requestQueue)) {
            is Either.Left -> {
                try {
                    Either.Left(response.value.getString("default_branch"))
                } catch (e: Exception) {
                    Either.Right(Error("Could not parse result of fetchBranchName api call"))
                }
            }
            is Either.Right -> {
                Either.Right(Error(response.value))
            }
        }
    }

    override suspend fun fetchLatestVersion(
        org: String,
        app: String,
        requestQueue: RequestQueue
    ): Either<LatestVersionData, Error> {
        val url = getReleasesUrl(org, app)
        return when (val response = ApiUtils.getJsonArray(url, requestQueue)) {
            is Either.Left -> {
                try {
                    val parsed = parseReleasesJson(response.value)
                    Either.Left(parsed)
                } catch (e: Exception) {
                    Either.Right(Error("Could not parse result of fetchLatestVersion api call"))
                }
            }
            is Either.Right -> {
                Either.Right(Error(response.value))
            }
        }
    }

    override suspend fun tryDetermineAndroidRoot(
        org: String,
        app: String,
        branch: String,
        requestQueue: RequestQueue
    ): String {
        val candidates = listOf("app", "android/app")
        candidates.forEach { candidate ->
            if (ApiUtils.get(
                    getFileMetaDataUrl(org, app, branch, "$candidate/build.gradle"),
                    requestQueue
                ).isLeft()
            ) {
                return candidate
            }
        }
        return ""
    }
}