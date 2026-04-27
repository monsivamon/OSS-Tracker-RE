package com.monsivamon.android_oss_tracker.repo

import arrow.core.Either
import com.android.volley.AuthFailureError
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.monsivamon.android_oss_tracker.BuildConfig

/**
 * A utility object that provides coroutine-based wrappers for Volley network requests.
 * It handles authentication headers globally for GitHub/GitLab API requests.
 */
object ApiUtils {

    // Safely loaded from the local.properties file via BuildConfig to prevent token leakage.
    private val GITHUB_TOKEN = BuildConfig.GITHUB_TOKEN

    /**
     * Generates the required HTTP headers for API authentication.
     * Injects the GitHub Personal Access Token as a Bearer token if it is configured.
     *
     * @return A map of header keys and values.
     */
    private fun getAuthHeaders(): MutableMap<String, String> {
        val headers = HashMap<String, String>()
        if (GITHUB_TOKEN.isNotBlank()) {
            headers["Authorization"] = "Bearer $GITHUB_TOKEN"
        }
        return headers
    }

    /**
     * Executes an HTTP GET request and returns the raw string response.
     *
     * @param url The target API endpoint URL.
     * @param requestQueue The Volley RequestQueue to handle the network operation.
     * @return An [Either] containing the raw response String on success (Left), or a VolleyError on failure (Right).
     */
    suspend fun get(url: String, requestQueue: RequestQueue) = suspendCoroutine<Either<String, VolleyError>> { cont ->
        requestQueue.add(object : StringRequest(
            Request.Method.GET, url,
            { response -> cont.resume(Either.Left(response)) },
            { error -> cont.resume(Either.Right(error)) }
        ) {
            @Throws(AuthFailureError::class)
            override fun getHeaders(): MutableMap<String, String> {
                return getAuthHeaders()
            }
        })
    }

    /**
     * Executes an HTTP GET request and returns a parsed JSON array.
     *
     * @param url The target API endpoint URL.
     * @param requestQueue The Volley RequestQueue to handle the network operation.
     * @return An [Either] containing the parsed JSONArray on success (Left), or a VolleyError on failure (Right).
     */
    suspend fun getJsonArray(url: String, requestQueue: RequestQueue) = suspendCoroutine<Either<JSONArray, VolleyError>> { cont ->
        requestQueue.add(object : JsonArrayRequest(
            Request.Method.GET, url, null,
            { response -> cont.resume(Either.Left(response)) },
            { error -> cont.resume(Either.Right(error)) }
        ) {
            @Throws(AuthFailureError::class)
            override fun getHeaders(): MutableMap<String, String> {
                return getAuthHeaders()
            }
        })
    }

    /**
     * Executes an HTTP GET request and returns a parsed JSON object.
     *
     * @param url The target API endpoint URL.
     * @param requestQueue The Volley RequestQueue to handle the network operation.
     * @return An [Either] containing the parsed JSONObject on success (Left), or a VolleyError on failure (Right).
     */
    suspend fun getJsonObject(url: String, requestQueue: RequestQueue) = suspendCoroutine<Either<JSONObject, VolleyError>> { cont ->
        requestQueue.add(object : JsonObjectRequest(
            Request.Method.GET, url, null,
            { response -> cont.resume(Either.Left(response)) },
            { error -> cont.resume(Either.Right(error)) }
        ) {
            @Throws(AuthFailureError::class)
            override fun getHeaders(): MutableMap<String, String> {
                return getAuthHeaders()
            }
        })
    }
}