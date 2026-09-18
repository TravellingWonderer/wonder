package com.wonder.provider.data.maps

import android.content.Context
import android.net.Uri
import com.wonder.provider.model.TravelProfileSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class TravelProfileRepository(
    context: Context,
    private val dao: MapsHistoryDao
) {

    private val appContext = context.applicationContext

    @Volatile
    private var cachedAiContext: String = ""

    val profile: Flow<TravelProfileSummary?> = dao.observeProfile().map { entity ->
        entity?.let { TravelProfileSummaryCodec.decode(it.summaryJson) }
    }

    fun aiContextSnapshot(): String = cachedAiContext

    suspend fun refreshAiContextCache() {
        cachedAiContext = buildAiContext()
    }

    suspend fun getSummary(): TravelProfileSummary? = withContext(Dispatchers.IO) {
        dao.profile()?.let { TravelProfileSummaryCodec.decode(it.summaryJson) }
    }

    suspend fun buildAiContext(): String = withContext(Dispatchers.IO) {
        val summary = getSummary() ?: return@withContext ""
        if (!summary.hasData) return@withContext ""
        buildString {
            appendLine("TRAVELLER MAPS HISTORY (from their Google Maps data — personalise suggestions)")
            summary.googleDisplayName?.let { appendLine("Traveller: $it") }
            summary.googleAccountEmail?.let { appendLine("Google account: $it") }
            appendLine("Recorded visits: ${summary.totalVisits} across ${summary.cityCount} cities/areas")
            if (summary.topCities.isNotEmpty()) {
                appendLine("Places they know well: ${summary.topCities.joinToString(", ")}")
            }
            if (summary.topCategories.isNotEmpty()) {
                appendLine("They often go to: ${summary.topCategories.joinToString(", ")}")
            }
            if (summary.frequentActivities.isNotEmpty()) {
                appendLine("Typical activities: ${summary.frequentActivities.joinToString(", ")}")
            }
            if (summary.recentPlaceNames.isNotEmpty()) {
                appendLine(
                    "Recent spots: ${summary.recentPlaceNames.take(8).joinToString(", ")}"
                )
            }
            appendLine(
                "Use this to suggest NEW places that fit their tastes, avoid obvious repeats " +
                    "unless they ask, and lean into categories they visit often."
            )
        }.trim()
    }

    suspend fun importFromUri(uri: Uri, source: String = "import"): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { stream ->
                importStream(stream, source)
            } ?: error("Could not read file")
        }
    }

    suspend fun importStream(input: java.io.InputStream, source: String): Int {
        val parsed = TimelineParser.parseArchive(input, source)
        if (parsed.isEmpty()) error("No visits found in file — use Timeline.json or a Takeout archive")
        persistVisits(parsed, sourceLabel = source, syncedAt = null)
        refreshAiContextCache()
        return parsed.size
    }

    suspend fun persistVisits(
        parsed: List<ParsedVisit>,
        sourceLabel: String,
        email: String? = null,
        displayName: String? = null,
        syncedAt: Long? = null
    ) {
        val existing = dao.profile()
        val entities = parsed.map { it.toEntity() }
        val summary = buildSummary(
            email = email ?: existing?.googleAccountEmail,
            displayName = displayName ?: existing?.googleDisplayName,
            visits = entities,
            syncedAt = syncedAt
        )
        dao.replaceVisits(
            visits = entities,
            profile = MapsProfileEntity(
                googleAccountEmail = summary.googleAccountEmail,
                googleDisplayName = summary.googleDisplayName,
                lastImportedAtEpochMillis = System.currentTimeMillis(),
                lastSyncedAtEpochMillis = syncedAt,
                totalVisits = summary.totalVisits,
                summaryJson = TravelProfileSummaryCodec.encode(summary)
            )
        )
        refreshAiContextCache()
    }

    suspend fun updateGoogleAccount(email: String?, displayName: String?) = withContext(Dispatchers.IO) {
        val existing = dao.profile() ?: MapsProfileEntity(
            googleAccountEmail = email,
            googleDisplayName = displayName,
            lastImportedAtEpochMillis = 0L,
            lastSyncedAtEpochMillis = null,
            totalVisits = 0,
            summaryJson = TravelProfileSummaryCodec.encode(
                TravelProfileSummary(
                    googleAccountEmail = email,
                    googleDisplayName = displayName,
                    totalVisits = 0,
                    cityCount = 0,
                    topCities = emptyList(),
                    topCategories = emptyList(),
                    recentPlaceNames = emptyList(),
                    frequentActivities = emptyList(),
                    lastImportedAtEpochMillis = 0L
                )
            )
        )
        val summary = TravelProfileSummaryCodec.decode(existing.summaryJson).copy(
            googleAccountEmail = email,
            googleDisplayName = displayName
        )
        dao.upsertProfile(
            existing.copy(
                googleAccountEmail = email,
                googleDisplayName = displayName,
                summaryJson = TravelProfileSummaryCodec.encode(summary)
            )
        )
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearVisits()
        dao.clearProfile()
        refreshAiContextCache()
    }

    private fun buildSummary(
        email: String?,
        displayName: String?,
        visits: List<MapsVisitEntity>,
        syncedAt: Long?
    ): TravelProfileSummary {
        val topCities = visits
            .map { it.city.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(8)
            .map { (city, count) -> "$city ($count)" }

        val topCategories = visits
            .map { it.category.trim() }
            .filter { it.isNotBlank() && !it.equals("Location", ignoreCase = true) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(6)
            .map { it.key }

        val recent = visits
            .sortedByDescending { it.startTimeEpochMillis }
            .map { it.placeName.trim() }
            .filter { it.isNotBlank() && !it.equals("Location ping", ignoreCase = true) }
            .distinct()
            .take(12)

        val cityCount = visits.map { it.city.trim() }.filter { it.isNotBlank() }.distinct().size

        val activities = topCategories.take(5)

        return TravelProfileSummary(
            googleAccountEmail = email,
            googleDisplayName = displayName,
            totalVisits = visits.size,
            cityCount = cityCount,
            topCities = topCities,
            topCategories = topCategories,
            recentPlaceNames = recent,
            frequentActivities = activities,
            lastImportedAtEpochMillis = System.currentTimeMillis()
        )
    }

    private fun ParsedVisit.toEntity() = MapsVisitEntity(
        id = id,
        placeId = placeId,
        placeName = placeName,
        address = address,
        city = city,
        country = country,
        latitude = latitude,
        longitude = longitude,
        category = category,
        visitedAt = visitedAt,
        startTimeEpochMillis = startTimeEpochMillis,
        endTimeEpochMillis = endTimeEpochMillis,
        durationMinutes = durationMinutes,
        source = source
    )
}

private object TravelProfileSummaryCodec {
    fun encode(summary: TravelProfileSummary): String = JSONObject().apply {
        put("email", summary.googleAccountEmail.orEmpty())
        put("name", summary.googleDisplayName.orEmpty())
        put("totalVisits", summary.totalVisits)
        put("cityCount", summary.cityCount)
        put("topCities", JSONArray(summary.topCities))
        put("topCategories", JSONArray(summary.topCategories))
        put("recentPlaceNames", JSONArray(summary.recentPlaceNames))
        put("frequentActivities", JSONArray(summary.frequentActivities))
        put("lastImportedAt", summary.lastImportedAtEpochMillis)
    }.toString()

    fun decode(json: String): TravelProfileSummary {
        val root = JSONObject(json)
        return TravelProfileSummary(
            googleAccountEmail = root.optString("email").ifBlank { null },
            googleDisplayName = root.optString("name").ifBlank { null },
            totalVisits = root.optInt("totalVisits"),
            cityCount = root.optInt("cityCount"),
            topCities = root.optJSONArray("topCities").toStringList(),
            topCategories = root.optJSONArray("topCategories").toStringList(),
            recentPlaceNames = root.optJSONArray("recentPlaceNames").toStringList(),
            frequentActivities = root.optJSONArray("frequentActivities").toStringList(),
            lastImportedAtEpochMillis = root.optLong("lastImportedAt")
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }
}
