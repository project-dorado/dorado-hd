package com.heretek.dorado_hd.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.heretek.dorado_hd.design.DoradoAccent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "dorado_hd_settings")

data class DoradoSettings(
    val accent: DoradoAccent = DoradoAccent.PINK,
    val deviceMode: Boolean = false,
    val artistImagesEnabled: Boolean = true,
    val artistImageTemplate: String = "",
    val libraryScanned: Boolean = false,
    /** Opt-in: auto-rescan whenever MediaStore announces a change. */
    val watchMediaStore: Boolean = false,
    /** Persistent URI of the user-imported SAF tree (or empty). */
    val importedTreeUri: String = "",
    /** Device Link sync rules — mirrors the desktop AppSettings rule labels. */
    val musicSyncRule: String = "All Music (Automatic Sync)",
    val podcastSyncRule: String = "3 Newest Episodes",
    val videoSyncRule: String = "All Videos & Pictures",
    val picturesSyncRule: String = "Newest 25 Items",
    /** M9.4 — offline Last.fm scrobbling (opt-in; credentials user-supplied). */
    val scrobbleEnabled: Boolean = false,
    val lastFmApiKey: String = "",
    val lastFmApiSecret: String = "",
    val lastFmSessionKey: String = "",
    /** Dorado Cloud — community services (catalog/directory/updates/social). */
    val cloudEnabled: Boolean = false,
    val cloudBaseUrl: String = "",
    val cloudAccessToken: String = "",
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val ACCENT = intPreferencesKey("accent")
        val DEVICE_MODE = booleanPreferencesKey("device_mode")
        val ARTIST_IMAGES = booleanPreferencesKey("artist_images")
        val ARTIST_IMAGE_TEMPLATE = stringPreferencesKey("artist_image_template")
        val LIBRARY_SCANNED = booleanPreferencesKey("library_scanned")
        val WATCH_MEDIA_STORE = booleanPreferencesKey("watch_media_store")
        val IMPORTED_TREE_URI = stringPreferencesKey("imported_tree_uri")
        val MUSIC_SYNC_RULE = stringPreferencesKey("music_sync_rule")
        val PODCAST_SYNC_RULE = stringPreferencesKey("podcast_sync_rule")
        val VIDEO_SYNC_RULE = stringPreferencesKey("video_sync_rule")
        val PICTURES_SYNC_RULE = stringPreferencesKey("pictures_sync_rule")
        val SCROBBLE_ENABLED = booleanPreferencesKey("scrobble_enabled")
        val LASTFM_API_KEY = stringPreferencesKey("lastfm_api_key")
        val LASTFM_API_SECRET = stringPreferencesKey("lastfm_api_secret")
        val LASTFM_SESSION_KEY = stringPreferencesKey("lastfm_session_key")
        val CLOUD_ENABLED = booleanPreferencesKey("cloud_enabled")
        val CLOUD_BASE_URL = stringPreferencesKey("cloud_base_url")
        val CLOUD_ACCESS_TOKEN = stringPreferencesKey("cloud_access_token")
    }

    val settings: Flow<DoradoSettings> = context.dataStore.data.map { p ->
        DoradoSettings(
            accent = DoradoAccent.entries.firstOrNull { it.id == p[Keys.ACCENT] } ?: DoradoAccent.PINK,
            deviceMode = p[Keys.DEVICE_MODE] ?: false,
            artistImagesEnabled = p[Keys.ARTIST_IMAGES] ?: true,
            artistImageTemplate = p[Keys.ARTIST_IMAGE_TEMPLATE] ?: "",
            libraryScanned = p[Keys.LIBRARY_SCANNED] ?: false,
            watchMediaStore = p[Keys.WATCH_MEDIA_STORE] ?: false,
            importedTreeUri = p[Keys.IMPORTED_TREE_URI] ?: "",
            musicSyncRule = p[Keys.MUSIC_SYNC_RULE] ?: "All Music (Automatic Sync)",
            podcastSyncRule = p[Keys.PODCAST_SYNC_RULE] ?: "3 Newest Episodes",
            videoSyncRule = p[Keys.VIDEO_SYNC_RULE] ?: "All Videos & Pictures",
            picturesSyncRule = p[Keys.PICTURES_SYNC_RULE] ?: "Newest 25 Items",
            scrobbleEnabled = p[Keys.SCROBBLE_ENABLED] ?: false,
            lastFmApiKey = p[Keys.LASTFM_API_KEY] ?: "",
            lastFmApiSecret = p[Keys.LASTFM_API_SECRET] ?: "",
            lastFmSessionKey = p[Keys.LASTFM_SESSION_KEY] ?: "",
            cloudEnabled = p[Keys.CLOUD_ENABLED] ?: false,
            cloudBaseUrl = p[Keys.CLOUD_BASE_URL] ?: "",
            cloudAccessToken = p[Keys.CLOUD_ACCESS_TOKEN] ?: "",
        )
    }

    suspend fun setAccent(accent: DoradoAccent) = context.dataStore.edit { it[Keys.ACCENT] = accent.id }
    suspend fun setDeviceMode(enabled: Boolean) = context.dataStore.edit { it[Keys.DEVICE_MODE] = enabled }
    suspend fun setArtistImages(enabled: Boolean) = context.dataStore.edit { it[Keys.ARTIST_IMAGES] = enabled }
    suspend fun setArtistImageTemplate(template: String) = context.dataStore.edit { it[Keys.ARTIST_IMAGE_TEMPLATE] = template }
    suspend fun setLibraryScanned(scanned: Boolean) = context.dataStore.edit { it[Keys.LIBRARY_SCANNED] = scanned }
    suspend fun setWatchMediaStore(enabled: Boolean) = context.dataStore.edit { it[Keys.WATCH_MEDIA_STORE] = enabled }
    suspend fun setImportedTreeUri(uri: String) = context.dataStore.edit { it[Keys.IMPORTED_TREE_URI] = uri }
    suspend fun setMusicSyncRule(rule: String) = context.dataStore.edit { it[Keys.MUSIC_SYNC_RULE] = rule }
    suspend fun setPodcastSyncRule(rule: String) = context.dataStore.edit { it[Keys.PODCAST_SYNC_RULE] = rule }
    suspend fun setMediaSyncRule(rule: String) = context.dataStore.edit {
        it[Keys.VIDEO_SYNC_RULE] = rule
        it[Keys.PICTURES_SYNC_RULE] = rule
    }
    suspend fun setScrobbleEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.SCROBBLE_ENABLED] = enabled }
    suspend fun setLastFmApiKey(value: String) = context.dataStore.edit { it[Keys.LASTFM_API_KEY] = value }
    suspend fun setLastFmApiSecret(value: String) = context.dataStore.edit { it[Keys.LASTFM_API_SECRET] = value }
    suspend fun setLastFmSessionKey(value: String) = context.dataStore.edit { it[Keys.LASTFM_SESSION_KEY] = value }
    suspend fun setCloudEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.CLOUD_ENABLED] = enabled }
    suspend fun setCloudBaseUrl(value: String) = context.dataStore.edit { it[Keys.CLOUD_BASE_URL] = value }
    suspend fun setCloudAccessToken(value: String) = context.dataStore.edit { it[Keys.CLOUD_ACCESS_TOKEN] = value }
}
