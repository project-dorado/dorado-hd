package com.heretek.dorado_hd.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.net.LrcLibParser
import com.heretek.dorado_hd.net.Lyrics
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold

/**
 * Lyrics for the current track via LRCLIB (M9.4). A post-device extension
 * (canon §10); plain text is preferred, falling back to de-timestamped LRC.
 */
@Composable
fun LyricsScreen(canvasWidth: androidx.compose.ui.unit.Dp) {
    val graph = LocalDoradoGraph.current
    val colors = LocalDoradoColors.current
    val current by graph.controller.nowPlaying.collectAsState()
    var lyrics by remember { mutableStateOf<Lyrics?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val track = current

    LaunchedEffect(track?.mediaId) {
        lyrics = null
        loaded = false
        if (track != null) {
            lyrics = graph.lyrics.lyricsFor(
                artist = track.artist,
                title = track.title,
                album = track.album,
                durationSec = (track.durationMs / 1000).toInt(),
            )
        }
        loaded = true
    }

    DetailScaffold(title = "lyrics") {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp),
        ) {
            val text = lyrics?.plain ?: lyrics?.synced?.let { LrcLibParser.plainFromSynced(it) }
            when {
                !loaded -> EdgeCropText(
                    text = "loading…",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    alpha = 0.4f,
                    modifier = Modifier.padding(DoradoTokens.EDGE.dp),
                )
                text == null -> EdgeCropText(
                    text = "no lyrics found for “${track?.title ?: ""}”",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    alpha = 0.5f,
                    modifier = Modifier.padding(DoradoTokens.EDGE.dp),
                )
                else -> BasicText(
                    text = text,
                    style = TextStyle(
                        fontFamily = Selawik,
                        fontSize = DoradoTokens.TYPE_LIST.sp,
                        color = colors.textPrimary.copy(alpha = 0.9f),
                        lineHeight = DoradoTokens.TYPE_NOW_META.sp * 1.4f,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 12.dp),
                )
            }
        }
    }
}
