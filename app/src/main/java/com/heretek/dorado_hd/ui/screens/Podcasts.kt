@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.heretek.dorado_hd.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.heretek.dorado_hd.cloud.CloudPodcastDirectory
import com.heretek.dorado_hd.data.db.PodcastEpisodeEntity
import com.heretek.dorado_hd.data.db.PodcastEpisodeFlat
import com.heretek.dorado_hd.data.db.PodcastFeedEntity
import com.heretek.dorado_hd.data.model.Track
import com.heretek.dorado_hd.data.repo.DoradoSettings
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.design.components.KineticList
import com.heretek.dorado_hd.design.components.firstLetterOf
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import com.heretek.dorado_hd.ui.components.LocalContextMenu
import com.heretek.dorado_hd.ui.components.MenuAction
import com.heretek.dorado_hd.ui.nav.DoradoDestination
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pure RSS / Atom podcast feed parser (Android's built-in XmlPullParser — no
 * new dependency). Returns null when the body is malformed; tolerates both
 * RSS 2.0 and Atom feeds by picking whichever <channel>/<feed> root is
 * present.
 */
object PodcastRss {
    data class Parsed(
        val title: String,
        val description: String,
        val artworkUrl: String,
        val episodes: List<ParsedEpisode>,
    )
    data class ParsedEpisode(
        val title: String,
        val pubAt: Long,
        val durationMs: Long,
        val enclosureUrl: String,
    )

    fun parse(stream: InputStream): Parsed? {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser().apply { setInput(stream, null) }
        var inChannel = false
        var inItem = false
        var title = ""; var description = ""; var artworkUrl = ""
        var currentTitle = ""; var currentDate = 0L; var currentDur = 0L; var currentEnc = ""
        val episodes = mutableListOf<ParsedEpisode>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.lowercase()
                    when {
                        tag == "channel" || tag == "feed" -> inChannel = true
                        tag == "item" || tag == "entry" -> {
                            inItem = true
                            currentTitle = ""; currentDate = 0L; currentDur = 0L; currentEnc = ""
                        }
                        tag == "title" -> {
                            // Scope titles: channel/feed title first, then per item.
                            val text = parser.nextText().trim()
                            if (inItem) currentTitle = text
                            else if (inChannel && title.isEmpty()) title = text
                        }
                        tag == "description" || tag == "subtitle" || tag == "itunes:subtitle" || tag == "itunes:summary" -> {
                            val text = parser.nextText().trim()
                            if (!inItem && description.isEmpty()) description = text
                        }
                        tag == "image" || tag == "itunes:image" -> {
                            val href = parser.getAttributeValue(null, "href")
                            if (!href.isNullOrBlank() && artworkUrl.isEmpty()) artworkUrl = href
                        }
                        (tag == "pubdate" || tag == "published") && inItem -> currentDate = parseDate(parser.nextText())
                        (tag == "duration" || tag == "itunes:duration") && inItem -> currentDur = parseDurationMs(parser.nextText())
                        tag == "enclosure" && inItem -> currentEnc = parser.getAttributeValue(null, "url") ?: ""
                        tag == "link" && inItem && currentEnc.isEmpty() -> currentEnc = parser.getAttributeValue(null, "href") ?: ""
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.lowercase()
                    if (tag == "item" || tag == "entry") {
                        if (currentTitle.isNotEmpty() && currentEnc.isNotEmpty()) {
                            episodes += ParsedEpisode(currentTitle, currentDate, currentDur, currentEnc)
                        }
                        inItem = false
                    }
                    if (tag == "channel" || tag == "feed") inChannel = false
                }
            }
            event = parser.next()
        }
        if (title.isEmpty() && episodes.isEmpty()) return null
        return Parsed(title.ifEmpty { "untitled feed" }, description, artworkUrl, episodes)
    }

    private fun parseDate(s: String): Long = try {
        java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.US).parse(s)?.time ?: 0L
    } catch (_: Exception) { 0L }

    private fun parseDurationMs(s: String): Long {
        val parts = s.trim().split(":")
        return try {
            when (parts.size) {
                1 -> parts[0].toLong() * 1000L
                2 -> parts[0].toLong() * 60_000L + parts[1].toLong() * 1000L
                3 -> parts[0].toLong() * 3_600_000L + parts[1].toLong() * 60_000L + parts[2].toLong() * 1000L
                else -> 0L
            }
        } catch (_: Exception) { 0L }
    }
}

object PodcastFetcher {
    suspend fun fetch(feedUrl: String): PodcastRss.Parsed? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val conn = URL(feedUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "DoradoHD/0.1")
            if (conn.responseCode !in 200..299) null
            else PodcastRss.parse(conn.inputStream)
        } catch (_: Exception) { null }
    }
}

/** Re-fetch a feed and append only episodes not already stored. */
private suspend fun refreshFeed(
    graph: com.heretek.dorado_hd.DoradoGraph,
    feed: PodcastFeedEntity,
) {
    val parsed = PodcastFetcher.fetch(feed.feedUrl) ?: return
    val existing = graph.podcasts.episodes(feed.id).first().map { it.enclosureUrl }.toSet()
    val fresh = parsed.episodes
        .filter { it.enclosureUrl.isNotBlank() && it.enclosureUrl !in existing }
        .map { e ->
            PodcastEpisodeEntity(
                feedId = feed.id,
                title = e.title,
                pubAt = e.pubAt,
                durationMs = e.durationMs,
                enclosureUrl = e.enclosureUrl,
                played = false,
                positionMs = 0,
            )
        }
    if (fresh.isNotEmpty()) graph.podcasts.addEpisodes(fresh)
}

@Composable
fun PodcastsScreen(canvasWidth: androidx.compose.ui.unit.Dp) {
    val graph = LocalDoradoGraph.current
    val menus = LocalContextMenu.current
    val scope = rememberCoroutineScope()
    val feeds by graph.podcasts.feeds().collectAsState(initial = emptyList())
    val episodes by graph.podcasts.episodesFlat().collectAsState(initial = emptyList())
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0, pageCount = { 4 })

    // M5 on-device search: subscribed library first, cloud directory when the
    // user has enabled and configured Dorado Cloud.
    val settings by graph.settingsFlow.collectAsState(initial = DoradoSettings())
    val directory = remember { CloudPodcastDirectory({ graph.settingsFlow.first() }) }
    val cloudOn = settings.cloudEnabled && settings.cloudBaseUrl.isNotBlank()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var localHits by remember { mutableStateOf<List<PodcastEpisodeFlat>>(emptyList()) }
    var cloudHits by remember { mutableStateOf<List<PodcastSearch.CloudHit>>(emptyList()) }
    var cloudBusy by remember { mutableStateOf(false) }
    var subscribing by remember { mutableStateOf(false) }
    var cloudNote by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    // Debounced, exactly like the music pivot's indexed search.
    LaunchedEffect(query, cloudOn) {
        actionError = null
        if (query.isBlank()) {
            localHits = emptyList()
            cloudHits = emptyList()
            cloudNote = null
            cloudBusy = false
        } else {
            delay(200)
            localHits = PodcastSearch.filterLocal(episodes, query)
            if (!cloudOn) {
                cloudHits = emptyList()
                cloudNote = "local results only — cloud directory off"
            } else {
                cloudBusy = true
                val result = runCatching { directory.search(query) }.getOrNull()
                cloudBusy = false
                when {
                    result == null -> {
                        cloudHits = emptyList()
                        cloudNote = "cloud directory unreachable — local results only"
                    }
                    !result.configured -> {
                        cloudHits = emptyList()
                        cloudNote = "cloud directory not configured — local results only"
                    }
                    else -> {
                        cloudHits = PodcastSearch.markSubscribed(
                            result.items,
                            PodcastSearch.subscribedFeedUrls(feeds),
                        )
                        cloudNote = result.attribution.takeIf { it.isNotBlank() } ?: "cloud directory"
                    }
                }
            }
        }
    }

    DetailScaffold(title = "podcasts") {
        Column(Modifier.fillMaxSize()) {
            if (searching) {
                PodcastSearchPanel(
                    query = query,
                    onQueryChange = { query = it },
                    onDone = {
                        searching = false
                        query = ""
                    },
                    localHits = localHits,
                    cloudHits = cloudHits,
                    cloudBusy = cloudBusy,
                    subscribing = subscribing,
                    cloudNote = cloudNote,
                    actionError = actionError,
                    onPlayLocal = { ep -> scope.launch { graph.controller.play(listOf(trackForEpisode(ep))) } },
                    onLongPressLocal = { ep ->
                        menus.show(
                            title = ep.title,
                            actions = listOf(
                                MenuAction("play") { scope.launch { graph.controller.play(listOf(trackForEpisode(ep))) } },
                                MenuAction("pin to quickplay") {
                                    scope.launch {
                                        graph.quickplay.pin(
                                            com.heretek.dorado_hd.data.model.PinKind.EPISODE,
                                            ep.episodeId,
                                            ep.title,
                                            ep.enclosureUrl,
                                            0,
                                        )
                                    }
                                },
                            ),
                        )
                    },
                    onCloudHit = { hit ->
                        scope.launch {
                            subscribing = true
                            val ok = subscribeAndPlayLatest(graph, hit)
                            subscribing = false
                            actionError = if (ok) null else "couldn't reach that feed — try again"
                        }
                    },
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp)
                        .clickable {
                                menus.showPrompt("add feed", "https://example.com/feed.xml") { url ->
                                    scope.launch {
                                        val parsed = PodcastFetcher.fetch(url)
                                        if (parsed != null) {
                                            val feedId = graph.podcasts.addFeed(
                                                PodcastFeedEntity(
                                                    title = parsed.title,
                                                    feedUrl = url,
                                                    artworkUrl = parsed.artworkUrl,
                                                    description = parsed.description,
                                                    subscribedAt = System.currentTimeMillis(),
                                                ),
                                            )
                                            graph.podcasts.addEpisodes(
                                                parsed.episodes.map { e ->
                                                    PodcastEpisodeEntity(
                                                        feedId = feedId,
                                                        title = e.title,
                                                        pubAt = e.pubAt,
                                                        durationMs = e.durationMs,
                                                        enclosureUrl = e.enclosureUrl,
                                                        played = false,
                                                        positionMs = 0,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                        },
                ) {
                    EdgeCropText(text = "+ add feed by url", fontSize = DoradoTokens.TYPE_LIST.dp, color = LocalDoradoColors.current.accent)
                }
                com.heretek.dorado_hd.design.components.CrossbarBar(
                    labels = listOf("audio", "video", "subscriptions", "episodes"),
                    selected = pagerState.currentPage,
                    onSelect = { idx -> scope.launch { pagerState.animateScrollToPage(idx) } },
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .padding(horizontal = DoradoTokens.EDGE.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    EdgeCropText(
                        text = "search",
                        fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
                        color = LocalDoradoColors.current.accent,
                        modifier = Modifier.clickable { searching = true },
                    )
                }
                androidx.compose.foundation.pager.HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    when (page) {
                        0 -> PodcastsFeedList(feeds, onOpen = { feed -> graph.nav.push(DoradoDestination.PodcastFeed(feed.id)) })
                        // RSS cannot reliably distinguish audio/video enclosures in
                        // this build — say so instead of duplicating the audio list.
                        1 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EdgeCropText(
                                text = "video podcasts are not supported in this build",
                                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                                alpha = 0.4f,
                            )
                        }
                        2 -> PodcastsFeedList(feeds, onOpen = { feed -> graph.nav.push(DoradoDestination.PodcastFeed(feed.id)) })
                        else -> PodcastEpisodeList(episodes, onPlay = { ep ->
                            scope.launch { graph.controller.play(listOf(trackForEpisode(ep))) }
                        })
                    }
                }
            }
        }
    }
}

/** The podcast pivot's search surface: typing, results, cloud labels. */
@Composable
private fun PodcastSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    onDone: () -> Unit,
    localHits: List<PodcastEpisodeFlat>,
    cloudHits: List<PodcastSearch.CloudHit>,
    cloudBusy: Boolean,
    subscribing: Boolean,
    cloudNote: String?,
    actionError: String?,
    onPlayLocal: (PodcastEpisodeFlat) -> Unit,
    onLongPressLocal: (PodcastEpisodeFlat) -> Unit,
    onCloudHit: (PodcastSearch.CloudHit) -> Unit,
) {
    val colors = LocalDoradoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(DoradoTokens.HEADER_HEIGHT.dp)
            .padding(horizontal = DoradoTokens.EDGE.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
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
            modifier = Modifier.clickable { onDone() },
        )
    }
    when {
        query.isBlank() -> EdgeCropText(
            text = "type to search",
            fontSize = DoradoTokens.TYPE_LIST.dp,
            alpha = 0.4f,
            modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp),
        )
        localHits.isEmpty() && cloudHits.isEmpty() && !cloudBusy && !subscribing -> Column(
            Modifier.padding(horizontal = DoradoTokens.EDGE.dp),
        ) {
            EdgeCropText(text = "no results", fontSize = DoradoTokens.TYPE_LIST.dp, alpha = 0.4f)
            cloudNote?.let {
                EdgeCropText(
                    text = it,
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = colors.textSecondary,
                    alpha = 0.6f,
                )
            }
        }
        else -> LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            items(localHits, key = { "local:${it.episodeId}" }) { ep ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(DoradoTokens.ROW_HEIGHT.dp)
                        .combinedClickable(
                            onClick = { onPlayLocal(ep) },
                            onLongClick = { onLongPressLocal(ep) },
                        )
                        .padding(horizontal = DoradoTokens.EDGE.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        EdgeCropText(text = ep.title, fontSize = DoradoTokens.TYPE_LIST.dp)
                        EdgeCropText(
                            text = ep.feedTitle,
                            fontSize = DoradoTokens.TYPE_CAPTION.dp,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
            if (cloudHits.isNotEmpty()) {
                item {
                    EdgeCropText(
                        text = cloudNote ?: "cloud directory",
                        fontSize = DoradoTokens.TYPE_CAPTION.dp,
                        color = colors.accent,
                        modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
                    )
                }
                items(
                    cloudHits,
                    key = { "cloud:" + it.feed.feedUrl.ifBlank { it.feed.feedId } },
                ) { hit ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(DoradoTokens.ROW_HEIGHT.dp)
                            .clickable { onCloudHit(hit) }
                            .padding(horizontal = DoradoTokens.EDGE.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            EdgeCropText(text = hit.feed.title, fontSize = DoradoTokens.TYPE_LIST.dp)
                            EdgeCropText(
                                text = if (hit.subscribed) {
                                    "subscribed · tap to play latest"
                                } else {
                                    "tap to subscribe + play latest"
                                },
                                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            } else if (cloudNote != null) {
                item {
                    EdgeCropText(
                        text = cloudNote,
                        fontSize = DoradoTokens.TYPE_CAPTION.dp,
                        color = colors.textSecondary,
                        alpha = 0.6f,
                        modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
                    )
                }
            }
            if (cloudBusy) {
                item {
                    EdgeCropText(
                        text = "searching cloud directory…",
                        fontSize = DoradoTokens.TYPE_CAPTION.dp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
                    )
                }
            }
            if (subscribing) {
                item {
                    EdgeCropText(
                        text = "loading feed…",
                        fontSize = DoradoTokens.TYPE_CAPTION.dp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
                    )
                }
            }
            actionError?.let { message ->
                item {
                    EdgeCropText(
                        text = message,
                        fontSize = DoradoTokens.TYPE_CAPTION.dp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** Episode → playable Track, the mapping the podcast pivots already use. */
private fun trackForEpisode(ep: PodcastEpisodeFlat): Track = Track(
    mediaId = ep.episodeId,
    title = ep.title,
    artist = "",
    artistId = 0,
    album = ep.feedTitle,
    albumId = 0,
    genre = "podcast",
    durationMs = ep.durationMs,
    dateAdded = ep.pubAt,
    trackNumber = 0,
    year = "",
    uri = android.net.Uri.parse(ep.enclosureUrl),
)

/**
 * Cloud directory hit: subscribe (when new) and play the newest episode.
 * Returns false on a transport failure so the panel can say so.
 */
private suspend fun subscribeAndPlayLatest(
    graph: com.heretek.dorado_hd.DoradoGraph,
    hit: PodcastSearch.CloudHit,
): Boolean {
    val feedUrl = hit.feed.feedUrl.trim()
    if (feedUrl.isEmpty()) return false
    val normalized = PodcastSearch.normalizeFeedUrl(feedUrl)
    val existing = graph.podcasts.feeds().first()
        .firstOrNull { PodcastSearch.normalizeFeedUrl(it.feedUrl) == normalized }

    val feedId: Long
    val feedTitle: String
    if (existing != null) {
        feedId = existing.id
        feedTitle = existing.title
    } else {
        val parsed = PodcastFetcher.fetch(feedUrl) ?: return false
        feedId = graph.podcasts.addFeed(
            PodcastFeedEntity(
                title = parsed.title.ifBlank { hit.feed.title },
                feedUrl = feedUrl,
                artworkUrl = parsed.artworkUrl.ifBlank { hit.feed.imageUrl },
                description = parsed.description.ifBlank { hit.feed.description },
                subscribedAt = System.currentTimeMillis(),
            ),
        )
        feedTitle = parsed.title.ifBlank { hit.feed.title }
        graph.podcasts.addEpisodes(
            parsed.episodes.map { e ->
                PodcastEpisodeEntity(
                    feedId = feedId,
                    title = e.title,
                    pubAt = e.pubAt,
                    durationMs = e.durationMs,
                    enclosureUrl = e.enclosureUrl,
                    played = false,
                    positionMs = 0,
                )
            },
        )
    }

    val latest = graph.podcasts.episodes(feedId).first().firstOrNull() ?: return false
    val track = Track(
        mediaId = latest.id,
        title = latest.title,
        artist = "",
        artistId = 0,
        album = feedTitle,
        albumId = 0,
        genre = "podcast",
        durationMs = latest.durationMs,
        dateAdded = latest.pubAt,
        trackNumber = 0,
        year = "",
        uri = android.net.Uri.parse(latest.enclosureUrl),
    )
    graph.controller.play(listOf(track), 0)
    return true
}

@Composable
private fun PodcastsFeedList(
    feeds: List<com.heretek.dorado_hd.data.db.PodcastFeedEntity>,
    onOpen: (com.heretek.dorado_hd.data.db.PodcastFeedEntity) -> Unit,
) {
    val menus = com.heretek.dorado_hd.ui.components.LocalContextMenu.current
    val scope = rememberCoroutineScope()
    val graph = com.heretek.dorado_hd.ui.LocalDoradoGraph.current
    if (feeds.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EdgeCropText(text = "no feeds yet", fontSize = DoradoTokens.TYPE_NOW_META.dp, alpha = 0.4f)
        }
        return
    }
    com.heretek.dorado_hd.design.components.KineticList(
        items = feeds,
        key = { it.id },
        letter = { com.heretek.dorado_hd.design.components.firstLetterOf(it.title) },
        bottomPadding = 36.dp,
        rowContent = { feed, _ ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(DoradoTokens.ROW_HEIGHT.dp)
                    .combinedClickable(
                        onClick = { onOpen(feed) },
                        onLongClick = {
                            menus.show(
                                title = feed.title,
                                actions = listOf(
                                    com.heretek.dorado_hd.ui.components.MenuAction("refresh") {
                                        scope.launch { refreshFeed(graph, feed) }
                                    },
                                    com.heretek.dorado_hd.ui.components.MenuAction("pin to quickplay") {
                                        scope.launch {
                                            graph.quickplay.pin(
                                                com.heretek.dorado_hd.data.model.PinKind.PODCAST,
                                                feed.id,
                                                feed.title,
                                                feed.description.take(60),
                                                0,
                                            )
                                        }
                                    },
                                    com.heretek.dorado_hd.ui.components.MenuAction("remove") {
                                        scope.launch { graph.podcasts.deleteFeed(feed.id) }
                                    },
                                ),
                            )
                        },
                    )
                    .padding(horizontal = DoradoTokens.EDGE.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    EdgeCropText(text = feed.title, fontSize = DoradoTokens.TYPE_LIST.dp)
                    EdgeCropText(text = feed.description.take(60), fontSize = DoradoTokens.TYPE_CAPTION.dp, color = LocalDoradoColors.current.textSecondary)
                }
            }
        },
    )
}

@Composable
private fun PodcastEpisodeList(
    episodes: List<com.heretek.dorado_hd.data.db.PodcastEpisodeFlat>,
    onPlay: (com.heretek.dorado_hd.data.db.PodcastEpisodeFlat) -> Unit,
) {
    val graph = com.heretek.dorado_hd.ui.LocalDoradoGraph.current
    val menus = com.heretek.dorado_hd.ui.components.LocalContextMenu.current
    val scope = rememberCoroutineScope()
    if (episodes.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EdgeCropText(text = "no episodes", fontSize = DoradoTokens.TYPE_NOW_META.dp, alpha = 0.4f)
        }
        return
    }
    com.heretek.dorado_hd.design.components.KineticList(
        items = episodes,
        key = { it.episodeId },
        letter = { com.heretek.dorado_hd.design.components.firstLetterOf(it.title) },
        bottomPadding = 36.dp,
        rowContent = { ep, _ ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(DoradoTokens.ROW_HEIGHT.dp)
                    .combinedClickable(
                        onClick = { onPlay(ep) },
                        onLongClick = {
                            menus.show(
                                title = ep.title,
                                actions = listOf(
                                    com.heretek.dorado_hd.ui.components.MenuAction("play") { onPlay(ep) },
                                    com.heretek.dorado_hd.ui.components.MenuAction("pin to quickplay") {
                                        scope.launch {
                                            graph.quickplay.pin(
                                                com.heretek.dorado_hd.data.model.PinKind.EPISODE,
                                                ep.episodeId,
                                                ep.title,
                                                ep.enclosureUrl,
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
                    EdgeCropText(text = ep.title, fontSize = DoradoTokens.TYPE_LIST.dp)
                    EdgeCropText(text = ep.feedTitle, fontSize = DoradoTokens.TYPE_CAPTION.dp, color = LocalDoradoColors.current.textSecondary)
                }
            }
        },
    )
}

@Composable
fun PodcastFeedScreen(feedId: Long, canvasWidth: androidx.compose.ui.unit.Dp) {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val menus = LocalContextMenu.current
    val episodes by graph.podcasts.episodes(feedId).collectAsState(initial = emptyList())
    val feeds by graph.podcasts.feeds().collectAsState(initial = emptyList())
    // Resolve the real feed title instead of a hardcoded "podcast".
    val feedTitle = feeds.firstOrNull { it.id == feedId }?.title ?: "podcast"

    if (episodes.isEmpty()) {
        DetailScaffold(title = feedTitle) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EdgeCropText(text = "no episodes", fontSize = DoradoTokens.TYPE_NOW_META.dp, alpha = 0.4f)
            }
        }
        return
    }

    DetailScaffold(title = feedTitle) {
        KineticList(
            items = episodes,
            key = { it.id },
            letter = { firstLetterOf(it.title) },
            bottomPadding = 36.dp,
            rowContent = { ep, _ ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(DoradoTokens.ROW_HEIGHT.dp)
                        .combinedClickable(
                            onClick = {
                                scope.launch {
                                    val track = com.heretek.dorado_hd.data.model.Track(
                                mediaId = ep.id,
                                        title = ep.title,
                                        artist = "",
                                        artistId = 0,
                                        album = feedTitle,
                                        albumId = 0,
                                        genre = "podcast",
                                        durationMs = ep.durationMs,
                                        dateAdded = ep.pubAt,
                                        trackNumber = 0,
                                        year = "",
                                        uri = android.net.Uri.parse(ep.enclosureUrl),
                                    )
                                    graph.controller.play(listOf(track))
                                }
                            },
                            onLongClick = {
                                menus.show(
                                    title = ep.title,
                                    actions = listOf(
                                        com.heretek.dorado_hd.ui.components.MenuAction("pin to quickplay") {
                                            scope.launch {
                                                graph.quickplay.pin(
                                                    com.heretek.dorado_hd.data.model.PinKind.EPISODE,
                                                    ep.id,
                                                    ep.title,
                                                    ep.enclosureUrl,
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
                        EdgeCropText(text = ep.title, fontSize = DoradoTokens.TYPE_LIST.dp)
                        EdgeCropText(text = formatDuration(ep.durationMs), fontSize = DoradoTokens.TYPE_CAPTION.dp, color = LocalDoradoColors.current.textSecondary)
                    }
                }
            },
        )
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val s = ms / 1000; val m = s / 60; val h = m / 60
    return when {
        h > 0 -> "%d:%02d:%02d".format(h, m % 60, s % 60)
        else -> "%d:%02d".format(m, s % 60)
    }
}
