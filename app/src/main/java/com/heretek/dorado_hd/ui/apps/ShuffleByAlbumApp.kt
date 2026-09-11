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
fun ShuffleByAlbumApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val colors = LocalDoradoColors.current
    val albums by graph.library.albums().collectAsState(initial = emptyList())
    var pick by remember { mutableStateOf<com.heretek.dorado_hd.data.model.Album?>(null) }

    DetailScaffold(title = "shuffle by album") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EdgeCropText(
                text = "shuffle",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.accent,
                modifier = Modifier.combinedClickable(
                    onClick = {
                        if (albums.isNotEmpty()) pick = albums.random()
                    },
                    onLongClick = {},
                ),
            )
            if (albums.isEmpty()) {
                EdgeCropText(text = "no albums on device", fontSize = DoradoTokens.TYPE_NOW_META.dp, color = colors.textSecondary)
                return@Column
            }
            pick?.let { album ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    com.heretek.dorado_hd.design.components.AlbumArt(
                        model = album.albumArtUri,
                        contentDescription = album.title,
                        modifier = Modifier.size(88.dp),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        EdgeCropText(text = album.title, fontSize = DoradoTokens.TYPE_NOW_META.dp)
                        EdgeCropText(text = album.artist, fontSize = DoradoTokens.TYPE_LIST.dp, color = colors.textSecondary)
                        EdgeCropText(
                            text = "play",
                            fontSize = DoradoTokens.TYPE_LIST.dp,
                            color = colors.accent,
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    scope.launch {
                                        val tracks = graph.library.tracksByAlbum(album.albumId)
                                        if (tracks.isNotEmpty()) graph.controller.play(tracks, 0)
                                    }
                                },
                                onLongClick = {},
                            ),
                        )
                    }
                }
            }
        }
    }
}

/* ============================== Music Quiz ============================== */
