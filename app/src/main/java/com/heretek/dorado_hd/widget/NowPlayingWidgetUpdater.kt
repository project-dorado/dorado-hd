package com.heretek.dorado_hd.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll

/**
 * Pushes a playback snapshot into every placed Now Playing widget and asks
 * Glance to re-render. Safe to call when no widget is on the home screen.
 */
object NowPlayingWidgetUpdater {
    suspend fun update(context: Context, state: NowPlayingWidgetState) {
        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(NowPlayingWidget::class.java)
        if (ids.isEmpty()) return

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
            }
        }

        NowPlayingWidget().updateAll(context)
    }
}
