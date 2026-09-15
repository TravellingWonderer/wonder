package com.wonder.provider.data

import com.wonder.provider.model.ExploreFeed
import com.wonder.provider.model.ExploreFeedKind
import com.wonder.provider.model.FoodFavorite
import com.wonder.provider.model.LocalGuide
import com.wonder.provider.model.LocalTake
import com.wonder.provider.model.SmallTripIdea
import com.wonder.provider.model.TripIdea
import org.json.JSONArray
import org.json.JSONObject

internal object ExploreFeedCodec {

    fun encode(feed: ExploreFeed): String = JSONObject().apply {
        put("destination", feed.destination)
        put("gatheredAt", feed.gatheredAtEpochMillis)
        put("sourceLabel", feed.sourceLabel)
        put("contentDate", feed.contentDate)
        put("feedKind", feed.feedKind.name)
        put("tripIdeas", encodeTripIdeas(feed.tripIdeas))
        put("foodFavorites", encodeFood(feed.foodFavorites))
        put("localGuides", encodeGuides(feed.localGuides))
        put("smallTrips", encodeSmallTrips(feed.smallTrips))
        put("localTakes", encodeTakes(feed.localTakes))
    }.toString()

    fun decode(raw: String): ExploreFeed? = runCatching {
        val json = JSONObject(raw)
        val contentDate = json.optString("contentDate", "")
        if (contentDate.isBlank()) return null
        ExploreFeed(
            destination = json.getString("destination"),
            gatheredAtEpochMillis = json.getLong("gatheredAt"),
            sourceLabel = json.getString("sourceLabel"),
            contentDate = contentDate,
            feedKind = runCatching {
                ExploreFeedKind.valueOf(json.optString("feedKind", ExploreFeedKind.DISCOVERY.name))
            }.getOrDefault(ExploreFeedKind.DISCOVERY),
            tripIdeas = json.getJSONArray("tripIdeas").mapTripIdeas(),
            foodFavorites = json.getJSONArray("foodFavorites").mapFood(),
            localGuides = json.getJSONArray("localGuides").mapGuides(),
            smallTrips = json.getJSONArray("smallTrips").mapSmallTrips(),
            localTakes = json.getJSONArray("localTakes").mapTakes()
        )
    }.getOrNull()

    fun decodeFromAi(
        raw: String,
        context: ExploreGenerationContext,
        sourceLabel: String
    ): ExploreFeed? =
        runCatching {
            val json = extractJson(raw) ?: return null
            ExploreFeed(
                destination = context.destination,
                gatheredAtEpochMillis = System.currentTimeMillis(),
                sourceLabel = sourceLabel,
                contentDate = context.today.toString(),
                feedKind = context.feedKind,
                tripIdeas = json.optJSONArray("tripIdeas")?.mapTripIdeas().orEmpty(),
                foodFavorites = json.optJSONArray("foodFavorites")?.mapFood().orEmpty(),
                localGuides = json.optJSONArray("localGuides")?.mapGuides().orEmpty(),
                smallTrips = json.optJSONArray("smallTrips")?.mapSmallTrips().orEmpty(),
                localTakes = json.optJSONArray("localTakes")?.mapTakes().orEmpty()
            ).takeIf { it.tripIdeas.isNotEmpty() }
        }.getOrNull()

    private fun extractJson(raw: String): JSONObject? {
        val trimmed = raw.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return JSONObject(trimmed.substring(start, end + 1))
    }

    private fun JSONArray.mapTripIdeas(): List<TripIdea> = buildList {
        for (i in 0 until length()) {
            val o = getJSONObject(i)
            add(
                TripIdea(
                    id = o.optString("id", "idea-$i"),
                    title = o.getString("title"),
                    destination = o.optString("destination", ""),
                    summary = o.getString("summary"),
                    durationDays = o.optInt("durationDays", 5),
                    budgetHint = o.optString("budgetHint", ""),
                    emoji = o.optString("emoji", "✈️"),
                    vibe = o.optString("vibe", "")
                )
            )
        }
    }

    private fun JSONArray.mapFood(): List<FoodFavorite> = buildList {
        for (i in 0 until length()) {
            val o = getJSONObject(i)
            add(
                FoodFavorite(
                    id = o.optString("id", "food-$i"),
                    name = o.getString("name"),
                    place = o.getString("place"),
                    description = o.getString("description"),
                    priceHint = o.optString("priceHint", ""),
                    emoji = o.optString("emoji", "🍽️")
                )
            )
        }
    }

    private fun JSONArray.mapGuides(): List<LocalGuide> = buildList {
        for (i in 0 until length()) {
            val o = getJSONObject(i)
            add(
                LocalGuide(
                    id = o.optString("id", "guide-$i"),
                    name = o.getString("name"),
                    specialty = o.getString("specialty"),
                    bio = o.getString("bio"),
                    tip = o.optString("tip", ""),
                    emoji = o.optString("emoji", "🧭")
                )
            )
        }
    }

    private fun JSONArray.mapSmallTrips(): List<SmallTripIdea> = buildList {
        for (i in 0 until length()) {
            val o = getJSONObject(i)
            add(
                SmallTripIdea(
                    id = o.optString("id", "small-$i"),
                    title = o.getString("title"),
                    duration = o.getString("duration"),
                    description = o.getString("description"),
                    location = o.getString("location"),
                    emoji = o.optString("emoji", "🗺️")
                )
            )
        }
    }

    private fun JSONArray.mapTakes(): List<LocalTake> = buildList {
        for (i in 0 until length()) {
            val o = getJSONObject(i)
            add(
                LocalTake(
                    id = o.optString("id", "take-$i"),
                    place = o.getString("place"),
                    category = o.optString("category", "Place"),
                    opinion = o.getString("opinion"),
                    residentName = o.getString("residentName"),
                    residentDetail = o.optString("residentDetail", "")
                )
            )
        }
    }

    private fun encodeTripIdeas(ideas: List<TripIdea>): JSONArray = JSONArray().also { arr ->
        ideas.forEach { idea ->
            arr.put(
                JSONObject().apply {
                    put("id", idea.id)
                    put("title", idea.title)
                    put("destination", idea.destination)
                    put("summary", idea.summary)
                    put("durationDays", idea.durationDays)
                    put("budgetHint", idea.budgetHint)
                    put("emoji", idea.emoji)
                    put("vibe", idea.vibe)
                }
            )
        }
    }

    private fun encodeFood(food: List<FoodFavorite>): JSONArray = JSONArray().also { arr ->
        food.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("place", item.place)
                    put("description", item.description)
                    put("priceHint", item.priceHint)
                    put("emoji", item.emoji)
                }
            )
        }
    }

    private fun encodeGuides(guides: List<LocalGuide>): JSONArray = JSONArray().also { arr ->
        guides.forEach { guide ->
            arr.put(
                JSONObject().apply {
                    put("id", guide.id)
                    put("name", guide.name)
                    put("specialty", guide.specialty)
                    put("bio", guide.bio)
                    put("tip", guide.tip)
                    put("emoji", guide.emoji)
                }
            )
        }
    }

    private fun encodeSmallTrips(trips: List<SmallTripIdea>): JSONArray = JSONArray().also { arr ->
        trips.forEach { trip ->
            arr.put(
                JSONObject().apply {
                    put("id", trip.id)
                    put("title", trip.title)
                    put("duration", trip.duration)
                    put("description", trip.description)
                    put("location", trip.location)
                    put("emoji", trip.emoji)
                }
            )
        }
    }

    private fun encodeTakes(takes: List<LocalTake>): JSONArray = JSONArray().also { arr ->
        takes.forEach { take ->
            arr.put(
                JSONObject().apply {
                    put("id", take.id)
                    put("place", take.place)
                    put("category", take.category)
                    put("opinion", take.opinion)
                    put("residentName", take.residentName)
                    put("residentDetail", take.residentDetail)
                }
            )
        }
    }
}
