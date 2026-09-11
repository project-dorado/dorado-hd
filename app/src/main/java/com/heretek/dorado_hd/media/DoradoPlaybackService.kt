package com.heretek.dorado_hd.media

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.heretek.dorado_hd.MainActivity
import com.heretek.dorado_hd.DoradoApp
import com.heretek.dorado_hd.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.withContext

/**
 * Foreground Media3 session service. Holds the ExoPlayer instance and the
 * MediaSession that powers the system notification / lock screen controls.
 */
class DoradoPlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(DoradoMediaNotificationProvider(this))

        val player = ExoPlayer.Builder(this, TapRenderersFactory(this))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Expose the session id so app-side audio effects (equalizer presets)
        // can attach.
        PlaybackSession.audioSessionId = player.audioSessionId

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        session = MediaSession.Builder(this, player)
            .setBitmapLoader(
                androidx.media3.session.CacheBitmapLoader(androidx.media3.session.SimpleBitmapLoader()),
            )
            .setSessionActivity(sessionActivity)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): com.google.common.util.concurrent.ListenableFuture<MutableList<MediaItem>> =
                    resolveMediaItems(mediaItems)
            })
            .build()
    }

    /**
     * External controllers (lock screen, Wear, Auto) may hand back items with
     * only a mediaId. Resolve them against the library, mirroring the Zune
     * sync model where the device database is the source of truth.
     */
    private fun resolveMediaItems(requested: List<MediaItem>): com.google.common.util.concurrent.ListenableFuture<MutableList<MediaItem>> =
        GlobalScope.future(Dispatchers.IO) {
            val repo = (applicationContext as DoradoApp).graph.library
            requested.map { item ->
                if (item.localConfiguration != null) {
                    item
                } else {
                    val id = item.mediaId.toLongOrNull()
                    val track = id?.let { repo.track(it) }
                    if (track != null) track.toMediaItem()
                    else item.buildUpon().setUri(android.net.Uri.EMPTY).build()
                }
            }.toMutableList()
        }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
            session = null
        }
        super.onDestroy()
    }
}

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(mediaId.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(albumArtUri)
            .build(),
    )
    .build()
