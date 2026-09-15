package com.wonder.provider.auth

import com.wonder.provider.data.maps.ParsedVisit
import com.wonder.provider.data.maps.TimelineParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/** Looks for Google Takeout / Timeline exports in the user's Google Drive. */
class GoogleDriveTimelineFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun fetchTimelineVisits(accessToken: String): List<ParsedVisit> = withContext(Dispatchers.IO) {
        val files = listCandidateFiles(accessToken)
        if (files.isEmpty()) {
            error(
                "No Timeline or Takeout files found in Drive. Export from Google Maps " +
                    "(Timeline → Export) or takeout.google.com, then import the file here."
            )
        }

        val visits = mutableListOf<ParsedVisit>()
        files.take(5).forEach { file ->
            runCatching {
                val bytes = downloadFile(accessToken, file.id)
                ByteArrayInputStream(bytes).use { stream ->
                    visits += TimelineParser.parseArchive(stream, "drive:${file.name}")
                }
            }
        }
        if (visits.isEmpty()) {
            error("Found Drive files but could not read visits from them")
        }
        visits.distinctBy { it.id }
    }

    private fun listCandidateFiles(token: String): List<DriveFile> {
        val query = buildString {
            append("trashed = false and (")
            append("name contains 'Timeline' or ")
            append("name contains 'timeline' or ")
            append("name contains 'takeout' or ")
            append("name contains 'location-history' or ")
            append("name contains 'Location History'")
            append(") and (")
            append("mimeType = 'application/json' or ")
            append("mimeType = 'application/zip' or ")
            append("mimeType = 'application/x-zip-compressed' or ")
            append("mimeType = 'application/octet-stream'")
            append(")")
        }
        val url = "https://www.googleapis.com/drive/v3/files?" +
            "q=${java.net.URLEncoder.encode(query, Charsets.UTF_8.name())}&" +
            "pageSize=20&orderBy=modifiedTime desc&" +
            "fields=files(id,name,mimeType,modifiedTime)"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Drive search failed (${response.code})")
            }
            val body = response.body?.string().orEmpty()
            val files = JSONObject(body).optJSONArray("files") ?: return emptyList()
            return buildList {
                for (index in 0 until files.length()) {
                    val entry = files.getJSONObject(index)
                    add(
                        DriveFile(
                            id = entry.getString("id"),
                            name = entry.getString("name")
                        )
                    )
                }
            }
        }
    }

    private fun downloadFile(token: String, fileId: String): ByteArray {
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Drive download failed (${response.code})")
            return response.body?.bytes() ?: error("Empty Drive file")
        }
    }

    private data class DriveFile(val id: String, val name: String)
}
