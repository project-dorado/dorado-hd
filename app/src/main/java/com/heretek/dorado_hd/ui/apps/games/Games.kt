package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    data class Board(val rows: List<List<Int>>, val given: List<List<Boolean>>)

    fun newGame(seed: Long = 0L): Board {
        // Generate a solved board via backtrack, then carve out 40 cells.
        val base = List(9) { MutableList(9) { 0 } }
        val rng = Random(if (seed != 0L) seed else System.nanoTime())
        val solved = solve(base, rng) ?: List(9) { List(9) { 1 } }
        val puzzle = solved.map { it.toMutableList() }
        val keep = 81 - 45
        val cells = (0..80).toMutableList()
        cells.shuffle(rng)
        val clear = cells.take(81 - keep)
        for (c in clear) puzzle[c / 9][c % 9] = 0
        return Board(solved, puzzle.map { it.map { it == 0 } })
    }

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

enum class HexColor { A, B, C }
object HexicEngine {
    fun newBoard(size: Int = 7, rng: Random = Random.Default): Array<Array<HexColor>> =
        Array(size) { Array(size) { HexColor.values().random(rng) } }

    /** Cycle the tile's color (advance A→B→C→A). */
    fun rotate(board: Array<Array<HexColor>>, r: Int, c: Int) {
        board[r][c] = when (board[r][c]) { HexColor.A -> HexColor.B; HexColor.B -> HexColor.C; else -> HexColor.A }
    }

    /** Detect all clusters of 3+ adjacent (orthogonal-diagonal) same-color cells.
     *  Returns set of (r,c) to clear. */
    fun clusters(board: Array<Array<HexColor>>): Set<Pair<Int, Int>> {
        val n = board.size
        val visited = Array(n) { BooleanArray(n) }
        val result = mutableSetOf<Pair<Int, Int>>()
        val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, -1 to 1, 1 to -1, 1 to 1)
        for (r in 0 until n) for (c in 0 until n) {
            if (visited[r][c]) continue
            val color = board[r][c]
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

    /** Score = total cells cleared. */
    fun clearAndScore(board: Array<Array<HexColor>>, cells: Set<Pair<Int, Int>>): Int {
        for ((r, c) in cells) board[r][c] = HexColor.A
        return cells.size
    }
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

    fun tapTab(c: Int) {
        val tab = tabs[c]
        val top = tab.cards.lastOrNull() ?: return
        if (!top.faceUp) return
        // try to place on a foundation
        val fi = founds.indexOfFirst { f -> SolitaireEngine.toFoundation(top, f.cards.lastOrNull()) }
        if (fi >= 0) {
            val newPiles = piles.toMutableList()
            newPiles[9 + fi] = founds[fi].copy(cards = founds[fi].cards + top)
            newPiles[c] = tab.copy(cards = tab.cards.dropLast(1))
            piles = newPiles
            return
        }
        // try other tab columns
        for (j in tabs.indices) {
            if (j == c) continue
            val dest = tabs[j]
            val destTop = dest.cards.lastOrNull()?.takeIf { it.faceUp }
            if (SolitaireEngine.legalMove(top, destTop)) {
                val newPiles = piles.toMutableList()
                newPiles[j] = dest.copy(cards = dest.cards + top)
                newPiles[c] = tab.copy(cards = tab.cards.dropLast(1))
                piles = newPiles
                return
            }
        }
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
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                BasicText(
                    text = if (stock.cards.isEmpty()) "recycle" else "deal",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { tapStock() }) }.padding(end = 12.dp),
                )
                BasicText(
                    text = "waste ${waste.cards.size}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                tabs.forEachIndexed { c, tab ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(colors.elevated)
                            .padding(2.dp),
                        verticalArrangement = Arrangement.spacedBy((-14).dp),
                    ) {
                        tab.cards.forEach { card ->
                            SolCardView(card) { tapTab(c) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                founds.forEach { f ->
                    Box(Modifier.size(width = 40.dp, height = 56.dp).background(colors.elevated).padding(2.dp)) {
                        val top = f.cards.lastOrNull()
                        if (top != null) SolCardView(top) {}
                    }
                }
            }
        }
    }
}

@Composable
fun SolCardView(card: SolCard, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    val bg = if (card.faceUp) Color.White else colors.tile
    val fg = if (card.suit.red) colors.accent else Color.White
    Box(
        Modifier
            .size(width = DoradoTokens.CARD_W.dp, height = DoradoTokens.CARD_H.dp)
            .background(bg)
            .padding(2.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
    ) {
        if (card.faceUp) {
            BasicText(
                text = "${card.rank}",
                style = TextStyle(fontFamily = Selawik, fontSize = 11.sp, color = fg),
            )
        }
    }
}

@Composable
fun SudokuApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val board by remember { mutableStateOf(SudokuEngine.newGame()) }
    val cells = remember { mutableStateListOf<Int>().apply { repeat(81) { add(board.rows.flatten()[it]) } } }
    val given = board.given.flatten()
    var elapsed by remember { mutableStateOf(0L) }
    var running by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(-1) }

    LaunchedEffect(running) {
        while (running) { delay(1000); elapsed++ }
    }

    fun setCell(idx: Int, v: Int) {
        if (given[idx]) return
        cells[idx] = v
    }

    fun check(): Boolean {
        val cur = cells.toList()
        for (r in 0 until 9) for (c in 0 until 9) {
            val v = cur[r * 9 + c]
            if (v == 0) return false
            for (i in 0 until 9) if (i != c && cur[r * 9 + i] == v) return false
            for (i in 0 until 9) if (i != r && cur[i * 9 + c] == v) return false
        }
        return true
    }

    val solved = check()
    LaunchedEffect(solved) {
        if (solved) {
            running = false
            scope.launch { graph.games.record("sudoku", (10_000 - elapsed.toInt()).coerceAtLeast(0), null) }
        }
    }

    DetailScaffold(title = "sudoku") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "%02d:%02d".format(elapsed / 60, elapsed % 60),
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.textPrimary),
            )
            Spacer(Modifier.height(4.dp))
            for (r in 0 until 9) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    for (c in 0 until 9) {
                        val idx = r * 9 + c
                        val v = cells[idx]
                        val isGiven = given[idx]
                        val isSel = selected == idx
                        Box(
                            Modifier
                                .size(width = DoradoTokens.SUDOKU_CELL.dp, height = DoradoTokens.SUDOKU_CELL.dp)
                                .background(
                                    when {
                                        isSel -> colors.accent
                                        isGiven -> colors.tile
                                        else -> colors.elevated
                                    },
                                )
                                .pointerInput(Unit) { detectTapGestures(onTap = { selected = idx }) },
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            if (v != 0) BasicText(
                                text = v.toString(),
                                style = TextStyle(
                                    fontFamily = Selawik,
                                    fontSize = DoradoTokens.TYPE_LIST.sp,
                                    color = if (isGiven) colors.textPrimary else colors.accent,
                                ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(1.dp))
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..9).forEach { n ->
                    Box(
                        Modifier
                            .size(width = DoradoTokens.SUDOKU_CELL.dp, height = DoradoTokens.SUDOKU_CELL.dp)
                            .background(colors.elevated)
                            .pointerInput(n) { detectTapGestures(onTap = { if (selected >= 0) setCell(selected, n) }) },
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        BasicText(text = n.toString(), style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary))
                    }
                }
                Box(
                    Modifier
                        .size(width = DoradoTokens.SUDOKU_CELL.dp, height = DoradoTokens.SUDOKU_CELL.dp)
                        .background(colors.elevated)
                        .pointerInput(Unit) { detectTapGestures(onTap = { if (selected >= 0) setCell(selected, 0) }) },
                    contentAlignment = androidx.compose.ui.Alignment.Center,
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
    val board = remember { HexicEngine.newBoard(n) }
    var score by remember { mutableStateOf(0) }
    var best by remember { mutableStateOf(0) }
    val bestFlow by graph.games.top("hexic").collectAsState(initial = emptyList())

    fun tap(r: Int, c: Int) {
        HexicEngine.rotate(board, r, c)
        var gained = 0
        while (true) {
            val cells = HexicEngine.clusters(board)
            if (cells.isEmpty()) break
            gained += HexicEngine.clearAndScore(board, cells)
        }
        score += gained
        if (score > best) best = score
    }

    DetailScaffold(title = "hexic") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(text = "score $score  best ${bestFlow.firstOrNull()?.score ?: 0}", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent))
            Spacer(Modifier.height(8.dp))
            val hexicAccent = LocalDoradoColors.current.accent
            val hexicBg = colors.elevated
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                val cols = n; val rows = n
                val cellW = w / cols
                val cellH = h / rows
                for (r in 0 until rows) for (c in 0 until cols) {
                    val cx = c * cellW + cellW / 2
                    val cy = r * cellH + cellH / 2
                    drawCircle(hexicBg, cellW * 0.45f, Offset(cx, cy))
                    val color = when (board[r][c]) { HexColor.A -> Color.White; HexColor.B -> hexicAccent; HexColor.C -> Color.Yellow }
                    drawCircle(color, cellW * 0.25f, Offset(cx, cy))
                }
            }
            // Tap detection overlay (we use a transparent grid because Canvas pointer
            // routing is non-trivial): render tap cells.
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (r in 0 until n) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (c in 0 until n) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(DoradoTokens.HEX_RADIUS.dp * 3)
                                    .pointerInput(r to c) { detectTapGestures(onTap = { tap(r, c) }) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            BasicText(text = "+ rotate tile • clear 3+ same-color adjacents", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
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
    val legal by remember(board, player) { mutableStateOf(ReversiEngine.legalMoves(board, player)) }

    fun apply(r: Int, c: Int) {
        if ((r to c) !in legal) return
        board = ReversiEngine.apply(board, r, c, player)
        val nextPlayer = if (player == ReversiEngine.BLACK) ReversiEngine.WHITE else ReversiEngine.BLACK
        val nextLegal = ReversiEngine.legalMoves(board, nextPlayer)
        if (nextLegal.isNotEmpty()) {
            player = nextPlayer
        } else if (ReversiEngine.legalMoves(board, player).isNotEmpty()) {
            // skip back to original player if opponent has no moves
        } else {
            val (b, w) = ReversiEngine.score(board)
            scope.launch {
                graph.games.record("reversi", if (b > w) b else if (w > b) w else 0, "B$b W$w")
            }
        }
    }

    LaunchedEffect(board, player) {
        if (player == ReversiEngine.WHITE) {
            // simple AI: pick move with most flips
            delay(250)
            val best = legal.maxByOrNull { (r, c) -> ReversiEngine.apply(board, r, c, player).let { ReversiEngine.score(it).second } } ?: return@LaunchedEffect
            apply(best.first, best.second)
        }
    }

    val (bs, ws) = ReversiEngine.score(board)
    DetailScaffold(title = "reversi") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(text = "you $bs — ai $ws   ${if (player == ReversiEngine.BLACK) "your move" else "ai…"}", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.textPrimary))
            Spacer(Modifier.height(8.dp))
            for (r in 0 until 8) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    for (c in 0 until 8) {
                        val v = board[r][c]
                        val isLegal = (r to c) in legal && player == ReversiEngine.BLACK
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(colors.elevated)
                                .pointerInput(r to c) { detectTapGestures(onTap = { apply(r, c) }) },
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            if (v != ReversiEngine.EMPTY) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(0.7f)
                                        .aspectRatio(1f)
                                        .background(if (v == ReversiEngine.BLACK) Color.Black else Color.White),
                                )
                            } else if (isLegal) {
                                BasicText(text = "·", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent))
                            }
                        }
                    }
                }
            }
        }
    }
}
