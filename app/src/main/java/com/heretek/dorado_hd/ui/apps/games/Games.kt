package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/* ============================================================ */
/*                          Solitaire (Klondike)                  */
/* ============================================================ */

enum class SolSuit {
    SPADE, HEART, CLUB, DIAMOND;
    val red: Boolean get() = this == HEART || this == DIAMOND
}
data class SolCard(val suit: SolSuit, val rank: Int, val faceUp: Boolean = false) {
    override fun toString(): String = "${suit.name.take(1)}${rank}"
}
data class Pile(
    val name: String,
    val cards: List<SolCard>,
)

object SolitaireEngine {
    fun newGame(rng: Random = Random.Default): List<Pile> {
        val deck = (SolSuit.values()).flatMap { s -> (1..13).map { r -> SolCard(s, r, false) } }.shuffled(rng)
        val tabs = mutableListOf<Pile>()
        var idx = 0
        for (c in 0 until 7) {
            val size = c + 1
            val cards = deck.subList(idx, idx + size).mapIndexed { i, card ->
                if (i == size - 1) card.copy(faceUp = true) else card
            }
            tabs += Pile("tab$c", cards)
            idx += size
        }
        val stock = Pile("stock", deck.subList(idx, deck.size))
        val waste = Pile("waste", emptyList())
        val founds = SolSuit.values().map { Pile("found${it.name}", emptyList()) }
        return tabs + stock + waste + founds
    }

    /** Face-up the newly exposed card after the top card leaves a column. */
    fun exposeTop(cards: List<SolCard>): List<SolCard> {
        val last = cards.lastOrNull() ?: return cards
        return if (!last.faceUp) cards.dropLast(1) + last.copy(faceUp = true) else cards
    }

    fun legalMove(card: SolCard, onto: SolCard?): Boolean {
        if (card.faceUp && onto != null && onto.faceUp && card.suit.red != onto.suit.red && card.rank == onto.rank - 1) return true
        return card.faceUp && onto == null && card.rank == 13 // empty column accepts King
    }

    fun toFoundation(card: SolCard, foundationTop: SolCard?): Boolean {
        if (!card.faceUp) return false
        return if (foundationTop == null) card.rank == 1
        else card.suit == foundationTop.suit && card.rank == foundationTop.rank + 1
    }
}

/* ============================================================ */
/*                            Sudoku                              */
/* ============================================================ */

object SudokuEngine {
    data class Board(
        val rows: List<List<Int>>,
        val given: List<List<Boolean>>,
        val solution: List<List<Int>>,
    )

    fun newGame(seed: Long = 0L): Board {
        // Generate a solved board via backtrack, then carve out 36 cells.
        val base = List(9) { MutableList(9) { 0 } }
        val rng = Random(if (seed != 0L) seed else System.nanoTime())
        val solved = solve(base, rng) ?: List(9) { List(9) { 1 } }
        val puzzle = solved.map { it.toMutableList() }
        val keep = 81 - 45
        val cells = (0..80).toMutableList()
        cells.shuffle(rng)
        val clear = cells.take(81 - keep)
        for (c in clear) puzzle[c / 9][c % 9] = 0
        return Board(
            rows = puzzle.map { it.toList() },
            given = puzzle.map { row -> row.map { it != 0 } },
            solution = solved,
        )
    }

    /** True when every cell matches the generated solution. */
    fun isSolved(cells: List<Int>, solution: List<List<Int>>): Boolean =
        cells.size == 81 && cells.indices.all { cells[it] == solution[it / 9][it % 9] }

    private fun solve(grid: List<MutableList<Int>>, rng: Random): List<List<Int>>? {
        for (r in 0 until 9) for (c in 0 until 9) if (grid[r][c] == 0) {
            val nums = (1..9).shuffled(rng)
            for (n in nums) {
                grid[r][c] = n
                if (valid(grid, r, c) && solve(grid, rng) != null) return grid.map { it.toList() }
                grid[r][c] = 0
            }
            return null
        }
        return grid.map { it.toList() }
    }

    private fun valid(grid: List<MutableList<Int>>, r: Int, c: Int): Boolean {
        val v = grid[r][c]
        for (i in 0 until 9) if (i != c && grid[r][i] == v) return false
        for (i in 0 until 9) if (i != r && grid[i][c] == v) return false
        val br = r / 3 * 3; val bc = c / 3 * 3
        for (i in 0 until 3) for (j in 0 until 3) {
            val rr = br + i; val cc = bc + j
            if ((rr != r || cc != c) && grid[rr][cc] == v) return false
        }
        return true
    }
}

/* ============================================================ */
/*                              Hexic                             */
/* ============================================================ */

enum class HexColor { A, B, C, EMPTY }
object HexicEngine {
    fun newBoard(size: Int = 7, rng: Random = Random.Default): Array<Array<HexColor>> =
        Array(size) { Array(size) { listOf(HexColor.A, HexColor.B, HexColor.C).random(rng) } }

    /** Cycle the tile's color (advance A→B→C→A), reviving cleared tiles. */
    fun rotate(board: Array<Array<HexColor>>, r: Int, c: Int): Array<Array<HexColor>> {
        val next = copyOf(board)
        next[r][c] = when (board[r][c]) {
            HexColor.A -> HexColor.B
            HexColor.B -> HexColor.C
            HexColor.EMPTY -> HexColor.A
            else -> HexColor.A
        }
        return next
    }

    /** Detect all clusters of 3+ adjacent (orthogonal-diagonal) same-color cells.
     *  Cleared (EMPTY) cells never participate, so clearing cannot loop. */
    fun clusters(board: Array<Array<HexColor>>): Set<Pair<Int, Int>> {
        val n = board.size
        val visited = Array(n) { BooleanArray(n) }
        val result = mutableSetOf<Pair<Int, Int>>()
        val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, -1 to 1, 1 to -1, 1 to 1)
        for (r in 0 until n) for (c in 0 until n) {
            if (visited[r][c]) continue
            val color = board[r][c]
            if (color == HexColor.EMPTY) {
                visited[r][c] = true
                continue
            }
            val stack = ArrayDeque<Pair<Int, Int>>().apply { add(r to c) }
            val comp = mutableListOf<Pair<Int, Int>>()
            while (stack.isNotEmpty()) {
                val (cr, cc) = stack.removeLast()
                if (cr !in 0 until n || cc !in 0 until n || visited[cr][cc]) continue
                if (board[cr][cc] != color) continue
                visited[cr][cc] = true
                comp += cr to cc
                for ((dr, dc) in dirs) stack.add(cr + dr to cc + dc)
            }
            if (comp.size >= 3) result.addAll(comp)
        }
        return result
    }

    /** Clear the cells; returns a new board. */
    fun clearAndScore(board: Array<Array<HexColor>>, cells: Set<Pair<Int, Int>>): Array<Array<HexColor>> {
        val next = copyOf(board)
        for ((r, c) in cells) next[r][c] = HexColor.EMPTY
        return next
    }

    /** Refill cleared cells with random colors (cascades end before refill). */
    fun refill(board: Array<Array<HexColor>>, rng: Random = Random.Default): Array<Array<HexColor>> {
        val next = copyOf(board)
        for (r in next.indices) for (c in next.indices) {
            if (next[r][c] == HexColor.EMPTY) next[r][c] = listOf(HexColor.A, HexColor.B, HexColor.C).random(rng)
        }
        return next
    }

    private fun copyOf(board: Array<Array<HexColor>>): Array<Array<HexColor>> =
        Array(board.size) { board[it].copyOf() }
}

/* ============================================================ */
/*                            Reversi                             */
/* ============================================================ */

object ReversiEngine {
    const val EMPTY = 0; const val BLACK = 1; const val WHITE = 2
    fun newBoard(): Array<IntArray> = Array(8) { IntArray(8) }.also {
        it[3][3] = WHITE; it[4][4] = WHITE; it[3][4] = BLACK; it[4][3] = BLACK
    }

    fun legalMoves(board: Array<IntArray>, player: Int): Set<Pair<Int, Int>> {
        val moves = mutableSetOf<Pair<Int, Int>>()
        for (r in 0 until 8) for (c in 0 until 8) if (board[r][c] == EMPTY && flips(board, r, c, player).isNotEmpty()) moves.add(r to c)
        return moves
    }

    fun apply(board: Array<IntArray>, r: Int, c: Int, player: Int): Array<IntArray> {
        val next: Array<IntArray> = Array(board.size) { board[it].copyOf() }
        val flips = flips(board, r, c, player)
        if (flips.isEmpty()) return board
        next[r][c] = player
        for ((fr, fc) in flips) next[fr][fc] = player
        return next
    }

    private fun flips(board: Array<IntArray>, r: Int, c: Int, player: Int): List<Pair<Int, Int>> {
        if (board[r][c] != EMPTY) return emptyList()
        val opp = if (player == BLACK) WHITE else BLACK
        val out = mutableListOf<Pair<Int, Int>>()
        for ((dr, dc) in dirs()) {
            var rr = r + dr; var cc = c + dc
            val line = mutableListOf<Pair<Int, Int>>()
            while (rr in 0 until 8 && cc in 0 until 8 && board[rr][cc] == opp) {
                line.add(rr to cc); rr += dr; cc += dc
            }
            if (line.isNotEmpty() && rr in 0 until 8 && cc in 0 until 8 && board[rr][cc] == player) out.addAll(line)
        }
        return out
    }

    private fun dirs(): List<Pair<Int, Int>> = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, -1 to 1, 1 to -1, 1 to 1)

    fun score(board: Array<IntArray>): Pair<Int, Int> {
        var b = 0; var w = 0
        for (r in 0 until 8) for (c in 0 until 8) {
            if (board[r][c] == BLACK) b++ else if (board[r][c] == WHITE) w++
        }
        return b to w
    }
}

/* ============================================================ */
/*                              UI                                 */
/* ============================================================ */

@Composable
fun SolitaireApp() {
    val colors = LocalDoradoColors.current
    var piles by remember { mutableStateOf(SolitaireEngine.newGame()) }
    val tabs = piles.subList(0, 7)
    val stock = piles[7]; val waste = piles[8]; val founds = piles.subList(9, 13)

    /** Try to send [top] to a foundation or another column. */
    fun placeTop(fromIndex: Int, from: Pile, top: SolCard) {
        val fi = founds.indexOfFirst { f -> SolitaireEngine.toFoundation(top, f.cards.lastOrNull()) }
        if (fi >= 0) {
            val newPiles = piles.toMutableList()
            newPiles[9 + fi] = founds[fi].copy(cards = founds[fi].cards + top)
            newPiles[fromIndex] = from.copy(cards = SolitaireEngine.exposeTop(from.cards.dropLast(1)))
            piles = newPiles
            return
        }
        for (j in tabs.indices) {
            if (j == fromIndex) continue
            val dest = tabs[j]
            val destTop = dest.cards.lastOrNull()?.takeIf { it.faceUp }
            if (SolitaireEngine.legalMove(top, destTop)) {
                val newPiles = piles.toMutableList()
                newPiles[j] = dest.copy(cards = dest.cards + top)
                newPiles[fromIndex] = from.copy(cards = SolitaireEngine.exposeTop(from.cards.dropLast(1)))
                piles = newPiles
                return
            }
        }
    }

    fun tapTab(c: Int) {
        val tab = tabs[c]
        val top = tab.cards.lastOrNull() ?: return
        if (!top.faceUp) {
            // Flip the exposed top card.
            val newPiles = piles.toMutableList()
            newPiles[c] = tab.copy(cards = tab.cards.dropLast(1) + top.copy(faceUp = true))
            piles = newPiles
            return
        }
        placeTop(c, tab, top)
    }

    fun tapWaste() {
        val top = waste.cards.lastOrNull()?.takeIf { it.faceUp } ?: return
        placeTop(8, waste, top)
    }

    fun tapStock() {
        if (stock.cards.isEmpty()) {
            // recycle waste → stock
            val recyc = waste.cards.reversed()
            piles = piles.toMutableList().also {
                it[7] = stock.copy(cards = recyc.map { c -> c.copy(faceUp = false) })
                it[8] = waste.copy(cards = emptyList())
            }
            return
        }
        val drawn = stock.cards.last()
        piles = piles.toMutableList().also {
            it[7] = stock.copy(cards = stock.cards.dropLast(1))
            it[8] = waste.copy(cards = waste.cards + drawn.copy(faceUp = true))
        }
    }

    DetailScaffold(title = "solitaire") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                EdgeText(
                    text = if (stock.cards.isEmpty()) "recycle" else "deal",
                    color = colors.accent,
                    onClick = { tapStock() },
                )
                Spacer(Modifier.width(10.dp))
                // Waste is a real, playable pile now.
                if (waste.cards.lastOrNull() != null) {
                    SolCardView(waste.cards.last(), width = 26.dp, height = 36.dp) { tapWaste() }
                } else {
                    BasicText(
                        text = "waste",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                tabs.forEachIndexed { c, tab ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(colors.elevated)
                            .padding(1.dp),
                        verticalArrangement = Arrangement.spacedBy((-20).dp),
                    ) {
                        tab.cards.forEach { card ->
                            SolCardView(card, width = 28.dp, height = 38.dp) { tapTab(c) }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                founds.forEach { f ->
                    Box(
                        Modifier
                            .size(width = 28.dp, height = 38.dp)
                            .background(colors.elevated)
                            .padding(1.dp),
                    ) {
                        val top = f.cards.lastOrNull()
                        if (top != null) SolCardView(top, width = 26.dp, height = 36.dp) {}
                        else BasicText(
                            text = if (f.name.contains("SPADE")) "♠" else if (f.name.contains("HEART")) "♥" else if (f.name.contains("CLUB")) "♣" else "♦",
                            style = TextStyle(fontFamily = Selawik, fontSize = 10.sp, color = colors.textSecondary),
                            modifier = Modifier.padding(2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EdgeText(text: String, color: Color, onClick: () -> Unit) {
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = Selawik,
            fontSize = DoradoTokens.TYPE_LIST.sp,
            color = color,
        ),
        modifier = Modifier
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(vertical = 4.dp, horizontal = 2.dp),
    )
}

@Composable
fun SolCardView(card: SolCard, width: Dp = DoradoTokens.CARD_W.dp, height: Dp = DoradoTokens.CARD_H.dp, onClick: () -> Unit = {}) {
    val colors = LocalDoradoColors.current
    val bg = if (card.faceUp) Color.White else colors.tile
    // Black suits must be dark on the white face; using Color.White here made
    // face-up clubs/spades invisible.
    val fg = if (card.suit.red) colors.accent else Color.Black
    Box(
        Modifier
            .size(width = width, height = height)
            .background(bg)
            .padding(1.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
    ) {
        if (card.faceUp) {
            BasicText(
                text = "${rankLabel(card.rank)}${suitGlyph(card.suit)}",
                style = TextStyle(fontFamily = Selawik, fontSize = 10.sp, color = fg),
            )
        }
    }
}

private fun rankLabel(rank: Int): String = when (rank) {
    1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; else -> rank.toString()
}

private fun suitGlyph(suit: SolSuit): String = when (suit) {
    SolSuit.SPADE -> "♠"; SolSuit.HEART -> "♥"; SolSuit.CLUB -> "♣"; SolSuit.DIAMOND -> "♦"
}

@Composable
fun SudokuApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val board = remember { SudokuEngine.newGame() }
    val cells = remember { mutableStateListOf<Int>().apply { repeat(81) { add(board.rows.flatten()[it]) } } }
    val given = board.given.flatten()
    var elapsed by remember { mutableStateOf(0L) }
    var running by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(-1) }
    var recorded by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running) { delay(1000); elapsed++ }
    }

    fun setCell(idx: Int, v: Int) {
        if (given[idx]) return
        cells[idx] = v
    }

    val solved = SudokuEngine.isSolved(cells.toList(), board.solution)
    LaunchedEffect(solved) {
        if (solved && !recorded) {
            recorded = true
            running = false
            scope.launch { graph.games.record("sudoku", (10_000 - elapsed.toInt()).coerceAtLeast(0), null) }
        }
    }

    DetailScaffold(title = "sudoku") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = if (solved) "solved" else "%02d:%02d".format(elapsed / 60, elapsed % 60),
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = if (solved) colors.accent else colors.textPrimary),
            )
            Spacer(Modifier.height(4.dp))
            // Board scales to whichever axis is tightest, so all 9 rows are
            // always visible in device mode.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val cell = minOf(maxWidth, maxHeight) / 9
                Column {
                    for (r in 0 until 9) {
                        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            for (c in 0 until 9) {
                                val idx = r * 9 + c
                                val v = cells[idx]
                                val isGiven = given[idx]
                                val isSel = selected == idx
                                Box(
                                    Modifier
                                        .size(cell)
                                        .background(
                                            when {
                                                isSel -> colors.accent
                                                isGiven -> colors.tile
                                                else -> colors.elevated
                                            },
                                        )
                                        .pointerInput(idx) { detectTapGestures(onTap = { selected = idx }) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (v != 0) BasicText(
                                        text = v.toString(),
                                        style = TextStyle(
                                            fontFamily = Selawik,
                                            fontSize = 13.sp,
                                            color = if (isGiven) colors.textPrimary else colors.accent,
                                        ),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(1.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                (1..9).forEach { n ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(26.dp)
                            .background(colors.elevated)
                            .pointerInput(n) { detectTapGestures(onTap = { if (selected >= 0) setCell(selected, n) }) },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(text = n.toString(), style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary))
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(26.dp)
                        .background(colors.elevated)
                        .pointerInput(Unit) { detectTapGestures(onTap = { if (selected >= 0) setCell(selected, 0) }) },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(text = "x", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
                }
            }
        }
    }
}

@Composable
fun HexicApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val n = 7
    var board by remember { mutableStateOf(HexicEngine.newBoard(n)) }
    var score by remember { mutableStateOf(0) }
    var best by remember { mutableStateOf(0) }
    val bestFlow by graph.games.top("hexic").collectAsState(initial = emptyList())

    DisposableEffect(Unit) {
        onDispose {
            val finalScore = score
            if (finalScore > 0) {
                scope.launch(NonCancellable) { graph.games.record("hexic", finalScore, null) }
            }
        }
    }

    fun tap(r: Int, c: Int) {
        var next = HexicEngine.rotate(board, r, c)
        var gained = 0
        // Cascade: EMPTY cells never form clusters, so each pass strictly
        // reduces the non-empty tile count and the loop terminates.
        while (true) {
            val cells = HexicEngine.clusters(next)
            if (cells.isEmpty()) break
            gained += cells.size
            next = HexicEngine.clearAndScore(next, cells)
        }
        next = HexicEngine.refill(next)
        board = next
        score += gained
        if (score > best) best = score
    }

    DetailScaffold(title = "hexic") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(text = "score $score  best ${bestFlow.firstOrNull()?.score ?: 0}", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent))
            Spacer(Modifier.height(6.dp))
            val hexicAccent = colors.accent
            val hexicBg = colors.elevated
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(n) {
                        // Hit-test on the same Canvas that draws the board; the
                        // old overlay was offset from the drawing and unreachable.
                        detectTapGestures { offset ->
                            val cellW = size.width / n.toFloat()
                            val cellH = size.height / n.toFloat()
                            val c = (offset.x / cellW).toInt().coerceIn(0, n - 1)
                            val r = (offset.y / cellH).toInt().coerceIn(0, n - 1)
                            tap(r, c)
                        }
                    },
            ) {
                val w = size.width; val h = size.height
                val cellW = w / n
                val cellH = h / n
                for (r in 0 until n) for (c in 0 until n) {
                    val cx = c * cellW + cellW / 2
                    val cy = r * cellH + cellH / 2
                    drawCircle(hexicBg, cellW * 0.45f, Offset(cx, cy))
                    val color = when (board[r][c]) {
                        HexColor.A -> Color.White
                        HexColor.B -> hexicAccent
                        HexColor.C -> Color.Yellow
                        HexColor.EMPTY -> hexicBg
                    }
                    drawCircle(color, cellW * 0.25f, Offset(cx, cy))
                }
            }
            Spacer(Modifier.height(6.dp))
            BasicText(text = "rotate a tile • clear 3+ same-color adjacents", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
        }
    }
}

@Composable
fun ReversiApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    var board by remember { mutableStateOf(ReversiEngine.newBoard()) }
    var player by remember { mutableStateOf(ReversiEngine.BLACK) }
    var recorded by remember { mutableStateOf(false) }
    val legal = remember(board, player) { ReversiEngine.legalMoves(board, player) }
    val gameOver = remember(board) {
        ReversiEngine.legalMoves(board, ReversiEngine.BLACK).isEmpty() &&
            ReversiEngine.legalMoves(board, ReversiEngine.WHITE).isEmpty()
    }

    fun apply(r: Int, c: Int) {
        if ((r to c) !in legal) return
        val nextBoard = ReversiEngine.apply(board, r, c, player)
        board = nextBoard
        val opponent = if (player == ReversiEngine.BLACK) ReversiEngine.WHITE else ReversiEngine.BLACK
        // Opponent passes when it has no legal move; board then returns to us.
        player = if (ReversiEngine.legalMoves(nextBoard, opponent).isNotEmpty()) opponent else player
    }

    LaunchedEffect(board, player) {
        if (player == ReversiEngine.WHITE && !gameOver) {
            delay(250)
            // Simple AI: pick the move with the most flips; off the main thread.
            val best = withContext(Dispatchers.Default) {
                legal.maxByOrNull { (r, c) ->
                    ReversiEngine.score(ReversiEngine.apply(board, r, c, player)).second
                }
            } ?: return@LaunchedEffect
            apply(best.first, best.second)
        }
    }

    val (bs, ws) = ReversiEngine.score(board)
    LaunchedEffect(gameOver) {
        if (gameOver && !recorded) {
            recorded = true
            val score = when {
                bs > ws -> 1
                ws > bs -> 0
                else -> 1 // draw counts as a non-loss
            }
            scope.launch { graph.games.record("reversi", score, "B$bs W$ws") }
        }
    }

    DetailScaffold(title = "reversi") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                BasicText(
                    text = when {
                        gameOver && bs > ws -> "you win $bs—$ws"
                        gameOver && ws > bs -> "ai wins $bs—$ws"
                        gameOver -> "draw $bs—$ws"
                        player == ReversiEngine.BLACK -> "you $bs — ai $ws   your move"
                        else -> "you $bs — ai $ws   ai…"
                    },
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = if (gameOver) colors.accent else colors.textPrimary),
                    modifier = Modifier.weight(1f),
                )
                if (gameOver) {
                    EdgeText(text = "new game", color = colors.accent, onClick = {
                        board = ReversiEngine.newBoard()
                        player = ReversiEngine.BLACK
                        recorded = false
                    })
                }
            }
            Spacer(Modifier.height(6.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val cell = minOf(maxWidth, maxHeight) / 8
                Column {
                    for (r in 0 until 8) {
                        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            for (c in 0 until 8) {
                                val v = board[r][c]
                                val isLegal = (r to c) in legal && player == ReversiEngine.BLACK && !gameOver
                                Box(
                                    Modifier
                                        .size(cell)
                                        .background(colors.elevated)
                                        .pointerInput(r to c) { detectTapGestures(onTap = { apply(r, c) }) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (v != ReversiEngine.EMPTY) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth(0.7f)
                                                .fillMaxHeight(0.7f)
                                                .background(if (v == ReversiEngine.BLACK) Color.Black else Color.White),
                                        )
                                    } else if (isLegal) {
                                        BasicText(text = "·", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(1.dp))
                    }
                }
            }
        }
    }
}
