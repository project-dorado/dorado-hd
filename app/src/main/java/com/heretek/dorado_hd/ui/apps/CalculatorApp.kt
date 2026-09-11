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
    var expr by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Double?>(null) }
    var scientific by remember { mutableStateOf(false) }

    val rows = if (scientific) SCIENTIFIC_KEYS else BASIC_KEYS

    DetailScaffold(title = "calculator") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = result?.let { formatNumber(it) } ?: expr.ifEmpty { "0" },
                style = TextStyle(
                    fontFamily = Selawik,
                    fontWeight = FontWeight.Light,
                    fontSize = DoradoTokens.TYPE_NOW_TITLE.sp,
                    color = if (result != null) colors.accent else colors.textPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.border),
            )
            Spacer(Modifier.height(6.dp))
            // Weighted rows so every keypad row (basic or scientific) always
            // fits the 224dp device-mode content area; nothing is clipped.
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                rows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        row.forEach { key ->
                            KeyButton(key, Modifier.weight(1f).fillMaxHeight()) { label ->
                                when (label) {
                                    "C" -> { expr = ""; result = null }
                                    "=" -> {
                                        val v = CalcEngine.eval(expr)
                                        result = v
                                        expr = v?.let { formatNumber(it) } ?: expr
                                    }
                                    "±" -> {
                                        // Toggle the sign of the last operand without
                                        // corrupting a preceding subtraction: "5-3" →
                                        // "5-(-3)" and "5-(-3)" → "5-(3)".
                                        val num = Regex("\\d+(?:\\.\\d+)?$").find(expr)
                                        if (num != null) {
                                            val start = num.range.first
                                            val raw = num.value
                                            val prev = expr.getOrNull(start - 1)
                                            val beforeMinus = if (prev == '-') expr.getOrNull(start - 2) else null
                                            val minusIsSign = prev == '-' &&
                                                (beforeMinus == null || beforeMinus in "+-*/^(")
                                            expr = when {
                                                minusIsSign -> expr.removeRange(start - 1, start)
                                                prev == '-' -> expr.substring(0, start) + "(-" + raw + ")"
                                                else -> expr.substring(0, start) + "-" + raw
                                            }
                                            result = null
                                        }
                                    }
                                    "sci" -> scientific = !scientific
                                    "del" -> { if (expr.isNotEmpty()) expr = expr.dropLast(1); result = null }
                                    else -> { expr += label; result = null }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatNumber(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else "%.6f".format(v).trimEnd('0').trimEnd('.')

private val BASIC_KEYS = listOf(
    listOf("7", "8", "9", "/"),
    listOf("4", "5", "6", "*"),
    listOf("1", "2", "3", "-"),
    listOf("0", ".", "(", ")"),
    listOf("+", "±", "C", "del", "="),
    listOf("sci"),
)

private val SCIENTIFIC_KEYS = listOf(
    listOf("7", "8", "9", "/"),
    listOf("4", "5", "6", "*"),
    listOf("1", "2", "3", "-"),
    listOf("0", ".", "(", ")"),
    listOf("+", "±", "C", "del", "="),
    listOf("sci"),
    listOf("sin", "cos", "tan", "%"),
    listOf("log", "ln", "sqrt", "^"),
)

@Composable
private fun KeyButton(label: String, modifier: Modifier = Modifier, onClick: (String) -> Unit) {
    val colors = LocalDoradoColors.current
    val accent = label in setOf("=", "C", "sci")
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

/* ============================== Notes ============================== */
