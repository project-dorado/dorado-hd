@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.apps.MiniSynth
import com.heretek.dorado_hd.ui.apps.SfxBank
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private fun kickoffLabel(ms: Long): String =
    SimpleDateFormat("EEE d MMM h:mm a", Locale.getDefault()).format(Date(ms))

private fun outcomeLabel(fixture: FanFixture): String =
    if (fixture.status == FixtureStatus.FINAL) "${fixture.homeScore}-${fixture.awayScore}" else "vs"

@Composable
fun FanPredictionApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }
    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    var state by remember { mutableStateOf<FanState?>(null) }
    var screen by remember { mutableStateOf("sports") }
    var sport by remember { mutableStateOf(FanSport.SOCCER) }
    var selectedFixtureId by remember { mutableStateOf<Int?>(null) }
    var draftPick by remember { mutableStateOf(FanPick.HOME) }
    var homeInput by remember { mutableStateOf("") }
    var awayInput by remember { mutableStateOf("") }
    var activeField by remember { mutableStateOf(0) }
    var favoriteSport by remember { mutableStateOf(FanSport.SOCCER) }
    var nicknameDraft by remember { mutableStateOf("") }

    fun playCue(name: String) = bank.play(name)

    LaunchedEffect(Unit) {
        val savedBlob = graph.appState.get("fan-prediction")
        val deviceId = graph.appState.get("fan-prediction-device")
            ?: UUID.randomUUID().toString().also { graph.appState.put("fan-prediction-device", it) }
        val loaded = savedBlob?.let { FanPredictionEngine.decode(it) }
            ?: FanPredictionEngine.newState(deviceId, System.currentTimeMillis(), System.currentTimeMillis().toInt())
        state = FanPredictionEngine.refresh(loaded, System.currentTimeMillis())
    }
    LaunchedEffect(state) {
        val current = state ?: return@LaunchedEffect
        graph.appState.put("fan-prediction", FanPredictionEngine.encode(current))
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val current = state ?: continue
            state = FanPredictionEngine.apply(current, FanEvent.Refresh(System.currentTimeMillis()))
        }
    }

    fun dispatch(event: FanEvent) {
        val current = state ?: return
        state = FanPredictionEngine.apply(current, event)
        when (event) {
            is FanEvent.MakePrediction -> playCue("coin")
            is FanEvent.SetFavoriteTeam -> playCue("select")
            is FanEvent.SetNickname -> playCue("select")
            is FanEvent.RemovePrediction -> playCue("back")
            FanEvent.ResetScores -> playCue("error")
            is FanEvent.Refresh -> playCue("tick")
            is FanEvent.SetOffline -> playCue("back")
        }
    }

    fun openFixture(fixture: FanFixture) {
        selectedFixtureId = fixture.id
        val existing = state?.predictions?.get(fixture.id)
        draftPick = existing?.pick ?: if (fixture.sport.hasDraw) FanPick.DRAW else FanPick.HOME
        homeInput = existing?.homeScore?.toString() ?: ""
        awayInput = existing?.awayScore?.toString() ?: ""
        activeField = 0
        screen = "predict"
        playCue("select")
    }

    when (screen) {
        "sports" -> {
            val current = state ?: return
            FanSportsScreen(
                state = current,
                onSport = { picked -> sport = picked; screen = "games"; playCue("select") },
                onStandings = { screen = "standings"; playCue("select") },
                onSettings = { nicknameDraft = current.profile.nickname; screen = "settings"; playCue("select") },
                onAbout = { screen = "about"; playCue("select") },
                onRefresh = { dispatch(FanEvent.Refresh(System.currentTimeMillis())) },
            )
        }

        "games" -> {
            val current = state ?: return
            FanGamesScreen(
                state = current,
                sport = sport,
                onFixture = { fixture ->
                    if (FanPredictionEngine.canPredict(current, fixture)) openFixture(fixture) else playCue("error")
                },
                onQuickPick = { fixture, pick ->
                    if (FanPredictionEngine.canPredict(current, fixture)) {
                        dispatch(FanEvent.MakePrediction(fixture.id, pick))
                    } else {
                        playCue("error")
                    }
                },
                onStandings = { screen = "standings"; playCue("select") },
                onFavorite = { favoriteSport = sport; screen = "favorites"; playCue("select") },
                onSettings = { nicknameDraft = current.profile.nickname; screen = "settings"; playCue("select") },
                onBack = { screen = "sports" },
            )
        }

        "predict" -> {
            val current = state ?: return
            val fixture = current.fixtures.firstOrNull { it.id == selectedFixtureId } ?: return
            FanPredictionScreen(
                fixture = fixture,
                draftPick = draftPick,
                homeInput = homeInput,
                awayInput = awayInput,
                activeField = activeField,
                existing = current.predictions[fixture.id],
                onPick = { draftPick = it; playCue("click") },
                onKey = { key ->
                    val value = if (activeField == 0) homeInput else awayInput
                    val next = FanPredictionEngine.appendScoreDigit(value, key)
                    if (activeField == 0) homeInput = next else awayInput = next
                    playCue("click")
                },
                onDel = {
                    if (activeField == 0) homeInput = homeInput.dropLast(1) else awayInput = awayInput.dropLast(1)
                    playCue("click")
                },
                onField = { activeField = it; playCue("click") },
                onRemove = {
                    dispatch(FanEvent.RemovePrediction(fixture.id))
                    screen = "games"
                },
                onSave = {
                    val home = homeInput.toIntOrNull()
                    val away = awayInput.toIntOrNull()
                    dispatch(FanEvent.MakePrediction(fixture.id, draftPick, home, away))
                    screen = "games"
                },
                onBack = { screen = "games" },
            )
        }

        "standings" -> {
            val current = state ?: return
            FanStandingsScreen(
                rows = FanPredictionEngine.sortedStandings(current),
                onBack = { screen = "sports" },
            )
        }

        "favorites" -> {
            val current = state ?: return
            FanFavoritesScreen(
                state = current,
                sport = favoriteSport,
                onSport = { favoriteSport = it; playCue("select") },
                onTeam = { teamId -> dispatch(FanEvent.SetFavoriteTeam(favoriteSport, teamId)) },
                onUnset = { dispatch(FanEvent.SetFavoriteTeam(favoriteSport, null)) },
                onBack = { screen = "games" },
            )
        }

        "settings" -> {
            val current = state ?: return
            FanSettingsScreen(
                state = current,
                draft = nicknameDraft,
                onKey = { ch ->
                    nicknameDraft = (nicknameDraft + ch).take(FanPredictionEngine.MAX_NICKNAME)
                    playCue("click")
                },
                onSpace = {
                    nicknameDraft = (nicknameDraft + " ").take(FanPredictionEngine.MAX_NICKNAME)
                    playCue("click")
                },
                onDel = {
                    nicknameDraft = nicknameDraft.dropLast(1)
                    playCue("click")
                },
                onSave = {
                    dispatch(FanEvent.SetNickname(nicknameDraft))
                    playCue("select")
                },
                onStandings = { screen = "standings"; playCue("select") },
                onBack = { screen = "sports" },
            )
        }

        else -> FanAboutScreen(
            state = state,
            onBack = { screen = "sports" },
        )
    }
}

/* ------------------------------------------------------------------ */
/* Screens                                                             */
/* ------------------------------------------------------------------ */

@Composable
private fun FanSportsScreen(
    state: FanState,
    onSport: (FanSport) -> Unit,
    onStandings: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "fan prediction") {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(DoradoTokens.EDGE.dp),
        ) {
            BasicText(
                text = FanPredictionEngine.OFFLINE_BANNER,
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "sports",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(4.dp))
            FanSport.entries.forEach { entry ->
                val matches = FanPredictionEngine.matchesFor(state, entry)
                val upcoming = matches.count { it.status == FixtureStatus.SCHEDULED }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.tile)
                        .border(0.5.dp, colors.border)
                        .pointerInput(entry) { detectTapGestures(onTap = { onSport(entry) }) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(width = 26.dp, height = 20.dp)
                            .background(colors.elevated)
                            .border(0.5.dp, if (entry == FanSport.SOCCER) colors.accent else colors.border),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = entry.label.take(1).uppercase(),
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            text = entry.label,
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                        )
                        BasicText(
                            text = "$upcoming upcoming · ${matches.size} fixtures",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
                        )
                        BasicText(
                            text = if (state.profile.favorites[entry] != null) {
                                FanTeams.byId(state.profile.favorites[entry]!!)?.name ?: "favourite set"
                            } else {
                                "no favourite"
                            },
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textInactive),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "account: ${FanPredictionEngine.nicknameOrHandle(state)}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            BasicText(
                text = FanPredictionEngine.favoritesLine(state),
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            if (state.banner != null) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = state.banner,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accent),
                )
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FanButton("refresh", onRefresh)
                FanButton("standings", onStandings)
                FanButton("settings", onSettings)
                FanButton("about", onAbout)
            }
        }
    }
}

@Composable
private fun FanGamesScreen(
    state: FanState,
    sport: FanSport,
    onFixture: (FanFixture) -> Unit,
    onQuickPick: (FanFixture, FanPick) -> Unit,
    onStandings: () -> Unit,
    onFavorite: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val fixtures = FanPredictionEngine.matchesFor(state, sport)
    DetailScaffold(title = "fan prediction · ${sport.label}", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(DoradoTokens.EDGE.dp),
        ) {
            BasicText(
                text = "offline simulation · predictions lock at kickoff",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
            )
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FanButton("standings", onStandings)
                FanButton("favourite team", onFavorite)
                FanButton("settings", onSettings)
            }
            Spacer(Modifier.height(8.dp))
            fixtures.forEach { fixture ->
                val prediction = state.predictions[fixture.id]
                val locked = !FanPredictionEngine.canPredict(state, fixture)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(if (prediction != null) colors.tilePressed else colors.tile)
                        .border(0.5.dp, if (prediction != null) colors.accent else colors.border)
                        .pointerInput(fixture.id, locked) { detectTapGestures(onTap = { onFixture(fixture) }) }
                        .padding(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            text = FanTeams.byId(fixture.homeId)?.name ?: "?",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                            modifier = Modifier.weight(1f),
                        )
                        BasicText(
                            text = outcomeLabel(fixture),
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                        )
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            BasicText(
                                text = FanTeams.byId(fixture.awayId)?.name ?: "?",
                                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            text = when {
                                fixture.status == FixtureStatus.FINAL && prediction != null ->
                                    "final · predicted ${prediction.homeScore ?: pickLabel(prediction.pick)}-${prediction.awayScore ?: pickLabel(prediction.pick)} · ${FanPredictionEngine.scorePrediction(prediction, fixture)} pts"

                                fixture.status == FixtureStatus.FINAL -> "final · no prediction"
                                prediction != null -> "${kickoffLabel(fixture.kickoffMs)} · predicted ${predictionLabel(prediction)}"
                                else -> "${kickoffLabel(fixture.kickoffMs)} · (tap to predict)"
                            },
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(6.dp))
                        if (!locked) {
                            FanPickButton("h", prediction == null || prediction.pick == FanPick.HOME) { onQuickPick(fixture, FanPick.HOME) }
                            if (sport.hasDraw) {
                                FanPickButton("d", prediction?.pick == FanPick.DRAW) { onQuickPick(fixture, FanPick.DRAW) }
                            }
                            FanPickButton("a", prediction?.pick == FanPick.AWAY) { onQuickPick(fixture, FanPick.AWAY) }
                        } else {
                            BasicText(
                                text = "locked",
                                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

private fun predictionLabel(prediction: FanPrediction): String =
    if (prediction.homeScore != null && prediction.awayScore != null) {
        "${prediction.homeScore}-${prediction.awayScore}"
    } else {
        prediction.pick.name.lowercase()
    }

private fun pickLabel(pick: FanPick): String = when (pick) {
    FanPick.HOME -> "h"
    FanPick.DRAW -> "d"
    FanPick.AWAY -> "a"
}

@Composable
private fun FanPredictionScreen(
    fixture: FanFixture,
    draftPick: FanPick,
    homeInput: String,
    awayInput: String,
    activeField: Int,
    existing: FanPrediction?,
    onPick: (FanPick) -> Unit,
    onKey: (String) -> Unit,
    onDel: () -> Unit,
    onField: (Int) -> Unit,
    onRemove: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "prediction", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(DoradoTokens.EDGE.dp),
        ) {
            BasicText(
                text = "${FanTeams.byId(fixture.homeId)?.name ?: "?"} vs ${FanTeams.byId(fixture.awayId)?.name ?: "?"}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
            )
            BasicText(
                text = "${kickoffLabel(fixture.kickoffMs)} · ${fixture.sport.label} · offline simulation",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FanPickButton("home win", draftPick == FanPick.HOME) { onPick(FanPick.HOME) }
                if (fixture.sport.hasDraw) {
                    FanPickButton("draw", draftPick == FanPick.DRAW) { onPick(FanPick.DRAW) }
                }
                FanPickButton("away win", draftPick == FanPick.AWAY) { onPick(FanPick.AWAY) }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FanScoreField("home", homeInput, activeField == 0) { onField(0) }
                Spacer(Modifier.width(10.dp))
                FanScoreField("away", awayInput, activeField == 1) { onField(1) }
            }
            Spacer(Modifier.height(8.dp))
            FanKeypad(onKey = onKey, onDel = onDel)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FanButton("save prediction", onSave)
                Spacer(Modifier.width(6.dp))
                if (existing != null) FanButton("remove", onRemove)
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = "optional exact score · 3 pts for the outcome, +2 exact",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
        }
    }
}

@Composable
private fun FanScoreField(label: String, value: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Column {
        BasicText(
            text = label,
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
        )
        Box(
            Modifier
                .size(width = 48.dp, height = 30.dp)
                .background(if (active) colors.tilePressed else colors.tile)
                .border(0.5.dp, if (active) colors.accent else colors.border)
                .pointerInput(label, active) { detectTapGestures(onTap = { onClick() }) },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = value.ifEmpty { "-" },
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.textPrimary),
            )
        }
    }
}

@Composable
private fun FanKeypad(onKey: (String) -> Unit, onDel: () -> Unit) {
    val colors = LocalDoradoColors.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (row in 0 until 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (col in 0 until 3) {
                    val digit = row * 3 + col + 1
                    FanKey(digit.toString(), colors) { onKey(digit.toString()) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            FanKey("0", colors) { onKey("0") }
            FanKey("del", colors) { onDel() }
        }
    }
}

@Composable
private fun FanKey(label: String, colors: com.heretek.dorado_hd.design.DoradoColors, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 34.dp, height = 28.dp)
            .background(colors.tile)
            .border(0.5.dp, colors.border)
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textPrimary),
        )
    }
}

@Composable
private fun FanStandingsScreen(rows: List<FanStandingRow>, onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "fan prediction · standings", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(DoradoTokens.EDGE.dp),
        ) {
            BasicText(
                text = "local standings · offline simulation",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().border(0.5.dp, colors.border).padding(4.dp)) {
                BasicText(
                    text = "name",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                    modifier = Modifier.weight(1f),
                )
                BasicText(
                    text = "pts",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                    modifier = Modifier.width(36.dp),
                )
                BasicText(
                    text = "%",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                    modifier = Modifier.width(36.dp),
                )
            }
            rows.forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (row.isYou) colors.tilePressed else colors.background)
                        .border(0.5.dp, if (row.isYou) colors.accent else colors.border)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = if (row.isYou) "${row.name} (you)" else row.name,
                        style = TextStyle(
                            fontFamily = Selawik,
                            fontSize = DoradoTokens.TYPE_LIST.sp,
                            color = if (row.isYou) colors.accent else colors.textPrimary,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    BasicText(
                        text = row.points.toString(),
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                        modifier = Modifier.width(36.dp),
                    )
                    BasicText(
                        text = FanPredictionEngine.percentLabel(row),
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
                        modifier = Modifier.width(36.dp),
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun FanFavoritesScreen(
    state: FanState,
    sport: FanSport,
    onSport: (FanSport) -> Unit,
    onTeam: (Int) -> Unit,
    onUnset: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val current = state.profile.favorites[sport]
    DetailScaffold(title = "favourite team", onBack = onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "stored locally · shown in the standings line",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FanSport.entries.forEach { entry ->
                    FanPickButton(entry.label, entry == sport) { onSport(entry) }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (current != null) {
                FanButton("unset ${FanTeams.byId(current)?.name ?: ""}", onUnset)
                Spacer(Modifier.height(8.dp))
            }
            FanTeams.of(sport).forEach { team ->
                val selected = team.id == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (selected) colors.tilePressed else colors.tile)
                        .border(0.5.dp, if (selected) colors.accent else colors.border)
                        .pointerInput(team.id) { detectTapGestures(onTap = { onTeam(team.id) }) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = team.name,
                        style = TextStyle(
                            fontFamily = Selawik,
                            fontSize = DoradoTokens.TYPE_LIST.sp,
                            color = if (selected) colors.accent else colors.textPrimary,
                        ),
                    )
                    Spacer(Modifier.weight(1f))
                    if (selected) {
                        BasicText(
                            text = "favourite",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accent),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}

@Composable
private fun FanSettingsScreen(
    state: FanState,
    draft: String,
    onKey: (String) -> Unit,
    onSpace: () -> Unit,
    onDel: () -> Unit,
    onSave: () -> Unit,
    onStandings: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "profile", onBack = onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "nickname · shown in the local standings",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(colors.tile)
                    .border(0.5.dp, colors.border)
                    .padding(8.dp),
            ) {
                BasicText(
                    text = draft.ifEmpty { "tap letters below" },
                    style = TextStyle(
                        fontFamily = Selawik,
                        fontSize = DoradoTokens.TYPE_LIST.sp,
                        color = if (draft.isEmpty()) colors.textInactive else colors.textPrimary,
                    ),
                )
            }
            Spacer(Modifier.height(6.dp))
            for (rowStart in 0 until 26 step 9) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    for (i in rowStart until minOf(rowStart + 9, 26)) {
                        val ch = ('a' + i)
                        FanKey(ch.toString(), colors) { onKey(ch.toString()) }
                    }
                }
                Spacer(Modifier.height(3.dp))
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                FanKey("space", colors) { onSpace() }
                FanKey("del", colors) { onDel() }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FanButton("save", onSave)
                FanButton("standings", onStandings)
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = "device id ${state.profile.deviceId.take(13)}… · silent local registration",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
        }
    }
}

@Composable
private fun FanAboutScreen(state: FanState?, onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "fan prediction · about", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "Fan Prediction",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "The original 2010 app pulled fixtures from a live scoring service. That service is gone, so this build is an offline simulation: fixtures, scores and rival standings are generated on-device from a fixed seed.",
                style = TextStyle(
                    fontFamily = Selawik,
                    fontSize = DoradoTokens.TYPE_LIST.sp,
                    color = colors.textPrimary,
                    lineHeight = (DoradoTokens.TYPE_LIST * 1.35f).sp,
                ),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "no network requests · account ${state?.profile?.deviceId?.take(8) ?: "—"} · predictions stay on this device",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Chrome                                                              */
/* ------------------------------------------------------------------ */

@Composable
private fun FanButton(label: String, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .height(26.dp)
            .background(colors.tile)
            .border(0.5.dp, colors.border)
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) }
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
private fun FanPickButton(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .height(24.dp)
            .background(if (active) colors.tilePressed else colors.tile)
            .border(0.5.dp, if (active) colors.accent else colors.border)
            .pointerInput(label, active) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                fontFamily = Selawik,
                fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp,
                color = if (active) colors.accent else colors.textPrimary,
            ),
        )
    }
}
