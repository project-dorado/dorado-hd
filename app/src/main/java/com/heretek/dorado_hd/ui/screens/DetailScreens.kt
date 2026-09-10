package com.heretek.dorado_hd.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.DoradoMotion
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.components.AlbumArt
import com.heretek.dorado_hd.design.components.CrossbarBar
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.design.components.KineticList
import com.heretek.dorado_hd.design.components.firstLetterOf
import com.heretek.dorado_hd.design.components.rememberZuneFlingBehavior
import com.heretek.dorado_hd.data.model.Album
import com.heretek.dorado_hd.data.model.PinKind
import com.heretek.dorado_hd.data.model.Track
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import com.heretek.dorado_hd.ui.components.LocalContextMenu
import com.heretek.dorado_hd.ui.components.MenuAction
import com.heretek.dorado_hd.ui.components.TrackRow
import com.heretek.dorado_hd.ui.components.trackMenuActions
import com.heretek.dorado_hd.ui.nav.DoradoDestination
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Album detail: art, year, then the track list. Tapping a track plays the
 * album from that point, exactly as the device does.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailScreen(albumId: Long, canvasWidth: androidx.compose.ui.unit.Dp) {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val menus = LocalContextMenu.current
    var album by remember { mutableStateOf<Album?>(null) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    val nowPlayingId by graph.controller.nowPlaying.collectAsState()

    LaunchedEffect(albumId) {
        tracks = graph.library.tracksByAlbum(albumId)
        album = tracks.firstOrNull()?.let {
            com.heretek.dorado_hd.data.model.Album(
                albumId = it.albumId,
                title = it.album,
                artist = it.artist,
                artistId = it.artistId,
                year = it.year,
                trackCount = tracks.size,
                totalDurationMs = tracks.sumOf { t -> t.durationMs },
                dateAdded = it.dateAdded,
            )
        }
    }

    DetailScaffold(title = album?.title ?: "album") {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DoradoTokens.EDGE.dp),
            ) {
                AlbumArt(
                    model = album?.albumArtUri,
                    contentDescription = album?.title,
                    modifier = Modifier.size(72.dp),
                )
                Column(Modifier.padding(start = 12.dp)) {
                    EdgeCropText(
                        text = album?.artist ?: "",
                        fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    )
                    EdgeCropText(
                        text = listOfNotNull(album?.year?.takeIf { it != "unknown" }, "${album?.trackCount ?: 0} songs").joinToString(" — "),
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = LocalDoradoColors.current.textSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row {
                        EdgeCropText(
                            text = "play",
                            fontSize = DoradoTokens.TYPE_LIST.dp,
                            color = LocalDoradoColors.current.accent,
                            modifier = Modifier
                                .clickable {
                                    tracks.firstOrNull()?.let { graph.controller.play(tracks, 0) }
                                }
                                .padding(end = 16.dp),
                        )
                        EdgeCropText(
                            text = "shuffle",
                            fontSize = DoradoTokens.TYPE_LIST.dp,
                            color = LocalDoradoColors.current.accent,
                            modifier = Modifier.clickable {
                                tracks.firstOrNull()?.let {
                                    graph.controller.play(tracks, 0)
                                    graph.controller.setShuffle(true)
                                }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            KineticList(
                items = tracks,
                key = { it.mediaId },
                letter = { firstLetterOf(it.title) },
                rowContent = { track, index ->
                    TrackRow(
                        track = track,
                        playing = nowPlayingId?.mediaId == track.mediaId,
                        onClick = { graph.controller.play(tracks, index) },
                        onLongClick = {
                            menus.show(
                                title = track.title,
                                actions = trackMenuActions(graph, scope, track) + listOf(
                                    MenuAction("view artist") { graph.nav.push(DoradoDestination.Artist(track.artistId)) },
                                ),
                            )
                        },
                        showSubLabel = false,
                    )
                },
            )
        }
    }
}

/**
 * Artist detail: the full device crossbar — albums · songs · bio · photos ·
 * related (canon §4; TechCrunch 2009 documents the five pivots).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistDetailScreen(artistId: Long, canvasWidth: androidx.compose.ui.unit.Dp) {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val menus = LocalContextMenu.current
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var artistName by remember { mutableStateOf<String?>(null) }
    var related by remember { mutableStateOf<List<com.heretek.dorado_hd.data.model.Artist>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(artistId) {
        tracks = graph.library.tracksByArtist(artistId)
        albums = graph.library.albumsByArtist(artistId)
        artistName = tracks.firstOrNull()?.artist
        // On-device audio similarity first (M9), genre overlap as fallback.
        related = graph.analysis.relatedArtists(
            seedArtistId = artistId,
            artists = graph.library.artists().first(),
            library = graph.library.tracks().first(),
            count = 12,
        )
        if (related.isEmpty()) related = graph.library.relatedArtists(artistId)
        loaded = true
    }

    val title = artistName ?: "artist"

    DetailScaffold(title = title) {
        if (!loaded) return@DetailScaffold
        Column(Modifier.fillMaxSize()) {
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                initialPage = 0,
                pageCount = { 5 },
            )
            CrossbarBar(
                labels = listOf("albums", "songs", "bio", "photos", "related"),
                selected = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            )
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> ArtistAlbums(albums)
                    1 -> ArtistSongs(tracks, menus, graph)
                    2 -> ArtistBio(artistName)
                    3 -> ArtistPhotos(artistName)
                    else -> ArtistRelated(related, artistName)
                }
            }
        }
    }
}

@Composable
private fun ArtistBio(artistName: String?) {
    val graph = LocalDoradoGraph.current
    val colors = LocalDoradoColors.current
    if (artistName == null) return
    var bio by remember(artistName) { mutableStateOf<String?>(null) }
    var failed by remember(artistName) { mutableStateOf(false) }

    LaunchedEffect(artistName) {
        bio = graph.artistBios.bioFor(artistName)
        failed = bio == null
    }

    val text = bio
    when {
        text != null -> Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            androidx.compose.foundation.text.BasicText(
                text = text,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = com.heretek.dorado_hd.design.Selawik,
                    fontSize = DoradoTokens.TYPE_LIST.sp,
                    color = colors.textPrimary.copy(alpha = 0.9f),
                    lineHeight = DoradoTokens.ROW_HEIGHT.sp * 0.5f,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 12.dp),
            )
        }
        failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EdgeCropText(
                text = "no biography",
                fontSize = DoradoTokens.TYPE_NOW_META.dp,
                alpha = 0.4f,
            )
        }
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EdgeCropText(
                text = "loading…",
                fontSize = DoradoTokens.TYPE_NOW_META.dp,
                alpha = 0.4f,
            )
        }
    }
}

@Composable
private fun ArtistPhotos(artistName: String?) {
    val graph = LocalDoradoGraph.current
    var photo by remember(artistName) { mutableStateOf<java.io.File?>(null) }

    LaunchedEffect(artistName) {
        if (artistName != null) photo = graph.artistImages.backgroundFor(artistName)
    }

    // The Zune HD's artist page showed multiple band photos from zune.net.
    // catalog.zune.net is gone, so we surface a single available photo (the
    // wallpaper pulled from MusicBrainz) and an honest caption explaining
    // where the rest would have come from.
    Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
        if (photo != null) {
            coil3.compose.AsyncImage(
                model = android.net.Uri.fromFile(photo),
                contentDescription = artistName,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            EdgeCropText(
                text = artistName ?: "",
                fontSize = DoradoTokens.TYPE_NOW_TITLE.dp,
                color = LocalDoradoColors.current.textPrimary,
                modifier = Modifier.padding(top = 8.dp),
            )
            EdgeCropText(
                text = "photos from catalog.zune.net (frozen 2012)",
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = LocalDoradoColors.current.textSecondary,
                alpha = 0.6f,
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EdgeCropText(
                    text = "no photos — enable artist photos in settings",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    alpha = 0.4f,
                )
            }
        }
    }
}

@Composable
private fun ArtistRelated(related: List<com.heretek.dorado_hd.data.model.Artist>, artistName: String? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val graph = LocalDoradoGraph.current
    val menus = LocalContextMenu.current
    val scope = rememberCoroutineScope()
    if (related.isEmpty()) {
        // The Zune HD surfaced 'similar artists' from Last.fm + your friends'
        // listening history. We have neither offline, so the empty state
        // points the user at MusicBrainz, which the DoradoSocial community used
        // as a fallback for related-artist data.
        Column(
            Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        ) {
            EdgeCropText(
                text = "no related artists yet.",
                fontSize = DoradoTokens.TYPE_NOW_META.dp,
                color = LocalDoradoColors.current.textSecondary,
            )
            EdgeCropText(
                text = "related artists are ranked by on-device audio similarity. this artist has no comparable tracks in your library yet.",
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = LocalDoradoColors.current.textSecondary,
                alpha = 0.6f,
            )
            if (artistName != null) {
                val url = "https://musicbrainz.org/artist/" + (artistName.replace(" ", "%20")) + "?query="
                EdgeCropText(
                    text = "open musicbrainz for “$artistName”",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    color = LocalDoradoColors.current.accent,
                    modifier = Modifier
                        .pointerInput(artistName) {
                            detectTapGestures(onTap = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(intent) }
                            })
                        }
                        .padding(vertical = 6.dp),
                )
            }
        }
        return
    }
    com.heretek.dorado_hd.design.components.KineticList(
        items = related,
        key = { it.artistId },
        letter = { com.heretek.dorado_hd.design.components.firstLetterOf(it.name) },
        rowContent = { artist, _ ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(DoradoTokens.ROW_HEIGHT.dp)
                    .combinedClickable(
                        onClick = { graph.nav.push(DoradoDestination.Artist(artist.artistId)) },
                        onLongClick = {
                            menus.show(
                                title = artist.name,
                                actions = listOf(
                                    MenuAction("pin to quickplay") {
                                        scope.launch {
                                            graph.quickplay.pin(PinKind.ARTIST, artist.artistId, artist.name, "${artist.albumCount} albums", 0)
                                        }
                                    },
                                ),
                            )
                        },
                    )
                    .padding(horizontal = DoradoTokens.EDGE.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                EdgeCropText(
                    text = artist.name,
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistAlbums(albums: List<Album>) {
    val graph = LocalDoradoGraph.current
    val menus = LocalContextMenu.current
    val scope = rememberCoroutineScope()
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(DoradoTokens.EDGE.dp),
        horizontalArrangement = Arrangement.spacedBy(DoradoTokens.GRID_GUTTER.dp),
        modifier = Modifier.fillMaxSize(),
        flingBehavior = rememberZuneFlingBehavior(),
    ) {
        items(albums, key = { it.albumId }) { album ->
            Column(
                Modifier
                    .combinedClickable(
                        onClick = { graph.nav.push(DoradoDestination.Album(album.albumId)) },
                        onLongClick = {
                            menus.show(
                                title = album.title,
                                actions = listOf(
                                    MenuAction("pin to quickplay") {
                                        scope.launch {
                                            graph.quickplay.pin(PinKind.ALBUM, album.albumId, album.title, album.artist, album.albumId)
                                        }
                                    },
                                ),
                            )
                        },
                    )
                    .width(92.dp),
            ) {
                AlbumArt(
                    model = album.albumArtUri,
                    contentDescription = album.title,
                    modifier = Modifier.size(92.dp),
                )
                EdgeCropText(text = album.title, fontSize = DoradoTokens.TYPE_CAPTION.dp, modifier = Modifier.fillMaxWidth())
                EdgeCropText(
                    text = album.year,
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = LocalDoradoColors.current.textSecondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistSongs(
    tracks: List<Track>,
    menus: com.heretek.dorado_hd.ui.components.MenuController,
    graph: com.heretek.dorado_hd.DoradoGraph,
) {
    val nowPlayingId by graph.controller.nowPlaying.collectAsState()
    val scope = rememberCoroutineScope()
    KineticList(
        items = tracks,
        key = { it.mediaId },
        letter = { firstLetterOf(it.title) },
        rowContent = { track, index ->
            TrackRow(
                track = track,
                playing = nowPlayingId?.mediaId == track.mediaId,
                onClick = { graph.controller.play(tracks, index) },
                onLongClick = {
                    menus.show(
                        title = track.title,
                        actions = trackMenuActions(graph, scope, track) + listOf(
                            MenuAction("view album") { graph.nav.push(DoradoDestination.Album(track.albumId)) },
                        ),
                    )
                },
            )
        },
    )
}

/** Genre detail: every track in a genre. */
@Composable
fun GenreScreen(genre: String, canvasWidth: androidx.compose.ui.unit.Dp) {
    val graph = LocalDoradoGraph.current
    val menus = LocalContextMenu.current
    val scope = rememberCoroutineScope()
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    val nowPlayingId by graph.controller.nowPlaying.collectAsState()

    LaunchedEffect(genre) {
        tracks = graph.library.tracksByGenre(genre)
    }

    DetailScaffold(title = genre) {
        KineticList(
            items = tracks,
            key = { it.mediaId },
            letter = { firstLetterOf(it.title) },
            rowContent = { track, index ->
                TrackRow(
                    track = track,
                    playing = nowPlayingId?.mediaId == track.mediaId,
                    onClick = { graph.controller.play(tracks, index) },
                    onLongClick = {
                        menus.show(
                            title = track.title,
                            actions = trackMenuActions(graph, scope, track) + listOf(
                                MenuAction("view album") { graph.nav.push(DoradoDestination.Album(track.albumId)) },
                                MenuAction("view artist") { graph.nav.push(DoradoDestination.Artist(track.artistId)) },
                            ),
                        )
                    },
                )
            },
        )
    }
}

/** Playlist detail: tracks with removal. */
@Composable
fun PlaylistDetailScreen(playlistId: Long, canvasWidth: androidx.compose.ui.unit.Dp) {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val menus = LocalContextMenu.current
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    val nowPlayingId by graph.controller.nowPlaying.collectAsState()

    LaunchedEffect(playlistId) {
        tracks = graph.library.tracksInPlaylist(playlistId)
    }

    DetailScaffold(title = "playlist") {
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EdgeCropText(
                    text = "empty playlist",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    alpha = 0.4f,
                )
            }
        } else {
            KineticList(
                items = tracks,
                key = { it.mediaId },
                letter = { firstLetterOf(it.title) },
                rowContent = { track, index ->
                    TrackRow(
                        track = track,
                        playing = nowPlayingId?.mediaId == track.mediaId,
                        onClick = { graph.controller.play(tracks, index) },
                        onLongClick = {
                            menus.show(
                                title = track.title,
                                actions = trackMenuActions(graph, scope, track) + listOf(
                                    MenuAction("remove from playlist") {
                                        scope.launch {
                                            graph.library.removeFromPlaylist(playlistId, track.mediaId)
                                            tracks = graph.library.tracksInPlaylist(playlistId)
                                        }
                                    },
                                ),
                            )
                        },
                    )
                },
            )
        }
    }
}
