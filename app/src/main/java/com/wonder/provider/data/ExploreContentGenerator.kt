package com.wonder.provider.data

import com.wonder.provider.ai.AiChatProvider
import com.wonder.provider.ai.LlmTurn
import com.wonder.provider.model.ExploreFeed
import com.wonder.provider.model.ExploreFeedKind
import com.wonder.provider.model.FoodFavorite
import com.wonder.provider.model.LocalGuide
import com.wonder.provider.model.LocalTake
import com.wonder.provider.model.SmallTripIdea
import com.wonder.provider.model.TourInterest
import com.wonder.provider.model.TripIdea
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Builds the Explore feed once per day; [ExploreRepository] caches the result.
 */
internal object ExploreContentGenerator {

    private const val AI_GENERATION_TIMEOUT_MS = 45_000L

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM")

    private const val AI_SYSTEM_DISCOVERY = """
You are a travel discovery editor. Return ONLY valid JSON, no markdown, matching this schema:
{
  "tripIdeas": [{"id":"...","title":"...","destination":"...","summary":"...","durationDays":5,"budgetHint":"€800","emoji":"🇵🇹","vibe":"slow food"}],
  "foodFavorites": [{"id":"...","name":"...","place":"...","description":"...","priceHint":"€15","emoji":"🐟"}],
  "localGuides": [{"id":"...","name":"...","specialty":"...","bio":"...","tip":"...","emoji":"🧭"}],
  "smallTrips": [{"id":"...","title":"...","duration":"half day","description":"...","location":"...","emoji":"🌊"}],
  "localTakes": [{"id":"...","place":"...","category":"Restaurant","opinion":"...","residentName":"...","residentDetail":"lives in Alfama"}]
}
Provide 4 tripIdeas, 4 foodFavorites, 3 localGuides, 4 smallTrips, 4 localTakes. Be specific to the city. Sound like a local, not a brochure. This is for someone planning a future trip — inspire them with fresh picks they have not seen before.
Only use real, well-known places that exist in that city. If the destination is not a real city or country, return empty arrays for every list instead of inventing a place.
The trip title is a nickname and must never be used as a city or venue name.
"""

    private const val AI_SYSTEM_LIVE = """
You are a travel concierge for someone currently ON their trip. Return ONLY valid JSON, no markdown, matching this schema:
{
  "tripIdeas": [{"id":"...","title":"...","destination":"...","summary":"...","durationDays":1,"budgetHint":"€40","emoji":"🌊","vibe":"today"}],
  "foodFavorites": [{"id":"...","name":"...","place":"...","description":"...","priceHint":"€15","emoji":"🐟"}],
  "localGuides": [{"id":"...","name":"...","specialty":"...","bio":"...","tip":"...","emoji":"🧭"}],
  "smallTrips": [{"id":"...","title":"...","duration":"2 hours","description":"...","location":"...","emoji":"🌊"}],
  "localTakes": [{"id":"...","place":"...","category":"Restaurant","opinion":"...","residentName":"...","residentDetail":"lives nearby"}]
}
Provide 4 tripIdeas (same-day or next-day side quests from their base — NOT generic vacation plans), 4 foodFavorites (where to eat today near their plans), 3 localGuides (bookable today or tomorrow), 4 smallTrips (fill gaps in today's schedule — afternoon/evening), 4 localTakes (timely tips for this week). Reference their itinerary when provided. Sound practical and immediate.
Only use real places in the stated destination. If the destination is not a real city, return empty arrays. Never turn the trip title into a city or venue.
"""

    suspend fun generate(
        context: ExploreGenerationContext,
        chatProvider: AiChatProvider?
    ): ExploreFeed {
        if (chatProvider != null &&
            (context.feedKind == ExploreFeedKind.NEARBY || DestinationInference.isGrounded(context.destination))
        ) {
            runCatching {
                withTimeoutOrNull(AI_GENERATION_TIMEOUT_MS) {
                    val system = if (context.feedKind == ExploreFeedKind.LIVE_TRIP) AI_SYSTEM_LIVE else AI_SYSTEM_DISCOVERY
                    val raw = chatProvider.converse(
                        system,
                        listOf(LlmTurn(fromUser = true, content = context.userPrompt()))
                    )
                    ExploreFeedCodec.decodeFromAi(raw, context, chatProvider.sourceLabel)
                }?.let { return it }
            }
        }
        return if (context.feedKind == ExploreFeedKind.LIVE_TRIP) {
            generateLiveLocal(context)
        } else {
            generateLocal(context.destination, context.interests, context.today)
        }
    }

    private fun ExploreGenerationContext.userPrompt(): String = buildString {
        if (!DestinationInference.isGrounded(destination)) {
            append("Destination is not decided. Do not invent a city or any specific places. Return empty arrays.")
            return@buildString
        }
        append("Destination: $destination (a real place — never substitute the trip title).")
        append(" Interests: ${interests.joinToString { it.label }}.")
        if (feedKind == ExploreFeedKind.LIVE_TRIP) {
            append(" Trip: \"$tripTitle\".")
            dayNumber?.let { append(" Today is day $it of the trip.") }
            tripStartDate?.let { start ->
                tripEndDate?.let { end ->
                    append(" Dates: ${DATE_FORMAT.format(start)}–${DATE_FORMAT.format(end)}.")
                }
            }
            append(
                if (todayPlan.isEmpty()) {
                    " Today's itinerary: open day — suggest how to fill it."
                } else {
                    " Today's itinerary: ${todayPlan.joinToString("; ")}."
                }
            )
        }
    }

    fun generateLocal(
        destination: String,
        interests: Set<TourInterest>,
        today: LocalDate = LocalDate.now()
    ): ExploreFeed {
        if (!DestinationInference.isGrounded(destination) && !CityCatalog.isKnownPlace(destination)) {
            return emptyFeed(destination, today, ExploreFeedKind.DISCOVERY)
        }
        val city = CityCatalog.resolveCity(destination)
        val base = when {
            city.lowercase().contains("lisbon") -> lisbonFeed(destination, today, ExploreFeedKind.DISCOVERY)
            city.lowercase().contains("paris") -> parisFeed(destination, today)
            else -> genericFeed(destination, city, today, ExploreFeedKind.DISCOVERY)
        }
        return rotateForDay(base, today)
    }

    /** Location-based picks when the traveller has no active trip. */
    fun generateNearby(cityLabel: String, today: LocalDate = LocalDate.now()): ExploreFeed {
        val city = CityCatalog.resolveCity(cityLabel)
        val pois = CityCatalog.getPois(city)
        val base = generateLocal(cityLabel, setOf(TourInterest.LOCAL, TourInterest.FOOD), today)

        val thingsToDo = pois
            .filter { it.category != TourInterest.FOOD }
            .shuffled(Random(today.toEpochDay()))
            .take(4)
            .mapIndexed { index, poi ->
                TripIdea(
                    id = "nearby-do-$index",
                    title = poi.name,
                    destination = cityLabel,
                    summary = poi.description + if (poi.tip.isNotBlank()) " Tip: ${poi.tip}" else "",
                    durationDays = 1,
                    budgetHint = if (poi.cost > 0) "≈ €${poi.cost}" else "Free–low",
                    emoji = poi.emoji,
                    vibe = "Near you"
                )
            }

        return base.copy(
            destination = cityLabel,
            sourceLabel = "Wonder · Near you",
            feedKind = ExploreFeedKind.NEARBY,
            tripIdeas = thingsToDo.ifEmpty {
                base.tripIdeas.map { it.copy(vibe = "Near you", durationDays = 1) }
            },
            foodFavorites = base.foodFavorites.mapIndexed { index, food ->
                food.copy(
                    id = "nearby-food-$index",
                    description = "Worth a stop nearby — ${food.description}"
                )
            },
            smallTrips = base.smallTrips.mapIndexed { index, trip ->
                trip.copy(
                    id = "nearby-small-$index",
                    duration = "Half day",
                    description = "Easy from where you are — ${trip.description}"
                )
            },
            localTakes = base.localTakes.mapIndexed { index, take ->
                take.copy(
                    id = "nearby-take-$index",
                    opinion = "Right now in ${cityLabel.split(",").first().trim()}: ${take.opinion}"
                )
            }
        )
    }

    /** Picks a different slice of local catalog content each calendar day. */
    private fun rotateForDay(feed: ExploreFeed, today: LocalDate): ExploreFeed {
        val random = Random(today.toEpochDay())
        return feed.copy(
            tripIdeas = feed.tripIdeas.shuffled(random).take(4),
            foodFavorites = feed.foodFavorites.shuffled(random).take(4),
            localGuides = feed.localGuides.shuffled(random).take(3),
            smallTrips = feed.smallTrips.shuffled(random).take(4),
            localTakes = feed.localTakes.shuffled(random).take(4)
        )
    }

    private fun generateLiveLocal(context: ExploreGenerationContext): ExploreFeed {
        if (!DestinationInference.isGrounded(context.destination) && !CityCatalog.isKnownPlace(context.destination)) {
            return emptyFeed(context.destination, context.today, ExploreFeedKind.LIVE_TRIP)
        }
        val city = CityCatalog.resolveCity(context.destination)
        val discovery = when {
            city.lowercase().contains("lisbon") ->
                lisbonFeed(context.destination, context.today, ExploreFeedKind.LIVE_TRIP)
            city.lowercase().contains("paris") ->
                parisFeed(context.destination, context.today).copy(feedKind = ExploreFeedKind.LIVE_TRIP)
            else ->
                genericFeed(context.destination, city, context.today, ExploreFeedKind.LIVE_TRIP)
        }
        val dayLabel = context.dayNumber?.let { "Day $it" } ?: "Today"
        val planHint = context.todayPlan.firstOrNull()?.let { " near $it" }.orEmpty()

        return rotateForDay(
            discovery.copy(
            sourceLabel = "Wonder · $dayLabel",
            feedKind = ExploreFeedKind.LIVE_TRIP,
            contentDate = context.today.toString(),
            tripIdeas = discovery.tripIdeas.mapIndexed { index, idea ->
                idea.copy(
                    id = "live-idea-$index",
                    title = "While you're here: ${idea.title}",
                    durationDays = 1,
                    summary = "$dayLabel — ${idea.summary}",
                    vibe = "Today & nearby"
                )
            },
            foodFavorites = discovery.foodFavorites.mapIndexed { index, food ->
                food.copy(
                    id = "live-food-$index",
                    description = "For $dayLabel$planHint — ${food.description}"
                )
            },
            smallTrips = listOf(
                SmallTripIdea(
                    id = "live-gap-am",
                    title = if (context.todayPlan.isEmpty()) "Morning wander" else "Before ${context.todayPlan.first()}",
                    duration = "2–3 hours",
                    description = "Slow start — coffee, one street, no agenda. Save energy for what's booked.",
                    location = context.destination.split(",").first().trim(),
                    emoji = "☕"
                ),
                SmallTripIdea(
                    id = "live-gap-pm",
                    title = "Afternoon pocket trip",
                    duration = "Half day",
                    description = "Escape the main plan — one neighbourhood or viewpoint locals hit on a weekday.",
                    location = city,
                    emoji = "🌤️"
                )
            ) + discovery.smallTrips.take(2).mapIndexed { index, trip ->
                trip.copy(id = "live-small-$index", duration = "This week")
            },
            localTakes = discovery.localTakes.mapIndexed { index, take ->
                take.copy(
                    id = "live-take-$index",
                    opinion = "This week in ${context.destination.split(",").first().trim()}: ${take.opinion}"
                )
            }
            ),
            context.today
        )
    }

    private fun lisbonFeed(
        destination: String,
        today: LocalDate,
        kind: ExploreFeedKind
    ) = ExploreFeed(
        destination = destination,
        gatheredAtEpochMillis = System.currentTimeMillis(),
        sourceLabel = "Wonder",
        contentDate = today.toString(),
        feedKind = kind,
        tripIdeas = listOf(
            TripIdea(
                id = "idea-coast",
                title = "Atlantic & azulejo week",
                destination = "Lisbon + Cascais + Sintra",
                summary = "Slow mornings in Alfama, tram to Belém for pasteis, one misty Sintra palace day, and a sunset train to Cascais. No rush — leave two afternoons completely open.",
                durationDays = 8,
                budgetHint = "€1,400–1,800 pp",
                emoji = "🌊",
                vibe = "Coastal & unhurried"
            ),
            TripIdea(
                id = "idea-food",
                title = "Petiscos trail",
                destination = "Lisbon",
                summary = "Eat your way through tascas in Mouraria, a market lunch at Time Out, wine in Bairro Alto, and a chef's table in Príncipe Real. Budget extra for the unplanned second bottle.",
                durationDays = 5,
                budgetHint = "€900–1,100 pp",
                emoji = "🍷",
                vibe = "Food-first"
            ),
            TripIdea(
                id = "idea-setubal",
                title = "Lisbon base, Setúbal days",
                destination = "Lisbon + Setúbal peninsula",
                summary = "Stay in the city but escape twice: Arrábida cliffs by car, fresh fish in Setúbal harbour, and a dolphin-watching morning if the weather holds.",
                durationDays = 6,
                budgetHint = "€1,000–1,300 pp",
                emoji = "🐬",
                vibe = "City + day trips"
            ),
            TripIdea(
                id = "idea-winter",
                title = "Winter light in Lisbon",
                destination = "Lisbon",
                summary = "Fewer crowds, golden low sun, long lunches that turn into fado nights. Miradouros at 4pm, not midnight. Perfect for remote workers who want culture after 5.",
                durationDays = 10,
                budgetHint = "€1,600–2,000 pp",
                emoji = "☀️",
                vibe = "Off-season calm"
            )
        ),
        foodFavorites = listOf(
            FoodFavorite(
                id = "food-cervejaria",
                name = "Polvo à lagareiro",
                place = "Cervejaria Ramiro, Intendente",
                description = "Garlic-heavy octopus with olive oil and potatoes — the version locals order before the prawns. Book or queue before 19:30.",
                priceHint = "€35–45 pp with wine",
                emoji = "🐙"
            ),
            FoodFavorite(
                id = "food-tasca",
                name = "Bacalhau à brás",
                place = "Tasca do Chico, Bairro Alto",
                description = "Shredded cod with egg and straw potatoes in a room where fado starts around 21:00. Eat first, stay for the music.",
                priceHint = "€18–22",
                emoji = "🐟"
            ),
            FoodFavorite(
                id = "food-pastel",
                name = "Pastel de nata",
                place = "Manteigaria, Baixa",
                description = "Flaky, not too sweet, best eaten standing at the counter while they're still warm. Skip the tourist queue at Belém unless you're already there.",
                priceHint = "€1.30",
                emoji = "🥧"
            ),
            FoodFavorite(
                id = "food-market",
                name = "Prego no pão",
                place = "Time Out Market, Cais do Sodré",
                description = "Steak sandwich at Prego — lunch between museum stops. Share a table, order a half bottle of Douro.",
                priceHint = "€14–20",
                emoji = "🥩"
            )
        ),
        localGuides = listOf(
            LocalGuide(
                id = "guide-ines",
                name = "Inês R.",
                specialty = "Alfama & fado walks",
                bio = "Born in Alfama; runs small-group walks that skip the postcard route and end at a family tasca.",
                tip = "Ask her about the Tuesday flea market — she knows which stalls are worth the early start.",
                emoji = "🎸"
            ),
            LocalGuide(
                id = "guide-miguel",
                name = "Miguel S.",
                specialty = "Surf & coast days",
                bio = "Surf instructor based in Costa da Caparica; picks beaches by wind and swell, not Instagram.",
                tip = "He'll lend a wetsuit — bring sunscreen even in winter.",
                emoji = "🏄"
            ),
            LocalGuide(
                id = "guide-sofia",
                name = "Sofia L.",
                specialty = "Contemporary Lisbon",
                bio = "Curator who opens LX Factory, MAAT, and studio visits most visitors never find.",
                tip = "Her Saturday morning route includes the best coffee in Alcântara.",
                emoji = "🎨"
            )
        ),
        smallTrips = listOf(
            SmallTripIdea(
                id = "small-sintra",
                title = "Sintra palaces, back door",
                duration = "Full day",
                description = "Train from Rossio, Pena early, then Monserrate gardens instead of the second palace queue. Tram 435 back through the old town for travesseiros.",
                location = "Sintra",
                emoji = "🏰"
            ),
            SmallTripIdea(
                id = "small-cascais",
                title = "Cascais by train",
                duration = "Half day",
                description = "Forty minutes from Cais do Sodré. Walk the coastal path to Boca do Inferno, swim if it's warm, gelado on the marina.",
                location = "Cascais",
                emoji = "🌊"
            ),
            SmallTripIdea(
                id = "small-arrabida",
                title = "Arrábida viewpoints",
                duration = "Full day",
                description = "Rent a car or join a small tour — turquoise coves, cork forests, and a long lunch in Setúbal. Don't rush the last viewpoint.",
                location = "Setúbal peninsula",
                emoji = "🌿"
            ),
            SmallTripIdea(
                id = "small-montijo",
                title = "Ferry to the south bank",
                duration = "Afternoon",
                description = "Five-minute ferry from Terreiro do Paço. Different light on the city, cheaper seafood, and almost no tourists.",
                location = "Montijo",
                emoji = "⛴️"
            )
        ),
        localTakes = listOf(
            LocalTake(
                id = "take-pasteis",
                place = "Pastéis de Belém",
                category = "Bakery",
                opinion = "The pasteis are good — they're the original — but I'd only go if I'm already in Belém for the monastery. Otherwise Manteigaria or Aloma in the city, no queue.",
                residentName = "João",
                residentDetail = "Graphic designer, lives in Santos"
            ),
            LocalTake(
                id = "take-park",
                place = "Park rooftop bar",
                category = "Bar",
                opinion = "Best view in Bairro Alto for one drink at sunset. After that, walk down to a tasca — you're paying for the elevator, not the cocktails.",
                residentName = "Beatriz",
                residentDetail = "Bartender, Chiado"
            ),
            LocalTake(
                id = "take-tram28",
                place = "Tram 28",
                category = "Transport",
                opinion = "Take it once, early morning, Graça to Estrela — not at 11am with your backpack. Or just walk Alfama; it's faster and you won't get pickpocketed.",
                residentName = "Rui",
                residentDetail = "Taxi driver, 20 years in Lisbon"
            ),
            LocalTake(
                id = "take-timeout",
                place = "Time Out Market",
                category = "Food hall",
                opinion = "Touristy but honest about it — great when you're with friends and want ten options without a reservation war. Locals come for lunch, not dinner.",
                residentName = "Mariana",
                residentDetail = "Food writer, Campo de Ourique"
            )
        )
    )

    private fun parisFeed(destination: String, today: LocalDate) =
        genericFeed(destination, "Paris", today, ExploreFeedKind.DISCOVERY).copy(
            tripIdeas = listOf(
                TripIdea(
                    "p1",
                    "Left Bank literary week",
                    "Paris",
                    "Cafés, small museums, and long dinners — no Eiffel queue required.",
                    7,
                    "€1,500 pp",
                    "📚",
                    "Slow culture"
                ),
                TripIdea(
                    "p2",
                    "Boulangerie crawl",
                    "Paris",
                    "A different arrondissement each morning; afternoons in parks.",
                    4,
                    "€800 pp",
                    "🥐",
                    "Food-first"
                )
            ) + genericFeed(destination, "Paris", today, ExploreFeedKind.DISCOVERY).tripIdeas.take(2)
        )

    private fun emptyFeed(
        destination: String,
        today: LocalDate,
        kind: ExploreFeedKind
    ) = ExploreFeed(
        destination = destination,
        gatheredAtEpochMillis = System.currentTimeMillis(),
        sourceLabel = "Wonder",
        contentDate = today.toString(),
        feedKind = kind,
        tripIdeas = emptyList(),
        foodFavorites = emptyList(),
        localGuides = emptyList(),
        smallTrips = emptyList(),
        localTakes = emptyList()
    )

    private fun genericFeed(
        destination: String,
        city: String,
        today: LocalDate,
        kind: ExploreFeedKind
    ) = ExploreFeed(
        destination = destination,
        gatheredAtEpochMillis = System.currentTimeMillis(),
        sourceLabel = "Wonder",
        contentDate = today.toString(),
        feedKind = kind,
        tripIdeas = listOf(
            TripIdea(
                id = "gen-weekend",
                title = "$city long weekend",
                destination = city,
                summary = "Two full days plus travel — old town, one standout meal, and one viewpoint at golden hour.",
                durationDays = 3,
                budgetHint = "Varies",
                emoji = "✈️",
                vibe = "Weekend escape"
            ),
            TripIdea(
                id = "gen-food",
                title = "$city through its markets",
                destination = city,
                summary = "Build days around market mornings and neighbourhood dinners locals actually eat.",
                durationDays = 5,
                budgetHint = "Mid-range",
                emoji = "🍽️",
                vibe = "Food trail"
            ),
            TripIdea(
                id = "gen-slow",
                title = "Slow $city",
                destination = city,
                summary = "One neighbourhood per day, no checklist tourism — leave room for the café you stumble into.",
                durationDays = 7,
                budgetHint = "Flexible",
                emoji = "🌿",
                vibe = "Unhurried"
            ),
            TripIdea(
                id = "gen-art",
                title = "$city galleries & back streets",
                destination = city,
                summary = "Contemporary spaces mixed with the one classic museum worth the ticket.",
                durationDays = 4,
                budgetHint = "Mid-range",
                emoji = "🎨",
                vibe = "Art & design"
            )
        ),
        foodFavorites = CityCatalog.getPois(city)
            .filter { it.category == TourInterest.FOOD || it.category == TourInterest.LOCAL }
            .take(4)
            .mapIndexed { i, poi ->
                FoodFavorite(
                    id = "food-gen-$i",
                    name = poi.name,
                    place = city,
                    description = poi.description,
                    priceHint = if (poi.cost > 0) "≈ €${poi.cost}" else "Free–low",
                    emoji = poi.emoji
                )
            },
        localGuides = listOf(
            LocalGuide(
                id = "guide-gen-1",
                name = "Local host",
                specialty = "Neighbourhood walks",
                bio = "Small-group walks through $city away from the main drag.",
                tip = poiTip(city),
                emoji = "🧭"
            )
        ),
        smallTrips = CityCatalog.getPois(city)
            .filter { it.category == TourInterest.NATURE || it.category == TourInterest.ADVENTURE }
            .take(3)
            .mapIndexed { i, poi ->
                SmallTripIdea(
                    id = "small-gen-$i",
                    title = poi.name,
                    duration = "${poi.durationMinutes / 60}h",
                    description = poi.description,
                    location = city,
                    emoji = poi.emoji
                )
            } + SmallTripIdea(
            id = "small-gen-extra",
            title = "$city after dark",
            duration = "Evening",
            description = "One bar, one late kitchen, one walk home through the lit streets.",
            location = city,
            emoji = "🌙"
        ),
        localTakes = listOf(
            LocalTake(
                id = "take-gen-1",
                place = "Main tourist strip",
                category = "Area",
                opinion = "Fine once, quickly — the good stuff is one parallel street over.",
                residentName = "A local",
                residentDetail = "Lives in $city"
            ),
            LocalTake(
                id = "take-gen-2",
                place = "Hop-on bus",
                category = "Tour",
                opinion = "Walk or metro instead unless mobility is an issue — you'll see more and pay less.",
                residentName = "A local",
                residentDetail = "Lives in $city"
            )
        )
    )

    private fun poiTip(city: String): String =
        CityCatalog.getPois(city).firstOrNull()?.tip ?: "Ask what's good today — menus change."
}
