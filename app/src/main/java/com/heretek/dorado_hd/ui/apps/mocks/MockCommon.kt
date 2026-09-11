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

/** Shared shell for the dead-service app surfaces (canon §8). */
@Composable
internal fun MockPage(
    title: String,
    spacing: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    DetailScaffold(title = title) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = DoradoTokens.EDGE.dp, end = DoradoTokens.EDGE.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            Spacer(Modifier.height(DoradoTokens.EDGE.dp))
            content()
        }
    }
}

/** Wrapping body text (EdgeCropText is single-line and clipped sentences). */
@Composable
internal fun MockBody(text: String) {
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = Selawik,
            fontSize = DoradoTokens.TYPE_LIST.sp,
            color = LocalDoradoColors.current.textPrimary,
            lineHeight = (DoradoTokens.TYPE_LIST * 1.35f).sp,
        ),
    )
}
