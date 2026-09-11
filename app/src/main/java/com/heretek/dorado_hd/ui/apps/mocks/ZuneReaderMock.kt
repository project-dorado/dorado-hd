package com.heretek.dorado_hd.ui.apps.mocks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.delay

/**
 * Zune Reader (docs/apps/zunereader.md, W7): library → reader with font steps,
 * TOC / bookmark menus, a light/dark reader theme and a history stack.
 *
 * The OPDS catalog and importers are gone; the three books are original
 * clean-room texts written for Dorado-HD. Reading position, bookmarks, font
 * size and theme persist in `graph.appState`.
 */

private enum class ReaderPane { LIBRARY, READER, TOC, BOOKMARKS, MENU, THEME, ABOUT, CATALOGS }

private data class ReaderPalette(
    val background: Color,
    val foreground: Color,
    val meta: Color,
)

@Composable
fun ZuneReaderApp() {
    val graph = LocalDoradoGraph.current
    var loaded by remember { mutableStateOf(false) }
    var pane by remember { mutableStateOf(ReaderPane.LIBRARY) }
    var state by remember { mutableStateOf(ReaderState()) }
    var currentBookId by remember { mutableStateOf<String?>(null) }
    var pageIndex by remember { mutableStateOf(0) }
    var history by remember { mutableStateOf(emptyList<Int>()) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        graph.appState.get("zunereader")?.let { raw ->
            ReaderStateCodec.decode(raw)?.let { state = it }
        }
        state.lastBookId?.let { currentBookId = it }
        loaded = true
    }

    LaunchedEffect(state, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(500)
        graph.appState.put("zunereader", ReaderStateCodec.encode(state))
    }

    val book = ReaderModel.bookById(currentBookId)
    val doc = remember(book) { book?.let { ReaderModel.parseBook(it) } }
    val fontSp = ReaderModel.FONT_STOPS[state.fontIndex.coerceIn(0, ReaderModel.FONT_STOPS.lastIndex)]
    val pagination = remember(doc, fontSp) {
        doc?.let { ReaderModel.paginate(it.blocks, ReaderModel.charsPerPage(fontSp)) }
    }
    LaunchedEffect(pagination) {
        val pag = pagination ?: return@LaunchedEffect
        if (pageIndex > pag.pages.lastIndex) pageIndex = pag.pages.lastIndex.coerceAtLeast(0)
    }

    fun savePosition(bookId: String, page: Int) {
        val pag = pagination ?: return
        val start = pag.startBlocks.getOrElse(page) { 0 }
        state = state.copy(lastBookId = bookId, positions = state.positions + (bookId to start))
    }

    fun openBook(book: ReaderBook) {
        val parsed = ReaderModel.parseBook(book)
        val chars = ReaderModel.charsPerPage(fontSp)
        val pag = ReaderModel.paginate(parsed.blocks, chars)
        val savedBlock = state.positions[book.id] ?: 0
        currentBookId = book.id
        pageIndex = ReaderModel.pageForBlock(pag, savedBlock)
        history = emptyList()
        pane = ReaderPane.READER
    }

    fun jumpToBlock(bookId: String, blockIndex: Int) {
        val pag = pagination ?: return
        history = ReaderModel.pushHistory(history, pageIndex)
        pageIndex = ReaderModel.pageForBlock(pag, blockIndex)
        savePosition(bookId, pageIndex)
    }

    fun stepFont(up: Boolean) {
        val next = ReaderModel.nextFontStop(ReaderModel.FONT_STOPS[state.fontIndex], up)
        val index = ReaderModel.FONT_STOPS.indexOf(next)
        if (index >= 0) state = state.copy(fontIndex = index)
    }

    fun addBookmark() {
        val id = currentBookId ?: return
        val pag = pagination ?: return
        val block = pag.startBlocks.getOrElse(pageIndex) { 0 }
        val label = "page ${pageIndex + 1} · " + (pag.pages.getOrNull(pageIndex)?.take(28) ?: "bookmark")
        val list = state.bookmarks[id].orEmpty()
        val updated = ReaderModel.addBookmark(list, Bookmark("m$block", block, label))
        state = state.copy(bookmarks = state.bookmarks + (id to updated))
        note = "bookmark saved"
    }

    when (pane) {
        ReaderPane.LIBRARY -> LibraryScreen(
            theme = state.theme,
            positions = state.positions,
            note = note,
            onOpen = { openBook(it) },
            onAddUrl = { note = "import by url is offline — the catalog service closed in 2012." },
        )
        ReaderPane.READER -> if (book == null || doc == null || pagination == null) {
            LaunchedEffect(Unit) { pane = ReaderPane.LIBRARY }
        } else {
            ReaderScreen(
                book = book,
                doc = doc,
                pagination = pagination,
                pageIndex = pageIndex,
                fontSp = fontSp,
                theme = state.theme,
                note = note,
                bookmarkCount = state.bookmarks[book.id].orEmpty().size,
                onBack = {
                    if (history.isEmpty()) {
                        savePosition(book.id, pageIndex)
                        pane = ReaderPane.LIBRARY
                    } else {
                        pageIndex = history.last()
                        history = ReaderModel.popHistory(history)
                    }
                },
                onPrev = {
                    if (pageIndex > 0) {
                        history = ReaderModel.pushHistory(history, pageIndex)
                        pageIndex -= 1
                        savePosition(book.id, pageIndex)
                    }
                },
                onNext = {
                    if (pageIndex < pagination.pages.lastIndex) {
                        history = ReaderModel.pushHistory(history, pageIndex)
                        pageIndex += 1
                        savePosition(book.id, pageIndex)
                    }
                },
                onFontSmaller = { stepFont(false) },
                onFontLarger = { stepFont(true) },
                onToc = { pane = ReaderPane.TOC },
                onBookmarks = { pane = ReaderPane.BOOKMARKS },
                onAddBookmark = { addBookmark() },
                onMenu = { pane = ReaderPane.MENU },
            )
        }
        ReaderPane.TOC -> TocScreen(
            toc = doc?.toc.orEmpty(),
            onBack = { pane = ReaderPane.READER },
            onSelect = { entry -> currentBookId?.let { jumpToBlock(it, entry.blockIndex) }; pane = ReaderPane.READER },
        )
        ReaderPane.BOOKMARKS -> BookmarksScreen(
            bookmarks = currentBookId?.let { state.bookmarks[it] }.orEmpty(),
            onBack = { pane = ReaderPane.READER },
            onSelect = { mark -> currentBookId?.let { jumpToBlock(it, mark.blockIndex) }; pane = ReaderPane.READER },
            onRemove = { mark ->
                val id = currentBookId ?: return@BookmarksScreen
                state = state.copy(bookmarks = state.bookmarks + (id to ReaderModel.removeBookmark(state.bookmarks[id].orEmpty(), mark.id)))
            },
        )
        ReaderPane.MENU -> MenuScreen(
            theme = state.theme,
            onBack = { pane = ReaderPane.READER },
            onLibrary = { currentBookId?.let { savePosition(it, pageIndex) }; pane = ReaderPane.LIBRARY },
            onTheme = { pane = ReaderPane.THEME },
            onAbout = { pane = ReaderPane.ABOUT },
            onCatalogs = { pane = ReaderPane.CATALOGS },
        )
        ReaderPane.THEME -> ThemeScreen(
            theme = state.theme,
            onBack = { pane = ReaderPane.MENU },
            onSelect = { state = state.copy(theme = it) },
        )
        ReaderPane.ABOUT -> AboutScreen(onBack = { pane = ReaderPane.MENU })
        ReaderPane.CATALOGS -> CatalogsScreen(onBack = { pane = ReaderPane.MENU })
    }
}

@Composable
private fun LibraryScreen(
    theme: ReaderTheme,
    positions: Map<String, Int>,
    note: String?,
    onOpen: (ReaderBook) -> Unit,
    onAddUrl: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val palette = readerPalette(theme)
    DetailScaffold(title = "zune reader") {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(palette.background)
                .padding(horizontal = DoradoTokens.EDGE.dp),
        ) {
            EdgeCropText(
                text = "offline library — catalog service closed · sample texts authored for dorado-hd",
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = colors.accent,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            note?.let {
                EdgeCropText(it, DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
            }
            ReaderModel.books().forEach { book ->
                val doc = remember(book) { ReaderModel.parseBook(book) }
                val block = positions[book.id]
                val progress = if (block != null) "resume · ${ReaderModel.progressPercent(block, doc)}% read" else "not started"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable { onOpen(book) },
                ) {
                    Box(
                        Modifier
                            .size(width = 40.dp, height = 56.dp)
                            .background(colors.tile),
                        contentAlignment = Alignment.Center,
                    ) {
                        EdgeCropText(book.title.take(1), DoradoTokens.TYPE_NOW_TITLE.dp, color = colors.accent)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        EdgeCropText(book.title, DoradoTokens.TYPE_LIST.dp, color = palette.foreground)
                        EdgeCropText(book.author, DoradoTokens.TYPE_CAPTION.dp, color = palette.meta)
                        EdgeCropText(progress, DoradoTokens.TYPE_CAPTION.dp, color = colors.accent)
                    }
                }
            }
            EdgeCropText(
                text = "add book by url — offline",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.textInactive,
                modifier = Modifier
                    .clickable(onClick = onAddUrl)
                    .padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ReaderScreen(
    book: ReaderBook,
    doc: ReaderDoc,
    pagination: Pagination,
    pageIndex: Int,
    fontSp: Int,
    theme: ReaderTheme,
    note: String?,
    bookmarkCount: Int,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onFontSmaller: () -> Unit,
    onFontLarger: () -> Unit,
    onToc: () -> Unit,
    onBookmarks: () -> Unit,
    onAddBookmark: () -> Unit,
    onMenu: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val palette = readerPalette(theme)
    DetailScaffold(title = book.title, onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .background(palette.background),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 2.dp),
            ) {
                EdgeCropText("${book.author} · ${doc.title}", DoradoTokens.TYPE_CAPTION.dp, color = palette.meta, modifier = Modifier.weight(1f))
                ActionLabel("toc", palette.meta, onToc)
                ActionLabel("marks ($bookmarkCount)", palette.meta, onBookmarks)
                ActionLabel("bookmark", colors.accent, onAddBookmark)
                ActionLabel("menu", palette.meta, onMenu)
            }
            note?.let {
                EdgeCropText(it, DoradoTokens.TYPE_CAPTION.dp, color = colors.accent, modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp))
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DoradoTokens.EDGE.dp),
            ) {
                BasicText(
                    text = pagination.pages.getOrNull(pageIndex).orEmpty(),
                    style = TextStyle(
                        fontFamily = Selawik,
                        fontSize = fontSp.sp,
                        color = palette.foreground,
                        lineHeight = (fontSp * 1.45f).sp,
                    ),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 4.dp),
            ) {
                ActionLabel("prev", if (pageIndex > 0) palette.meta else colors.textInactive, onPrev)
                EdgeCropText(
                    text = "page ${pageIndex + 1} / ${pagination.pages.size.coerceAtLeast(1)}",
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = palette.meta,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                ActionLabel("next", if (pageIndex < pagination.pages.lastIndex) palette.meta else colors.textInactive, onNext)
                Spacer(Modifier.weight(1f))
                ActionLabel("a-", palette.meta, onFontSmaller)
                EdgeCropText("$fontSp", DoradoTokens.TYPE_CAPTION.dp, color = palette.meta, modifier = Modifier.padding(horizontal = 4.dp))
                ActionLabel("a+", palette.meta, onFontLarger)
            }
        }
    }
}

@Composable
private fun TocScreen(toc: List<TocEntry>, onBack: () -> Unit, onSelect: (TocEntry) -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "table of contents", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DoradoTokens.EDGE.dp),
        ) {
            if (toc.isEmpty()) {
                Wrapped("no table of contents in this book.", DoradoTokens.TYPE_LIST.sp, colors.textSecondary)
            }
            toc.forEach { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clickable { onSelect(entry) },
                ) {
                    Spacer(Modifier.width(((entry.level - 1).coerceAtLeast(0) * 16).dp))
                    EdgeCropText(entry.title, DoradoTokens.TYPE_LIST.dp, color = if (entry.level <= 1) colors.textPrimary else colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun BookmarksScreen(
    bookmarks: List<Bookmark>,
    onBack: () -> Unit,
    onSelect: (Bookmark) -> Unit,
    onRemove: (Bookmark) -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "bookmarks", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DoradoTokens.EDGE.dp),
        ) {
            if (bookmarks.isEmpty()) {
                Wrapped("no bookmarks yet — press bookmark while reading.", DoradoTokens.TYPE_LIST.sp, colors.textSecondary)
            }
            bookmarks.forEach { mark ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(36.dp)) {
                    EdgeCropText("remove", DoradoTokens.TYPE_CAPTION.dp, color = colors.textInactive, modifier = Modifier.clickable { onRemove(mark) })
                    Spacer(Modifier.width(16.dp))
                    EdgeCropText(mark.label, DoradoTokens.TYPE_LIST.dp, color = colors.textPrimary, modifier = Modifier.weight(1f).clickable { onSelect(mark) })
                }
            }
        }
    }
}

@Composable
private fun MenuScreen(
    theme: ReaderTheme,
    onBack: () -> Unit,
    onLibrary: () -> Unit,
    onTheme: () -> Unit,
    onAbout: () -> Unit,
    onCatalogs: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "menu", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DoradoTokens.EDGE.dp),
        ) {
            MenuRow("my books", "all ${ReaderModel.books().size} titles", colors, onLibrary)
            MenuRow("reader theme", theme.label, colors, onTheme)
            MenuRow("catalogs", "offline — catalog service closed", colors, onCatalogs)
            MenuRow("about zune reader", "archived 2012 · clean-room rebuild", colors, onAbout)
        }
    }
}

@Composable
private fun ThemeScreen(theme: ReaderTheme, onBack: () -> Unit, onSelect: (ReaderTheme) -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "reader theme", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = DoradoTokens.EDGE.dp),
        ) {
            ReaderTheme.entries.forEach { item ->
                val selected = item == theme
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(44.dp).clickable { onSelect(item) }) {
                    Box(
                        Modifier
                            .size(width = 28.dp, height = 28.dp)
                            .background(if (item == ReaderTheme.DARK) colors.background else colors.textPrimary),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        EdgeCropText(item.label, DoradoTokens.TYPE_LIST.dp, color = if (selected) colors.accent else colors.textPrimary)
                        EdgeCropText(if (selected) "selected" else "tap to use", DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "about zune reader", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EdgeCropText("archived 2012", DoradoTokens.TYPE_NOW_META.dp, color = colors.accent)
            Wrapped("the device reader imported epub, html, palm and pml files and browsed opds catalogs. those importers and the catalog service are gone.", DoradoTokens.TYPE_LIST.sp, colors.textPrimary)
            Wrapped("this build keeps a local library with three original sample texts, font steps, bookmarks and a light/dark reader theme. no books from the corpus or any third party are bundled.", DoradoTokens.TYPE_LIST.sp, colors.textPrimary)
        }
    }
}

@Composable
private fun CatalogsScreen(onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "catalogs", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EdgeCropText("offline — catalog service closed", DoradoTokens.TYPE_NOW_META.dp, color = colors.accent)
            Wrapped("the pre-registered opds catalog and add-catalog-by-url pointed at services that no longer answer. browse and download are disabled in this rebuild.", DoradoTokens.TYPE_LIST.sp, colors.textPrimary)
        }
    }
}

@Composable
private fun MenuRow(label: String, subtitle: String, colors: com.heretek.dorado_hd.design.DoradoColors, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        EdgeCropText(label, DoradoTokens.TYPE_LIST.dp, color = colors.textPrimary)
        EdgeCropText(subtitle, DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
    }
}

@Composable
private fun ActionLabel(label: String, color: Color, onClick: () -> Unit) {
    EdgeCropText(
        text = label,
        fontSize = DoradoTokens.TYPE_CAPTION.dp,
        color = color,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
    )
}

@Composable
private fun readerPalette(theme: ReaderTheme): ReaderPalette {
    val colors = LocalDoradoColors.current
    return when (theme) {
        ReaderTheme.DARK -> ReaderPalette(colors.background, colors.textPrimary, colors.textSecondary)
        ReaderTheme.LIGHT -> ReaderPalette(colors.textPrimary, colors.background, colors.background.copy(alpha = 0.6f))
    }
}

@Composable
private fun Wrapped(text: String, size: TextUnit, color: Color) {
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = Selawik,
            fontSize = size,
            color = color,
            lineHeight = size * 1.4f,
        ),
    )
}
