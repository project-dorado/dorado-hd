package com.heretek.dorado_hd.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.data.model.Track
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.components.AlbumArt
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class QuizScreen { MENU, CHOOSE_MIX, ROUND, RESULTS }

@Composable
fun MusicQuizApp() {
    val graph = LocalDoradoGraph.current
    val colors = LocalDoradoColors.current
    val scope = rememberCoroutineScope()
    val tracks by graph.library.tracks().collectAsState(initial = emptyList())
    val highScores by graph.games.top("musicquiz", 50).collectAsState(initial = emptyList())
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }

    var screen by remember { mutableStateOf(QuizScreen.MENU) }
    var difficulty by remember { mutableStateOf(QuizEngine.Difficulty.NORMAL) }
    var bonusEnabled by remember { mutableStateOf(true) }
    var soundLevel by remember { mutableStateOf(2) }
    var mixName by remember { mutableStateOf("megamix") }
    var round by remember { mutableStateOf<QuizEngine.QuizState?>(null) }
    var recorded by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var mixError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    LaunchedEffect(Unit) {
        graph.appState.get("musicquiz")?.let { blob ->
            val parts = blob.split(';').mapNotNull { it.split('=').takeIf { p -> p.size == 2 } }
            val map = parts.associate { it[0] to it[1] }
            difficulty = if (map["difficulty"] == "hardcore") QuizEngine.Difficulty.HARDCORE else QuizEngine.Difficulty.NORMAL
            bonusEnabled = map["bonus"] != "0"
            soundLevel = map["sound"]?.toIntOrNull()?.coerceIn(0, 3) ?: 2
            mixName = map["mix"] ?: "megamix"
        }
    }

    fun persist() {
        scope.launch {
            graph.appState.put(
                "musicquiz",
                "difficulty=${difficulty.name.lowercase()};bonus=${if (bonusEnabled) 1 else 0};" +
                    "sound=$soundLevel;mix=$mixName",
            )
        }
    }

    fun cue(name: String) {
        if (soundLevel > 0) bank.play(name)
    }

    fun startRound() {
        val pool = if (mixName.equals("megamix", ignoreCase = true)) {
            tracks
        } else {
            tracks.filter { it.artist.equals(mixName, ignoreCase = true) }
        }
        val state = QuizEngine.startRound(
            tracks = pool,
            difficulty = difficulty,
            seed = System.currentTimeMillis(),
            bonusEnabled = bonusEnabled,
            mixName = mixName,
        )
        if (state != null) {
            round = state
            recorded = false
            paused = false
            mixError = null
            screen = QuizScreen.ROUND
        } else {
            // Never fail silently: tell the user why the round did not start.
            val valid = pool.count { QuizEngine.isContentValid(it) }
            mixError = if (valid < difficulty.optionCount) {
                "can't start — \"$mixName\" has $valid valid track${if (valid == 1) "" else "s"}; " +
                    "${difficulty.optionCount} needed for ${difficulty.name.lowercase()}"
            } else {
                "can't start a round with this mix"
            }
            cue("error")
        }
    }

    fun exitToMenu() {
        round = null
        paused = false
        screen = QuizScreen.MENU
    }

    Column(Modifier.fillMaxSize()) {
        DetailScaffold(title = "music quiz") {
            when (screen) {
                QuizScreen.MENU -> QuizMenu(
                    tracks = tracks,
                    difficulty = difficulty,
                    bonusEnabled = bonusEnabled,
                    soundLevel = soundLevel,
                    mixName = mixName,
                    highScores = highScores.filter { it.meta == QuizEngine.highScoreKey(mixName, difficulty) },
                    error = mixError,
                    onToggleDifficulty = {
                        difficulty = if (difficulty == QuizEngine.Difficulty.NORMAL) QuizEngine.Difficulty.HARDCORE else QuizEngine.Difficulty.NORMAL
                        mixError = null
                        cue("select")
                        persist()
                    },
                    onToggleBonus = { bonusEnabled = !bonusEnabled; cue("select"); persist() },
                    onCycleSound = { soundLevel = (soundLevel + 1) % 4; persist() },
                    onChooseMix = { screen = QuizScreen.CHOOSE_MIX },
                    onStart = { cue("score"); startRound() },
                )
                QuizScreen.CHOOSE_MIX -> QuizMixPicker(
                    tracks = tracks,
                    current = mixName,
                    onPick = { name -> mixName = name; mixError = null; persist(); cue("select"); screen = QuizScreen.MENU },
                    onBack = { screen = QuizScreen.MENU },
                )
                QuizScreen.ROUND -> {
                    val state = round
                    if (state == null) {
                        QuizMenu(
                            tracks = tracks,
                            difficulty = difficulty,
                            bonusEnabled = bonusEnabled,
                            soundLevel = soundLevel,
                            mixName = mixName,
                            highScores = emptyList(),
                            error = mixError,
                            onToggleDifficulty = {},
                            onToggleBonus = {},
                            onCycleSound = {},
                            onChooseMix = { screen = QuizScreen.CHOOSE_MIX },
                            onStart = { startRound() },
                        )
                    } else {
                        QuizRound(
                            state = state,
                            tracks = tracks,
                            paused = paused,
                            soundLevel = soundLevel,
                            onAnswer = { index ->
                                val before = state.question
                                val answered = QuizEngine.answer(state, index)
                                if (answered.phase == QuizEngine.Phase.REVEAL) {
                                    val wasCorrect = answered.chosenIndex == before?.answerIndex
                                    cue(if (wasCorrect) "score" else "error")
                                }
                                round = answered
                            },
                            onHint = {
                                val hinted = QuizEngine.useHint(state, tracks)
                                if (hinted.hintsUsed != state.hintsUsed) {
                                    state.question?.mediaId?.let { id ->
                                        tracks.firstOrNull { it.mediaId == id }?.let { track ->
                                            graph.controller.play(listOf(track), 0)
                                            graph.controller.seekTo(hinted.hintSeekMs)
                                        }
                                    }
                                    cue("pop")
                                }
                                round = hinted
                            },
                            onPlayClip = {
                                state.question?.mediaId?.let { id ->
                                    tracks.firstOrNull { it.mediaId == id }?.let { track ->
                                        graph.controller.play(listOf(track), 0)
                                    }
                                }
                            },
                            onBonusAnswer = { index ->
                                val bonus = state.bonusQuestion
                                val isCorrect = bonus != null && index == bonus.answerIndex
                                cue(if (isCorrect) "coin" else "error")
                                round = QuizEngine.answerBonus(state, tracks, index)
                            },
                            onPause = { paused = !paused },
                            onQuit = { exitToMenu() },
                        )
                    }
                }
                QuizScreen.RESULTS -> {
                    val state = round
                    if (state != null) {
                        LaunchedEffect(state) {
                            if (!recorded) {
                                recorded = true
                                cue("win")
                                scope.launch {
                                    graph.games.record(
                                        "musicquiz",
                                        state.score,
                                        QuizEngine.highScoreKey(state.mixName, state.difficulty),
                                    )
                                }
                            }
                        }
                        QuizResults(
                            state = state,
                            best = highScores
                                .filter { it.meta == QuizEngine.highScoreKey(state.mixName, state.difficulty) }
                                .maxOfOrNull { it.score } ?: 0,
                            onAgain = { cue("select"); startRound() },
                            onMenu = { exitToMenu() },
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(round?.questionNumber, round?.phase) {
        val state = round ?: return@LaunchedEffect
        if (state.phase != QuizEngine.Phase.QUESTION && state.phase != QuizEngine.Phase.BONUS) return@LaunchedEffect
        while (true) {
            delay(100)
            val current = round ?: return@LaunchedEffect
            if (paused) continue
            if (current.phase != QuizEngine.Phase.QUESTION && current.phase != QuizEngine.Phase.BONUS) return@LaunchedEffect
            val ticked = QuizEngine.tick(current, 0.1f)
            round = ticked
            if (ticked.phase != current.phase) return@LaunchedEffect
        }
    }

    LaunchedEffect(round?.phase, round?.questionNumber, round?.bonusCorrect) {
        val state = round ?: return@LaunchedEffect
        when (state.phase) {
            QuizEngine.Phase.REVEAL -> {
                delay(1100)
                round?.let { round = QuizEngine.proceed(it, tracks) }
                if (round?.phase == QuizEngine.Phase.RESULTS) screen = QuizScreen.RESULTS
            }
            QuizEngine.Phase.RESULTS -> screen = QuizScreen.RESULTS
            else -> Unit
        }
    }
}

@Composable
private fun QuizMenu(
    tracks: List<Track>,
    difficulty: QuizEngine.Difficulty,
    bonusEnabled: Boolean,
    soundLevel: Int,
    mixName: String,
    highScores: List<com.heretek.dorado_hd.data.db.GameScoreEntity>,
    error: String?,
    onToggleDifficulty: () -> Unit,
    onToggleBonus: () -> Unit,
    onCycleSound: () -> Unit,
    onChooseMix: () -> Unit,
    onStart: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val soundLabels = listOf("off", "low", "medium", "high")
    Column(
        Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        EdgeCropText(text = "mega mix", fontSize = DoradoTokens.TYPE_MENU_ITEM.dp, color = colors.accent, modifier = Modifier.pointerInput(Unit) { tap { onStart() } })
        EdgeCropText(text = "custom mix — $mixName", fontSize = DoradoTokens.TYPE_LIST.dp, color = colors.textSecondary, modifier = Modifier.pointerInput(Unit) { tap { onChooseMix() } })
        EdgeCropText(text = "answers: ${difficulty.name.lowercase()} (${difficulty.optionCount})", fontSize = DoradoTokens.TYPE_LIST.dp, modifier = Modifier.pointerInput(Unit) { tap { onToggleDifficulty() } })
        EdgeCropText(text = "bonus round: ${if (bonusEnabled) "on" else "off"}", fontSize = DoradoTokens.TYPE_LIST.dp, modifier = Modifier.pointerInput(Unit) { tap { onToggleBonus() } })
        EdgeCropText(text = "sound: ${soundLabels[soundLevel]}", fontSize = DoradoTokens.TYPE_LIST.dp, modifier = Modifier.pointerInput(Unit) { tap { onCycleSound() } })
        if (tracks.count { QuizEngine.isContentValid(it) } < difficulty.optionCount) {
            EdgeCropText(text = "need more content for this mode", fontSize = DoradoTokens.TYPE_NOW_META.dp, color = colors.textInactive)
        }
        if (error != null) {
            EdgeCropText(text = error, fontSize = DoradoTokens.TYPE_NOW_META.dp, color = colors.accentBright)
        }
        Spacer(Modifier.height(4.dp))
        EdgeCropText(
            text = "high scores — ${QuizEngine.highScoreKey(mixName, difficulty)}",
            fontSize = DoradoTokens.TYPE_CAPTION.dp,
            color = colors.textSecondary,
        )
        if (highScores.isEmpty()) {
            EdgeCropText(text = "none yet", fontSize = DoradoTokens.TYPE_LIST.dp, color = colors.textInactive)
        } else {
            highScores.take(5).forEachIndexed { i, row ->
                EdgeCropText(
                    text = "${i + 1}. ${row.score}",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = if (i == 0) colors.accent else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun QuizMixPicker(
    tracks: List<Track>,
    current: String,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val mixes = remember(tracks) {
        listOf("megamix") + tracks.map { it.artist }.filter { it.isNotBlank() }.distinct().sorted()
    }
    Column(
        Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EdgeCropText(text = "custom mix", fontSize = DoradoTokens.TYPE_NOW_TITLE.dp)
        EdgeCropText(text = "mix the answers from one artist", fontSize = DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
        mixes.forEach { mix ->
            EdgeCropText(
                text = if (mix == current) "$mix —" else mix,
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = if (mix == current) colors.accent else colors.textPrimary,
                modifier = Modifier.fillMaxWidth().pointerInput(mix) { tap { onPick(mix) } }.padding(vertical = 4.dp),
            )
        }
        EdgeCropText(text = "back", fontSize = DoradoTokens.TYPE_LIST.dp, color = colors.textSecondary, modifier = Modifier.pointerInput(Unit) { tap { onBack() } })
    }
}

@Composable
private fun QuizRound(
    state: QuizEngine.QuizState,
    tracks: List<Track>,
    paused: Boolean,
    soundLevel: Int,
    onAnswer: (Int) -> Unit,
    onHint: () -> Unit,
    onPlayClip: () -> Unit,
    onBonusAnswer: (Int) -> Unit,
    onPause: () -> Unit,
    onQuit: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val question = if (state.phase == QuizEngine.Phase.BONUS) state.bonusQuestion else state.question
    val bonus = state.phase == QuizEngine.Phase.BONUS
    val seconds = if (bonus) state.bonusSecondsLeft else state.secondsLeft
    val totalSeconds = if (bonus) QuizEngine.BONUS_SECONDS else QuizEngine.QUESTION_SECONDS

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                EdgeCropText(
                    text = if (bonus) "bonus" else "question ${state.questionNumber} of ${state.totalQuestions}",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    color = colors.accent,
                    modifier = Modifier.weight(1f),
                )
                EdgeCropText(text = "score ${state.score}", fontSize = DoradoTokens.TYPE_NOW_META.dp)
                Spacer(Modifier.width(10.dp))
                EdgeCropText(
                    text = if (paused) "resume" else "pause",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    color = colors.textSecondary,
                    modifier = Modifier.pointerInput(Unit) { tap { onPause() } },
                )
            }
            Box(Modifier.fillMaxWidth().height(4.dp).background(colors.tile)) {
                Box(
                    Modifier.fillMaxWidth((seconds / totalSeconds).coerceIn(0f, 1f)).fillMaxHeight().background(colors.accent),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EdgeCropText(text = "%02d".format(seconds.toInt().coerceAtLeast(0)), fontSize = DoradoTokens.TYPE_NOW_META.dp)
                if (!bonus && state.question?.kind == QuizEngine.Kind.ALBUM_ART) {
                    val track = tracks.firstOrNull { it.mediaId == question?.mediaId }
                    if (track != null) {
                        AlbumArt(model = track.albumArtUri, contentDescription = track.album, modifier = Modifier.size(72.dp))
                    }
                } else if (!bonus && question?.mediaId != null) {
                    EdgeCropText(
                        text = "play clip",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = colors.accent,
                        modifier = Modifier.pointerInput(Unit) { tap { onPlayClip() } },
                    )
                }
                if (!bonus) {
                    EdgeCropText(
                        text = "hint (${state.hintsLeft})",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = if (state.hintsLeft > 0) colors.accent else colors.textInactive,
                        modifier = Modifier.pointerInput(Unit) { tap { onHint() } },
                    )
                }
            }
            EdgeCropText(
                text = question?.prompt ?: "…",
                fontSize = DoradoTokens.TYPE_NOW_TITLE.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                question?.options?.forEachIndexed { index, option ->
                    val revealed = !bonus && state.phase == QuizEngine.Phase.REVEAL
                    val isAnswer = index == question.answerIndex
                    val isChosen = state.chosenIndex == index
                    val color = when {
                        revealed && isAnswer -> colors.accentBright
                        revealed && isChosen && !isAnswer -> colors.textInactive
                        else -> colors.textPrimary
                    }
                    val prefix = when {
                        revealed && isAnswer -> "✓ "
                        revealed && isChosen && !isAnswer -> "✗ "
                        else -> "${index + 1}. "
                    }
                    EdgeCropText(
                        text = prefix + option,
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = color,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(index, bonus, state.phase) {
                                tap { if (bonus) onBonusAnswer(index) else onAnswer(index) }
                            }
                            .padding(vertical = 4.dp),
                    )
                }
            }
            val feedback = when {
                bonus -> "bonus hits ${state.bonusCorrect}"
                state.phase == QuizEngine.Phase.REVEAL && state.chosenIndex == question?.answerIndex -> "+${state.points} points"
                state.phase == QuizEngine.Phase.REVEAL && state.chosenIndex != null -> "wrong"
                state.phase == QuizEngine.Phase.REVEAL -> "out of time"
                else -> " "
            }
            EdgeCropText(text = feedback, fontSize = DoradoTokens.TYPE_NOW_META.dp, color = colors.accent)
        }

        if (paused) {
            Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.92f))) {
                Column(
                    Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(40.dp))
                    EdgeCropText(text = "paused", fontSize = DoradoTokens.TYPE_MENU_ITEM.dp)
                    EdgeCropText(text = "resume", fontSize = DoradoTokens.TYPE_LIST.dp, color = colors.accent, modifier = Modifier.pointerInput(Unit) { tap { onPause() } })
                    EdgeCropText(text = "main menu", fontSize = DoradoTokens.TYPE_LIST.dp, color = colors.accent, modifier = Modifier.pointerInput(Unit) { tap { onQuit() } })
                }
            }
        }
    }
}

@Composable
private fun QuizResults(
    state: QuizEngine.QuizState,
    best: Int,
    onAgain: () -> Unit,
    onMenu: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Column(
        Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EdgeCropText(text = "results", fontSize = DoradoTokens.TYPE_MENU_ITEM.dp)
        EdgeCropText(text = "correct ${state.correct} of ${state.totalQuestions}", fontSize = DoradoTokens.TYPE_NOW_TITLE.dp)
        EdgeCropText(text = "score ${state.score}", fontSize = DoradoTokens.TYPE_NOW_TITLE.dp, color = colors.accent)
        if (state.bonusEnabled) {
            EdgeCropText(text = "bonus ${state.bonusCorrect} x 5 = ${state.bonusPoints}", fontSize = DoradoTokens.TYPE_LIST.dp, color = colors.textSecondary)
        }
        EdgeCropText(
            text = "high score ${maxOf(best, state.score)}",
            fontSize = DoradoTokens.TYPE_LIST.dp,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(8.dp))
        EdgeCropText(text = "play again", fontSize = DoradoTokens.TYPE_LIST.dp, color = colors.accent, modifier = Modifier.pointerInput(Unit) { tap { onAgain() } })
        EdgeCropText(text = "main menu", fontSize = DoradoTokens.TYPE_LIST.dp, color = colors.textSecondary, modifier = Modifier.pointerInput(Unit) { tap { onMenu() } })
    }
}

private suspend fun PointerInputScope.tap(action: () -> Unit) {
    detectTapGestures { action() }
}
