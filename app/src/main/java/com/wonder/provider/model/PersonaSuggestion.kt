package com.wonder.provider.model

import kotlin.random.Random

/** One traveller archetype with three tap-to-ask questions at different vibes. */
data class PersonaPanel(
    val persona: String,
    val questions: List<String>,
    /** Material-style icon key — AI-assigned or inferred. See [PersonaIconCatalog]. */
    val iconKey: String? = null,
    /** Optional avatar emoji — AI-assigned or inferred. Shown in the avatar circle when set. */
    val emoji: String? = null,
    /** Set for user-created personas attached to a trip. */
    val customPersonaId: String? = null,
    val personaDescription: String? = null,
    val isSticky: Boolean = false
) {
    init {
        if (!isSticky) {
            require(questions.size == QUESTIONS_PER_PERSONA) {
                "Each persona needs exactly $QUESTIONS_PER_PERSONA questions"
            }
        } else {
            require(!customPersonaId.isNullOrBlank()) { "Sticky panels require a customPersonaId" }
            require(questions.size <= QUESTIONS_PER_PERSONA) {
                "Sticky panels have at most $QUESTIONS_PER_PERSONA questions"
            }
        }
    }

    val isReady: Boolean get() = questions.size == QUESTIONS_PER_PERSONA

    fun panelKey(): String = customPersonaId ?: persona

    fun resolvedIconKey(): String = PersonaIconCatalog.resolveIconKey(persona, iconKey)

    fun resolvedEmoji(): String = PersonaIconCatalog.resolveEmoji(persona, iconKey, emoji)

    companion object {
        const val QUESTIONS_PER_PERSONA = 3
        const val DEFAULT_PERSONA_COUNT = 4

        fun sticky(
            personaId: String,
            name: String,
            description: String,
            iconKey: String?,
            emoji: String?,
            questions: List<String> = emptyList()
        ): PersonaPanel = PersonaPanel(
            persona = name,
            questions = questions,
            iconKey = iconKey,
            emoji = emoji,
            customPersonaId = personaId,
            personaDescription = description,
            isSticky = true
        )
    }
}

/** @deprecated Legacy flat chip — use [PersonaPanel]. */
data class PersonaSuggestion(
    val persona: String,
    val question: String
)

object TravellerPersonas {
    val catalog = listOf(
        "Lazy Panda",
        "Sceptic Nanny",
        "Food Junkie",
        "Beer-a-holic",
        "Weekend Warrior",
        "Budget Hawk",
        "Golden Hour Chaser",
        "Chaos Gremlin",
        "Culture Vulture",
        "Night Owl",
        "Early Riser",
        "Instagram Realist",
        "Rain Plan B",
        "Local's Cousin",
        "Slow Travel Monk"
    )

    fun fromPlainQuestions(
        questions: List<String>,
        excludePersonas: Set<String> = emptySet(),
        excludeQuestions: Set<String> = emptySet()
    ): List<PersonaPanel> = buildDefaultPanels(
        excludePersonas = excludePersonas,
        excludeQuestions = excludeQuestions + questions.filter { it.isNotBlank() }.toSet()
    )

    fun buildPanels(
        questionPool: List<String>,
        personaCount: Int = PersonaPanel.DEFAULT_PERSONA_COUNT,
        excludePersonas: Set<String> = emptySet(),
        excludeQuestions: Set<String> = emptySet()
    ): List<PersonaPanel> {
        val random = Random.Default
        val personas = pickUnused(excludePersonas, personaCount, random)
        val pool = questionPool
            .filter { it.isNotBlank() && !SuggestionSimilarity.matchesAny(it, excludeQuestions) }
            .distinctBy { it.lowercase() }
            .shuffled(random)

        return personas.mapIndexed { index, persona ->
            val slice = pool.drop(index * PersonaPanel.QUESTIONS_PER_PERSONA)
                .take(PersonaPanel.QUESTIONS_PER_PERSONA)
            val questions = personaVoiceQuestions(
                persona = persona,
                seed = slice,
                excludeQuestions = excludeQuestions,
                random = random
            )
            PersonaPanel(
                persona = persona,
                questions = questions,
                iconKey = PersonaIconCatalog.catalogIconKey(persona),
                emoji = PersonaIconCatalog.catalogEmoji(persona)
            )
        }.shuffled(random)
    }

    fun buildPanelsFromTriplets(
        triplets: List<List<String>>,
        excludePersonas: Set<String> = emptySet(),
        excludeQuestions: Set<String> = emptySet()
    ): List<PersonaPanel> {
        val random = Random.Default
        val personas = pickUnused(excludePersonas, triplets.size, random)
        return triplets.zip(personas).map { (_, persona) ->
            PersonaPanel(
                persona = persona,
                questions = personaVoiceQuestions(
                    persona = persona,
                    seed = emptyList(),
                    excludeQuestions = excludeQuestions,
                    random = random
                ),
                iconKey = PersonaIconCatalog.catalogIconKey(persona),
                emoji = PersonaIconCatalog.catalogEmoji(persona)
            )
        }.shuffled(random)
    }

    fun pickUnused(used: Set<String>, count: Int, random: Random = Random.Default): List<String> {
        val available = catalog.filter { it !in used }.shuffled(random)
        if (available.size >= count) return available.take(count)
        return (available + catalog.shuffled(random)).distinct().take(count)
    }

    fun toPanel(
        persona: String,
        rawQuestions: List<String>,
        excludeQuestions: Set<String> = emptySet(),
        iconKey: String? = null,
        emoji: String? = null
    ): PersonaPanel {
        val resolvedKey = PersonaIconCatalog.resolveIconKey(persona, iconKey)
        return PersonaPanel(
            persona = persona,
            questions = personaVoiceQuestions(
                persona = persona,
                seed = rawQuestions,
                excludeQuestions = excludeQuestions,
                random = Random.Default
            ),
            iconKey = resolvedKey,
            emoji = PersonaIconCatalog.resolveEmoji(persona, resolvedKey, emoji)
        )
    }

    fun finalizePanels(
        panels: List<PersonaPanel>,
        excludePersonas: Set<String> = emptySet(),
        excludeQuestions: Set<String> = emptySet(),
        targetCount: Int = PersonaPanel.DEFAULT_PERSONA_COUNT
    ): List<PersonaPanel> {
        val random = Random.Default
        val seenPersonas = mutableSetOf<String>()
        val seenQuestions = mutableSetOf<String>()

        val cleaned = panels.mapNotNull { panel ->
            if (panel.persona.isBlank()) return@mapNotNull null
            if (panel.persona in excludePersonas && panel.persona in seenPersonas) return@mapNotNull null
            val validSeeds = panel.questions.filter { question ->
                question.isNotBlank() &&
                    !SuggestionSimilarity.matchesAny(question, excludeQuestions) &&
                    PersonaQuestionCatalog.isShowableQuestion(panel.persona, question)
            }
            val persona = if (panel.persona in excludePersonas) {
                pickUnused(excludePersonas + seenPersonas, 1, random).firstOrNull() ?: panel.persona
            } else {
                panel.persona
            }
            seenPersonas.add(persona)
            val samePersona = persona == panel.persona
            val resolvedKey = PersonaIconCatalog.resolveIconKey(
                persona,
                panel.iconKey.takeIf { samePersona }
            )
            val voiceQuestions = personaVoiceQuestions(
                persona = persona,
                seed = validSeeds,
                excludeQuestions = excludeQuestions + seenQuestions,
                random = random
            )
            if (voiceQuestions.isEmpty()) return@mapNotNull null
            voiceQuestions.forEach { seenQuestions.add(it.lowercase()) }
            PersonaPanel(
                persona = persona,
                questions = voiceQuestions,
                iconKey = resolvedKey,
                emoji = PersonaIconCatalog.resolveEmoji(
                    persona,
                    resolvedKey,
                    panel.emoji.takeIf { samePersona }
                )
            )
        }

        return cleaned.take(targetCount).shuffled(random)
    }

    /** Builds panels with persona-voice questions only — ignores generic triplet pools. */
    fun buildDefaultPanels(
        personaCount: Int = PersonaPanel.DEFAULT_PERSONA_COUNT,
        excludePersonas: Set<String> = emptySet(),
        excludeQuestions: Set<String> = emptySet()
    ): List<PersonaPanel> {
        val random = Random.Default
        val personas = pickUnused(excludePersonas, personaCount, random)
        return personas.map { persona ->
            PersonaPanel(
                persona = persona,
                questions = personaVoiceQuestions(persona, emptyList(), excludeQuestions, random),
                iconKey = PersonaIconCatalog.catalogIconKey(persona),
                emoji = PersonaIconCatalog.catalogEmoji(persona)
            )
        }.shuffled(random)
    }

    private fun personaVoiceQuestions(
        persona: String,
        seed: List<String>,
        excludeQuestions: Set<String>,
        random: Random
    ): List<String> = PersonaQuestionCatalog.questionsFor(
        persona = persona,
        seed = seed,
        exclude = excludeQuestions,
        random = random
    )
}

object SuggestionSimilarity {
    private val topicKeys = listOf(
        "budget", "spent", "expense", "next", "tomorrow", "today", "unbooked", "book",
        "whole trip", "full plan", "nearby", "free", "empty", "draft", "flight", "eat", "food"
    )

    fun matchesAny(question: String, excluded: Set<String>): Boolean =
        excluded.any { isSimilar(question, it) }

    fun isSimilar(a: String, b: String): Boolean {
        val left = normalize(a)
        val right = normalize(b)
        if (left.isBlank() || right.isBlank()) return false
        if (left == right) return true
        if (left in right || right in left) return true
        val leftTopics = topicKeys.filter { it in left }.toSet()
        val rightTopics = topicKeys.filter { it in right }.toSet()
        return leftTopics.isNotEmpty() && leftTopics == rightTopics
    }

    fun expand(excluded: Set<String>): Set<String> = excluded

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
}
