package com.heretek.dorado_hd

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.heretek.dorado_hd.data.db.DoradoDatabase
import com.heretek.dorado_hd.data.repo.LibraryRepository
import com.heretek.dorado_hd.data.repo.QuickplayRepository
import com.heretek.dorado_hd.data.repo.SettingsRepository
import com.heretek.dorado_hd.media.PlaybackController
import com.heretek.dorado_hd.net.ArtistImageService
import com.heretek.dorado_hd.ui.nav.DoradoNav
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The application graph. Deliberately small and hand-wired: no DI framework,
 * mirroring the lean structure of the Zune-era codebase we emulate.
 */
class DoradoGraph(
    val library: LibraryRepository,
    val quickplay: QuickplayRepository,
    val settings: SettingsRepository,
    val settingsFlow: kotlinx.coroutines.flow.Flow<com.heretek.dorado_hd.data.repo.DoradoSettings>,
    val controller: PlaybackController,
    val nav: DoradoNav,
    val artistImages: ArtistImageService,
    val artistBios: com.heretek.dorado_hd.net.ArtistBioService,
    val notes: com.heretek.dorado_hd.data.repo.NotesRepository,
    val calendar: com.heretek.dorado_hd.data.repo.CalendarRepository,
    val alarms: com.heretek.dorado_hd.data.repo.AlarmRepository,
    val radio: com.heretek.dorado_hd.data.repo.RadioRepository,
    val podcasts: com.heretek.dorado_hd.data.repo.PodcastRepository,
    val games: com.heretek.dorado_hd.data.repo.GameRepository,
    val deviceLink: com.heretek.dorado_hd.data.repo.DeviceLinkRepository,
    val analysis: com.heretek.dorado_hd.analysis.AudioAnalysisService,
    val mixes: com.heretek.dorado_hd.analysis.CloudMixService,
    val scrobble: com.heretek.dorado_hd.scrobble.ScrobbleService,
    val lyrics: com.heretek.dorado_hd.net.LrcLibService,
    val cloudSignIn: com.heretek.dorado_hd.cloud.CloudSignIn,
    val cloudSignInCallback: com.heretek.dorado_hd.cloud.CloudSignInCallback,
    val cloudUpdates: com.heretek.dorado_hd.cloud.CloudUpdateService,
    val playCounts: com.heretek.dorado_hd.analysis.PlayCountStore,
    val appState: com.heretek.dorado_hd.data.repo.AppStateRepository,
)

open class DoradoApp : Application() {

    lateinit var graph: DoradoGraph
        private set

    companion object {
        /**
         * Media3's `MediaController` cannot bind a session service under
         * Robolectric (the shadow passes a null ComponentName). UI tests run
         * with a [Test] application that flips this before `onCreate`; the
         * shipped app always connects.
         */
        @Volatile
        var autoConnectPlayback: Boolean = true
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val db = DoradoDatabase.get(this)
        val library = LibraryRepository(this, db)
        val quickplay = QuickplayRepository(db)
        val settings = SettingsRepository(this)
        val latestSettings = java.util.concurrent.atomic.AtomicReference(com.heretek.dorado_hd.data.repo.DoradoSettings())
        val scrobbleStore = com.heretek.dorado_hd.data.repo.RoomScrobbleStore(db.scrobbleDao())
        val playCountStore = com.heretek.dorado_hd.data.repo.RoomPlayCountStore(db.playCountDao())
        val scrobbleSink = com.heretek.dorado_hd.net.LastFmClient(
            apiKey = { latestSettings.get().lastFmApiKey },
            secret = { latestSettings.get().lastFmApiSecret },
            sessionKey = { latestSettings.get().lastFmSessionKey },
        )
        val cloudMetadata = com.heretek.dorado_hd.cloud.CloudMetadataSource(settings = { latestSettings.get() })
        val scrobble = com.heretek.dorado_hd.scrobble.ScrobbleService(
            store = scrobbleStore,
            sink = scrobbleSink,
            enabled = { latestSettings.get().scrobbleEnabled },
            cloudListen = { artist, title, album ->
                if (cloudMetadata.isEnabled()) cloudMetadata.recordListen(artist, title, album)
            },
        )
        val controller = PlaybackController(
            this, library, quickplay, scrobble, playCountStore,
            crossfadeMs = { latestSettings.get().crossfadeSeconds * 1000L },
        )
        val nav = DoradoNav()
        val artistImages = ArtistImageService(this, db, settings, cloudMetadata)
        val artistBios = com.heretek.dorado_hd.net.ArtistBioService(this, cloudMetadata)
        val notes = com.heretek.dorado_hd.data.repo.NotesRepository(db)
        val calendar = com.heretek.dorado_hd.data.repo.CalendarRepository(db)
        val alarms = com.heretek.dorado_hd.data.repo.AlarmRepository(db)
        val radio = com.heretek.dorado_hd.data.repo.RadioRepository(db)
        val podcasts = com.heretek.dorado_hd.data.repo.PodcastRepository(db)
        val games = com.heretek.dorado_hd.data.repo.GameRepository(db)
        val deviceLink = com.heretek.dorado_hd.data.repo.DeviceLinkRepository(this)
        val featureStore = com.heretek.dorado_hd.data.repo.RoomFeatureStore(db.trackFeatureDao())
        val analyzer = com.heretek.dorado_hd.analysis.PcmFeatureAnalyzer(this)
        val analysis = com.heretek.dorado_hd.analysis.AudioAnalysisService(featureStore, analyzer)
        val cloudMixSource = com.heretek.dorado_hd.cloud.CloudMixSource(settings = { latestSettings.get() })
        val mixes = com.heretek.dorado_hd.analysis.CloudMixService(
            com.heretek.dorado_hd.analysis.DynamicMixService(analysis),
            cloudMixSource,
        )
        val lyrics = com.heretek.dorado_hd.net.LrcLibService()

        // Interactive OAuth 2.0 (PKCE) sign-in: browser + deep-link callback.
        val cloudSignInCallback = com.heretek.dorado_hd.cloud.CloudSignInCallback()
        val cloudSignIn = com.heretek.dorado_hd.cloud.CloudSignIn(
            currentSettings = { latestSettings.get() },
            setEnabled = { settings.setCloudEnabled(it) },
            setToken = { settings.setCloudAccessToken(it) },
            launchBrowser = { url ->
                startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            awaitRedirect = { _, timeoutMs -> cloudSignInCallback.await(timeoutMs) },
        )
        val cloudUpdates = com.heretek.dorado_hd.cloud.CloudUpdateService(settings = { latestSettings.get() })
        val equalizer = com.heretek.dorado_hd.media.EqualizerController()
        val appState = com.heretek.dorado_hd.data.repo.AppStateRepository(db)

        graph = DoradoGraph(
            library, quickplay, settings, settings.settings, controller, nav,
            artistImages, artistBios, notes, calendar, alarms, radio, podcasts, games, deviceLink, analysis, mixes, scrobble, lyrics,
            cloudSignIn, cloudSignInCallback, cloudUpdates, playCountStore, appState,
        )

        if (autoConnectPlayback) {
            appScope.launch {
                controller.connect()
            }
        }

        // Hydrate the M9.3 audio-feature cache from Room so similarity queries
        // immediately use previously analyzed tracks.
        appScope.launch {
            runCatching { analysis.preload() }
        }

        // Keep the artist-image service aware of the user's settings.
        settings.settings
            .onEach {
                artistImages.settingsSnapshot = it
                latestSettings.set(it)
                // M14: apply the device equalizer preset to the playback session.
                equalizer.apply(
                    com.heretek.dorado_hd.analysis.EqPreset.fromName(it.eqPreset),
                    com.heretek.dorado_hd.media.PlaybackSession.audioSessionId,
                )
            }
            .launchIn(appScope)

        // Flush any scrobbles queued while offline (no-op until enabled + configured).
        appScope.launch {
            runCatching { scrobble.flush() }
        }

        // M10: mirror the current track into the home-screen widget. No-op when no
        // widget is placed; failures (e.g. Glance unavailable) are swallowed.
        appScope.launch {
            combine(controller.nowPlaying, controller.isPlaying) { track, playing -> track to playing }
                .collect { (track, playing) ->
                    val state = com.heretek.dorado_hd.widget.NowPlayingWidgetStateMapper.from(
                        title = track?.title,
                        artist = track?.artist,
                        album = track?.album,
                        artUri = track?.albumArtUri?.toString(),
                        isPlaying = playing,
                    )
                    runCatching {
                        com.heretek.dorado_hd.widget.NowPlayingWidgetUpdater.update(this@DoradoApp, state)
                    }
                }
        }

        // Opt-in MediaStore watcher (Settings > collection > watch media store).
        // Debounced 2 s to coalesce bursts (e.g. mass-transfer).
        var watcher: ContentObserver? = null
        val handler = Handler(Looper.getMainLooper())
        settings.settings
            .onEach { cfg ->
                val want = cfg.watchMediaStore
                if (want && watcher == null) {
                    val w = object : ContentObserver(handler) {
                        override fun onChange(selfChange: Boolean, uri: Uri?) {
                            appScope.launch {
                                delay(2_000)
                                library.refresh()
                            }
                        }
                    }
                    contentResolver.registerContentObserver(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        true,
                        w,
                    )
                    watcher = w
                } else if (!want && watcher != null) {
                    contentResolver.unregisterContentObserver(watcher!!)
                    watcher = null
                }
            }
            .launchIn(appScope)

        // First launch: build the collection from MediaStore.
        appScope.launch {
            if (settings.settings.first().libraryScanned.not()) {
                library.refresh()
                settings.setLibraryScanned(true)
            }
        }

        // Seed radio station defaults and register mini-apps (canon §8).
        appScope.launch { radio.seedDefaultsIfEmpty() }
        // Touch DoradoApps so its lazy registry is materialized before any UI
        // looks it up; equivalent to the prior eager assignment.
        com.heretek.dorado_hd.ui.apps.DoradoApps.all.size
    }
}
