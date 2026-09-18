package com.wonder.provider.data.travel

import com.wonder.provider.model.StayOfferSummary
import com.wonder.provider.model.StaySearchQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/** Approximate hotel prices via [Duffel Stays](https://duffel.com/docs/guides/searching-for-stays). */
class DuffelStaysClient(private val accessToken: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: StaySearchQuery): List<StayOfferSummary> = withContext(Dispatchers.IO) {
        require(accessToken.isNotBlank()) { "Duffel access token not configured." }
        require(query.checkOut.isAfter(query.checkIn)) { "Check-out must be after check-in." }

        val guests = JSONArray().apply {
            repeat(query.adults.coerceIn(1, 8)) { put(JSONObject().put("type", "adult")) }
        }
        val body = JSONObject()
            .put(
                "data",
                JSONObject()
                    .put(
                        "location",
                        JSONObject()
                            .put("radius", query.radiusKm.coerceIn(1, 50))
                            .put(
                                "geographic_coordinates",
                                JSONObject()
                                    .put("latitude", query.latitude)
                                    .put("longitude", query.longitude)
                            )
                    )
                    .put("check_in_date", query.checkIn.toString())
                    .put("check_out_date", query.checkOut.toString())
                    .put("guests", guests)
                    .put("rooms", query.rooms.coerceIn(1, 4))
            )
            .toString()

        val request = Request.Builder()
            .url("$BASE/stays/search")
            .header("Authorization", "Bearer $accessToken")
            .header("Duffel-Version", API_VERSION)
            .header("Accept", "application/json")
            .header("Accept-Encoding", "gzip")
            .post(body.toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw TravelSearchException(parseDuffelError(raw, response.code))
            }
            val results = JSONObject(raw).optJSONObject("data")?.optJSONArray("results")
                ?: JSONObject(raw).optJSONArray("data")
                ?: JSONArray()
            val nights = ChronoUnit.DAYS.between(query.checkIn, query.checkOut).toInt().coerceAtLeast(1)
            (0 until results.length()).mapNotNull { index ->
                parseResult(results.optJSONObject(index), nights)
            }.sortedBy { it.totalPrice }.take(8)
        }
    }

    private fun parseResult(result: JSONObject?, nights: Int): StayOfferSummary? {
        if (result == null) return null
        val amount = result.optString("cheapest_rate_total_amount")
            .ifBlank { result.optString("cheapest_rate_base_amount") }
            .toDoubleOrNull()
            ?: return null
        val currency = CurrencyCodes.display(
            result.optString("cheapest_rate_currency")
                .ifBlank { result.optString("cheapest_rate_base_currency", "EUR") }
        )
        val accommodation = result.optJSONObject("accommodation") ?: JSONObject()
        val name = accommodation.optString("name").ifBlank { "Stay" }
        val reviewScore = accommodation.optDouble("review_score", Double.NaN)
            .takeIf { !it.isNaN() && it > 0 }
        val starRating = accommodation.optInt("rating", 0).takeIf { it > 0 }?.toDouble()
        val rating = reviewScore ?: starRating
        val location = accommodation.optJSONObject("location")
        val neighbourhood = location?.optJSONObject("address")?.optString("city_name")
            ?.ifBlank { location.optString("neighborhood_name") }
            .orEmpty()
            .ifBlank { location?.optString("city_name").orEmpty() }

        return StayOfferSummary(
            id = result.optString("id").ifBlank { accommodation.optString("id") },
            name = name,
            totalPrice = amount,
            currency = currency,
            nights = nights,
            rating = rating,
            neighbourhood = neighbourhood.orEmpty()
        )
    }

    private fun parseDuffelError(body: String, code: Int): String {
        val message = runCatching {
            JSONObject(body).optJSONArray("errors")
                ?.optJSONObject(0)
                ?.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return message ?: "Stay search failed (HTTP $code)."
    }

    private companion object {
        const val BASE = "https://api.duffel.com"
        const val API_VERSION = "v2"
        val JSON = "application/json".toMediaType()
    }
}
