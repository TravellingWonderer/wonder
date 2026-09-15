package com.wonder.provider.model

import kotlin.random.Random

/**
 * Persona-voice questions only — concrete, in-character, never meta templates or trip-admin prompts.
 */
object PersonaQuestionCatalog {

    private val catalog: Map<String, List<String>> = mapOf(
        "Lazy Panda" to listOf(
            "Where's the coziest spot to eat without a long queue?",
            "Can we swap something out for a slow morning?",
            "What's worth doing within a five-minute walk?",
            "Which day looks light enough to sleep in?",
            "Is there a park bench view worth lingering on?"
        ),
        "Sceptic Nanny" to listOf(
            "What could go wrong with this plan?",
            "Are we walking too much between stops?",
            "What safety stuff are we glossing over?",
            "Which reservation actually needs a backup?",
            "Is that neighborhood fine after dark?"
        ),
        "Food Junkie" to listOf(
            "Where's the one dish I can't leave without trying?",
            "What's open late if we're hungry after plans?",
            "Which market or street food fits our route?",
            "Who makes the best version of the local specialty?",
            "Where do chefs eat on their night off?"
        ),
        "Beer-a-holic" to listOf(
            "Where do locals actually drink around here?",
            "What's a good brewery or bar near tonight?",
            "Any casual pint spots that won't feel touristy?",
            "Which beer garden fits a lazy afternoon?",
            "Is there a taproom walkable from where we're staying?"
        ),
        "Weekend Warrior" to listOf(
            "What's the most packed day — can we trim it?",
            "Where can we squeeze in something active?",
            "What early start is actually worth it?",
            "Is there a hike or climb that fits one morning?",
            "Which activity will we feel in our legs tomorrow?"
        ),
        "Budget Hawk" to listOf(
            "Where are we overspending for what we get?",
            "What's the best cheap eat that still feels local?",
            "Which paid thing could we skip without regret?",
            "Any free experiences that beat the ticketed ones?",
            "Where are tourists paying double for no reason?"
        ),
        "Golden Hour Chaser" to listOf(
            "Where's the best sunset viewpoint on our route?",
            "Which day has the nicest light for photos?",
            "What should we reschedule to catch golden hour?",
            "Which rooftop or waterfront is worth the timing?",
            "Where does the light hit the old town best?"
        ),
        "Chaos Gremlin" to listOf(
            "What's the wildest thing we could still pull off?",
            "Where should we ditch the plan and wander?",
            "Any weird local thing we'd regret skipping?",
            "What's open late that's slightly unhinged?",
            "Where would a spontaneous detour actually pay off?"
        ),
        "Culture Vulture" to listOf(
            "Which museum or gallery fits a free afternoon?",
            "What's the one cultural stop we'd be idiots to miss?",
            "Any smaller exhibit that's better than the big one?",
            "Which neighborhood tells the city's story best?",
            "Is there a performance worth building an evening around?"
        ),
        "Night Owl" to listOf(
            "What actually happens after 10pm around here?",
            "Where should late dinner land on our schedule?",
            "Any live music or night market worth staying up for?",
            "Which bar district is fun without being a trap?",
            "What's still open when everything else closes?"
        ),
        "Early Riser" to listOf(
            "What opens early before the crowds hit?",
            "Which morning spot is best before 9am?",
            "Can we front-load something quiet at sunrise?",
            "Where's the best bakery line worth joining early?",
            "Which viewpoint is empty if we're up first?"
        ),
        "Instagram Realist" to listOf(
            "What's photogenic but not a total time sink?",
            "Which famous spot is better from the side angle?",
            "Where's a pretty walk that doesn't feel staged?",
            "What's worth one photo but not a two-hour queue?",
            "Which mural or facade is actually in a nice area?"
        ),
        "Rain Plan B" to listOf(
            "If it rains, what's still fun indoors nearby?",
            "Which outdoor plans need a backup?",
            "Where's a cozy café if weather turns?",
            "What museum or market saves a wet afternoon?",
            "Which covered street is good for a rainy stroll?"
        ),
        "Local's Cousin" to listOf(
            "What would my cousin skip that tourists love?",
            "Where do people who live here actually eat?",
            "What's the neighborhood move tourists miss?",
            "Which tourist trap is fine once, quickly?",
            "Where's the supermarket snack I'd actually buy?"
        ),
        "Slow Travel Monk" to listOf(
            "Which day feels too rushed — can we lighten it?",
            "Where's a good place to sit and people-watch?",
            "What should we leave unplanned on purpose?",
            "Which café could become our daily ritual?",
            "What's worth doing slowly instead of checking off?"
        )
    )

    /** Questions safe to show in the UI — filters generic admin prompts and meta templates. */
    fun isShowableQuestion(persona: String, question: String): Boolean {
        val trimmed = question.trim()
        if (trimmed.isBlank() || trimmed.length < 12) return false
        if (isGenericTripQuestion(trimmed)) return false
        if (isMetaTemplate(trimmed, persona)) return false
        return true
    }

    fun questionsFor(
        persona: String,
        seed: List<String> = emptyList(),
        exclude: Set<String> = emptySet(),
        count: Int = PersonaPanel.QUESTIONS_PER_PERSONA,
        random: Random = Random.Default
    ): List<String> {
        val pool = fullPoolFor(persona)
        val chosen = linkedSetOf<String>()

        seed
            .map { it.trim() }
            .filter { isShowableQuestion(persona, it) && !SuggestionSimilarity.matchesAny(it, exclude) }
            .forEach { chosen.add(it) }

        pool
            .filter { isShowableQuestion(persona, it) && !SuggestionSimilarity.matchesAny(it, exclude) &&
                chosen.none { c -> SuggestionSimilarity.isSimilar(c, it) } }
            .shuffled(random)
            .forEach { candidate ->
                if (chosen.size >= count) return@forEach
                chosen.add(candidate)
            }

        if (chosen.size < count) {
            unknownPersonaPool()
                .filter { !SuggestionSimilarity.matchesAny(it, exclude + chosen) &&
                    chosen.none { c -> SuggestionSimilarity.isSimilar(c, it) } }
                .shuffled(random)
                .forEach { candidate ->
                    if (chosen.size >= count) return@forEach
                    chosen.add(candidate)
                }
        }

        return chosen.take(count).shuffled(random)
    }

    fun questionsForCustom(
        name: String,
        description: String,
        exclude: Set<String> = emptySet(),
        random: Random = Random.Default
    ): List<String> {
        val fromDescription = inferPool("$name $description")
        val pool = if (fromDescription.isNotEmpty()) fromDescription else inferPool(name)
        if (pool.isNotEmpty()) {
            return questionsFor(name, seed = emptyList(), exclude = exclude, random = random)
        }
        return questionsFor(name, seed = emptyList(), exclude = exclude, random = random)
    }

    /** @deprecated Use [isShowableQuestion] */
    fun isPersonaVoice(persona: String, question: String): Boolean = isShowableQuestion(persona, question)

    fun isGenericTripQuestion(question: String): Boolean {
        val normalized = question.lowercase()
        return genericPhrases.any { phrase -> normalized.contains(phrase) }
    }

    fun sanitize(persona: String, questions: List<String>, exclude: Set<String> = emptySet()): List<String> {
        val valid = questions.filter { isShowableQuestion(persona, it) && !SuggestionSimilarity.matchesAny(it, exclude) }
        if (valid.size >= PersonaPanel.QUESTIONS_PER_PERSONA) {
            return valid.take(PersonaPanel.QUESTIONS_PER_PERSONA)
        }
        return questionsFor(
            persona = persona,
            seed = valid,
            exclude = exclude + valid.toSet()
        )
    }

    private fun fullPoolFor(persona: String): List<String> {
        val direct = resolvePool(persona)
        if (direct.isNotEmpty()) return direct
        return inferPool(persona)
    }

    private fun resolvePool(persona: String): List<String> =
        catalog[persona] ?: catalog.entries.firstOrNull { (key, _) ->
            key.equals(persona, ignoreCase = true)
        }?.value.orEmpty()

    private fun inferPool(persona: String): List<String> {
        val lower = persona.lowercase()
        return when {
            "lazy" in lower || "panda" in lower -> catalog["Lazy Panda"].orEmpty()
            "sceptic" in lower || "nanny" in lower -> catalog["Sceptic Nanny"].orEmpty()
            "food" in lower || "junkie" in lower || "eat" in lower || "chef" in lower -> catalog["Food Junkie"].orEmpty()
            "beer" in lower || "brew" in lower || "bar" in lower || "pub" in lower -> catalog["Beer-a-holic"].orEmpty()
            "budget" in lower || "hawk" in lower || "cheap" in lower -> catalog["Budget Hawk"].orEmpty()
            "golden" in lower || "sunset" in lower || "photo" in lower && "instagram" !in lower -> catalog["Golden Hour Chaser"].orEmpty()
            "chaos" in lower || "gremlin" in lower || "wild" in lower -> catalog["Chaos Gremlin"].orEmpty()
            "culture" in lower || "vulture" in lower || "museum" in lower -> catalog["Culture Vulture"].orEmpty()
            "night" in lower || "owl" in lower || "late" in lower -> catalog["Night Owl"].orEmpty()
            "early" in lower || "riser" in lower || "morning" in lower -> catalog["Early Riser"].orEmpty()
            "instagram" in lower || "realist" in lower -> catalog["Instagram Realist"].orEmpty()
            "rain" in lower || "weather" in lower -> catalog["Rain Plan B"].orEmpty()
            "local" in lower || "cousin" in lower || "resident" in lower -> catalog["Local's Cousin"].orEmpty()
            "slow" in lower || "monk" in lower || "unhurried" in lower -> catalog["Slow Travel Monk"].orEmpty()
            "warrior" in lower || "weekend" in lower || "active" in lower -> catalog["Weekend Warrior"].orEmpty()
            else -> emptyList()
        }
    }

    /** Genuine first-person questions when no catalog match — never mention the persona by name. */
    private fun unknownPersonaPool(): List<String> = listOf(
        "What's the most underrated spot around here?",
        "Where would I actually spend a free afternoon?",
        "What's worth doing that most tourists skip?",
        "Which neighborhood feels most lived-in?",
        "Where's the best version of the local specialty?",
        "What would make this trip feel less rushed?",
        "Which view is worth the walk up?",
        "Where do people hang out on a weekday evening?"
    )

    private fun isMetaTemplate(question: String, persona: String): Boolean {
        val n = question.lowercase()
        if (metaPhrases.any { n.contains(it) }) return true
        val personaLower = persona.trim().lowercase()
        if (personaLower.length > 2 && n.contains(personaLower)) return true
        if (n.startsWith("as ") && ("what" in n || "how" in n)) return true
        if (n.contains("what would ") && n.contains(" want")) return true
        if (n.contains("what should we figure")) return true
        return false
    }

    private val metaPhrases = listOf(
        "speaking as",
        "what would i ask",
        "what should i ask",
        "what should we figure out",
        "the first thing i'd ask",
        "after hearing that",
        "if i only listened to",
        "would bug me most",
        "would want to change",
        "would refuse to skip",
        "judge our pace",
        "what about this trip would",
        "from my pov",
        "in that voice",
        "tap to ask",
        "persona would"
    )

    private val genericPhrases = listOf(
        "how's our budget",
        "how much have we spent",
        "show me the whole trip",
        "show me the full plan",
        "what's still unbooked",
        "what's on tomorrow",
        "what's next",
        "what can you help",
        "where should we start planning",
        "help me shape this trip",
        "expense tracker",
        "which days are empty",
        "help me draft",
        "show me model settings",
        "let me try that again"
    )
}
