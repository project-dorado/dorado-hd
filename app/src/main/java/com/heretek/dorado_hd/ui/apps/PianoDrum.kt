package com.heretek.dorado_hd.ui.apps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/* ============================== Piano engine ============================== */

/**
 * Pure layout, voice-allocation and envelope maths for the 52-white-key
 * electric piano. MIDI note 21 (A0) is white key 0 and MIDI 108 (C8) is white
 * key 51, giving the standard 52/36 white/black split of an 88-key keyboard.
 */
object PianoEngine {
    const val WHITE_KEYS = 52
    const val BLACK_KEYS = 36
    const val VOICE_CAP = 14
    const val OCTAVE_NOTCHES = 7
    const val VOLUME_LEVELS = 3
    const val RELEASE_FADE_MS = 1_000L
    const val DAMPEN_GAIN = 0.7f
    const val FIRST_MIDI = 21
    const val LAST_MIDI = 108
    val VOLUME_GAINS = floatArrayOf(0.3f, 0.6f, 1.0f)

    /** Semitone offset of each white key within an A-anchored 7-key group. */
    private val WHITE_OFFSETS = intArrayOf(0, 2, 3, 5, 7, 8, 10)
    private val BLACK_PCS = setOf(9, 0, 2, 5, 7)
    private val NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    data class PianoKey(val whiteIndex: Int, val black: Boolean, val midi: Int)

    data class Voice(
        val midi: Int,
        val startedMs: Long,
        val held: Boolean = true,
        val releasedAtMs: Long? = null,
    )

    data class PianoState(
        val whiteOffset: Float = 0f,
        val volume: Int = 1,
        val notation: Boolean = false,
        val sustain: Boolean = false,
        val dampen: Boolean = false,
        val voices: List<Voice> = emptyList(),
    ) {
        val gain: Float
            get() = VOLUME_GAINS[volume.coerceIn(0, VOLUME_LEVELS - 1)] * if (dampen) DAMPEN_GAIN else 1f
    }

    fun whiteMidi(whiteIndex: Int): Int {
        val i = whiteIndex.coerceIn(0, WHITE_KEYS - 1)
        return FIRST_MIDI + (i / 7) * 12 + WHITE_OFFSETS[i % 7]
    }

    fun blackMidi(whiteIndex: Int): Int = whiteMidi(whiteIndex) + 1

    fun hasBlackAfter(whiteIndex: Int): Boolean =
        whiteIndex in 0 until WHITE_KEYS - 1 && (whiteMidi(whiteIndex) % 12) in BLACK_PCS

    fun whiteKeys(): List<PianoKey> = (0 until WHITE_KEYS).map { PianoKey(it, false, whiteMidi(it)) }

    fun blackKeys(): List<PianoKey> =
        (0 until WHITE_KEYS).filter { hasBlackAfter(it) }.map { PianoKey(it, true, blackMidi(it)) }

    fun midiToFrequency(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    fun noteName(midi: Int): String {
        val octave = midi / 12 - 1
        return NAMES[((midi % 12) + 12) % 12] + octave
    }

    /** White index of middle C (C4) plus or minus whole octaves. */
    fun snapWhiteIndex(octave: Int): Int = (2 + (octave - 1) * 7).coerceIn(0, WHITE_KEYS - 1)

    fun octaveForOffset(whiteOffset: Float): Int =
        ((whiteOffset - 2f) / 7f).roundToInt().plus(1).coerceIn(1, OCTAVE_NOTCHES)

    fun snapOffset(whiteOffset: Float): Float = snapWhiteIndex(octaveForOffset(whiteOffset)).toFloat()

    /**
     * Hit-test a keyboard-local touch. Black keys claim the top band; white
     * keys claim the rest. [whiteOffset] is in white-key units (device
     * `WhiteKeyOffset`).
     */
    fun hitTest(
        xPx: Float,
        yPx: Float,
        whiteWidth: Float,
        blackWidth: Float,
        blackHeight: Float,
        whiteOffset: Float,
    ): PianoKey? {
        if (whiteWidth <= 0f) return null
        val canvasX = xPx + whiteOffset * whiteWidth
        if (yPx <= blackHeight) {
            var bestIndex = -1
            var bestDistance = Float.MAX_VALUE
            for (i in 0 until WHITE_KEYS) {
                if (!hasBlackAfter(i)) continue
                val center = (i + 1) * whiteWidth
                val distance = abs(canvasX - center)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = i
                }
            }
            if (bestIndex >= 0 && bestDistance <= blackWidth / 2f) {
                return PianoKey(bestIndex, true, blackMidi(bestIndex))
            }
        }
        val white = floor(canvasX / whiteWidth).toInt()
        if (white !in 0 until WHITE_KEYS) return null
        return PianoKey(white, false, whiteMidi(white))
    }

    /** Points from [fromX] to [toX] no further than [stepPx] apart. */
    fun samplePath(fromX: Float, toX: Float, stepPx: Float = 10f): List<Float> {
        val distance = abs(toX - fromX)
        if (distance < 0.01f) return listOf(toX)
        val steps = ceil(distance / stepPx).toInt().coerceAtLeast(1)
        return (1..steps).map { fromX + (toX - fromX) * it / steps }
    }

    /** Keep at most [cap] voices, evicting the oldest first. */
    fun allocate(voices: List<Voice>, incoming: Voice, cap: Int = VOICE_CAP): List<Voice> {
        val kept = if (voices.size >= cap) voices.drop(voices.size - cap + 1) else voices
        return kept + incoming
    }

    fun press(state: PianoState, key: PianoKey, nowMs: Long): PianoState {
        val without = state.voices.filterNot { it.midi == key.midi && it.held }
        return state.copy(voices = allocate(without, Voice(key.midi, nowMs)))
    }

    fun release(state: PianoState, midi: Int, nowMs: Long): PianoState =
        state.copy(
            voices = state.voices.map { voice ->
                if (voice.midi != midi || !voice.held) {
                    voice
                } else if (state.sustain) {
                    voice.copy(held = false, releasedAtMs = null)
                } else {
                    voice.copy(held = false, releasedAtMs = nowMs)
                }
            },
        )

    /** Sustain off: every ringing released note starts its 1 s fade. */
    fun unsustain(state: PianoState, nowMs: Long): PianoState =
        state.copy(
            voices = state.voices.map { voice ->
                if (!voice.held && voice.releasedAtMs == null) voice.copy(releasedAtMs = nowMs) else voice
            },
        )

    fun prune(state: PianoState, nowMs: Long): PianoState =
        state.copy(voices = state.voices.filter { gainFor(it, nowMs, state.dampen) > 0.001f })

    fun gainFor(voice: Voice, nowMs: Long, dampen: Boolean): Float {
        val envelope = if (voice.releasedAtMs == null) {
            1f
        } else {
            (1f - (nowMs - voice.releasedAtMs).toFloat() / RELEASE_FADE_MS).coerceIn(0f, 1f)
        }
        return envelope * if (dampen) DAMPEN_GAIN else 1f
    }

    fun audibleMidis(state: PianoState, nowMs: Long): Set<Int> =
        state.voices.filter { gainFor(it, nowMs, state.dampen) > 0.001f }.map { it.midi }.toSet()

    fun cycleVolume(volume: Int): Int = (volume + 1) % VOLUME_LEVELS
}

/* ============================== Piano synthesis ============================== */

/**
 * Cached per-note piano samples.
 *
 * Limitation (documented): [MiniSynth] exposes a single `AudioTrack` whose
 * `write` calls serialise, so overlapping notes are queued in write order
 * rather than sample-accurately mixed. Per-note samples are pre-rendered once
 * and kept short (~450 ms) so key-press latency stays bounded; chords may
 * smear slightly compared with the device's 14-voice mixer.
 */
class PianoVoiceBank(private val synth: MiniSynth) {
    private val cache = mutableMapOf<Int, DoubleArray>()
    private val active = ArrayDeque<Job>()

    fun samples(midi: Int): DoubleArray = cache.getOrPut(midi) {
        pianoNote(PianoEngine.midiToFrequency(midi), durationMs = 450, sampleRate = synth.sampleRate)
    }

    fun play(midi: Int) {
        while (active.size >= PianoEngine.VOICE_CAP) active.removeFirstOrNull()?.cancel()
        val job = synth.scope.launch { synth.playSamples(samples(midi)) }
        active.addLast(job)
    }

    fun stop() {
        active.forEach { it.cancel() }
        active.clear()
        cache.clear()
    }
}

/** Fast-attack, long-decay voice with three partials; synth-only. */
fun pianoNote(freqHz: Double, durationMs: Int, sampleRate: Int = 22050): DoubleArray {
    val n = (durationMs * sampleRate / 1000).coerceAtLeast(1)
    val out = DoubleArray(n)
    val attack = (n / 80).coerceAtLeast(1)
    val w1 = 2 * PI * freqHz / sampleRate
    val w2 = 2 * PI * freqHz * 2.0 / sampleRate
    val w3 = 2 * PI * freqHz * 3.0 / sampleRate
    for (i in 0 until n) {
        val t = i.toDouble() / n
        val attackEnv = if (i < attack) i.toDouble() / attack else 1.0
        val env = attackEnv * (0.55 * kotlin.math.exp(-3.4 * t) + 0.45 * kotlin.math.exp(-1.1 * t))
        val body = sin(i * w1) + 0.32 * sin(i * w2) + 0.14 * sin(i * w3)
        out[i] = body * env * 0.32
    }
    return out
}

/* ============================== Piano UI ============================== */

@Composable
fun PianoApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    val voices = remember { PianoVoiceBank(synth) }
    var piano by remember { mutableStateOf(PianoEngine.PianoState()) }
    var controlPage by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        synth.start()
        onDispose {
            voices.stop()
            synth.stop()
        }
    }

    LaunchedEffect(Unit) {
        graph.appState.get("piano")?.let { blob ->
            val map = blob.split(';').mapNotNull {
                val p = it.split('=')
                if (p.size == 2) p[0] to p[1] else null
            }.toMap()
            piano = piano.copy(
                volume = map["volume"]?.toIntOrNull()?.coerceIn(0, PianoEngine.VOLUME_LEVELS - 1) ?: 1,
                notation = map["notation"] == "1",
            )
        }
    }

    LaunchedEffect(piano.volume, piano.notation) {
        graph.appState.put("piano", "volume=${piano.volume};notation=${if (piano.notation) 1 else 0}")
    }

    fun playKey(key: PianoEngine.PianoKey) {
        piano = PianoEngine.press(piano, key, System.currentTimeMillis())
        voices.play(key.midi)
    }

    fun releaseMidi(midi: Int) {
        piano = PianoEngine.release(piano, midi, System.currentTimeMillis())
    }

    DetailScaffold(title = "piano") {
        Column(
            Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (controlPage == 0) {
                    EdgeCropText(
                        text = "notation: ${if (piano.notation) "on" else "off"}",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = if (piano.notation) colors.accent else colors.textSecondary,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures { piano = piano.copy(notation = !piano.notation) }
                        },
                    )
                    EdgeCropText(
                        text = "volume: ${listOf("low", "medium", "high")[piano.volume]}",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = colors.textSecondary,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures { piano = piano.copy(volume = PianoEngine.cycleVolume(piano.volume)) }
                        },
                    )
                } else {
                    EdgeCropText(
                        text = "dampen: ${if (piano.dampen) "on" else "off"}",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = if (piano.dampen) colors.accent else colors.textSecondary,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures { piano = piano.copy(dampen = !piano.dampen) }
                        },
                    )
                    EdgeCropText(
                        text = "sustain: ${if (piano.sustain) "on" else "off"}",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = if (piano.sustain) colors.accent else colors.textSecondary,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures {
                                piano = if (piano.sustain) {
                                    PianoEngine.unsustain(piano.copy(sustain = false), System.currentTimeMillis())
                                } else {
                                    piano.copy(sustain = true)
                                }
                            }
                        },
                    )
                }
                EdgeCropText(
                    text = "⇄ controls",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.textInactive,
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures { controlPage = 1 - controlPage } },
                )
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                OctaveBar(
                    whiteOffset = piano.whiteOffset,
                    onScrub = { piano = piano.copy(whiteOffset = it) },
                    onSnap = { octave -> piano = piano.copy(whiteOffset = PianoEngine.snapWhiteIndex(octave).toFloat()) },
                    modifier = Modifier.width(24.dp).fillMaxHeight(),
                )
                Spacer(Modifier.width(6.dp))
                PianoKeyboard(
                    state = piano,
                    onPress = { playKey(it) },
                    onRelease = { releaseMidi(it) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun OctaveBar(
    whiteOffset: Float,
    onScrub: (Float) -> Unit,
    onSnap: (Int) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalDoradoColors.current
    val current = PianoEngine.octaveForOffset(whiteOffset)
    Box(
        modifier
            .background(colors.elevated)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { onSnap(current) },
                ) { change, _ ->
                    val fraction = (change.position.y / size.height).coerceIn(0f, 1f)
                    val first = PianoEngine.snapWhiteIndex(1).toFloat()
                    val last = PianoEngine.snapWhiteIndex(PianoEngine.OCTAVE_NOTCHES).toFloat()
                    onScrub(first + fraction * (last - first))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    val fraction = (position.y / size.height).coerceIn(0f, 1f)
                    val octave = (1 + fraction * (PianoEngine.OCTAVE_NOTCHES - 1)).roundToInt()
                    onSnap(octave)
                }
            },
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            (1..PianoEngine.OCTAVE_NOTCHES).forEach { octave ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(if (octave == current) colors.accent else colors.border),
                )
            }
        }
    }
}

@Composable
private fun PianoKeyboard(
    state: PianoEngine.PianoState,
    onPress: (PianoEngine.PianoKey) -> Unit,
    onRelease: (Int) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalDoradoColors.current
    val latest = rememberUpdatedState(state)
    val latestPress = rememberUpdatedState(onPress)
    val latestRelease = rememberUpdatedState(onRelease)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val whiteWidth = with(density) { DoradoTokens.PIANO_KEY_W.dp.toPx() }
    val blackWidth = with(density) { DoradoTokens.PIANO_BLACK_W.dp.toPx() }
    val blackHeight = with(density) { DoradoTokens.PIANO_BLACK_H.dp.toPx() }
    val nameStyle = TextStyle(fontFamily = Selawik, fontSize = 8.sp, color = Color.Black)

    Canvas(
        modifier.pointerInput(whiteWidth) {
            awaitPointerEventScope {
                val active = mutableMapOf<Long, Int>()
                while (true) {
                    val event = awaitPointerEvent()
                    val snapshot = latest.value
                    for (change in event.changes) {
                        val id = change.id.value
                        val position = change.position
                        when {
                            change.pressed && !change.previousPressed -> {
                                PianoEngine.hitTest(
                                    position.x, position.y, whiteWidth, blackWidth, blackHeight, snapshot.whiteOffset,
                                )?.let { key ->
                                    active[id] = key.midi
                                    latestPress.value(key)
                                }
                            }
                            change.pressed -> {
                                val key = active[id]
                                val previous = change.previousPosition
                                if (key != null && (position - previous).getDistance() > 10f) {
                                    PianoEngine.samplePath(previous.x, position.x, 10f).forEach { x ->
                                        val hit = PianoEngine.hitTest(
                                            x, position.y, whiteWidth, blackWidth, blackHeight, snapshot.whiteOffset,
                                        )
                                        if (hit != null && hit.midi != active[id]) {
                                            active[id]?.let { latestRelease.value(it) }
                                            active[id] = hit.midi
                                            latestPress.value(hit)
                                        }
                                    }
                                }
                            }
                            else -> {
                                active.remove(id)?.let { latestRelease.value(it) }
                            }
                        }
                        change.consume()
                    }
                }
            }
        },
    ) {
        val offset = state.whiteOffset
        val audible = PianoEngine.audibleMidis(state, System.currentTimeMillis())
        drawRect(color = colors.elevated)
        val first = floor(offset).toInt().coerceAtLeast(0)
        val last = (first + (size.width / whiteWidth).toInt() + 2).coerceAtMost(PianoEngine.WHITE_KEYS - 1)
        for (i in first..last) {
            val midi = PianoEngine.whiteMidi(i)
            val x = (i - offset) * whiteWidth
            drawRect(color = Color.White, topLeft = Offset(x, 0f), size = Size(whiteWidth - 1f, size.height))
            if (midi in audible) {
                drawRect(
                    color = colors.accent.copy(alpha = 0.45f),
                    topLeft = Offset(x, 0f),
                    size = Size(whiteWidth - 1f, size.height),
                )
            }
            if (state.notation) {
                val label = textMeasurer.measure(PianoEngine.noteName(midi), nameStyle)
                drawText(
                    textMeasurer = textMeasurer,
                    text = PianoEngine.noteName(midi),
                    topLeft = Offset(x + (whiteWidth - label.size.width) / 2f, size.height - label.size.height - 4f),
                    style = nameStyle,
                )
            }
        }
        for (key in PianoEngine.blackKeys()) {
            val x = (key.whiteIndex + 1 - offset) * whiteWidth - blackWidth / 2f
            if (x + blackWidth < 0f || x > size.width) continue
            drawRect(color = Color.Black, topLeft = Offset(x, 0f), size = Size(blackWidth, blackHeight))
            if (key.midi in audible) {
                drawRect(
                    color = colors.accent.copy(alpha = 0.6f),
                    topLeft = Offset(x, 0f),
                    size = Size(blackWidth, blackHeight),
                )
            }
        }
    }
}

/* ============================== Drum engine ============================== */

/**
 * Pure drum-pad model and pattern sequencer. Pads are freely placed on the
 * stage; zones choose between the two layered samples, held pads retrigger on
 * their loop period, and record/playback keeps timestamped events in order.
 */
object DrumMachineEngine {
    const val MIN_BPM = 40
    const val MAX_BPM = 340
    const val DEFAULT_BPM = 120
    const val VOICE_CAP = 12
    const val DEFAULT_LOOP_SECONDS = 0.25
    const val SPLIT_PX = 15f
    const val RIDE_SPLIT = 40f
    const val STAGE_W = 272f
    const val STAGE_H = 480f

    enum class DrumType { KICK, TOM, SNARE, HIHAT, CRASH, RIDE, CLAP, MISC }

    enum class Mode { PLAY, ARRANGE, RECORD, PLAYBACK }

    enum class Zone(val index: Int) { ONE(1), TWO(2) }

    data class DrumPad(
        val id: Int,
        val x: Float,
        val y: Float,
        val size: Float,
        val type: DrumType,
        val soundA: String,
        val soundB: String,
        val loop: Boolean = true,
        val loopSeconds: Double = DEFAULT_LOOP_SECONDS,
        val layerDepth: Int = 0,
    )

    data class DrumEvent(val atMs: Long, val padId: Int, val zone: Zone)

    data class DrumMachineState(
        val mode: Mode = Mode.PLAY,
        val bpm: Int = DEFAULT_BPM,
        val pads: List<DrumPad> = standardArrangement(),
        val recorded: List<DrumEvent> = emptyList(),
        val playback: List<DrumEvent> = emptyList(),
        val selectedPad: Int? = null,
        val metronome: Boolean = false,
        val recordStartMs: Long = 0L,
    )

    /**
     * Zone split: hats are open left / closed right; rides split bell (centre)
     * from edge radially; the other types fall back to the 15 px split.
     */
    fun zoneFor(pad: DrumPad, localX: Float, localY: Float): Zone = when (pad.type) {
        DrumType.HIHAT -> if (localX > SPLIT_PX) Zone.TWO else Zone.ONE
        DrumType.RIDE -> {
            val dx = localX - pad.size / 2f
            val dy = localY - pad.size / 2f
            val radius = RIDE_SPLIT * pad.size
            if (dx * dx + dy * dy <= radius * radius) Zone.ONE else Zone.TWO
        }
        else -> if (localX > SPLIT_PX) Zone.TWO else Zone.ONE
    }

    fun contains(pad: DrumPad, x: Float, y: Float): Boolean =
        x >= pad.x && x <= pad.x + pad.size && y >= pad.y && y <= pad.y + pad.size

    /** Topmost pad at a stage point (higher [DrumPad.layerDepth] wins). */
    fun padAt(state: DrumMachineState, x: Float, y: Float): DrumPad? =
        state.pads.filter { contains(it, x, y) }.maxByOrNull { it.layerDepth }

    fun setBpm(state: DrumMachineState, bpm: Int): DrumMachineState =
        state.copy(bpm = bpm.coerceIn(MIN_BPM, MAX_BPM))

    fun movePad(state: DrumMachineState, padId: Int, dx: Float, dy: Float): DrumMachineState =
        state.copy(
            pads = state.pads.map { pad ->
                if (pad.id != padId) {
                    pad
                } else {
                    pad.copy(
                        x = (pad.x + dx).coerceIn(0f, STAGE_W - pad.size),
                        y = (pad.y + dy).coerceIn(0f, STAGE_H - pad.size),
                    )
                }
            },
        )

    fun beginRecording(state: DrumMachineState, nowMs: Long): DrumMachineState =
        state.copy(mode = Mode.RECORD, recorded = emptyList(), recordStartMs = nowMs)

    fun record(state: DrumMachineState, atMs: Long, padId: Int, zone: Zone): DrumMachineState =
        state.copy(recorded = state.recorded + DrumEvent((atMs - state.recordStartMs).coerceAtLeast(0L), padId, zone))

    fun beginPlayback(state: DrumMachineState): DrumMachineState =
        state.copy(mode = Mode.PLAYBACK, playback = state.recorded.sortedBy { it.atMs })

    fun stopPlayback(state: DrumMachineState): DrumMachineState =
        state.copy(mode = Mode.PLAY, playback = emptyList())

    /** Events due at [elapsedMs], removed from the queue one-shot. */
    fun dueEvents(state: DrumMachineState, elapsedMs: Long): Pair<List<DrumEvent>, DrumMachineState> {
        val due = state.playback.filter { it.atMs <= elapsedMs }.sortedBy { it.atMs }
        return due to state.copy(playback = state.playback.filterNot { it.atMs <= elapsedMs })
    }

    fun loopIntervalMs(loopSeconds: Double): Long =
        (loopSeconds.coerceAtLeast(0.01) * 1000.0).roundToLong().coerceAtLeast(1L)

    /** Retrigger instants for a pad held since [heldSinceMs], excluding the hit. */
    fun pendingLoopTriggers(heldSinceMs: Long, nowMs: Long, loopSeconds: Double): List<Long> {
        val interval = loopIntervalMs(loopSeconds)
        if (nowMs <= heldSinceMs) return emptyList()
        val count = ((nowMs - heldSinceMs) / interval).toInt()
        return (1..count).map { heldSinceMs + it * interval }
    }

    fun allocateVoices(active: List<Long>, incoming: Long, cap: Int = VOICE_CAP): List<Long> {
        val kept = if (active.size >= cap) active.drop(active.size - cap + 1) else active
        return kept + incoming
    }

    fun metronomePeriodMs(bpm: Int): Long = 60_000L / bpm.coerceIn(MIN_BPM, MAX_BPM)

    fun preset(index: Int): List<DrumPad> = when (index) {
        1 -> listOf(
            DrumPad(10, 8f, 8f, 84f, DrumType.KICK, "kick", "kick2", layerDepth = 0),
            DrumPad(11, 100f, 8f, 84f, DrumType.SNARE, "snare", "snare2", layerDepth = 0),
            DrumPad(12, 192f, 8f, 72f, DrumType.HIHAT, "hatOpen", "hatClosed", layerDepth = 0),
            DrumPad(13, 8f, 100f, 84f, DrumType.TOM, "tom", "tom2", layerDepth = 0),
            DrumPad(14, 100f, 100f, 84f, DrumType.CLAP, "clap", "clap2", layerDepth = 0),
            DrumPad(15, 192f, 100f, 72f, DrumType.CRASH, "crash", "crash2", layerDepth = 0),
            DrumPad(16, 8f, 192f, 84f, DrumType.RIDE, "ride", "ride2", layerDepth = 0),
            DrumPad(17, 100f, 192f, 84f, DrumType.MISC, "misc", "misc2", layerDepth = 0),
        )
        2 -> listOf(
            DrumPad(20, 8f, 8f, 128f, DrumType.KICK, "kick", "kick2", layerDepth = 1),
            DrumPad(21, 144f, 8f, 128f, DrumType.SNARE, "snare", "snare2", layerDepth = 1),
            DrumPad(22, 8f, 144f, 128f, DrumType.HIHAT, "hatOpen", "hatClosed", layerDepth = 1),
            DrumPad(23, 144f, 144f, 128f, DrumType.CRASH, "crash", "crash2", layerDepth = 1),
            DrumPad(24, 8f, 280f, 128f, DrumType.TOM, "tom", "tom2", layerDepth = 1),
            DrumPad(25, 144f, 280f, 128f, DrumType.CLAP, "clap", "clap2", layerDepth = 1),
        )
        else -> listOf(
            DrumPad(30, 8f, 8f, 84f, DrumType.KICK, "kick", "kick2", layerDepth = 2),
            DrumPad(31, 100f, 8f, 84f, DrumType.SNARE, "snare", "snare2", layerDepth = 2),
            DrumPad(32, 192f, 8f, 72f, DrumType.HIHAT, "hatOpen", "hatClosed", layerDepth = 2),
            DrumPad(33, 8f, 100f, 84f, DrumType.TOM, "tom", "tom2", layerDepth = 2),
            DrumPad(34, 100f, 100f, 84f, DrumType.RIDE, "ride", "ride2", layerDepth = 2),
            DrumPad(35, 192f, 100f, 72f, DrumType.CLAP, "clap", "clap2", layerDepth = 2),
            DrumPad(36, 8f, 192f, 84f, DrumType.MISC, "misc", "misc2", layerDepth = 2),
            DrumPad(37, 100f, 192f, 84f, DrumType.CRASH, "crash", "crash2", layerDepth = 2),
            DrumPad(38, 192f, 192f, 72f, DrumType.HIHAT, "hatOpen", "hatClosed", layerDepth = 2),
        )
    }

    fun standardArrangement(): List<DrumPad> = preset(0)

    fun encode(state: DrumMachineState): String {
        val pads = state.pads.joinToString("|") { p ->
            listOf(
                p.id, p.type.name, p.soundA, p.soundB, p.x, p.y, p.size,
                if (p.loop) 1 else 0, p.loopSeconds, p.layerDepth,
            ).joinToString(":")
        }
        return "bpm=${state.bpm};metro=${if (state.metronome) 1 else 0};pads=$pads"
    }

    fun decode(blob: String?): DrumMachineState? {
        if (blob.isNullOrBlank()) return null
        val map = blob.split(';').mapNotNull {
            val p = it.split('=', limit = 2)
            if (p.size == 2) p[0] to p[1] else null
        }.toMap()
        val bpm = map["bpm"]?.toIntOrNull() ?: return null
        val pads = map["pads"].orEmpty().split('|').mapNotNull { row ->
            val f = row.split(':')
            if (f.size < 10) {
                null
            } else {
                runCatching {
                    DrumPad(
                        id = f[0].toInt(),
                        x = f[4].toFloat(),
                        y = f[5].toFloat(),
                        size = f[6].toFloat(),
                        type = DrumType.valueOf(f[1]),
                        soundA = f[2],
                        soundB = f[3],
                        loop = f[7] == "1",
                        loopSeconds = f[8].toDouble(),
                        layerDepth = f[9].toInt(),
                    )
                }.getOrNull()
            }
        }
        return DrumMachineState(
            bpm = bpm.coerceIn(MIN_BPM, MAX_BPM),
            pads = pads.ifEmpty { standardArrangement() },
            metronome = map["metro"] == "1",
        )
    }
}

/* ============================== Drum synthesis ============================== */

/** Deterministic synthesized kit voices (no sampled assets). */
object DrumVoices {
    private val cache = mutableMapOf<String, DoubleArray>()

    fun samples(name: String, sampleRate: Int = 22050): DoubleArray =
        cache.getOrPut("$name@$sampleRate") {
            when (name) {
                "kick" -> sweptTone(sampleRate, 150.0, 45.0, 170, 0.85)
                "kick2" -> sweptTone(sampleRate, 90.0, 40.0, 220, 0.8)
                "snare" -> mix(listOf(sfxNoise(130, sampleRate, gain = 0.5, decay = 7.0, seed = 7, lowPass = 0.55), sfxTone(190.0, 110, sampleRate, decay = 6.0, gain = 0.35)))
                "snare2" -> mix(listOf(sfxNoise(220, sampleRate, gain = 0.45, decay = 4.0, seed = 9, lowPass = 0.5), sfxTone(150.0, 180, sampleRate, decay = 4.5, gain = 0.3)))
                "hatClosed" -> sfxNoise(60, sampleRate, gain = 0.4, decay = 9.0, seed = 13, lowPass = 0.95)
                "hatOpen" -> sfxNoise(260, sampleRate, gain = 0.34, decay = 4.0, seed = 15, lowPass = 0.85)
                "tom" -> sfxTone(190.0, 260, sampleRate, decay = 4.0, gain = 0.5)
                "tom2" -> sfxTone(130.0, 320, sampleRate, decay = 3.5, gain = 0.5)
                "clap" -> mix(listOf(sfxNoise(30, sampleRate, gain = 0.5, decay = 8.0, seed = 3, lowPass = 0.8), sfxNoise(40, sampleRate, gain = 0.4, decay = 6.0, seed = 5, lowPass = 0.7)))
                "clap2" -> sfxNoise(90, sampleRate, gain = 0.45, decay = 5.0, seed = 11, lowPass = 0.6)
                "crash" -> mix(listOf(sfxNoise(700, sampleRate, gain = 0.4, decay = 2.4, seed = 17, lowPass = 0.25), sfxTone(5200.0, 500, sampleRate, decay = 4.0, gain = 0.1)))
                "crash2" -> sfxNoise(360, sampleRate, gain = 0.4, decay = 3.0, seed = 19, lowPass = 0.2)
                "ride" -> mix(listOf(sfxTone(3100.0, 420, sampleRate, decay = 4.5, gain = 0.18), sfxNoise(420, sampleRate, gain = 0.22, decay = 4.0, seed = 21, lowPass = 0.4)))
                "ride2" -> sfxTone(2200.0, 300, sampleRate, decay = 5.0, gain = 0.24)
                "misc" -> sfxTone(880.0, 90, sampleRate, decay = 7.0, gain = 0.4)
                else -> sfxTone(660.0, 80, sampleRate, decay = 8.0, gain = 0.4)
            }
        }

    private fun sweptTone(sampleRate: Int, fromHz: Double, toHz: Double, durationMs: Int, gain: Double): DoubleArray {
        val n = (durationMs * sampleRate / 1000).coerceAtLeast(1)
        val out = DoubleArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val freq = fromHz + (toHz - fromHz) * t
            phase += 2 * PI * freq / sampleRate
            out[i] = sin(phase) * kotlin.math.exp(-4.0 * t) * gain
        }
        return out
    }

    private fun mix(parts: List<DoubleArray>): DoubleArray {
        val length = parts.maxOfOrNull { it.size } ?: 0
        val out = DoubleArray(length)
        for (part in parts) {
            for (i in part.indices) out[i] += part[i]
        }
        for (i in out.indices) out[i] = out[i].coerceIn(-1.0, 1.0)
        return out
    }
}

/** Plays kit voices through [MiniSynth] with a 12-voice eviction cap. */
class DrumPadBank(private val synth: MiniSynth) {
    private val active = ArrayDeque<Job>()

    fun play(pad: DrumMachineEngine.DrumPad, zone: DrumMachineEngine.Zone) {
        val name = if (zone == DrumMachineEngine.Zone.ONE) pad.soundA else pad.soundB
        playName(name)
    }

    fun playName(name: String) {
        while (active.size >= DrumMachineEngine.VOICE_CAP) active.removeFirstOrNull()?.cancel()
        active.addLast(synth.scope.launch { synth.playSamples(DrumVoices.samples(name, synth.sampleRate)) })
    }

    fun playMetronome(accent: Boolean) {
        synth.scope.launch { synth.playSamples(metronomeClick(accent, synth.sampleRate)) }
    }

    fun stop() {
        active.forEach { it.cancel() }
        active.clear()
    }
}

/* ============================== Drum UI ============================== */

@Composable
fun DrumMachineApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    val bank = remember { DrumPadBank(synth) }
    var machine by remember { mutableStateOf(DrumMachineEngine.DrumMachineState()) }
    var playbackStart by remember { mutableStateOf(0L) }
    var metronomeBeat by remember { mutableStateOf(0L) }

    DisposableEffect(Unit) {
        synth.start()
        onDispose {
            bank.stop()
            synth.stop()
        }
    }

    LaunchedEffect(Unit) {
        DrumMachineEngine.decode(graph.appState.get("drums"))?.let { machine = it }
    }

    LaunchedEffect(machine.bpm, machine.metronome, machine.pads) {
        graph.appState.put("drums", DrumMachineEngine.encode(machine))
    }

    LaunchedEffect(machine.mode) {
        if (machine.mode != DrumMachineEngine.Mode.PLAYBACK) return@LaunchedEffect
        playbackStart = System.currentTimeMillis()
        while (true) {
            delay(16)
            val elapsed = System.currentTimeMillis() - playbackStart
            val (due, next) = DrumMachineEngine.dueEvents(machine, elapsed)
            due.forEach { event ->
                machine.pads.firstOrNull { it.id == event.padId }?.let { bank.play(it, event.zone) }
            }
            machine = next
            if (next.playback.isEmpty()) {
                machine = next.copy(mode = DrumMachineEngine.Mode.PLAY)
                return@LaunchedEffect
            }
        }
    }

    LaunchedEffect(machine.metronome, machine.bpm) {
        if (!machine.metronome) return@LaunchedEffect
        var beat = 0L
        while (true) {
            delay(DrumMachineEngine.metronomePeriodMs(machine.bpm))
            beat++
            metronomeBeat = beat
            bank.playMetronome(beat % 4 == 1L)
        }
    }

    fun strike(pad: DrumMachineEngine.DrumPad, zone: DrumMachineEngine.Zone) {
        bank.play(pad, zone)
        if (machine.mode == DrumMachineEngine.Mode.RECORD) {
            machine = DrumMachineEngine.record(machine, System.currentTimeMillis(), pad.id, zone)
        }
    }

    DetailScaffold(title = "drum machine") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                EdgeCropText(text = "${machine.bpm} bpm", fontSize = DoradoTokens.TYPE_NOW_META.dp, modifier = Modifier.weight(1f))
                listOf(-10, -1, 1, 10).forEach { delta ->
                    EdgeCropText(
                        text = if (delta > 0) "+$delta" else "$delta",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = colors.accent,
                        modifier = Modifier.pointerInput(delta) {
                            detectTapGestures { machine = DrumMachineEngine.setBpm(machine, machine.bpm + delta) }
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                EdgeCropText(
                    text = if (machine.mode == DrumMachineEngine.Mode.ARRANGE) "arrange ▸" else "arrange",
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = if (machine.mode == DrumMachineEngine.Mode.ARRANGE) colors.accent else colors.textSecondary,
                    modifier = Modifier.pointerInput(machine.mode) {
                        detectTapGestures {
                            machine = machine.copy(
                                mode = if (machine.mode == DrumMachineEngine.Mode.ARRANGE) DrumMachineEngine.Mode.PLAY else DrumMachineEngine.Mode.ARRANGE,
                            )
                        }
                    },
                )
                EdgeCropText(
                    text = if (machine.mode == DrumMachineEngine.Mode.RECORD) "stop rec" else "record",
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = if (machine.mode == DrumMachineEngine.Mode.RECORD) colors.accent else colors.textSecondary,
                    modifier = Modifier.pointerInput(machine.mode) {
                        detectTapGestures {
                            machine = if (machine.mode == DrumMachineEngine.Mode.RECORD) {
                                machine.copy(mode = DrumMachineEngine.Mode.PLAY)
                            } else {
                                DrumMachineEngine.beginRecording(machine, System.currentTimeMillis())
                            }
                        }
                    },
                )
                EdgeCropText(
                    text = "play",
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = if (machine.mode == DrumMachineEngine.Mode.PLAYBACK) colors.accent else colors.textSecondary,
                    modifier = Modifier.pointerInput(machine.mode, machine.recorded.size) {
                        detectTapGestures {
                            machine = if (machine.mode == DrumMachineEngine.Mode.PLAYBACK) {
                                DrumMachineEngine.stopPlayback(machine)
                            } else {
                                DrumMachineEngine.beginPlayback(machine)
                            }
                        }
                    },
                )
                EdgeCropText(
                    text = "metro",
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = if (machine.metronome) colors.accent else colors.textSecondary,
                    modifier = Modifier.pointerInput(machine.metronome) {
                        detectTapGestures { machine = machine.copy(metronome = !machine.metronome) }
                    },
                )
                listOf(1, 2, 3).forEach { preset ->
                    EdgeCropText(
                        text = "kit $preset",
                        fontSize = DoradoTokens.TYPE_CAPTION.dp,
                        color = colors.textSecondary,
                        modifier = Modifier.pointerInput(preset) {
                            detectTapGestures { machine = machine.copy(pads = DrumMachineEngine.preset(preset)) }
                        },
                    )
                }
                EdgeCropText(
                    text = if (machine.recorded.isEmpty()) "pattern ▸ empty" else "pattern ${machine.recorded.size}",
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = colors.textInactive,
                )
            }
            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f).background(colors.elevated),
            ) {
                val scale = minOf(maxWidth.value / DrumMachineEngine.STAGE_W, maxHeight.value / DrumMachineEngine.STAGE_H)
                machine.pads.sortedBy { it.layerDepth }.forEach { pad ->
                    DrumPadView(
                        pad = pad,
                        scale = scale,
                        selected = machine.selectedPad == pad.id,
                        arrange = machine.mode == DrumMachineEngine.Mode.ARRANGE,
                        onStrike = { zone -> strike(pad, zone) },
                        onSelect = { machine = machine.copy(selectedPad = pad.id) },
                        onMove = { dx, dy -> machine = DrumMachineEngine.movePad(machine, pad.id, dx / scale, dy / scale) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DrumPadView(
    pad: DrumMachineEngine.DrumPad,
    scale: Float,
    selected: Boolean,
    arrange: Boolean,
    onStrike: (DrumMachineEngine.Zone) -> Unit,
    onSelect: () -> Unit,
    onMove: (Float, Float) -> Unit,
) {
    val colors = LocalDoradoColors.current
    val scope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<Job?>(null) }
    val sizeDp = (pad.size * scale).dp
    Box(
        Modifier
            .offset(x = (pad.x * scale).dp, y = (pad.y * scale).dp)
            .size(sizeDp)
            .background(if (selected) colors.accent.copy(alpha = 0.35f) else colors.tile)
            .pointerInput(pad.id, arrange) {
                if (arrange) {
                    detectDragGestures(
                        onDragStart = { onSelect() },
                    ) { change, dragAmount ->
                        change.consume()
                        onMove(dragAmount.x, dragAmount.y)
                    }
                } else {
                    detectTapGestures(
                        onPress = { offset ->
                            val zone = DrumMachineEngine.zoneFor(pad, offset.x, offset.y)
                            onStrike(zone)
                            if (pad.loop) {
                                holdJob = scope.launch {
                                    while (isActive) {
                                        delay(DrumMachineEngine.loopIntervalMs(pad.loopSeconds))
                                        onStrike(zone)
                                    }
                                }
                            }
                            tryAwaitRelease()
                            holdJob?.cancel()
                            holdJob = null
                        },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EdgeCropText(
                text = pad.type.name.lowercase(),
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = colors.textPrimary,
            )
            if (pad.size * scale >= 60f) {
                EdgeCropText(
                    text = "${pad.soundA}/${pad.soundB}",
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
