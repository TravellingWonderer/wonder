package com.wonder.provider

import android.app.Application

class WonderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
