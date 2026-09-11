package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.mocks.City
import com.heretek.dorado_hd.ui.apps.mocks.CurrentReading
import com.heretek.dorado_hd.ui.apps.mocks.DayForecast
import com.heretek.dorado_hd.ui.apps.mocks.Sky
import com.heretek.dorado_hd.ui.apps.mocks.TempUnit
import com.heretek.dorado_hd.ui.apps.mocks.WeatherModel
import com.heretek.dorado_hd.ui.apps.mocks.WeatherState
import com.heretek.dorado_hd.ui.apps.mocks.WeatherStateCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 7 weather rules: the 3-hour current expiry, local-day pruning, the
 * forecast fallback, F/C truncation, deterministic forecasts and the codec.
 */
class WeatherModelTest {

    private val day = 86_400_000L
    private val hour = 3_600_000L

    private fun city(
        tz: Int = 0,
        observedAt: Long? = null,
        tempF: Int? = 58,
        sky: Sky? = Sky.RAIN,
        daily: List<DayForecast> = emptyList(),
    ) = City(
        id = "test",
        name = "testville",
        region = "test coast",
        tzOffsetMinutes = tz,
        seed = 7,
        baseHighF = 60,
        baseLowF = 45,
        current = observedAt?.let { CurrentReading(tempF, sky, it) },
        daily = daily,
        refreshedAtMillis = observedAt ?: 0L,
    )

    @Test
    fun `current reading expires after three hours`() {
        val now = 100L * day
        val fresh = WeatherModel.ageOut(city(observedAt = now - 3 * hour), now)
        assertEquals(58, fresh.current?.tempF)

        val stale = WeatherModel.ageOut(city(observedAt = now - 3 * hour - 1), now)
        assertNull("past the TTL the reading must drop", stale.current)
    }

    @Test
    fun `daily rows prune at the local day boundary`() {
        val now = 10L * day + 60_000L
        val forecast = listOf(
            DayForecast(dayStartMillis = 9L * day, sky = Sky.SUN, highF = 62, lowF = 48),
            DayForecast(dayStartMillis = 10L * day, sky = Sky.CLOUD, highF = 60, lowF = 47),
            DayForecast(dayStartMillis = 11L * day, sky = Sky.RAIN, highF = 58, lowF = 44),
        )
        val aged = WeatherModel.ageOut(city(observedAt = null, daily = forecast), now)
        assertEquals(2, aged.daily.size)
        assertEquals(10L * day, aged.daily.first().dayStartMillis)
    }

    @Test
    fun `local day honours the city time zone`() {
        val now = 10L * day + 23 * hour
        val forecast = listOf(
            DayForecast(dayStartMillis = 10L * day, sky = Sky.SUN, highF = 60, lowF = 45),
            DayForecast(dayStartMillis = 11L * day, sky = Sky.CLOUD, highF = 59, lowF = 44),
        )
        val aged = WeatherModel.ageOut(city(tz = 120, daily = forecast), now)
        assertEquals("UTC+2 is already the next local day", 1, aged.daily.size)
        assertEquals(11L * day, aged.daily.first().dayStartMillis)
    }

    @Test
    fun `missing current falls back to the first forecast row`() {
        val forecast = listOf(
            DayForecast(dayStartMillis = 10L * day, sky = Sky.CLOUD, highF = 66, lowF = 50),
            DayForecast(dayStartMillis = 11L * day, sky = Sky.SUN, highF = 70, lowF = 53),
        )
        val reading = WeatherModel.effectiveReading(city(observedAt = null, daily = forecast))
        assertNull("temperature is unknown without a reading", reading.tempF)
        assertEquals(Sky.CLOUD, reading.sky)
        assertEquals(66, reading.highF)
        assertEquals(50, reading.lowF)
        assertTrue(reading.fromForecast)
    }

    @Test
    fun `aged current with no reading still uses the forecast condition`() {
        val now = 100L * day
        val forecast = listOf(DayForecast(now, Sky.SNOW, 30, 20))
        val stale = WeatherModel.ageOut(city(observedAt = now - 4 * hour, daily = forecast), now)
        val reading = WeatherModel.effectiveReading(stale)
        assertNull(reading.tempF)
        assertEquals(Sky.SNOW, reading.sky)
    }

    @Test
    fun `fahrenheit converts to celsius with truncation`() {
        assertEquals("62°", WeatherModel.formatTemp(62, TempUnit.FAHRENHEIT))
        assertEquals("16°", WeatherModel.formatTemp(62, TempUnit.CELSIUS))
        assertEquals("0°", WeatherModel.formatTemp(32, TempUnit.CELSIUS))
        assertEquals("100°", WeatherModel.formatTemp(212, TempUnit.CELSIUS))
        assertEquals("—", WeatherModel.formatTemp(null, TempUnit.CELSIUS))
    }

    @Test
    fun `last seen degrades by minutes hours and days`() {
        val now = 1_000_000_000_000L
        assertEquals("just now", WeatherModel.formatAgo(now, now - 30_000L))
        assertEquals("1 minute ago", WeatherModel.formatAgo(now, now - 90_000L))
        assertEquals("5 minutes ago", WeatherModel.formatAgo(now, now - 5 * 60_000L))
        assertEquals("1 hour ago", WeatherModel.formatAgo(now, now - hour))
        assertEquals("2 days ago", WeatherModel.formatAgo(now, now - 50 * hour))
        assertNull("empty stamp hides the line", WeatherModel.formatAgo(now, 0L))
    }

    @Test
    fun `forecast is deterministic and seven days long`() {
        val seedCity = city()
        val first = WeatherModel.forecast(seedCity, 100L)
        val second = WeatherModel.forecast(seedCity, 100L)
        assertEquals(WeatherModel.FORECAST_DAYS, first.size)
        assertEquals(first, second)
        assertNotEquals(first, WeatherModel.forecast(seedCity, 101L))
        first.forEachIndexed { index, row ->
            assertEquals(
                "row $index must land on its local day",
                100L + index,
                WeatherModel.localDay(row.dayStartMillis, seedCity.tzOffsetMinutes),
            )
        }
    }

    @Test
    fun `simulated refresh re-stamps the archived snapshot`() {
        val now = 200L * day
        val refreshed = WeatherModel.simulateRefresh(city(), now)
        assertEquals(now, refreshed.refreshedAtMillis)
        assertEquals(now, refreshed.current?.observedAtMillis)
        assertEquals(WeatherModel.FORECAST_DAYS, refreshed.daily.size)
    }

    @Test
    fun `weather state codec round trips`() {
        val base = city(tz = -480)
        val seeded = WeatherModel.snapshot(base, WeatherModel.ARCHIVE_DAY)
        val state = WeatherState(
            unit = TempUnit.CELSIUS,
            selected = 1,
            cities = listOf(seeded, WeatherModel.snapshot(city(tz = 60), 15_630L)),
        )
        val decoded = WeatherStateCodec.decode(WeatherStateCodec.encode(state))
        assertEquals(state, decoded)
        assertNull(WeatherStateCodec.decode(null))
        assertNull(WeatherStateCodec.decode("not a state blob"))
        assertTrue(WeatherStateCodec.decode("v1|f|0")!!.cities.isEmpty())
    }
}
