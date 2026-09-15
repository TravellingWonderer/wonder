package com.wonder.provider.auth

import android.app.Activity
import android.content.Intent

/**
 * Routes Google sign-in activity results from [android.app.Activity] to the screen that
 * requested sign-in. Compose launchers can miss results when Google's sign-in UI opens.
 */
class GoogleSignInBridge {

    private var launchActivity: ((Intent) -> Unit)? = null
    private var resultHandler: ((Int, Intent?) -> Unit)? = null
    private var pendingResult: Pair<Int, Intent?>? = null

    var pendingDriveSync: Boolean = false
        private set

    fun attach(launch: (Intent) -> Unit) {
        launchActivity = launch
    }

    fun setResultHandler(handler: ((Int, Intent?) -> Unit)?) {
        resultHandler = handler
        val pending = pendingResult
        if (handler != null && pending != null) {
            pendingResult = null
            handler(pending.first, pending.second)
        }
    }

    fun launchSignIn(intent: Intent, forDriveSync: Boolean) {
        pendingDriveSync = forDriveSync
        pendingResult = null
        launchActivity?.invoke(intent) ?: error("Google sign-in bridge not attached")
    }

    fun onActivityResult(resultCode: Int, data: Intent?) {
        val handler = resultHandler
        if (handler != null) {
            handler(resultCode, data)
        } else {
            pendingResult = resultCode to data
        }
    }

    val isAttached: Boolean get() = launchActivity != null
}
