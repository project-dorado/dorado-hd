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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.data.model.Rating
import com.heretek.dorado_hd.data.repo.InboxMessage
import com.heretek.dorado_hd.data.repo.InboxRepository
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.social.CardExporter
import com.heretek.dorado_hd.social.CardRenderer
import com.heretek.dorado_hd.social.ZuneCardData
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Zune Social (canon §8, D2): a local memorial feed, the local-first inbox
 * cache and the Zune Card. The service shut down in 2012, so nothing here
 * requires a network; when Dorado Cloud is on the main Social screen syncs the
 * inbox, and this mini-app renders the same cached rows with an offline label.
 */

private enum class SocialTab { FEED, INBOX, PROFILE }

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

private data class LocalCard(val tracks: Int, val hearts: Int, val plays: Int)

@Composable
fun SocialApp() {
    val graph = LocalDoradoGraph.current
    val colors = LocalDoradoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(SocialTab.FEED) }
    var card by remember { mutableStateOf<LocalCard?>(null) }
    val inbox by graph.inbox.messages().collectAsState(initial = emptyList())
    val unread by graph.inbox.unreadCount().collectAsState(initial = 0)

    LaunchedEffect(Unit) {
        val tracks = graph.library.tracks().first()
        val ratings = graph.quickplay.ratings()
        val plays = graph.playCounts.counts()
        card = LocalCard(
            tracks = tracks.size,
            hearts = ratings.count { it.value == Rating.HEART.value },
            plays = plays.values.sum(),
        )
    }

    DetailScaffold(title = "social") {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DoradoTokens.EDGE.dp),
            ) {
                EdgeCropText(
                    text = InboxRepository.OFFLINE_DISABLED,
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                SocialTab.entries.forEach { item ->
                    val label = if (item == SocialTab.INBOX && unread > 0) "inbox $unread" else item.name.lowercase()
                    EdgeCropText(
                        text = label,
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
                SocialTab.INBOX -> InboxTab(inbox) { id -> scope.launch { graph.inbox.markRead(id) } }
                SocialTab.PROFILE -> ProfileTab(
                    card = card,
                    onExport = {
                        card?.let { local ->
                            val bitmap = CardRenderer(CardRenderer.selawik(context)).render(
                                ZuneCardData(
                                    displayName = "you",
                                    tracks = local.tracks,
                                    hearts = local.hearts,
                                    plays = local.plays,
                                    memberSince = "september 2009",
                                    offline = true,
                                    background = colors.background.toArgb(),
                                    accent = colors.accent.toArgb(),
                                    textPrimary = colors.textPrimary.toArgb(),
                                    textSecondary = colors.textSecondary.toArgb(),
                                ),
                            )
                            context.startActivity(CardExporter.shareIntent(context, bitmap, "zune-card"))
                        }
                    },
                )
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
private fun InboxTab(inbox: List<InboxMessage>, onMarkRead: (String) -> Unit) {
    val colors = LocalDoradoColors.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DoradoTokens.EDGE.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        if (inbox.isEmpty()) {
            EdgeCropText(
                text = "no messages — the inbox is empty.",
                fontSize = DoradoTokens.TYPE_NOW_META.dp,
                alpha = 0.6f,
            )
            return
        }
        inbox.forEach { message ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onMarkRead(message.id) }
                    .padding(vertical = 6.dp),
            ) {
                EdgeCropText(
                    text = message.senderTag.ifBlank { "unknown sender" },
                    fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
                    color = if (message.isRead) colors.textSecondary else colors.accent,
                    fontWeight = if (message.isRead) FontWeight.Normal else FontWeight.SemiBold,
                )
                EdgeCropText(
                    text = message.subject.ifBlank { "(no subject)" },
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun ProfileTab(card: LocalCard?, onExport: () -> Unit) {
    val colors = LocalDoradoColors.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
            Column(Modifier.weight(1f)) {
                EdgeCropText("you", DoradoTokens.TYPE_NOW_TITLE.dp, color = colors.textPrimary)
                EdgeCropText("member since september 2009 · profile archived", DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            SocialStat("tracks", (card?.tracks ?: 0).toString(), Modifier.weight(1f))
            SocialStat("hearts", (card?.hearts ?: 0).toString(), Modifier.weight(1f))
            SocialStat("plays", (card?.plays ?: 0).toString(), Modifier.weight(1f))
        }
        EdgeCropText(
            text = "export card",
            fontSize = DoradoTokens.TYPE_LIST.dp,
            color = colors.accent,
            modifier = Modifier.clickable { onExport() },
        )
        BasicText(
            text = "zune social kept a running record of what you played and loved. the service is gone; the card is rendered on-device from your own library, with no microsoft art or fonts.",
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
private fun SocialStat(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalDoradoColors.current
    Column(modifier) {
        EdgeCropText(value, DoradoTokens.TYPE_NOW_TITLE.dp, color = colors.textPrimary)
        EdgeCropText(label, DoradoTokens.TYPE_CAPTION.dp, color = colors.textInactive)
    }
}
