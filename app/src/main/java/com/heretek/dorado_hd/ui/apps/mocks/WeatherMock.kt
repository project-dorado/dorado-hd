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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
 * Weather (docs/apps/weather.md, W7): horizontally paged city pages with a
 * condition glyph, large current temperature, hi/lo pair and a seven-row
 * forecast, plus a settings panel with the °F/°C toggle and a city manager.
 *
 * The zune.net service is dead, so the page renders the 2012 snapshot and
 * local deterministic simulations. Everything is labelled archived.
 */

private enum class WeatherPane { CITIES, SETTINGS, ABOUT }

private val DAY_NAMES = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")

@Composable
fun WeatherApp() {
    val graph = LocalDoradoGraph.current
    var pane by remember { mutableStateOf(WeatherPane.CITIES) }
    var state by remember {
        mutableStateOf(
            WeatherState(
                unit = TempUnit.FAHRENHEIT,
                selected = 0,
                cities = WeatherModel.seedCities(),
            ),
        )
    }
    var loaded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        graph.appState.get("weather")?.let { raw ->
            WeatherStateCodec.decode(raw)?.let { state = it }
        }
        loaded = true
    }

    LaunchedEffect(state, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(500)
        graph.appState.put("weather", WeatherStateCodec.encode(state))
    }

    when (pane) {
        WeatherPane.CITIES -> CityPagerScreen(
            state = state,
            onState = { state = it },
            onOpenSettings = { pane = WeatherPane.SETTINGS },
        )
        WeatherPane.SETTINGS -> SettingsScreen(
            state = state,
            onState = { state = it },
            query = query,
            onQuery = { query = it },
            onBack = { pane = WeatherPane.CITIES },
            onAbout = { pane = WeatherPane.ABOUT },
        )
        WeatherPane.ABOUT -> AboutScreen(onBack = { pane = WeatherPane.SETTINGS })
    }
}

@Composable
private fun CityPagerScreen(
    state: WeatherState,
    onState: (WeatherState) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val now = remember { System.currentTimeMillis() }
    val pages = state.cities.size.coerceAtLeast(1)
    val pager = rememberPagerState(initialPage = state.selected.coerceIn(0, pages - 1)) { pages }

    LaunchedEffect(state.cities.size) {
        if (pager.currentPage > state.cities.lastIndex) {
            pager.scrollToPage(state.cities.lastIndex.coerceAtLeast(0))
        }
    }
    LaunchedEffect(pager.currentPage, state.cities.size) {
        val page = pager.currentPage
        if (page != state.selected && page <= state.cities.lastIndex) {
            onState(state.copy(selected = page))
        }
    }
    LaunchedEffect(state.selected, state.cities.size) {
        if (state.selected != pager.currentPage && state.selected <= state.cities.lastIndex) {
            pager.scrollToPage(state.selected)
        }
    }

    DetailScaffold(title = "weather") {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 2.dp),
            ) {
                EdgeCropText(
                    text = if (state.cities.isEmpty()) "no cities" else "city ${state.selected + 1} of ${state.cities.size}",
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.weight(1f))
                EdgeCropText(
                    text = "refresh",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    color = colors.accent,
                    modifier = Modifier
                        .clickable {
                            val at = System.currentTimeMillis()
                            onState(
                                state.copy(
                                    cities = state.cities.mapIndexed { index, city ->
                                        if (index == pager.currentPage) WeatherModel.simulateRefresh(city, at) else city
                                    },
                                ),
                            )
                        }
                        .padding(horizontal = 8.dp),
                )
                EdgeCropText(
                    text = "settings",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .clickable { onOpenSettings() }
                        .padding(horizontal = 8.dp),
                )
            }

            if (state.cities.isEmpty()) {
                NoCityMessage(onOpenSettings)
            } else {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    val city = state.cities.getOrNull(page) ?: return@HorizontalPager
                    CityPage(city = city, unit = state.unit, nowMillis = now)
                }
            }
        }
    }
}

@Composable
private fun NoCityMessage(onOpenSettings: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .width(280.dp)
                .background(colors.tile)
                .padding(DoradoTokens.EDGE.dp),
        ) {
            WxText("no cities saved", DoradoTokens.TYPE_LIST.sp, colors.textPrimary)
            WxText("the city manager is offline; add a city from the seeded list.", DoradoTokens.TYPE_LIST_SECONDARY.sp, colors.textSecondary)
            WxText("open city manager", DoradoTokens.TYPE_LIST_SECONDARY.sp, colors.accent, onClick = onOpenSettings)
        }
    }
}

@Composable
private fun CityPage(city: City, unit: TempUnit, nowMillis: Long) {
    val colors = LocalDoradoColors.current
    val reading = WeatherModel.effectiveReading(city)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DoradoTokens.EDGE.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        EdgeCropText(
            text = "${city.name}, ${city.region}",
            fontSize = DoradoTokens.TYPE_NOW_META.dp,
            color = colors.textSecondary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkyGlyph(sky = reading.sky, size = 96.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                EdgeCropText(
                    text = WeatherModel.formatTemp(reading.tempF, unit),
                    fontSize = DoradoTokens.TYPE_HEADER_CROPPED.dp,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Light,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EdgeCropText(
                        text = "hi ${WeatherModel.formatTemp(reading.highF, unit)}",
                        fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    EdgeCropText(
                        text = "lo ${WeatherModel.formatTemp(reading.lowF, unit)}",
                        fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
                        color = colors.textSecondary,
                    )
                    if (reading.fromForecast) {
                        Spacer(Modifier.width(8.dp))
                        EdgeCropText(
                            text = "no reading",
                            fontSize = DoradoTokens.TYPE_CAPTION.dp,
                            color = colors.textInactive,
                        )
                    }
                }
            }
        }

        EdgeCropText(
            text = "archived 2012 — zune.net service closed · simulated local forecast",
            fontSize = DoradoTokens.TYPE_CAPTION.dp,
            color = colors.accent,
        )

        city.daily.take(WeatherModel.FORECAST_DAYS).forEachIndexed { index, day ->
            ForecastRow(day = day, tzOffsetMinutes = city.tzOffsetMinutes, unit = unit, today = index == 0)
        }

        val ago = WeatherModel.formatAgo(nowMillis, city.refreshedAtMillis)
        if (ago != null) {
            EdgeCropText(
                text = "archived last seen $ago",
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = colors.textInactive,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ForecastRow(day: DayForecast, tzOffsetMinutes: Int, unit: TempUnit, today: Boolean) {
    val colors = LocalDoradoColors.current
    val localDay = WeatherModel.localDay(day.dayStartMillis, tzOffsetMinutes)
    val name = when {
        today -> "today"
        else -> DAY_NAMES[(((localDay % 7) + 7 + 4) % 7).toInt()]
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(DoradoTokens.ROW_HEIGHT.dp),
    ) {
        Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterStart) {
            EdgeCropText(name, DoradoTokens.TYPE_LIST.dp, color = if (today) colors.accent else colors.textPrimary)
        }
        SkyGlyph(sky = day.sky, size = 26.dp)
        Spacer(Modifier.weight(1f))
        Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterEnd) {
            EdgeCropText(WeatherModel.formatTemp(day.highF, unit), DoradoTokens.TYPE_LIST.dp, color = colors.textPrimary)
        }
        Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterEnd) {
            EdgeCropText(WeatherModel.formatTemp(day.lowF, unit), DoradoTokens.TYPE_LIST.dp, color = colors.textSecondary)
        }
    }
}

@Composable
private fun SettingsScreen(
    state: WeatherState,
    onState: (WeatherState) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onAbout: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val suggestions = remember(query, state.cities) {
        if (query.isBlank()) emptyList()
        else WeatherModel.catalog()
            .filter { it.id !in state.cities.map { city -> city.id } }
            .filter { it.name.contains(query.trim().lowercase()) || it.region.contains(query.trim().lowercase()) }
            .take(4)
    }
    DetailScaffold(title = "city manager", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WxText("units", DoradoTokens.TYPE_LIST.sp, colors.textSecondary)
                Spacer(Modifier.width(16.dp))
                UnitToggle("°f", state.unit == TempUnit.FAHRENHEIT) {
                    onState(state.copy(unit = TempUnit.FAHRENHEIT))
                }
                UnitToggle("°c", state.unit == TempUnit.CELSIUS) {
                    onState(state.copy(unit = TempUnit.CELSIUS))
                }
                Spacer(Modifier.weight(1f))
                WxText("about", DoradoTokens.TYPE_LIST.sp, colors.textSecondary, onClick = onAbout)
            }

            WxText("saved cities", DoradoTokens.TYPE_CAPTION.sp, colors.textInactive)

            if (state.cities.isEmpty()) {
                WxText("no cities saved — search below to add one.", DoradoTokens.TYPE_LIST.sp, colors.textSecondary)
            }
            state.cities.forEachIndexed { index, city ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(35.dp),
                ) {
                    WxText("remove", DoradoTokens.TYPE_CAPTION.sp, colors.textInactive) {
                        val next = state.cities.filterIndexed { i, _ -> i != index }
                        onState(state.copy(cities = next, selected = state.selected.coerceIn(0, (next.size - 1).coerceAtLeast(0))))
                    }
                    Spacer(Modifier.width(16.dp))
                    WxText(
                        text = "${city.name}, ${city.region}",
                        size = DoradoTokens.TYPE_LIST.sp,
                        color = if (index == state.selected) colors.accent else colors.textPrimary,
                        onClick = {
                            onState(state.copy(selected = index))
                            onBack()
                        },
                    )
                }
            }

            WxText("add city", DoradoTokens.TYPE_CAPTION.sp, colors.textInactive)
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
            if (query.isBlank()) {
                WxText("type a city name; suggestions match the seeded list.", DoradoTokens.TYPE_CAPTION.sp, colors.textInactive)
            }
            suggestions.forEach { city ->
                WxText(
                    text = "add ${city.name}, ${city.region}",
                    size = DoradoTokens.TYPE_LIST.sp,
                    color = colors.accent,
                ) {
                    val snapshot = WeatherModel.snapshot(city, WeatherModel.ARCHIVE_DAY)
                    onState(
                        state.copy(
                            cities = state.cities + snapshot,
                            selected = state.cities.size,
                        ),
                    )
                    onQuery("")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UnitToggle(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    WxText(
        text = label,
        size = DoradoTokens.TYPE_NOW_META.sp,
        color = if (active) colors.accent else colors.textInactive,
        onClick = onClick,
    )
}

@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "about weather", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EdgeCropText("archived 2012", DoradoTokens.TYPE_NOW_META.dp, color = colors.accent)
            WxBody("the original weather app was a thin client for the zune.net / msn weather feed. that service is gone, so dorado-hd ships a clean-room local build.")
            WxBody("city pages, the seven-day list, the °f/°c toggle and the city manager all work offline. forecasts and conditions are generated deterministically on device and labelled archived — they are not real weather.")
            WxBody("the condition glyphs are re-authored shapes; no microsoft artwork or type is bundled.")
        }
    }
}

@Composable
private fun WxText(
    text: String,
    size: TextUnit,
    color: Color,
    onClick: (() -> Unit)? = null,
) {
    val base = Modifier.padding(vertical = 2.dp)
    EdgeCropText(
        text = text,
        fontSize = size.value.dp,
        color = color,
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
    )
}

@Composable
private fun WxBody(text: String) {
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = Selawik,
            fontSize = DoradoTokens.TYPE_LIST.sp,
            color = LocalDoradoColors.current.textPrimary,
            lineHeight = (DoradoTokens.TYPE_LIST * 1.4f).sp,
        ),
    )
}

/**
 * Re-authored condition glyphs (the device shipped 128/32 px bitmap atlases).
 * Simple geometric shapes, drawn only with theme tokens.
 */
@Composable
private fun SkyGlyph(sky: Sky?, size: Dp, modifier: Modifier = Modifier) {
    val colors = LocalDoradoColors.current
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val ink = colors.textPrimary
        val accent = colors.accent
        val stroke = w * 0.06f

        fun cloud(scale: Float = 1f, dy: Float = 0f) {
            val cx = w * 0.5f
            val cy = h * 0.5f + dy
            drawCircle(ink, w * 0.17f * scale, Offset(cx - w * 0.14f * scale, cy))
            drawCircle(ink, w * 0.21f * scale, Offset(cx, cy - h * 0.08f * scale))
            drawCircle(ink, w * 0.14f * scale, Offset(cx + w * 0.15f * scale, cy + h * 0.02f * scale))
            drawRect(
                ink,
                topLeft = Offset(cx - w * 0.29f * scale, cy + h * 0.02f * scale),
                size = Size(w * 0.57f * scale, h * 0.15f * scale),
            )
        }

        fun sun(cx: Float, cy: Float, radius: Float) {
            drawCircle(accent, radius, Offset(cx, cy))
            val r0 = radius * 1.45f
            val r1 = radius * 1.95f
            for (i in 0 until 8) {
                val angle = i * Math.PI.toFloat() / 4f
                drawLine(
                    color = accent,
                    start = Offset(cx + kotlin.math.cos(angle) * r0, cy + kotlin.math.sin(angle) * r0),
                    end = Offset(cx + kotlin.math.cos(angle) * r1, cy + kotlin.math.sin(angle) * r1),
                    strokeWidth = stroke,
                )
            }
        }

        when (sky) {
            null -> drawLine(ink, Offset(w * 0.2f, h * 0.5f), Offset(w * 0.8f, h * 0.5f), stroke)
            Sky.SUN -> sun(w * 0.5f, h * 0.5f, w * 0.2f)
            Sky.PARTLY -> {
                sun(w * 0.36f, h * 0.34f, w * 0.15f)
                cloud(scale = 0.85f, dy = h * 0.13f)
            }
            Sky.CLOUD -> cloud()
            Sky.RAIN -> {
                cloud(scale = 0.9f, dy = -h * 0.08f)
                for (i in 0 until 3) {
                    val x = w * (0.32f + i * 0.18f)
                    drawLine(accent, Offset(x, h * 0.72f), Offset(x - w * 0.05f, h * 0.92f), stroke)
                }
            }
            Sky.STORM -> {
                cloud(scale = 0.9f, dy = -h * 0.08f)
                val bolt = Path().apply {
                    moveTo(w * 0.52f, h * 0.6f)
                    lineTo(w * 0.42f, h * 0.82f)
                    lineTo(w * 0.5f, h * 0.82f)
                    lineTo(w * 0.44f, h) 
                    lineTo(w * 0.62f, h * 0.76f)
                    lineTo(w * 0.53f, h * 0.76f)
                    lineTo(w * 0.6f, h * 0.6f)
                    close()
                }
                drawPath(bolt, accent)
            }
            Sky.SNOW -> {
                cloud(scale = 0.9f, dy = -h * 0.08f)
                for (i in 0 until 3) {
                    val x = w * (0.32f + i * 0.18f)
                    val y = h * 0.82f
                    drawLine(ink, Offset(x - w * 0.04f, y), Offset(x + w * 0.04f, y), stroke)
                    drawLine(ink, Offset(x, y - h * 0.06f), Offset(x, y + h * 0.06f), stroke)
                }
            }
            Sky.FOG -> {
                for (i in 0 until 3) {
                    val y = h * (0.32f + i * 0.18f)
                    drawLine(ink, Offset(w * (0.2f + i * 0.06f), y), Offset(w * (0.8f - i * 0.06f), y), stroke)
                }
            }
            Sky.WIND -> {
                for (i in 0 until 3) {
                    val y = h * (0.32f + i * 0.18f)
                    drawLine(ink, Offset(w * 0.2f, y), Offset(w * 0.72f, y), stroke)
                    drawArc(
                        color = ink,
                        startAngle = -90f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(w * 0.66f, y - h * 0.09f),
                        size = Size(w * 0.18f, h * 0.18f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                    )
                }
            }
        }
    }
}
