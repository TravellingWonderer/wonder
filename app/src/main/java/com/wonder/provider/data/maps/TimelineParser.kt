package com.wonder.provider.data.maps

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipInputStream

/** Parses Google Maps Timeline / Takeout location history into visit records. */
object TimelineParser {

    fun parseArchive(input: InputStream, source: String): List<ParsedVisit> {
        val buffered = BufferedInputStream(input)
        buffered.mark(8)
        val header = ByteArray(4)
        val read = buffered.read(header)
        buffered.reset()
        return if (read >= 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()) {
            parseZip(buffered, source)
        } else {
            parseJsonText(buffered.bufferedReader().readText(), source)
        }
    }

    private fun parseZip(input: InputStream, source: String): List<ParsedVisit> {
        val visits = mutableListOf<ParsedVisit>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                    val text = zip.readBytes().decodeToString()
                    visits += parseJsonText(text, source)
                }
                entry = zip.nextEntry
            }
        }
        return dedupe(visits)
    }

    fun parseJsonText(text: String, source: String): List<ParsedVisit> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyList()
        return when {
            trimmed.startsWith("[") -> parsePhoneArray(JSONArray(trimmed), source)
            else -> {
                val root = JSONObject(trimmed)
                when {
                    root.has("semanticSegments") || root.has("rawSignals") ->
                        parsePhoneTimeline(root, source)
                    root.has("timelineObjects") ->
                        parseTimelineObjects(root.getJSONArray("timelineObjects"), source)
                    root.has("locations") ->
                        parseRecordsJson(root, source)
                    else -> emptyList()
                }
            }
        }
    }

    private fun parsePhoneArray(array: JSONArray, source: String): List<ParsedVisit> {
        val visits = mutableListOf<ParsedVisit>()
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            if (entry.has("visit")) {
                parsePhoneVisit(entry, source)?.let { visits += it }
            }
        }
        return dedupe(visits)
    }

    private fun parsePhoneTimeline(root: JSONObject, source: String): List<ParsedVisit> {
        val visits = mutableListOf<ParsedVisit>()
        root.optJSONArray("semanticSegments")?.let { segments ->
            for (index in 0 until segments.length()) {
                val segment = segments.optJSONObject(index) ?: continue
                if (segment.has("visit")) {
                    parsePhoneVisit(segment, source)?.let { visits += it }
                }
            }
        }
        root.optJSONObject("userLocationProfile")
            ?.optJSONArray("frequentPlaces")
            ?.let { frequent ->
                for (index in 0 until frequent.length()) {
                    val place = frequent.optJSONObject(index) ?: continue
                    parseFrequentPlace(place, source)?.let { visits += it }
                }
            }
        return dedupe(visits)
    }

    private fun parsePhoneVisit(segment: JSONObject, source: String): ParsedVisit? {
        val visit = segment.optJSONObject("visit") ?: return null
        val candidate = visit.optJSONObject("topCandidate") ?: return null
        val start = parseInstant(segment.optString("startTime")) ?: return null
        val end = parseInstant(segment.optString("endTime"))
        val (lat, lng) = parseLatLng(candidate.optString("placeLocation"))
        val semanticType = candidate.optString("semanticType").ifBlank { "UNKNOWN" }
        val placeId = candidate.optString("placeId").ifBlank { null }
        val address = candidate.optString("placeAddress").ifBlank {
            candidate.optString("address").ifBlank { "" }
        }
        val name = candidate.optString("placeName").ifBlank {
            address.substringBefore(",").ifBlank { semanticType.label() }
        }
        val (city, country) = parseCityCountry(address, candidate)
        val durationMinutes = durationMinutes(start, end)
        val id = visitId(placeId, start.toEpochMilli(), lat, lng, name)
        return ParsedVisit(
            id = id,
            placeId = placeId,
            placeName = name,
            address = address,
            city = city,
            country = country,
            latitude = lat,
            longitude = lng,
            category = semanticType.label(),
            visitedAt = start.atZone(ZoneId.systemDefault()).toLocalDate(),
            startTimeEpochMillis = start.toEpochMilli(),
            endTimeEpochMillis = end?.toEpochMilli(),
            durationMinutes = durationMinutes,
            source = source
        )
    }

    private fun parseFrequentPlace(place: JSONObject, source: String): ParsedVisit? {
        val name = place.optString("placeName").ifBlank { return null }
        val placeId = place.optString("placeId").ifBlank { null }
        val (lat, lng) = parseLatLng(place.optString("placeLocation"))
        val semanticType = place.optString("semanticType").ifBlank { "FREQUENT" }
        val id = visitId(placeId, 0L, lat, lng, name)
        return ParsedVisit(
            id = id,
            placeId = placeId,
            placeName = name,
            address = "",
            city = "",
            country = "",
            latitude = lat,
            longitude = lng,
            category = semanticType.label(),
            visitedAt = LocalDate.now(),
            startTimeEpochMillis = 0L,
            endTimeEpochMillis = null,
            durationMinutes = 0,
            source = "${source}_frequent"
        )
    }

    private fun parseTimelineObjects(objects: JSONArray, source: String): List<ParsedVisit> {
        val visits = mutableListOf<ParsedVisit>()
        for (index in 0 until objects.length()) {
            val entry = objects.optJSONObject(index) ?: continue
            val placeVisit = entry.optJSONObject("placeVisit") ?: continue
            val location = placeVisit.optJSONObject("location") ?: continue
            val duration = placeVisit.optJSONObject("duration") ?: continue
            val start = parseInstant(duration.optString("startTimestamp")) ?: continue
            val end = parseInstant(duration.optString("endTimestamp"))
            val lat = location.optLong("latitudeE7", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
                ?.let { it / 1e7 }
            val lng = location.optLong("longitudeE7", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
                ?.let { it / 1e7 }
            val name = location.optString("name").ifBlank { location.optString("address").substringBefore(",") }
            val address = location.optString("address")
            val (city, country) = parseCityCountry(address, location)
            val placeId = location.optString("placeId").ifBlank { null }
            val semanticType = placeVisit.optString("semanticType").ifBlank { "VISIT" }
            val id = visitId(placeId, start.toEpochMilli(), lat, lng, name)
            visits += ParsedVisit(
                id = id,
                placeId = placeId,
                placeName = name.ifBlank { "Unknown place" },
                address = address,
                city = city,
                country = country,
                latitude = lat,
                longitude = lng,
                category = semanticType.label(),
                visitedAt = start.atZone(ZoneId.systemDefault()).toLocalDate(),
                startTimeEpochMillis = start.toEpochMilli(),
                endTimeEpochMillis = end?.toEpochMilli(),
                durationMinutes = durationMinutes(start, end),
                source = source
            )
        }
        return visits
    }

    private fun parseRecordsJson(root: JSONObject, source: String): List<ParsedVisit> {
        val locations = root.optJSONArray("locations") ?: return emptyList()
        val visits = mutableListOf<ParsedVisit>()
        for (index in 0 until locations.length()) {
            val point = locations.optJSONObject(index) ?: continue
            val lat = point.optLong("latitudeE7", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let { it / 1e7 }
            val lng = point.optLong("longitudeE7", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let { it / 1e7 }
            val start = parseInstant(point.optString("timestamp")) ?: continue
            val id = visitId(null, start.toEpochMilli(), lat, lng, "Location ping")
            visits += ParsedVisit(
                id = id,
                placeId = null,
                placeName = "Location ping",
                address = "",
                city = "",
                country = "",
                latitude = lat,
                longitude = lng,
                category = "Location",
                visitedAt = start.atZone(ZoneId.systemDefault()).toLocalDate(),
                startTimeEpochMillis = start.toEpochMilli(),
                endTimeEpochMillis = null,
                durationMinutes = 0,
                source = source
            )
        }
        return dedupe(visits)
    }

    private fun parseInstant(value: String): Instant? =
        runCatching { Instant.parse(value) }.getOrNull()

    private fun parseLatLng(raw: String): Pair<Double?, Double?> {
        if (raw.isBlank()) return null to null
        val cleaned = raw.removePrefix("geo:").trim()
        val parts = cleaned.split(",", limit = 2)
        if (parts.size != 2) return null to null
        val lat = parts[0].trim().toDoubleOrNull()
        val lng = parts[1].trim().toDoubleOrNull()
        return lat to lng
    }

    private fun parseCityCountry(address: String, json: JSONObject): Pair<String, String> {
        json.optJSONObject("placeLocation")?.let { location ->
            val city = location.optString("town").ifBlank { location.optString("city") }
            val country = location.optString("country")
            if (city.isNotBlank() || country.isNotBlank()) return city to country
        }
        val parts = address.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> parts[parts.size - 2] to parts.last()
            parts.size == 1 -> parts.first() to ""
            else -> "" to ""
        }
    }

    private fun durationMinutes(start: Instant, end: Instant?): Int {
        if (end == null) return 0
        return ((end.epochSecond - start.epochSecond) / 60).toInt().coerceAtLeast(0)
    }

    private fun visitId(
        placeId: String?,
        startMillis: Long,
        lat: Double?,
        lng: Double?,
        name: String
    ): String {
        val seed = "${placeId.orEmpty()}|$startMillis|$lat|$lng|$name"
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun dedupe(visits: List<ParsedVisit>): List<ParsedVisit> =
        visits.distinctBy { it.id }

    private fun String.label(): String = removePrefix("TYPE_")
        .replace('_', ' ')
        .lowercase()
        .replaceFirstChar { it.titlecase() }
}

data class ParsedVisit(
    val id: String,
    val placeId: String?,
    val placeName: String,
    val address: String,
    val city: String,
    val country: String,
    val latitude: Double?,
    val longitude: Double?,
    val category: String,
    val visitedAt: LocalDate,
    val startTimeEpochMillis: Long,
    val endTimeEpochMillis: Long?,
    val durationMinutes: Int,
    val source: String
)
