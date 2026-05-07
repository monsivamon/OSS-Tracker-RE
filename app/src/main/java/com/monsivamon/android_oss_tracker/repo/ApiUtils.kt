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
import com.monsivamon.android_oss_tracker.util.AppSettings

/**
 * Utility object providing coroutine‑based wrappers for Volley network requests.
 *
 * All HTTP requests automatically carry an `Authorization: Bearer` header
 * when a GitHub Personal Access Token has been configured in [AppSettings].
 */
object ApiUtils {

    /**
     * Builds the authentication headers for an API request.
     *
     * Reads the user‑supplied GitHub token from [AppSettings.githubToken]
     * and, if it is non‑blank, inserts it as a Bearer token.
     *
     * @return A mutable map of header keys and values.
     */
    private fun getAuthHeaders(): MutableMap<String, String> {
        val headers = HashMap<String, String>()
        val token = AppSettings.githubToken
        if (token.isNotBlank()) {
            headers["Authorization"] = "Bearer $token"
        }
        return headers
    }

    /**
     * Performs an HTTP `GET` request and returns the raw response string.
     *
     * @param url           Target API endpoint.
     * @param requestQueue  The Volley [RequestQueue] to use.
     * @return [Either.Left] with the response body on success,
     *         [Either.Right] with the [VolleyError] on failure.
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
     * Performs an HTTP `GET` request and returns a parsed [JSONArray].
     *
     * @param url           Target API endpoint.
     * @param requestQueue  The Volley [RequestQueue] to use.
     * @return [Either.Left] with the parsed array on success,
     *         [Either.Right] with the [VolleyError] on failure.
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
     * Performs an HTTP `GET` request and returns a parsed [JSONObject].
     *
     * @param url           Target API endpoint.
     * @param requestQueue  The Volley [RequestQueue] to use.
     * @return [Either.Left] with the parsed object on success,
     *         [Either.Right] with the [VolleyError] on failure.
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