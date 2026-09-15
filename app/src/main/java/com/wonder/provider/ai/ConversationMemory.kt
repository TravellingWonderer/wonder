package com.wonder.provider.ai

import com.wonder.provider.model.AgentCard
import com.wonder.provider.model.ChatTurn
import com.wonder.provider.model.Speaker
import com.wonder.provider.model.TurnPhase

/**
 * Hermes-inspired session memory at travel scale: protect recent turns verbatim, keep a structured
 * checkpoint of everything before that in the system prompt so the model does not lose the thread.
 */
class ConversationMemory(
    private val summarizer: TravelSessionSummarizer = TravelSessionSummarizer()
) {
    private var rollingSummary: String = ""
    private var sessionNotes: String = ""

    fun reset() {
        rollingSummary = ""
        sessionNotes = ""
    }

    /** Builds the checkpoint and transcript slice sent to the model for this turn. */
    fun prepare(history: List<ChatTurn>, input: String): PreparedSessionContext {
        val settled = settledTurns(history)
        val tail = settled.takeLast(VERBATIM_TAIL)
        val archived = settled.dropLast(VERBATIM_TAIL)

        val checkpoint = summarizer.buildCheckpoint(
            archived = archived,
            recentLookback = SUMMARY_LOOKBACK,
            sessionNotes = sessionNotes
        )

        val transcript = tail.map { it.toLlmTurn() } + LlmTurn(fromUser = true, content = input)

        return PreparedSessionContext(
            checkpoint = checkpoint,
            transcript = transcript
        )
    }

    /** Updates the rolling checkpoint after a completed exchange. */
    fun commit(history: List<ChatTurn>, input: String, reply: String) {
        val settled = settledTurns(history) + listOf(
            ChatTurn(id = "commit-user", speaker = Speaker.YOU, text = input),
            ChatTurn(id = "commit-wonder", speaker = Speaker.WONDER, text = reply)
        )
        val archived = settled.dropLast(VERBATIM_TAIL)
        rollingSummary = summarizer.buildCheckpoint(
            archived = archived,
            recentLookback = SUMMARY_LOOKBACK,
            sessionNotes = sessionNotes
        )
    }

    fun noteEvent(note: String) {
        if (note.isBlank()) return
        sessionNotes = if (sessionNotes.isBlank()) note.trim() else "${sessionNotes.trim()}\n${note.trim()}"
    }

    private fun settledTurns(history: List<ChatTurn>): List<ChatTurn> =
        history.filter { it.phase == TurnPhase.SETTLED && it.text.isNotBlank() }

    companion object {
        /** Prior turns always spelled out in the checkpoint (Hermes tail protection, travel-sized). */
        const val SUMMARY_LOOKBACK = 10

        /** Most recent turns kept word-for-word in the message transcript. */
        const val VERBATIM_TAIL = 4
    }
}

data class PreparedSessionContext(
    val checkpoint: String,
    val transcript: List<LlmTurn>
)

/** Structured, deterministic checkpoint — no extra model call on the hot path. */
class TravelSessionSummarizer {

    fun buildCheckpoint(
        archived: List<ChatTurn>,
        recentLookback: Int,
        sessionNotes: String = ""
    ): String {
        if (archived.isEmpty() && sessionNotes.isBlank()) return ""

        val recent = archived.takeLast(recentLookback)
        val older = archived.dropLast(recentLookback)

        return buildString {
            appendLine("## Active thread")
            appendLine(activeThread(recent))
            appendLine()

            if (older.isNotEmpty()) {
                appendLine("## Earlier in this chat")
                appendLine(compressOlder(older))
                appendLine()
            }

            if (recent.isNotEmpty()) {
                appendLine("## Last ${recent.size} turns (checkpoint — stay consistent with these)")
                recent.forEach { turn ->
                    appendLine(formatTurn(turn))
                }
            }

            val topics = extractTopics(archived)
            if (topics.isNotEmpty()) {
                appendLine()
                appendLine("## Topics touched")
                appendLine(topics.joinToString(", "))
            }

            val open = pendingAsks(recent)
            if (open.isNotEmpty()) {
                appendLine()
                appendLine("## Still open")
                open.forEach { appendLine("- $it") }
            }

            if (sessionNotes.isNotBlank()) {
                appendLine()
                appendLine("## Session notes")
                appendLine(sessionNotes.trim())
            }
        }.trim()
    }

    private fun activeThread(recent: List<ChatTurn>): String {
        val lastUser = recent.lastOrNull { it.speaker == Speaker.YOU }?.text?.trim()
        if (!lastUser.isNullOrBlank()) return "Traveller last asked: \"$lastUser\""
        val lastWonder = recent.lastOrNull { it.speaker == Speaker.WONDER }?.text?.trim()
        return lastWonder?.let { "Wonder last said: \"${it.take(160)}\"" } ?: "None yet."
    }

    private fun compressOlder(older: List<ChatTurn>): String {
        val lines = older
            .chunked(2)
            .mapNotNull { chunk ->
                val user = chunk.firstOrNull { it.speaker == Speaker.YOU }?.text?.trim().orEmpty()
                val wonder = chunk.firstOrNull { it.speaker == Speaker.WONDER }?.text?.trim().orEmpty()
                when {
                    user.isNotBlank() && wonder.isNotBlank() ->
                        "- Asked \"${user.take(80)}\" → ${wonder.take(100)}"
                    user.isNotBlank() -> "- Asked \"${user.take(80)}\""
                    wonder.isNotBlank() -> "- Wonder: ${wonder.take(100)}"
                    else -> null
                }
            }
            .take(8)

        return if (lines.isEmpty()) {
            "Earlier small talk and trip questions."
        } else {
            lines.joinToString("\n")
        }
    }

    private fun formatTurn(turn: ChatTurn): String {
        val who = if (turn.speaker == Speaker.YOU) "Traveller" else "Wonder"
        val cards = cardHint(turn.cards)
        return "- $who: ${turn.text.trim()}${cards?.let { " $it" }.orEmpty()}"
    }

    private fun cardHint(cards: List<AgentCard>): String? {
        if (cards.isEmpty()) return null
        val labels = cards.mapNotNull { cardLabel(it) }.take(3)
        if (labels.isEmpty()) return null
        return "[showed ${labels.joinToString(", ")}]"
    }

    private fun cardLabel(card: AgentCard): String? = when (card) {
        is AgentCard.Nearby -> card.heading
        is AgentCard.Budget -> "budget"
        is AgentCard.DayPlan -> card.heading.substringBefore("·").trim()
        is AgentCard.NowNext -> "now/next"
        is AgentCard.TripOverview -> "trip overview"
        is AgentCard.ExpenseLog -> "expenses"
        is AgentCard.Loose -> card.heading
        is AgentCard.DraftDay -> "draft day in ${card.tour.city}"
        is AgentCard.Doorway -> card.headline
        is AgentCard.FlightResults -> "live flights ${card.result.query.origin}-${card.result.query.destination}"
    }

    private fun extractTopics(turns: List<ChatTurn>): List<String> {
        val blob = turns.joinToString(" ") { it.text }.lowercase()
        return TOPIC_KEYWORDS.filter { (keyword, _) -> keyword in blob }.map { it.second }.distinct()
    }

    private fun pendingAsks(recent: List<ChatTurn>): List<String> {
        val lastWonder = recent.lastOrNull { it.speaker == Speaker.WONDER }?.text.orEmpty()
        return buildList {
            if (lastWonder.contains('?')) add("Answer Wonder's last question before changing subject.")
            recent.filter { it.speaker == Speaker.YOU && it.text.contains('?') }
                .takeLast(2)
                .forEach { add("Traveller asked: \"${it.text.take(120)}\"") }
        }.distinct().take(3)
    }

    private companion object {
        val TOPIC_KEYWORDS = listOf(
            "budget" to "budget",
            "spent" to "spending",
            "expense" to "expenses",
            "tomorrow" to "tomorrow",
            "today" to "today",
            "sintra" to "Sintra",
            "alfama" to "Alfama",
            "book" to "booking",
            "free" to "free time",
            "nearby" to "nearby picks",
            "plan" to "planning",
            "train" to "transport",
            "food" to "food",
            "restaurant" to "food"
        )
    }
}

private fun ChatTurn.toLlmTurn(): LlmTurn {
    val cards = cards.takeIf { it.isNotEmpty() }
        ?.let { list ->
            val labels = list.mapNotNull { card ->
                when (card) {
                    is AgentCard.Nearby -> card.heading
                    is AgentCard.Budget -> "budget summary"
                    is AgentCard.DayPlan -> card.heading
                    is AgentCard.NowNext -> "now and next"
                    is AgentCard.DraftDay -> "draft for ${card.tour.city}"
                    else -> null
                }
            }
            if (labels.isEmpty()) null else labels.joinToString(", ")
        }

    val content = if (cards == null) text.trim() else "${text.trim()}\n[Wonder showed: $cards]"
    return LlmTurn(fromUser = speaker == Speaker.YOU, content = content)
}
