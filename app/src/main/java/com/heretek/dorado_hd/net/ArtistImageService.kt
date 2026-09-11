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
    private val cloud: com.heretek.dorado_hd.cloud.CloudMetadataSource? = null,
) {
    private val mbidCache = mutableMapOf<String, String>()

    suspend fun backgroundFor(artist: String): File? = withContext(Dispatchers.IO) {
        val key = artist.trim().lowercase()
        val dir = File(context.filesDir, "artist_images").apply { mkdirs() }
        val cached = db.artistImageDao().get(key)
        if (cached != null) {
            val file = File(cached.imagePath)
            if (file.exists() && System.currentTimeMillis() - cached.fetchedAt < SEVEN_DAYS) {
                return@withContext file
            }
        }

        // Cloud-first: hydrate from the Dorado Cloud artwork CDN when enabled.
        if (cloud?.isEnabled() == true) {
            try {
                val bytes = cloud.artistImage(artist)
                if (bytes != null && bytes.size > 1024) {
                    val file = File(dir, "cloud_" + key.replace(Regex("[^a-z0-9]"), "_") + ".img")
                    file.writeBytes(bytes)
                    db.artistImageDao().put(
                        ArtistImageEntity(artistKey = key, imagePath = file.absolutePath, fetchedAt = System.currentTimeMillis()),
                    )
                    return@withContext file
                }
            } catch (_: Exception) {
                // fall through to the configured template
            }
        }

        val config = currentTemplate() ?: return@withContext null

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

    /**
     * Multi-photo source for the artist `photos` pivot (M13/B5). Prefers the
     * configured Dorado Cloud artwork module, then Cover Art Archive
     * release-group covers keyed by the MusicBrainz artist id, then the single
     * cached wallpaper — so the pivot renders a grid or the same one photo it
     * always did, never nothing.
     */
    suspend fun photosFor(artist: String): List<ArtistPhoto> = withContext(Dispatchers.IO) {
        val cloudUrls = if (cloud?.isEnabled() == true) {
            runCatching { cloud.artistPhotoUrls(artist) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val coverArtUrls = if (cloudUrls.isEmpty() && photosAllowed()) {
            runCatching { coverArtPhotos(artist) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val wallpaper = if (cloudUrls.isEmpty() && coverArtUrls.isEmpty()) {
            runCatching { backgroundFor(artist) }.getOrNull()?.let { "file://" + it.absolutePath }
        } else {
            null
        }

        ArtistPhotos.select(cloudUrls, coverArtUrls, wallpaper)
    }

    private fun photosAllowed(): Boolean = settingsSnapshot?.artistImagesEnabled != false

    /** Cover Art Archive release-group covers for the artist's MBID. */
    private suspend fun coverArtPhotos(artist: String): List<String> {
        val mbid = lookupMbid(artist) ?: return emptyList()
        val body = fetchText(
            "https://musicbrainz.org/ws/2/release-group?artist=$mbid&fmt=json&limit=25",
        ) ?: return emptyList()
        return ArtistPhotos.coverArtUrls(ArtistPhotos.releaseGroupIds(body))
    }

    private fun fetchText(url: String): String? {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) return null
            return connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return null
        } finally {
            connection.disconnect()
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
