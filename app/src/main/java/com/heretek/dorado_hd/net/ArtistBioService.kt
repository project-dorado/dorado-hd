package com.heretek.dorado_hd.net

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Artist biography for the artist page's `bio` pivot (canon §4 — TechCrunch
 * 2009: "the artist's bio, pictures from shows and such, and related
 * artists"). The device pulled editorial copy from zune.net; we resolve a
 * plain-language extract from Wikipedia's REST summary API keyed by artist
 * name, cached on disk for a week like the artist photography.
 */
class ArtistBioService(private val context: Context) {
    private val memory = mutableMapOf<String, String>()

    suspend fun bioFor(artist: String): String? = withContext(Dispatchers.IO) {
        val key = artist.trim().lowercase()
        memory[key]?.let { return@withContext it }

        val dir = File(context.filesDir, "artist_bios").apply { mkdirs() }
        val file = File(dir, key.replace(Regex("[^a-z0-9]"), "_") + ".txt")
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < SEVEN_DAYS) {
            file.readText().takeIf { it.isNotBlank() }?.let {
                memory[key] = it
                return@withContext it
            }
        }

        try {
            val url = "https://en.wikipedia.org/api/rest_v1/page/summary/" +
                java.net.URLEncoder.encode(artist, "UTF-8").replace("+", "%20")
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            try {
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", "application/json")
                if (connection.responseCode !in 200..299) return@withContext null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val extract = Regex("\"extract\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                    .find(body)?.groupValues?.get(1)
                    ?.replace(Regex("\\\\n"), "\n")
                    ?.replace("\\\"", "\"")
                    ?.replace("\\\\", "\\")
                    ?.takeIf { it.length > 80 }
                    ?: return@withContext null
                file.writeText(extract)
                memory[key] = extract
                extract
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val SEVEN_DAYS = 7L * 24 * 60 * 60 * 1000
        private const val USER_AGENT = "DoradoHD/0.1 (android; sister-app to dorado)"
    }
}
