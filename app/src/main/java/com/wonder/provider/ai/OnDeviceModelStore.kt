package com.wonder.provider.ai

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

/** Links and package ids for Google AI Edge Gallery — where Gemma bundles are downloaded. */
object EdgeGalleryLinks {
    const val PLAY_STORE =
        "https://play.google.com/store/apps/details?id=com.google.ai.edge.gallery"
    const val GITHUB = "https://github.com/google-ai-edge/gallery"
    const val WIKI_IMPORT =
        "https://github.com/google-ai-edge/gallery/wiki/6.-Importing-Local-Models-(optional)"
    const val HF_LITERT_COMMUNITY = "https://huggingface.co/litert-community"
    const val HF_GEMMA3_1B =
        "https://huggingface.co/litert-community/gemma-3-1b-it-litert-lm"
    const val PACKAGE_ID = "com.google.ai.edge.gallery"
    const val LEGACY_PACKAGE_ID = "com.google.aiedge.gallery"
}

/**
 * Resolves and validates `.litertlm` model files the traveller attached after downloading
 * through Google AI Edge Gallery or Hugging Face.
 */
class OnDeviceModelStore(private val context: Context) {

    fun isGalleryInstalled(): Boolean {
        val pm = context.packageManager
        return listOf(EdgeGalleryLinks.PACKAGE_ID, EdgeGalleryLinks.LEGACY_PACKAGE_ID).any { id ->
            runCatching {
                pm.getPackageInfo(id, 0)
                true
            }.getOrDefault(false)
        }
    }

    /** Whether [uriString] points at a readable `.litertlm` file. */
    fun validateModelUri(uriString: String): Boolean {
        if (uriString.isBlank()) return false
        localModelFile(uriString)?.let { return it.isFile && it.length() > 0L }
        return runCatching {
            val uri = Uri.parse(uriString)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize > 0L } == true
        }.getOrDefault(false)
    }

    fun displayName(uriString: String): String {
        if (uriString.isBlank()) return ""
        return runCatching {
            val uri = Uri.parse(uriString)
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index).orEmpty()
                }
            }
            uri.lastPathSegment.orEmpty()
        }.getOrDefault(uriString)
    }

    /**
     * Copies the attached model into app storage so LiteRT-LM can open it with a plain path.
     * Large files — this runs on a background thread.
     */
    fun materializeModelPath(uriString: String): String? {
        if (!validateModelUri(uriString)) return null
        localModelFile(uriString)?.let { local ->
            val dest = File(context.filesDir, "models/gemma.litertlm")
            dest.parentFile?.mkdirs()
            if (dest.exists() && dest.length() == local.length()) return dest.absolutePath
            return runCatching {
                local.inputStream().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                if (dest.length() > 0) dest.absolutePath else null
            }.getOrNull()
        }

        val dest = File(context.filesDir, "models/gemma.litertlm")
        dest.parentFile?.mkdirs()
        if (dest.exists() && dest.length() > 0) {
            // Already copied — skip unless the source changed (cheap heuristic: size).
            val sourceSize = runCatching {
                context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")?.use {
                    it.statSize
                }
            }.getOrNull()
            if (sourceSize != null && dest.length() == sourceSize) return dest.absolutePath
        }

        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            if (dest.length() > 0) dest.absolutePath else null
        }.getOrNull()
    }

    /**
     * Copies a `.litertlm` bundle from Downloads into app storage and returns its URI string.
     * Used for emulator testing after `adb push … /sdcard/Download/`.
     */
    fun attachFromDownloads(fileName: String): String? {
        val dest = bundledModelFile()
        dest.parentFile?.mkdirs()

        if (fileName == INTERNAL_ATTACH_TOKEN) {
            return dest.takeIf { it.isFile && it.length() > 0L }?.let { "file://${it.absolutePath}" }
        }

        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val source = listOf(
            File(downloads, fileName),
            File(context.getExternalFilesDir(null), fileName)
        ).firstOrNull { it.isFile && it.name.endsWith(".litertlm", ignoreCase = true) } ?: return null

        return runCatching {
            source.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            if (dest.length() > 0) "file://${dest.absolutePath}" else null
        }.getOrNull()
    }

    fun bundledModelFile(): File = File(context.filesDir, "models/gemma.litertlm")

    private fun localModelFile(uriString: String): File? {
        if (!uriString.startsWith("file://")) return null
        val path = Uri.parse(uriString).path ?: return null
        return File(path).takeIf { it.exists() }
    }

    /**
     * Looks in Downloads for `.litertlm` bundles — the same files Edge Gallery imports —
     * so Wonder can suggest one the traveller may already have.
     */
    fun discoverDownloadedModels(): List<DiscoveredModel> {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloads.isDirectory) return emptyList()

        return downloads.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(".litertlm", ignoreCase = true) }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                DiscoveredModel(
                    name = file.name,
                    sizeMb = file.length() / (1024 * 1024)
                )
            }
    }
}

data class DiscoveredModel(
    val name: String,
    val sizeMb: Long
)

/** Passed to [OnDeviceModelStore.attachFromDownloads] after adb copies the bundle into app storage. */
const val INTERNAL_ATTACH_TOKEN = "@internal"
