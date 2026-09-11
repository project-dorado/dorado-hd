package com.heretek.dorado_hd.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoMotion
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.components.AlbumArt
import com.heretek.dorado_hd.design.components.CrossbarBar
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.data.model.PinKind
import com.heretek.dorado_hd.data.model.Track
import com.heretek.dorado_hd.design.components.KineticList
import com.heretek.dorado_hd.design.components.firstLetterOf
import com.heretek.dorado_hd.design.components.rememberZuneFlingBehavior
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import com.heretek.dorado_hd.ui.components.LocalContextMenu
import com.heretek.dorado_hd.ui.components.MenuAction
import com.heretek.dorado_hd.ui.components.TrackRow
import com.heretek.dorado_hd.ui.components.trackMenuActions
import com.heretek.dorado_hd.ui.nav.DoradoDestination
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Real Zune HD music crossbar order. */
private val PIVOTS = listOf("albums", "artists", "playlists", "songs", "genres")

@Composable
fun MusicScreen(canvasWidth: androidx.compose.ui.unit.Dp) {
    val graph = LocalDoradoGraph.current
    val colors = LocalDoradoColors.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { PIVOTS.size })
    val selected = pagerState.currentPage

    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }

    // Debounced indexed search (device zcontent_serv content service).
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
        } else {
            delay(200)
            results = graph.library.search(query)
        }
    }

    DetailScaffold(title = "music") {
        Column(Modifier.fillMaxSize()) {
            if (searching) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(DoradoTokens.HEADER_HEIGHT.dp)
                        .padding(horizontal = DoradoTokens.EDGE.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = Selawik,
                            fontSize = DoradoTokens.TYPE_LIST.sp,
                            color = colors.textPrimary,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    EdgeCropText(
                        text = "done",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = colors.accent,
                        modifier = Modifier.clickable {
                            searching = false
                            query = ""
                            results = emptyList()
                        },
                    )
                }
                when {
                    query.isBlank() -> EdgeCropText(
                        text = "type to search",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        alpha = 0.4f,
                        modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp),
                    )
                    results.isEmpty() -> EdgeCropText(
                        text = "no results",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        alpha = 0.4f,
                        modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp),
                    )
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(results, key = { index, t -> "$index:${t.mediaId}" }) { index, track ->
                            TrackRow(
                                track = track,
                                onClick = { graph.controller.play(results, index) },
                                onLongClick = {},
                            )
                        }
                    }
                }
            } else {
                CrossbarBar(
                    labels = PIVOTS,
                    selected = selected,
                    onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .padding(horizontal = DoradoTokens.EDGE.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EdgeCropText(
                        text = "search",
                        fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
                        color = colors.accent,
                        modifier = Modifier.clickable { searching = true },
                    )
                }
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (page) {
                        0 -> AlbumsTab()
                        1 -> ArtistsTab()
                        2 -> PlaylistsTab()
                        3 -> SongsTab()
                        else -> GenresTab()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumsTab() {
    val graph = LocalDoradoGraph.current
    val menus = LocalContextMenu.current
    val scope = rememberCoroutineScope()
    val albums by graph.library.albums().collectAsState(initial = emptyList())

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = DoradoTokens.ALBUM_TILE.dp),
        contentPadding = PaddingValues(DoradoTokens.EDGE.dp),
        horizontalArrangement = Arrangement.spacedBy(DoradoTokens.GRID_GUTTER.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
        flingBehavior = rememberZuneFlingBehavior(),
    ) {
        items(albums, key = { it.albumId }) { album ->
            Column(
                Modifier.combinedClickable(
                    onClick = { graph.nav.push(DoradoDestination.Album(album.albumId)) },
                    onLongClick = {
                        menus.show(
                            title = album.title,
                            actions = listOf(
                                MenuAction("pin to quickplay") {
                                    scope.launch {
                                        graph.quickplay.pin(
                                            PinKind.ALBUM, album.albumId, album.title, album.artist, album.albumId,
                                        )
                                    }
                                },
                                MenuAction("view artist") {
                                    graph.nav.push(DoradoDestination.Artist(album.artistId))
                                },
                            ),
                        )
                    },
                ),
            ) {
                AlbumArt(
                    model = album.albumArtUri,
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxWidth(),
                )
                EdgeCropText(
                    text = album.title,
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                EdgeCropText(
                    text = album.artist,
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
private fun ArtistsTab() {
    val graph = LocalDoradoGraph.current
    val menus = LocalContextMenu.current
    val scope = rememberCoroutineScope()
    val artists by graph.library.artists().collectAsState(initial = emptyList())

    KineticList(
        items = artists,
        key = { it.artistId },
        letter = { firstLetterOf(it.name) },
        rowContent = { artist, _ ->
            Row(
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    EdgeCropText(text = artist.name, fontSize = DoradoTokens.TYPE_LIST.dp)
                    EdgeCropText(
                        text = "${artist.albumCount} albums — ${artist.trackCount} songs",
                        fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
                        color = LocalDoradoColors.current.textSecondary,
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistsTab() {
    val graph = LocalDoradoGraph.current
    val menus = LocalContextMenu.current
    val scope = rememberCoroutineScope()
    val playlists by graph.library.playlists().collectAsState(initial = emptyList())

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        com.heretek.dorado_hd.design.components.KineticList(
            items = playlists,
            key = { it.id },
            letter = { firstLetterOf(it.name) },
            rowContent = { playlist, _ ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(DoradoTokens.ROW_HEIGHT.dp)
                        .combinedClickable(
                            onClick = { graph.nav.push(DoradoDestination.PlaylistDetail(playlist.id)) },
                            onLongClick = {
                                menus.show(
                                    title = playlist.name,
                                actions = listOf(
                                    MenuAction("pin to quickplay") {
                                        scope.launch {
                                            graph.quickplay.pin(PinKind.PLAYLIST, playlist.id, playlist.name, "${playlist.trackCount} songs", 0)
                                        }
                                    },
                                    MenuAction("delete playlist") {
                                        scope.launch { graph.library.deletePlaylist(playlist.id) }
                                    },
                                ),
                                )
                            },
                        )
                        .padding(horizontal = DoradoTokens.EDGE.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        EdgeCropText(text = playlist.name, fontSize = DoradoTokens.TYPE_LIST.dp)
                        EdgeCropText(
                            text = "${playlist.trackCount} songs",
                            fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
                            color = LocalDoradoColors.current.textSecondary,
                        )
                    }
                }
            },
        )

        // Create playlist affordance, styled as plain text like everything else.
        EdgeCropText(
            text = "+ new playlist",
            fontSize = DoradoTokens.TYPE_LIST.dp,
            alpha = 0.6f,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .clickable {
                    menus.showPrompt("new playlist", "name") { name ->
                        scope.launch { graph.library.createPlaylist(name) }
                    }
                }
                .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 12.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongsTab() {
    val graph = LocalDoradoGraph.current
    val menus = LocalContextMenu.current
    val scope = rememberCoroutineScope()
    val songs by graph.library.tracks().collectAsState(initial = emptyList())
    val nowPlayingId by graph.controller.nowPlaying.collectAsState()
    val playlists by graph.library.playlists().collectAsState(initial = emptyList())

    KineticList(
        items = songs,
        key = { it.mediaId },
        letter = { firstLetterOf(it.title) },
        bottomPadding = 96.dp,
        snap = true,
        rowContent = { track, index ->
            TrackRow(
                track = track,
                playing = nowPlayingId?.mediaId == track.mediaId,
                onClick = { graph.controller.play(songs, index) },
                onLongClick = {
                    menus.show(
                        title = track.title,
                        actions = trackMenuActions(graph, scope, track) + listOf(
                            MenuAction("add to playlist") {
                                menus.show(
                                    title = "add to playlist",
                                    actions = playlists.map { playlist ->
                                        MenuAction(playlist.name) {
                                            scope.launch { graph.library.addToPlaylist(playlist.id, track) }
                                        }
                                    } + MenuAction("new playlist…") {
                                        menus.showPrompt("new playlist", "name") { name ->
                                            scope.launch {
                                                val id = graph.library.createPlaylist(name)
                                                graph.library.addToPlaylist(id, track)
                                            }
                                        }
                                    },
                                )
                            },
                            MenuAction("view album") { graph.nav.push(DoradoDestination.Album(track.albumId)) },
                            MenuAction("view artist") { graph.nav.push(DoradoDestination.Artist(track.artistId)) },
                        ),
                    )
                },
            )
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GenresTab() {
    val graph = LocalDoradoGraph.current
    val menus = LocalContextMenu.current
    val scope = rememberCoroutineScope()
    val genres by graph.library.genres().collectAsState(initial = emptyList())

    KineticList(
        items = genres,
        key = { it.name },
        letter = { firstLetterOf(it.name) },
        rowContent = { genre, _ ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(DoradoTokens.ROW_HEIGHT.dp)
                    .combinedClickable(
                        onClick = { graph.nav.push(DoradoDestination.Genre(genre.name)) },
                        onLongClick = {
                            menus.show(
                                title = genre.name,
                                actions = listOf(
                                    MenuAction("pin to quickplay") {
                                        scope.launch {
                                            graph.quickplay.pin(
                                                PinKind.GENRE,
                                                PinKind.stableId(genre.name),
                                                genre.name,
                                                "${genre.trackCount} songs",
                                                0,
                                            )
                                        }
                                    },
                                ),
                            )
                        },
                    )
                    .padding(horizontal = DoradoTokens.EDGE.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    EdgeCropText(text = genre.name, fontSize = DoradoTokens.TYPE_LIST.dp)
                    EdgeCropText(
                        text = "${genre.trackCount} songs",
                        fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
                        color = LocalDoradoColors.current.textSecondary,
                    )
                }
            }
        },
    )
}
