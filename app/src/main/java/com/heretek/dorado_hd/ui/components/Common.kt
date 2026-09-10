package com.heretek.dorado_hd.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoMotion
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.components.AlbumArt
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.data.model.Track
import com.heretek.dorado_hd.ui.LocalDoradoGraph

/** One action in a context menu. */
data class MenuAction(val label: String, val action: () -> Unit)

class MenuRequest(
    val title: String,
    val actions: List<MenuAction>,
)

/**
 * Zune-style long-press context menu: a dimmed scrim plus a bottom panel of
 * text actions sliding up. No Material chrome.
 */
class MenuController {
    var menu by mutableStateOf<MenuRequest?>(null)
    var prompt by mutableStateOf<PromptRequest?>(null)

    fun show(title: String, actions: List<MenuAction>) {
        menu = MenuRequest(title, actions)
    }

    fun showPrompt(title: String, placeholder: String, onConfirm: (String) -> Unit) {
        prompt = PromptRequest(title, placeholder, onConfirm)
    }

    fun dismiss() {
        menu = null
        prompt = null
    }

    class PromptRequest(val title: String, val placeholder: String, val onConfirm: (String) -> Unit)
}

val LocalContextMenu = androidx.compose.runtime.staticCompositionLocalOf<MenuController> {
    error("MenuController not provided")
}

@Composable
fun ContextMenuOverlay(controller: MenuController) {
    val colors = LocalDoradoColors.current
    val request = controller.menu
    AnimatedVisibility(
        visible = request != null,
        enter = fadeIn(DoradoMotion.pivot()),
        exit = fadeOut(DoradoMotion.pivot()),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(LocalDoradoColors.current.background.copy(alpha = 0.55f))
                .pointerInput(Unit) {
                    detectTapGestures { controller.dismiss() }
                },
        )
    }
    AnimatedVisibility(
        visible = request != null,
        enter = slideInVertically(DoradoMotion.pivot()) { it } + fadeIn(DoradoMotion.pivot()),
        exit = slideOutVertically(DoradoMotion.pivot()) { it } + fadeOut(DoradoMotion.pivot()),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.elevated)
                .border(0.5.dp, colors.border),
        ) {
            EdgeCropText(
                text = request?.title ?: "",
                fontSize = DoradoTokens.TYPE_NOW_META.dp,
                modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 10.dp),
                color = colors.textSecondary,
            )
            request?.actions?.forEach { action ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            controller.dismiss()
                            action.action()
                        }
                        .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 12.dp),
                ) {
                    EdgeCropText(text = action.label, fontSize = DoradoTokens.TYPE_LIST.dp)
                }
            }
            Spacer(Modifier.height(DoradoTokens.EDGE.dp))
        }
    }

    val prompt = controller.prompt
    AnimatedVisibility(
        visible = prompt != null,
        enter = slideInVertically(DoradoMotion.pivot()) { it } + fadeIn(DoradoMotion.pivot()),
        exit = slideOutVertically(DoradoMotion.pivot()) { it } + fadeOut(DoradoMotion.pivot()),
    ) {
        PromptPanel(controller)
    }
}

@Composable
private fun PromptPanel(controller: MenuController) {
    val colors = LocalDoradoColors.current
    var text by remember { mutableStateOf("") }
    val request = controller.prompt ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .background(colors.elevated)
            .border(0.5.dp, colors.border)
            .padding(DoradoTokens.EDGE.dp),
    ) {
        EdgeCropText(
            text = request.title,
            fontSize = DoradoTokens.TYPE_NOW_META.dp,
            color = colors.textSecondary,
        )
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = Selawik,
                fontSize = DoradoTokens.TYPE_NOW_META.sp,
                color = colors.textPrimary,
            ),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (text.isNotBlank()) {
                        val value = text
                        controller.dismiss()
                        request.onConfirm(value)
                    }
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .border(0.5.dp, colors.border)
                .padding(horizontal = 10.dp, vertical = 10.dp),
        )
    }
}

/**
 * Standard detail screen scaffold: the cropped header (back button) plus
 * content beneath.
 */
@Composable
fun DetailScaffold(
    title: String,
    content: @Composable () -> Unit,
) {
    val graph = LocalDoradoGraph.current
    Column(Modifier.fillMaxSize()) {
        com.heretek.dorado_hd.design.components.CroppedHeader(
            text = title,
            onBack = { graph.nav.pop() },
        )
        Box(Modifier.weight(1f)) {
            content()
        }
    }
}

/**
 * A track list row: title over artist — album. The currently playing track
 * renders in the accent color.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    playing: Boolean = false,
    showSubLabel: Boolean = true,
) {
    val colors = LocalDoradoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(DoradoTokens.ROW_HEIGHT.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = DoradoTokens.EDGE.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            EdgeCropText(
                text = track.title,
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = if (playing) colors.accent else colors.textPrimary,
                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (showSubLabel) {
                EdgeCropText(
                    text = "${track.artist} — ${track.album}",
                    fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/** Small section label used across quickplay and details. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val colors = LocalDoradoColors.current
    EdgeCropText(
        text = text,
        fontSize = DoradoTokens.TYPE_CROSSBAR.dp,
        alpha = 0.6f,
        modifier = modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
    )
}

@Composable
fun ArtTile(
    model: Any?,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    AlbumArt(
        model = model,
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

/** Truncation helper shared by row labels. */
fun String.ellipsize(max: Int = 60): String =
    if (length <= max) this else take(max - 1) + "…"
