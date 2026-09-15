package com.wonder.provider.ai

import com.wonder.provider.BuildConfig
import com.wonder.provider.model.CuratedTour
import com.wonder.provider.model.TourBuildRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WonderHostedAiProvider(
    private val fallback: LocalAiProvider = LocalAiProvider(),
    private val baseUrl: String = BuildConfig.WONDER_AI_BASE_URL
) : AiTourProvider {

    override val sourceLabel = "Wonder AI"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    override suspend fun curateTour(request: TourBuildRequest): CuratedTour =
        withContext(Dispatchers.IO) {
            try {
                curateViaHostedApi(request)
            } catch (_: Exception) {
                fallback.curateTour(request)
            }
        }

    private fun curateViaHostedApi(request: TourBuildRequest): CuratedTour {
        val body = JSONObject().apply {
            put("city", request.city)
            put("interests", request.interests.map { it.name })
            put("pace", request.pace.name)
            put("duration", request.duration.name)
            put("budget", request.budget.name)
            put("groupSize", request.groupSize)
            put("notes", request.notes)
        }

        val httpRequest = Request.Builder()
            .url(baseUrl)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Wonder-Client", "android-provider/1.0")
            .build()

        val responseBody = client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) error("Hosted AI unavailable (${response.code})")
            response.body?.string() ?: error("Empty response from Wonder AI")
        }

        return AiResponseParser.parse(responseBody, request)
    }
}

object AiProviderFactory {

    fun create(settings: AiSettings): AiTourProvider = build(settings)

    fun createChat(settings: AiSettings): AiChatProvider = build(settings)

    private fun build(settings: AiSettings): LlmAiProvider = when (settings.providerType) {
        AiProviderType.OPENAI -> OpenAiProvider(settings.apiKey)
        AiProviderType.GEMINI -> GeminiAiProvider(settings.apiKey)
        AiProviderType.ANTHROPIC -> AnthropicAiProvider(settings.apiKey)
        AiProviderType.MISTRAL -> MistralAiProvider(settings.apiKey)
        AiProviderType.CUSTOM_OPENAI_COMPAT -> CustomOpenAiCompatibleProvider(
            apiKey = settings.apiKey,
            baseUrl = settings.customApiBaseUrl,
            model = settings.customModelName,
            displayLabel = CustomOpenAiCompatibleProvider.displayLabel(
                settings.customApiBaseUrl,
                settings.customModelName
            )
        )
        AiProviderType.ON_DEVICE_WONDER, AiProviderType.GEMMA_LITERT ->
            error("On-device providers are created through AiSettingsRepository")
    }
}

class AiTourService(
    private val settingsRepository: AiSettingsRepository,
    private val hostedProvider: WonderHostedAiProvider = WonderHostedAiProvider()
) {

    suspend fun curateTour(request: TourBuildRequest): AiTourOutcome {
        val settings = settingsRepository.getSettings()

        if (settings.isCloudConfigured) {
            val provider = AiProviderFactory.create(settings)
            val tour = provider.curateTour(request)
            return AiTourOutcome(
                tour = tour,
                sourceLabel = provider.sourceLabel,
                usedFallback = false
            )
        }

        val tour = hostedProvider.curateTour(request)
        return AiTourOutcome(
            tour = tour,
            sourceLabel = hostedProvider.sourceLabel,
            usedFallback = false
        )
    }

    fun activeSourceDescription(settings: AiSettings): String = when (settings.providerType) {
        AiProviderType.ON_DEVICE_WONDER ->
            "Wonder is using its built-in model — free, offline, no setup"
        AiProviderType.GEMMA_LITERT -> when {
            settings.isGemmaConfigured ->
                "Wonder is running ${settingsRepository.modelStore().displayName(settings.modelUri)} on this phone"
            else ->
                "Gemma on-device is selected — attach a model file downloaded through Google AI Edge Gallery"
        }
        else -> when {
            settings.isCloudConfigured && settings.providerType == AiProviderType.CUSTOM_OPENAI_COMPAT ->
                "Wonder is thinking with ${CustomOpenAiCompatibleProvider.displayLabel(
                    settings.customApiBaseUrl,
                    settings.customModelName
                )}"
            settings.isCloudConfigured ->
                "Wonder is thinking with your ${settings.providerType.displayName} subscription"
            settings.providerType == AiProviderType.CUSTOM_OPENAI_COMPAT ->
                "Add your OpenAI-compatible base URL, model name, and access token below"
            else ->
                "Connect a ${settings.providerType.displayName} key, or pick a free on-device model below"
        }
    }
}
