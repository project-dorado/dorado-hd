package com.heretek.dorado_hd.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heretek.dorado_hd.data.official.OfficialApp
import com.heretek.dorado_hd.data.official.OfficialCatalog
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.DoradoMotion
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.components.AlbumArt
import com.heretek.dorado_hd.design.components.CrossbarBar
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.design.components.KineticList
import com.heretek.dorado_hd.design.components.firstLetterOf
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
    val scope = rememberCoroutineScope()
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = 0,
        pageCount = { 4 },
    )
    Column(Modifier.fillMaxSize()) {
        CrossbarBar(
            labels = listOf("music", "videos", "podcasts", "apps"),
            selected = pagerState.currentPage,
            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
        )
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> MarketplaceMusic()
                1 -> EmptyPivot("videos", "coming soon")
                2 -> EmptyPivot("podcasts", "add feeds in podcasts")
                else -> AppsPivot()
            }
        }
    }
}

@Composable
private fun MarketplaceMusic() {
    val graph = LocalDoradoGraph.current
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
        ) {
            items(featured, key = { it.albumId }) { album ->
                Column(
                    Modifier.width(DoradoTokens.APP_TILE.dp).combinedClickable(
                        onClick = { graph.nav.push(DoradoDestination.Album(album.albumId)) },
                        onLongClick = {},
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
            ) {
                items(installed, key = { it.id }) { app ->
                    Column(
                        Modifier.width(DoradoTokens.APP_TILE.dp).combinedClickable(
                            onClick = { graph.nav.push(DoradoDestination.MiniApp(app.id)) },
                            onLongClick = {},
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
    val isInstalled = entry.installedId != null
    Row(
        Modifier
            .fillMaxWidth()
            .height(DoradoTokens.ROW_HEIGHT.dp)
            .combinedClickable(
                enabled = isInstalled,
                onClick = { entry.installedId?.let { graph.nav.push(DoradoDestination.MiniApp(it)) } },
                onLongClick = {},
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
