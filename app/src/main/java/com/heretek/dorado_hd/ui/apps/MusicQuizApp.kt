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
fun MusicQuizApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val tracks by graph.library.tracks().collectAsState(initial = emptyList())
    var question by remember { mutableStateOf<QuizEngine.Question?>(null) }
    var correct by remember { mutableStateOf(0) }
    var wrong by remember { mutableStateOf(0) }
    var exhausted by remember { mutableStateOf(false) }

    LaunchedEffect(tracks) {
        if (question == null && !exhausted) {
            question = QuizEngine.buildQuestion(tracks)
            if (question == null && tracks.isNotEmpty()) exhausted = true
        }
    }

    DetailScaffold(title = "music quiz") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            val q = question
            if (q == null) {
                EdgeCropText(
                    text = if (exhausted) "need at least 4 artists or albums to quiz" else "loading…",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    alpha = 0.6f,
                )
                if (exhausted) {
                    Spacer(Modifier.height(8.dp))
                    EdgeCropText(text = "correct $correct — wrong $wrong", fontSize = DoradoTokens.TYPE_CAPTION.dp, alpha = 0.6f)
                }
                return@Column
            }
            BasicText(
                text = q.prompt,
                style = TextStyle(
                    fontFamily = Selawik,
                    fontWeight = FontWeight.Light,
                    fontSize = DoradoTokens.TYPE_NOW_TITLE.sp,
                    color = LocalDoradoColors.current.textPrimary,
                ),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            q.options.forEachIndexed { i, opt ->
                EdgeCropText(
                    text = "${i + 1}. $opt",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = LocalDoradoColors.current.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (opt == q.answer) {
                                    correct++
                                } else {
                                    wrong++
                                }
                                val next = QuizEngine.buildQuestion(tracks)
                                if (next == null) exhausted = true
                                question = next
                            },
                            onLongClick = {},
                        )
                        .padding(vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            EdgeCropText(text = "correct $correct — wrong $wrong", fontSize = DoradoTokens.TYPE_CAPTION.dp, alpha = 0.6f)
        }
    }
}

/* ============================== Alarm Clock ============================== */

/** Stable PendingIntent request code from a Long alarm id (no truncation). */
