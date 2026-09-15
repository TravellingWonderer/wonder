package com.wonder.provider.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wonder.provider.AppContainer

/**
 * Debug-only hook for emulator setup:
 * adb shell am broadcast -a com.wonder.provider.ATTACH_GEMMA --es file "model.litertlm"
 * adb shell am broadcast … --es file "@internal"   # after copying to files/models/gemma.litertlm
 */
class AttachGemmaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val fileName = intent.getStringExtra(EXTRA_FILE) ?: return
        AppContainer.init(context.applicationContext)
        val attached = AppContainer.aiSettingsRepository.attachFromDownloads(fileName)
        Log.i(TAG, if (attached) "Attached Gemma: $fileName" else "Attach failed: $fileName")
    }

    companion object {
        private const val TAG = "AttachGemmaReceiver"
        const val ACTION = "com.wonder.provider.ATTACH_GEMMA"
        const val EXTRA_FILE = "file"
    }
}
