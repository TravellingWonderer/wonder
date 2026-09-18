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

    private val _homeAirport = MutableStateFlow(loadHomeAirport())
    val homeAirport: StateFlow<String> = _homeAirport.asStateFlow()

    fun getDuffelToken(): String =
        _duffelToken.value.ifBlank { BuildConfig.DUFFEL_ACCESS_TOKEN.trim() }

    fun isDuffelConfigured(): Boolean = getDuffelToken().isNotBlank()

    fun saveDuffelToken(token: String) {
        prefs.edit().putString(KEY_DUFFEL, token.trim()).apply()
        _duffelToken.value = token.trim()
    }

    fun clearDuffelToken() = saveDuffelToken("")

    fun getHomeAirport(): String =
        _homeAirport.value.ifBlank { "LHR" }.uppercase()

    fun saveHomeAirport(code: String) {
        val cleaned = code.trim().uppercase().take(3)
        prefs.edit().putString(KEY_HOME_AIRPORT, cleaned).apply()
        _homeAirport.value = cleaned
    }

    private fun loadToken(): String = prefs.getString(KEY_DUFFEL, "").orEmpty()

    private fun loadHomeAirport(): String = prefs.getString(KEY_HOME_AIRPORT, "").orEmpty()

    private companion object {
        const val PREFS_NAME = "wonder_travel_api"
        const val KEY_DUFFEL = "duffel_token"
        const val KEY_HOME_AIRPORT = "home_airport"
    }
}
