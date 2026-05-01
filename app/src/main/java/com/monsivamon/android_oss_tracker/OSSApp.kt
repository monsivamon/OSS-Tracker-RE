package com.monsivamon.android_oss_tracker

import android.app.Application
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley

/**
 * Custom [Application] subclass responsible for early, one-time initialization
 * of shared services that the entire app depends on.
 *
 * ## Design Rationale
 * Volley's [RequestQueue] is thread-safe by nature, but creating a new instance
 * inside each composable or Activity is wasteful — it spawns multiple cache
 * directories and thread pools, increasing GC pressure.  By initialising it here,
 * the queue is created once, shared via the [OSSApp.requestQueue] singleton, and
 * automatically torn down when the process is killed.
 *
 * ## Usage
 * Any screen or utility that needs to perform network requests can simply reference
 * [OSSApp.requestQueue] without passing it through Composable parameters.
 */
class OSSApp : Application() {

    companion object {
        /**
         * Application-scoped Volley request queue.
         * Initialised during [onCreate] and safe to access from any thread.
         */
        lateinit var requestQueue: RequestQueue
            private set
    }

    override fun onCreate() {
        super.onCreate()
        requestQueue = Volley.newRequestQueue(this)
    }
}