package com.heretek.dorado_hd.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pushes a playback snapshot into every placed Now Playing widget and asks
 * Glance to re-render. Safe to call when no widget is on the home screen.
 */
object NowPlayingWidgetUpdater {
    suspend fun update(context: Context, state: NowPlayingWidgetState) {
        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(NowPlayingWidget::class.java)
        if (ids.isEmpty()) return

        // Glance 1.2 has no URI-based ImageProvider, so decode a small
        // thumbnail here (off the main thread) and store the bytes.
        val artBytes = state.artUri?.let { uri ->
            withContext(Dispatchers.IO) { decodeArtThumbnail(context, uri) }
        }

        ids.forEach { id ->
            updateAppWidgetState(context, id) { prefs ->
                prefs[NowPlayingWidgetKeys.Title] = state.title
                prefs[NowPlayingWidgetKeys.Artist] = state.artist
                prefs[NowPlayingWidgetKeys.Album] = state.album
                prefs[NowPlayingWidgetKeys.Playing] = state.isPlaying
                if (state.artUri != null) {
                    prefs[NowPlayingWidgetKeys.Art] = state.artUri
                } else {
                    prefs.remove(NowPlayingWidgetKeys.Art)
                }
                if (artBytes != null) {
                    prefs[NowPlayingWidgetKeys.ArtBytes] = artBytes
                } else {
                    prefs.remove(NowPlayingWidgetKeys.ArtBytes)
                }
            }
        }

        NowPlayingWidget().updateAll(context)
    }

    /** Downscaled PNG bytes for the widget's 44dp art slot; null on any failure. */
    private fun decodeArtThumbnail(context: Context, uri: String, maxPx: Int = 128): ByteArray? = try {
        val parsed = android.net.Uri.parse(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(parsed)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (bounds.outWidth / sample > maxPx || bounds.outHeight / sample > maxPx) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        }
    } catch (_: Exception) {
        null
    }
}
