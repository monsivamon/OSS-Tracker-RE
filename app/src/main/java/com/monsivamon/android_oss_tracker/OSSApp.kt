package com.monsivamon.android_oss_tracker

import android.app.Application
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.monsivamon.android_oss_tracker.util.ThemeState

class OSSApp : Application() {

    companion object {
        lateinit var requestQueue: RequestQueue
            private set
    }

    override fun onCreate() {
        super.onCreate()
        requestQueue = Volley.newRequestQueue(this)
        ThemeState.init(this)
    }
}