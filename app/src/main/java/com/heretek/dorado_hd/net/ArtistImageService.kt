package com.heretek.dorado_hd.net

import android.content.Context
import com.heretek.dorado_hd.data.db.ArtistImageEntity
import com.heretek.dorado_hd.data.db.DoradoDatabase
import com.heretek.dorado_hd.data.repo.SettingsRepository
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Artist background photography, replicating the Zune HD's signature Now
 * Playing wallpaper. The original device fetched
 * `catalog.zune.net/v3.0/{locale}/music/artist/{mbid}/deviceBackgroundImage`
 * over USB-PPP; community recreations (ZuneArtistImages, zuneupdate.com)
 * keep that catalog alive. We keep the same concept user-configurable:
 * a URL template with `{mbid}` is resolved after a MusicBrainz lookup.
 */
class ArtistImageService(
    private val context: Context,
    private val db: DoradoDatabase,
    private val settings: SettingsRepository,
) {
    private val mbidCache = mutableMapOf<String, String>()

    suspend fun backgroundFor(artist: String): File? = withContext(Dispatchers.IO) {
        val config = currentTemplate() ?: return@withContext null
        val key = artist.trim().lowercase()
        val dir = File(context.filesDir, "artist_images").apply { mkdirs() }
        val cached = db.artistImageDao().get(key)
        if (cached != null) {
            val file = File(cached.imagePath)
            if (file.exists() && System.currentTimeMillis() - cached.fetchedAt < SEVEN_DAYS) {
                return@withContext file
            }
        }

        return@withContext try {
            val mbid = lookupMbid(artist) ?: return@withContext null
            val url = config.replace("{mbid}", mbid)
            val file = File(dir, mbid + ".img")
            download(url, file) ?: return@withContext null
            db.artistImageDao().put(
                ArtistImageEntity(artistKey = key, imagePath = file.absolutePath, fetchedAt = System.currentTimeMillis()),
            )
            file
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun currentTemplate(): String? {
        val s = settingsSnapshot ?: return null
        if (!s.artistImagesEnabled) return null
        return s.artistImageTemplate.takeIf { it.isNotBlank() }
    }

    @Volatile
    var settingsSnapshot: com.heretek.dorado_hd.data.repo.DoradoSettings? = null

    private suspend fun lookupMbid(artist: String): String? {
        mbidCache[artist.lowercase()]?.let { return it }
        val url = "https://musicbrainz.org/ws/2/artist/?query=artist:%22" +
            java.net.URLEncoder.encode(artist, "UTF-8") + "%22&fmt=json&limit=1"
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val id = Regex("\"id\"\\s*:\\s*\"([0-9a-f-]{36})\"").find(body)?.groupValues?.get(1)
            if (id != null) mbidCache[artist.lowercase()] = id
            return id
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: String, target: File): File? {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        try {
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return if (target.length() > 1024) target else null
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val SEVEN_DAYS = 7L * 24 * 60 * 60 * 1000
        private const val USER_AGENT = "DoradoHD/0.1 (android; sister-app to dorado)"
    }
}
