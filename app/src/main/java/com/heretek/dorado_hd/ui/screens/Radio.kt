@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.heretek.dorado_hd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.data.db.RadioStationEntity
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
import kotlinx.coroutines.launch

/** Dial frequency format, e.g. 98700 kHz → "98.7 MHz". */
fun formatDial(freqKhz: Int): String =
    if (freqKhz <= 0) "stream" else "%.1f MHz".format(freqKhz / 1000.0)

/** Step a frequency by a drag delta (px → kHz). 4 px ≈ 10 kHz. */
fun stepFrequency(freqKhz: Int, deltaPx: Float): Int =
    (freqKhz + (deltaPx.toInt() / 4) * 10).coerceIn(87500, 108000)

@Composable
fun RadioScreen(canvasWidth: androidx.compose.ui.unit.Dp) {
    val graph = LocalDoradoGraph.current
    val menus = LocalContextMenu.current
    val scope = rememberCoroutineScope()
    val stations by graph.radio.stations().collectAsState(initial = emptyList())
    var current by remember { mutableStateOf<RadioStationEntity?>(stations.firstOrNull()) }
    var dialKhz by remember { mutableStateOf(current?.frequencyKhz ?: 98700) }

    DetailScaffold(title = "radio") {
        Column(Modifier.fillMaxSize()) {
            val colors = LocalDoradoColors.current
            // Dial sits at the top across all pivots.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp)
                    .height(DoradoTokens.DIAL_HEIGHT.dp)
                    .background(colors.elevated),
            ) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    val freq = dialKhz.coerceIn(87500, 108000)
                    val frac = (freq - 87500).toFloat() / (108000 - 87500).toFloat()
                    val cx = w * (1f - frac)
                    drawLine(colors.border, Offset(0f, h / 2), Offset(w, h / 2), 1f)
                    for (mhz in 87..108) {
                        val f = (mhz - 87) / (108 - 87).toFloat()
                        val xx = w * f
                        val tickLen = if (mhz % 5 == 0) DoradoTokens.DIAL_TICK_MAJOR.dp.toPx() else DoradoTokens.DIAL_TICK_MINOR.dp.toPx()
                        drawLine(colors.border, Offset(xx, h / 2 - tickLen / 2), Offset(xx, h / 2 + tickLen / 2), 1f)
                    }
                    val cy = h / 2
                    val needle = h / 2
                    drawLine(colors.accent, Offset(cx - needle, cy), Offset(cx, cy - 6), 2f)
                    drawLine(colors.accent, Offset(cx, cy + 6), Offset(cx + needle, cy), 2f)
                    drawCircle(colors.accent, 5f, Offset(cx, cy))
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(dialKhz) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                dialKhz = stepFrequency(dialKhz, -drag.x)
                            }
                        },
                )
                BasicText(
                    text = formatDial(dialKhz),
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.textPrimary),
                    modifier = Modifier.align(Alignment.BottomStart).padding(DoradoTokens.EDGE.dp / 2),
                )
            }
            val fmStations = stations.filter { !it.isPreset && it.frequencyKhz > 0 }
            val hdStations = stations.filter { it.isPreset }
            // 'presets' pivot = top 5 most-recently-played (canon §3.6).
            val presetStations = stations
                .filter { it.lastPlayedAt > 0 }
                .sortedByDescending { it.lastPlayedAt }
                .take(5)
            val pivotBuckets = listOf(fmStations, hdStations, presetStations)
            val pivotLabels = listOf("FM", "HD", "presets")
            val radioPagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0, pageCount = { 3 })
            com.heretek.dorado_hd.design.components.CrossbarBar(
                labels = pivotLabels,
                selected = radioPagerState.currentPage,
                onSelect = { i -> scope.launch { radioPagerState.animateScrollToPage(i) } },
            )
            androidx.compose.foundation.pager.HorizontalPager(state = radioPagerState, modifier = Modifier.fillMaxSize()) { page ->
                val pivotList = pivotBuckets.getOrNull(page) ?: emptyList()
                Column(Modifier.fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = DoradoTokens.EDGE.dp, vertical = 4.dp)) {
                        EdgeCropText(
                            text = "tune in",
                            fontSize = DoradoTokens.TYPE_LIST.dp,
                            color = LocalDoradoColors.current.accent,
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    val match = stations.firstOrNull { it.frequencyKhz == dialKhz }
                                        ?: stations.firstOrNull { it.streamUrl.isNotBlank() }
                                    if (match != null) {
                                        current = match
                                        playStation(graph, match)
                                        scope.launch { graph.radio.touch(match.id) }
                                    }
                                },
                                onLongClick = {},
                            ),
                        )
                        Spacer(Modifier.weight(1f))
                        EdgeCropText(
                            text = "+ station",
                            fontSize = DoradoTokens.TYPE_LIST.dp,
                            color = LocalDoradoColors.current.accent,
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    menus.showPrompt("add station", "name|url") { text ->
                                        val parts = text.split("|")
                                        if (parts.size >= 2) {
                                            scope.launch {
                                                graph.radio.add(
                                                    RadioStationEntity(
                                                        name = parts[0].trim().ifBlank { "stream" },
                                                        frequencyKhz = dialKhz,
                                                        streamUrl = parts[1].trim(),
                                                        isPreset = false,
                                                        lastPlayedAt = 0,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                },
                                onLongClick = {},
                            ),
                        )
                    }
                    RadioStationList(
                        stations = pivotList,
                        current = current,
                        onStation = { s ->
                            current = s
                            dialKhz = s.frequencyKhz.coerceAtLeast(87500)
                            playStation(graph, s)
                            scope.launch { graph.radio.touch(s.id) }
                        },
                        onLongPress = { s ->
                            menus.show(
                                title = s.name,
                                actions = listOf(
                                    MenuAction("remove") {
                                        scope.launch { graph.radio.delete(s.id) }
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioStationList(
    stations: List<RadioStationEntity>,
    current: RadioStationEntity?,
    onStation: (RadioStationEntity) -> Unit,
    onLongPress: (RadioStationEntity) -> Unit,
) {
    if (stations.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EdgeCropText(
                text = "no stations here",
                fontSize = DoradoTokens.TYPE_NOW_META.dp,
                alpha = 0.4f,
            )
        }
        return
    }
    KineticList(
        items = stations,
        key = { it.id },
        letter = { firstLetterOf(it.name) },
        rowContent = { s, _ ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(DoradoTokens.ROW_HEIGHT.dp)
                    .combinedClickable(
                        onClick = { onStation(s) },
                        onLongClick = { onLongPress(s) },
                    )
                    .padding(horizontal = DoradoTokens.EDGE.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    EdgeCropText(text = s.name, fontSize = DoradoTokens.TYPE_LIST.dp, color = if (s.id == current?.id) LocalDoradoColors.current.accent else LocalDoradoColors.current.textPrimary)
                    EdgeCropText(text = formatDial(s.frequencyKhz), fontSize = DoradoTokens.TYPE_CAPTION.dp, color = LocalDoradoColors.current.textSecondary)
                }
            }
        },
    )
}

private fun playStation(graph: com.heretek.dorado_hd.DoradoGraph, station: RadioStationEntity) {
    val track = com.heretek.dorado_hd.data.model.Track(
        mediaId = station.id,
        title = station.name,
        artist = "radio",
        artistId = 0,
        album = "radio",
        albumId = 0,
        genre = "radio",
        durationMs = 0,
        dateAdded = 0,
        trackNumber = 0,
        year = "",
        uri = android.net.Uri.parse(station.streamUrl),
    )
    graph.controller.play(listOf(track))
}
