package com.wonder.provider.data.travel

import com.wonder.provider.model.FlightOfferSummary
import com.wonder.provider.model.FlightSearchQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Live flight offers via [Duffel](https://duffel.com/docs) — the main self-serve alternative now
 * that Google Flights has no public API and Amadeus self-service is closed.
 */
class DuffelFlightClient(private val accessToken: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: FlightSearchQuery): List<FlightOfferSummary> = withContext(Dispatchers.IO) {
        require(accessToken.isNotBlank()) { "Duffel access token not configured." }

        val passengers = JSONArray().apply {
            repeat(query.adults.coerceIn(1, 9)) { put(JSONObject().put("type", "adult")) }
        }
        val slices = JSONArray().apply {
            put(slice(query.origin, query.destination, query.departDate))
            query.returnDate?.let { put(slice(query.destination, query.origin, it)) }
        }

        val body = JSONObject()
            .put("data", JSONObject()
                .put("slices", slices)
                .put("passengers", passengers)
                .put("cabin_class", "economy")
            )
            .toString()

        val createRequest = Request.Builder()
            .url("$BASE/air/offer_requests")
            .header("Authorization", "Bearer $accessToken")
            .header("Duffel-Version", API_VERSION)
            .header("Accept", "application/json")
            .post(body.toRequestBody(JSON))
            .build()

        val offerRequestId = client.newCall(createRequest).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw TravelSearchException(parseDuffelError(raw, response.code))
            }
            JSONObject(raw).getJSONObject("data").getString("id")
        }

        fetchOffers(offerRequestId)
    }

    private fun fetchOffers(offerRequestId: String): List<FlightOfferSummary> {
        repeat(8) { attempt ->
            val url = "$BASE/air/offers?offer_request_id=$offerRequestId&limit=8"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("Duffel-Version", API_VERSION)
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw TravelSearchException(parseDuffelError(raw, response.code))
                }
                val json = JSONObject(raw)
                val offers = json.optJSONArray("data") ?: JSONArray()
                if (offers.length() > 0) {
                    return (0 until offers.length()).mapNotNull { index ->
                        parseOffer(offers.optJSONObject(index))
                    }.sortedBy { it.price }
                }
            }

            if (attempt < 7) Thread.sleep(800)
        }
        return emptyList()
    }

    private fun parseOffer(offer: JSONObject?): FlightOfferSummary? {
        if (offer == null) return null
        val price = offer.optString("total_amount").toDoubleOrNull() ?: return null
        val currency = offer.optString("total_currency", "EUR")
        val slices = offer.optJSONArray("slices") ?: return null
        if (slices.length() == 0) return null

        val firstSlice = slices.getJSONObject(0)
        val segments = firstSlice.optJSONArray("segments") ?: return null
        if (segments.length() == 0) return null

        val firstSeg = segments.getJSONObject(0)
        val lastSeg = segments.getJSONObject(segments.length() - 1)
        val stops = (segments.length() - 1).coerceAtLeast(0)

        val airline = firstSeg.optJSONObject("marketing_carrier")
            ?.optString("name")
            ?.ifBlank { firstSeg.optJSONObject("operating_carrier")?.optString("name") }
            ?.ifBlank { "Airline" }
            ?: "Airline"

        val origin = firstSeg.optJSONObject("origin")?.optString("iata_code").orEmpty()
        val destination = lastSeg.optJSONObject("destination")?.optString("iata_code").orEmpty()
        val departAt = parseDateTime(firstSeg.optString("departing_at"))
        val arriveAt = parseDateTime(lastSeg.optString("arriving_at"))

        return FlightOfferSummary(
            id = offer.optString("id"),
            airline = airline,
            price = price,
            currency = currency,
            origin = origin,
            destination = destination,
            departLabel = formatTime(departAt),
            arriveLabel = formatTime(arriveAt),
            durationLabel = formatDuration(departAt, arriveAt),
            stops = stops
        )
    }

    private fun slice(origin: String, destination: String, date: java.time.LocalDate): JSONObject =
        JSONObject()
            .put("origin", origin)
            .put("destination", destination)
            .put("departure_date", date.toString())

    private fun parseDateTime(value: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME) }.getOrNull()

    private fun formatTime(value: LocalDateTime?): String =
        value?.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)) ?: "—"

    private fun formatDuration(start: LocalDateTime?, end: LocalDateTime?): String {
        if (start == null || end == null) return ""
        val minutes = Duration.between(start, end).toMinutes().coerceAtLeast(0)
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    private fun parseDuffelError(body: String, code: Int): String {
        val message = runCatching {
            JSONObject(body).optJSONArray("errors")
                ?.optJSONObject(0)
                ?.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return message ?: "Flight search failed (HTTP $code)."
    }

    private companion object {
        const val BASE = "https://api.duffel.com"
        const val API_VERSION = "v2"
        val JSON = "application/json".toMediaType()
    }
}

class TravelSearchException(message: String) : Exception(message)
