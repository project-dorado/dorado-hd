@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.heretek.dorado_hd.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.heretek.dorado_hd.data.db.PodcastEpisodeEntity
import com.heretek.dorado_hd.data.db.PodcastFeedEntity
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.design.components.KineticList
import com.heretek.dorado_hd.design.components.firstLetterOf
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import com.heretek.dorado_hd.ui.components.LocalContextMenu
import com.heretek.dorado_hd.ui.components.MenuAction
import com.heretek.dorado_hd.ui.nav.DoradoDestination
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
                        tag == "title" && inChannel -> title = parser.nextText()
                        tag == "description" || tag == "subtitle" || tag == "itunes:subtitle" -> {
                            if (inChannel) description = parser.nextText()
                        }
                        tag == "image" || tag == "itunes:image" -> {
                            artworkUrl = parser.getAttributeValue(null, "href") ?: ""
                        }
                        tag == "item" || tag == "entry" -> {
                            currentTitle = ""; currentDate = 0L; currentDur = 0L; currentEnc = ""
                        }
                        tag == "title" && inChannel.not() -> { /* per-episode below */ }
                        tag == "title" -> if (inChannel && title.isNotEmpty().not()) title = parser.nextText()
                        tag == "title" -> currentTitle = parser.nextText()
                        tag == "pubdate" || tag == "published" -> currentDate = parseDate(parser.nextText())
                        tag == "duration" -> currentDur = parseDurationMs(parser.nextText())
                        tag == "enclosure" -> currentEnc = parser.getAttributeValue(null, "url") ?: ""
                        tag == "link" && currentEnc.isEmpty() -> currentEnc = parser.getAttributeValue(null, "href") ?: ""
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.lowercase()
                    if (tag == "item" || tag == "entry") {
                        if (currentTitle.isNotEmpty() && currentEnc.isNotEmpty()) {
                            episodes += ParsedEpisode(currentTitle, currentDate, currentDur, currentEnc)
                        }
                    }
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
    suspend fun fetch(feedUrl: String): PodcastRss.Parsed? {
        return try {
            val conn = URL(feedUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "DoradoHD/0.1")
            if (conn.responseCode !in 200..299) null
            else PodcastRss.parse(conn.inputStream)
        } catch (_: Exception) { null }
    }
}

@Composable
fun PodcastsScreen(canvasWidth: androidx.compose.ui.unit.Dp) {
    val graph = LocalDoradoGraph.current
    val menus = LocalContextMenu.current
    val scope = rememberCoroutineScope()
    val feeds by graph.podcasts.feeds().collectAsState(initial = emptyList())
    val episodes by graph.podcasts.episodesFlat().collectAsState(initial = emptyList())
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0, pageCount = { 4 })

    DetailScaffold(title = "podcasts") {
        Column(Modifier.fillMaxSize()) {
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
            androidx.compose.foundation.pager.HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> PodcastsFeedList(feeds, onOpen = { feed -> graph.nav.push(DoradoDestination.PodcastFeed(feed.id)) })
                    // 'video' is a placeholder on this build — we cannot distinguish
                    // audio vs video podcasts reliably from RSS alone; surface the
                    // same feeds as audio until MediaStore tagging exists.
                    1 -> PodcastsFeedList(feeds, onOpen = { feed -> graph.nav.push(DoradoDestination.PodcastFeed(feed.id)) })
                    2 -> PodcastsFeedList(feeds, onOpen = { feed -> graph.nav.push(DoradoDestination.PodcastFeed(feed.id)) })
                    else -> PodcastEpisodeList(episodes, onPlay = { ep ->
                        scope.launch {
                            val track = com.heretek.dorado_hd.data.model.Track(
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
                            graph.controller.play(listOf(track))
                        }
                    })
                }
            }
        }
    }
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
    val feedTitle = remember(feedId) { "podcast" }

    DetailScaffold(title = feedTitle) {
        KineticList(
            items = episodes,
            key = { it.id },
            letter = { firstLetterOf(it.title) },
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
