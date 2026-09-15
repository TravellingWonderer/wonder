package com.wonder.provider.data.travel

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wonder.provider.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TravelApiSettingsRepository(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        PREFS_NAME,
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _duffelToken = MutableStateFlow(loadToken())
    val duffelToken: StateFlow<String> = _duffelToken.asStateFlow()

    fun getDuffelToken(): String =
        _duffelToken.value.ifBlank { BuildConfig.DUFFEL_ACCESS_TOKEN.trim() }

    fun isDuffelConfigured(): Boolean = getDuffelToken().isNotBlank()

    fun saveDuffelToken(token: String) {
        prefs.edit().putString(KEY_DUFFEL, token.trim()).apply()
        _duffelToken.value = token.trim()
    }

    fun clearDuffelToken() = saveDuffelToken("")

    private fun loadToken(): String = prefs.getString(KEY_DUFFEL, "").orEmpty()

    private companion object {
        const val PREFS_NAME = "wonder_travel_api"
        const val KEY_DUFFEL = "duffel_token"
    }
}
