package com.heretek.dorado_hd.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.heretek.dorado_hd.DoradoGraph
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.media.PlaybackController
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.components.AlbumArt
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.design.components.HomeMenuItem
import com.heretek.dorado_hd.design.components.KineticList
import com.heretek.dorado_hd.design.components.StaggerEntrance
import com.heretek.dorado_hd.design.components.firstLetterOf
import com.heretek.dorado_hd.data.model.PinKind
import com.heretek.dorado_hd.data.repo.QuickplayCard
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.LocalContextMenu
import com.heretek.dorado_hd.ui.components.SectionLabel
import com.heretek.dorado_hd.ui.nav.DoradoDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The Zune HD home: two pages side by side. The home text menu sits on the
 * right; Quickplay is parked to its left ("left and to the rear, in a bit of
 * visual 3D trickery" — canon §3.2). The 3D effect combines a slight
 * rotationY (camera tilt) and a depth translation so the rear page appears
 * to recede behind the front page, anchored at 0.6x the frame speed.
 */
@Composable
fun HomePages(canvasWidth: Dp) {
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 2 })
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val behind = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            .coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    if (page == 0) {
                        translationX = behind * 0.4f * size.width
                        // 3D parallax: tilt the rear page so it appears angled away from the viewer.
                        rotationY = -behind * 12f
                        cameraDistance = 12f * density
                        val depth = 0.94f + 0.06f * (1f - behind)
                        scaleX = depth
                        scaleY = depth
                        alpha = 0.85f + 0.15f * (1f - behind)
                    }
                },
        ) {
            if (page == 0) QuickplayScreen(canvasWidth) else HomeMenuScreen(canvasWidth)
        }
    }
}

/** The home menu: 9 entries listed canonically, in a kinetic scrolling column. */
private data class HomeEntry(val id: String, val label: String, val destination: DoradoDestination)

private val HOME_ENTRIES = listOf(
    HomeEntry("music", "music", DoradoDestination.Music),
    HomeEntry("videos", "videos", DoradoDestination.Videos),
    HomeEntry("pictures", "pictures", DoradoDestination.Pictures),
    HomeEntry("radio", "radio", DoradoDestination.Radio),
    HomeEntry("marketplace", "marketplace", DoradoDestination.Marketplace),
    HomeEntry("social", "social", DoradoDestination.Social),
    HomeEntry("podcasts", "podcasts", DoradoDestination.Podcasts),
    HomeEntry("internet", "internet", DoradoDestination.Internet),
    HomeEntry("settings", "settings", DoradoDestination.Settings),
)

@Composable
fun HomeMenuScreen(canvasWidth: Dp) {
    val graph = LocalDoradoGraph.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(48.dp))
            // The kinetic list of menu entries. The right-edge alphabet rail
            // (canon §3.5) comes for free via KineticList.
            KineticList(
                items = HOME_ENTRIES,
                key = { it.id },
                letter = { firstLetterOf(it.label) },
                modifier = Modifier.weight(1f),
                rowContent = { entry, _ ->
                    HomeMenuItem(
                        label = entry.label,
                        onClick = { graph.nav.push(entry.destination) },
                    )
                },
            )
            // Bottom watermark — canon §2 (textWatermark 0.08).
            EdgeCropText(
                text = "dorado hd",
                fontSize = DoradoTokens.TYPE_CROSSBAR.dp,
                alpha = 0.08f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = DoradoTokens.EDGE.dp, bottom = DoradoTokens.EDGE.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickplayScreen(canvasWidth: Dp) {
    val graph = LocalDoradoGraph.current
    val controller = graph.controller
    val scope = rememberCoroutineScope()
    val menus = LocalContextMenu.current

    val nowPlaying by controller.nowPlaying.collectAsState()
    val pins by graph.quickplay.pins().collectAsState(initial = emptyList())
    val history by graph.quickplay.history(12).collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(DoradoTokens.EDGE.dp))
        SectionLabel("now playing")

        val track = nowPlaying
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .combinedClickable(
                    onClick = { if (track != null) graph.nav.push(DoradoDestination.NowPlaying) },
                    onLongClick = {},
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (track != null) {
                AlbumArt(
                    model = track.albumArtUri,
                    contentDescription = track.album,
                    modifier = Modifier
                        .padding(start = DoradoTokens.EDGE.dp)
                        .size(64.dp),
                )
                Column(Modifier.padding(start = 12.dp)) {
                    EdgeCropText(text = track.title, fontSize = DoradoTokens.TYPE_NOW_META.dp)
                    EdgeCropText(
                        text = track.artist,
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = LocalDoradoColors.current.textSecondary,
                    )
                }
            } else {
                EdgeCropText(
                    text = "nothing playing",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    alpha = 0.4f,
                    modifier = Modifier.padding(start = DoradoTokens.EDGE.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        // Smart DJ lane (canon §4): tap "play mix" to queue a 25-track
        // smart-DJ order — hearts first, broken skipped. This is the
        // on-device manifestation of Smart DJ.
        SectionLabel("smart dj")
        SmartDjRow(graph, scope)

        Spacer(Modifier.height(8.dp))
        SectionLabel("pins")
        QuickplayRow(graph, scope, cards = pins)

        Spacer(Modifier.height(8.dp))
        SectionLabel("history")
        QuickplayRow(graph, scope, cards = history)

        Spacer(Modifier.height(8.dp))
        SectionLabel("new")
        NewRow()
    }
}

/** Build and queue a Smart DJ mix on the controller. Pulls the full library,
 *  sorts hearts-first / broken-skipped via [PlaybackController.smartShuffleOrder],
 *  caps at 25 tracks. */
@Composable
private fun SmartDjRow(graph: DoradoGraph, scope: CoroutineScope) {
    val colors = LocalDoradoColors.current
    var building by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(DoradoTokens.ROW_HEIGHT.dp)
            .combinedClickable(
                enabled = !building,
                onClick = {
                    building = true
                    scope.launch {
                        try {
                            val tracks = graph.library.tracks().first()
                            val ratings = graph.quickplay.ratings()
                            val trackList = tracks.take(60)
                            val current = graph.controller.nowPlaying.value
                            val ordered = PlaybackController.smartShuffleOrder(trackList, ratings, current)
                            val mix = ordered.take(25)
                            if (mix.isNotEmpty()) graph.controller.play(mix, 0)
                        } finally {
                            building = false
                        }
                    }
                },
                onLongClick = {},
            )
            .padding(horizontal = DoradoTokens.EDGE.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EdgeCropText(
            text = if (building) "building mix…" else "play smart dj mix",
            fontSize = DoradoTokens.TYPE_NOW_META.dp,
            color = colors.accent,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickplayRow(graph: DoradoGraph, scope: CoroutineScope, cards: List<QuickplayCard>) {
    val menus = LocalContextMenu.current
    if (cards.isEmpty()) {
        EdgeCropText(
            text = "empty",
            fontSize = DoradoTokens.TYPE_LIST.dp,
            alpha = 0.4f,
            modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 4.dp),
        )
        return
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = DoradoTokens.EDGE.dp),
        horizontalArrangement = Arrangement.spacedBy(DoradoTokens.GRID_GUTTER.dp),
    ) {
        items(cards, key = { "${it.kind}:${it.refId}:${it.label}" }) { card ->
            Column(
                Modifier
                    .width(72.dp)
                    .combinedClickable(
                        onClick = { openCard(graph, scope, card) },
                        onLongClick = {
                            menus.show(
                                title = card.label,
                                actions = listOf(
                                    com.heretek.dorado_hd.ui.components.MenuAction("unpin") {
                                        scope.launch { graph.quickplay.unpin(card.kind, card.refId) }
                                    },
                                ),
                            )
                        },
                    ),
            ) {
                AlbumArt(
                    model = card.artAlbumId.takeIf { it > 0 }
                        ?.let { android.net.Uri.parse("content://media/external/audio/albumart/$it") },
                    contentDescription = card.label,
                    modifier = Modifier.size(72.dp),
                )
                EdgeCropText(
                    text = card.label,
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewRow() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val albums by graph.library.albums().collectAsState(initial = emptyList())
    val recent = albums.sortedByDescending { it.dateAdded }.take(12)
    LazyRow(
        contentPadding = PaddingValues(horizontal = DoradoTokens.EDGE.dp),
        horizontalArrangement = Arrangement.spacedBy(DoradoTokens.GRID_GUTTER.dp),
    ) {
        items(recent, key = { it.albumId }) { album ->
            Column(
                Modifier
                    .width(72.dp)
                    .combinedClickable(
                        onClick = { graph.nav.push(DoradoDestination.Album(album.albumId)) },
                        onLongClick = {},
                    ),
            ) {
                AlbumArt(
                    model = album.albumArtUri,
                    contentDescription = album.title,
                    modifier = Modifier.size(72.dp),
                )
                EdgeCropText(
                    text = album.title,
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun openCard(graph: DoradoGraph, scope: CoroutineScope, card: QuickplayCard) {
    when (card.kind) {
        PinKind.TRACK -> scope.launch {
            graph.library.track(card.refId)?.let { graph.controller.play(listOf(it)) }
        }
        PinKind.ALBUM -> graph.nav.push(DoradoDestination.Album(card.refId))
        PinKind.ARTIST -> graph.nav.push(DoradoDestination.Artist(card.refId))
        PinKind.PLAYLIST -> graph.nav.push(DoradoDestination.PlaylistDetail(card.refId))
        PinKind.PICTURE -> {
            val uri = card.subLabel.takeIf { it.startsWith("content://") || it.startsWith("file://") }
            if (uri != null) graph.nav.push(DoradoDestination.PictureDetail(uri))
        }
        PinKind.RADIO -> graph.nav.push(DoradoDestination.Radio)
    }
}
