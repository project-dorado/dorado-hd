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

@Composable fun SocialMock() {
    MockPage("social", spacing = 10.dp) {
        EdgeCropText(text = "the social feed is frozen.", fontSize = DoradoTokens.TYPE_NOW_META.dp, alpha = 0.6f)
        MockBody("the zune social service shut down in 2012. what you see here is a memorial.")
    }
}
