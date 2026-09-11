package com.heretek.dorado_hd.ui.apps.mocks

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
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
 * MSN Money (docs/apps/msnmoney.md, W7): bottom twist menu over watchlist /
 * markets / currencies / articles, quote rows and detail with a chart, plus
 * the converter and the capped ticker manager.
 *
 * The MSN Money feeds are dead. Quotes, charts and articles are local,
 * deterministic simulations and every screen is labelled archived.
 */

private enum class MoneyPane { SECTION, DETAIL, ARTICLE, MANAGER, INFO }

@Composable
fun MsnMoneyApp() {
    val graph = LocalDoradoGraph.current
    var loaded by remember { mutableStateOf(false) }
    var pane by remember { mutableStateOf(MoneyPane.SECTION) }
    var section by remember { mutableStateOf(MoneySection.WATCHLIST) }
    var watchlist by remember { mutableStateOf(MoneyModel.DEFAULT_WATCHLIST) }
    var fromCode by remember { mutableStateOf("USD") }
    var toCode by remember { mutableStateOf("EUR") }
    var amount by remember { mutableStateOf("100") }
    var tick by remember { mutableStateOf(0) }
    var detailSymbol by remember { mutableStateOf<String?>(null) }
    var articleId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        graph.appState.get("msnmoney")?.let { raw ->
            MoneyStateCodec.decode(raw)?.let { saved ->
                section = saved.section
                watchlist = saved.watchlist.ifEmpty { MoneyModel.DEFAULT_WATCHLIST }
                fromCode = saved.fromCode
                toCode = saved.toCode
                amount = saved.amount
            }
        }
        loaded = true
    }

    LaunchedEffect(section, watchlist, fromCode, toCode, amount, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(500)
        graph.appState.put(
            "msnmoney",
            MoneyStateCodec.encode(MoneyState(section, watchlist, fromCode, toCode, amount)),
        )
    }

    when (pane) {
        MoneyPane.SECTION -> SectionScreen(
            section = section,
            onSection = { section = it },
            watchlist = watchlist,
            tick = tick,
            onRefresh = { tick++ },
            fromCode = fromCode,
            toCode = toCode,
            amount = amount,
            onAmount = { amount = it },
            onFrom = { fromCode = it },
            onTo = { toCode = it },
            onOpenDetail = { symbol ->
                detailSymbol = symbol
                pane = MoneyPane.DETAIL
            },
            onOpenArticle = { id ->
                articleId = id
                pane = MoneyPane.ARTICLE
            },
            onOpenManager = { query = ""; pane = MoneyPane.MANAGER },
            onOpenInfo = { pane = MoneyPane.INFO },
        )
        MoneyPane.DETAIL -> {
            val quote = MoneyModel.quoteFor(detailSymbol.orEmpty())
            if (quote == null) {
                pane = MoneyPane.SECTION
            } else {
                QuoteDetailScreen(
                    quote = quote,
                    tick = tick,
                    onBack = { pane = MoneyPane.SECTION },
                    onNews = { section = MoneySection.ARTICLES; pane = MoneyPane.SECTION },
                    onCurrency = { section = MoneySection.CURRENCIES; pane = MoneyPane.SECTION },
                )
            }
        }
        MoneyPane.ARTICLE -> {
            val article = Newsroom.byId(articleId.orEmpty())
            if (article == null) {
                pane = MoneyPane.SECTION
            } else {
                ArticleScreen(article = article, onBack = { pane = MoneyPane.SECTION })
            }
        }
        MoneyPane.MANAGER -> ManagerScreen(
            watchlist = watchlist,
            onWatchlist = { watchlist = it },
            query = query,
            onQuery = { query = it },
            onBack = { pane = MoneyPane.SECTION },
        )
        MoneyPane.INFO -> InfoScreen(onBack = { pane = MoneyPane.SECTION })
    }
}

@Composable
private fun SectionScreen(
    section: MoneySection,
    onSection: (MoneySection) -> Unit,
    watchlist: List<String>,
    tick: Int,
    onRefresh: () -> Unit,
    fromCode: String,
    toCode: String,
    amount: String,
    onAmount: (String) -> Unit,
    onFrom: (String) -> Unit,
    onTo: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    onOpenManager: () -> Unit,
    onOpenInfo: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "msn money") {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DoradoTokens.EDGE.dp),
            ) {
                EdgeCropText(section.label, DoradoTokens.TYPE_NOW_META.dp, color = colors.accent)
                Spacer(Modifier.width(10.dp))
                EdgeCropText(
                    text = "archived 2012 — no live quotes · simulated feed",
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = colors.textInactive,
                    modifier = Modifier.weight(1f),
                )
                if (section == MoneySection.WATCHLIST) {
                    EdgeCropText(
                        text = "manage",
                        fontSize = DoradoTokens.TYPE_NOW_META.dp,
                        color = colors.textPrimary,
                        modifier = Modifier
                            .clickable(onClick = onOpenManager)
                            .padding(horizontal = 6.dp),
                    )
                }
                EdgeCropText(
                    text = "info",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .clickable(onClick = onOpenInfo)
                        .padding(horizontal = 6.dp),
                )
                EdgeCropText(
                    text = "refresh",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    color = colors.accent,
                    modifier = Modifier
                        .clickable(onClick = onRefresh)
                        .padding(horizontal = 6.dp),
                )
            }

            Box(Modifier.weight(1f)) {
                when (section) {
                    MoneySection.WATCHLIST -> WatchlistSection(watchlist, tick, onOpenDetail)
                    MoneySection.MARKETS -> MarketsSection(tick, onOpenDetail)
                    MoneySection.CURRENCIES -> ConverterSection(
                        fromCode, toCode, amount, onAmount, onFrom, onTo,
                    )
                    MoneySection.ARTICLES -> ArticlesSection(onOpenArticle)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DoradoTokens.CROSSBAR_HEIGHT.dp),
            ) {
                MoneySection.entries.forEach { item ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable { onSection(item) },
                        contentAlignment = Alignment.Center,
                    ) {
                        EdgeCropText(
                            text = item.label,
                            fontSize = DoradoTokens.TYPE_CROSSBAR.dp,
                            color = if (item == section) colors.textPrimary else colors.textInactive,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistSection(watchlist: List<String>, tick: Int, onOpenDetail: (String) -> Unit) {
    val colors = LocalDoradoColors.current
    val quotes = watchlist.mapNotNull { MoneyModel.quoteFor(it) }
    if (quotes.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            WrappedText("watchlist is empty — use manage to add up to 50 tickers.", DoradoTokens.TYPE_LIST.sp, colors.textSecondary)
        }
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DoradoTokens.EDGE.dp),
    ) {
        quotes.forEach { quote ->
            QuoteRow(quote = quote, tick = tick, onClick = { onOpenDetail(quote.symbol) })
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MarketsSection(tick: Int, onOpenDetail: (String) -> Unit) {
    val colors = LocalDoradoColors.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DoradoTokens.EDGE.dp),
    ) {
        MoneyModel.INDICES.forEach { index ->
            val price = displayPrice(index, tick)
            val change = formatChange(price, index.prevClose)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(40.dp)) {
                EdgeCropText(index.symbol, DoradoTokens.TYPE_LIST.dp, color = colors.textPrimary, modifier = Modifier.weight(1f))
                EdgeCropText(formatPrice(price), DoradoTokens.TYPE_LIST.dp, color = colors.textPrimary)
                Spacer(Modifier.width(10.dp))
                MoneyArrow(change.direction, 10.dp)
                Spacer(Modifier.width(4.dp))
                EdgeCropText(changeText(change), DoradoTokens.TYPE_CAPTION.dp, color = changeColor(change))
            }
        }
        Spacer(Modifier.height(8.dp))
        EdgeCropText("most active", DoradoTokens.TYPE_CAPTION.dp, color = colors.textInactive)
        MoneyModel.QUOTES.forEach { quote ->
            QuoteRow(quote = quote, tick = tick, onClick = { onOpenDetail(quote.symbol) })
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun QuoteRow(quote: Quote, tick: Int, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    val price = displayPrice(quote, tick)
    val change = formatChange(price, quote.prevClose)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(DoradoTokens.ROW_HEIGHT.dp)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.weight(1f)) {
            EdgeCropText(quote.symbol, DoradoTokens.TYPE_LIST.dp, color = colors.textPrimary)
            EdgeCropText(quote.name, DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
        }
        EdgeCropText(formatPrice(price), DoradoTokens.TYPE_LIST.dp, color = colors.textPrimary)
        Spacer(Modifier.width(10.dp))
        MoneyArrow(change.direction, 10.dp)
        Spacer(Modifier.width(4.dp))
        Box(Modifier.width(84.dp), contentAlignment = Alignment.CenterEnd) {
            EdgeCropText(changeText(change), DoradoTokens.TYPE_CAPTION.dp, color = changeColor(change))
        }
    }
}

@Composable
private fun QuoteDetailScreen(
    quote: Quote,
    tick: Int,
    onBack: () -> Unit,
    onNews: () -> Unit,
    onCurrency: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    var range by remember { mutableStateOf(ChartRange.ONE_DAY) }
    val series = remember(quote.symbol, range, tick) { MoneyModel.series(quote, range, tick) }
    val price = series.lastOrNull() ?: quote.price
    val change = formatChange(price, quote.prevClose)
    DetailScaffold(title = quote.symbol.lowercase(), onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    EdgeCropText(quote.name, DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EdgeCropText(formatPrice(price), DoradoTokens.TYPE_HEADER_CROPPED.dp, color = colors.textPrimary)
                        Spacer(Modifier.width(12.dp))
                        MoneyArrow(change.direction, 12.dp)
                        Spacer(Modifier.width(4.dp))
                        EdgeCropText(changeText(change), DoradoTokens.TYPE_LIST.dp, color = changeColor(change))
                    }
                }
                EdgeCropText("articles", DoradoTokens.TYPE_LIST.dp, color = colors.accent, modifier = Modifier.clickable(onClick = onNews))
                Spacer(Modifier.width(12.dp))
                EdgeCropText("converter", DoradoTokens.TYPE_LIST.dp, color = colors.accent, modifier = Modifier.clickable(onClick = onCurrency))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                ChartRange.entries.forEach { item ->
                    EdgeCropText(
                        text = item.label,
                        fontSize = DoradoTokens.TYPE_CAPTION.dp,
                        color = if (item == range) colors.accent else colors.textInactive,
                        modifier = Modifier
                            .clickable { range = item }
                            .padding(end = 12.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                EdgeCropText("simulated chart", DoradoTokens.TYPE_CAPTION.dp, color = colors.textInactive)
            }

            ChartPanel(series)

            StatGrid(quote, series)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ChartPanel(series: List<Double>) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .background(colors.tile),
    ) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            if (series.size < 2) return@Canvas
            var min = series.first()
            var max = series.first()
            series.forEach {
                if (it < min) min = it
                if (it > max) max = it
            }
            val span = (max - min).takeIf { it > 0.0 } ?: 1.0
            var previous: Offset? = null
            series.forEachIndexed { index, value ->
                val x = size.width * index / (series.size - 1).toFloat()
                val y = size.height * (1f - ((value - min) / span).toFloat()) * 0.9f + size.height * 0.05f
                val point = Offset(x, y)
                previous?.let { drawLine(colors.accent, it, point, 2f) }
                previous = point
            }
        }
        EdgeCropText(
            text = "local simulation — archived quote data",
            fontSize = DoradoTokens.TYPE_CAPTION.dp,
            color = colors.textInactive,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
        )
    }
}

@Composable
private fun StatGrid(quote: Quote, series: List<Double>) {
    val colors = LocalDoradoColors.current
    val high = series.maxOrNull() ?: quote.price
    val low = series.minOrNull() ?: quote.price
    val stats = listOf(
        "prev close" to formatPrice(quote.prevClose),
        "open" to formatPrice(quote.prevClose * 0.998),
        "day high" to formatPrice(high),
        "day low" to formatPrice(low),
        "volume" to MoneyModel.abbreviate(quote.volume.toDouble()),
        "year high" to formatPrice(high * 1.08),
        "year low" to formatPrice(low * 0.86),
        "p/e" to "%.1f".format(quote.pe),
        "eps" to formatPrice(quote.eps),
        "market cap" to MoneyModel.abbreviate(quote.marketCap.toDouble()),
    )
    Column {
        stats.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEach { (label, value) ->
                    Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
                        EdgeCropText(label, DoradoTokens.TYPE_CAPTION.dp, color = colors.textInactive)
                        EdgeCropText(value, DoradoTokens.TYPE_LIST_SECONDARY.dp, color = colors.textPrimary)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ConverterSection(
    fromCode: String,
    toCode: String,
    amount: String,
    onAmount: (String) -> Unit,
    onFrom: (String) -> Unit,
    onTo: (String) -> Unit,
) {
    val colors = LocalDoradoColors.current
    val from = CURRENCIES.firstOrNull { it.code == fromCode } ?: CURRENCIES.first()
    val to = CURRENCIES.firstOrNull { it.code == toCode } ?: CURRENCIES[1]
    val rate = rateBetween(from, to)
    val conversion = convertAmount(amount, rate)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DoradoTokens.EDGE.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EdgeCropText("amount", DoradoTokens.TYPE_CAPTION.dp, color = colors.textInactive)
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = amount,
                onValueChange = onAmount,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier
                    .width(160.dp)
                    .background(colors.tile)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            CurrencyCard(from, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Column {
                EdgeCropText("prev", DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary, modifier = Modifier.clickable {
                    onFrom(rotateCode(fromCode, -1))
                })
                EdgeCropText("next", DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary, modifier = Modifier.clickable {
                    onFrom(rotateCode(fromCode, 1))
                })
            }
            Spacer(Modifier.width(12.dp))
            CurrencyCard(to, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Column {
                EdgeCropText("prev", DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary, modifier = Modifier.clickable {
                    onTo(rotateCode(toCode, -1))
                })
                EdgeCropText("next", DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary, modifier = Modifier.clickable {
                    onTo(rotateCode(toCode, 1))
                })
            }
        }

        EdgeCropText(
            text = "1 ${from.code} = %.4f %s".format(rate, to.code),
            fontSize = DoradoTokens.TYPE_CAPTION.dp,
            color = colors.textSecondary,
        )

        when (conversion) {
            is Conversion.Ok -> EdgeCropText(
                text = "%.2f %s".format(conversion.value, to.code),
                fontSize = DoradoTokens.TYPE_HEADER_CROPPED.dp,
                color = colors.accent,
            )
            is Conversion.Invalid -> EdgeCropText(
                text = conversion.reason,
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.textSecondary,
            )
        }
        EdgeCropText(
            text = "simulated rate table — archived 2012 carry-trade model",
            fontSize = DoradoTokens.TYPE_CAPTION.dp,
            color = colors.textInactive,
        )
    }
}

@Composable
private fun CurrencyCard(currency: Currency, modifier: Modifier = Modifier) {
    val colors = LocalDoradoColors.current
    Column(
        modifier
            .background(colors.tile)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        EdgeCropText(currency.code, DoradoTokens.TYPE_NOW_META.dp, color = colors.accent)
        EdgeCropText(currency.name, DoradoTokens.TYPE_LIST.dp, color = colors.textPrimary)
        EdgeCropText(currency.symbol, DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
    }
}

@Composable
private fun ArticlesSection(onOpen: (String) -> Unit) {
    val colors = LocalDoradoColors.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DoradoTokens.EDGE.dp),
    ) {
        Newsroom.ARTICLES.forEach { article ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .clickable { onOpen(article.id) }
                    .padding(vertical = 6.dp),
            ) {
                EdgeCropText(article.title, DoradoTokens.TYPE_LIST.dp, color = colors.textPrimary)
                EdgeCropText("${article.section} · ${article.author} · ${article.date}", DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun ArticleScreen(article: Article, onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    var note by remember { mutableStateOf(false) }
    DetailScaffold(title = "article", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            EdgeCropText(article.title, DoradoTokens.TYPE_NOW_TITLE.dp, color = colors.textPrimary)
            EdgeCropText("${article.section} · ${article.author} · ${article.date}", DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
            article.body.forEach { paragraph ->
                WrappedText(paragraph, DoradoTokens.TYPE_LIST.sp, colors.textPrimary)
            }
            EdgeCropText(
                text = "open link",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.accent,
                modifier = Modifier.clickable { note = true },
            )
            if (note) {
                EdgeCropText(
                    text = "the original story link pointed at the closed msn money service.",
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = colors.textInactive,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ManagerScreen(
    watchlist: List<String>,
    onWatchlist: (List<String>) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val suggestions = remember(query, watchlist) {
        MoneyModel.searchTickers(query, MoneyModel.QUOTES).filterNot { watchlist.contains(it.symbol) }
    }
    DetailScaffold(title = "watchlist manager", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EdgeCropText(
                text = "tickers ${watchlist.size} of ${MoneyModel.MAX_QUOTES}",
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = colors.textInactive,
            )
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(colors.tile)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
            suggestions.forEach { quote ->
                EdgeCropText(
                    text = "add ${quote.symbol} — ${quote.name}",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.accent,
                    modifier = Modifier.clickable {
                        onWatchlist(MoneyModel.addQuote(watchlist, quote.symbol))
                        onQuery("")
                    }.padding(vertical = 2.dp),
                )
            }
            if (query.isNotBlank() && suggestions.isEmpty()) {
                EdgeCropText("no matches in the seeded ticker list.", DoradoTokens.TYPE_CAPTION.dp, color = colors.textInactive)
            }

            Spacer(Modifier.height(6.dp))
            watchlist.forEach { symbol ->
                val quote = MoneyModel.quoteFor(symbol)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(35.dp)) {
                    EdgeCropText("remove", DoradoTokens.TYPE_CAPTION.dp, color = colors.textInactive, modifier = Modifier.clickable {
                        onWatchlist(MoneyModel.removeQuote(watchlist, symbol))
                    })
                    Spacer(Modifier.width(16.dp))
                    EdgeCropText(
                        text = if (quote != null) "$symbol — ${quote.name}" else symbol,
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoScreen(onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "about msn money", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EdgeCropText("archived 2012", DoradoTokens.TYPE_NOW_META.dp, color = colors.accent)
            WrappedText("the original app streamed quotes, charts and dispatches from the msn money feeds. those feeds are gone, so dorado-hd keeps a small simulated market on device.", DoradoTokens.TYPE_LIST.sp, colors.textPrimary)
            WrappedText("watchlist, converter pair and amount persist between launches. charts and prices come from a deterministic local walk and are labelled simulated; they are not investment data.", DoradoTokens.TYPE_LIST.sp, colors.textPrimary)
        }
    }
}

@Composable
private fun MoneyArrow(direction: PriceDirection, size: Dp) {
    val colors = LocalDoradoColors.current
    val color = when (direction) {
        PriceDirection.UP -> colors.accent
        PriceDirection.DOWN -> colors.textInactive
        PriceDirection.FLAT -> colors.textInactive
    }
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        when (direction) {
            PriceDirection.UP -> {
                val path = Path().apply {
                    moveTo(w * 0.5f, 0f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(path, color)
            }
            PriceDirection.DOWN -> {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w * 0.5f, h)
                    close()
                }
                drawPath(path, color)
            }
            PriceDirection.FLAT -> drawLine(color, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), w * 0.2f)
        }
    }
}

@Composable
private fun changeColor(change: Change): Color = when (change.direction) {
    PriceDirection.UP -> LocalDoradoColors.current.accent
    PriceDirection.DOWN -> LocalDoradoColors.current.textInactive
    PriceDirection.FLAT -> LocalDoradoColors.current.textInactive
}

private fun displayPrice(quote: Quote, tick: Int): Double =
    MoneyModel.priceWalk(quote.symbol.hashCode() * 31, quote.prevClose, 8 + tick).last()

private fun formatPrice(value: Double): String = "%.2f".format(value)

private fun rotateCode(current: String, delta: Int): String {
    val index = CURRENCIES.indexOfFirst { it.code == current }.coerceAtLeast(0)
    val next = (index + delta + CURRENCIES.size) % CURRENCIES.size
    return CURRENCIES[next].code
}

@Composable
private fun WrappedText(text: String, size: TextUnit, color: Color) {
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
