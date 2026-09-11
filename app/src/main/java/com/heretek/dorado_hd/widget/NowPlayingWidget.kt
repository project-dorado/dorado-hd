package com.heretek.dorado_hd.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.heretek.dorado_hd.media.DoradoPlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

/** Preference keys backing the widget's Glance state. */
object NowPlayingWidgetKeys {
    val Title = stringPreferencesKey("np_title")
    val Artist = stringPreferencesKey("np_artist")
    val Album = stringPreferencesKey("np_album")
    val Playing = booleanPreferencesKey("np_playing")
    val Art = stringPreferencesKey("np_art")

    /** Decoded 128px album-art thumbnail (Glance 1.2 has no URI provider). */
    val ArtBytes = androidx.datastore.preferences.core.byteArrayPreferencesKey("np_art_bytes")
}

/**
 * Zune-style Now Playing home-screen widget (M10). Renders square, typographic
 * transport controls reading the playback snapshot pushed by
 * [NowPlayingWidgetUpdater]. Widget surfaces render outside Compose, so the
 * design tokens can't be consumed directly; the matte-black canvas, white text
 * and muted grey mirror `DoradoTokens` values to honor the invariant's spirit.
 */
class NowPlayingWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            NowPlayingWidgetContent(
                state = NowPlayingWidgetState(
                    title = prefs[NowPlayingWidgetKeys.Title].orEmpty(),
                    artist = prefs[NowPlayingWidgetKeys.Artist].orEmpty(),
                    album = prefs[NowPlayingWidgetKeys.Album].orEmpty(),
                    isPlaying = prefs[NowPlayingWidgetKeys.Playing] ?: false,
                    artUri = prefs[NowPlayingWidgetKeys.Art],
                ),
                artBytes = prefs[NowPlayingWidgetKeys.ArtBytes],
            )
        }
    }
}

@Composable
private fun NowPlayingWidgetContent(state: NowPlayingWidgetState, artBytes: ByteArray?) {
    val white = ColorProvider(Color(0xFFFFFFFF))
    val muted = ColorProvider(Color(0xFF9AA0A6))
    val artBitmap = artBytes?.let {
        android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF0D0D0F)))
            .padding(10.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            // Album art from the decoded thumbnail pushed with the snapshot.
            if (artBitmap != null) {
                Image(
                    provider = ImageProvider(artBitmap),
                    contentDescription = null,
                    contentScale = androidx.glance.layout.ContentScale.Crop,
                    modifier = GlanceModifier.size(44.dp),
                )
                Spacer(GlanceModifier.width(8.dp))
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = state.title.ifBlank { "dorado hd" },
                    maxLines = 1,
                    style = TextStyle(color = white, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    text = state.artist.ifBlank { if (state.title.isBlank()) "not playing" else "" },
                    maxLines = 1,
                    style = TextStyle(color = muted, fontSize = 12.sp),
                )
                if (state.album.isNotBlank()) {
                    Text(
                        text = state.album,
                        maxLines = 1,
                        style = TextStyle(color = muted, fontSize = 10.sp),
                    )
                }
            }
        }

        Spacer(GlanceModifier.height(6.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            TransportGlyph("\u23EE", actionRunCallback<PreviousTrackAction>())
            Spacer(GlanceModifier.width(18.dp))
            TransportGlyph(
                if (state.isPlaying) "\u23F8" else "\u25B6",
                actionRunCallback<TogglePlaybackAction>(),
            )
            Spacer(GlanceModifier.width(18.dp))
            TransportGlyph("\u23ED", actionRunCallback<NextTrackAction>())
        }
    }
}

@Composable
private fun TransportGlyph(glyph: String, action: Action) {
    Text(
        text = glyph,
        style = TextStyle(color = ColorProvider(Color(0xFFFFFFFF)), fontSize = 22.sp),
        modifier = GlanceModifier.padding(6.dp).clickable(action),
    )
}

/** Drives the shared Media3 session directly from a widget tap. */
sealed class TransportAction : ActionCallback {
    protected abstract fun apply(controller: MediaController)

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // MediaController must be built and used on the main looper; Glance
        // dispatches ActionCallbacks on a background dispatcher.
        withContext(Dispatchers.Main.immediate) {
            val token = SessionToken(context, ComponentName(context, DoradoPlaybackService::class.java))
            val controller = MediaController.Builder(context, token).buildAsync().await()
            try {
                apply(controller)
            } finally {
                controller.release()
            }
        }
    }
}

class TogglePlaybackAction : TransportAction() {
    override fun apply(controller: MediaController) {
        if (controller.isPlaying) controller.pause() else controller.play()
    }
}

class NextTrackAction : TransportAction() {
    override fun apply(controller: MediaController) = controller.seekToNextMediaItem()
}

class PreviousTrackAction : TransportAction() {
    override fun apply(controller: MediaController) = controller.seekToPreviousMediaItem()
}

/** Hosts [NowPlayingWidget]; declared in AndroidManifest as the appwidget receiver. */
class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}
