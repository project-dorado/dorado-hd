package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.mocks.Conversion
import com.heretek.dorado_hd.ui.apps.mocks.CURRENCIES
import com.heretek.dorado_hd.ui.apps.mocks.ChartRange
import com.heretek.dorado_hd.ui.apps.mocks.MoneyModel
import com.heretek.dorado_hd.ui.apps.mocks.MoneySection
import com.heretek.dorado_hd.ui.apps.mocks.MoneyState
import com.heretek.dorado_hd.ui.apps.mocks.MoneyStateCodec
import com.heretek.dorado_hd.ui.apps.mocks.PriceDirection
import com.heretek.dorado_hd.ui.apps.mocks.changeText
import com.heretek.dorado_hd.ui.apps.mocks.convertAmount
import com.heretek.dorado_hd.ui.apps.mocks.formatChange
import com.heretek.dorado_hd.ui.apps.mocks.rateBetween
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 7 MSN Money rules: change formatting, ticker search and the 50-quote
 * cap, currency conversion validation/rounding and the deterministic walk.
 */
class MsnMoneyModelTest {

    @Test
    fun `change carries direction delta and percent`() {
        val up = formatChange(110.0, 100.0)
        assertEquals(PriceDirection.UP, up.direction)
        assertEquals(10.0, up.delta, 0.0001)
        assertEquals(10.0, up.percent, 0.0001)
        assertEquals("+10.00 (+10.00%)", changeText(up))

        val down = formatChange(90.0, 100.0)
        assertEquals(PriceDirection.DOWN, down.direction)
        assertEquals("-10.00 (-10.00%)", changeText(down))

        val flat = formatChange(100.0, 100.0)
        assertEquals(PriceDirection.FLAT, flat.direction)
    }

    @Test
    fun `ticker search needs at least one character`() {
        assertTrue(MoneyModel.searchTickers("", MoneyModel.QUOTES).isEmpty())
        assertTrue(MoneyModel.searchTickers("   ", MoneyModel.QUOTES).isEmpty())
        val hit = MoneyModel.searchTickers("n", MoneyModel.QUOTES)
        assertTrue(hit.isNotEmpty())
        assertTrue(hit.any { it.symbol == "NPTX" })
        assertTrue(MoneyModel.searchTickers("zzzz", MoneyModel.QUOTES).isEmpty())
    }

    @Test
    fun `watchlist is capped at fifty unique tickers`() {
        var list = emptyList<String>()
        repeat(60) { index -> list = MoneyModel.addQuote(list, "T%03d".format(index)) }
        assertEquals(MoneyModel.MAX_QUOTES, list.size)
        assertEquals("T000", list.first())
        assertEquals("T049", list.last())

        val duplicate = MoneyModel.addQuote(list, "T000")
        assertEquals(list, duplicate)

        val removed = MoneyModel.removeQuote(list, "T010")
        assertEquals(49, removed.size)
        assertTrue(removed.none { it == "T010" })
    }

    private fun assertOk(expected: Double, result: Conversion) {
        assertTrue("expected Ok($expected), got $result", result is Conversion.Ok)
        assertEquals(expected, (result as Conversion.Ok).value, 0.0001)
    }

    @Test
    fun `converter rounds and validates`() {
        assertOk(92.0, convertAmount("100", 0.92, 2))
        assertOk(3.33, convertAmount("10", 1.0 / 3.0, 2))
        assertOk(8.5, convertAmount("10", 0.85, 2))
        assertOk(0.0, convertAmount("0", 0.92, 2))

        assertTrue(convertAmount("", 1.0) is Conversion.Invalid)
        assertTrue(convertAmount("abc", 1.0) is Conversion.Invalid)
        assertTrue(convertAmount("-5", 1.0) is Conversion.Invalid)
        assertTrue(convertAmount("1234567890123", 1.0) is Conversion.Invalid)
    }

    @Test
    fun `rate table converts through usd`() {
        val usd = CURRENCIES.first { it.code == "USD" }
        val eur = CURRENCIES.first { it.code == "EUR" }
        assertEquals(0.92, rateBetween(usd, eur), 0.0001)
        assertOk(108.7, convertAmount("100", rateBetween(eur, usd), 2))
    }

    @Test
    fun `quote walk is deterministic`() {
        val a = MoneyModel.priceWalk(5, 100.0, 12)
        val b = MoneyModel.priceWalk(5, 100.0, 12)
        assertEquals(a, b)
        assertEquals(12, a.size)
        assertNotEquals(a, MoneyModel.priceWalk(6, 100.0, 12))
        assertEquals(a.last(), MoneyModel.priceAt(5, 100.0, 11), 0.0001)
        assertTrue(MoneyModel.priceWalk(5, 100.0, 0).isEmpty())
    }

    @Test
    fun `chart ranges carry distinct point counts`() {
        val quote = MoneyModel.QUOTES.first()
        ChartRange.entries.forEach { range ->
            val series = MoneyModel.series(quote, range)
            assertEquals(range.points, series.size)
        }
        assertNotEquals(
            MoneyModel.series(quote, ChartRange.ONE_DAY),
            MoneyModel.series(quote, ChartRange.ONE_YEAR),
        )
    }

    @Test
    fun `large numbers abbreviate`() {
        assertEquals("1.50B", MoneyModel.abbreviate(1_500_000_000.0))
        assertEquals("2.50M", MoneyModel.abbreviate(2_500_000.0))
        assertEquals("1.25T", MoneyModel.abbreviate(1_250_000_000_000.0))
        assertEquals("4.20K", MoneyModel.abbreviate(4_200.0))
        assertEquals("950.00", MoneyModel.abbreviate(950.0))
    }

    @Test
    fun `money state codec round trips`() {
        val state = MoneyState(
            section = MoneySection.CURRENCIES,
            watchlist = listOf("NPTX", "MLBK"),
            fromCode = "USD",
            toCode = "JPY",
            amount = "250.50",
        )
        assertEquals(state, MoneyStateCodec.decode(MoneyStateCodec.encode(state)))
        assertNull(MoneyStateCodec.decode(null))
        assertNull(MoneyStateCodec.decode("garbage"))

        val fallback = MoneyStateCodec.decode("v1|nonsense|USD|EUR|100|NPTX")!!
        assertEquals(MoneySection.WATCHLIST, fallback.section)
    }
}
