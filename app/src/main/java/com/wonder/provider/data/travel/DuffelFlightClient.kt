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
        .readTimeout(90, TimeUnit.SECONDS)
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
            .put(
                "data",
                JSONObject()
                    .put("slices", slices)
                    .put("passengers", passengers)
                    .put("cabin_class", "economy")
            )
            .toString()

        // return_offers=true (default) embeds offers in the create response — prefer that over polling.
        val createRequest = Request.Builder()
            .url("$BASE/air/offer_requests?return_offers=true&supplier_timeout=20000")
            .header("Authorization", "Bearer $accessToken")
            .header("Duffel-Version", API_VERSION)
            .header("Accept", "application/json")
            .header("Accept-Encoding", "gzip")
            .post(body.toRequestBody(JSON))
            .build()

        client.newCall(createRequest).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw TravelSearchException(parseDuffelError(raw, response.code))
            }
            val data = JSONObject(raw).getJSONObject("data")
            val embedded = data.optJSONArray("offers")
            if (embedded != null && embedded.length() > 0) {
                return@withContext parseOffers(embedded)
            }
            val offerRequestId = data.getString("id")
            fetchOffers(offerRequestId)
        }
    }

    private fun fetchOffers(offerRequestId: String): List<FlightOfferSummary> {
        repeat(6) { attempt ->
            val url = "$BASE/air/offers?offer_request_id=$offerRequestId&sort=total_amount&limit=10"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("Duffel-Version", API_VERSION)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw TravelSearchException(parseDuffelError(raw, response.code))
                }
                val offers = JSONObject(raw).optJSONArray("data") ?: JSONArray()
                if (offers.length() > 0) {
                    return parseOffers(offers)
                }
            }

            if (attempt < 5) Thread.sleep(700)
        }
        return emptyList()
    }

    private fun parseOffers(offers: JSONArray): List<FlightOfferSummary> =
        (0 until offers.length()).mapNotNull { index ->
            parseOffer(offers.optJSONObject(index))
        }.sortedBy { it.price }

    private fun parseOffer(offer: JSONObject?): FlightOfferSummary? {
        if (offer == null) return null
        val price = offer.optString("total_amount").toDoubleOrNull() ?: return null
        val currency = CurrencyCodes.display(offer.optString("total_currency", "EUR"))
        val slices = offer.optJSONArray("slices") ?: return null
        if (slices.length() == 0) return null

        val outbound = parseSlice(slices.getJSONObject(0)) ?: return null
        val inbound = if (slices.length() > 1) parseSlice(slices.getJSONObject(1)) else null

        return FlightOfferSummary(
            id = offer.optString("id"),
            airline = outbound.airline,
            price = price,
            currency = currency,
            origin = outbound.origin,
            destination = outbound.destination,
            departLabel = outbound.departLabel,
            arriveLabel = outbound.arriveLabel,
            durationLabel = outbound.durationLabel,
            stops = outbound.stops,
            returnOrigin = inbound?.origin,
            returnDestination = inbound?.destination,
            returnDepartLabel = inbound?.departLabel,
            returnArriveLabel = inbound?.arriveLabel
        )
    }

    private fun parseSlice(slice: JSONObject): SliceSummary? {
        val segments = slice.optJSONArray("segments") ?: return null
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
        val durationLabel = formatIsoDuration(slice.optString("duration"))
            .ifBlank { formatDuration(departAt, arriveAt) }

        return SliceSummary(
            airline = airline,
            origin = origin,
            destination = destination,
            departLabel = formatTime(departAt),
            arriveLabel = formatTime(arriveAt),
            durationLabel = durationLabel,
            stops = stops
        )
    }

    private fun slice(origin: String, destination: String, date: java.time.LocalDate): JSONObject =
        JSONObject()
            .put("origin", origin.uppercase(Locale.ENGLISH))
            .put("destination", destination.uppercase(Locale.ENGLISH))
            .put("departure_date", date.toString())

    private fun parseDateTime(value: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME) }.getOrNull()

    private fun formatTime(value: LocalDateTime?): String =
        value?.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)) ?: "—"

    private fun formatDuration(start: LocalDateTime?, end: LocalDateTime?): String {
        if (start == null || end == null) return ""
        val minutes = Duration.between(start, end).toMinutes().coerceAtLeast(0)
        return formatMinutes(minutes)
    }

    private fun formatIsoDuration(raw: String): String {
        if (raw.isBlank()) return ""
        return runCatching {
            val duration = Duration.parse(raw)
            formatMinutes(duration.toMinutes().coerceAtLeast(0))
        }.getOrDefault("")
    }

    private fun formatMinutes(minutes: Long): String {
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

    private data class SliceSummary(
        val airline: String,
        val origin: String,
        val destination: String,
        val departLabel: String,
        val arriveLabel: String,
        val durationLabel: String,
        val stops: Int
    )

    private companion object {
        const val BASE = "https://api.duffel.com"
        const val API_VERSION = "v2"
        val JSON = "application/json".toMediaType()
    }
}

class TravelSearchException(message: String) : Exception(message)
