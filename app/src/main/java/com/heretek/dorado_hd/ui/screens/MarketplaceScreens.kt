package com.heretek.dorado_hd.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.heretek.dorado_hd.data.official.OfficialApp
import com.heretek.dorado_hd.data.official.OfficialCatalog
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoMotion
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.components.AlbumArt
import com.heretek.dorado_hd.design.components.CrossbarBar
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.design.components.KineticList
import com.heretek.dorado_hd.design.components.firstLetterOf
import com.heretek.dorado_hd.design.components.rememberZuneFlingBehavior
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.apps.MiniAppScaffold
import com.heretek.dorado_hd.ui.apps.DoradoApps
import com.heretek.dorado_hd.ui.components.DetailScaffold
import com.heretek.dorado_hd.ui.nav.DoradoDestination
import kotlinx.coroutines.launch

/**
 * The Zune marketplace shell (canon §3.6, §8): a crossbar pivoting over
 * music / videos / podcasts / apps. The `apps` pivot lists every installed
 * mini-app plus the frozen official catalog (unavailable entries dim).
 */
@Composable
fun MarketplaceScreen(canvasWidth: androidx.compose.ui.unit.Dp) {
    val graph = LocalDoradoGraph.current
    val colors = LocalDoradoColors.current
    val scope = rememberCoroutineScope()
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = 0,
        pageCount = { 5 },
    )
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<OfficialApp?>(null) }

    detail?.let { app ->
        MarketplaceDetails(app) { detail = null }
        return
    }

    DetailScaffold(title = "marketplace") {
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
                        },
                    )
                }
                val results = remember(query) { OfficialCatalog.search(query) }
                when {
                    query.isBlank() -> EdgeCropText(
                        text = "search the catalog",
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
                        items(results) { app ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { detail = app }
                                    .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
                            ) {
                                EdgeCropText(
                                    text = app.title,
                                    fontSize = DoradoTokens.TYPE_LIST.dp,
                                )
                                EdgeCropText(
                                    text = app.category,
                                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                                    color = colors.accent,
                                )
                            }
                        }
                    }
                }
            } else {
                CrossbarBar(
                    labels = listOf("music", "videos", "podcasts", "apps", "games"),
                    selected = pagerState.currentPage,
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
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (page) {
                        0 -> MarketplaceMusic()
                        1 -> MarketplaceVideos()
                        2 -> MarketplacePodcasts()
                        3 -> AppsPivot()
                        else -> GamesPivot()
                    }
                }
            }
        }
    }
}

/** Catalog entry details (device GemMarketplaceDetailsScene). */
@Composable
private fun MarketplaceDetails(app: OfficialApp, onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = app.title) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = DoradoTokens.EDGE.dp),
        ) {
            EdgeCropText(text = app.title, fontSize = DoradoTokens.TYPE_NOW_TITLE.dp)
            EdgeCropText(
                text = app.category,
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = colors.accent,
            )
            EdgeCropText(
                text = app.description,
                fontSize = DoradoTokens.TYPE_LIST.dp,
                alpha = 0.85f,
                modifier = Modifier.padding(top = 8.dp),
            )
            EdgeCropText(
                text = if (app.installedId != null) {
                    "installed — canonical re-implementation"
                } else {
                    "unavailable — frozen marketplace catalog"
                },
                fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun MarketplaceVideos() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val graph = LocalDoradoGraph.current
    val menus = com.heretek.dorado_hd.ui.components.LocalContextMenu.current
    val scope = rememberCoroutineScope()
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val list = mutableListOf<VideoItem>()
            val cursor = context.contentResolver.query(
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    android.provider.MediaStore.Video.Media._ID,
                    android.provider.MediaStore.Video.Media.TITLE,
                    android.provider.MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                ),
                null, null,
                "${android.provider.MediaStore.Video.Media.DATE_ADDED} DESC",
            )
            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.TITLE)
                val bucketCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                // Cap while reading: MediaProvider rejects `LIMIT` in sortOrder.
                while (list.size < 50 && c.moveToNext()) {
                    val id = c.getLong(idCol)
                    list += VideoItem(
                        id = id,
                        title = c.getString(titleCol) ?: "untitled",
                        artist = "",
                        uri = android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id,
                        ),
                        bucket = c.getString(bucketCol) ?: "",
                    )
                }
            }
            videos = list
        }
    }
    if (videos.isEmpty()) {
        EmptyPivot("videos", "no videos on device")
        return
    }
    KineticList(
        items = videos,
        key = { it.id },
        letter = { firstLetterOf(it.title) },
        rowContent = { v, _ ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(DoradoTokens.ROW_HEIGHT.dp)
                    .combinedClickable(
                        onClick = { graph.nav.push(DoradoDestination.Video(v.title, v.uri.toString())) },
                        onLongClick = {
                            menus.show(
                                title = v.title,
                                actions = listOf(
                                    com.heretek.dorado_hd.ui.components.MenuAction("play") {
                                        graph.nav.push(DoradoDestination.Video(v.title, v.uri.toString()))
                                    },
                                    com.heretek.dorado_hd.ui.components.MenuAction("pin to quickplay") {
                                        scope.launch {
                                            graph.quickplay.pin(
                                                com.heretek.dorado_hd.data.model.PinKind.VIDEO,
                                                v.id, v.title, v.uri.toString(), 0,
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
                EdgeCropText(text = v.title, fontSize = DoradoTokens.TYPE_LIST.dp)
            }
        },
    )
}

@Composable
private fun MarketplacePodcasts() {
    val graph = LocalDoradoGraph.current
    val feeds by graph.podcasts.feeds().collectAsState(initial = emptyList())
    if (feeds.isEmpty()) {
        EmptyPivot("podcasts", "add feeds in podcasts")
        return
    }
    KineticList(
        items = feeds,
        key = { it.id },
        letter = { firstLetterOf(it.title) },
        rowContent = { feed, _ ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(DoradoTokens.ROW_HEIGHT.dp)
                    .clickable { graph.nav.push(DoradoDestination.PodcastFeed(feed.id)) }
                    .padding(horizontal = DoradoTokens.EDGE.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    EdgeCropText(text = feed.title, fontSize = DoradoTokens.TYPE_LIST.dp)
                    EdgeCropText(
                        text = "${feed.description.take(48)}",
                        fontSize = DoradoTokens.TYPE_CAPTION.dp,
                        color = LocalDoradoColors.current.textSecondary,
                    )
                }
            }
        },
    )
}

@Composable
private fun GamesPivot() {
    val graph = LocalDoradoGraph.current
    val installed = DoradoApps.all.filter { it.category == "games" }
    val catalogGames = OfficialCatalog.all.filter { it.category == OfficialCatalog.GAMES }
    Column(Modifier.fillMaxSize()) {
        if (installed.isNotEmpty()) {
            EdgeCropText(
                text = "installed",
                fontSize = DoradoTokens.TYPE_CROSSBAR.dp,
                alpha = 0.6f,
                modifier = Modifier.padding(start = DoradoTokens.EDGE.dp, top = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = DoradoTokens.EDGE.dp),
                horizontalArrangement = Arrangement.spacedBy(DoradoTokens.GRID_GUTTER.dp),
                flingBehavior = rememberZuneFlingBehavior(frameRetention = DoradoMotion.KINETIC_LANE_FRAME_RETENTION),
            ) {
                items(installed, key = { it.id }) { app ->
                    Column(
                        Modifier.width(DoradoTokens.APP_TILE.dp).clickable {
                            graph.nav.push(DoradoDestination.MiniApp(app.id))
                        },
                    ) {
                        AlbumArt(
                            model = null,
                            contentDescription = app.title,
                            modifier = Modifier.size(DoradoTokens.APP_TILE.dp),
                        )
                        EdgeCropText(text = app.title, fontSize = DoradoTokens.TYPE_CAPTION.dp, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        EdgeCropText(
            text = "frozen catalog",
            fontSize = DoradoTokens.TYPE_CROSSBAR.dp,
            alpha = 0.6f,
            modifier = Modifier.padding(start = DoradoTokens.EDGE.dp),
        )
        KineticList(
            items = catalogGames,
            key = { it.exe },
            letter = { firstLetterOf(it.title) },
            rowContent = { entry, _ -> AppsCatalogRow(entry) },
        )
    }
}

@Composable
private fun MarketplaceMusic() {
    val graph = LocalDoradoGraph.current
    val menus = com.heretek.dorado_hd.ui.components.LocalContextMenu.current
    val scope = rememberCoroutineScope()
    val albums by graph.library.albums().collectAsState(initial = emptyList())
    val featured = remember(albums) { albums.take(12) }
    Column(Modifier.fillMaxSize().padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp)) {
        EdgeCropText(
            text = "featured",
            fontSize = DoradoTokens.TYPE_CROSSBAR.dp,
            alpha = 0.6f,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(DoradoTokens.GRID_GUTTER.dp),
            flingBehavior = rememberZuneFlingBehavior(frameRetention = DoradoMotion.KINETIC_LANE_FRAME_RETENTION),
        ) {
            items(featured, key = { it.albumId }) { album ->
                Column(
                    Modifier.width(DoradoTokens.APP_TILE.dp).combinedClickable(
                        onClick = { graph.nav.push(DoradoDestination.Album(album.albumId)) },
                        onLongClick = {
                            menus.show(
                                title = album.title,
                                actions = listOf(
                                    com.heretek.dorado_hd.ui.components.MenuAction("pin to quickplay") {
                                        scope.launch {
                                            graph.quickplay.pin(
                                                com.heretek.dorado_hd.data.model.PinKind.ALBUM,
                                                album.albumId, album.title, album.artist, album.albumId,
                                            )
                                        }
                                    },
                                ),
                            )
                        },
                    ),
                ) {
                    AlbumArt(
                        model = album.albumArtUri,
                        contentDescription = album.title,
                        modifier = Modifier.size(DoradoTokens.APP_TILE.dp),
                    )
                    EdgeCropText(text = album.title, fontSize = DoradoTokens.TYPE_CAPTION.dp, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun EmptyPivot(label: String, note: String) {
    Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EdgeCropText(text = label, fontSize = DoradoTokens.TYPE_NOW_META.dp, alpha = 0.4f)
        EdgeCropText(text = note, fontSize = DoradoTokens.TYPE_LIST.dp, alpha = 0.4f)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppsPivot() {
    val graph = LocalDoradoGraph.current
    val menus = com.heretek.dorado_hd.ui.components.LocalContextMenu.current
    val scope = rememberCoroutineScope()
    val installed = DoradoApps.all
    val catalog = OfficialCatalog.all
    Column(Modifier.fillMaxSize()) {
        if (installed.isNotEmpty()) {
            EdgeCropText(
                text = "installed",
                fontSize = DoradoTokens.TYPE_CROSSBAR.dp,
                alpha = 0.6f,
                modifier = Modifier.padding(start = DoradoTokens.EDGE.dp, top = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = DoradoTokens.EDGE.dp),
                horizontalArrangement = Arrangement.spacedBy(DoradoTokens.GRID_GUTTER.dp),
                flingBehavior = rememberZuneFlingBehavior(frameRetention = DoradoMotion.KINETIC_LANE_FRAME_RETENTION),
            ) {
                items(installed, key = { it.id }) { app ->
                    Column(
                        Modifier.width(DoradoTokens.APP_TILE.dp).combinedClickable(
                            onClick = { graph.nav.push(DoradoDestination.MiniApp(app.id)) },
                            onLongClick = {
                                menus.show(
                                    title = app.title,
                                    actions = listOf(
                                        com.heretek.dorado_hd.ui.components.MenuAction("pin to quickplay") {
                                            scope.launch {
                                                graph.quickplay.pin(
                                                    com.heretek.dorado_hd.data.model.PinKind.APP,
                                                    com.heretek.dorado_hd.data.model.PinKind.stableId(app.id),
                                                    app.title, app.id, 0,
                                                )
                                            }
                                        },
                                    ),
                                )
                            },
                        ),
                    ) {
                        AlbumArt(
                            model = null,
                            contentDescription = app.title,
                            modifier = Modifier.size(DoradoTokens.APP_TILE.dp),
                        )
                        EdgeCropText(text = app.title, fontSize = DoradoTokens.TYPE_CAPTION.dp, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        EdgeCropText(
            text = "frozen catalog",
            fontSize = DoradoTokens.TYPE_CROSSBAR.dp,
            alpha = 0.6f,
            modifier = Modifier.padding(start = DoradoTokens.EDGE.dp),
        )
        KineticList(
            items = catalog,
            key = { it.exe },
            letter = { firstLetterOf(it.title) },
            rowContent = { entry, _ ->
                AppsCatalogRow(entry)
            },
        )
    }
}

@Composable
private fun AppsCatalogRow(entry: OfficialApp) {
    val graph = LocalDoradoGraph.current
    val colors = LocalDoradoColors.current
    val menus = com.heretek.dorado_hd.ui.components.LocalContextMenu.current
    val scope = rememberCoroutineScope()
    val isInstalled = entry.installedId != null
    Row(
        Modifier
            .fillMaxWidth()
            .height(DoradoTokens.ROW_HEIGHT.dp)
            .combinedClickable(
                enabled = isInstalled,
                onClick = { entry.installedId?.let { graph.nav.push(DoradoDestination.MiniApp(it)) } },
                onLongClick = {
                    entry.installedId?.let { id ->
                        menus.show(
                            title = entry.title,
                            actions = listOf(
                                com.heretek.dorado_hd.ui.components.MenuAction("pin to quickplay") {
                                    scope.launch {
                                        graph.quickplay.pin(
                                            com.heretek.dorado_hd.data.model.PinKind.APP,
                                            com.heretek.dorado_hd.data.model.PinKind.stableId(id),
                                            entry.title, id, 0,
                                        )
                                    }
                                },
                            ),
                        )
                    }
                },
            )
            .padding(horizontal = DoradoTokens.EDGE.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            EdgeCropText(
                text = entry.title,
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = if (isInstalled) colors.textPrimary else colors.textInactive,
            )
            EdgeCropText(
                text = entry.category,
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = colors.textSecondary,
            )
        }
        EdgeCropText(
            text = if (isInstalled) "open" else "unavailable",
            fontSize = DoradoTokens.TYPE_CAPTION.dp,
            color = if (isInstalled) colors.accent else colors.textInactive,
        )
    }
}

/** Mini-app host screen: looks up the renderer and shows the mini-app fullscreen. */
@Composable
fun MiniAppScreen(appId: String, canvasWidth: androidx.compose.ui.unit.Dp) {
    val graph = LocalDoradoGraph.current
    val app = remember(appId) { DoradoApps.byId(appId) }
    if (app == null) {
        DetailScaffold(title = "app") {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EdgeCropText(
                    text = "unavailable",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    alpha = 0.4f,
                )
            }
        }
        return
    }
    MiniAppScaffold(title = app.title, onBack = { graph.nav.pop() }) {
        app.render()
    }
}
