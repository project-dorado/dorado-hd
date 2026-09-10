package com.heretek.dorado_hd.ui.apps.mocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.components.DetailScaffold

/**
 * Mock shells for the dead-marketplace apps (canon §8 + §9). Era-faithful
 * layouts with canned content; no live network or service integration.
 */

@Composable fun WeatherMock() {
    DetailScaffold(title = "weather") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
}

@Composable fun TwitterMock() {
    DetailScaffold(title = "twitter") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                "@zune" to "the social feed has been quiet since 2012. here's to the years we shared.",
                "@britney" to "listening to a whole album for the first time in ages. the shuffle is off. the world is right.",
                "@radio" to "tuning in from somewhere off the grid. signal's fine.",
            ).forEach { (handle, text) ->
                Column {
                    EdgeCropText(text = handle, fontSize = DoradoTokens.TYPE_NOW_META.dp, color = LocalDoradoColors.current.accent)
                    EdgeCropText(text = text, fontSize = DoradoTokens.TYPE_LIST.dp)
                }
            }
        }
    }
}

@Composable fun FacebookMock() {
    DetailScaffold(title = "facebook") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EdgeCropText(text = "your zune hd wall: 7 friends still listen", fontSize = DoradoTokens.TYPE_NOW_META.dp, alpha = 0.6f)
            listOf(
                "alex" to "shared: a playlist called 'morning'.",
                "jordan" to "shared: a photo from the live show.",
                "sam" to "pinned a new track.",
            ).forEach { (name, what) ->
                Column {
                    EdgeCropText(text = name, fontSize = DoradoTokens.TYPE_NOW_META.dp, color = LocalDoradoColors.current.accent)
                    EdgeCropText(text = what, fontSize = DoradoTokens.TYPE_LIST.dp)
                }
            }
        }
    }
}

@Composable fun EmailMock() {
    DetailScaffold(title = "email") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                "windows live" to "your inbox has been frozen since august 2012.",
                "exchange admin" to "policy reminder: please rotate your calendar password.",
                "noreply" to "no new messages — but it is still possible to send.",
            ).forEach { (from, body) ->
                Column {
                    EdgeCropText(text = from, fontSize = DoradoTokens.TYPE_NOW_META.dp, color = LocalDoradoColors.current.accent)
                    EdgeCropText(text = body, fontSize = DoradoTokens.TYPE_LIST.dp)
                }
            }
        }
    }
}

@Composable fun MessengerMock() {
    DetailScaffold(title = "messenger") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "alex" to "are you still on zune?",
                "alex" to "anyway — saw this and thought of you. miss the old days.",
                "you" to "(sending is disabled — service frozen)",
            ).forEach { (who, msg) ->
                Column {
                    EdgeCropText(text = who, fontSize = DoradoTokens.TYPE_NOW_META.dp, color = LocalDoradoColors.current.accent)
                    EdgeCropText(text = msg, fontSize = DoradoTokens.TYPE_LIST.dp)
                }
            }
        }
    }
}

@Composable fun MsnMoneyMock() {
    DetailScaffold(title = "msn money") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    EdgeCropText(text = headline, fontSize = DoradoTokens.TYPE_LIST.dp)
                }
            }
        }
    }
}

@Composable fun ZuneReaderMock() {
    DetailScaffold(title = "zune reader") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EdgeCropText(text = "your library", fontSize = DoradoTokens.TYPE_NOW_META.dp, alpha = 0.6f)
            listOf(
                "The Brief Wondrous Life of Oscar Wao" to "junot díaz",
                "Fingersmith" to "sarah waters",
                "Cloud Atlas" to "david mitchell",
                "The Windup Girl" to "paolo bacigalupi",
                "House of Leaves" to "mark z. danielewski",
            ).forEach { (title, author) ->
                Column {
                    EdgeCropText(text = title, fontSize = DoradoTokens.TYPE_LIST.dp)
                    EdgeCropText(text = author, fontSize = DoradoTokens.TYPE_CAPTION.dp, color = LocalDoradoColors.current.textSecondary)
                }
            }
        }
    }
}

@Composable fun SocialMock() {
    DetailScaffold(title = "social") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EdgeCropText(text = "the social feed is frozen.", fontSize = DoradoTokens.TYPE_NOW_META.dp, alpha = 0.6f)
            EdgeCropText(text = "the zune social service shut down in 2012. what you see here is a memorial.", fontSize = DoradoTokens.TYPE_LIST.dp)
        }
    }
}
