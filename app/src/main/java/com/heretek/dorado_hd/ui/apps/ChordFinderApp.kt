package com.heretek.dorado_hd.ui.apps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.data.db.NoteEntity
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/* ============================== Calculator ============================== */
@Composable
fun ChordFinderApp() {
    val colors = LocalDoradoColors.current
    var root by remember { mutableStateOf("C") }
    var quality by remember { mutableStateOf("major") }
    DetailScaffold(title = "chord finder") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChordData.ROOTS.forEach { r ->
                    EdgeCropText(text = r, fontSize = DoradoTokens.TYPE_LIST.dp, color = if (r == root) colors.accent else colors.textPrimary,
                        modifier = Modifier.combinedClickable(onClick = { root = r }, onLongClick = {}))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChordData.QUALITIES.forEach { q ->
                    EdgeCropText(text = q, fontSize = DoradoTokens.TYPE_LIST.dp, color = if (q == quality) colors.accent else colors.textPrimary,
                        modifier = Modifier.combinedClickable(onClick = { quality = q }, onLongClick = {}))
                }
            }
            Spacer(Modifier.height(12.dp))
            val shape = ChordData.shapeFor(root, quality)
            if (shape != null) {
                Fretboard(shape)
            } else {
                EdgeCropText(
                    text = "no open shape for $root $quality",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun Fretboard(frets: IntArray) {
    val colors = LocalDoradoColors.current
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
        val w = size.width; val h = size.height
        val topPad = h * 0.18f
        val boardH = h - topPad
        val fretCount = 5
        val fretGap = w / fretCount
        val stringYs = FloatArray(6) { topPad + (it + 1) * boardH / 7f }
        // Fret wires plus a thicker nut at the left edge.
        for (i in 1..fretCount) {
            val x = i * fretGap
            drawLine(colors.border.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(x, topPad), androidx.compose.ui.geometry.Offset(x, h), 1f)
        }
        drawLine(colors.textSecondary, androidx.compose.ui.geometry.Offset(0f, topPad), androidx.compose.ui.geometry.Offset(0f, h), 3f)
        for (y in stringYs) {
            drawLine(colors.border, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(w, y), 1f)
        }
        for (i in 0 until minOf(frets.size, 6)) {
            val f = frets[i]
            val y = stringYs[i]
            val markerY = y - topPad * 0.7f
            when {
                f < 0 -> {
                    // Muted: X marker above the nut.
                    val s = 5f
                    drawLine(colors.textSecondary, androidx.compose.ui.geometry.Offset(4f - s, markerY - s), androidx.compose.ui.geometry.Offset(4f + s, markerY + s), 1.5f)
                    drawLine(colors.textSecondary, androidx.compose.ui.geometry.Offset(4f - s, markerY + s), androidx.compose.ui.geometry.Offset(4f + s, markerY - s), 1.5f)
                }
                f == 0 -> drawCircle(colors.textSecondary, 5f, androidx.compose.ui.geometry.Offset(4f, markerY), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                else -> drawCircle(colors.accent, 6f, androidx.compose.ui.geometry.Offset((f - 0.5f) * fretGap, y))
            }
        }
    }
}

/* ============================== Shuffle By Album ============================== */
