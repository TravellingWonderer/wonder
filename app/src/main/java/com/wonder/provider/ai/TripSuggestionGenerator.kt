package com.wonder.provider.ai

import com.wonder.provider.model.ChatTurn
import com.wonder.provider.model.PersonaIconCatalog
import com.wonder.provider.model.PersonaPanel
import com.wonder.provider.model.Speaker
import com.wonder.provider.model.TravellerPersonas
import com.wonder.provider.model.TripMode
import com.wonder.provider.model.TurnPhase
import org.json.JSONArray
import org.json.JSONObject

/**
 * Generates expandable persona panels — each avatar opens three questions at different vibes.
 */
class TripSuggestionGenerator(
    private val prompt: AgentPrompt,
    private val mode: () -> TripMode
) {

    suspend fun generate(
        provider: AiChatProvider,
        history: List<ChatTurn>,
        lastWonderSay: String?,
        welcomeOnly: Boolean,
        fallbackTriplets: List<List<String>>,
        exclude: List<PersonaPanel> = emptyList()
    ): List<PersonaPanel> = runCatching {
        val raw = provider.converse(
            systemPrompt = if (exclude.isEmpty()) SYSTEM_INITIAL else SYSTEM_MORE,
            turns = listOf(
                LlmTurn(
                    fromUser = true,
                    content = buildUserMessage(history, lastWonderSay, welcomeOnly, exclude)
                )
            )
        )
        parsePersonaPanels(raw).ifEmpty {
            TravellerPersonas.buildDefaultPanels()
        }.let { TravellerPersonas.finalizePanels(it) }
    }.getOrElse {
        TravellerPersonas.finalizePanels(TravellerPersonas.buildDefaultPanels())
    }

    private fun buildUserMessage(
        history: List<ChatTurn>,
        lastWonderSay: String?,
        welcomeOnly: Boolean,
        exclude: List<PersonaPanel>
    ): String = buildString {
        appendLine("MODE: ${mode().name}")
        if (welcomeOnly) {
            appendLine("MOMENT: Brand-new trip — first conversation, warm opener just given.")
        } else {
            appendLine("MOMENT: Mid-conversation — suggest what they should ask next.")
        }
        if (exclude.isNotEmpty()) {
            appendLine("Already shown — do NOT repeat these personas or questions:")
            exclude.forEach { panel ->
                panel.questions.forEach { question ->
                    appendLine("- ${panel.persona}: $question")
                }
            }
        }
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
        appendLine()
        appendLine("PERSONA CATALOG (pick varied names from this list):")
        appendLine(TravellerPersonas.catalog.shuffled().joinToString(", "))
        appendLine()
        appendLine(
            "STRICT RULE: Every question MUST sound like it could ONLY come from that persona's mouth. " +
                "A Budget Hawk asks about money; a Food Junkie asks about meals; a Lazy Panda asks about effort. " +
                "NEVER generic trip-admin prompts (budget tracker, whole trip, unbooked, tomorrow, what's next). " +
                "Each persona's three questions must reflect their personality — not interchangeable topics."
        )
        appendLine()
        appendLine(PersonaIconCatalog.promptForAi())
    }.trim()

    private fun conversationSnippet(history: List<ChatTurn>): String {
        val settled = history
            .filter { it.phase == TurnPhase.SETTLED && it.text.isNotBlank() }
            .takeLast(SNIPPET_TURNS)
        if (settled.isEmpty()) return "No prior turns yet."
        return settled.joinToString("\n") { turn ->
            val who = if (turn.speaker == Speaker.YOU) "Traveller" else "Wonder"
            "$who: ${turn.text.trim().take(220)}"
        }
    }

    private fun parsePersonaPanels(raw: String): List<PersonaPanel> {
        val trimmed = strip(raw)
        val root = extractJsonObject(trimmed) ?: return emptyList()
        root.optJSONArray("personas")?.let { array ->
            return parsePanelArray(array)
        }
        root.optJSONArray("suggestions")?.let { array ->
            return parseLegacyFlatArray(array)
        }
        return emptyList()
    }

    private fun parsePanelArray(array: JSONArray): List<PersonaPanel> =
        buildList {
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                val persona = entry.optString("persona").trim()
                val questionsArray = entry.optJSONArray("questions")
                val questions = buildList {
                    if (questionsArray != null) {
                        for (qIndex in 0 until questionsArray.length()) {
                            questionsArray.optString(qIndex).trim().takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    } else {
                        entry.optString("question").trim().takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
                if (persona.isNotBlank() && questions.isNotEmpty()) {
                    val iconKey = entry.optString("iconKey")
                        .ifBlank { entry.optString("icon") }
                    val emoji = entry.optString("emoji")
                    add(
                        TravellerPersonas.toPanel(
                            persona = persona,
                            rawQuestions = questions,
                            iconKey = iconKey.takeIf { it.isNotBlank() },
                            emoji = emoji.takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }.take(MAX_PERSONAS).shuffled()

    private fun parseLegacyFlatArray(array: JSONArray): List<PersonaPanel> {
        val triplets = buildList {
            var bucket = mutableListOf<String>()
            for (index in 0 until array.length()) {
                val objectEntry = array.optJSONObject(index)
                val question = when {
                    objectEntry != null -> objectEntry.optString("question")
                        .ifBlank { objectEntry.optString("text") }
                        .trim()
                    else -> array.optString(index).trim()
                }
                if (question.isBlank()) continue
                bucket.add(question)
                if (bucket.size == PersonaPanel.QUESTIONS_PER_PERSONA) {
                    add(bucket.toList())
                    bucket = mutableListOf()
                }
            }
            if (bucket.isNotEmpty()) add(bucket)
        }
        return TravellerPersonas.buildPanelsFromTriplets(triplets)
    }

    private fun extractJsonObject(raw: String): JSONObject? =
        runCatching { JSONObject(raw) }.getOrNull()
            ?: runCatching {
                val start = raw.indexOf('{')
                val end = raw.lastIndexOf('}')
                if (start >= 0 && end > start) JSONObject(raw.substring(start, end + 1)) else null
            }.getOrNull()

    private fun strip(raw: String): String = raw.trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

    private companion object {
        const val MIN_PERSONAS = 3
        const val MAX_PERSONAS = 5
        const val SNIPPET_TURNS = 6

        private val personaRules = """
            Each persona is a distinct traveller archetype with THREE first-person questions:
            - "persona": vivid 2–4 word name (from catalog or invent one like "Budget Hawk")
            - "iconKey": one key from the ICON KEYS list that fits this persona's vibe
            - "emoji": one emoji character that visually matches the persona (required)
            - "questions": array of exactly 3 strings, each under 90 chars, in that persona's voice ONLY

            STRICT: Every question must be unmistakably from THIS persona — never generic trip commands.
            Forbidden: "How's our budget?", "Show me the whole trip", "What's still unbooked?",
            "What's on tomorrow?", "What's next?", "As [persona], what should I ask?" — meta or admin prompts.

            Write concrete questions this person would genuinely wonder — not questions about asking questions.

            Question 1 = what this persona cares about most. Question 2 = their angle on the trip.
            Question 3 = playful but still in character. Never second person. Never bare UI commands.
        """.trimIndent()

        val SYSTEM_INITIAL = """
            You are a practical travel companion curating expandable persona avatars.
            Each persona offers THREE questions the user might tap after opening that avatar.

            Write $MIN_PERSONAS–$MAX_PERSONAS personas — all different vibes
            (lazy, sceptical, food-obsessed, budget-anxious, schedule-focused, etc.).
            Every persona MUST include a matching iconKey and emoji.

            $personaRules

            Return JSON only:
            {"personas":[{"persona":"Lazy Panda","iconKey":"spa","emoji":"🦥","questions":["…","…","…"]}]}
        """.trimIndent()

        val SYSTEM_MORE = """
            You are a practical travel companion. The traveller wants MORE persona avatars.
            Write $MIN_PERSONAS fresh personas with NEW names not already listed, each with 3 questions,
            iconKey, and emoji.

            $personaRules

            Return JSON only:
            {"personas":[{"persona":"…","iconKey":"…","emoji":"…","questions":["…","…","…"]}]}
        """.trimIndent()
    }
}
