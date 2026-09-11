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

@Composable
private fun MockPage(
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
private fun MockBody(text: String) {
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

@Composable fun TwitterMock() {
    MockPage("twitter", spacing = 12.dp) {
        listOf(
            "@zune" to "the social feed has been quiet since 2012. here's to the years we shared.",
            "@britney" to "listening to a whole album for the first time in ages. the shuffle is off. the world is right.",
            "@radio" to "tuning in from somewhere off the grid. signal's fine.",
        ).forEach { (handle, text) ->
            Column {
                EdgeCropText(text = handle, fontSize = DoradoTokens.TYPE_NOW_META.dp, color = LocalDoradoColors.current.accent)
                MockBody(text)
            }
        }
    }
}

@Composable fun FacebookMock() {
    MockPage("facebook", spacing = 10.dp) {
        EdgeCropText(text = "your zune hd wall: 7 friends still listen", fontSize = DoradoTokens.TYPE_NOW_META.dp, alpha = 0.6f)
        listOf(
            "alex" to "shared: a playlist called 'morning'.",
            "jordan" to "shared: a photo from the live show.",
            "sam" to "pinned a new track.",
        ).forEach { (name, what) ->
            Column {
                EdgeCropText(text = name, fontSize = DoradoTokens.TYPE_NOW_META.dp, color = LocalDoradoColors.current.accent)
                MockBody(what)
            }
        }
    }
}

@Composable fun EmailMock() {
    MockPage("email", spacing = 10.dp) {
        listOf(
            "windows live" to "your inbox has been frozen since august 2012.",
            "exchange admin" to "policy reminder: please rotate your calendar password.",
            "noreply" to "no new messages — but it is still possible to send.",
        ).forEach { (from, body) ->
            Column {
                EdgeCropText(text = from, fontSize = DoradoTokens.TYPE_NOW_META.dp, color = LocalDoradoColors.current.accent)
                MockBody(body)
            }
        }
    }
}

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

@Composable fun MsnMoneyMock() {
    MockPage("msn money", spacing = 10.dp) {
        EdgeCropText(text = "dow jones (frozen)", fontSize = DoradoTokens.TYPE_NOW_META.dp, alpha = 0.6f)
        EdgeCropText(text = "13,583.93 +27.59", fontSize = DoradoTokens.TYPE_NOW_TITLE.dp * 1.4f)
        Spacer(Modifier.height(8.dp))
        listOf(
            "tech" to "Microsoft announces the end of Zune hardware sales.",
            "media" to "Music subscription services consolidate around streaming.",
            "world" to "Markets end mixed after central bank statement.",
        ).forEach { (sec, headline) ->
            Column {
                EdgeCropText(text = sec, fontSize = DoradoTokens.TYPE_NOW_META.dp, color = LocalDoradoColors.current.accent)
                MockBody(headline)
            }
        }
    }
}

@Composable fun ZuneReaderMock() {
    MockPage("zune reader", spacing = 10.dp) {
        EdgeCropText(text = "your library", fontSize = DoradoTokens.TYPE_NOW_META.dp, alpha = 0.6f)
        listOf(
            "The Brief Wondrous Life of Oscar Wao" to "junot díaz",
            "Fingersmith" to "sarah waters",
            "Cloud Atlas" to "david mitchell",
            "The Windup Girl" to "paolo bacigalupi",
            "House of Leaves" to "mark z. danielewski",
        ).forEach { (title, author) ->
            Column {
                MockBody(title)
                EdgeCropText(text = author, fontSize = DoradoTokens.TYPE_CAPTION.dp, color = LocalDoradoColors.current.textSecondary)
            }
        }
    }
}

@Composable fun SocialMock() {
    MockPage("social", spacing = 10.dp) {
        EdgeCropText(text = "the social feed is frozen.", fontSize = DoradoTokens.TYPE_NOW_META.dp, alpha = 0.6f)
        MockBody("the zune social service shut down in 2012. what you see here is a memorial.")
    }
}
