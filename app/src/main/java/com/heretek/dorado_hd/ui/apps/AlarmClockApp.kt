package com.heretek.dorado_hd.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.heretek.dorado_hd.data.db.AlarmEntity
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.components.CrossbarBar
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.design.components.KineticList
import com.heretek.dorado_hd.design.components.firstLetterOf
import com.heretek.dorado_hd.media.AlarmScheduler
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import com.heretek.dorado_hd.ui.components.LocalContextMenu
import com.heretek.dorado_hd.ui.components.MenuAction
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/* ============================== Engine ============================== */

/** 12/24-hour clock face preference (default 12h on device). */
enum class ClockFormat { H12, H24 }

/** Alarm delivery source: buzzer / playlist / radio presets. */
enum class AlarmSource { BUZZER, PLAYLIST, RADIO }

/**
 * Which AlarmManager call the scheduler may use: exact (permission granted or
 * pre-Android 12), a 60 s window, or an inexact fallback when the OS refuses.
 */
enum class AlarmScheduleMode { EXACT, WINDOW, INEXACT }

/**
 * Immutable alarm-clock state. Persisted through `graph.appState` under the
 * "alarm" key; the Room `AlarmEntity` list keeps OS scheduling separate.
 */
data class AlarmClockState(
    val clockFormat: ClockFormat = ClockFormat.H12,
    val dim: Int = 5,
    val alarmEnabled: Boolean = false,
    val alarmMinuteOfDay: Int = 7 * 60,
    val alarmSource: AlarmSource = AlarmSource.BUZZER,
    val snoozeMinutes: Int = 10,
    val sleepMinutes: Int = 20,
    val sleepVolume: Int = 2,
    val playlistId: Long = 0,
    val radioPreset: Long = 0,
    val ringing: Boolean = false,
    val ringingSource: AlarmSource? = null,
    val contentError: Boolean = false,
    val snoozed: Boolean = false,
    val snoozeMinuteOfDay: Int = -1,
    val lastFiredMinuteOfDay: Int = -1,
    val alertSeconds: Int = 0,
    val sleepEnabled: Boolean = false,
    val sleepSecondsLeft: Int = 20 * 60,
    val sleepOutOfTime: Boolean = false,
)

/**
 * Pure, deterministic alarm clock rules (W1 fidelity): one-shot minute-match
 * firing with snooze, a 30-minute buzzer bow-out, playlist/radio source
 * fallback, and the sleep timer's ping/shake/out-of-time ladder.
 */
object AlarmClockEngine {
    const val BOW_OUT_SECONDS = 1800
    const val SLEEP_PING_SECONDS = 9
    const val SLEEP_OUT_SECONDS = 10
    const val SHAKE_G_THRESHOLD = 1.0
    const val SNOOZE_MIN = 1
    const val SNOOZE_MAX = 30
    const val DIM_MIN = 1
    const val DIM_MAX = 10
    const val VOLUME_LEVELS = 4

    fun initial(): AlarmClockState = AlarmClockState()

    /** Exact alarms need the permission from Android 12 (API 31) on. */
    fun scheduleMode(sdkInt: Int, canScheduleExact: Boolean): AlarmScheduleMode =
        if (sdkInt < 31 || canScheduleExact) AlarmScheduleMode.EXACT else AlarmScheduleMode.WINDOW

    fun normalizeMinute(minuteOfDay: Int): Int = ((minuteOfDay % 1440) + 1440) % 1440

    fun setAlarmTime(state: AlarmClockState, hour: Int, minute: Int): AlarmClockState =
        state.copy(alarmMinuteOfDay = normalizeMinute(hour * 60 + minute))

    fun setAlarmEnabled(state: AlarmClockState, enabled: Boolean): AlarmClockState =
        state.copy(
            alarmEnabled = enabled,
            snoozed = if (enabled) state.snoozed else false,
            ringing = if (enabled) state.ringing else false,
            ringingSource = if (enabled) state.ringingSource else null,
        )

    fun setAlarmSource(state: AlarmClockState, source: AlarmSource): AlarmClockState =
        state.copy(alarmSource = source)

    fun setSnoozeMinutes(state: AlarmClockState, minutes: Int): AlarmClockState =
        state.copy(snoozeMinutes = minutes.coerceIn(SNOOZE_MIN, SNOOZE_MAX))

    fun setClockFormat(state: AlarmClockState, format: ClockFormat): AlarmClockState =
        state.copy(clockFormat = format)

    fun setDim(state: AlarmClockState, dim: Int): AlarmClockState =
        state.copy(dim = dim.coerceIn(DIM_MIN, DIM_MAX))

    fun setSleep(state: AlarmClockState, minutes: Int, volume: Int): AlarmClockState {
        val m = minutes.coerceIn(1, 120)
        return state.copy(
            sleepMinutes = m,
            sleepVolume = volume.coerceIn(0, VOLUME_LEVELS - 1),
            sleepEnabled = true,
            sleepSecondsLeft = m * 60,
            sleepOutOfTime = false,
        )
    }

    fun setSleepVolume(state: AlarmClockState, volume: Int): AlarmClockState =
        state.copy(sleepVolume = volume.coerceIn(0, VOLUME_LEVELS - 1))

    fun disableSleep(state: AlarmClockState): AlarmClockState =
        state.copy(sleepEnabled = false, sleepSecondsLeft = state.sleepMinutes * 60, sleepOutOfTime = false)

    /**
     * One 1-second alarm tick. [seconds] is normally 1; shake resets use 0 so
     * the reset does not also consume the countdown's second.
     */
    fun tick(
        state: AlarmClockState,
        nowMinuteOfDay: Int,
        seconds: Int = 1,
        playlistAvailable: Boolean = true,
        radioAvailable: Boolean = true,
    ): AlarmClockState {
        val now = normalizeMinute(nowMinuteOfDay)
        var s = state
        // Re-arm the minute comparator once the fired minute has passed.
        if (!s.snoozed && !s.ringing && s.lastFiredMinuteOfDay >= 0 && s.lastFiredMinuteOfDay != now) {
            s = s.copy(lastFiredMinuteOfDay = -1)
        }
        if (s.ringing) {
            val elapsed = s.alertSeconds + seconds
            if (elapsed >= BOW_OUT_SECONDS) {
                // The buzzer bows out: silence and disable the alarm.
                return s.copy(
                    ringing = false,
                    ringingSource = null,
                    alertSeconds = 0,
                    alarmEnabled = false,
                    contentError = false,
                )
            }
            return s.copy(alertSeconds = elapsed)
        }
        if (!s.alarmEnabled) return s
        val target = if (s.snoozed && s.snoozeMinuteOfDay >= 0) s.snoozeMinuteOfDay else s.alarmMinuteOfDay
        if (now != target || s.lastFiredMinuteOfDay == now) return s
        val source = when {
            s.alarmSource == AlarmSource.PLAYLIST && !playlistAvailable -> AlarmSource.BUZZER
            s.alarmSource == AlarmSource.RADIO && !radioAvailable -> AlarmSource.BUZZER
            else -> s.alarmSource
        }
        return s.copy(
            ringing = true,
            ringingSource = source,
            alertSeconds = 0,
            lastFiredMinuteOfDay = now,
            snoozed = false,
            contentError = source != s.alarmSource,
        )
    }

    /** Defer the current ring by [AlarmClockState.snoozeMinutes] and re-arm. */
    fun snooze(state: AlarmClockState, nowMinuteOfDay: Int): AlarmClockState {
        if (!state.alarmEnabled) return state
        val now = normalizeMinute(nowMinuteOfDay)
        return state.copy(
            ringing = false,
            ringingSource = null,
            alertSeconds = 0,
            contentError = false,
            snoozed = true,
            snoozeMinuteOfDay = normalizeMinute(now + state.snoozeMinutes),
            lastFiredMinuteOfDay = now,
        )
    }

    fun dismiss(state: AlarmClockState): AlarmClockState =
        state.copy(ringing = false, ringingSource = null, alertSeconds = 0, contentError = false)

    /**
     * Sleep timer: counts down one second at a time. At zero the ping window
     * opens; a shake delta over 1 g resets the countdown; ten seconds past
     * zero raises out-of-time.
     */
    fun sleepTick(state: AlarmClockState, accelDeltaG: Double, seconds: Int = 1): AlarmClockState {
        if (!state.sleepEnabled || state.sleepOutOfTime) return state
        if (state.sleepSecondsLeft <= 0 && accelDeltaG > SHAKE_G_THRESHOLD) {
            return state.copy(sleepSecondsLeft = state.sleepMinutes * 60)
        }
        val left = state.sleepSecondsLeft - seconds
        if (left <= -SLEEP_OUT_SECONDS) {
            return state.copy(sleepSecondsLeft = -SLEEP_OUT_SECONDS, sleepOutOfTime = true, sleepEnabled = false)
        }
        return state.copy(sleepSecondsLeft = left)
    }

    /** True during the nine-second "ping" window after the countdown hits zero. */
    fun isSleepPinging(state: AlarmClockState): Boolean =
        state.sleepEnabled && state.sleepSecondsLeft <= 0 && state.sleepSecondsLeft > -SLEEP_OUT_SECONDS

    fun restartSleep(state: AlarmClockState): AlarmClockState =
        state.copy(sleepEnabled = true, sleepSecondsLeft = state.sleepMinutes * 60, sleepOutOfTime = false)

    /** Minutes until the armed (or snoozed) alarm; -1 when disabled. */
    fun minutesUntilAlarm(state: AlarmClockState, nowMinuteOfDay: Int): Int {
        if (!state.alarmEnabled) return -1
        val now = normalizeMinute(nowMinuteOfDay)
        val target = if (state.snoozed && state.snoozeMinuteOfDay >= 0) state.snoozeMinuteOfDay else state.alarmMinuteOfDay
        var delta = (target - now + 1440) % 1440
        if (delta == 0 && state.lastFiredMinuteOfDay == now) delta = 1440
        return delta
    }

    fun formatClock(minuteOfDay: Int, format: ClockFormat): String {
        val m = normalizeMinute(minuteOfDay)
        val hour24 = m / 60
        val minute = m % 60
        return when (format) {
            ClockFormat.H24 -> "%02d:%02d".format(Locale.US, hour24, minute)
            ClockFormat.H12 -> {
                val hour = if (hour24 % 12 == 0) 12 else hour24 % 12
                "%d:%02d %s".format(Locale.US, hour, minute, if (hour24 < 12) "am" else "pm")
            }
        }
    }

    fun formatCountdown(seconds: Int): String {
        val clamped = seconds.coerceAtLeast(0)
        return "%d:%02d".format(Locale.US, clamped / 60, clamped % 60)
    }

    /**
     * Converts a frame-to-frame roll/pitch jump into an estimated raw g delta.
     * `rememberTilt` smooths each axis with [smoothing], so a raw 1 g step
     * survives as a `(1 - smoothing)` fraction of the degrees; dividing it
     * back out recovers the device's >1 g shake test (`90·g` mapping).
     */
    fun proxyG(
        previousRollDeg: Float,
        previousPitchDeg: Float,
        rollDeg: Float,
        pitchDeg: Float,
        smoothing: Float = 0.85f,
    ): Double {
        val joint = max(abs(rollDeg - previousRollDeg), abs(pitchDeg - previousPitchDeg)).toDouble()
        val contribution = (1.0 - smoothing).coerceAtLeast(0.01)
        return joint / (90.0 * contribution)
    }

    fun serialize(state: AlarmClockState): String = listOf(
        state.clockFormat.name,
        state.dim,
        state.alarmEnabled,
        state.alarmMinuteOfDay,
        state.alarmSource.name,
        state.snoozeMinutes,
        state.sleepMinutes,
        state.sleepVolume,
        state.playlistId,
        state.radioPreset,
        if (state.sleepEnabled) 1 else 0,
        state.sleepSecondsLeft,
        if (state.sleepOutOfTime) 1 else 0,
    ).joinToString(";")

    fun parse(raw: String?): AlarmClockState {
        val base = initial()
        if (raw.isNullOrBlank()) return base
        val parts = raw.split(";")
        fun intAt(index: Int, fallback: Int) = parts.getOrNull(index)?.trim()?.toIntOrNull() ?: fallback
        fun longAt(index: Int, fallback: Long) = parts.getOrNull(index)?.trim()?.toLongOrNull() ?: fallback
        val sleepMinutes = intAt(6, base.sleepMinutes).coerceIn(1, 120)
        val sleepEnabled = parts.getOrNull(10)?.trim() == "1"
        val sleepOutOfTime = parts.getOrNull(12)?.trim() == "1"
        return base.copy(
            clockFormat = runCatching { ClockFormat.valueOf(parts[0]) }.getOrDefault(base.clockFormat),
            dim = intAt(1, base.dim).coerceIn(DIM_MIN, DIM_MAX),
            alarmEnabled = parts.getOrNull(2)?.trim()?.toBooleanStrictOrNull() ?: false,
            alarmMinuteOfDay = normalizeMinute(intAt(3, base.alarmMinuteOfDay)),
            alarmSource = runCatching { AlarmSource.valueOf(parts[4]) }.getOrDefault(base.alarmSource),
            snoozeMinutes = intAt(5, base.snoozeMinutes).coerceIn(SNOOZE_MIN, SNOOZE_MAX),
            sleepMinutes = sleepMinutes,
            sleepVolume = intAt(7, base.sleepVolume).coerceIn(0, VOLUME_LEVELS - 1),
            playlistId = longAt(8, base.playlistId),
            radioPreset = longAt(9, base.radioPreset),
            sleepEnabled = sleepEnabled,
            sleepSecondsLeft = if (sleepEnabled) intAt(11, sleepMinutes * 60) else sleepMinutes * 60,
            sleepOutOfTime = sleepOutOfTime,
        )
    }
}

/* ============================== UI ============================== */

private fun alarmRequestCode(id: Long): Int = (id xor (id ushr 32)).toInt()

private fun alarmToneBurst(volume: Int): DoubleArray =
    sfxTone(880.0, 260, decay = 2.0, gain = 0.18 + 0.08 * volume.coerceIn(0, 3))

private fun sleepPingBurst(volume: Int): DoubleArray =
    sfxTone(1320.0, 160, decay = 4.0, gain = 0.12 + 0.06 * volume.coerceIn(0, 3))

@Composable
fun AlarmClockApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val menus = LocalContextMenu.current
    val alarms by graph.alarms.alarms().collectAsState(initial = emptyList())
    val playlists by graph.library.playlists().collectAsState(initial = emptyList())
    val stations by graph.radio.stations().collectAsState(initial = emptyList())
    val synth = remember { MiniSynth(scope) }

    var engine by remember { mutableStateOf(AlarmClockEngine.initial()) }
    var loaded by remember { mutableStateOf(false) }
    var nowMinute by remember {
        mutableStateOf(AppClock.localTime().let { it.hour * 60 + it.minute })
    }
    val pager = rememberPagerState(initialPage = 0, pageCount = { 4 })
    // Short pivot labels so all four tabs fit the crossbar without the last
    // one being cropped at the right edge.
    val pageLabels = listOf("clock", "alarm", "sleep", "setup")

    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    LaunchedEffect(Unit) {
        engine = AlarmClockEngine.parse(graph.appState.get("alarm"))
        loaded = true
    }
    // Persist only meaningful settings/alarm changes; the 1 Hz tick must not be
    // a 1 Hz Room write. A 5 s snapshot while the sleep timer runs keeps the
    // remaining countdown current, and the final dispose write flushes it.
    LaunchedEffect(
        loaded, engine.clockFormat, engine.dim, engine.alarmEnabled, engine.alarmMinuteOfDay,
        engine.alarmSource, engine.snoozeMinutes, engine.sleepMinutes, engine.sleepVolume,
        engine.playlistId, engine.radioPreset, engine.sleepEnabled, engine.ringing,
    ) {
        if (loaded) graph.appState.put("alarm", AlarmClockEngine.serialize(engine))
    }
    val alarmSnapshot = rememberUpdatedState(AlarmClockEngine.serialize(engine))
    LaunchedEffect(loaded, engine.sleepEnabled) {
        if (!loaded || !engine.sleepEnabled) return@LaunchedEffect
        while (isActive) {
            delay(5_000)
            graph.appState.put("alarm", alarmSnapshot.value)
        }
    }
    DisposableEffect(loaded) {
        onDispose {
            if (loaded) scope.launch(NonCancellable) {
                graph.appState.put("alarm", alarmSnapshot.value)
            }
        }
    }

    // The 1 Hz tick drives minute-match firing, the 30-minute bow-out and the
    // sleep countdown; buzzer/ping audio is synthesized per second.
    LaunchedEffect(Unit) {
        while (isActive) {
            val now = AppClock.localTime()
            nowMinute = now.hour * 60 + now.minute
            engine = AlarmClockEngine.tick(
                engine,
                nowMinute,
                seconds = 1,
                playlistAvailable = playlists.isNotEmpty(),
                radioAvailable = stations.isNotEmpty(),
            )
            engine = AlarmClockEngine.sleepTick(engine, accelDeltaG = 0.0, seconds = 1)
            if (engine.ringing && engine.ringingSource == AlarmSource.BUZZER) {
                val burst = alarmToneBurst(engine.sleepVolume)
                scope.launch { synth.playSamples(burst) }
            }
            if (AlarmClockEngine.isSleepPinging(engine)) {
                val burst = sleepPingBurst(engine.sleepVolume)
                scope.launch { synth.playSamples(burst) }
            }
            delay(1000)
        }
    }

    // Shake-to-reset for the sleep alarm, sourced from the shared tilt helper.
    val tilt = rememberTilt()
    var previousTilt by remember { mutableStateOf<com.heretek.dorado_hd.ui.apps.TiltState?>(null) }
    LaunchedEffect(tilt.value) {
        val current = tilt.value
        val previous = previousTilt
        previousTilt = current
        if (previous == null || !current.available) return@LaunchedEffect
        val g = AlarmClockEngine.proxyG(previous.rollDeg, previous.pitchDeg, current.rollDeg, current.pitchDeg)
        if (g > AlarmClockEngine.SHAKE_G_THRESHOLD &&
            engine.sleepEnabled &&
            engine.sleepSecondsLeft <= 0
        ) {
            engine = AlarmClockEngine.sleepTick(engine, accelDeltaG = g, seconds = 0)
        }
    }

    fun alarmPi(alarm: AlarmEntity): android.app.PendingIntent =
        android.app.PendingIntent.getBroadcast(
            context,
            alarmRequestCode(alarm.id),
            android.content.Intent(context, com.heretek.dorado_hd.media.AlarmReceiver::class.java).apply {
                putExtra("alarmId", alarm.id)
                putExtra("alarmKind", alarm.alarmKind)
                putExtra("refId", alarm.refId)
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(alarm: AlarmEntity) {
        if (!alarm.enabled) return
        val am = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val triggerAt = AlarmScheduler.nextFireMs(alarm.hour, alarm.minute, daysOfWeek = alarm.daysOfWeek)
        val pi = alarmPi(alarm)
        val exactAllowed = android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            when (AlarmClockEngine.scheduleMode(android.os.Build.VERSION.SDK_INT, exactAllowed)) {
                AlarmScheduleMode.EXACT ->
                    am.set(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi)
                AlarmScheduleMode.WINDOW ->
                    am.setWindow(android.app.AlarmManager.RTC_WAKEUP, triggerAt, 60_000L, pi)
                AlarmScheduleMode.INEXACT ->
                    am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            // Exact alarms were revoked between the check and the call: fall
            // back to an allow-idle alarm rather than crashing the page.
            runCatching {
                am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
    }

    fun cancel(alarm: AlarmEntity) {
        val am = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        am.cancel(alarmPi(alarm))
    }

    fun parseTime(input: String): Pair<Int, Int>? {
        val m = Regex("(\\d{1,2})\\s*[:.]\\s*(\\d{1,2})").find(input.trim()) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || min !in 0..59) return null
        return h to min
    }

    DetailScaffold(title = "alarm clock") {
        Column(Modifier.fillMaxSize()) {
            if (engine.ringing) {
                RingBanner(
                    source = engine.ringingSource ?: AlarmSource.BUZZER,
                    contentError = engine.contentError,
                    onSnooze = { engine = AlarmClockEngine.snooze(engine, nowMinute) },
                    onDismiss = { engine = AlarmClockEngine.dismiss(engine) },
                )
            }
            CrossbarBar(
                labels = pageLabels,
                selected = pager.currentPage,
                onSelect = { index -> scope.launch { pager.animateScrollToPage(index) } },
            )
            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
                when (page) {
                    0 -> ClockPage(
                        nowMinute = nowMinute,
                        engine = engine,
                        onToggleAlarm = {
                            engine = AlarmClockEngine.setAlarmEnabled(engine, !engine.alarmEnabled)
                        },
                        onToggleSleep = {
                            engine = if (engine.sleepEnabled) {
                                AlarmClockEngine.disableSleep(engine)
                            } else {
                                AlarmClockEngine.setSleep(engine, engine.sleepMinutes, engine.sleepVolume)
                            }
                        },
                    )
                    1 -> AlarmPage(
                        engine = engine,
                        onEngine = { engine = it },
                        playlists = playlists,
                        stations = stations,
                        menus = menus,
                        onSave = {
                            val hour = engine.alarmMinuteOfDay / 60
                            val minute = engine.alarmMinuteOfDay % 60
                            val kind = when (engine.alarmSource) {
                                AlarmSource.PLAYLIST -> 0
                                AlarmSource.RADIO -> 1
                                AlarmSource.BUZZER -> 2
                            }
                            val ref = when (engine.alarmSource) {
                                AlarmSource.PLAYLIST -> engine.playlistId
                                AlarmSource.RADIO -> engine.radioPreset
                                AlarmSource.BUZZER -> 0L
                            }
                            engine = AlarmClockEngine.setAlarmEnabled(engine, true)
                            scope.launch {
                                val entity = AlarmEntity(
                                    hour = hour,
                                    minute = minute,
                                    enabled = true,
                                    label = "alarm",
                                    alarmKind = kind,
                                    refId = ref,
                                    daysOfWeek = 0,
                                )
                                val id = graph.alarms.add(entity)
                                schedule(entity.copy(id = id))
                            }
                        },
                        alarms = alarms,
                        onToggleAlarm = { alarm ->
                            scope.launch {
                                val enable = !alarm.enabled
                                graph.alarms.setEnabled(alarm.id, enable)
                                if (enable) schedule(alarm.copy(enabled = true)) else cancel(alarm)
                            }
                        },
                        onEditAlarm = { alarm ->
                            menus.showPrompt(
                                "edit alarm",
                                "%02d:%02d".format(alarm.hour, alarm.minute),
                            ) { value ->
                                val (h, m) = parseTime(value) ?: (alarm.hour to alarm.minute)
                                scope.launch {
                                    graph.alarms.setTime(alarm.id, h, m)
                                    val updated = alarm.copy(hour = h, minute = m)
                                    if (updated.enabled) schedule(updated) else cancel(updated)
                                }
                            }
                        },
                        onDeleteAlarm = { alarm ->
                            scope.launch {
                                cancel(alarm)
                                graph.alarms.delete(alarm.id)
                            }
                        },
                    )
                    2 -> SleepPage(engine = engine, onEngine = { engine = it })
                    else -> SettingsPage(engine = engine, onEngine = { engine = it })
                }
            }
        }
    }
}

@Composable
private fun RingBanner(
    source: AlarmSource,
    contentError: Boolean,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.tile)
            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EdgeCropText(
            text = "ringing: ${source.name.lowercase()}",
            fontSize = DoradoTokens.TYPE_LIST.dp,
            color = colors.accent,
            modifier = Modifier.weight(1f),
        )
        if (contentError) {
            EdgeCropText(
                text = "source unavailable",
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = colors.textSecondary,
                modifier = Modifier.padding(end = 10.dp),
            )
        }
        EdgeCropText(
            text = "snooze",
            fontSize = DoradoTokens.TYPE_LIST.dp,
            color = colors.accent,
            modifier = Modifier
                .combinedClickable(onClick = onSnooze, onLongClick = {})
                .padding(horizontal = 8.dp),
        )
        EdgeCropText(
            text = "dismiss",
            fontSize = DoradoTokens.TYPE_LIST.dp,
            color = colors.textSecondary,
            modifier = Modifier
                .combinedClickable(onClick = onDismiss, onLongClick = {})
                .padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun ClockPage(
    nowMinute: Int,
    engine: AlarmClockState,
    onToggleAlarm: () -> Unit,
    onToggleSleep: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
        EdgeCropText(
            text = AlarmClockEngine.formatClock(nowMinute, engine.clockFormat),
            fontSize = DoradoTokens.TYPE_HEADER_CROP_VISIBLE.dp,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(12.dp))
        EdgeCropText(
            text = if (engine.alarmEnabled) {
                val suffix = if (engine.snoozed) " (snoozed)" else ""
                "alarm ${AlarmClockEngine.formatClock(engine.alarmMinuteOfDay, engine.clockFormat)}$suffix"
            } else {
                "alarm off"
            },
            fontSize = DoradoTokens.TYPE_LIST.dp,
            color = if (engine.alarmEnabled) colors.accent else colors.textInactive,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onToggleAlarm, onLongClick = {})
                .padding(vertical = 6.dp),
        )
        EdgeCropText(
            text = if (engine.sleepEnabled) {
                if (engine.sleepOutOfTime) {
                    "sleep out of time"
                } else {
                    "sleep ${AlarmClockEngine.formatCountdown(engine.sleepSecondsLeft)}"
                }
            } else {
                "sleep off"
            },
            fontSize = DoradoTokens.TYPE_LIST.dp,
            color = if (engine.sleepEnabled) colors.accent else colors.textInactive,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onToggleSleep, onLongClick = {})
                .padding(vertical = 6.dp),
        )
        if (engine.snoozed) {
            Spacer(Modifier.height(8.dp))
            EdgeCropText(
                text = "snoozed until ${
                    AlarmClockEngine.formatClock(engine.snoozeMinuteOfDay, engine.clockFormat)
                }",
                fontSize = DoradoTokens.TYPE_NOW_META.dp,
                color = colors.textSecondary,
            )
        }
        if (engine.sleepEnabled && engine.sleepSecondsLeft <= 0) {
            Spacer(Modifier.height(8.dp))
            EdgeCropText(
                text = "shake to reset",
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun AlarmPage(
    engine: AlarmClockState,
    onEngine: (AlarmClockState) -> Unit,
    playlists: List<com.heretek.dorado_hd.data.model.Playlist>,
    stations: List<com.heretek.dorado_hd.data.db.RadioStationEntity>,
    menus: com.heretek.dorado_hd.ui.components.MenuController,
    onSave: () -> Unit,
    alarms: List<AlarmEntity>,
    onToggleAlarm: (AlarmEntity) -> Unit,
    onEditAlarm: (AlarmEntity) -> Unit,
    onDeleteAlarm: (AlarmEntity) -> Unit,
) {
    val colors = LocalDoradoColors.current
    Column(Modifier.fillMaxSize().padding(horizontal = DoradoTokens.EDGE.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (engine.clockFormat == ClockFormat.H24) {
                val hour = engine.alarmMinuteOfDay / 60
                TimeWheel(
                    items = (0..23).map { "%02d".format(it) },
                    index = hour,
                    onIndex = { onEngine(AlarmClockEngine.setAlarmTime(engine, it, engine.alarmMinuteOfDay % 60)) },
                )
                EdgeCropText(text = ":", fontSize = DoradoTokens.TYPE_NOW_TITLE.dp, color = colors.textSecondary)
                TimeWheel(
                    items = (0..59).map { "%02d".format(it) },
                    index = engine.alarmMinuteOfDay % 60,
                    onIndex = { onEngine(AlarmClockEngine.setAlarmTime(engine, engine.alarmMinuteOfDay / 60, it)) },
                )
            } else {
                val hour24 = engine.alarmMinuteOfDay / 60
                val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
                TimeWheel(
                    items = (1..12).map { "%d".format(it) },
                    index = hour12 - 1,
                    onIndex = { selected ->
                        val pm = hour24 >= 12
                        val h24 = (selected + 1) % 12 + if (pm) 12 else 0
                        onEngine(AlarmClockEngine.setAlarmTime(engine, h24, engine.alarmMinuteOfDay % 60))
                    },
                )
                EdgeCropText(text = ":", fontSize = DoradoTokens.TYPE_NOW_TITLE.dp, color = colors.textSecondary)
                TimeWheel(
                    items = (0..59).map { "%02d".format(it) },
                    index = engine.alarmMinuteOfDay % 60,
                    onIndex = { onEngine(AlarmClockEngine.setAlarmTime(engine, hour24, it)) },
                )
                TimeWheel(
                    items = listOf("am", "pm"),
                    index = if (hour24 < 12) 0 else 1,
                    onIndex = { selected ->
                        val hour12Base = if (hour24 % 12 == 0) 12 else hour24 % 12
                        val h24 = (hour12Base % 12) + if (selected == 1) 12 else 0
                        onEngine(AlarmClockEngine.setAlarmTime(engine, h24, engine.alarmMinuteOfDay % 60))
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            AlarmSource.entries.forEach { source ->
                EdgeCropText(
                    text = source.name.lowercase(),
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = if (source == engine.alarmSource) colors.accent else colors.textInactive,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { onEngine(AlarmClockEngine.setAlarmSource(engine, source)) },
                            onLongClick = {},
                        )
                        .padding(end = 14.dp, top = 4.dp, bottom = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        EdgeCropText(
            text = when (engine.alarmSource) {
                AlarmSource.BUZZER -> "buzzer tone"
                AlarmSource.PLAYLIST -> playlists.firstOrNull { it.id == engine.playlistId }?.name ?: "choose playlist"
                AlarmSource.RADIO -> stations.firstOrNull { it.id == engine.radioPreset }?.name ?: "choose station"
            },
            fontSize = DoradoTokens.TYPE_NOW_META.dp,
            color = if (engine.alarmSource == AlarmSource.BUZZER) colors.textSecondary else colors.accent,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        when (engine.alarmSource) {
                            AlarmSource.BUZZER -> menus.show(
                                title = "buzzer",
                                actions = listOf(MenuAction("default tone") { }),
                            )
                            AlarmSource.PLAYLIST -> menus.show(
                                title = "playlist",
                                actions = playlists.map { playlist ->
                                    MenuAction(playlist.name) {
                                        onEngine(engine.copy(playlistId = playlist.id))
                                    }
                                },
                            )
                            AlarmSource.RADIO -> menus.show(
                                title = "radio preset",
                                actions = stations.map { station ->
                                    MenuAction(station.name) {
                                        onEngine(engine.copy(radioPreset = station.id))
                                    }
                                },
                            )
                        }
                    },
                    onLongClick = {},
                )
                .padding(vertical = 6.dp),
        )
        EdgeCropText(
            text = "schedule",
            fontSize = DoradoTokens.TYPE_LIST.dp,
            color = colors.accent,
            modifier = Modifier
                .combinedClickable(onClick = onSave, onLongClick = {})
                .padding(vertical = 6.dp),
        )
        Spacer(Modifier.height(4.dp))
        EdgeCropText(
            text = "saved alarms",
            fontSize = DoradoTokens.TYPE_CAPTION.dp,
            color = colors.textSecondary,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        KineticList(
            items = alarms,
            key = { it.id },
            letter = { firstLetterOf(it.label) },
            rowContent = { alarm, _ ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(DoradoTokens.ROW_HEIGHT.dp)
                        .combinedClickable(
                            onClick = { onToggleAlarm(alarm) },
                            onLongClick = {
                                menus.show(
                                    title = "%02d:%02d %s".format(alarm.hour, alarm.minute, alarm.label),
                                    actions = listOf(
                                        MenuAction("edit time") { onEditAlarm(alarm) },
                                        MenuAction("delete") { onDeleteAlarm(alarm) },
                                    ),
                                )
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        EdgeCropText(
                            text = "%02d:%02d".format(alarm.hour, alarm.minute),
                            fontSize = DoradoTokens.TYPE_NOW_META.dp,
                            color = if (alarm.enabled) colors.textPrimary else colors.textInactive,
                        )
                        EdgeCropText(
                            text = alarm.label,
                            fontSize = DoradoTokens.TYPE_LIST.dp,
                            color = colors.textSecondary,
                        )
                    }
                    EdgeCropText(
                        text = if (alarm.enabled) "on" else "off",
                        fontSize = DoradoTokens.TYPE_CAPTION.dp,
                        color = colors.textSecondary,
                    )
                }
            },
        )
    }
}

@Composable
private fun SleepPage(engine: AlarmClockState, onEngine: (AlarmClockState) -> Unit) {
    val colors = LocalDoradoColors.current
    Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
        EdgeCropText(
            text = when {
                !engine.sleepEnabled -> "sleep off"
                engine.sleepOutOfTime -> "out of time"
                else -> AlarmClockEngine.formatCountdown(engine.sleepSecondsLeft)
            },
            fontSize = DoradoTokens.TYPE_HEADER_CROPPED.dp,
            color = if (engine.sleepEnabled) colors.textPrimary else colors.textInactive,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        onEngine(
                            if (engine.sleepEnabled) {
                                AlarmClockEngine.disableSleep(engine)
                            } else {
                                AlarmClockEngine.setSleep(engine, engine.sleepMinutes, engine.sleepVolume)
                            },
                        )
                    },
                    onLongClick = { onEngine(AlarmClockEngine.restartSleep(engine)) },
                ),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimeWheel(
                items = (1..60).map { "%d".format(it) },
                index = (engine.sleepMinutes - 1).coerceIn(0, 59),
                onIndex = { onEngine(engine.copy(sleepMinutes = it + 1)) },
            )
            EdgeCropText(
                text = "minutes",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        EdgeCropText(
            text = "volume",
            fontSize = DoradoTokens.TYPE_CAPTION.dp,
            color = colors.textSecondary,
        )
        Row(Modifier.padding(top = 6.dp)) {
            (0 until AlarmClockEngine.VOLUME_LEVELS).forEach { level ->
                EdgeCropText(
                    text = if (level == engine.sleepVolume) "on" else "off",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = if (level == engine.sleepVolume) colors.accent else colors.textInactive,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { onEngine(AlarmClockEngine.setSleepVolume(engine, level)) },
                            onLongClick = {},
                        )
                        .padding(end = 14.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        EdgeCropText(
            text = "at zero a ping plays; shake the device to restart the countdown",
            fontSize = DoradoTokens.TYPE_CAPTION.dp,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun SettingsPage(engine: AlarmClockState, onEngine: (AlarmClockState) -> Unit) {
    val colors = LocalDoradoColors.current
    Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
        EdgeCropText(
            text = if (engine.clockFormat == ClockFormat.H12) "12-hour clock" else "24-hour clock",
            fontSize = DoradoTokens.TYPE_LIST.dp,
            color = colors.accent,
            modifier = Modifier
                .combinedClickable(
                    onClick = {
                        onEngine(
                            AlarmClockEngine.setClockFormat(
                                engine,
                                if (engine.clockFormat == ClockFormat.H12) ClockFormat.H24 else ClockFormat.H12,
                            ),
                        )
                    },
                    onLongClick = {},
                )
                .padding(vertical = 6.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            EdgeCropText(
                text = "dim",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            TimeWheel(
                items = (AlarmClockEngine.DIM_MIN..AlarmClockEngine.DIM_MAX).map { "%02d".format(it) },
                index = engine.dim - AlarmClockEngine.DIM_MIN,
                onIndex = { onEngine(AlarmClockEngine.setDim(engine, it + AlarmClockEngine.DIM_MIN)) },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            EdgeCropText(
                text = "snooze",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            TimeWheel(
                items = (AlarmClockEngine.SNOOZE_MIN..AlarmClockEngine.SNOOZE_MAX).map { "%02d".format(it) },
                index = engine.snoozeMinutes - AlarmClockEngine.SNOOZE_MIN,
                onIndex = { onEngine(AlarmClockEngine.setSnoozeMinutes(engine, it + AlarmClockEngine.SNOOZE_MIN)) },
            )
        }
        Spacer(Modifier.height(8.dp))
        EdgeCropText(
            text = "alarm keeps ringing for 30 minutes, then bows out and disables itself",
            fontSize = DoradoTokens.TYPE_CAPTION.dp,
            color = colors.textSecondary,
        )
    }
}

/**
 * Snapping vertical wheel (device TimeControls): three visible rows, the
 * center one selected, wrapped. Drag or fling vertically to step.
 */
@Composable
private fun TimeWheel(
    items: List<String>,
    index: Int,
    onIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val colors = LocalDoradoColors.current
    val n = items.size
    val center = ((index % n) + n) % n
    var drag by remember { mutableStateOf(0f) }
    Column(
        modifier = modifier
            .width(56.dp)
            .pointerInput(items, index) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        val steps = (drag / 28f).roundToInt()
                        if (steps != 0) onIndex(((center - steps) % n + n) % n)
                        drag = 0f
                    },
                    onVerticalDrag = { change, delta ->
                        change.consume()
                        drag += delta
                    },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EdgeCropText(
            text = items[(center - 1 + n) % n],
            fontSize = DoradoTokens.TYPE_CAPTION.dp,
            color = colors.textInactive,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        EdgeCropText(
            text = items[center],
            fontSize = DoradoTokens.TYPE_NOW_TITLE.dp,
            color = colors.textPrimary,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        EdgeCropText(
            text = items[(center + 1) % n],
            fontSize = DoradoTokens.TYPE_CAPTION.dp,
            color = colors.textInactive,
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
}
