package com.heretek.dorado_hd.ui.apps

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.data.model.Track
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.components.AlbumArt
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import com.heretek.dorado_hd.ui.screens.formatTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Pure queue builder for Shuffle By Album: shuffle the album order, keep every
 * album's track order, and hand the flattened list to the playback controller.
 */
object ShuffleEngine {
    enum class Scope(val label: String) {
        ALL("all"),
        ARTIST("artist"),
        GENRE("genre"),
        PLAYLIST("playlist"),
    }

    const val VARIOUS_ARTISTS = "Various Artists"
    const val UNKNOWN_ARTIST = "Unknown Artist"
    const val SOUNDTRACK = "Soundtrack"
    val PLACEHOLDER_ARTISTS: Set<String> = setOf(VARIOUS_ARTISTS, UNKNOWN_ARTIST, SOUNDTRACK)

    data class AlbumGroup(
        val albumId: Long,
        val title: String,
        val artist: String,
        val tracks: List<Track>,
    )

    data class ShuffleState(
        val scope: Scope,
        val scopeName: String?,
        val groupByArtist: Boolean,
        val albums: List<AlbumGroup>,
        val albumIndex: Int = 0,
        val songIndex: Int = 0,
        val isPlaying: Boolean = false,
        val generation: Int = 0,
    )

    data class ShufflePrefs(
        val groupByArtist: Boolean,
        val scope: Scope,
        val scopeName: String?,
    )

    fun buildQueue(
        albums: List<AlbumGroup>,
        scope: Scope = Scope.ALL,
        scopeName: String? = null,
        groupByArtist: Boolean = false,
        rng: Random = Random.Default,
        retainAlbumId: Long? = null,
        retainSongIndex: Int = 0,
    ): ShuffleState? {
        val playable = albums.filter { it.tracks.isNotEmpty() }
        val scoped = when (scope) {
            Scope.ALL -> playable
            Scope.ARTIST ->
                if (scopeName.isNullOrBlank()) playable.filter { it.artist !in PLACEHOLDER_ARTISTS }
                else playable.filter { it.artist.equals(scopeName, ignoreCase = true) }
            Scope.GENRE ->
                if (scopeName.isNullOrBlank()) playable
                else playable.filter { group -> group.tracks.any { it.genre.equals(scopeName, ignoreCase = true) } }
            Scope.PLAYLIST -> playable
        }
        if (scoped.isEmpty()) return null
        val ordered = when {
            groupByArtist && scope == Scope.ARTIST && scopeName.isNullOrBlank() -> artistBlockOrder(scoped, rng)
            groupByArtist -> deinterleave(scoped, rng)
            else -> scoped.shuffled(rng)
        }
        val finalOrder = if (retainAlbumId != null) {
            val from = ordered.indexOfFirst { it.albumId == retainAlbumId }
            if (from > 0) listOf(ordered[from]) + ordered.filterIndexed { index, _ -> index != from } else ordered
        } else {
            ordered
        }
        val retained = retainAlbumId != null && finalOrder.firstOrNull()?.albumId == retainAlbumId
        val songIndex = if (retained) retainSongIndex.coerceIn(0, finalOrder.first().tracks.lastIndex) else 0
        return ShuffleState(
            scope = scope,
            scopeName = scopeName,
            groupByArtist = groupByArtist,
            albums = finalOrder,
            albumIndex = 0,
            songIndex = songIndex,
        )
    }

    private fun artistBlockOrder(albums: List<AlbumGroup>, rng: Random): List<AlbumGroup> {
        val byArtist = albums.groupBy { it.artist }
        val artists = byArtist.keys.shuffled(rng)
        return artists.flatMap { artist -> byArtist.getValue(artist).shuffled(rng) }
    }

    /**
     * Shuffle albums then greedily pick the artist with the most remaining
     * albums that differs from the previous one, so neighbours differ whenever
     * a rearrangement allows it.
     */
    private fun deinterleave(albums: List<AlbumGroup>, rng: Random): List<AlbumGroup> {
        val buckets = albums.groupBy { it.artist }.values.map { it.shuffled(rng).toMutableList() }.toMutableList()
        val out = mutableListOf<AlbumGroup>()
        var lastArtist: String? = null
        while (buckets.any { it.isNotEmpty() }) {
            val nonEmpty = buckets.filter { it.isNotEmpty() }
            val candidates = nonEmpty.filter { it.first().artist != lastArtist }
            val pool = if (candidates.isNotEmpty()) candidates else nonEmpty
            val maxRemaining = pool.maxOf { it.size }
            val best = pool.filter { it.size == maxRemaining }
            val bucket = best.random(rng)
            val album = bucket.removeAt(0)
            out += album
            lastArtist = album.artist
        }
        return out
    }

    fun currentTrack(state: ShuffleState): Track? =
        state.albums.getOrNull(state.albumIndex)?.tracks?.getOrNull(state.songIndex)

    fun currentAlbum(state: ShuffleState): AlbumGroup? = state.albums.getOrNull(state.albumIndex)

    fun atQueueEnd(state: ShuffleState): Boolean {
        val last = state.albums.lastOrNull() ?: return true
        return state.albumIndex >= state.albums.lastIndex && state.songIndex >= last.tracks.lastIndex
    }

    fun flatten(state: ShuffleState): List<Pair<Int, Int>> =
        state.albums.flatMapIndexed { albumIndex, group -> group.tracks.indices.map { albumIndex to it } }

    fun flatIndexOf(state: ShuffleState): Int {
        var index = 0
        for (a in 0 until state.albumIndex.coerceAtMost(state.albums.size)) {
            index += state.albums[a].tracks.size
        }
        return index + state.songIndex
    }

    fun trackAtFlat(state: ShuffleState, flatIndex: Int): Track? {
        var remaining = flatIndex
        for (group in state.albums) {
            if (remaining < group.tracks.size) return group.tracks.getOrNull(remaining)
            remaining -= group.tracks.size
        }
        return null
    }

    fun upcoming(state: ShuffleState, count: Int = 5): List<Track> {
        val flat = flatten(state)
        val start = flatIndexOf(state)
        return flat.drop(start + 1).take(count).mapNotNull { (a, s) -> state.albums.getOrNull(a)?.tracks?.getOrNull(s) }
    }

    fun next(state: ShuffleState, forward: Boolean = true, rng: Random = Random.Default): ShuffleState {
        if (state.albums.isEmpty()) return state
        val album = state.albums[state.albumIndex]
        if (forward && state.songIndex < album.tracks.lastIndex) {
            return state.copy(songIndex = state.songIndex + 1)
        }
        return nextAlbum(state, forward, rng)
    }

    fun nextAlbum(state: ShuffleState, forward: Boolean = true, rng: Random = Random.Default): ShuffleState {
        if (state.albums.isEmpty()) return state
        if (forward) {
            if (state.albumIndex >= state.albums.lastIndex) {
                return state.copy(
                    albums = state.albums.shuffled(rng),
                    albumIndex = 0,
                    songIndex = 0,
                    generation = state.generation + 1,
                )
            }
            return state.copy(albumIndex = state.albumIndex + 1, songIndex = 0)
        }
        return if (state.albumIndex <= 0) {
            state.copy(songIndex = 0)
        } else {
            val previous = state.albums[state.albumIndex - 1]
            state.copy(albumIndex = state.albumIndex - 1, songIndex = previous.tracks.lastIndex)
        }
    }

    fun previous(state: ShuffleState): ShuffleState {
        val album = state.albums.getOrNull(state.albumIndex) ?: return state
        if (state.songIndex > 0) return state.copy(songIndex = state.songIndex - 1)
        return if (state.albumIndex > 0) {
            val previousAlbum = state.albums[state.albumIndex - 1]
            state.copy(albumIndex = state.albumIndex - 1, songIndex = previousAlbum.tracks.lastIndex)
        } else {
            state
        }
    }

    /** Follow an external (controller) track change; null when not in the queue. */
    fun syncToMediaId(state: ShuffleState, mediaId: Long): ShuffleState? {
        state.albums.forEachIndexed { a, group ->
            val s = group.tracks.indexOfFirst { it.mediaId == mediaId }
            if (s >= 0) return state.copy(albumIndex = a, songIndex = s)
        }
        return null
    }

    fun carousel(state: ShuffleState, radius: Int = 2): List<AlbumGroup> {
        if (state.albums.isEmpty()) return emptyList()
        return (-radius..radius).mapNotNull { offset ->
            state.albums.getOrNull(state.albumIndex + offset)
        }
    }

    fun encodePrefs(prefs: ShufflePrefs): String =
        "group=${if (prefs.groupByArtist) 1 else 0};scope=${prefs.scope.name.lowercase()};" +
            "name=${prefs.scopeName ?: ""}"

    fun decodePrefs(blob: String?): ShufflePrefs {
        val map = blob.orEmpty().split(';').mapNotNull {
            val parts = it.split('=').takeIf { p -> p.size == 2 } ?: return@mapNotNull null
            parts[0] to parts[1]
        }.toMap()
        val scope = runCatching { Scope.valueOf(map["scope"]?.uppercase() ?: "ALL") }.getOrDefault(Scope.ALL)
        return ShufflePrefs(
            groupByArtist = map["group"] == "1",
            scope = scope,
            scopeName = map["name"]?.ifBlank { null },
        )
    }
}

private enum class ShuffleOverlay { NONE, SCOPE, ARTISTS, GENRES, PLAYLISTS, QUEUE }

@Composable
fun ShuffleByAlbumApp() {
    val graph = LocalDoradoGraph.current
    val colors = LocalDoradoColors.current
    val scope = rememberCoroutineScope()
    val tracks by graph.library.tracks().collectAsState(initial = emptyList())
    val nowPlaying by graph.controller.nowPlaying.collectAsState()
    val isPlaying by graph.controller.isPlaying.collectAsState()
    val playlists by graph.library.playlists().collectAsState(initial = emptyList())

    val groups = remember(tracks) {
        tracks.groupBy { it.albumId }
            .map { (albumId, list) ->
                val ordered = list.sortedWith(compareBy({ it.trackNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }, { it.title }))
                ShuffleEngine.AlbumGroup(
                    albumId = albumId,
                    title = list.first().album,
                    artist = list.first().artist,
                    tracks = ordered,
                )
            }
            .filter { it.title.isNotBlank() }
    }

    var state by remember { mutableStateOf<ShuffleEngine.ShuffleState?>(null) }
    var overlay by remember { mutableStateOf(ShuffleOverlay.NONE) }
    var dragX by remember { mutableStateOf(0f) }
    var lastTouchAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var saver by remember { mutableStateOf(false) }
    var prefs by remember { mutableStateOf(ShuffleEngine.ShufflePrefs(false, ShuffleEngine.Scope.ALL, null)) }

    fun rebuild(retainCurrent: Boolean) {
        val current = state?.let { ShuffleEngine.currentTrack(it) }
        val rebuilt = ShuffleEngine.buildQueue(
            albums = groups,
            scope = prefs.scope,
            scopeName = prefs.scopeName,
            groupByArtist = prefs.groupByArtist,
            rng = Random(state?.generation ?: 0),
            retainAlbumId = if (retainCurrent) current?.albumId else null,
            retainSongIndex = if (retainCurrent) state?.songIndex ?: 0 else 0,
        )
        state = rebuilt
    }

    LaunchedEffect(Unit) {
        prefs = ShuffleEngine.decodePrefs(graph.appState.get("shufflebyalbum"))
        if (groups.isNotEmpty() && state == null) rebuild(retainCurrent = false)
    }

    LaunchedEffect(groups) {
        if (groups.isEmpty()) {
            state = null
        } else if (state == null || state?.albums?.none { album -> groups.any { it.albumId == album.albumId } } == true) {
            rebuild(retainCurrent = false)
        }
    }

    LaunchedEffect(prefs) {
        graph.appState.put("shufflebyalbum", ShuffleEngine.encodePrefs(prefs))
    }

    LaunchedEffect(nowPlaying?.mediaId) {
        val track = nowPlaying ?: return@LaunchedEffect
        val current = state ?: return@LaunchedEffect
        ShuffleEngine.syncToMediaId(current, track.mediaId)?.let { state = it }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            if (System.currentTimeMillis() - lastTouchAt >= DoradoTokens.IDLE_SCREENSAVER_MS) saver = true
        }
    }

    fun touch() {
        lastTouchAt = System.currentTimeMillis()
        saver = false
    }

    fun playQueue(index: Int = state?.let { ShuffleEngine.flatIndexOf(it) } ?: 0) {
        val current = state ?: return
        val flat = current.albums.flatMap { it.tracks }
        if (flat.isEmpty()) return
        graph.controller.play(flat, index.coerceIn(0, flat.lastIndex))
        state = current.copy(isPlaying = true)
    }

    fun skip(forward: Boolean) {
        val current = state ?: return
        val next = if (forward) ShuffleEngine.next(current, forward = true, rng = Random(current.generation + 17)) else ShuffleEngine.previous(current)
        state = next
        val flat = next.albums.flatMap { it.tracks }
        val index = ShuffleEngine.flatIndexOf(next)
        if (flat.isNotEmpty()) {
            if (next.generation != current.generation) {
                graph.controller.play(flat, index)
            } else {
                graph.controller.seekToIndex(index)
            }
        }
    }

    fun skipToCurrent() {
        val current = state ?: return
        val flat = current.albums.flatMap { it.tracks }
        if (flat.isNotEmpty()) graph.controller.seekToIndex(ShuffleEngine.flatIndexOf(current).coerceIn(0, flat.lastIndex))
    }

    DetailScaffold(title = "shuffle by album") {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val current = state
                if (current == null) {
                    EdgeCropText(text = "no albums on device", fontSize = DoradoTokens.TYPE_NOW_META.dp, color = colors.textSecondary)
                    return@Column
                }
                val track = ShuffleEngine.currentTrack(current)
                val album = ShuffleEngine.currentAlbum(current)
                EdgeCropText(
                    text = album?.artist ?: track?.artist ?: "—",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    color = colors.textSecondary,
                )
                EdgeCropText(
                    text = album?.title ?: "—",
                    fontSize = DoradoTokens.TYPE_NOW_TITLE.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                EdgeCropText(
                    text = track?.title ?: "—",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.accent,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Five-art carousel: ±2 around the current album; drag to flip.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .pointerInput(current.albumIndex) {
                            detectHorizontalDragGestures(
                                onDragStart = { touch() },
                                onDragEnd = {
                                    if (dragX < -60f) {
                                        state = ShuffleEngine.nextAlbum(current, forward = true, rng = Random(current.generation + 3))
                                        skipToCurrent()
                                    } else if (dragX > 60f) {
                                        state = ShuffleEngine.nextAlbum(current, forward = false)
                                        skipToCurrent()
                                    }
                                    dragX = 0f
                                },
                            ) { _, delta -> dragX += delta }
                        },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShuffleEngine.carousel(current, radius = 2).forEach { entry ->
                        val focused = entry.albumId == album?.albumId
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    alpha = if (focused) 1f else 0.55f
                                    translationX = if (focused) dragX * 0.25f else 0f
                                },
                        ) {
                            AlbumArt(
                                model = entry.tracks.firstOrNull()?.albumArtUri,
                                contentDescription = entry.title,
                                modifier = Modifier.size(if (focused) 64.dp else 48.dp),
                            )
                            EdgeCropText(
                                text = entry.title,
                                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                                color = if (focused) colors.accent else colors.textSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    EdgeCropText(
                        text = "◀◀",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = colors.textPrimary,
                        modifier = Modifier.pointerInput(Unit) { detectTapGestures { touch(); skip(false) } },
                    )
                    EdgeCropText(
                        text = if (isPlaying) "pause" else "play",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = colors.accent,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures {
                                touch()
                                if (isPlaying) graph.controller.toggle() else playQueue()
                            }
                        },
                    )
                    EdgeCropText(
                        text = "▶▶",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = colors.textPrimary,
                        modifier = Modifier.pointerInput(Unit) { detectTapGestures { touch(); skip(true) } },
                    )
                    EdgeCropText(
                        text = "queue",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = colors.textSecondary,
                        modifier = Modifier.pointerInput(Unit) { detectTapGestures { touch(); overlay = ShuffleOverlay.QUEUE } },
                    )
                    EdgeCropText(
                        text = "shuffle",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = colors.textSecondary,
                        modifier = Modifier.pointerInput(Unit) { detectTapGestures { touch(); overlay = ShuffleOverlay.SCOPE } },
                    )
                }
                val upcoming = ShuffleEngine.upcoming(current, 5)
                EdgeCropText(text = "up next", fontSize = DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                upcoming.forEach { next ->
                    EdgeCropText(
                        text = "${next.title} — ${next.artist}",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = colors.textSecondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            when (overlay) {
                ShuffleOverlay.QUEUE -> QueueSheet(
                    state = state,
                    onPick = { index ->
                        touch()
                        playQueue(index)
                        overlay = ShuffleOverlay.NONE
                    },
                    onDismiss = { touch(); overlay = ShuffleOverlay.NONE },
                )
                ShuffleOverlay.SCOPE -> ScopePicker(
                    prefs = prefs,
                    onPickAll = {
                        prefs = prefs.copy(scope = ShuffleEngine.Scope.ALL, scopeName = null)
                        rebuild(retainCurrent = true)
                        overlay = ShuffleOverlay.NONE
                    },
                    onPickArtists = { overlay = ShuffleOverlay.ARTISTS },
                    onPickGenres = { overlay = ShuffleOverlay.GENRES },
                    onPickPlaylists = { overlay = ShuffleOverlay.PLAYLISTS },
                    onToggleGroup = {
                        prefs = prefs.copy(groupByArtist = !prefs.groupByArtist)
                        rebuild(retainCurrent = true)
                    },
                    onDismiss = { touch(); overlay = ShuffleOverlay.NONE },
                )
                ShuffleOverlay.ARTISTS -> PickerList(
                    title = "artist",
                    entries = tracks.map { it.artist }.filter { it.isNotBlank() }.distinct().sorted(),
                    onPick = { name ->
                        prefs = prefs.copy(scope = ShuffleEngine.Scope.ARTIST, scopeName = name)
                        rebuild(retainCurrent = false)
                        overlay = ShuffleOverlay.NONE
                    },
                    onDismiss = { touch(); overlay = ShuffleOverlay.NONE },
                )
                ShuffleOverlay.GENRES -> PickerList(
                    title = "genre",
                    entries = tracks.map { it.genre }.filter { it.isNotBlank() }.distinct().sorted(),
                    onPick = { name ->
                        prefs = prefs.copy(scope = ShuffleEngine.Scope.GENRE, scopeName = name)
                        rebuild(retainCurrent = false)
                        overlay = ShuffleOverlay.NONE
                    },
                    onDismiss = { touch(); overlay = ShuffleOverlay.NONE },
                )
                ShuffleOverlay.PLAYLISTS -> PickerList(
                    title = "playlist",
                    entries = playlists.map { it.name },
                    onPick = { name ->
                        val playlist = playlists.firstOrNull { it.name == name }
                        if (playlist != null) {
                            scope.launch {
                                val list = graph.library.tracksInPlaylist(playlist.id)
                                val grouped = list.groupBy { it.albumId }.map { (albumId, ts) ->
                                    ShuffleEngine.AlbumGroup(albumId, ts.first().album, ts.first().artist, ts.sortedBy { it.trackNumber })
                                }
                                val built = ShuffleEngine.buildQueue(grouped, ShuffleEngine.Scope.PLAYLIST, name, prefs.groupByArtist)
                                if (built != null) state = built
                                prefs = prefs.copy(scope = ShuffleEngine.Scope.PLAYLIST, scopeName = name)
                                overlay = ShuffleOverlay.NONE
                            }
                        }
                    },
                    onDismiss = { touch(); overlay = ShuffleOverlay.NONE },
                )
                ShuffleOverlay.NONE -> Unit
            }

            if (saver) {
                Screensaver(state = state, onAnyTouch = { touch() })
            }
        }
    }
}

private fun playQueue() {}

@Composable
private fun QueueSheet(
    state: ShuffleEngine.ShuffleState?,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    if (state == null) return
    val flat = state.albums.flatMap { it.tracks }
    val currentIndex = ShuffleEngine.flatIndexOf(state)
    val remaining = flat.drop(currentIndex).sumOf { it.durationMs }
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                EdgeCropText(
                    text = "queue — song ${currentIndex + 1} of ${flat.size}",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    color = colors.accent,
                    modifier = Modifier.weight(1f),
                )
                EdgeCropText(
                    text = "${formatTime(remaining)} left",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.width(12.dp))
                EdgeCropText(
                    text = "close",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.textSecondary,
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures { onDismiss() } },
                )
            }
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(flat) { index, track ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(index) { detectTapGestures { onPick(index) } }
                            .padding(vertical = 4.dp),
                    ) {
                        EdgeCropText(
                            text = if (index == currentIndex) "▸ ${track.title}" else track.title,
                            fontSize = DoradoTokens.TYPE_LIST.dp,
                            color = if (index == currentIndex) colors.accent else colors.textPrimary,
                        )
                        EdgeCropText(
                            text = "${track.album} — ${track.artist}",
                            fontSize = DoradoTokens.TYPE_CAPTION.dp,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopePicker(
    prefs: ShuffleEngine.ShufflePrefs,
    onPickAll: () -> Unit,
    onPickArtists: () -> Unit,
    onPickGenres: () -> Unit,
    onPickPlaylists: () -> Unit,
    onToggleGroup: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EdgeCropText(text = "shuffle", fontSize = DoradoTokens.TYPE_MENU_ITEM.dp)
            EdgeCropText(
                text = if (prefs.scope == ShuffleEngine.Scope.ALL && prefs.scopeName == null) "all — " else "all",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.accent,
                modifier = Modifier.pointerInput(Unit) { detectTapGestures { onPickAll() } },
            )
            EdgeCropText(
                text = if (prefs.scope == ShuffleEngine.Scope.ARTIST) "artist — ${prefs.scopeName ?: "pick"}" else "artist",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = if (prefs.scope == ShuffleEngine.Scope.ARTIST) colors.accent else colors.textPrimary,
                modifier = Modifier.pointerInput(Unit) { detectTapGestures { onPickArtists() } },
            )
            EdgeCropText(
                text = if (prefs.scope == ShuffleEngine.Scope.GENRE) "genre — ${prefs.scopeName ?: "pick"}" else "genre",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = if (prefs.scope == ShuffleEngine.Scope.GENRE) colors.accent else colors.textPrimary,
                modifier = Modifier.pointerInput(Unit) { detectTapGestures { onPickGenres() } },
            )
            EdgeCropText(
                text = if (prefs.scope == ShuffleEngine.Scope.PLAYLIST) "playlist — ${prefs.scopeName ?: "pick"}" else "playlist",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = if (prefs.scope == ShuffleEngine.Scope.PLAYLIST) colors.accent else colors.textPrimary,
                modifier = Modifier.pointerInput(Unit) { detectTapGestures { onPickPlaylists() } },
            )
            EdgeCropText(
                text = "group by artist: ${if (prefs.groupByArtist) "on" else "off"}",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.textSecondary,
                modifier = Modifier.pointerInput(Unit) { detectTapGestures { onToggleGroup() } },
            )
            EdgeCropText(
                text = "close",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.textSecondary,
                modifier = Modifier.pointerInput(Unit) { detectTapGestures { onDismiss() } },
            )
        }
    }
}

@Composable
private fun PickerList(
    title: String,
    entries: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                EdgeCropText(text = title, fontSize = DoradoTokens.TYPE_NOW_TITLE.dp, modifier = Modifier.weight(1f))
                EdgeCropText(
                    text = "close",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.textSecondary,
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures { onDismiss() } },
                )
            }
            if (entries.isEmpty()) {
                EdgeCropText(text = "none", fontSize = DoradoTokens.TYPE_LIST.dp, color = colors.textInactive)
                return@Column
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(entries) { _, entry ->
                    EdgeCropText(
                        text = entry,
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(entry) { detectTapGestures { onPick(entry) } }
                            .padding(vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Screensaver(state: ShuffleEngine.ShuffleState?, onAnyTouch: () -> Unit) {
    val colors = LocalDoradoColors.current
    val current = state ?: return
    val track = ShuffleEngine.currentTrack(current)
    val transition = rememberInfiniteTransition(label = "saver")
    val drift by transition.animateFloat(
        initialValue = -24f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(5_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .graphicsLayer { translationX = drift }
            .pointerInput(Unit) { detectTapGestures { onAnyTouch() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AlbumArt(
                model = track?.albumArtUri,
                contentDescription = track?.album,
                modifier = Modifier.size(120.dp),
            )
            EdgeCropText(text = track?.title ?: "—", fontSize = DoradoTokens.TYPE_SAVER_TITLE.dp)
            EdgeCropText(text = track?.artist ?: "—", fontSize = DoradoTokens.TYPE_SAVER_ARTIST.dp, color = colors.textSecondary)
            EdgeCropText(text = track?.album ?: "—", fontSize = DoradoTokens.TYPE_SAVER_ALBUM.dp, color = colors.textInactive)
        }
    }
}

