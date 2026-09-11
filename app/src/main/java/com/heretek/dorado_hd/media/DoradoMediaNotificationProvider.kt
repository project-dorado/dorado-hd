package com.heretek.dorado_hd.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.common.Player
import androidx.media3.session.MediaNotification.ActionFactory
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.MoreExecutors
import com.heretek.dorado_hd.R
import java.util.concurrent.ConcurrentHashMap

/**
 * Lock-screen/notification surface (M10). A dedicated low-importance channel
 * with public visibility, compact transport actions taken from the session's
 * command layout, and album art loaded through the session's bitmap loader
 * (artwork URI from [MediaMetadata]; decoded asynchronously, then the
 * notification is re-posted through the provider callback).
 */
@OptIn(UnstableApi::class)
class DoradoMediaNotificationProvider(private val context: Context) : MediaNotification.Provider {

    private val artworkCache = ConcurrentHashMap<String, Bitmap>()
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val player = mediaSession.player
        val metadata = player.mediaMetadata
        val actions = customLayout.take(MAX_ACTIONS).map { button ->
            actionFactory.createCustomActionFromCustomCommandButton(mediaSession, button)
        }

        val style = MediaStyleNotificationHelper.MediaStyle(mediaSession)
        if (actions.isNotEmpty()) {
            style.setShowActionsInCompactView(*actions.indices.toList().toIntArray())
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(metadata.title ?: context.getString(R.string.app_name))
            .setContentText(metadata.artist ?: metadata.albumTitle ?: "")
            .apply { mediaSession.sessionActivity?.let { setContentIntent(it) } }
            .setDeleteIntent(actionFactory.createNotificationDismissalIntent(mediaSession))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setStyle(style)
        actions.forEach(builder::addAction)

        artwork(mediaSession, metadata, customLayout, actionFactory, onNotificationChangedCallback)
            ?.let(builder::setLargeIcon)

        return MediaNotification(NOTIFICATION_ID, builder.build())
    }

    /**
     * Album art from embedded bytes when present, else asynchronously from the
     * artwork URI; the notification is re-posted once the bitmap arrives.
     */
    private fun artwork(
        mediaSession: MediaSession,
        metadata: MediaMetadata,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): Bitmap? {
        metadata.artworkData?.let { data ->
            runCatching { BitmapFactory.decodeByteArray(data, 0, data.size) }.getOrNull()?.let { return it }
        }
        val uri = metadata.artworkUri?.toString() ?: return null
        artworkCache[uri]?.let { return it }
        if (loading.add(uri)) {
            try {
                val future = mediaSession.bitmapLoader.loadBitmapFromMetadata(metadata)
                if (future == null) {
                    loading.remove(uri)
                    return null
                }
                future.addListener(
                    {
                        try {
                            val bitmap = future.get()
                            if (bitmap != null) {
                                artworkCache[uri] = bitmap
                                mainHandler.post {
                                    onNotificationChangedCallback.onNotificationChanged(
                                        createNotification(
                                            mediaSession,
                                            customLayout,
                                            actionFactory,
                                            onNotificationChangedCallback,
                                        ),
                                    )
                                }
                            }
                        } catch (_: Exception) {
                            // Artwork is optional; keep the artwork-less notification.
                        } finally {
                            loading.remove(uri)
                        }
                    },
                    MoreExecutors.directExecutor(),
                )
            } catch (_: Exception) {
                loading.remove(uri)
            }
        }
        return null
    }

    override fun handleCustomCommand(
        mediaSession: MediaSession,
        action: String,
        extras: android.os.Bundle,
    ): Boolean = false

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        MediaNotification.Provider.NotificationChannelInfo(CHANNEL_ID, CHANNEL_NAME)

    companion object {
        const val CHANNEL_ID = "dorado_now_playing"
        const val CHANNEL_NAME = "now playing"
        const val NOTIFICATION_ID = 0x5A17
        private const val MAX_ACTIONS = 3
    }
}
