package com.wonder.provider.ai

enum class AiProviderKind {
    /** Runs entirely on the phone — no subscription, no network. */
    ON_DEVICE,
    /** Your API key, billed by the provider. */
    CLOUD
}

enum class AiProviderType(
    val displayName: String,
    val kind: AiProviderKind,
    val keyHint: String,
    val docsUrl: String,
    val subtitle: String
) {
    ON_DEVICE_WONDER(
        displayName = "Wonder",
        kind = AiProviderKind.ON_DEVICE,
        keyHint = "",
        docsUrl = "",
        subtitle = "Built in · works offline · no setup"
    ),
    GEMMA_LITERT(
        displayName = "Gemma on-device",
        kind = AiProviderKind.ON_DEVICE,
        keyHint = "model.litertlm",
        docsUrl = "https://github.com/google-ai-edge/gallery/wiki/6.-Importing-Local-Models-(optional)",
        subtitle = "Free · via Google AI Edge Gallery & LiteRT-LM"
    ),
    OPENAI(
        displayName = "OpenAI",
        kind = AiProviderKind.CLOUD,
        keyHint = "sk-…",
        docsUrl = "https://platform.openai.com/api-keys",
        subtitle = "GPT-4o mini · cloud"
    ),
    GEMINI(
        displayName = "Google Gemini",
        kind = AiProviderKind.CLOUD,
        keyHint = "AIza…",
        docsUrl = "https://aistudio.google.com/apikey",
        subtitle = "Gemini Flash · cloud"
    ),
    ANTHROPIC(
        displayName = "Anthropic",
        kind = AiProviderKind.CLOUD,
        keyHint = "sk-ant-…",
        docsUrl = "https://console.anthropic.com/settings/keys",
        subtitle = "Claude Haiku · cloud"
    ),
    MISTRAL(
        displayName = "Mistral AI",
        kind = AiProviderKind.CLOUD,
        keyHint = "…",
        docsUrl = "https://console.mistral.ai/api-keys",
        subtitle = "Mistral Small · cloud"
    ),
    CUSTOM_OPENAI_COMPAT(
        displayName = "Custom API",
        kind = AiProviderKind.CLOUD,
        keyHint = "access token",
        docsUrl = "",
        subtitle = "OpenAI-compatible · your endpoint & key"
    );

    val isOnDevice: Boolean get() = kind == AiProviderKind.ON_DEVICE

    fun validateKeyFormat(key: String): Boolean = when (this) {
        ON_DEVICE_WONDER, GEMMA_LITERT -> true
        OPENAI -> key.startsWith("sk-") && key.length > 20
        GEMINI -> key.startsWith("AIza") && key.length > 20
        ANTHROPIC -> key.startsWith("sk-ant-") && key.length > 20
        MISTRAL -> key.length >= 20
        CUSTOM_OPENAI_COMPAT -> key.length >= 4
    }

    fun validateCustomBaseUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
    }

    companion object {
        val onDevice: List<AiProviderType> = entries.filter { it.isOnDevice }
        val cloud: List<AiProviderType> = entries.filter { !it.isOnDevice }
    }
}

data class AiSettings(
    val providerType: AiProviderType = AiProviderType.ON_DEVICE_WONDER,
    /** Cloud API key, when [providerType] is a cloud provider. */
    val apiKey: String = "",
    /** Base URL for [AiProviderType.CUSTOM_OPENAI_COMPAT], e.g. `https://api.openrouter.ai/v1`. */
    val customApiBaseUrl: String = "",
    /** Model id sent to a custom OpenAI-compatible endpoint, e.g. `gpt-4o-mini`. */
    val customModelName: String = "",
    /**
     * Content URI of a `.litertlm` model the traveller downloaded — typically the same bundle
     * they imported into Google AI Edge Gallery.
     */
    val modelUri: String = "",
    /** Optional Hugging Face token if they want Wonder to fetch a bundle for them later. */
    val huggingFaceToken: String = ""
) {
    val isCloudConfigured: Boolean
        get() = when (providerType) {
            AiProviderType.CUSTOM_OPENAI_COMPAT ->
                apiKey.isNotBlank() &&
                    customApiBaseUrl.isNotBlank() &&
                    customModelName.isNotBlank() &&
                    providerType.validateKeyFormat(apiKey) &&
                    providerType.validateCustomBaseUrl(customApiBaseUrl)

            else ->
                !providerType.isOnDevice &&
                    providerType != AiProviderType.ON_DEVICE_WONDER &&
                    apiKey.isNotBlank() &&
                    providerType.validateKeyFormat(apiKey)
        }

    val isGemmaConfigured: Boolean
        get() = providerType == AiProviderType.GEMMA_LITERT && modelUri.isNotBlank()

    /** True when Wonder should route turns to a connected model rather than the built-in engine. */
    val isConfigured: Boolean
        get() = isCloudConfigured || isGemmaConfigured

    /** Kept for callers that still ask “are they using their own model?” */
    val useOwnKey: Boolean get() = isConfigured
}
