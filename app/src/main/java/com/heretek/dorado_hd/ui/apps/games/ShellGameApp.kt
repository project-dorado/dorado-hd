package com.heretek.dorado_hd.ui.apps.games

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoAccent
import com.heretek.dorado_hd.design.DoradoColors
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.apps.MiniSynth
import com.heretek.dorado_hd.ui.apps.SfxBank
import com.heretek.dorado_hd.ui.apps.appDescription
import com.heretek.dorado_hd.ui.apps.appTap
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.delay
import kotlin.math.min

/* ============================================================ */
/*                   Shell Game of the Future                     */
/* ============================================================ */

private const val SHELL_VW = 272f
private const val SHELL_VH = 480f
private const val SHELL_ROW_Y = 300f
private const val SHELL_ROW_H = 30f
private const val SHELL_CHIP_W = 74f
private const val SHELL_ROW_W = 244f

@Composable
fun ShellGameApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }
    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    var shell by remember { mutableStateOf(ShellGameEngine.idle(seed = System.currentTimeMillis().toInt())) }
    var paused by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(2) }
    var fanfare by remember { mutableStateOf(false) }
    var pendingShake by remember { mutableStateOf<Float?>(null) }
    var ring by remember { mutableStateOf(ShellShakeRing()) }
    var rowScroll by remember { mutableStateOf(floatArrayOf(0f, 0f, 0f)) }
    var loaded by remember { mutableStateOf(false) }
    val scores by graph.games.top("shell-game", 5).collectAsState(initial = emptyList())

    fun play(name: String) {
        if (volume > 0) bank.play(name)
    }

    LaunchedEffect(Unit) {
        val unlocked = graph.appState.get("shell-game.trophies")
            ?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        volume = graph.appState.get("shell-game.volume")?.toIntOrNull() ?: 2
        shell = ShellGameEngine.idle(unlocked, System.currentTimeMillis().toInt())
        loaded = true
    }

    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val strength = ring.push(ShakeSample(event.values[0], event.values[1], event.values[2]))
                ring = strength.first
                if (strength.second != null) pendingShake = strength.second
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (manager != null && sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { manager?.unregisterListener(listener) }
    }

    // Game loop: one 60 Hz engine step, feeding the shake ring's latest event.
    LaunchedEffect(paused, about) {
        if (paused || about) return@LaunchedEffect
        while (true) {
            delay(16)
            val strength = pendingShake
            pendingShake = null
            val before = shell
            val next = ShellGameEngine.step(before, 16, strength)
            shell = next
            if (strength != null) play("tick")
            if (before.phase != next.phase) {
                when (next.phase) {
                    ShellPhase.LAUNCH -> play("whoosh")
                    ShellPhase.CHOOSE -> play("pop")
                    ShellPhase.SUCCESS -> play("coin")
                    ShellPhase.FAILURE -> play("error")
                    else -> Unit
                }
            }
            if (next.phase == ShellPhase.SUCCESS && !fanfare) {
                fanfare = true
                play("win")
            }
            if (next.phase == ShellPhase.FAILURE && next.holderRevealed && before.holderRevealed != next.holderRevealed) {
                play("pop")
            }
        }
    }

    LaunchedEffect(shell.unlocked, loaded) {
        if (!loaded) return@LaunchedEffect
        graph.appState.put("shell-game.trophies", shell.unlocked.sorted().joinToString(","))
    }

    LaunchedEffect(volume, loaded) {
        if (!loaded) return@LaunchedEffect
        graph.appState.put("shell-game.volume", volume.toString())
    }

    LaunchedEffect(shell.phase) {
        if (shell.phase == ShellPhase.SUCCESS) {
            graph.games.record("shell-game", shell.tier.ordinal + 1, shell.tier.label)
        }
    }

    fun collect() {
        shell = ShellGameEngine.reset(shell)
        fanfare = false
    }

    DetailScaffold(title = "shell game of the future", onBack = { paused = true }) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                val phaseLabel = when (shell.phase) {
                    ShellPhase.SITTING -> "shake or tap to charge"
                    ShellPhase.LAUNCH -> "get ready"
                    ShellPhase.FLYING -> "watch the flight"
                    ShellPhase.CHOOSE -> "pick a robot"
                    ShellPhase.SUCCESS -> "trophy won"
                    ShellPhase.FAILURE -> "empty"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().appDescription(
                        "trophies ${shell.unlocked.size} of ${ShellGameEngine.TROPHIES.size}, $phaseLabel",
                    ),
                ) {
                    BasicText(
                        text = "trophies ${shell.unlocked.size}/${ShellGameEngine.TROPHIES.size}",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        text = phaseLabel,
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    EdgeText("pause", colors.textPrimary) { paused = true }
                }
                Spacer(Modifier.height(4.dp))
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val view = shellView(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .appDescription("shell game, $phaseLabel, charge ${shell.charge.toInt()}")
                            .pointerInput(shell.phase) {
                                detectTapGestures { offset ->
                                    val x = view.invX(offset.x)
                                    val y = view.invY(offset.y)
                                    val robot = shellRobotAt(x, y)
                                    when {
                                        shell.phase == ShellPhase.CHOOSE && robot != null ->
                                            shell = ShellGameEngine.choose(shell, robot)
                                        shell.phase == ShellPhase.SITTING ->
                                            shell = ShellGameEngine.tapCharge(shell)
                                    }
                                }
                            }
                            .pointerInput(shell.phase) {
                                detectDragGestures { change, drag ->
                                    val row = ((view.invY(change.position.y) - SHELL_ROW_Y) / SHELL_ROW_H).toInt()
                                    if (row in 0..2) {
                                        val next = rowScroll.copyOf()
                                        next[row] = (next[row] + drag.x / view.scale).coerceIn(-(SHELL_CHIP_W * 8 - SHELL_ROW_W), 0f)
                                        rowScroll = next
                                    }
                                }
                            },
                    ) {
                        drawShellScene(shell, view, colors, rowScroll)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    (0..3).forEach { level ->
                        EdgeText(
                            text = listOf("off", "low", "medium", "high")[level],
                            color = if (volume == level) colors.accent else colors.textSecondary,
                            onClick = { volume = level },
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    BasicText(
                        text = scores.firstOrNull()?.let { "best ${it.meta} tier" } ?: "",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
                    )
                }
            }

            if (fanfare) {
                ShellOverlay(
                    title = if (shell.unlocked.size == ShellGameEngine.TROPHIES.size) "collection complete" else "trophy unlocked",
                    detail = shell.trophy ?: "",
                    hint = "tap to launch again",
                    onClick = { collect() },
                )
            } else if (shell.phase == ShellPhase.FAILURE && shell.holderRevealed) {
                ShellOverlay(
                    title = "not this robot",
                    detail = "the trophy was in pad ${shell.hiddenIndex + 1}",
                    hint = "tap to try again",
                    onClick = { collect() },
                )
            }

            if (paused || about) {
                ShellPause(
                    about = about,
                    volume = volume,
                    onResume = { paused = false; about = false },
                    onAbout = { about = !about },
                    onVolume = { volume = it },
                    onReset = { shell = ShellGameEngine.reset(shell); fanfare = false },
                    onMenu = { graph.nav.pop() },
                )
            }
        }
    }
}

@Composable
private fun ShellOverlay(title: String, detail: String, hint: String, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.88f))
            .appTap(label = hint) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(
                text = title,
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_TITLE.sp, color = colors.accentBright),
            )
            if (detail.isNotEmpty()) {
                BasicText(
                    text = detail,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.textPrimary),
                )
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = hint,
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
        }
    }
}

@Composable
private fun ShellPause(
    about: Boolean,
    volume: Int,
    onResume: () -> Unit,
    onAbout: () -> Unit,
    onVolume: (Int) -> Unit,
    onReset: () -> Unit,
    onMenu: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.94f))
            .padding(DoradoTokens.EDGE.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(
            text = if (about) "about" else "paused",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
        )
        if (about) {
            BasicText(
                text = "Three rocket robots, one hidden space trophy. Shake the " +
                    "device to charge their boosters, then tap the robot you " +
                    "believe carries the prize.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
            )
        } else {
            BasicText(
                text = "volume",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf("off", "low", "medium", "high").forEachIndexed { index, label ->
                    EdgeText(label, if (volume == index) colors.accent else colors.textSecondary) { onVolume(index) }
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EdgeText("resume", colors.textPrimary) { onResume() }
                EdgeText("about", colors.accent) { onAbout() }
                EdgeText("reset", colors.textPrimary) { onReset() }
                EdgeText("main menu", colors.textPrimary) { onMenu() }
            }
        }
        if (about) {
            EdgeText("back", colors.textPrimary) { onAbout() }
        }
    }
}

private class ShellView(val scale: Float, val ox: Float, val oy: Float) {
    fun x(v: Float): Float = ox + v * scale
    fun y(v: Float): Float = oy + v * scale
    fun s(v: Float): Float = v * scale
    fun invX(px: Float): Float = (px - ox) / scale
    fun invY(py: Float): Float = (py - oy) / scale
}

private fun shellView(width: Float, height: Float): ShellView {
    val scale = min(width / SHELL_VW, height / SHELL_VH)
    return ShellView(
        scale = scale,
        ox = (width - SHELL_VW * scale) / 2f,
        oy = (height - SHELL_VH * scale) / 2f,
    )
}

private fun shellRobotAt(x: Float, y: Float): Int? {
    for (i in 0 until 3) {
        val pad = ShellGameEngine.PADS[i]
        if (x in (pad.x - 6f)..(pad.x + 34f) && y in (pad.y - 52f)..(pad.y + 34f)) return i
    }
    return null
}

private fun DrawScope.drawShellScene(
    state: ShellState,
    view: ShellView,
    colors: DoradoColors,
    rowScroll: FloatArray,
) {
    drawRect(colors.background, Offset(view.x(0f), view.y(0f)), Size(view.s(SHELL_VW), view.s(SHELL_VH)))
    // Star field: deterministic dots with a slow drift while flying.
    val drift = state.robots.minOfOrNull { it.pos.y } ?: 0f
    for (i in 0 until 90) {
        val sx = ((i * 97) % 272).toFloat()
        val sy = ((i * 53) % 480 + drift * 0.4f) % 480f
        val twinkle = 0.25f + 0.5f * (((i * 31) % 7) / 7f)
        drawCircle(
            color = if (i % 5 == 0) colors.accentBright.copy(alpha = 0.7f) else colors.textSecondary.copy(alpha = twinkle),
            radius = view.s(if (i % 9 == 0) 1.6f else 1.0f),
            center = Offset(view.x(sx), view.y(sy)),
        )
    }
    drawTrophyRows(state, view, colors, rowScroll)
    drawLaunchPads(view, colors)
    state.robots.forEachIndexed { index, robot ->
        val revealed = (state.phase == ShellPhase.SUCCESS && index == state.hiddenIndex) ||
            (state.phase == ShellPhase.FAILURE && state.holderRevealed && index == state.hiddenIndex)
        val opened = (state.phase == ShellPhase.FAILURE && index == state.chosenIndex) ||
            (state.phase == ShellPhase.SUCCESS && index == state.hiddenIndex)
        drawRobot(robot, view, colors, flying = state.phase == ShellPhase.FLYING, opened = opened, revealed = revealed)
    }
    drawChargeMeter(state.charge, view, colors)
}

private fun DrawScope.drawLaunchPads(view: ShellView, colors: DoradoColors) {
    for (pad in ShellGameEngine.PADS) {
        val path = Path().apply {
            moveTo(view.x(pad.x - 4f), view.y(pad.y + 26f))
            lineTo(view.x(pad.x + 30f), view.y(pad.y + 26f))
            lineTo(view.x(pad.x + 26f), view.y(pad.y + 34f))
            lineTo(view.x(pad.x), view.y(pad.y + 34f))
            close()
        }
        drawPath(path, color = colors.tilePressed)
        drawLine(
            color = colors.accent,
            start = Offset(view.x(pad.x - 2f), view.y(pad.y + 26f)),
            end = Offset(view.x(pad.x + 28f), view.y(pad.y + 26f)),
            strokeWidth = view.s(1.5f),
        )
    }
}

private fun DrawScope.drawRobot(
    robot: ShellRobot,
    view: ShellView,
    colors: DoradoColors,
    flying: Boolean,
    opened: Boolean,
    revealed: Boolean,
) {
    val x = view.x(robot.pos.x)
    val y = view.y(robot.pos.y)
    val w = view.s(30f)
    val bodyH = view.s(30f)
    if (flying) {
        val flame = Path().apply {
            moveTo(x + w * 0.25f, y + bodyH)
            lineTo(x + w * 0.75f, y + bodyH)
            lineTo(x + w * 0.5f, y + bodyH + view.s(14f))
            close()
        }
        drawPath(flame, color = DoradoAccent.ORANGE.bright)
    }
    drawRect(DoradoAccent.CYAN.primary, Offset(x, y), Size(w, bodyH))
    drawRect(colors.accent, Offset(x + view.s(2f), y + view.s(2f)), Size(w - view.s(4f), view.s(7f)))
    // Dome and antenna.
    drawCircle(color = colors.accentBright, radius = view.s(6f), center = Offset(x + w / 2f, y - view.s(2f)))
    drawLine(
        color = colors.textPrimary,
        start = Offset(x + w / 2f, y - view.s(7f)),
        end = Offset(x + w / 2f, y - view.s(16f)),
        strokeWidth = view.s(1.4f),
    )
    drawCircle(color = DoradoAccent.PINK.bright, radius = view.s(2f), center = Offset(x + w / 2f, y - view.s(16f)))
    // Bay door.
    val doorColor = if (opened) colors.background else DoradoAccent.PURPLE.primary
    drawRect(doorColor, Offset(x + view.s(6f), y + view.s(12f)), Size(w - view.s(12f), view.s(14f)))
    if (revealed) {
        // Hidden trophy: cup + stem.
        val cx = x + w / 2f
        val cy = y - view.s(20f)
        drawCircle(color = colors.accentBright, radius = view.s(5f), center = Offset(cx, cy))
        drawRect(colors.accentBright, Offset(cx - view.s(1.5f), cy), Size(view.s(3f), view.s(7f)))
        drawRect(colors.accentBright, Offset(cx - view.s(5f), cy + view.s(7f)), Size(view.s(10f), view.s(2f)))
    }
}

private fun DrawScope.drawChargeMeter(charge: Float, view: ShellView, colors: DoradoColors) {
    val left = view.x(14f)
    val top = view.y(444f)
    val w = view.s(SHELL_VW - 28f)
    val h = view.s(18f)
    drawRect(colors.tile, Offset(left, top), Size(w, h))
    val fraction = (charge / ShellGameEngine.CHARGE_MAX).coerceIn(0f, 1f)
    val fill = Brush.horizontalGradient(listOf(colors.accent, colors.accentBright))
    drawRect(fill, Offset(left, top), Size(w * fraction, h))
    drawRect(colors.border, Offset(left, top), Size(w, h), style = Stroke(view.s(1f)))
    val levels = listOf(ShellGameEngine.MEDIUM_LAMP, ShellGameEngine.HARD_LAMP)
    levels.forEachIndexed { index, lamp ->
        val lit = charge >= lamp
        val cx = left + w * (if (index == 0) 0.5f else 0.85f)
        drawCircle(
            color = if (lit) colors.accentBright else colors.tilePressed,
            radius = view.s(4f),
            center = Offset(cx, top + h / 2f),
        )
    }
    val label = Paint().apply {
        isAntiAlias = true
        textSize = view.s(7f)
        textAlign = Paint.Align.CENTER
        color = colors.textPrimary.toArgb()
    }
    drawContext.canvas.nativeCanvas.drawText(
        "charge ${charge.toInt()}",
        left + w / 2f,
        top - view.s(4f),
        label,
    )
}

private fun DrawScope.drawTrophyRows(
    state: ShellState,
    view: ShellView,
    colors: DoradoColors,
    rowScroll: FloatArray,
) {
    ShellTier.entries.forEachIndexed { row, tier ->
        val top = view.y(SHELL_ROW_Y + row * SHELL_ROW_H)
        val height = view.s(SHELL_ROW_H - 4f)
        drawRect(colors.elevated.copy(alpha = 0.85f), Offset(view.x(14f), top), Size(view.s(SHELL_ROW_W), height))
        val names = ShellGameEngine.trophiesFor(tier)
        clipRect(view.x(14f), top, view.x(14f + SHELL_ROW_W), top + height) {
            names.forEachIndexed { index, name ->
                val cx = view.x(18f + index * SHELL_CHIP_W + rowScroll[row])
                val cw = view.s(SHELL_CHIP_W - 4f)
                val unlocked = name in state.unlocked
                drawRect(
                    color = if (unlocked) colors.accent.copy(alpha = 0.35f) else colors.tile,
                    topLeft = Offset(cx, top + view.s(2f)),
                    size = Size(cw, height - view.s(4f)),
                )
                drawRect(
                    color = if (unlocked) colors.accentBright else colors.border,
                    topLeft = Offset(cx, top + view.s(2f)),
                    size = Size(cw, height - view.s(4f)),
                    style = Stroke(view.s(1f)),
                )
                val paint = Paint().apply {
                    isAntiAlias = true
                    textSize = view.s(8f)
                    color = (if (unlocked) colors.textPrimary else colors.textInactive).toArgb()
                }
                drawContext.canvas.nativeCanvas.drawText(
                    name.removePrefix(tier.prefix),
                    cx + view.s(4f),
                    top + height / 2f + view.s(3f),
                    paint,
                )
            }
        }
    }
}
