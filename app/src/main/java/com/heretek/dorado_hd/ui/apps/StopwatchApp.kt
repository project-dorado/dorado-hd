package com.heretek.dorado_hd.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import java.util.Locale
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/* ============================== Stopwatch ============================== */

data class StopwatchLap(val number: Int, val splitMs: Long, val totalMs: Long)

data class StopwatchState(
    val running: Boolean = false,
    val accumulatedMs: Long = 0L,
    val startedAtWall: Long = 0L,
    val laps: List<StopwatchLap> = emptyList(),
) {
    /** Wall-clock elapsed; survives process death via [startedAtWall]. */
    fun elapsed(nowWall: Long): Long =
        if (running) accumulatedMs + (nowWall - startedAtWall).coerceAtLeast(0L) else accumulatedMs
}

data class CountdownState(
    val setupMs: Long = 0L,
    val remainingMs: Long = 0L,
    val running: Boolean = false,
    val startedAtWall: Long = 0L,
    val alarming: Boolean = false,
    val alarmStartedAtWall: Long = 0L,
) {
    fun remaining(nowWall: Long): Long =
        if (running) (remainingMs - (nowWall - startedAtWall)).coerceAtLeast(0L) else remainingMs
}

data class AlarmPhase(val flashOn: Boolean, val volume: Double, val expired: Boolean)

data class StopwatchRestore(
    val stopwatch: StopwatchState,
    val countdown: CountdownState,
    val mode: String,
)

/**
 * Stopwatch + countdown engine. Pure Kotlin and wall-clock based so resume,
 * expiry and alarm timing are testable without Android.
 */
object StopwatchEngine {

    /** The device auto-stops just before 24 h: 23:59:59.95. */
    const val MAX_STOPWATCH_MS = 23 * 3_600_000L + 59 * 60_000L + 59_950L
    const val MAX_COUNTDOWN_MS = 23 * 3_600_000L + 59 * 60_000L + 59_000L

    /** Alarm auto-silences after 29 s; the crescendo floor is 0.85. */
    const val ALARM_STOP_MS = 29_000L
    const val ALARM_CRESCENDO_MS = 30_000.0

    const val MODE_STOPWATCH = "stopwatch"
    const val MODE_TIMER = "timer"

    /** Persistence cadence: one snapshot per second instead of one per tick. */
    const val SNAPSHOT_INTERVAL_MS = 1_000L

    /** True when [nowMs] is at least [SNAPSHOT_INTERVAL_MS] past the last write. */
    fun snapshotDue(lastWriteMs: Long, nowMs: Long, intervalMs: Long = SNAPSHOT_INTERVAL_MS): Boolean =
        nowMs - lastWriteMs >= intervalMs

    fun start(state: StopwatchState, nowWall: Long): StopwatchState {
        if (state.running) return state
        val armed = if (state.accumulatedMs >= MAX_STOPWATCH_MS) {
            state.copy(accumulatedMs = 0L, laps = emptyList())
        } else {
            state
        }
        return armed.copy(running = true, startedAtWall = nowWall)
    }

    fun pause(state: StopwatchState, nowWall: Long): StopwatchState =
        if (!state.running) state
        else state.copy(
            running = false,
            accumulatedMs = state.elapsed(nowWall).coerceAtMost(MAX_STOPWATCH_MS),
            startedAtWall = 0L,
        )

    fun reset(state: StopwatchState): StopwatchState = StopwatchState()

    /** Records split (since previous lap) and total for the new lap. */
    fun lap(state: StopwatchState, nowWall: Long): StopwatchState {
        if (!state.running) return state
        val total = state.elapsed(nowWall).coerceAtMost(MAX_STOPWATCH_MS)
        if (total <= 0L) return state
        val last = state.laps.lastOrNull()?.totalMs ?: 0L
        return state.copy(laps = state.laps + StopwatchLap(state.laps.size + 1, total - last, total))
    }

    /** Auto-stop when the 23:59:59.95 cap is reached. */
    fun tick(state: StopwatchState, nowWall: Long): StopwatchState =
        if (state.running && state.elapsed(nowWall) >= MAX_STOPWATCH_MS) {
            state.copy(running = false, accumulatedMs = MAX_STOPWATCH_MS, startedAtWall = 0L)
        } else {
            state
        }

    fun countdownFromWheels(hours: Int, minutes: Int, seconds: Int): Long =
        (hours.coerceIn(0, 23) * 3_600L + minutes.coerceIn(0, 59) * 60L + seconds.coerceIn(0, 59)) * 1_000L

    /** unit: 0 = hours, 1 = minutes, 2 = seconds; wheels wrap like the device. */
    fun adjustWheel(state: CountdownState, unit: Int, delta: Int): CountdownState {
        var h = (state.setupMs / 3_600_000L).toInt()
        var m = ((state.setupMs % 3_600_000L) / 60_000L).toInt()
        var s = ((state.setupMs % 60_000L) / 1_000L).toInt()
        when (unit) {
            0 -> h = (h + delta + 24) % 24
            1 -> m = (m + delta + 60) % 60
            else -> s = (s + delta + 60) % 60
        }
        return state.copy(setupMs = countdownFromWheels(h, m, s))
    }

    fun startCountdown(state: CountdownState, nowWall: Long): CountdownState {
        val remaining = if (state.remainingMs <= 0L) state.setupMs else state.remainingMs
        if (remaining <= 0L) return state
        return state.copy(
            remainingMs = remaining,
            running = true,
            startedAtWall = nowWall,
            alarming = false,
            alarmStartedAtWall = 0L,
        )
    }

    fun pauseCountdown(state: CountdownState, nowWall: Long): CountdownState =
        if (!state.running) state
        else state.copy(running = false, remainingMs = state.remaining(nowWall), startedAtWall = 0L)

    fun resetCountdown(state: CountdownState): CountdownState = state.copy(
        remainingMs = 0L, running = false, startedAtWall = 0L, alarming = false, alarmStartedAtWall = 0L,
    )

    /** Transitions to the ringing state when the countdown hits zero. */
    fun tickCountdown(state: CountdownState, nowWall: Long): CountdownState =
        if (state.running && state.remaining(nowWall) <= 0L) {
            state.copy(
                remainingMs = 0L,
                running = false,
                startedAtWall = 0L,
                alarming = true,
                alarmStartedAtWall = nowWall,
            )
        } else {
            state
        }

    fun stopAlarm(state: CountdownState): CountdownState =
        state.copy(alarming = false, alarmStartedAtWall = 0L)

    /** Flash at 2 Hz, volume climbs 0.85 → 1.0 over 30 s, expires after 29 s. */
    fun alarmPhase(elapsedMs: Long): AlarmPhase {
        val e = elapsedMs.coerceAtLeast(0L)
        val volume = (0.85 + 0.15 * (e / ALARM_CRESCENDO_MS)).coerceIn(0.85, 1.0)
        return AlarmPhase(flashOn = (e / 500L) % 2L == 0L, volume = volume, expired = e > ALARM_STOP_MS)
    }

    fun formatStopwatch(ms: Long): String {
        val s = ms.coerceAtLeast(0L)
        val h = s / 3_600_000L
        val m = (s % 3_600_000L) / 60_000L
        val sec = (s % 60_000L) / 1_000L
        val cs = (s % 1_000L) / 10L
        return if (h > 0) {
            "%d:%02d:%02d.%02d".format(Locale.US, h, m, sec, cs)
        } else {
            "%02d:%02d.%02d".format(Locale.US, m, sec, cs)
        }
    }

    fun formatCountdown(ms: Long): String {
        val s = ms.coerceAtLeast(0L) / 1000L
        val h = s / 3600L
        val m = (s % 3600L) / 60L
        val sec = s % 60L
        return if (h > 0) {
            "%d:%02d:%02d".format(Locale.US, h, m, sec)
        } else {
            "%02d:%02d".format(Locale.US, m, sec)
        }
    }

    fun serialize(stopwatch: StopwatchState, countdown: CountdownState, mode: String): String = buildString {
        append("mode=").append(mode)
        append(";swRun=").append(if (stopwatch.running) 1 else 0)
        append(";swAcc=").append(stopwatch.accumulatedMs)
        append(";swStart=").append(stopwatch.startedAtWall)
        append(";laps=").append(
            stopwatch.laps.joinToString(",") { "${it.number}:${it.splitMs}:${it.totalMs}" },
        )
        append(";cdSetup=").append(countdown.setupMs)
        append(";cdRem=").append(countdown.remainingMs)
        append(";cdRun=").append(if (countdown.running) 1 else 0)
        append(";cdStart=").append(countdown.startedAtWall)
        append(";cdAlarm=").append(if (countdown.alarming) 1 else 0)
        append(";cdAlarmAt=").append(countdown.alarmStartedAtWall)
    }

    /** Restore with wall-clock catch-up; an expired countdown resets to zero. */
    fun restore(blob: String?, nowWall: Long): StopwatchRestore {
        if (blob.isNullOrBlank()) return StopwatchRestore(StopwatchState(), CountdownState(), MODE_STOPWATCH)
        val f = blob.split(';').mapNotNull {
            val eq = it.indexOf('=')
            if (eq <= 0) null else it.substring(0, eq) to it.substring(eq + 1)
        }.toMap()
        val laps = f["laps"].orEmpty().split(',').mapNotNull { part ->
            val bits = part.split(':')
            if (bits.size != 3) null
            else StopwatchLap(
                bits[0].toIntOrNull() ?: 0,
                bits[1].toLongOrNull() ?: 0L,
                bits[2].toLongOrNull() ?: 0L,
            )
        }
        var stopwatch = StopwatchState(
            running = f["swRun"] == "1",
            accumulatedMs = f["swAcc"]?.toLongOrNull() ?: 0L,
            startedAtWall = f["swStart"]?.toLongOrNull() ?: 0L,
            laps = laps,
        )
        if (stopwatch.running && stopwatch.elapsed(nowWall) >= MAX_STOPWATCH_MS) {
            stopwatch = stopwatch.copy(running = false, accumulatedMs = MAX_STOPWATCH_MS, startedAtWall = 0L)
        }
        var countdown = CountdownState(
            setupMs = f["cdSetup"]?.toLongOrNull() ?: 0L,
            remainingMs = f["cdRem"]?.toLongOrNull() ?: 0L,
            running = f["cdRun"] == "1",
            startedAtWall = f["cdStart"]?.toLongOrNull() ?: 0L,
            alarming = f["cdAlarm"] == "1",
            alarmStartedAtWall = f["cdAlarmAt"]?.toLongOrNull() ?: 0L,
        )
        if (countdown.running && countdown.remaining(nowWall) <= 0L) {
            countdown = countdown.copy(
                remainingMs = 0L, running = false, startedAtWall = 0L,
                alarming = false, alarmStartedAtWall = 0L,
            )
        }
        return StopwatchRestore(stopwatch, countdown, f["mode"] ?: MODE_STOPWATCH)
    }
}

@Composable
fun StopwatchApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val colors = LocalDoradoColors.current
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }
    DisposableEffect(Unit) {
        onDispose { synth.stop() }
    }

    var mode by remember { mutableStateOf(StopwatchEngine.MODE_STOPWATCH) }
    var sw by remember { mutableStateOf(StopwatchState()) }
    var cd by remember { mutableStateOf(CountdownState()) }
    var nowMs by remember { mutableStateOf(AppClock.millis()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        graph.appState.get("stopwatch")?.let { blob ->
            val restored = StopwatchEngine.restore(blob, AppClock.millis())
            sw = restored.stopwatch
            cd = restored.countdown
            mode = restored.mode
        }
        loaded = true
    }
    // Persist only when a meaningful field changes (start/pause/lap/reset or
    // countdown expiry) and take a 1 s snapshot while a clock is ticking.
    // Writing on every state emission would issue ~30 Room writes/s.
    LaunchedEffect(
        loaded, mode, sw.running, sw.accumulatedMs, sw.laps,
        cd.running, cd.remainingMs, cd.alarming, cd.alarmStartedAtWall,
    ) {
        if (loaded) graph.appState.put("stopwatch", StopwatchEngine.serialize(sw, cd, mode))
    }
    val snapshot = rememberUpdatedState(StopwatchEngine.serialize(sw, cd, mode))
    LaunchedEffect(loaded, sw.running || cd.running) {
        if (!loaded || !(sw.running || cd.running)) return@LaunchedEffect
        while (true) {
            delay(StopwatchEngine.SNAPSHOT_INTERVAL_MS)
            graph.appState.put("stopwatch", snapshot.value)
        }
    }
    DisposableEffect(loaded) {
        onDispose {
            if (loaded) scope.launch(NonCancellable) {
                graph.appState.put("stopwatch", snapshot.value)
            }
        }
    }
    LaunchedEffect(sw.running, cd.running) {
        while (sw.running || cd.running) {
            nowMs = AppClock.millis()
            if (sw.running) sw = StopwatchEngine.tick(sw, nowMs)
            if (cd.running) cd = StopwatchEngine.tickCountdown(cd, nowMs)
            delay(33)
        }
        nowMs = AppClock.millis()
    }
    LaunchedEffect(cd.alarming, cd.alarmStartedAtWall) {
        if (!cd.alarming) return@LaunchedEffect
        synth.start()
        while (isActive) {
            val phase = StopwatchEngine.alarmPhase(AppClock.millis() - cd.alarmStartedAtWall)
            if (phase.expired) {
                cd = StopwatchEngine.stopAlarm(cd)
                break
            }
            val tone = sineNote(if (phase.flashOn) 880.0 else 640.0, 220)
            synth.playSamples(DoubleArray(tone.size) { i -> tone[i] * phase.volume })
            delay(500)
        }
    }

    DetailScaffold(title = "stopwatch") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(StopwatchEngine.MODE_STOPWATCH, StopwatchEngine.MODE_TIMER).forEach { m ->
                    val on = m == mode
                    EdgeCropText(
                        text = m,
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = if (on) colors.accent else colors.textSecondary,
                        modifier = Modifier
                            .combinedClickable(onClick = { mode = m }, onLongClick = {})
                            .padding(end = 12.dp, top = 4.dp, bottom = 8.dp),
                    )
                }
            }
            if (mode == StopwatchEngine.MODE_STOPWATCH) {
                StopwatchPage(
                    sw = sw,
                    nowMs = nowMs,
                    onStartPause = {
                        if (sw.running) {
                            sw = StopwatchEngine.pause(sw, AppClock.millis())
                        } else {
                            sw = StopwatchEngine.start(sw, AppClock.millis())
                            bank.play("select")
                        }
                    },
                    onLap = {
                        sw = StopwatchEngine.lap(sw, AppClock.millis())
                        bank.play("tick")
                    },
                    onReset = { sw = StopwatchEngine.reset(sw) },
                )
            } else {
                CountdownPage(
                    cd = cd,
                    nowMs = nowMs,
                    onWheel = { unit, delta -> cd = StopwatchEngine.adjustWheel(cd, unit, delta) },
                    onStartPause = {
                        if (cd.running) {
                            cd = StopwatchEngine.pauseCountdown(cd, AppClock.millis())
                        } else {
                            cd = StopwatchEngine.startCountdown(cd, AppClock.millis())
                            bank.play("select")
                        }
                    },
                    onReset = { cd = StopwatchEngine.resetCountdown(cd) },
                    onStopAlarm = { cd = StopwatchEngine.stopAlarm(cd) },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StopwatchPage(
    sw: StopwatchState,
    nowMs: Long,
    onStartPause: () -> Unit,
    onLap: () -> Unit,
    onReset: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val elapsed = sw.elapsed(nowMs)
    val last = sw.laps.lastOrNull()
    Column(Modifier.fillMaxSize()) {
        BasicText(
            text = StopwatchEngine.formatStopwatch(elapsed),
            style = TextStyle(
                fontFamily = Selawik,
                fontWeight = FontWeight.Light,
                fontSize = DoradoTokens.TYPE_HEADER_CROPPED.sp,
                color = colors.textPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        EdgeCropText(
            text = last?.let {
                "split ${StopwatchEngine.formatStopwatch(it.splitMs)}   total ${StopwatchEngine.formatStopwatch(it.totalMs)}"
            } ?: "split 00:00.00",
            fontSize = DoradoTokens.TYPE_CAPTION.dp,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EdgeCropText(
                text = if (sw.running) "pause" else "start",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.accent,
                modifier = Modifier
                    .combinedClickable(onClick = onStartPause, onLongClick = {})
                    .padding(vertical = 4.dp),
            )
            EdgeCropText(
                text = "lap",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = if (sw.running) colors.accent else colors.textInactive,
                modifier = Modifier
                    .combinedClickable(enabled = sw.running, onClick = onLap, onLongClick = {})
                    .padding(vertical = 4.dp),
            )
            EdgeCropText(
                text = "reset",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = if (elapsed > 0L || sw.laps.isNotEmpty()) colors.textSecondary else colors.textInactive,
                modifier = Modifier
                    .combinedClickable(onClick = onReset, onLongClick = {})
                    .padding(vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        // Split and total per lap, newest first.
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            sw.laps.reversed().forEach { lap ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    EdgeCropText(
                        text = "lap ${lap.number}",
                        fontSize = DoradoTokens.TYPE_CAPTION.dp,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    EdgeCropText(
                        text = StopwatchEngine.formatStopwatch(lap.splitMs),
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.width(12.dp))
                    EdgeCropText(
                        text = StopwatchEngine.formatStopwatch(lap.totalMs),
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        alpha = 0.7f,
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownPage(
    cd: CountdownState,
    nowMs: Long,
    onWheel: (Int, Int) -> Unit,
    onStartPause: () -> Unit,
    onReset: () -> Unit,
    onStopAlarm: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val running = cd.running || cd.remainingMs > 0L
    Column(Modifier.fillMaxSize()) {
        if (running || cd.alarming) {
            val phase = StopwatchEngine.alarmPhase(nowMs - cd.alarmStartedAtWall)
            val remaining = cd.remaining(nowMs) + if (cd.running) 1_000L else 0L
            BasicText(
                text = StopwatchEngine.formatCountdown(remaining),
                style = TextStyle(
                    fontFamily = Selawik,
                    fontWeight = FontWeight.Light,
                    fontSize = DoradoTokens.TYPE_HEADER_CROPPED.sp,
                    color = if (cd.alarming && phase.flashOn) colors.accentBright else colors.textPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Wheel("h", (cd.setupMs / 3_600_000L).toInt()) { d -> onWheel(0, d) }
                Wheel("m", ((cd.setupMs % 3_600_000L) / 60_000L).toInt()) { d -> onWheel(1, d) }
                Wheel("s", ((cd.setupMs % 60_000L) / 1_000L).toInt()) { d -> onWheel(2, d) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EdgeCropText(
                text = if (cd.running) "pause" else "start",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.accent,
                modifier = Modifier
                    .combinedClickable(onClick = onStartPause, onLongClick = {})
                    .padding(vertical = 4.dp),
            )
            EdgeCropText(
                text = "reset",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.textSecondary,
                modifier = Modifier
                    .combinedClickable(onClick = onReset, onLongClick = {})
                    .padding(vertical = 4.dp),
            )
            if (cd.alarming) {
                EdgeCropText(
                    text = "stop alarm",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.accentBright,
                    modifier = Modifier
                        .combinedClickable(onClick = onStopAlarm, onLongClick = {})
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Wheel(label: String, value: Int, onDelta: (Int) -> Unit) {
    val colors = LocalDoradoColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        EdgeCropText(
            text = "+",
            fontSize = DoradoTokens.TYPE_LIST.dp,
            color = colors.accent,
            modifier = Modifier
                .combinedClickable(onClick = { onDelta(1) }, onLongClick = {})
                .padding(6.dp),
        )
        BasicText(
            text = "%02d".format(Locale.US, value),
            style = TextStyle(
                fontFamily = Selawik,
                fontWeight = FontWeight.Light,
                fontSize = DoradoTokens.TYPE_NOW_TITLE.sp,
                color = colors.textPrimary,
            ),
            modifier = Modifier
                .background(colors.elevated)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        EdgeCropText(
            text = label,
            fontSize = DoradoTokens.TYPE_CAPTION.dp,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
        EdgeCropText(
            text = "-",
            fontSize = DoradoTokens.TYPE_LIST.dp,
            color = colors.accent,
            modifier = Modifier
                .combinedClickable(onClick = { onDelta(-1) }, onLongClick = {})
                .padding(6.dp),
        )
    }
}
