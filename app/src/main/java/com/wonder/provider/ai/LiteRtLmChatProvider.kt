package com.wonder.provider.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LiteRtLmJniException
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs Gemma (or any `.litertlm` bundle) locally through LiteRT-LM — the same runtime Google AI
 * Edge Gallery uses. Wonder never ships a model; the traveller downloads one in Gallery (or from
 * Hugging Face) and attaches the file here.
 */
class LiteRtLmChatProvider(
    private val context: Context,
    private val modelStore: OnDeviceModelStore,
    private val modelUri: String
) : AiChatProvider {

    override val sourceLabel: String = "Gemma on-device"

    private val engineHolder = AtomicReference<Engine?>(null)
    private var modelPath: String? = null

    override suspend fun converse(systemPrompt: String, turns: List<LlmTurn>): String =
        withContext(Dispatchers.IO) {
            val path = modelPath ?: modelStore.materializeModelPath(modelUri)?.also { modelPath = it }
                ?: throw AiTourError.ProviderFailed("Model file unreadable — pick it again in settings.")

            val engine = engineHolder.get() ?: createEngine(path).also { engineHolder.set(it) }

            engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(systemPrompt),
                    initialMessages = turns.dropLast(1).map { turn ->
                        if (turn.fromUser) Message.user(turn.content) else Message.model(turn.content)
                    },
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.7)
                )
            ).use { conversation ->
                val last = turns.lastOrNull()?.content ?: ""
                val response = conversation.sendMessage(last)
                response.toString().trim().ifBlank {
                    throw AiTourError.ProviderFailed("On-device model returned an empty reply.")
                }
            }
        }

    private fun createEngine(path: String): Engine {
        return try {
            val config = EngineConfig(
                modelPath = path,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath
            )
            Engine(config).also { it.initialize() }
        } catch (error: LiteRtLmJniException) {
            throw AiTourError.ProviderFailed("Could not load the model: ${error.message}")
        } catch (error: Exception) {
            throw AiTourError.ProviderFailed("On-device engine failed: ${error.message}")
        }
    }

    fun release() {
        engineHolder.getAndSet(null)?.close()
        modelPath = null
    }
}
