package com.heretek.dorado_hd

import android.content.Context
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.google.common.collect.ImmutableList
import com.heretek.dorado_hd.media.DoradoMediaNotificationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestDoradoApp::class)
class NotificationProviderTest {

    private class FakeFactory(private val context: Context) : MediaNotification.ActionFactory {
        override fun createMediaAction(
            mediaSession: MediaSession,
            icon: IconCompat,
            title: CharSequence,
            command: Int,
        ): androidx.core.app.NotificationCompat.Action =
            androidx.core.app.NotificationCompat.Action.Builder(0, title, null).build()

        override fun createCustomAction(
            mediaSession: MediaSession,
            icon: IconCompat,
            title: CharSequence,
            action: String,
            extras: android.os.Bundle,
        ): androidx.core.app.NotificationCompat.Action =
            androidx.core.app.NotificationCompat.Action.Builder(0, title, null).build()

        override fun createCustomActionFromCustomCommandButton(
            mediaSession: MediaSession,
            customCommandButton: CommandButton,
        ): androidx.core.app.NotificationCompat.Action =
            androidx.core.app.NotificationCompat.Action.Builder(0, "action", null).build()

        override fun createMediaActionPendingIntent(mediaSession: MediaSession, command: Int) =
            android.app.PendingIntent.getActivity(
                context,
                1,
                android.content.Intent(),
                android.app.PendingIntent.FLAG_IMMUTABLE,
            )
    }

    @Test
    fun `provider builds a public media-style notification with the session metadata`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()
        player.setMediaItem(
            MediaItem.Builder()
                .setUri("file:///android_asset/notification-test.mp3")
                .setMediaId("1")
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle("song").setArtist("artist").build(),
                )
                .build(),
        )
        val session = MediaSession.Builder(context, player).build()
        val provider = DoradoMediaNotificationProvider(context)
        val notification = provider.createNotification(
            session,
            ImmutableList.of(),
            FakeFactory(context),
        ) { }
        assertNotNull(notification)
        val built = notification.notification
        assertEquals(DoradoMediaNotificationProvider.NOTIFICATION_ID, notification.notificationId)
        assertTrue(built.extras.getString(android.app.Notification.EXTRA_TITLE) == "song")
        assertEquals("now playing", provider.notificationChannelInfo.name)
        session.release()
        player.release()
    }
}
