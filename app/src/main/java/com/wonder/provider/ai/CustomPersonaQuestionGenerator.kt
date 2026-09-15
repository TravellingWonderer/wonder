package com.wonder.provider.ai

import com.wonder.provider.model.ChatTurn
import com.wonder.provider.model.CustomPersona
import com.wonder.provider.model.PersonaQuestionCatalog
import com.wonder.provider.model.PersonaPanel
import com.wonder.provider.model.Speaker
import com.wonder.provider.model.TurnPhase
import org.json.JSONObject

/** Generates three trip-grounded questions in a custom persona's voice. */
class CustomPersonaQuestionGenerator(
    private val prompt: AgentPrompt,
    private val settingsRepository: AiSettingsRepository,
    private val suggestionPicker: ContextualSuggestionPicker
) {

    suspend fun generate(
        persona: CustomPersona,
        history: List<ChatTurn>,
        lastWonderSay: String?
    ): List<String> {
        val provider = settingsRepository.chatProvider()
        if (provider != null && settingsRepository.getSettings().isConfigured) {
            runCatching {
                val raw = provider.converse(
                    systemPrompt = SYSTEM,
                    turns = listOf(
                        LlmTurn(
                            fromUser = true,
                            content = buildUserMessage(persona, history, lastWonderSay)
                        )
                    )
                )
                parseQuestions(raw)?.let { parsed ->
                    val clean = PersonaQuestionCatalog.sanitize(persona.name, parsed)
                    if (clean.size == PersonaPanel.QUESTIONS_PER_PERSONA) return clean
                }
            }
        }
        return offlineQuestions(persona, lastWonderSay)
    }

    private fun buildUserMessage(
        persona: CustomPersona,
        history: List<ChatTurn>,
        lastWonderSay: String?
    ): String = buildString {
        appendLine("PERSONA NAME: ${persona.name}")
        appendLine("PERSONALITY / VOICE:")
        appendLine(persona.description.trim())
        appendLine()
        appendLine("TRIP BRIEFING")
        appendLine(prompt.compactContext())
        appendLine()
        appendLine("RECENT CHAT")
        appendLine(conversationSnippet(history))
        if (!lastWonderSay.isNullOrBlank()) {
            appendLine()
            appendLine("WONDER'S LAST REPLY")
            appendLine(lastWonderSay.trim())
        }
    }.trim()

    private fun conversationSnippet(history: List<ChatTurn>): String {
        val settled = history
            .filter { it.phase == TurnPhase.SETTLED && it.text.isNotBlank() }
            .takeLast(6)
        if (settled.isEmpty()) return "No prior turns yet."
        return settled.joinToString("\n") { turn ->
            val who = if (turn.speaker == Speaker.YOU) "Traveller" else "Wonder"
            "$who: ${turn.text.trim().take(220)}"
        }
    }

    private fun parseQuestions(raw: String): List<String>? {
        val trimmed = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val json = runCatching { JSONObject(trimmed) }.getOrNull()
            ?: runCatching {
                val start = trimmed.indexOf('{')
                val end = trimmed.lastIndexOf('}')
                if (start >= 0 && end > start) JSONObject(trimmed.substring(start, end + 1)) else null
            }.getOrNull()
            ?: return null
        val array = json.optJSONArray("questions") ?: return null
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    private fun offlineQuestions(persona: CustomPersona, lastWonderSay: String?): List<String> =
        PersonaQuestionCatalog.questionsForCustom(
            name = persona.name,
            description = persona.description
        )

    private companion object {
        val SYSTEM = """
            You write EXACTLY three first-person questions this specific traveller persona would tap to ask Wonder.
            Stay fully in character — every question must reflect their personality and description.
            STRICT: Never generic trip-admin prompts (budget tracker, whole trip, unbooked, tomorrow, what's next).
            Never meta questions about being a persona ("As X, what should I ask?", "Speaking as…", "What would X want?").
            Write concrete, curious questions — things this person would genuinely wonder about the trip.
            Never second person. Each question under 90 chars.
            Return JSON only: {"questions":["…","…","…"]}
        """.trimIndent()
    }
}
