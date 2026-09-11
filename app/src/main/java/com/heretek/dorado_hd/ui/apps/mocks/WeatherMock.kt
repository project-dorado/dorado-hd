package com.heretek.dorado_hd.ui.apps.mocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.components.DetailScaffold

/**
 * Mock shells for the dead-marketplace apps (canon §8 + §9). Era-faithful
 * layouts with canned content; no live network or service integration.
 *
 * Every page scrolls and reserves the MiniPlayer strip: at 480x272 the
 * longer mocks (weather, reader, money) previously clipped their last rows
 * with no way to reach them.
 */

@Composable fun WeatherMock() {
    MockPage("weather") {
        EdgeCropText(text = "Seattle, WA", fontSize = DoradoTokens.TYPE_NOW_META.dp, alpha = 0.6f)
        EdgeCropText(text = "62°", fontSize = DoradoTokens.TYPE_HEADER_CROPPED.dp)
        EdgeCropText(text = "light rain — last seen 2012", fontSize = DoradoTokens.TYPE_NOW_META.dp, color = LocalDoradoColors.current.textSecondary)
        Spacer(Modifier.height(8.dp))
        // Frozen 7-day forecast (mock data).
        listOf(
            "mon" to "62° / 48°",
            "tue" to "60° / 47°",
            "wed" to "59° / 46°",
            "thu" to "61° / 49°",
            "fri" to "63° / 50°",
            "sat" to "65° / 52°",
            "sun" to "66° / 53°",
        ).forEach { (day, hiLo) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                EdgeCropText(text = day, fontSize = DoradoTokens.TYPE_LIST.dp, modifier = Modifier.weight(1f))
                EdgeCropText(text = hiLo, fontSize = DoradoTokens.TYPE_LIST.dp, color = LocalDoradoColors.current.accent)
            }
        }
    }
}
