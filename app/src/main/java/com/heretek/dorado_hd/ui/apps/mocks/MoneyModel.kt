package com.heretek.dorado_hd.ui.apps.mocks

import kotlin.math.abs
import kotlin.math.round

/**
 * MSN Money rules for the archived finance feed (docs/apps/msnmoney.md).
 *
 * The quote/chart/news services are gone, so every quote, chart point and
 * article here is local and deterministic. Pure Kotlin: no Android imports.
 */

enum class MoneySection(val id: String, val label: String) {
    WATCHLIST("watchlist", "watchlist"),
    MARKETS("markets", "markets"),
    CURRENCIES("currencies", "currencies"),
    ARTICLES("articles", "articles"),
    ;

    companion object {
        fun fromId(id: String?): MoneySection = entries.firstOrNull { it.id == id } ?: WATCHLIST
    }
}

enum class PriceDirection { UP, DOWN, FLAT }

data class Change(val delta: Double, val percent: Double, val direction: PriceDirection)

/** `QuoteData.Parse` presentation math: direction, absolute delta, percent. */
fun formatChange(price: Double, prev: Double): Change {
    val delta = price - prev
    val direction = when {
        abs(delta) < 0.0000001 -> PriceDirection.FLAT
        delta > 0 -> PriceDirection.UP
        else -> PriceDirection.DOWN
    }
    val percent = if (abs(prev) < 0.0000001) 0.0 else delta / prev * 100.0
    return Change(delta, percent, direction)
}

/** Signed display string, e.g. `+1.39 (+3.34%)`. */
fun changeText(change: Change, decimals: Int = 2): String {
    val sign = when (change.direction) {
        PriceDirection.UP -> "+"
        PriceDirection.DOWN -> "-"
        PriceDirection.FLAT -> ""
    }
    val fmt = "%.${decimals}f"
    return "$sign${fmt.format(abs(change.delta))} ($sign${fmt.format(abs(change.percent))}%)"
}

data class Quote(
    val symbol: String,
    val name: String,
    val price: Double,
    val prevClose: Double,
    val volume: Long,
    val marketCap: Long,
    val pe: Double,
    val eps: Double,
)

enum class ChartRange(val id: String, val label: String, val points: Int) {
    ONE_DAY("1d", "1d", 24),
    ONE_WEEK("1w", "1w", 28),
    ONE_MONTH("1m", "1m", 30),
    ONE_YEAR("1y", "1y", 52),
    MAX("max", "max", 60),
    ;

    companion object {
        fun fromId(id: String?): ChartRange = entries.firstOrNull { it.id == id } ?: ONE_DAY
    }
}

object MoneyModel {

    const val MAX_QUOTES = 50

    private fun noise(seed: Int, tick: Int): Int {
        var h = seed.toLong() * 1_664_525L + tick * 1_013_904_223L
        h = (h xor (h ushr 15)) * 2_246_822_519L
        h = h xor (h ushr 13)
        return (h and 0x7FFF_FFFFL).toInt()
    }

    /** Deterministic quote walk: ±0.80% per tick, anchored at start. */
    fun priceWalk(seed: Int, start: Double, ticks: Int): List<Double> {
        if (ticks <= 0) return emptyList()
        val out = ArrayList<Double>(ticks)
        var price = start
        for (t in 0 until ticks) {
            val step = (noise(seed, t) % 161 - 80) / 10_000.0
            price *= 1.0 + step
            out.add(price)
        }
        return out
    }

    fun priceAt(seed: Int, start: Double, tick: Int): Double =
        priceWalk(seed, start, tick + 1).last()

    fun series(quote: Quote, range: ChartRange, tick: Int = 0): List<Double> =
        priceWalk(quote.symbol.hashCode() * 31 + range.ordinal, quote.prevClose, range.points + tick)

    /** Ticker search matches on symbol or name with a one-character minimum. */
    fun searchTickers(query: String, universe: List<Quote>, limit: Int = 6): List<Quote> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return universe
            .filter { it.symbol.lowercase().contains(q) || it.name.lowercase().contains(q) }
            .take(limit)
    }

    fun addQuote(list: List<String>, symbol: String, cap: Int = MAX_QUOTES): List<String> {
        if (list.size >= cap || list.contains(symbol)) return list
        return list + symbol
    }

    fun removeQuote(list: List<String>, symbol: String): List<String> =
        list.filterNot { it == symbol }

    fun quoteFor(symbol: String): Quote? = QUOTES.firstOrNull { it.symbol == symbol }

    /** `LargeIntToReadableString`: abbreviated magnitudes. */
    fun abbreviate(value: Double): String {
        val magnitude = abs(value)
        val divisor: Double
        val suffix: String
        when {
            magnitude >= 1_000_000_000_000.0 -> { divisor = 1_000_000_000_000.0; suffix = "T" }
            magnitude >= 1_000_000_000.0 -> { divisor = 1_000_000_000.0; suffix = "B" }
            magnitude >= 1_000_000.0 -> { divisor = 1_000_000.0; suffix = "M" }
            magnitude >= 1_000.0 -> { divisor = 1_000.0; suffix = "K" }
            else -> return "%.2f".format(value)
        }
        return "%.2f%s".format(value / divisor, suffix)
    }

    val QUOTES: List<Quote> = listOf(
        Quote("NPTX", "northport technologies", 42.18, 41.60, 8_412_300, 58_400_000_000, 18.4, 2.29),
        Quote("CRVW", "cedar view software", 18.42, 18.90, 3_302_150, 9_120_000_000, 24.1, 0.76),
        Quote("MLBK", "millbrook freight", 67.05, 65.44, 1_248_900, 21_800_000_000, 14.7, 4.56),
        Quote("HRBR", "harbor energy", 31.77, 32.15, 6_004_512, 47_200_000_000, 9.8, 3.24),
        Quote("KTSP", "kestrel aerospace", 88.30, 86.02, 2_119_044, 33_500_000_000, 21.3, 4.14),
        Quote("FVWN", "fairhaven foods", 24.61, 24.61, 998_221, 6_800_000_000, 12.9, 1.91),
        Quote("LNBK", "lantern bancorp", 53.14, 54.03, 1_544_890, 18_900_000_000, 10.2, 5.21),
        Quote("RVRL", "riverline rail", 109.87, 108.10, 712_406, 27_100_000_000, 16.5, 6.66),
        Quote("OMLN", "old mill mining", 15.09, 15.51, 4_448_002, 4_200_000_000, 7.4, 2.04),
        Quote("GRDN", "gardener supplies", 39.75, 38.88, 1_002_377, 11_600_000_000, 19.8, 2.01),
    )

    val DEFAULT_WATCHLIST: List<String> = listOf("NPTX", "MLBK", "KTSP", "LNBK", "GRDN")

    val INDICES: List<Quote> = listOf(
        Quote("NORTH 100", "north composite", 13_583.93, 13_556.34, 0, 0, 0.0, 0.0),
        Quote("RIVER 30", "river industrials", 4_201.77, 4_219.03, 0, 0, 0.0, 0.0),
        Quote("BRIGHT TECH", "bright technology", 2_894.12, 2_861.55, 0, 0, 0.0, 0.0),
        Quote("GLOBAL 500", "global five hundred", 1_455.60, 1_449.81, 0, 0, 0.0, 0.0),
    )

    val MARKET_QUOTES: List<Quote> = INDICES + listOf(
        QUOTES[0], QUOTES[2], QUOTES[4], QUOTES[7],
    )
}

data class Currency(val code: String, val name: String, val symbol: String, val perUsd: Double)

val CURRENCIES: List<Currency> = listOf(
    Currency("USD", "us dollar", "$", 1.0),
    Currency("EUR", "euro", "EUR", 0.92),
    Currency("GBP", "pound sterling", "GBP", 0.79),
    Currency("JPY", "japanese yen", "JPY", 149.5),
    Currency("CAD", "canadian dollar", "CAD", 1.36),
    Currency("AUD", "australian dollar", "AUD", 1.52),
    Currency("CHF", "swiss franc", "CHF", 0.88),
    Currency("CNY", "chinese yuan", "CNY", 7.24),
)

fun rateBetween(from: Currency, to: Currency): Double = to.perUsd / from.perUsd

sealed interface Conversion {
    data class Ok(val value: Double) : Conversion
    data class Invalid(val reason: String) : Conversion
}

/**
 * Currency validation mirrors the device's rules: non-numeric, too-long and
 * negative amounts are rejected; results round to the display precision.
 */
fun convertAmount(raw: String, rate: Double, decimals: Int = 2): Conversion {
    val text = raw.trim().replace(",", "")
    if (text.isEmpty()) return Conversion.Invalid("enter an amount")
    if (text.length > 12) return Conversion.Invalid("amount too large")
    val value = text.toDoubleOrNull() ?: return Conversion.Invalid("numbers only")
    if (!value.isFinite() || value < 0.0) return Conversion.Invalid("enter a positive amount")
    val places = decimals.coerceIn(0, 4)
    var scale = 1.0
    repeat(places) { scale *= 10.0 }
    val result = round(value * rate * scale) / scale
    if (!result.isFinite()) return Conversion.Invalid("amount too large")
    return Conversion.Ok(result)
}

data class Article(
    val id: String,
    val title: String,
    val author: String,
    val date: String,
    val section: String,
    val body: List<String>,
)

/** Re-authored copy only; the original RSS dispatches are long gone. */
object Newsroom {
    val ARTICLES: List<Article> = listOf(
        Article(
            id = "freight",
            title = "freight data lifts the northport index",
            author = "m. alvarez",
            date = "oct 12, 2012",
            section = "markets",
            body = listOf(
                "the northport index closed higher for a third session after riverline rail reported stronger carload volumes across the valley corridor.",
                "analysts said the reading was less about consumer demand than about inventory restocking, and cautioned that one week does not make a trend.",
                "in the wider market, energy names lagged while industrial suppliers held most of their gains into the close.",
            ),
        ),
        Article(
            id = "housing",
            title = "millbrook builder sees orders steady",
            author = "r. okafor",
            date = "oct 11, 2012",
            section = "companies",
            body = listOf(
                "millbrook homes said new orders held roughly flat through the quarter, with cancellations easing in the coastal counties.",
                "management left its full-year outlook unchanged, noting that land costs and lending conditions remain the two swing factors.",
                "shares finished a fraction lower as investors looked past the headline to the margin guidance.",
            ),
        ),
        Article(
            id = "currencies",
            title = "yen eases as traders weigh rate outlook",
            author = "s. whitfield",
            date = "oct 10, 2012",
            section = "currencies",
            body = listOf(
                "the yen slipped against most major currencies as traders squared positions ahead of a central-bank statement later in the week.",
                "the euro was little changed, while the dollar firmed against a basket of trading partners.",
                "currency desks described the moves as orderly and range-bound.",
            ),
        ),
        Article(
            id = "cloud",
            title = "cedar view bets on subscription tools",
            author = "j. fenn",
            date = "oct 9, 2012",
            section = "technology",
            body = listOf(
                "cedar view software outlined a shift toward subscription pricing for its design suite, a model it says smooths revenue across the year.",
                "the company acknowledged that the transition would weigh on near-term bookings before recurring revenue catches up.",
                "several peers have made similar moves, and investors are watching retention figures closely.",
            ),
        ),
        Article(
            id = "energy",
            title = "harbor energy delays offshore survey",
            author = "p. nakamura",
            date = "oct 8, 2012",
            section = "energy",
            body = listOf(
                "harbor energy pushed its winter offshore survey back by a month, citing vessel availability rather than any change in the field plan.",
                "the company reaffirmed its production target for the year and said the delay would not affect delivery schedules.",
            ),
        ),
        Article(
            id = "outlook",
            title = "global 500 drifts ahead of earnings week",
            author = "the desk",
            date = "oct 8, 2012",
            section = "markets",
            body = listOf(
                "the global 500 ended narrowly mixed as investors waited for the first wave of quarterly reports.",
                "defensive sectors outperformed, while transport and materials traded in a tight band.",
                "trading volumes were below the month's average, a pattern desks attributed to the holiday weekend.",
            ),
        ),
    )

    fun byId(id: String): Article? = ARTICLES.firstOrNull { it.id == id }
}

data class MoneyState(
    val section: MoneySection,
    val watchlist: List<String>,
    val fromCode: String,
    val toCode: String,
    val amount: String,
)

object MoneyStateCodec {

    fun encode(state: MoneyState): String = listOf(
        "v1",
        state.section.id,
        state.fromCode,
        state.toCode,
        state.amount.replace('|', ' ').replace('\n', ' '),
        state.watchlist.joinToString(","),
    ).joinToString("|")

    fun decode(raw: String?): MoneyState? {
        if (raw.isNullOrBlank()) return null
        val f = raw.split('|')
        if (f.size < 6 || f[0] != "v1") return null
        val watchlist = f[5].split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return MoneyState(
            section = MoneySection.fromId(f[1]),
            fromCode = f[2].ifEmpty { "USD" },
            toCode = f[3].ifEmpty { "EUR" },
            amount = f[4].ifEmpty { "100" },
            watchlist = watchlist,
        )
    }
}
