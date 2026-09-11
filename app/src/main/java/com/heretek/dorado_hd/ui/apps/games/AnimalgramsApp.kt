package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlin.math.min
import kotlinx.coroutines.launch

private const val ANIMALGRAMS_SLUG = "animalgrams"

@Composable
fun AnimalgramsApp() {
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
    var progress by remember { mutableStateOf(AnimalgramsEngine.AnagramProgress()) }
    var habitat by remember { mutableStateOf(AnimalgramsEngine.HABITATS.first()) }
    var game by remember { mutableStateOf<AnimalgramsEngine.AnagramState?>(null) }
    var resetting by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        progress = AnimalgramsEngine.decodeProgress(graph.appState.get(ANIMALGRAMS_SLUG))
        loaded = true
    }

    LaunchedEffect(progress, loaded) {
        if (!loaded) return@LaunchedEffect
        graph.appState.put(ANIMALGRAMS_SLUG, AnimalgramsEngine.encodeProgress(progress))
    }

    LaunchedEffect(game?.hintLockMs) {
        var lock = game?.hintLockMs ?: 0L
        while (lock > 0L) {
            kotlinx.coroutines.delay(100)
            val next = game?.let { AnimalgramsEngine.step(it, 100) } ?: break
            game = next
            lock = next.hintLockMs
        }
    }

    fun openRound(key: String) {
        game = AnimalgramsEngine.newRound(key, seed = (System.currentTimeMillis() and 0x7FFFFFFF).toInt())
        recorded = false
        screen = "game"
    }

    fun playEvents(events: List<AnimalgramsEngine.Event>) {
        events.forEach { event ->
            bank.play(
                when (event) {
                    AnimalgramsEngine.Event.TYPE -> "click"
                    AnimalgramsEngine.Event.ERASE, AnimalgramsEngine.Event.CLEAR -> "back"
                    AnimalgramsEngine.Event.INVALID -> "error"
                    AnimalgramsEngine.Event.ACCEPTED -> "score"
                    AnimalgramsEngine.Event.LONG_WORD -> "coin"
                    AnimalgramsEngine.Event.HINT -> "select"
                    AnimalgramsEngine.Event.HINT_LOCKED -> "error"
                    AnimalgramsEngine.Event.WIN -> "win"
                },
            )
        }
    }

    fun settleWord(next: AnimalgramsEngine.AnagramState, before: AnimalgramsEngine.AnagramState) {
        val newWords = next.found - before.found
        if (newWords.isNotEmpty()) {
            newWords.forEach { word -> progress = AnimalgramsEngine.record(progress, next.animal, word) }
        }
        if (next.won && !recorded) {
            recorded = true
            val total = AnimalgramsEngine.ALL_ANIMALS.sumOf { AnimalgramsEngine.foundWords(progress, it).size }
            scope.launch { graph.games.record(ANIMALGRAMS_SLUG, total, next.animal) }
        }
    }

    if (screen == "menu") {
        AnimalgramsMenu(
            progress = progress,
            onPlay = { screen = "habitats" },
            onLearn = { screen = "learn" },
            onOptions = { screen = "options" },
        )
        return
    }
    if (screen == "learn") {
        AnimalgramsLearn(onBack = { screen = "menu" })
        return
    }
    if (screen == "options") {
        AnimalgramsOptions(
            progress = progress,
            onSound = { progress = progress.copy(sound = it) },
            onReset = { resetting = true },
            onBack = { screen = "menu" },
        )
        if (resetting) {
            AnimalgramsOverlay(
                title = "reset all progress?",
                subtitle = "every found word is erased",
                actions = listOf(
                    "reset" to { progress = AnimalgramsEngine.reset(progress); resetting = false },
                    "cancel" to { resetting = false },
                ),
            )
        }
        return
    }
    if (screen == "habitats") {
        AnimalgramsHabitats(progress = progress, onPick = { habitat = it; screen = "animals" }, onBack = { screen = "menu" })
        return
    }
    if (screen == "animals") {
        AnimalgramsAnimals(
            progress = progress,
            habitat = habitat,
            onPick = { openRound(it) },
            onBack = { screen = "habitats" },
        )
        return
    }

    val current = game ?: return
    val habitatKey = AnimalgramsEngine.habitatOf(current.animal)?.key ?: "jungle"
    DetailScaffold(title = "animalgrams", onBack = { game = null; screen = "animals" }) {
        Box(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val backdropScale = min(maxWidth.value, maxHeight.value) / 300f
                Canvas(Modifier.fillMaxSize()) {
                    drawAnimalgramsBackdrop(habitatKey, colors, backdropScale)
                }
            }
            Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            val percent = AnimalgramsEngine.animalPercent(progress, current.animal)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = current.animal.lowercase(),
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
                Spacer(Modifier.width(10.dp))
                BasicText(
                    text = "words ${current.found.size}/${current.available}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = "save $percent%",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(colors.elevated)
                    .border(0.5.dp, if (AnimalgramsEngine.isValidCandidate(current)) colors.accent else colors.border),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicText(
                    text = current.typedWord.ifEmpty { " " },
                    style = TextStyle(
                        fontFamily = Selawik,
                        fontSize = DoradoTokens.TYPE_NOW_META.sp,
                        color = if (AnimalgramsEngine.isValidCandidate(current)) colors.accentBright else colors.textPrimary,
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            AnimalgramsTiles(current.tiles) { index ->
                val next = AnimalgramsEngine.type(current, index)
                if (next !== current) {
                    game = next
                    playEvents(next.events)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AnimalgramsButton("hint", Modifier.weight(1f), enabled = current.hintLockMs == 0L) {
                    val next = AnimalgramsEngine.hint(current)
                    game = next
                    playEvents(next.events)
                }
                AnimalgramsButton("erase", Modifier.weight(1f), enabled = current.typed.isNotEmpty()) {
                    val next = AnimalgramsEngine.erase(current)
                    game = next
                    playEvents(next.events)
                }
                AnimalgramsButton("re-enter", Modifier.weight(1f), enabled = current.lastSubmitted.isNotEmpty()) {
                    val next = AnimalgramsEngine.reenter(current)
                    game = next
                    playEvents(next.events)
                }
                AnimalgramsButton("submit", Modifier.weight(1f), enabled = current.typed.size >= 3) {
                    val before = current
                    val next = AnimalgramsEngine.submit(current)
                    game = next
                    playEvents(next.events)
                    settleWord(next, before)
                }
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = current.message.ifEmpty { "tap tiles to spell a word" },
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(4.dp))
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                AnimalgramsEngine.wordsFor(current.animal).forEach { word ->
                    val found = word in current.found
                    BasicText(
                        text = if (found) word else "\u2022".repeat(word.length),
                        style = TextStyle(
                            fontFamily = Selawik,
                            fontSize = DoradoTokens.TYPE_LIST.sp,
                            color = if (found) colors.accent else colors.textInactive,
                            fontWeight = if (found) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
            if (current.won) {
                Spacer(Modifier.height(6.dp))
                AnimalgramsOverlay(
                    title = "${current.animal.lowercase()} complete",
                    subtitle = "every word found · medal earned",
                    actions = listOf(
                        "next animal" to { game = null; screen = "animals" },
                        "main menu" to { game = null; screen = "menu" },
                    ),
                )
            }
            }
        }
    }
}

private fun DrawScope.drawAnimalgramsBackdrop(habitatKey: String, colors: DoradoColors, scale: Float) {
    val tint = when (habitatKey) {
        "jungle" -> DoradoAccent.LIME.primary
        "savanna" -> DoradoAccent.ORANGE.primary
        "forest" -> DoradoAccent.LIME.bright
        "aqua" -> DoradoAccent.CYAN.primary
        else -> DoradoAccent.PURPLE.primary
    }
    drawRect(colors.background)
    drawRect(tint.copy(alpha = 0.08f), Offset(0f, 0f), Size(size.width, size.height * 0.42f))
    drawRect(tint.copy(alpha = 0.12f), Offset(0f, size.height * 0.72f), Size(size.width, size.height * 0.28f))
    for (i in 0 until 9) {
        val x = size.width * ((i * 37 % 100) / 100f)
        val y = size.height * ((i * 53 % 90) / 100f + 0.05f)
        val radius = (2.2f + (i % 3)) * scale
        drawCircle(tint.copy(alpha = 0.20f), radius, Offset(x, y))
    }
}

@Composable
private fun AnimalgramsTiles(tiles: List<AnimalgramsEngine.Tile>, onTap: (Int) -> Unit) {
    val colors = LocalDoradoColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tiles.indices.chunked(4).forEach { rowIndices ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowIndices.forEach { index ->
                    val tile = tiles[index]
                    Box(
                        Modifier
                            .size(42.dp)
                            .background(if (tile.used) colors.tilePressed else colors.tile)
                            .border(0.5.dp, if (tile.used) colors.accent else colors.border)
                            .pointerInput(index, tile.used) {
                                if (!tile.used) detectTapGestures(onTap = { onTap(index) })
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = tile.ch.uppercaseChar().toString(),
                            style = TextStyle(
                                fontFamily = Selawik,
                                fontSize = DoradoTokens.TYPE_NOW_META.sp,
                                color = if (tile.used) colors.textInactive else colors.textPrimary,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimalgramsMenu(progress: AnimalgramsEngine.AnagramProgress, onPlay: () -> Unit, onLearn: () -> Unit, onOptions: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "animalgrams") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            AnimalgramsButton("play", Modifier.fillMaxWidth()) { onPlay() }
            Spacer(Modifier.height(4.dp))
            AnimalgramsButton("learn to play", Modifier.fillMaxWidth()) { onLearn() }
            Spacer(Modifier.height(4.dp))
            AnimalgramsButton("options", Modifier.fillMaxWidth()) { onOptions() }
            Spacer(Modifier.height(10.dp))
            BasicText(
                text = "global ${AnimalgramsEngine.globalPercent(progress)}%",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            val total = AnimalgramsEngine.ALL_ANIMALS.sumOf { AnimalgramsEngine.foundWords(progress, it).size }
            BasicText(
                text = "words found $total",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val language = configuration.locales[0]?.language.orEmpty()
            if (!AnimalgramsEngine.isEnglishTag(language)) {
                BasicText(
                    text = "english only · this game accepts english words",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accent),
                )
            }
        }
    }
}

@Composable
private fun AnimalgramsHabitats(progress: AnimalgramsEngine.AnagramProgress, onPick: (AnimalgramsEngine.Habitat) -> Unit, onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "animalgrams · habitats", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            AnimalgramsEngine.HABITATS.forEach { habitat ->
                val percent = AnimalgramsEngine.habitatPercent(progress, habitat)
                val star = AnimalgramsEngine.hasStar(progress, habitat)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(colors.tile)
                        .border(0.5.dp, if (star) colors.accent else colors.border)
                        .pointerInput(habitat.key) { detectTapGestures(onTap = { onPick(habitat) }) }
                        .padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            text = habitat.label,
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                        )
                        Spacer(Modifier.weight(1f))
                        BasicText(
                            text = if (star) "$percent% complete · star" else "$percent% complete",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = if (star) colors.accent else colors.textSecondary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimalgramsAnimals(
    progress: AnimalgramsEngine.AnagramProgress,
    habitat: AnimalgramsEngine.Habitat,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "animalgrams · ${habitat.label}", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            habitat.animals.forEach { animal ->
                val percent = AnimalgramsEngine.animalPercent(progress, animal)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(colors.tile)
                        .border(0.5.dp, if (percent >= 100) colors.accent else colors.border)
                        .pointerInput(animal) { detectTapGestures(onTap = { onPick(animal) }) }
                        .padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            text = animal.lowercase(),
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                        )
                        Spacer(Modifier.weight(1f))
                        BasicText(
                            text = "$percent%",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = if (percent >= 100) colors.accent else colors.textSecondary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimalgramsOptions(
    progress: AnimalgramsEngine.AnagramProgress,
    onSound: (Boolean) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "animalgrams · options", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "sound",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AnimalgramsButton(if (progress.sound) "on" else "on*") { onSound(true) }
                AnimalgramsButton(if (!progress.sound) "off" else "off*") { onSound(false) }
            }
            Spacer(Modifier.height(10.dp))
            AnimalgramsButton("reset progress", Modifier.fillMaxWidth()) { onReset() }
        }
    }
}

@Composable
private fun AnimalgramsLearn(onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "animalgrams · learn", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp).verticalScroll(rememberScrollState())) {
            listOf(
                "each animal hides a list of common words made from its own letters.",
                "tap letter tiles to build a word, then submit. candidates glow before you commit.",
                "erase removes one letter; hold erase for half a second to clear the field.",
                "re-enter restores the last word you submitted.",
                "hint reveals the next letter of an unfound word, preferring the prefix you typed.",
                "find every word to earn the animal medal; complete every animal in a habitat for its star.",
                "our own compact english corpus is used, not the device word list.",
            ).forEach {
                BasicText(
                    text = it,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun AnimalgramsOverlay(title: String, subtitle: String? = null, actions: List<Pair<String, () -> Unit>>) {
    val colors = LocalDoradoColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .background(colors.elevated)
                .border(0.5.dp, colors.border)
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = title,
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = subtitle,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                actions.forEach { (label, action) -> AnimalgramsButton(label) { action() } }
            }
        }
    }
}

@Composable
private fun AnimalgramsButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
            .height(30.dp)
            .background(colors.tile)
            .border(0.5.dp, if (enabled) colors.border else colors.elevated)
            .pointerInput(label, enabled) { if (enabled) detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                fontFamily = Selawik,
                fontSize = DoradoTokens.TYPE_LIST.sp,
                color = if (enabled) colors.textPrimary else colors.textInactive,
            ),
        )
    }
}
