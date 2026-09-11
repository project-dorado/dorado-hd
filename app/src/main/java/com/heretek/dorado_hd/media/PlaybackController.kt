package com.heretek.dorado_hd.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.heretek.dorado_hd.data.model.RepeatMode
import com.heretek.dorado_hd.data.model.Rating
import com.heretek.dorado_hd.data.model.Track
import com.heretek.dorado_hd.data.repo.QuickplayRepository
import com.heretek.dorado_hd.data.repo.LibraryRepository
import com.heretek.dorado_hd.analysis.PlayCountStore
import com.heretek.dorado_hd.scrobble.ScrobbleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/**
 * Which presentation the current queue belongs to. `Radio.kt` marks its
 * streams [RADIO] when it starts them; Now Playing uses that explicit signal
 * to show the station identity and a live tag instead of the scrubber
 * (device `GemNowPlayingRadioScene`). Every other surface stays [LIBRARY].
 */
enum class PlaybackSource { LIBRARY, RADIO }

/**
 * App-side playback coordinator. Bridges the Compose UI to the Media3 session
 * service and owns queue ordering (Zune-style shuffle), repeat, ratings and
 * Quickplay history.
 */
class PlaybackController(
    private val context: Context,
    private val library: LibraryRepository,
    private val quickplay: QuickplayRepository,
    private val scrobble: ScrobbleService? = null,
    private val playCounts: PlayCountStore? = null,
    /** Fade-through duration in ms (0 disables); read live from settings. */
    private val crossfadeMs: () -> Long = { 0L },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private var candidateReachedThreshold = false
    private var fadeJob: kotlinx.coroutines.Job? = null
    private var fadeGeneration = 0
    private var fadeOutTriggered = false

    private val _nowPlaying = MutableStateFlow<Track?>(null)
    val nowPlaying: StateFlow<Track?> = _nowPlaying.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _repeat = MutableStateFlow(RepeatMode.OFF)
    val repeat: StateFlow<RepeatMode> = _repeat.asStateFlow()

    private val _currentRating = MutableStateFlow(Rating.NONE)
    val currentRating: StateFlow<Rating> = _currentRating.asStateFlow()

    /** Where the current queue came from (library vs. a live radio stream). */
    private val _source = MutableStateFlow(PlaybackSource.LIBRARY)
    val source: StateFlow<PlaybackSource> = _source.asStateFlow()

    /** Sleep-timer countdown in ms; 0 when no timer is armed. */
    private val _sleepRemainingMs = MutableStateFlow(0L)
    val sleepRemainingMs: StateFlow<Long> = _sleepRemainingMs.asStateFlow()

    private var sleepDeadlineElapsedMs = 0L
    private var sleepActive = false

    private var baseOrder: List<Track> = emptyList()

    suspend fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, DoradoPlaybackService::class.java))
        controller = MediaController.Builder(context, token).buildAsync().await()
        controller?.addListener(listener)
        positionTicker()
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                _durationMs.value = controller?.duration ?: 0L
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            fadeOutTriggered = false
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                fadeIn()
            }
            val previous = _nowPlaying.value
            val previousReached = candidateReachedThreshold
            val index = controller?.currentMediaItemIndex ?: return
            _currentIndex.value = index
            val track = _queue.value.getOrNull(index)
            _nowPlaying.value = track
            _durationMs.value = controller?.duration ?: 0L
            candidateReachedThreshold = false
            if (previous != null && previousReached && previous.mediaId != track?.mediaId) {
                val finished = previous
                scope.launch {
                    scrobble?.record(finished.artist, finished.title, finished.album, (finished.durationMs / 1000).toInt())
                }
                scope.launch {
                    playCounts?.increment(finished.mediaId)
                }
            }
            if (track != null) {
                scope.launch {
                    _currentRating.value = quickplay.ratingOf(track.mediaId).first()
                    quickplay.recordHistory(
                        kind = com.heretek.dorado_hd.data.model.PinKind.TRACK,
                        refId = track.mediaId,
                        label = track.title,
                        subLabel = track.artist,
                        artAlbumId = track.albumId,
                    )
                }
            }
        }
    }

    private fun positionTicker() {
        scope.launch {
            while (true) {
                val player = controller
                if (player != null && _isPlaying.value) {
                    val position = player.currentPosition.coerceAtLeast(0)
                    _positionMs.value = position
                    if (reachedScrobbleThreshold(position, _durationMs.value)) {
                        candidateReachedThreshold = true
                    }
                    // Fade-through: ramp out over the tail of the track so the
                    // automatic advance fades in on the next item.
                    val fadeMs = FadeRamp.effectiveMs(crossfadeMs())
                    if (fadeMs > 0 && !fadeOutTriggered &&
                        FadeRamp.inFadeOutWindow(position, _durationMs.value, fadeMs)
                    ) {
                        fadeOutTriggered = true
                        fadeTo(0f, fadeMs)
                    }
                    tickSleepTimer(player)
                }
                delay(500)
            }
        }
    }

    // ---- fade-through transition (post-device extension, canon §10) ----

    private fun cancelFade() {
        fadeGeneration++
        fadeJob?.cancel()
        fadeJob = null
    }

    private fun fadeIn() {
        val player = controller ?: return
        val ms = FadeRamp.effectiveMs(crossfadeMs())
        if (ms <= 0L) {
            cancelFade()
            player.volume = 1f
            return
        }
        player.volume = 0f
        fadeTo(1f, ms)
    }

    private fun fadeTo(target: Float, durationMs: Long) {
        val player = controller ?: return
        if (FadeRamp.effectiveMs(crossfadeMs()) <= 0L) {
            cancelFade()
            player.volume = 1f
            return
        }
        val from = player.volume
        val gen = ++fadeGeneration
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val steps = FadeRamp.steps(durationMs)
            for (i in 1..steps) {
                if (gen != fadeGeneration) return@launch
                delay(FadeRamp.STEP_MS)
                player.volume = FadeRamp.value(from, target, i * FadeRamp.STEP_MS, durationMs)
            }
            if (gen == fadeGeneration) player.volume = target
        }
    }

    // ---- sleep timer (post-device extension, canon §10) ----

    /** Arm the sleep timer for [minutes]; 0 or negative cancels it. */
    fun startSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        sleepDeadlineElapsedMs = android.os.SystemClock.elapsedRealtime() + minutes * 60_000L
        sleepActive = true
        _sleepRemainingMs.value = minutes * 60_000L
    }

    fun cancelSleepTimer() {
        sleepActive = false
        sleepDeadlineElapsedMs = 0L
        _sleepRemainingMs.value = 0L
    }

    private fun tickSleepTimer(player: MediaController) {
        if (!sleepActive) return
        val remaining = (sleepDeadlineElapsedMs - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        _sleepRemainingMs.value = remaining
        if (remaining > 0L) return
        sleepActive = false
        // Fade out over a short window, then stop; restore volume for the
        // next manual play.
        cancelFade()
        fadeJob = scope.launch {
            val ms = 3_000L
            val from = player.volume
            val steps = FadeRamp.steps(ms)
            for (i in 1..steps) {
                delay(FadeRamp.STEP_MS)
                player.volume = FadeRamp.value(from, 0f, i * FadeRamp.STEP_MS, ms)
            }
            player.pause()
            player.volume = 1f
        }
        _sleepRemainingMs.value = 0L
    }

    /** Manual skip: quick fade-out, then advance (the transition fades in). */
    private fun skipWithFade(action: (MediaController) -> Unit) {
        val player = controller ?: return
        val ms = FadeRamp.effectiveMs(crossfadeMs())
        if (ms <= 0L) {
            cancelFade()
            player.volume = 1f
            action(player)
            return
        }
        val quick = ms.coerceAtMost(400L)
        val gen = ++fadeGeneration
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val from = player.volume
            val steps = FadeRamp.steps(quick)
            for (i in 1..steps) {
                if (gen != fadeGeneration) return@launch
                delay(FadeRamp.STEP_MS)
                player.volume = FadeRamp.value(from, 0f, i * FadeRamp.STEP_MS, quick)
            }
            if (gen == fadeGeneration) {
                player.volume = 0f
                action(player)
            }
        }
    }

    fun play(tracks: List<Track>, startIndex: Int = 0, source: PlaybackSource = PlaybackSource.LIBRARY) {
        if (tracks.isEmpty()) return
        baseOrder = tracks
        _queue.value = tracks
        _source.value = source
        val items = tracks.map { it.toMediaItem() }
        val player = controller ?: return
        player.setMediaItems(items, startIndex, 0L)
        player.prepare()
        fadeOutTriggered = false
        if (FadeRamp.effectiveMs(crossfadeMs()) > 0L) player.volume = 0f
        player.play()
        if (FadeRamp.effectiveMs(crossfadeMs()) > 0L) fadeTo(1f, FadeRamp.effectiveMs(crossfadeMs()))
    }

    fun enqueue(track: Track) {
        val player = controller ?: return
        player.addMediaItem(track.toMediaItem())
        _queue.value = _queue.value + track
        if (baseOrder.isEmpty()) baseOrder = _queue.value
    }

    fun toggle() {
        val player = controller ?: return
        if (player.isPlaying) {
            // Pausing mid-fade must not leave the volume at 0 for the resume.
            cancelFade()
            player.volume = 1f
            player.pause()
        } else {
            player.play()
            fadeIn()
        }
    }

    fun next() {
        skipWithFade { it.seekToNextMediaItem() }
    }

    fun previous() {
        skipWithFade { it.seekToPreviousMediaItem() }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _positionMs.value = positionMs
    }

    fun seekToIndex(index: Int) {
        controller?.seekTo(index, 0L)
    }

    /** Removes the queue entry at [index] (the device's showlist/queue list). */
    fun removeAt(index: Int) {
        val queueNow = _queue.value
        if (index !in queueNow.indices) return
        controller?.removeMediaItem(index)
        _queue.value = queueNow.toMutableList().also { it.removeAt(index) }
    }

    fun setShuffle(enabled: Boolean) {
        _shuffle.value = enabled
        val player = controller ?: return
        val current = _nowPlaying.value
        val queueNow = _queue.value
        if (queueNow.isEmpty()) return

        if (!enabled) {
            val rest = baseOrder.filter { it.mediaId != current?.mediaId }
            val newOrder = if (current != null && baseOrder.contains(current)) {
                baseOrder
            } else {
                listOfNotNull(current) + rest
            }
            _queue.value = newOrder
            player.setMediaItems(newOrder.map { it.toMediaItem() }, 0, 0L)
            player.prepare()
            if (_isPlaying.value) player.play()
            return
        }

        // Smart DJ (canon §4): hearts prioritized, broken hearts skipped.
        scope.launch {
            val ratings = quickplay.ratings()
            val newOrder = smartShuffleOrder(baseOrder, ratings, _nowPlaying.value)
            _queue.value = newOrder
            player.setMediaItems(newOrder.map { it.toMediaItem() }, 0, 0L)
            player.prepare()
            if (_isPlaying.value) player.play()
        }
    }

    fun cycleRepeat() {
        _repeat.value = when (_repeat.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        controller?.repeatMode = when (_repeat.value) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    fun setRating(rating: Rating) {
        val track = _nowPlaying.value ?: return
        _currentRating.value = rating
        scope.launch { quickplay.setRating(track.mediaId, rating) }
    }

    suspend fun currentTrackListSnapshot(): List<Track> = _queue.value

    fun release() {
        controller?.release()
        controller = null
    }

    companion object {
        /**
         * Smart DJ ordering (canon §4): the current track leads, hearted
         * tracks follow, then unrated; broken-heart tracks are skipped.
         */
        fun smartShuffleOrder(
            base: List<Track>,
            ratings: Map<Long, Int>,
            current: Track?,
        ): List<Track> {
            val (broken, rest) = base.partition { ratings[it.mediaId] == Rating.BROKEN.value }
            val (hearted, neutral) = rest.partition { ratings[it.mediaId] == Rating.HEART.value }
            val lead = current?.let { c -> rest.firstOrNull { it.mediaId == c.mediaId } }
            return buildList {
                if (lead != null) add(lead)
                addAll(hearted.filter { it.mediaId != lead?.mediaId }.shuffled())
                addAll(neutral.filter { it.mediaId != lead?.mediaId }.shuffled())
            }
        }

        fun audioManager(context: Context): AudioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        /**
         * Last.fm scrobble rule: at least half the track, or 4 minutes —
         * whichever comes first.
         */
        fun reachedScrobbleThreshold(positionMs: Long, durationMs: Long): Boolean =
            positionMs >= 240_000L || (durationMs > 0 && positionMs >= durationMs / 2)
    }
}
