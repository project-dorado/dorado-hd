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
fun CalculatorApp() {
    val colors = LocalDoradoColors.current
    var state by remember { mutableStateOf(CalculatorState()) }
    val tilt = rememberTilt()
    var explicitLandscape by remember { mutableStateOf<Boolean?>(null) }
    val tiltLandscape = tilt.value.available && abs(tilt.value.rollDeg) > 45f
    val landscape = explicitLandscape ?: tiltLandscape

    val onKey: (String) -> Unit = { key ->
        val next = state.press(key, landscape)
        state = next
        when (key) {
            "sci" -> explicitLandscape = next.landscape
            "basic" -> {
                explicitLandscape = false
                state = state.copy(landscape = false)
            }
        }
    }

    DetailScaffold(title = "calculator") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            CalcDisplay(state, landscape)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.border),
            )
            Spacer(Modifier.height(6.dp))
            if (landscape) {
                LandscapeKeypad(state, onKey, Modifier.weight(1f))
            } else {
                Keypad(PORTRAIT_KEYS, onKey, Modifier.weight(1f))
            }
        }
    }
}

/** Right-aligned value plus a status line (angle / memory / pending operand). */
@Composable
private fun CalcDisplay(state: CalculatorState, landscape: Boolean) {
    val colors = LocalDoradoColors.current
    Column(Modifier.fillMaxWidth()) {
        BasicText(
            text = state.render(),
            style = TextStyle(
                fontFamily = Selawik,
                fontWeight = FontWeight.Light,
                fontSize = (if (landscape) DoradoTokens.TYPE_NOW_TITLE else DoradoTokens.TYPE_HEADER_CROPPED).sp,
                color = if (state.error) colors.textInactive else colors.textPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        val memo = buildString {
            append(state.angle.name.lowercase())
            if (state.memory != 0.0) append("  m ${CalculatorState.formatValue(state.memory)}")
            state.pendingOp?.let { append("  $it") }
            if (state.population.isNotEmpty()) {
                append("  n=${state.population.size}")
            }
        }
        EdgeCropText(text = memo, fontSize = DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
        if (landscape && state.population.isNotEmpty()) {
            EdgeCropText(
                text = state.population.takeLast(8).joinToString("  ") { CalculatorState.formatValue(it, true) },
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = colors.textInactive,
            )
        }
    }
}

@Composable
private fun Keypad(rows: List<List<String>>, onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                row.forEach { key ->
                    KeyButton(key, Modifier.weight(1f).fillMaxHeight(), onKey)
                }
            }
        }
    }
}

/** Scientific panel: fn cycles four pages, numeric pad is shared below. */
@Composable
private fun LandscapeKeypad(state: CalculatorState, onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    val page = SCI_FN_PAGES[state.page.coerceIn(0, SCI_FN_PAGES.lastIndex)]
    val rows = page + LANDSCAPE_NUMERIC + listOf(LANDSCAPE_CONTROLS)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                row.forEach { key ->
                    KeyButton(key, Modifier.weight(1f).fillMaxHeight(), onKey)
                }
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, modifier: Modifier = Modifier, onClick: (String) -> Unit) {
    val colors = LocalDoradoColors.current
    val accent = label in ACCENT_KEYS
    Box(
        modifier
            .background(if (accent) colors.elevated else Color.Transparent)
            .combinedClickable(onClick = { onClick(label) }, onLongClick = {})
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                fontFamily = Selawik,
                fontSize = DoradoTokens.TYPE_LIST.sp,
                color = if (accent) colors.accent else colors.textPrimary,
            ),
        )
    }
}

private val ACCENT_KEYS = setOf("=", "C", "AC", "sci", "basic", "fn")

private val PORTRAIT_KEYS = listOf(
    listOf("MC", "M+", "MR", "±"),
    listOf("7", "8", "9", "/"),
    listOf("4", "5", "6", "*"),
    listOf("1", "2", "3", "-"),
    listOf("0", ".", "(", ")"),
    listOf("+", "%", "C", "del"),
    listOf("AC", "sci", "="),
)

private val LANDSCAPE_NUMERIC = listOf(
    listOf("7", "8", "9", "/"),
    listOf("4", "5", "6", "*"),
    listOf("1", "2", "3", "-"),
    listOf("0", ".", "+", "="),
)

private val LANDSCAPE_CONTROLS = listOf("AC", "C", "del", "fn", "basic")

/** Four scientific pages, twelve keys each (trig / log / root-power / stats). */
private val SCI_FN_PAGES: List<List<List<String>>> = listOf(
    listOf(
        listOf("sin", "cos", "tan", "asin"),
        listOf("acos", "atan", "deg", "rad"),
        listOf("grad", "pi", "2pi", "halfpi"),
    ),
    listOf(
        listOf("ln", "log", "logy", "e"),
        listOf("exp", "tenx", "twox", "rand"),
        listOf("inv", "fact", "%", "clearPop"),
    ),
    listOf(
        listOf("sqrt", "cbrt", "sq", "cube"),
        listOf("yroot", "^", "(", ")"),
        listOf("inv", "fact", "%", "rand"),
    ),
    listOf(
        listOf("pop", "clearPop", "sum", "mean"),
        listOf("count", "stdev", "pi", "e"),
        listOf("2pi", "halfpi", "rand", "%"),
    ),
)
