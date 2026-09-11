@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.heretek.dorado_hd.ui.screens

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.webkit.WebView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.heretek.dorado_hd.data.model.Rating
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.components.AlbumArt
import com.heretek.dorado_hd.design.components.CrossbarBar
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.design.components.firstLetterOf
import com.heretek.dorado_hd.design.components.KineticList
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* ============================================================ */
/*                          Videos                                */
/* ============================================================ */

data class VideoItem(val id: Long, val title: String, val artist: String, val uri: Uri, val bucket: String)

private enum class VideoPivot { ALL, MOVIES, TV, MUSIC_VIDEOS }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VideosScreen(canvasWidth: androidx.compose.ui.unit.Dp) {
    val graph = LocalDoradoGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var playing by remember { mutableStateOf<VideoItem?>(null) }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0, pageCount = { 4 })

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val list = mutableListOf<VideoItem>()
            val cursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.TITLE,
                    MediaStore.Video.Media.ARTIST,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                ),
                null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC",
            )
            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.ARTIST)
                val bucketCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                // Cap while reading: MediaProvider rejects `LIMIT` in sortOrder.
                while (list.size < 200 && c.moveToNext()) {
                    val id = c.getLong(idCol)
                    list += VideoItem(
                        id = id,
                        title = c.getString(titleCol) ?: "untitled",
                        artist = c.getString(artistCol) ?: "",
                        uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                        bucket = c.getString(bucketCol) ?: "",
                    )
                }
            }
            videos = list
        }
    }

    val cur = playing
    if (cur != null) {
        VideoPlayerScreen(cur) { playing = null }
        return
    }

    // Bucket-name heuristics — MediaStore does not reliably expose category,
    // so we partition by substring match against the bucket display name.
    val movies = videos.filter { it.bucket.contains("movie", ignoreCase = true) || it.bucket.contains("film", ignoreCase = true) }
    val tv = videos.filter { it.bucket.contains("tv", ignoreCase = true) || it.bucket.contains("show", ignoreCase = true) || it.bucket.contains("series", ignoreCase = true) }
    val music = videos.filter { it.bucket.contains("music", ignoreCase = true) && !it.bucket.contains("movie", ignoreCase = true) }
    val buckets = listOf(videos, movies, tv, music)
    val pivotLabels = listOf("all", "movies", "tv", "music videos")

    DetailScaffold(title = "videos") {
        Column(Modifier.fillMaxSize()) {
            CrossbarBar(
                labels = pivotLabels,
                selected = pagerState.currentPage,
                onSelect = { idx -> scope.launch { pagerState.animateScrollToPage(idx) } },
            )
            androidx.compose.foundation.pager.HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                VideoPivot.values()[page].let { pivot ->
                    VideoListContent(
                        videos = buckets[pivot.ordinal],
                        onPlay = { playing = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoListContent(videos: List<VideoItem>, onPlay: (VideoItem) -> Unit) {
    val graph = LocalDoradoGraph.current
    val menus = com.heretek.dorado_hd.ui.components.LocalContextMenu.current
    val scope = rememberCoroutineScope()
    if (videos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EdgeCropText(
                text = "no videos here",
                fontSize = DoradoTokens.TYPE_NOW_META.dp,
                alpha = 0.4f,
            )
        }
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
                        onClick = { onPlay(v) },
                        onLongClick = {
                            menus.show(
                                title = v.title,
                                actions = listOf(
                                    com.heretek.dorado_hd.ui.components.MenuAction("play") { onPlay(v) },
                                    com.heretek.dorado_hd.ui.components.MenuAction("pin to quickplay") {
                                        scope.launch {
                                            graph.quickplay.pin(
                                                com.heretek.dorado_hd.data.model.PinKind.VIDEO,
                                                v.id,
                                                v.title,
                                                v.uri.toString(),
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
                    EdgeCropText(text = v.title, fontSize = DoradoTokens.TYPE_LIST.dp)
                    EdgeCropText(text = v.artist, fontSize = DoradoTokens.TYPE_CAPTION.dp, color = LocalDoradoColors.current.textSecondary)
                }
            }
        },
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VideoPlayerScreen(item: VideoItem, onExit: () -> Unit) {
    val context = LocalContext.current
    val player = remember { androidx.media3.exoplayer.ExoPlayer.Builder(context).build() }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    DisposableEffect(item.id) {
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
        player.prepare()
        player.play()
        val poll = kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
            try {
                while (true) {
                    positionMs = player.currentPosition.coerceAtLeast(0)
                    durationMs = if (player.duration > 0) player.duration else 0
                    isPlaying = player.isPlaying
                    kotlinx.coroutines.delay(250)
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // cancelled by onDispose below
            }
        }
        onDispose {
            poll.cancel()
            player.release()
        }
    }
    DetailScaffold(title = "video") {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        android.view.SurfaceView(ctx).also { player.setVideoSurface(it.holder.surface) }
                    },
                )
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clickable(onClick = onExit),
                ) {
                    EdgeCropText(text = "<- back", fontSize = DoradoTokens.TYPE_LIST.dp, color = LocalDoradoColors.current.accent)
                }
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                ) {
                    BasicText(
                        text = item.title,
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = LocalDoradoColors.current.textPrimary),
                    )
                }
            }
            // Transport + scrubber bar.
            Column(Modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .clickable {
                                if (player.isPlaying) player.pause() else player.play()
                            }
                            .padding(end = 12.dp),
                    ) {
                        EdgeCropText(
                            text = if (isPlaying) "pause" else "play",
                            fontSize = DoradoTokens.TYPE_NOW_META.dp,
                            color = LocalDoradoColors.current.accent,
                        )
                    }
                    EdgeCropText(
                        text = "${positionMs / 1000}s / ${if (durationMs > 0) durationMs / 1000 else "?"}s",
                        fontSize = DoradoTokens.TYPE_CAPTION.dp,
                        color = LocalDoradoColors.current.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(LocalDoradoColors.current.tile),
                ) {
                    val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    Box(
                        Modifier
                            .height(6.dp)
                            .fillMaxWidth(frac)
                            .background(LocalDoradoColors.current.accent),
                    )
                }
            }
        }
    }
}

data class PictureBucket(val name: String, val items: List<PictureItem>)
data class PictureItem(val id: Long, val displayName: String, val uri: Uri, val dateTaken: Long)

private enum class PicturePivot { ALL, ALBUMS, DATE_TAKEN, FAVORITES }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PicturesScreen(canvasWidth: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    var buckets by remember { mutableStateOf<List<PictureBucket>>(emptyList()) }
    var flat by remember { mutableStateOf<List<PictureItem>>(emptyList()) }
    var selectedBucket by remember { mutableStateOf<PictureBucket?>(null) }
    var viewerIndex by remember { mutableStateOf(0) }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0, pageCount = { 4 })
    val pinned by graph.quickplay.pins().collectAsState(initial = emptyList())
    val favoriteItems = remember(pinned, flat) {
        pinned.filter { it.kind == com.heretek.dorado_hd.data.model.PinKind.PICTURE }
            .mapNotNull { card ->
                val uri = card.subLabel.takeIf { it.startsWith("content://") || it.startsWith("file://") }
                    ?: return@mapNotNull null
                PictureItem(
                    id = card.refId,
                    displayName = card.label,
                    uri = android.net.Uri.parse(uri),
                    dateTaken = 0L,
                )
            }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val byBucket = linkedMapOf<String, MutableList<PictureItem>>()
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN,
                ),
                null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )
            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                // Cap while reading: MediaProvider rejects `LIMIT` in sortOrder.
                while (byBucket.values.sumOf { it.size } < 500 && c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val item = PictureItem(
                        id = id,
                        displayName = c.getString(nameCol) ?: "untitled",
                        uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                        dateTaken = c.getLong(dateCol),
                    )
                    val bucket = c.getString(bucketCol) ?: "unknown"
                    byBucket.getOrPut(bucket) { mutableListOf() } += item
                }
            }
            buckets = byBucket.map { (k, v) -> PictureBucket(k, v) }
            flat = buckets.flatMap { it.items }.sortedByDescending { it.dateTaken }
        }
    }

    val viewing = selectedBucket
    if (viewing != null) {
        val pagerStateBucket = rememberPagerState(initialPage = viewerIndex.coerceAtMost(viewing.items.lastIndex), pageCount = { viewing.items.size })
        Box(Modifier.fillMaxSize().background(LocalDoradoColors.current.background)) {
            HorizontalPager(state = pagerStateBucket, modifier = Modifier.fillMaxSize()) { p ->
                val item = viewing.items[p]
                AsyncImage(
                    model = item.uri,
                    contentDescription = item.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clickable {
                        val item = viewing.items[pagerStateBucket.currentPage]
                        scope.launch { graph.quickplay.pin(com.heretek.dorado_hd.data.model.PinKind.PICTURE, item.id, item.displayName, item.uri.toString(), 0) }
                    },
            ) {
                EdgeCropText(text = "pin", fontSize = DoradoTokens.TYPE_LIST.dp, color = LocalDoradoColors.current.accent)
            }
            Box(Modifier.align(Alignment.TopStart).padding(8.dp).clickable { selectedBucket = null }) {
                EdgeCropText(text = "<- albums", fontSize = DoradoTokens.TYPE_LIST.dp, color = LocalDoradoColors.current.accent)
            }
        }
        return
    }

    DetailScaffold(title = "pictures") {
        Column(Modifier.fillMaxSize()) {
            CrossbarBar(
                labels = listOf("all", "albums", "date taken", "favorites"),
                selected = pagerState.currentPage,
                onSelect = { idx -> scope.launch { pagerState.animateScrollToPage(idx) } },
            )
            androidx.compose.foundation.pager.HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (PicturePivot.values()[page]) {
                    PicturePivot.ALL -> PictureListContent(
                        pictures = flat,
                        onPicture = { selectedBucket = PictureBucket("all", flat); viewerIndex = 0 },
                    )
                    PicturePivot.ALBUMS -> Column {
                        buckets.forEach { bucket ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(DoradoTokens.ROW_HEIGHT.dp)
                                    .clickable { selectedBucket = bucket; viewerIndex = 0 }
                                    .padding(horizontal = DoradoTokens.EDGE.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    EdgeCropText(text = bucket.name, fontSize = DoradoTokens.TYPE_LIST.dp)
                                    EdgeCropText(text = "${bucket.items.size} photos", fontSize = DoradoTokens.TYPE_CAPTION.dp, color = LocalDoradoColors.current.textSecondary)
                                }
                            }
                        }
                    }
                    PicturePivot.DATE_TAKEN -> PictureListContent(
                        pictures = flat,
                        onPicture = { selectedBucket = PictureBucket("date taken", flat); viewerIndex = 0 },
                    )
                    PicturePivot.FAVORITES -> PictureListContent(
                        pictures = favoriteItems,
                        onPicture = { /* tapping a favorite reopens in viewer via subLabel URI */ },
                    )
                }
            }
        }
    }
}

@Composable
private fun PictureListContent(pictures: List<PictureItem>, onPicture: (PictureItem) -> Unit) {
    val graph = LocalDoradoGraph.current
    val menus = com.heretek.dorado_hd.ui.components.LocalContextMenu.current
    val scope = rememberCoroutineScope()
    if (pictures.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EdgeCropText(
                text = "no pictures here",
                fontSize = DoradoTokens.TYPE_NOW_META.dp,
                alpha = 0.4f,
            )
        }
        return
    }
    KineticList(
        items = pictures,
        key = { it.id },
        letter = { firstLetterOf(it.displayName) },
        rowContent = { p, _ ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(DoradoTokens.ROW_HEIGHT.dp)
                    .combinedClickable(
                        onClick = { onPicture(p) },
                        onLongClick = {
                            menus.show(
                                title = p.displayName,
                                actions = listOf(
                                    com.heretek.dorado_hd.ui.components.MenuAction("view") { onPicture(p) },
                                    com.heretek.dorado_hd.ui.components.MenuAction("pin to quickplay") {
                                        scope.launch {
                                            graph.quickplay.pin(
                                                com.heretek.dorado_hd.data.model.PinKind.PICTURE,
                                                p.id,
                                                p.displayName,
                                                p.uri.toString(),
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
                    EdgeCropText(text = p.displayName, fontSize = DoradoTokens.TYPE_LIST.dp)
                    EdgeCropText(text = "photo", fontSize = DoradoTokens.TYPE_CAPTION.dp, color = LocalDoradoColors.current.textSecondary)
                }
            }
        },
    )
}

/*                          Internet                                */
/* ============================================================ */

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun InternetScreen(canvasWidth: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("https://duckduckgo.com") }
    var currentUrl by remember { mutableStateOf(url) }
    var input by remember(url) { mutableStateOf(url) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    val bookmarks = remember { mutableStateListOf<String>() }
    val history = remember { mutableStateListOf<String>() }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    fun navigate(to: String) {
        val candidate = if (to.startsWith("http")) to else "https://$to"
        url = candidate
        currentUrl = candidate
        input = candidate
        webViewRef.value?.loadUrl(candidate)
        if (history.lastOrNull() != candidate) history.add(candidate)
    }

    DetailScaffold(title = "internet") {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(DoradoTokens.EDGE.dp)) {
                // Back / forward in-page navigation (canon §3.6).
                Box(
                    Modifier.clickable(enabled = canBack, onClick = { webViewRef.value?.goBack() })
                        .padding(end = 6.dp),
                ) {
                    EdgeCropText(
                        text = "<",
                        fontSize = DoradoTokens.TYPE_NOW_META.dp,
                        color = if (canBack) LocalDoradoColors.current.accent else LocalDoradoColors.current.textInactive,
                    )
                }
                Box(
                    Modifier.clickable(enabled = canForward, onClick = { webViewRef.value?.goForward() })
                        .padding(end = 6.dp),
                ) {
                    EdgeCropText(
                        text = ">",
                        fontSize = DoradoTokens.TYPE_NOW_META.dp,
                        color = if (canForward) LocalDoradoColors.current.accent else LocalDoradoColors.current.textInactive,
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = LocalDoradoColors.current.textPrimary),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(LocalDoradoColors.current.accent),
                    modifier = Modifier.weight(1f).background(LocalDoradoColors.current.elevated).padding(6.dp),
                )
                Box(
                    Modifier
                        .padding(start = 6.dp)
                        .clickable { navigate(input) },
                ) {
                    EdgeCropText(text = "go", fontSize = DoradoTokens.TYPE_LIST.dp, color = LocalDoradoColors.current.accent)
                }
                Box(
                    Modifier
                        .padding(start = 6.dp)
                        .clickable {
                            navigate(currentUrl)
                            if (bookmarks.lastOrNull() != currentUrl) bookmarks.add(currentUrl)
                        },
                ) {
                    EdgeCropText(text = "*", fontSize = DoradoTokens.TYPE_LIST.dp, color = LocalDoradoColors.current.accent)
                }
            }
            AndroidView(
                modifier = Modifier.weight(1f).background(LocalDoradoColors.current.background),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        loadUrl(url)
                        webViewRef.value = this
                        // Refresh back/forward availability whenever the page changes.
                        this.webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageStarted(view: WebView?, u: String?, favicon: android.graphics.Bitmap?) {
                                canBack = view?.canGoBack() == true
                                canForward = view?.canGoForward() == true
                                u?.let { currentUrl = it; input = it }
                            }
                            override fun onPageFinished(view: WebView?, u: String?) {
                                canBack = view?.canGoBack() == true
                                canForward = view?.canGoForward() == true
                            }
                        }
                    }
                },
                update = { wv -> wv.setBackgroundColor(android.graphics.Color.BLACK) },
            )
            if (bookmarks.isNotEmpty() || history.isNotEmpty()) {
                BasicText(
                    text = "bookmarks: " + bookmarks.joinToString(" · "),
                    style = TextStyle(
                        fontFamily = Selawik,
                        fontSize = DoradoTokens.TYPE_CAPTION.sp,
                        color = LocalDoradoColors.current.textSecondary,
                    ),
                    modifier = Modifier.padding(DoradoTokens.EDGE.dp),
                )
            }
        }
    }
}

data class MockPost(val author: String, val text: String, val when_: String)

private val FROZEN_FEED = listOf(
    MockPost("zune team", "the Zune Pass lives on in our hearts. thanks for the years.", "september 2012"),
    MockPost("a fellow listener", "what are you zuning today?", "october 2012"),
    MockPost("mix master mika", "shuffled by album all morning — life-changing.", "november 2012"),
)

private data class ZuneCard(val tracks: Int, val hearts: Int, val plays: Int)

@Composable
fun SocialScreen(canvasWidth: androidx.compose.ui.unit.Dp) {
    val graph = LocalDoradoGraph.current
    val colors = LocalDoradoColors.current
    var card by remember { mutableStateOf<ZuneCard?>(null) }
    LaunchedEffect(Unit) {
        val tracks = graph.library.tracks().first()
        val ratings = graph.quickplay.ratings()
        val plays = graph.playCounts.counts()
        card = ZuneCard(
            tracks = tracks.size,
            hearts = ratings.count { it.value == Rating.HEART.value },
            plays = plays.values.sum(),
        )
    }
    DetailScaffold(title = "social") {
        Column(Modifier.fillMaxSize()) {
            // Zune Card — the device's GemUserCardScene, as a local substitute
            // for the dead Zune Social servers.
            card?.let { c ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                ) {
                    EdgeCropText(text = "zune card", fontSize = DoradoTokens.TYPE_NOW_META.dp, color = colors.accent)
                    EdgeCropText(
                        text = "${c.tracks} tracks · ${c.hearts} hearts · ${c.plays} plays",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                    )
                }
            }
            EdgeCropText(
                text = "the social feed is frozen in time.",
                fontSize = DoradoTokens.TYPE_NOW_META.dp,
                alpha = 0.6f,
                modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
            )
            FROZEN_FEED.forEach { post ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                ) {
                    EdgeCropText(text = post.author, fontSize = DoradoTokens.TYPE_NOW_META.dp, color = LocalDoradoColors.current.accent)
                    EdgeCropText(text = post.text, fontSize = DoradoTokens.TYPE_LIST.dp)
                    EdgeCropText(text = post.when_, fontSize = DoradoTokens.TYPE_CAPTION.dp, color = LocalDoradoColors.current.textSecondary)
                }
            }
        }
    }
}

@Composable
fun PictureDetailScreen(uri: String) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    DetailScaffold(title = "picture") {
        Box(Modifier.fillMaxSize().background(LocalDoradoColors.current.background)) {
            AsyncImage(
                model = android.net.Uri.parse(uri),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    // Device GemPictureTouchClient: pinch-zoom, pan and double-tap.
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale <= 1f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            },
                        )
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }
    }
}
