package com.wonder.provider.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wonder.provider.ai.AiProviderType
import com.wonder.provider.ai.AiSettings
import com.wonder.provider.ai.AiSettingsRepository
import com.wonder.provider.ai.AiTourService
import com.wonder.provider.ai.DiscoveredModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AiSettingsViewModel(
    private val repository: AiSettingsRepository,
    private val aiTourService: AiTourService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiSettingsUiState())
    val uiState: StateFlow<AiSettingsUiState> = _uiState.asStateFlow()

    init {
        refreshDiscovery()
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        settings = settings,
                        apiKeyInput = settings.apiKey,
                        customBaseUrlInput = settings.customApiBaseUrl,
                        customModelInput = settings.customModelName,
                        hfTokenInput = settings.huggingFaceToken,
                        selectedProvider = settings.providerType,
                        modelFileName = repository.modelStore().displayName(settings.modelUri),
                        modelAttached = settings.isGemmaConfigured,
                        sourceDescription = aiTourService.activeSourceDescription(settings),
                        saved = false
                    )
                }
            }
        }
    }

    fun refreshDiscovery() {
        val store = repository.modelStore()
        _uiState.update {
            it.copy(
                galleryInstalled = store.isGalleryInstalled(),
                discoveredModels = store.discoverDownloadedModels()
            )
        }
    }

    fun setProvider(type: AiProviderType) {
        _uiState.update { it.copy(selectedProvider = type, saved = false, error = null) }
    }

    fun setApiKey(key: String) {
        _uiState.update { it.copy(apiKeyInput = key, saved = false, error = null) }
    }

    fun setCustomBaseUrl(url: String) {
        _uiState.update { it.copy(customBaseUrlInput = url, saved = false, error = null) }
    }

    fun setCustomModel(model: String) {
        _uiState.update { it.copy(customModelInput = model, saved = false, error = null) }
    }

    fun setHfToken(token: String) {
        _uiState.update { it.copy(hfTokenInput = token, saved = false, error = null) }
    }

    fun onModelPicked(uri: Uri) {
        repository.persistModelUri(uri)
        _uiState.update {
            it.copy(
                modelFileName = repository.modelStore().displayName(uri.toString()),
                modelAttached = true,
                saved = true,
                error = null
            )
        }
    }
    fun save() {
        val state = _uiState.value
        when (state.selectedProvider) {
            AiProviderType.ON_DEVICE_WONDER -> {
                repository.saveSettings(
                    AiSettings(providerType = AiProviderType.ON_DEVICE_WONDER)
                )
            }

            AiProviderType.GEMMA_LITERT -> {
                val uri = state.settings.modelUri
                if (uri.isBlank() || !repository.modelStore().validateModelUri(uri)) {
                    _uiState.update {
                        it.copy(error = "Attach a .litertlm model file first — see the steps above.")
                    }
                    return
                }
                repository.saveSettings(
                    AiSettings(
                        providerType = AiProviderType.GEMMA_LITERT,
                        modelUri = uri,
                        huggingFaceToken = state.hfTokenInput.trim()
                    )
                )
            }

            AiProviderType.CUSTOM_OPENAI_COMPAT -> {
                val baseUrl = state.customBaseUrlInput.trim()
                val model = state.customModelInput.trim()
                if (baseUrl.isBlank()) {
                    _uiState.update { it.copy(error = "Enter the API base URL") }
                    return
                }
                if (!state.selectedProvider.validateCustomBaseUrl(baseUrl)) {
                    _uiState.update { it.copy(error = "Base URL must start with http:// or https://") }
                    return
                }
                if (model.isBlank()) {
                    _uiState.update { it.copy(error = "Enter the model name your endpoint expects") }
                    return
                }
                if (state.apiKeyInput.isBlank()) {
                    _uiState.update { it.copy(error = "Enter your access token") }
                    return
                }
                if (!state.selectedProvider.validateKeyFormat(state.apiKeyInput)) {
                    _uiState.update { it.copy(error = "Access token looks too short") }
                    return
                }
                repository.saveSettings(
                    AiSettings(
                        providerType = AiProviderType.CUSTOM_OPENAI_COMPAT,
                        apiKey = state.apiKeyInput.trim(),
                        customApiBaseUrl = baseUrl,
                        customModelName = model
                    )
                )
            }

            else -> {
                if (state.apiKeyInput.isBlank()) {
                    _uiState.update { it.copy(error = "Enter your API key") }
                    return
                }
                if (!state.selectedProvider.validateKeyFormat(state.apiKeyInput)) {
                    _uiState.update {
                        it.copy(error = "Key format looks wrong — expected ${state.selectedProvider.keyHint}")
                    }
                    return
                }
                repository.saveSettings(
                    AiSettings(
                        providerType = state.selectedProvider,
                        apiKey = state.apiKeyInput.trim()
                    )
                )
            }
        }
        _uiState.update { it.copy(saved = true, error = null) }
    }

    fun clearCredentials() {
        repository.clearCredentials()
        _uiState.update {
            it.copy(
                apiKeyInput = "",
                customBaseUrlInput = "",
                customModelInput = "",
                hfTokenInput = "",
                modelFileName = "",
                modelAttached = false,
                selectedProvider = AiProviderType.ON_DEVICE_WONDER,
                saved = true,
                error = null
            )
        }
    }
}

data class AiSettingsUiState(
    val settings: AiSettings = AiSettings(),
    val selectedProvider: AiProviderType = AiProviderType.ON_DEVICE_WONDER,
    val apiKeyInput: String = "",
    val customBaseUrlInput: String = "",
    val customModelInput: String = "",
    val hfTokenInput: String = "",
    val modelFileName: String = "",
    val modelAttached: Boolean = false,
    val galleryInstalled: Boolean = false,
    val discoveredModels: List<DiscoveredModel> = emptyList(),
    val sourceDescription: String = "",
    val saved: Boolean = false,
    val error: String? = null
)
