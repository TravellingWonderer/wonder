package com.wonder.provider.ai

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AiSettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val modelStore = OnDeviceModelStore(appContext)

    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        PREFS_NAME,
        MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AiSettings> = _settings.asStateFlow()

    private var litertProvider: LiteRtLmChatProvider? = null

    fun getSettings(): AiSettings = _settings.value

    fun modelStore(): OnDeviceModelStore = modelStore

    fun saveSettings(settings: AiSettings) {
        if (settings.modelUri != _settings.value.modelUri) {
            releaseOnDeviceEngine()
        }
        prefs.edit()
            .putString(KEY_PROVIDER, settings.providerType.name)
            .putString(KEY_API_KEY, settings.apiKey)
            .putString(KEY_CUSTOM_BASE_URL, settings.customApiBaseUrl)
            .putString(KEY_CUSTOM_MODEL, settings.customModelName)
            .putString(KEY_MODEL_URI, settings.modelUri)
            .putString(KEY_HF_TOKEN, settings.huggingFaceToken)
            .apply()
        _settings.value = settings
    }

    fun clearCredentials() {
        releaseOnDeviceEngine()
        saveSettings(
            AiSettings(
                providerType = AiProviderType.ON_DEVICE_WONDER,
                apiKey = "",
                customApiBaseUrl = "",
                customModelName = "",
                modelUri = "",
                huggingFaceToken = ""
            )
        )
    }

    /** Persist read access to a model file the traveller picked in the system file chooser. */
    fun persistModelUri(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        saveSettings(getSettings().copy(modelUri = uri.toString()))
    }

    /** Attach a `.litertlm` file from Downloads and select Gemma on-device (debug / adb testing). */
    fun attachFromDownloads(fileName: String): Boolean {
        val uri = modelStore.attachFromDownloads(fileName) ?: return false
        saveSettings(
            AiSettings(
                providerType = AiProviderType.GEMMA_LITERT,
                modelUri = uri
            )
        )
        return true
    }

    fun chatProvider(): AiChatProvider? {
        val settings = getSettings()
        return when {
            settings.isGemmaConfigured -> litertProvider ?: LiteRtLmChatProvider(
                context = appContext,
                modelStore = modelStore,
                modelUri = settings.modelUri
            ).also { litertProvider = it }

            settings.isCloudConfigured ->
                AiProviderFactory.createChat(settings)

            else -> null
        }
    }

    fun releaseOnDeviceEngine() {
        litertProvider?.release()
        litertProvider = null
    }

    private fun loadSettings(): AiSettings {
        val providerName = prefs.getString(KEY_PROVIDER, AiProviderType.ON_DEVICE_WONDER.name)
        val provider = runCatching {
            AiProviderType.valueOf(providerName ?: AiProviderType.ON_DEVICE_WONDER.name)
        }.getOrDefault(AiProviderType.ON_DEVICE_WONDER)

        return AiSettings(
            providerType = provider,
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
            customApiBaseUrl = prefs.getString(KEY_CUSTOM_BASE_URL, "").orEmpty(),
            customModelName = prefs.getString(KEY_CUSTOM_MODEL, "").orEmpty(),
            modelUri = prefs.getString(KEY_MODEL_URI, "").orEmpty(),
            huggingFaceToken = prefs.getString(KEY_HF_TOKEN, "").orEmpty()
        )
    }

    companion object {
        private const val PREFS_NAME = "wonder_ai_settings"
        private const val KEY_PROVIDER = "provider_type"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_CUSTOM_BASE_URL = "custom_api_base_url"
        private const val KEY_CUSTOM_MODEL = "custom_model_name"
        private const val KEY_MODEL_URI = "model_uri"
        private const val KEY_HF_TOKEN = "hf_token"
    }
}
