package com.heretek.dorado_hd.ui.apps.mocks

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.components.DetailScaffold

/**
 * Zune Social (canon §8, offline mock): a small memorial feed plus a user
 * card. The service shut down in 2012, so the feed is frozen local copy and
 * nothing here talks to a network.
 */

private enum class SocialTab { FEED, PROFILE }

private data class SocialEntry(
    val author: String,
    val action: String,
    val subject: String,
    val stamp: String,
    val accent: Boolean = false,
)

private val FEED: List<SocialEntry> = listOf(
    SocialEntry("mira k.", "is listening to", "harbor lights (slow version)", "archived oct 2012"),
    SocialEntry("tomas r.", "hearted", "ninety-nine steps — a. vance", "archived oct 2012"),
    SocialEntry("the junction", "earned the", "night owl badge", "archived sep 2012", accent = true),
    SocialEntry("you", "added to", "the small machines shelf", "archived sep 2012"),
    SocialEntry("priya s.", "is listening to", "tide house field recordings", "archived sep 2012"),
    SocialEntry("oren b.", "joined", "zune social", "archived aug 2012"),
)

@Composable
fun SocialApp() {
    val colors = LocalDoradoColors.current
    var tab by remember { mutableStateOf(SocialTab.FEED) }
    DetailScaffold(title = "social") {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DoradoTokens.EDGE.dp),
            ) {
                EdgeCropText(
                    text = "the zune social service closed in 2012 — local memorial, no network",
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = colors.accent,
                    modifier = Modifier.weight(1f),
                )
                SocialTab.entries.forEach { item ->
                    EdgeCropText(
                        text = item.name.lowercase(),
                        fontSize = DoradoTokens.TYPE_NOW_META.dp,
                        color = if (item == tab) colors.textPrimary else colors.textInactive,
                        modifier = Modifier
                            .clickable { tab = item }
                            .padding(horizontal = 8.dp),
                    )
                }
            }

            when (tab) {
                SocialTab.FEED -> FeedTab()
                SocialTab.PROFILE -> ProfileTab()
            }
        }
    }
}

@Composable
private fun FeedTab() {
    val colors = LocalDoradoColors.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DoradoTokens.EDGE.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        FEED.forEach { entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    EdgeCropText(
                        text = "${entry.author} ${entry.action}",
                        fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
                        color = if (entry.accent) colors.accent else colors.textSecondary,
                    )
                    EdgeCropText(
                        text = entry.subject,
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = colors.textPrimary,
                    )
                }
                EdgeCropText(entry.stamp, DoradoTokens.TYPE_CAPTION.dp, color = colors.textInactive)
            }
        }
    }
}

@Composable
private fun ProfileTab() {
    val colors = LocalDoradoColors.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = DoradoTokens.EDGE.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 56.dp, height = 56.dp)
                    .background(colors.tile),
                contentAlignment = Alignment.Center,
            ) {
                EdgeCropText("you", DoradoTokens.TYPE_NOW_META.dp, color = colors.accent)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                EdgeCropText("you", DoradoTokens.TYPE_NOW_TITLE.dp, color = colors.textPrimary)
                EdgeCropText("member since september 2009 · profile archived", DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            SocialStat("plays", "4,812")
            SocialStat("hearts", "137")
            SocialStat("badges", "9")
            SocialStat("friends", "42")
        }
        BasicText(
            text = "zune social kept a running record of what you played and loved. the service is gone; these figures are a local memorial simulation.",
            style = TextStyle(
                fontFamily = Selawik,
                fontSize = DoradoTokens.TYPE_LIST.sp,
                color = colors.textSecondary,
                lineHeight = (DoradoTokens.TYPE_LIST * 1.4f).sp,
            ),
        )
    }
}

@Composable
private fun SocialStat(label: String, value: String) {
    val colors = LocalDoradoColors.current
    Column {
        EdgeCropText(value, DoradoTokens.TYPE_NOW_TITLE.dp, color = colors.textPrimary)
        EdgeCropText(label, DoradoTokens.TYPE_CAPTION.dp, color = colors.textInactive)
    }
}
