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

@Composable fun MessengerMock() {
    MockPage("messenger") {
        listOf(
            "alex" to "are you still on zune?",
            "alex" to "anyway — saw this and thought of you. miss the old days.",
            "you" to "(sending is disabled — service frozen)",
        ).forEach { (who, msg) ->
            Column {
                EdgeCropText(text = who, fontSize = DoradoTokens.TYPE_NOW_META.dp, color = LocalDoradoColors.current.accent)
                MockBody(msg)
            }
        }
    }
}
