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
fun NotesApp() {
    val graph = LocalDoradoGraph.current
    val menus = LocalContextMenu.current
    var selected by remember { mutableStateOf<NoteEntity?>(null) }
    val notes by graph.notes.notes().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    if (selected != null) {
        val s = selected!!
        var title by remember(s.id) { mutableStateOf(s.title) }
        var body by remember(s.id) { mutableStateOf(s.body) }
        DetailScaffold(
            title = "notes",
            onBack = {
                // Autosave on the way out; NonCancellable so the write is not
                // torn down with the composable after nav.pop().
                scope.launch(kotlinx.coroutines.NonCancellable) {
                    graph.notes.update(s.id, title.ifBlank { "untitled" }, body)
                }
                graph.nav.pop()
            },
        ) {
            Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                EditableLine(value = title, onChange = { title = it }, placeholder = "title")
                Spacer(Modifier.height(8.dp))
                EditableLine(value = body, onChange = { body = it }, placeholder = "body", multiLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().combinedClickable(
                    onClick = {
                        scope.launch { graph.notes.update(s.id, title.ifBlank { "untitled" }, body); selected = null }
                    },
                    onLongClick = {},
                ).padding(vertical = 8.dp)) {
                    EdgeCropText(text = "save", fontSize = DoradoTokens.TYPE_LIST.dp, color = LocalDoradoColors.current.accent)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().combinedClickable(
                    onClick = {
                        scope.launch { graph.notes.delete(s.id); selected = null }
                    },
                    onLongClick = {},
                ).padding(vertical = 8.dp)) {
                    EdgeCropText(text = "delete", fontSize = DoradoTokens.TYPE_LIST.dp, color = LocalDoradoColors.current.textInactive)
                }
            }
        }
        return
    }

    DetailScaffold(title = "notes") {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp)
                    .combinedClickable(
                        onClick = { scope.launch { graph.notes.add("untitled", "") } },
                        onLongClick = {},
                    ),
            ) {
                EdgeCropText(text = "+ new note", fontSize = DoradoTokens.TYPE_LIST.dp, color = LocalDoradoColors.current.accent)
            }
            KineticList(
                items = notes,
                key = { it.id },
                letter = { firstLetterOf(it.title) },
                rowContent = { n, _ ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(DoradoTokens.ROW_HEIGHT.dp)
                            .combinedClickable(
                                onClick = { selected = n },
                                onLongClick = {
                                    menus.show(
                                        title = n.title,
                                        actions = listOf(MenuAction("delete") { scope.launch { graph.notes.delete(n.id) } }),
                                    )
                                },
                            )
                            .padding(horizontal = DoradoTokens.EDGE.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        EdgeCropText(text = n.title, fontSize = DoradoTokens.TYPE_LIST.dp, modifier = Modifier.fillMaxWidth())
                    }
                },
            )
        }
    }
}

@Composable
fun EditableLine(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    multiLine: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDoradoColors.current
    var local by remember(value) { mutableStateOf(value) }
    Box(modifier.fillMaxWidth()) {
        androidx.compose.foundation.text.BasicTextField(
            value = local,
            onValueChange = {
                local = it
                onChange(it)
            },
            singleLine = !multiLine,
            textStyle = TextStyle(
                fontFamily = Selawik,
                fontSize = if (multiLine) DoradoTokens.TYPE_LIST.sp else DoradoTokens.TYPE_NOW_META.sp,
                color = colors.textPrimary,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.elevated)
                .padding(8.dp),
        )
        // Placeholder is overlaid inside the field, never a sibling row.
        if (local.isEmpty() && placeholder.isNotEmpty()) {
            EdgeCropText(
                text = placeholder,
                fontSize = DoradoTokens.TYPE_NOW_META.dp,
                alpha = 0.4f,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp),
            )
        }
    }
}

/* ============================== Stopwatch ============================== */
