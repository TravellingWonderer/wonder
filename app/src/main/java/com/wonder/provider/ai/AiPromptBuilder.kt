package com.wonder.provider.ai

import com.wonder.provider.model.*
import org.json.JSONArray
import org.json.JSONObject

object AiPromptBuilder {

    fun buildSystemPrompt(): String = """
        You are Wonder AI, an expert travel curator for experience providers.
        Create personalised tour itineraries. Return ONLY valid JSON — no markdown, no commentary.
        Schema:
        {
          "title": "string",
          "summary": "string (2-3 sentences, first person)",
          "highlights": ["string with emoji prefix"],
          "stops": [{
            "order": 1,
            "name": "string",
            "description": "string",
            "category": "FOOD|CULTURE|ADVENTURE|NATURE|NIGHTLIFE|SHOPPING|ART|LOCAL",
            "durationMinutes": 60,
            "estimatedCost": 25,
            "currency": "€",
            "timeSlot": "9:00 AM",
            "emoji": "🍽️",
            "tip": "local insider tip"
          }]
        }
    """.trimIndent()

    fun buildUserPrompt(request: TourBuildRequest): String {
        val interests = if (request.interests.isEmpty()) "well-rounded mix"
        else request.interests.joinToString(", ") { it.label }

        return buildString {
            appendLine("Create a ${request.duration.label.lowercase()} tour for ${request.city}.")
            appendLine("Only include real, well-known places that exist in that destination.")
            appendLine("Never invent a city from a trip nickname. If the city is not a real place, return {\"title\":\"\",\"summary\":\"\",\"highlights\":[],\"stops\":[]}.")
            appendLine("Interests: $interests")
            appendLine("Pace: ${request.pace.label} (${request.pace.stopsPerDay} stops/day)")
            appendLine("Budget: ${request.budget.label} (max €${request.budget.maxStopCost}/stop)")
            appendLine("Group size: ${request.groupSize}")
            appendLine("Days: ${request.duration.days}")
            if (request.notes.isNotBlank()) appendLine("Special requests: ${request.notes}")
            append("Sequence stops chronologically with realistic timing.")
        }
    }
}

object AiResponseParser {

    fun parse(json: String, request: TourBuildRequest): CuratedTour {
        val clean = json.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val root = JSONObject(clean)
        val stopsArray = root.getJSONArray("stops")
        val stops = (0 until stopsArray.length()).map { i ->
            val obj = stopsArray.getJSONObject(i)
            TourStop(
                order = obj.optInt("order", i + 1),
                name = obj.getString("name"),
                description = obj.getString("description"),
                category = parseCategory(obj.getString("category")),
                durationMinutes = obj.optInt("durationMinutes", 60),
                estimatedCost = obj.optInt("estimatedCost", 0),
                currency = obj.optString("currency", "€"),
                timeSlot = obj.optString("timeSlot", "Flexible"),
                emoji = obj.optString("emoji", "📍"),
                tip = obj.optString("tip", "")
            )
        }

        val highlights = root.getJSONArray("highlights").toStringList()
        val totalMinutes = stops.sumOf { it.durationMinutes }
        val totalBudget = stops.sumOf { it.estimatedCost }

        return CuratedTour(
            id = "tour_${System.currentTimeMillis()}",
            title = root.getString("title"),
            city = request.city.trim(),
            summary = root.getString("summary"),
            highlights = highlights,
            stops = stops,
            totalDurationHours = totalMinutes / 60.0,
            estimatedBudget = totalBudget,
            currency = stops.firstOrNull()?.currency ?: "€",
            matchScore = 92,
            days = request.duration.days
        )
    }

    private fun parseCategory(value: String): TourInterest =
        runCatching { TourInterest.valueOf(value.uppercase()) }
            .getOrDefault(TourInterest.CULTURE)

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }
}
