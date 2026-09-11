package com.heretek.dorado_hd.ui.apps.mocks

/**
 * Weather rules for the dead zune.net weather service (docs/apps/weather.md).
 *
 * The service and its forecast feed are gone; this build keeps a frozen
 * snapshot and re-derives refreshes locally and deterministically. Every
 * function here is pure Kotlin so the retention rule, the temperature
 * conversion and the state codec are unit-testable without Android.
 */

/** `WeatherOptions.TempSystem` — stored in the snapshot, flips display only. */
enum class TempUnit(val id: String, val label: String) {
    FAHRENHEIT("f", "°f"),
    CELSIUS("c", "°c"),
    ;

    companion object {
        fun fromId(id: String?): TempUnit = entries.firstOrNull { it.id == id } ?: FAHRENHEIT
    }
}

/** Re-authored condition set; the device shipped bitmap glyph atlases. */
enum class Sky {
    SUN,
    PARTLY,
    CLOUD,
    RAIN,
    STORM,
    SNOW,
    FOG,
    WIND,
}

data class DayForecast(
    val dayStartMillis: Long,
    val sky: Sky,
    val highF: Int,
    val lowF: Int,
)

data class CurrentReading(
    val tempF: Int?,
    val sky: Sky?,
    val observedAtMillis: Long,
)

data class City(
    val id: String,
    val name: String,
    val region: String,
    val tzOffsetMinutes: Int,
    val seed: Int,
    val baseHighF: Int,
    val baseLowF: Int,
    val current: CurrentReading? = null,
    val daily: List<DayForecast> = emptyList(),
    val refreshedAtMillis: Long = 0L,
)

/** What the city page can actually draw after the retention rule runs. */
data class EffectiveReading(
    val tempF: Int?,
    val sky: Sky?,
    val highF: Int?,
    val lowF: Int?,
    val fromForecast: Boolean,
)

data class WeatherState(
    val unit: TempUnit,
    val selected: Int,
    val cities: List<City>,
)

object WeatherModel {

    const val FORECAST_DAYS = 7
    const val CURRENT_TTL_MS = 3L * 60L * 60L * 1000L

    private const val DAY_MS = 86_400_000L
    private const val MINUTE_MS = 60_000L

    /** Local day index (days since epoch) for a city's wall clock. */
    fun localDay(epochMillis: Long, tzOffsetMinutes: Int): Long =
        Math.floorDiv(epochMillis + tzOffsetMinutes * MINUTE_MS, DAY_MS)

    /**
     * `CityData.AgeData`: current readings expire after three hours and daily
     * rows are pruned once their local day has passed.
     */
    fun ageOut(city: City, nowMillis: Long): City {
        val today = localDay(nowMillis, city.tzOffsetMinutes)
        val daily = city.daily.filter { localDay(it.dayStartMillis, city.tzOffsetMinutes) >= today }
        val current = city.current?.takeIf { nowMillis - it.observedAtMillis <= CURRENT_TTL_MS }
        return city.copy(current = current, daily = daily)
    }

    /**
     * A missing current reading degrades to the first forecast row's
     * condition / high / low; the temperature is unknown (no reading).
     */
    fun effectiveReading(city: City): EffectiveReading {
        val current = city.current
        val first = city.daily.firstOrNull()
        if (current?.tempF != null) {
            return EffectiveReading(
                tempF = current.tempF,
                sky = current.sky ?: first?.sky,
                highF = first?.highF,
                lowF = first?.lowF,
                fromForecast = false,
            )
        }
        return EffectiveReading(
            tempF = null,
            sky = current?.sky ?: first?.sky,
            highF = first?.highF,
            lowF = first?.lowF,
            fromForecast = true,
        )
    }

    /** Fahrenheit is the stored unit; display conversion truncates. */
    fun formatTemp(fahrenheit: Int?, unit: TempUnit): String {
        if (fahrenheit == null) return "—"
        val value = when (unit) {
            TempUnit.FAHRENHEIT -> fahrenheit
            TempUnit.CELSIUS -> (fahrenheit - 32) * 5 / 9
        }
        return "$value°"
    }

    /** Last-refresh stamp: days / hours / minutes ago, hidden when empty. */
    fun formatAgo(nowMillis: Long, thenMillis: Long): String? {
        if (thenMillis <= 0L) return null
        val delta = nowMillis - thenMillis
        if (delta < MINUTE_MS) return "just now"
        val minutes = delta / MINUTE_MS
        if (minutes < 60L) return if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
        val hours = minutes / 60L
        if (hours < 24L) return if (hours == 1L) "1 hour ago" else "$hours hours ago"
        val days = hours / 24L
        return if (days == 1L) "1 day ago" else "$days days ago"
    }

    /** Deterministic pseudo-random source; stable across platforms. */
    private fun noise(seed: Int, day: Long, salt: Int): Int {
        var h = seed.toLong() * 1_103_515_245L + day * 12_345L + salt * 7_919L
        h = (h xor (h ushr 13)) * 1_274_126_177L
        h = h xor (h ushr 16)
        return (h and 0x7FFF_FFFFL).toInt()
    }

    /**
     * Seven-day simulated forecast from a city seed. Same city and day always
     * produce the same rows, so the archive is stable between launches.
     */
    fun forecast(city: City, fromDay: Long, days: Int = FORECAST_DAYS): List<DayForecast> {
        if (days <= 0) return emptyList()
        return (0 until days).map { offset ->
            val day = fromDay + offset
            val n = noise(city.seed, day, 0)
            val m = noise(city.seed, day, 1)
            DayForecast(
                dayStartMillis = day * DAY_MS - city.tzOffsetMinutes * MINUTE_MS,
                sky = Sky.entries[(n / 7) % Sky.entries.size],
                highF = city.baseHighF + n % 7 - 3,
                lowF = city.baseLowF + m % 5 - 2,
            )
        }
    }

    /** Frozen snapshot builder used for the seed cities and the city search. */
    fun snapshot(city: City, day: Long): City {
        val daily = forecast(city, day)
        val first = daily.firstOrNull()
        return city.copy(
            current = first?.let {
                CurrentReading(tempF = it.highF - 4, sky = it.sky, observedAtMillis = day * DAY_MS)
            },
            daily = daily,
            refreshedAtMillis = day * DAY_MS,
        )
    }

    /** The device's refresh button, reimagined: re-stamp with a local forecast. */
    fun simulateRefresh(city: City, nowMillis: Long): City {
        val day = localDay(nowMillis, city.tzOffsetMinutes)
        val daily = forecast(city, day)
        val first = daily.firstOrNull()
        return city.copy(
            current = first?.let {
                CurrentReading(tempF = it.highF - 2, sky = it.sky, observedAtMillis = nowMillis)
            },
            daily = daily,
            refreshedAtMillis = nowMillis,
        )
    }

    /** 2012 archive epoch (2012-10-18): the service's last usable snapshot. */
    const val ARCHIVE_DAY = 15_632L

    private fun base(
        id: String,
        name: String,
        region: String,
        tzOffsetMinutes: Int,
        seed: Int,
        baseHighF: Int,
        baseLowF: Int,
    ) = City(id, name, region, tzOffsetMinutes, seed, baseHighF, baseLowF)

    private val CATALOG: List<City> = listOf(
        base("harborview", "harborview", "cascade coast", -480, 11, 61, 49),
        base("northport", "northport", "marina county", -300, 23, 68, 54),
        base("cedar-hollow", "cedar hollow", "ridge country", -360, 37, 55, 40),
        base("millbrook", "millbrook", "river valley", -240, 53, 72, 58),
        base("fairhaven", "fairhaven", "outer banks", -420, 71, 64, 51),
        base("lantern-bay", "lantern bay", "gulf shore", -360, 89, 78, 66),
        base("kestrel-park", "kestrel park", "high plains", -420, 97, 52, 35),
    )

    /** Cities offered by the settings search, all fictional. */
    fun catalog(): List<City> = CATALOG

    /** Three to five seeded pages, matching the spec's city-manager rule. */
    fun seedCities(): List<City> = CATALOG.take(4).map { snapshot(it, ARCHIVE_DAY) }

    fun cityById(id: String): City? = CATALOG.firstOrNull { it.id == id }
}

/**
 * Compact line format for `graph.appState("weather")`. Every field is
 * pipe-separated; daily rows are semicolon-separated `day:sky:hi:lo` tuples.
 */
object WeatherStateCodec {

    fun encode(state: WeatherState): String = buildString {
        append("v1|").append(state.unit.id).append('|').append(state.selected).append('\n')
        state.cities.forEach { city ->
            append("city|")
            append(city.id).append('|')
            append(city.name).append('|')
            append(city.region).append('|')
            append(city.tzOffsetMinutes).append('|')
            append(city.seed).append('|')
            append(city.baseHighF).append('|')
            append(city.baseLowF).append('|')
            append(city.refreshedAtMillis).append('|')
            append(city.current?.tempF?.toString() ?: "").append('|')
            append(city.current?.sky?.name ?: "").append('|')
            append(city.current?.observedAtMillis ?: 0L).append('|')
            append(
                city.daily.joinToString(";") {
                    "${it.dayStartMillis}:${it.sky.name}:${it.highF}:${it.lowF}"
                },
            )
            append('\n')
        }
    }

    fun decode(raw: String?): WeatherState? {
        if (raw.isNullOrBlank()) return null
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val header = lines.firstOrNull()?.split('|') ?: return null
        if (header.firstOrNull() != "v1") return null
        val unit = TempUnit.fromId(header.getOrNull(1))
        val selected = header.getOrNull(2)?.toIntOrNull() ?: 0
        val cities = lines.drop(1).mapNotNull { decodeCity(it) }
        return WeatherState(unit, selected.coerceAtLeast(0), cities)
    }

    private fun decodeCity(line: String): City? {
        val f = line.split('|')
        if (f.size < 13 || f[0] != "city") return null
        val id = f[1]
        val name = f[2]
        val region = f[3]
        val tz = f[4].toIntOrNull() ?: return null
        val seed = f[5].toIntOrNull() ?: return null
        val high = f[6].toIntOrNull() ?: return null
        val low = f[7].toIntOrNull() ?: return null
        val refreshed = f[8].toLongOrNull() ?: 0L
        val temp = f[9].toIntOrNull()
        val sky = f[10].takeIf { it.isNotEmpty() }?.let { name ->
            Sky.entries.firstOrNull { it.name == name }
        }
        val observed = f[11].toLongOrNull() ?: 0L
        val daily = f[12].split(';').mapNotNull { row ->
            val p = row.split(':')
            if (p.size < 4) return@mapNotNull null
            val day = p[0].toLongOrNull() ?: return@mapNotNull null
            val s = Sky.entries.firstOrNull { it.name == p[1] } ?: return@mapNotNull null
            DayForecast(day, s, p[2].toIntOrNull() ?: return@mapNotNull null, p[3].toIntOrNull() ?: return@mapNotNull null)
        }
        return City(
            id = id,
            name = name,
            region = region,
            tzOffsetMinutes = tz,
            seed = seed,
            baseHighF = high,
            baseLowF = low,
            current = if (temp != null || sky != null) CurrentReading(temp, sky, observed) else null,
            daily = daily,
            refreshedAtMillis = refreshed,
        )
    }
}
