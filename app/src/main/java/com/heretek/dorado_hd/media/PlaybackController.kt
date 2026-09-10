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
 * App-side playback coordinator. Bridges the Compose UI to the Media3 session
 * service and owns queue ordering (Zune-style shuffle), repeat, ratings and
 * Quickplay history.
 */
class PlaybackController(
    private val context: Context,
    private val library: LibraryRepository,
    private val quickplay: QuickplayRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null

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
            val index = controller?.currentMediaItemIndex ?: return
            _currentIndex.value = index
            val track = _queue.value.getOrNull(index)
            _nowPlaying.value = track
            _durationMs.value = controller?.duration ?: 0L
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
                    _positionMs.value = player.currentPosition.coerceAtLeast(0)
                }
                delay(500)
            }
        }
    }

    fun play(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        baseOrder = tracks
        _queue.value = tracks
        val items = tracks.map { it.toMediaItem() }
        val player = controller ?: return
        player.setMediaItems(items, startIndex, 0L)
        player.prepare()
        player.play()
    }

    fun enqueue(track: Track) {
        val player = controller ?: return
        player.addMediaItem(track.toMediaItem())
        _queue.value = _queue.value + track
        if (baseOrder.isEmpty()) baseOrder = _queue.value
    }

    fun toggle() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _positionMs.value = positionMs
    }

    fun seekToIndex(index: Int) {
        controller?.seekTo(index, 0L)
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
    }
}
