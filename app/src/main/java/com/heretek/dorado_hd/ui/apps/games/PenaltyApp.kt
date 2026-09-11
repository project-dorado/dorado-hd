package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

private enum class PendingRoute { MATCH, PRACTICE, VIEW }

private data class PendingPenalty(
    val trajectory: List<PenaltyVec3>,
    val outcome: KickOutcome,
    val zone: KeeperZone,
    val reaction: Double,
    val stage: PenaltyStage,
    val rng: Int,
    val spin: Double,
    val route: PendingRoute,
)

private fun stageForMatch(match: PenaltyMatch): PenaltyStage =
    if (!match.suddenDeath) PenaltyStage.KICKER
    else PenaltyStage.entries[2 + (match.kicksTaken / 2) % 4]

private fun stripLabel(strip: List<KickOutcome>): String =
    if (strip.isEmpty()) "-" else strip.joinToString(" ") { outcome ->
        when (outcome) {
            KickOutcome.GOAL -> "o"
            KickOutcome.MISS -> "x"
            KickOutcome.SAVED -> "s"
        }
    }

@Composable
fun PenaltyApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }
    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    var screen by remember { mutableStateOf("menu") }
    val latestScreen = rememberUpdatedState(screen)
    var tournament by remember { mutableStateOf<TournamentState?>(null) }
    var saved by remember { mutableStateOf<TournamentState?>(null) }
    var mad by remember { mutableStateOf<MadMinuteState?>(null) }
    var streak by remember { mutableStateOf<GoalStreakState?>(null) }
    var practice by remember { mutableStateOf<PenaltyMatch?>(null) }
    var tutorial by remember { mutableStateOf(false) }
    var tutorialLesson by remember { mutableStateOf(0) }
    var tutorialSeen by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingPenalty?>(null) }
    var animFrame by remember { mutableStateOf(0) }
    var lastOutcome by remember { mutableStateOf<KickOutcome?>(null) }
    var heldZone by remember { mutableStateOf(KeeperZone.NONE) }
    var pressedAt by remember { mutableStateOf(0L) }
    var soundOn by remember { mutableStateOf(true) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var sessionBestMad by remember { mutableStateOf(0) }
    var sessionBestStreak by remember { mutableStateOf(0) }
    var madRecorded by remember { mutableStateOf(false) }
    var tournamentRecorded by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    val scores by graph.games.top("penalty", 12).collectAsState(initial = emptyList())
    val bestMad = maxOf(sessionBestMad, scores.filter { it.meta == "mad minute" }.maxOfOrNull { it.score } ?: 0)
    val bestStreak = maxOf(sessionBestStreak, scores.filter { it.meta == "goal streak" }.maxOfOrNull { it.score } ?: 0)

    fun playCue(name: String) {
        if (soundOn) bank.play(name)
    }

    LaunchedEffect(Unit) {
        saved = graph.appState.get("penalty")?.let { PenaltyEngine.decodeTournament(it) }
        sessionBestMad = graph.appState.get("penalty-best-mad")?.toIntOrNull() ?: 0
        sessionBestStreak = graph.appState.get("penalty-best-streak")?.toIntOrNull() ?: 0
        soundOn = graph.appState.get("penalty-sound") != "0"
        tutorialSeen = graph.appState.get("penalty-tutorial") == "1"
        loaded = true
    }
    // Cold-start gate: never write the option defaults before the stored
    // profile has loaded.
    LaunchedEffect(soundOn, loaded) {
        if (loaded) graph.appState.put("penalty-sound", if (soundOn) "1" else "0")
    }
    LaunchedEffect(tournament) {
        val current = tournament ?: return@LaunchedEffect
        if (current.status == TournamentStatus.PLAYING) {
            graph.appState.put("penalty", PenaltyEngine.encodeTournament(current))
        } else {
            graph.appState.clear("penalty")
        }
    }
    LaunchedEffect(lastOutcome) {
        if (lastOutcome != null) {
            delay(1100)
            lastOutcome = null
        }
    }

    // Ball playback: advance the sampled trajectory one frame at a time. Bail
    // out if the player leaves the screen that owns the shot, or the animation
    // (and its pending state) would keep running off-screen.
    LaunchedEffect(pending) {
        val shot = pending ?: return@LaunchedEffect
        val host = when (shot.route) {
            PendingRoute.MATCH -> "match"
            PendingRoute.PRACTICE -> "tutorial"
            PendingRoute.VIEW -> null
        }
        animFrame = 0
        var last = 0L
        while (animFrame < shot.trajectory.size - 1) {
            if (host != null && latestScreen.value != host) {
                pending = null
                return@LaunchedEffect
            }
            withFrameNanos { now ->
                if (last == 0L || now - last >= 15_000_000L) {
                    last = now
                    animFrame++
                }
            }
        }
        if (host != null && latestScreen.value != host) {
            pending = null
            return@LaunchedEffect
        }
        lastOutcome = shot.outcome
        when (shot.outcome) {
            KickOutcome.GOAL -> playCue("score")
            KickOutcome.SAVED -> playCue("hit")
            KickOutcome.MISS -> playCue("error")
        }
        when (shot.route) {
            PendingRoute.PRACTICE -> {
                val current = practice
                if (current != null) {
                    practice = PenaltyEngine.recordShot(current, shot.outcome)
                    if (tutorial) {
                        when (tutorialLesson) {
                            0 -> tutorialLesson = 1
                            1 -> if (abs(shot.spin) >= 0.05) tutorialLesson = 2
                        }
                    }
                }
            }

            PendingRoute.MATCH -> {
                val current = tournament
                if (current != null && latestScreen.value == "match") {
                    tournament = current.copy(
                        match = PenaltyEngine.recordShot(current.match, shot.outcome),
                        rngState = shot.rng,
                    )
                }
            }

            PendingRoute.VIEW -> Unit
        }
        pending = null
    }

    fun takePlayerShot(swipe: PenaltySwipe, forPractice: Boolean) {
        if (pending != null) return
        val stage: PenaltyStage
        val rng: Int
        if (forPractice) {
            val current = practice ?: return
            if (current.finished) return
            stage = stageForMatch(current)
            rng = System.nanoTime().toInt()
        } else {
            val current = tournament ?: return
            if (current.match.finished || screen != "match") return
            stage = stageForMatch(current.match)
            rng = current.rngState
        }
        val kick = PenaltyEngine.kickFromSwipe(swipe, stage)
        val target = PenaltyEngine.goalIntersection(kick, stage)
        val plan = PenaltyEngine.aiKeeperPlan(target, stage, 0, rng)
        val shot = PenaltyEngine.simulateShot(kick, plan.first.zone, plan.first.reaction, stage)
        pending = PendingPenalty(
            trajectory = shot.trajectory,
            outcome = shot.outcome,
            zone = plan.first.zone,
            reaction = plan.first.reaction,
            stage = stage,
            rng = plan.second,
            spin = kick.spin,
            route = if (forPractice) PendingRoute.PRACTICE else PendingRoute.MATCH,
        )
        playCue("whoosh")
    }

    fun defendKick(zone: KeeperZone, reaction: Double, forPractice: Boolean) {
        if (pending != null) return
        val stage: PenaltyStage
        val rng: Int
        val roundIndex: Int
        if (forPractice) {
            val current = practice ?: return
            if (current.finished) return
            stage = stageForMatch(current)
            rng = System.nanoTime().toInt()
            roundIndex = 0
        } else {
            val current = tournament ?: return
            if (current.match.finished || screen != "match") return
            stage = stageForMatch(current.match)
            rng = current.rngState
            roundIndex = current.match.roundIndex
        }
        val ai = PenaltyEngine.aiKick(rng, stage, roundIndex)
        val shot = PenaltyEngine.simulateShot(ai.first, zone, reaction, stage)
        pending = PendingPenalty(
            trajectory = shot.trajectory,
            outcome = shot.outcome,
            zone = zone,
            reaction = reaction,
            stage = stage,
            rng = ai.second,
            spin = ai.first.spin,
            route = if (forPractice) PendingRoute.PRACTICE else PendingRoute.MATCH,
        )
        playCue("whoosh")
    }

    // Defend window: the shot fires 1.0 s after the phase opens; a tap before
    // then commits the dive with a small reaction penalty.
    val tournamentMatch = tournament?.match
    val practicePhase = practice?.let { PenaltyEngine.matchPhase(it) }
    val defendPhase = (screen == "match" && tournamentMatch != null &&
        PenaltyEngine.matchPhase(tournamentMatch) == MatchPhase.DEFEND && pending == null) ||
        (screen == "tutorial" && practicePhase == MatchPhase.DEFEND && pending == null)
    LaunchedEffect(defendPhase, tournamentMatch?.kicksTaken, practice?.kicksTaken) {
        if (!defendPhase) return@LaunchedEffect
        heldZone = KeeperZone.NONE
        pressedAt = 0L
        delay(1000)
        val reaction = if (pressedAt == 0L) 1.0 else 0.18
        defendKick(heldZone, reaction, forPractice = screen == "tutorial")
    }

    LaunchedEffect(mad?.over) {
        val current = mad ?: return@LaunchedEffect
        if (current.over && !madRecorded) {
            madRecorded = true
            if (current.score > sessionBestMad) sessionBestMad = current.score
            graph.appState.put("penalty-best-mad", current.score.toString())
            graph.games.record("penalty", current.score, "mad minute")
            playCue("lose")
        }
    }
    LaunchedEffect(streak?.best) {
        val current = streak ?: return@LaunchedEffect
        if (current.best > sessionBestStreak) sessionBestStreak = current.best
        if (current.best > 0) graph.appState.put("penalty-best-streak", current.best.toString())
    }
    LaunchedEffect(tournament?.status) {
        val current = tournament ?: return@LaunchedEffect
        if (current.status != TournamentStatus.PLAYING && !tournamentRecorded) {
            tournamentRecorded = true
            val score = if (current.status == TournamentStatus.WON) 1 else 0
            graph.games.record("penalty", score, "world game")
            playCue(if (current.status == TournamentStatus.WON) "win" else "lose")
        }
    }
    LaunchedEffect(tutorialSeen, loaded) {
        if (loaded && tutorialSeen) graph.appState.put("penalty-tutorial", "1")
    }

    fun startTournament(teamId: Int) {
        saved = null
        val t = PenaltyEngine.newTournament(teamId, System.currentTimeMillis().toInt())
        tournament = t
        tournamentRecorded = false
        screen = "bracket"
        playCue("select")
    }

    fun startMad() {
        mad = PenaltyEngine.newMadMinute(System.currentTimeMillis().toInt())
        madRecorded = false
        screen = "mad"
        playCue("select")
    }

    fun startStreak() {
        streak = PenaltyEngine.newGoalStreak(System.currentTimeMillis().toInt())
        screen = "streak"
        playCue("select")
    }

    fun startTutorial() {
        practice = PenaltyEngine.newMatch(1, 2, 0)
        tutorial = true
        tutorialLesson = if (tutorialSeen) 3 else 0
        screen = "tutorial"
        playCue("select")
    }

    fun finishTutorial() {
        tutorial = false
        tutorialSeen = true
        practice = null
        screen = "menu"
        playCue("win")
    }

    // Mad Minute clock.
    LaunchedEffect(screen, mad?.over) {
        if (screen != "mad") return@LaunchedEffect
        var last = 0L
        while (mad?.over == false) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000.0).coerceIn(0.0, 0.05)
                    val current = mad
                    if (current != null) mad = PenaltyEngine.madMinuteStep(current, dt)
                }
                last = now
            }
        }
    }

    when (screen) {
        "menu" -> PenaltyMenu(
            saved = saved?.takeIf { it.status == TournamentStatus.PLAYING },
            bestMad = bestMad,
            bestStreak = bestStreak,
            onResume = {
                val run = saved?.takeIf { it.status == TournamentStatus.PLAYING }
                if (run != null) {
                    tournament = run
                    tournamentRecorded = false
                    screen = "bracket"
                    playCue("select")
                }
            },
            onWorld = { screen = "country" },
            onMad = { startMad() },
            onStreak = { startStreak() },
            onTutorial = { startTutorial() },
            onScores = { screen = "scores" },
            onOptions = { screen = "options" },
            onAbout = { screen = "about" },
        )

        "country" -> PenaltyCountrySelect(
            onPick = { startTournament(it) },
            onBack = { screen = "menu" },
        )

        "bracket" -> {
            val current = tournament ?: return
            PenaltyBracket(
                tournament = current,
                onPlay = { screen = "match"; playCue("select") },
                onQuit = { screen = "menu" },
            )
        }

        "match" -> {
            val current = tournament ?: return
            PenaltyMatchScreen(
                match = current.match,
                playerTeam = PenaltyEngine.team(current.playerTeam),
                opponentTeam = PenaltyEngine.team(current.opponent),
                pending = pending,
                animFrame = animFrame,
                lastOutcome = lastOutcome,
                heldZone = heldZone,
                defending = defendPhase,
                title = "penalty · ${PenaltyEngine.ROUND_HEADINGS[current.round]}",
                onShootInput = { playCue("click") },
                onSwipe = { swipe -> takePlayerShot(swipe, forPractice = false) },
                onZone = { zone ->
                    heldZone = zone
                    pressedAt = System.currentTimeMillis()
                    playCue("click")
                },
                onContinue = {
                    val advanced = PenaltyEngine.advanceTournament(current)
                    tournament = advanced
                    tournamentRecorded = false
                    screen = if (advanced.status == TournamentStatus.PLAYING) "bracket" else "done"
                    playCue(if (advanced.status == TournamentStatus.LOST) "lose" else "select")
                },
                onQuit = { screen = "menu" },
            )
        }

        "done" -> {
            val current = tournament
            PenaltyTrophy(
                won = current?.status == TournamentStatus.WON,
                playerTeam = current?.let { PenaltyEngine.team(it.playerTeam) },
                onMenu = { tournament = null; screen = "menu" },
            )
        }

        "mad" -> {
            val current = mad
            if (current == null) return
            PenaltyMadMinute(
                state = current,
                pending = pending,
                animFrame = animFrame,
                lastOutcome = lastOutcome,
                onSwipe = { swipe ->
                    val updated = mad ?: return@PenaltyMadMinute
                    val next = PenaltyEngine.madMinuteKick(updated, swipe)
                    if (next == updated) return@PenaltyMadMinute
                    mad = next
                    val ball = next.liveBalls.lastOrNull()
                    if (ball != null) {
                        pending = PendingPenalty(
                            trajectory = ball.trajectory,
                            outcome = if (ball.goal) KickOutcome.GOAL else KickOutcome.MISS,
                            zone = KeeperZone.NONE,
                            reaction = 0.0,
                            stage = PenaltyStage.KICKER,
                            rng = next.rngState,
                            spin = 0.0,
                            route = PendingRoute.VIEW,
                        )
                    }
                    playCue("whoosh")
                },
                onAgain = { startMad() },
                onMenu = { mad = null; screen = "menu" },
            )
        }

        "streak" -> {
            val current = streak
            if (current == null) return
            PenaltyGoalStreak(
                state = current,
                pending = pending,
                animFrame = animFrame,
                lastOutcome = lastOutcome,
                onSwipe = { swipe ->
                    val updated = streak ?: return@PenaltyGoalStreak
                    val result = PenaltyEngine.goalStreakTake(updated, swipe)
                    streak = result.first
                    pending = PendingPenalty(
                        trajectory = result.second.trajectory,
                        outcome = result.second.outcome,
                        zone = result.second.keeperZone,
                        reaction = 0.0,
                        stage = updated.stage,
                        rng = result.first.rngState,
                        spin = result.second.spin,
                        route = PendingRoute.VIEW,
                    )
                    playCue("whoosh")
                },
                onMenu = { streak = null; screen = "menu" },
            )
        }

        "tutorial" -> {
            val current = practice
            if (current == null || tutorialLesson >= 3) {
                finishTutorial()
            } else {
                val lesson = tutorialLesson
                PenaltyTutorial(
                    match = current,
                    lesson = lesson,
                    pending = pending,
                    animFrame = animFrame,
                    lastOutcome = lastOutcome,
                    heldZone = heldZone,
                    defending = defendPhase,
                    onSwipe = { swipe -> takePlayerShot(swipe, forPractice = true) },
                    onZone = { zone ->
                        heldZone = zone
                        pressedAt = System.currentTimeMillis()
                        playCue("click")
                        if (lesson == 2) {
                            tutorialLesson = 3
                            finishTutorial()
                        }
                    },
                    onSkip = { finishTutorial() },
                )
            }
        }

        "scores" -> PenaltyScores(
            scores = scores.map { Triple(it.score, it.meta ?: "", it.playedAt) },
            onBack = { screen = "menu" },
        )

        "options" -> PenaltyOptions(
            soundOn = soundOn,
            confirmReset = showResetConfirm,
            onToggleSound = { soundOn = !soundOn; playCue("select") },
            onResetRequest = { showResetConfirm = true },
            onResetConfirm = {
                tournament = null
                saved = null
                sessionBestMad = 0
                sessionBestStreak = 0
                showResetConfirm = false
                playCue("error")
                scope.launch {
                    graph.appState.clear("penalty")
                    graph.appState.put("penalty-best-mad", "0")
                    graph.appState.put("penalty-best-streak", "0")
                }
            },
            onResetCancel = { showResetConfirm = false },
            onBack = { screen = "menu" },
        )

        else -> PenaltyAbout(onBack = { screen = "menu" })
    }
}

/* ------------------------------------------------------------------ */
/* Menus                                                               */
/* ------------------------------------------------------------------ */

@Composable
private fun PenaltyMenu(
    saved: TournamentState?,
    bestMad: Int,
    bestStreak: Int,
    onResume: () -> Unit,
    onWorld: () -> Unit,
    onMad: () -> Unit,
    onStreak: () -> Unit,
    onTutorial: () -> Unit,
    onScores: () -> Unit,
    onOptions: () -> Unit,
    onAbout: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "penalty") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DoradoTokens.EDGE.dp)) {
            if (saved != null) {
                PenaltyButton("continue game · ${PenaltyEngine.ROUND_HEADINGS[saved.round]}", onResume)
                Spacer(Modifier.height(6.dp))
            }
            PenaltyButton("world game", onWorld)
            Spacer(Modifier.height(4.dp))
            PenaltyButton("mad minute", onMad)
            Spacer(Modifier.height(4.dp))
            PenaltyButton("goal streak", onStreak)
            Spacer(Modifier.height(4.dp))
            PenaltyButton("tutorial", onTutorial)
            Spacer(Modifier.height(10.dp))
            PenaltyButton("high scores", onScores)
            Spacer(Modifier.height(4.dp))
            PenaltyButton("options", onOptions)
            Spacer(Modifier.height(4.dp))
            PenaltyButton("about", onAbout)
            Spacer(Modifier.height(10.dp))
            BasicText(
                text = "mad minute best $bestMad · goal streak best $bestStreak",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
        }
    }
}

@Composable
private fun PenaltyCountrySelect(onPick: (Int) -> Unit, onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "penalty · country", onBack = onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "pick your nation for the 32-nation knockout",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(6.dp))
            PenaltyEngine.TEAMS.forEach { team ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.tile)
                        .border(0.5.dp, colors.border)
                        .appTap(label = team.name) { onPick(team.id) }
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(22.dp)
                            .height(14.dp)
                            .background(Color(team.primary))
                            .border(0.5.dp, Color(team.secondary)),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        text = team.name,
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                    )
                }
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}

@Composable
private fun PenaltyBracket(tournament: TournamentState, onPlay: () -> Unit, onQuit: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "penalty · bracket") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "round of ${PenaltyEngine.roundMatches(tournament.round).let { if (it == 1) "final" else it * 2 }}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            Spacer(Modifier.height(6.dp))
            for (round in 0 until PenaltyEngine.ROUNDS) {
                BasicText(
                    text = PenaltyEngine.ROUND_HEADINGS[round],
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.height(2.dp))
                val offset = PenaltyEngine.roundOffset(round)
                val count = PenaltyEngine.roundMatches(round)
                for (slot in 0 until count) {
                    val fixture = tournament.matches.getOrNull(offset + slot) ?: continue
                    val isPlayer = fixture.home == tournament.playerTeam || fixture.away == tournament.playerTeam
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (isPlayer) colors.tilePressed else colors.elevated)
                            .border(0.5.dp, if (isPlayer) colors.accent else colors.border)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            text = if (fixture.home >= 0) PenaltyEngine.team(fixture.home).name else "—",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textPrimary),
                            modifier = Modifier.width(96.dp),
                        )
                        BasicText(
                            text = if (fixture.homeScore >= 0) "${fixture.homeScore}-${fixture.awayScore}" else "vs",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.accent),
                        )
                        Spacer(Modifier.weight(1f))
                        BasicText(
                            text = if (fixture.away >= 0) PenaltyEngine.team(fixture.away).name else "—",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textPrimary),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = "next: ${PenaltyEngine.team(tournament.playerTeam).name} vs ${PenaltyEngine.team(tournament.opponent).name}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
            )
            Spacer(Modifier.height(6.dp))
            PenaltyButton("play match", onPlay)
            Spacer(Modifier.height(4.dp))
            PenaltyButton("main menu", onQuit)
        }
    }
}

/* ------------------------------------------------------------------ */
/* Match / challenge screens                                           */
/* ------------------------------------------------------------------ */

@Composable
private fun PenaltyMatchScreen(
    match: PenaltyMatch,
    playerTeam: PenaltyTeam,
    opponentTeam: PenaltyTeam,
    pending: PendingPenalty?,
    animFrame: Int,
    lastOutcome: KickOutcome?,
    heldZone: KeeperZone,
    defending: Boolean,
    title: String,
    onShootInput: () -> Unit,
    onSwipe: (PenaltySwipe) -> Unit,
    onZone: (KeeperZone) -> Unit,
    onContinue: () -> Unit,
    onQuit: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = title) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            PenaltyHud(match, playerTeam, opponentTeam, colors)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                PenaltyCanvas(
                    stage = if (pending != null) pending.stage else stageForMatch(match),
                    trajectory = pending?.trajectory.orEmpty(),
                    animFrame = animFrame,
                    zone = pending?.zone ?: heldZone,
                    reaction = pending?.reaction ?: 0.0,
                    enabled = !match.finished && pending == null && !defending,
                    onShootInput = onShootInput,
                    onSwipe = onSwipe,
                )
                when {
                    match.finished -> PenaltyResultOverlay(
                        title = if (match.playerWon) "you win" else "game over",
                        detail = "${match.playerGoals} - ${match.opponentGoals}",
                        actions = listOf("continue" to onContinue, "menu" to onQuit),
                    )

                    lastOutcome == KickOutcome.GOAL -> PenaltyFlash("goal", colors.accentBright)
                    lastOutcome == KickOutcome.SAVED -> PenaltyFlash("saved", colors.accent)
                    lastOutcome == KickOutcome.MISS -> PenaltyFlash("miss", colors.textSecondary)

                    defending -> PenaltyZones(heldZone, onZone)
                }
            }
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = when {
                    match.finished -> "match over"
                    defending -> "tap a zone to dive as keeper"
                    else -> "swipe to direct your kick"
                },
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
        }
    }
}

@Composable
private fun PenaltyTutorial(
    match: PenaltyMatch,
    lesson: Int,
    pending: PendingPenalty?,
    animFrame: Int,
    lastOutcome: KickOutcome?,
    heldZone: KeeperZone,
    defending: Boolean,
    onSwipe: (PenaltySwipe) -> Unit,
    onZone: (KeeperZone) -> Unit,
    onSkip: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val prompt = when (lesson) {
        0 -> "swipe to direct your kick"
        1 -> "add spin by swiping in an arc"
        else -> "tap a zone to jump as keeper"
    }
    DetailScaffold(title = "penalty · tutorial") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            PenaltyHud(match, PenaltyEngine.team(match.playerTeam), PenaltyEngine.team(match.opponentTeam), colors)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                PenaltyCanvas(
                    stage = if (pending != null) pending.stage else stageForMatch(match),
                    trajectory = pending?.trajectory.orEmpty(),
                    animFrame = animFrame,
                    zone = pending?.zone ?: heldZone,
                    reaction = pending?.reaction ?: 0.0,
                    enabled = pending == null && !defending,
                    onShootInput = {},
                    onSwipe = onSwipe,
                )
                when {
                    lastOutcome == KickOutcome.GOAL -> PenaltyFlash("goal", colors.accentBright)
                    defending -> PenaltyZones(heldZone, onZone)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = prompt,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accent),
                )
                Spacer(Modifier.weight(1f))
                PenaltyButton("skip", onSkip)
            }
        }
    }
}

@Composable
private fun PenaltyMadMinute(
    state: MadMinuteState,
    pending: PendingPenalty?,
    animFrame: Int,
    lastOutcome: KickOutcome?,
    onSwipe: (PenaltySwipe) -> Unit,
    onAgain: () -> Unit,
    onMenu: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "penalty · mad minute") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "score ${state.score}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = "streak ${state.streak} · best ${state.bestStreak}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.width(10.dp))
                BasicText(
                    text = "%04.1f".format(state.timeLeft),
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.textPrimary),
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                PenaltyCanvas(
                    stage = PenaltyStage.KICKER,
                    trajectory = pending?.trajectory.orEmpty(),
                    animFrame = animFrame,
                    zone = pending?.zone ?: KeeperZone.NONE,
                    reaction = pending?.reaction ?: 0.0,
                    enabled = pending == null && !state.over && state.countdown <= 0.0,
                    onShootInput = {},
                    onSwipe = onSwipe,
                )
                val pickup = state.nextPickup
                if (pickup != null && !state.over) {
                    BasicText(
                        text = "pickup: ${pickup.name.lowercase().replace('_', ' ')}",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
                when {
                    state.countdown > 0.0 -> PenaltyFlash(state.countdown.toInt().coerceAtLeast(1).toString(), colors.accentBright)
                    state.over -> PenaltyResultOverlay(
                        title = "time up",
                        detail = "score ${state.score}",
                        actions = listOf("play again" to onAgain, "main menu" to onMenu),
                    )

                    lastOutcome == KickOutcome.GOAL -> PenaltyFlash("goal", colors.accentBright)
                    lastOutcome == KickOutcome.MISS -> PenaltyFlash("miss", colors.textSecondary)
                }
            }
            Spacer(Modifier.height(4.dp))
            val power = when {
                state.freezeLeft > 0.0 -> "time freeze %04.1f".format(state.freezeLeft)
                state.bonusLeft > 0.0 -> "points bonus x2 %04.1f".format(state.bonusLeft)
                state.speedupLeft > 0.0 -> "reset speedup %04.1f".format(state.speedupLeft)
                state.frenzyLeft > 0.0 -> "ball frenzy %04.1f".format(state.frenzyLeft)
                else -> "swipe to kick · three balls can live at once"
            }
            BasicText(
                text = power,
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
        }
    }
}

@Composable
private fun PenaltyGoalStreak(
    state: GoalStreakState,
    pending: PendingPenalty?,
    animFrame: Int,
    lastOutcome: KickOutcome?,
    onSwipe: (PenaltySwipe) -> Unit,
    onMenu: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "penalty · goal streak") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .appDescription("streak ${state.streak}, best ${state.best}, kicks ${state.attempts}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = "streak ${state.streak}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = "best ${state.best} · kicks ${state.attempts}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                PenaltyCanvas(
                    stage = state.stage,
                    trajectory = pending?.trajectory.orEmpty(),
                    animFrame = animFrame,
                    zone = pending?.zone ?: KeeperZone.NONE,
                    reaction = pending?.reaction ?: 0.0,
                    enabled = pending == null,
                    onShootInput = {},
                    onSwipe = onSwipe,
                )
                when {
                    lastOutcome == KickOutcome.GOAL -> PenaltyFlash("goal", colors.accentBright)
                    lastOutcome == KickOutcome.SAVED -> PenaltyFlash("saved", colors.textSecondary)
                    lastOutcome == KickOutcome.MISS -> PenaltyFlash("miss", colors.textSecondary)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "a miss resets the streak; the best sticks",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
                )
                Spacer(Modifier.weight(1f))
                PenaltyButton("menu", onMenu)
            }
        }
    }
}

@Composable
private fun PenaltyHud(
    match: PenaltyMatch,
    playerTeam: PenaltyTeam,
    opponentTeam: PenaltyTeam,
    colors: DoradoColors,
) {
    Column(
        Modifier.appDescription(
            "${playerTeam.name} ${match.playerGoals}, ${opponentTeam.name} ${match.opponentGoals}" +
                if (match.suddenDeath) ", sudden death" else "",
        ),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = playerTeam.name,
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = Color(playerTeam.primary)),
                modifier = Modifier.width(110.dp),
            )
            BasicText(
                text = "${match.playerGoals} - ${match.opponentGoals}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            Spacer(Modifier.weight(1f))
            BasicText(
                text = opponentTeam.name,
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = Color(opponentTeam.primary)),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = "you  ${stripLabel(match.playerStrip)}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.weight(1f))
            BasicText(
                text = "${stripLabel(match.opponentStrip)}  them",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
            )
        }
        if (match.suddenDeath) {
            BasicText(
                text = "sudden death",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Canvas                                                              */
/* ------------------------------------------------------------------ */

private class PenaltyView(val width: Float, val height: Float, val stage: PenaltyStage) {
    val horizon = height * 0.14f
    val bottom = height * 0.98f
    val focal = min(width, height) * 0.9f

    fun project(p: PenaltyVec3): Offset {
        val depth = (p.z - stage.kickZ).coerceAtLeast(0.0)
        val f = (1.0 / (1.0 + depth * 0.32)).toFloat()
        val groundY = bottom - (bottom - horizon) * (1f - f)
        val x = width / 2f + p.x.toFloat() * f * focal * 0.5f
        val y = groundY - p.y.toFloat() * f * focal * 0.55f
        return Offset(x, y)
    }

    fun scale(p: PenaltyVec3): Float {
        val depth = (p.z - stage.kickZ).coerceAtLeast(0.0)
        return (1.0 / (1.0 + depth * 0.32)).toFloat() * focal
    }
}

@Composable
private fun PenaltyCanvas(
    stage: PenaltyStage,
    trajectory: List<PenaltyVec3>,
    animFrame: Int,
    zone: KeeperZone,
    reaction: Double,
    enabled: Boolean,
    onShootInput: () -> Unit,
    onSwipe: (PenaltySwipe) -> Unit,
) {
    val colors = LocalDoradoColors.current
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    var dragLastX by remember { mutableStateOf(0f) }
    var dragLastY by remember { mutableStateOf(0f) }
    var dragFrames by remember { mutableStateOf(0) }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(enabled, stage) {
                if (enabled) {
                    detectDragGestures(
                        onDragStart = {
                            onShootInput()
                            dragX = 0f
                            dragY = 0f
                            dragLastX = 0f
                            dragLastY = 0f
                            dragFrames = 0
                        },
                        onDrag = { _, drag ->
                            dragX += drag.x
                            dragY += drag.y
                            dragLastX = drag.x
                            dragLastY = drag.y
                            dragFrames++
                        },
                        onDragEnd = {
                            val frames = maxOf(1, dragFrames)
                            val avgX = dragX / frames
                            val avgY = dragY / frames
                            val blendX = (avgX + dragLastX) / 2f
                            val blendY = (avgY + dragLastY) / 2f
                            onSwipe(
                                PenaltySwipe(
                                    vx = avgX.toDouble(),
                                    vy = avgY.toDouble(),
                                    avgVx = blendX.toDouble(),
                                    avgVy = blendY.toDouble(),
                                ),
                            )
                        },
                    )
                }
            }
            .appDescription("penalty pitch, stage ${stage.label}"),
    ) {
        drawPenaltyScene(colors, stage, trajectory, animFrame, zone, reaction)
    }
}

private fun DrawScope.drawPenaltyScene(
    colors: DoradoColors,
    stage: PenaltyStage,
    trajectory: List<PenaltyVec3>,
    animFrame: Int,
    zone: KeeperZone,
    reaction: Double,
) {
    drawRect(colors.background)
    val view = PenaltyView(size.width, size.height, stage)
    val backZ = PenaltyEngine.GOAL_LINE_Z + stage.goalDepth

    // Pitch trapezoid.
    val pitch = Path()
    val nearLeft = view.project(PenaltyVec3(-10.0, 0.0, stage.kickZ - 0.5))
    val nearRight = view.project(PenaltyVec3(10.0, 0.0, stage.kickZ - 0.5))
    val farLeft = view.project(PenaltyVec3(-10.0, 0.0, backZ))
    val farRight = view.project(PenaltyVec3(10.0, 0.0, backZ))
    pitch.moveTo(nearLeft.x, nearLeft.y)
    pitch.lineTo(nearRight.x, nearRight.y)
    pitch.lineTo(farRight.x, farRight.y)
    pitch.lineTo(farLeft.x, farLeft.y)
    pitch.close()
    drawPath(pitch, colors.elevated)

    // Goal frame and net.
    val postColor = colors.textPrimary
    val postL = view.project(PenaltyVec3(PenaltyEngine.GOAL_POST_LEFT_X, 0.0, PenaltyEngine.GOAL_LINE_Z))
    val postR = view.project(PenaltyVec3(PenaltyEngine.GOAL_POST_RIGHT_X, 0.0, PenaltyEngine.GOAL_LINE_Z))
    val topL = view.project(PenaltyVec3(PenaltyEngine.GOAL_POST_LEFT_X, PenaltyEngine.CROSSBAR_HEIGHT, PenaltyEngine.GOAL_LINE_Z))
    val topR = view.project(PenaltyVec3(PenaltyEngine.GOAL_POST_RIGHT_X, PenaltyEngine.CROSSBAR_HEIGHT, PenaltyEngine.GOAL_LINE_Z))
    val backTopL = view.project(PenaltyVec3(PenaltyEngine.GOAL_POST_LEFT_X, PenaltyEngine.CROSSBAR_HEIGHT, backZ))
    val backTopR = view.project(PenaltyVec3(PenaltyEngine.GOAL_POST_RIGHT_X, PenaltyEngine.CROSSBAR_HEIGHT, backZ))
    val backBottomL = view.project(PenaltyVec3(PenaltyEngine.GOAL_POST_LEFT_X, 0.0, backZ))
    val backBottomR = view.project(PenaltyVec3(PenaltyEngine.GOAL_POST_RIGHT_X, 0.0, backZ))
    drawLine(postColor, postL, topL, strokeWidth = 2f)
    drawLine(postColor, postR, topR, strokeWidth = 2f)
    drawLine(postColor, topL, topR, strokeWidth = 2f)
    drawLine(colors.border, topL, backTopL, strokeWidth = 1f)
    drawLine(colors.border, topR, backTopR, strokeWidth = 1f)
    drawLine(colors.border, backTopL, backTopR, strokeWidth = 1f)
    drawLine(colors.border, backBottomL, backBottomR, strokeWidth = 1f)
    for (i in 0..6) {
        val x = PenaltyEngine.GOAL_POST_LEFT_X + (PenaltyEngine.GOAL_HALF_WIDTH * 2.0) * i / 6.0
        val front = view.project(PenaltyVec3(x, 0.0, PenaltyEngine.GOAL_LINE_Z))
        val back = view.project(PenaltyVec3(x, 0.0, backZ))
        drawLine(colors.border, front, back, strokeWidth = 0.6f)
    }

    // Keeper.
    val elapsed = animFrame * PenaltyEngine.DT
    val keeper = PenaltyEngine.keeperDisplay(stage, zone, reaction, elapsed)
    val keeperFeet = view.project(PenaltyVec3(keeper.x, 0.0, keeper.z))
    val keeperHead = view.project(PenaltyVec3(keeper.x, keeper.y + PenaltyEngine.KEEPER_HEIGHT, keeper.z))
    val keeperScale = view.scale(keeper)
    drawCircle(colors.accent, (0.12f * keeperScale).coerceIn(2f, 10f), keeperHead)
    drawLine(colors.accent, keeperFeet, keeperHead, strokeWidth = (0.18f * keeperScale).coerceIn(2f, 10f))
    drawLine(
        colors.accent,
        view.project(PenaltyVec3(keeper.x - 0.25, keeper.y + 0.5, keeper.z)),
        view.project(PenaltyVec3(keeper.x + 0.25, keeper.y + 0.5, keeper.z)),
        strokeWidth = (0.08f * keeperScale).coerceIn(1f, 6f),
    )

    // Ball trail up to the current frame.
    val last = (animFrame).coerceIn(0, (trajectory.size - 1).coerceAtLeast(0))
    for (i in 0..last) {
        val point = trajectory.getOrNull(i) ?: continue
        val alpha = if (last == 0) 1f else 0.25f + 0.75f * i / last
        val screen = view.project(point)
        val radius = (PenaltyEngine.BALL_RADIUS.toFloat() * view.scale(point) * 0.35f).coerceIn(1.5f, 12f)
        val color = if (i == last) colors.textPrimary else colors.textSecondary
        drawCircle(color.copy(alpha = alpha), radius, screen)
    }
    if (trajectory.isEmpty()) {
        val rest = view.project(stage.kickSpot())
        drawCircle(colors.textPrimary, (PenaltyEngine.BALL_RADIUS.toFloat() * view.scale(stage.kickSpot()) * 0.35f).coerceIn(1.5f, 12f), rest)
    }

    // Kick spot marker.
    val spot = view.project(PenaltyVec3(stage.kickX, 0.01, stage.kickZ))
    drawCircle(colors.border, 2.5f, spot)
}

/* ------------------------------------------------------------------ */
/* Overlays / chrome                                                   */
/* ------------------------------------------------------------------ */

@Composable
private fun PenaltyZones(selected: KeeperZone, onZone: (KeeperZone) -> Unit) {
    val colors = LocalDoradoColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PenaltyZoneButton("high left", selected == KeeperZone.HIGH_LEFT, colors) { onZone(KeeperZone.HIGH_LEFT) }
                PenaltyZoneButton("jump", selected == KeeperZone.JUMP, colors) { onZone(KeeperZone.JUMP) }
                PenaltyZoneButton("high right", selected == KeeperZone.HIGH_RIGHT, colors) { onZone(KeeperZone.HIGH_RIGHT) }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PenaltyZoneButton("low left", selected == KeeperZone.LOW_LEFT, colors) { onZone(KeeperZone.LOW_LEFT) }
                PenaltyZoneButton("stand", selected == KeeperZone.HOLD, colors) { onZone(KeeperZone.HOLD) }
                PenaltyZoneButton("low right", selected == KeeperZone.LOW_RIGHT, colors) { onZone(KeeperZone.LOW_RIGHT) }
            }
        }
    }
}

@Composable
private fun PenaltyZoneButton(label: String, active: Boolean, colors: DoradoColors, onClick: () -> Unit) {
    Box(
        Modifier
            .height(26.dp)
            .background(if (active) colors.tilePressed else colors.tile)
            .border(0.5.dp, if (active) colors.accent else colors.border)
            .appTap(label = label) { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textPrimary),
        )
    }
}

@Composable
private fun PenaltyFlash(label: String, color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(
            text = label,
            style = TextStyle(fontFamily = Selawik, fontSize = 40.sp, color = color),
        )
    }
}

@Composable
private fun PenaltyResultOverlay(title: String, detail: String, actions: List<Pair<String, () -> Unit>>) {
    val colors = LocalDoradoColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .background(colors.elevated)
                .border(0.5.dp, colors.border)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = title,
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = detail,
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                actions.forEach { (label, action) -> PenaltyButton(label, action) }
            }
        }
    }
}

@Composable
private fun PenaltyButton(label: String, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .height(30.dp)
            .background(colors.tile)
            .border(0.5.dp, colors.border)
            .appTap(label = label) { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
        )
    }
}

@Composable
private fun PenaltyTrophy(won: Boolean, playerTeam: PenaltyTeam?, onMenu: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "penalty · trophy") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.Center) {
            BasicText(
                text = if (won) "trophy lifted" else "knocked out",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_TITLE.sp, color = colors.accent),
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = if (won) {
                    "${playerTeam?.name ?: "your nation"} wins the tournament"
                } else {
                    "a loss resets the bracket"
                },
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
            )
            Spacer(Modifier.height(10.dp))
            PenaltyButton("main menu", onMenu)
        }
    }
}

@Composable
private fun PenaltyScores(scores: List<Triple<Int, String, Long>>, onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "penalty · scores", onBack = onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "world game records 1 for a trophy, 0 for a knockout",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(6.dp))
            if (scores.isEmpty()) {
                BasicText(
                    text = "no results yet",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textInactive),
                )
            }
            scores.forEachIndexed { index, (score, meta, _) ->
            Row(
                Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                    BasicText(
                        text = "${index + 1}",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
                        modifier = Modifier.width(22.dp),
                    )
                    BasicText(
                        text = score.toString(),
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                    )
                    Spacer(Modifier.weight(1f))
                    BasicText(
                        text = meta,
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textInactive),
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun PenaltyOptions(
    soundOn: Boolean,
    confirmReset: Boolean,
    onToggleSound: () -> Unit,
    onResetRequest: () -> Unit,
    onResetConfirm: () -> Unit,
    onResetCancel: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "penalty · options", onBack = onBack) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                PenaltyButton(if (soundOn) "sound: on" else "sound: off", onToggleSound)
                Spacer(Modifier.height(4.dp))
                PenaltyButton("reset scores", onResetRequest)
                Spacer(Modifier.height(8.dp))
                BasicText(
                    text = "reset clears the saved bracket and local bests",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
            }
            if (confirmReset) {
                PenaltyResultOverlay(
                    title = "reset scores",
                    detail = "reset your high scores?",
                    actions = listOf("reset" to onResetConfirm, "cancel" to onResetCancel),
                )
            }
        }
    }
}

@Composable
private fun PenaltyAbout(onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "penalty · about", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "Penalty! Flick Soccer",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "clean-room re-creation for Dorado · swipe physics, keeper dives, 32-nation knockout, mad minute and goal streak",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "synthesized audio and vector art only",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
        }
    }
}
