package com.wonder.provider.ai

import com.wonder.provider.model.CuratedTour
import com.wonder.provider.model.TourBuildRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One message in a multi-turn exchange sent to a model. */
data class LlmTurn(val fromUser: Boolean, val content: String)

/** A provider that can hold a conversation, not just answer a one-shot prompt. */
interface AiChatProvider {
    val sourceLabel: String
    suspend fun converse(systemPrompt: String, turns: List<LlmTurn>): String
}

internal class HttpAiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    suspend fun post(url: String, headers: Map<String, String>, body: JSONObject): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw AiTourError.ProviderFailed("API error ${response.code}: $responseBody")
                }
                responseBody
            }
        }
}

internal abstract class LlmAiProvider(
    protected val apiKey: String,
    private val providerType: AiProviderType,
    override val sourceLabel: String
) : AiTourProvider, AiChatProvider {

    protected abstract suspend fun callLlm(systemPrompt: String, turns: List<LlmTurn>): String

    override suspend fun curateTour(request: TourBuildRequest): CuratedTour {
        val raw = send(
            AiPromptBuilder.buildSystemPrompt(),
            listOf(LlmTurn(fromUser = true, content = AiPromptBuilder.buildUserPrompt(request)))
        )
        return AiResponseParser.parse(raw, request)
    }

    override suspend fun converse(systemPrompt: String, turns: List<LlmTurn>): String =
        send(systemPrompt, turns)

    private suspend fun send(systemPrompt: String, turns: List<LlmTurn>): String {
        if (!providerType.validateKeyFormat(apiKey)) {
            throw AiTourError.InvalidApiKey(providerType)
        }
        return try {
            callLlm(systemPrompt, turns)
        } catch (e: AiTourError) {
            throw e
        } catch (e: Exception) {
            if (e.message?.contains("401") == true || e.message?.contains("403") == true) {
                throw AiTourError.InvalidApiKey(providerType)
            }
            throw AiTourError.NetworkError(e)
        }
    }

    /** OpenAI-shaped `messages` array, shared by OpenAI and Mistral. */
    protected fun openAiMessages(systemPrompt: String, turns: List<LlmTurn>): JSONArray =
        JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            turns.forEach {
                put(
                    JSONObject()
                        .put("role", if (it.fromUser) "user" else "assistant")
                        .put("content", it.content)
                )
            }
        }
}

internal class OpenAiProvider(apiKey: String) : LlmAiProvider(
    apiKey = apiKey,
    providerType = AiProviderType.OPENAI,
    sourceLabel = "OpenAI"
) {
    private val http = HttpAiClient()

    override suspend fun callLlm(systemPrompt: String, turns: List<LlmTurn>): String {
        val body = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("temperature", 0.7)
            put("response_format", JSONObject().put("type", "json_object"))
            put("messages", openAiMessages(systemPrompt, turns))
        }
        val response = http.post(
            url = "https://api.openai.com/v1/chat/completions",
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            ),
            body = body
        )
        return JSONObject(response)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }
}

internal class GeminiAiProvider(apiKey: String) : LlmAiProvider(
    apiKey = apiKey,
    providerType = AiProviderType.GEMINI,
    sourceLabel = "Google Gemini"
) {
    private val http = HttpAiClient()

    override suspend fun callLlm(systemPrompt: String, turns: List<LlmTurn>): String {
        val contents = JSONArray().apply {
            turns.forEach { turn ->
                put(
                    JSONObject()
                        .put("role", if (turn.fromUser) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", turn.content)))
                )
            }
        }
        val body = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            )
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("responseMimeType", "application/json")
            })
        }
        val response = http.post(
            url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey",
            headers = mapOf("Content-Type" to "application/json"),
            body = body
        )
        return JSONObject(response)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }
}

internal class AnthropicAiProvider(apiKey: String) : LlmAiProvider(
    apiKey = apiKey,
    providerType = AiProviderType.ANTHROPIC,
    sourceLabel = "Anthropic Claude"
) {
    private val http = HttpAiClient()

    override suspend fun callLlm(systemPrompt: String, turns: List<LlmTurn>): String {
        val messages = JSONArray().apply {
            turns.forEach { turn ->
                put(
                    JSONObject()
                        .put("role", if (turn.fromUser) "user" else "assistant")
                        .put("content", turn.content)
                )
            }
        }
        val body = JSONObject().apply {
            put("model", "claude-3-5-haiku-20241022")
            put("max_tokens", 4096)
            put("system", systemPrompt)
            put("messages", messages)
        }
        val response = http.post(
            url = "https://api.anthropic.com/v1/messages",
            headers = mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to "2023-06-01",
                "Content-Type" to "application/json"
            ),
            body = body
        )
        return JSONObject(response)
            .getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
    }
}

internal class MistralAiProvider(apiKey: String) : LlmAiProvider(
    apiKey = apiKey,
    providerType = AiProviderType.MISTRAL,
    sourceLabel = "Mistral AI"
) {
    private val http = HttpAiClient()

    override suspend fun callLlm(systemPrompt: String, turns: List<LlmTurn>): String {
        val body = JSONObject().apply {
            put("model", "mistral-small-latest")
            put("temperature", 0.7)
            put("response_format", JSONObject().put("type", "json_object"))
            put("messages", openAiMessages(systemPrompt, turns))
        }
        val response = http.post(
            url = "https://api.mistral.ai/v1/chat/completions",
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            ),
            body = body
        )
        return JSONObject(response)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }
}

/** Any endpoint that speaks the OpenAI chat completions API — OpenRouter, Ollama, LM Studio, etc. */
internal class CustomOpenAiCompatibleProvider(
    apiKey: String,
    private val baseUrl: String,
    private val model: String,
    displayLabel: String
) : LlmAiProvider(
    apiKey = apiKey,
    providerType = AiProviderType.CUSTOM_OPENAI_COMPAT,
    sourceLabel = displayLabel
) {
    private val http = HttpAiClient()

    override suspend fun callLlm(systemPrompt: String, turns: List<LlmTurn>): String {
        val body = JSONObject().apply {
            put("model", model)
            put("temperature", 0.7)
            put("messages", openAiMessages(systemPrompt, turns))
        }
        val response = http.post(
            url = chatCompletionsUrl(baseUrl),
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            ),
            body = body
        )
        return JSONObject(response)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    companion object {
        fun chatCompletionsUrl(baseUrl: String): String {
            val trimmed = baseUrl.trim().removeSuffix("/")
            return if (trimmed.endsWith("/chat/completions", ignoreCase = true)) {
                trimmed
            } else {
                "$trimmed/chat/completions"
            }
        }

        fun displayLabel(baseUrl: String, model: String): String {
            val host = runCatching {
                java.net.URI(trimmedBase(baseUrl)).host?.substringBefore(".")
            }.getOrNull()?.replaceFirstChar { it.titlecase() }
            return when {
                !host.isNullOrBlank() -> "$host · $model"
                model.isNotBlank() -> model
                else -> "Custom API"
            }
        }

        private fun trimmedBase(baseUrl: String): String {
            val trimmed = baseUrl.trim().removeSuffix("/")
            return if (trimmed.endsWith("/chat/completions", ignoreCase = true)) {
                trimmed.removeSuffix("/chat/completions").removeSuffix("/")
            } else {
                trimmed
            }
        }
    }
}
