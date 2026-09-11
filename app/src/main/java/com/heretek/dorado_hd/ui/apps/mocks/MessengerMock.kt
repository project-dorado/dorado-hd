package com.heretek.dorado_hd.ui.apps.mocks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import com.heretek.dorado_hd.ui.components.LocalContextMenu
import com.heretek.dorado_hd.ui.components.MenuAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class MsScreen { SIGNIN, HUB, CHATS, THREAD, FRIENDS, SOCIAL, OPTIONS, ABOUT }

@Composable
private fun MsAction(
    label: String,
    enabled: Boolean = true,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    EdgeCropText(
        text = label,
        fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
        color = when {
            !enabled -> colors.textInactive
            accent -> colors.accent
            else -> colors.textPrimary
        },
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 6.dp),
    )
}

@Composable
private fun MsPivots(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = LocalDoradoColors.current
    // FlowRow wraps instead of clipping: the four-state presence selector used
    // to shove its third option off-screen on the adaptive canvas (V-06).
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        labels.forEachIndexed { index, label ->
            EdgeCropText(
                text = label,
                fontSize = DoradoTokens.TYPE_CROSSBAR.dp,
                color = if (index == selected) colors.accent else colors.textSecondary,
                modifier = Modifier.clickable { onSelect(index) },
            )
        }
    }
}

@Composable
private fun MsBanner(text: String) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.elevated)
            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 4.dp),
    ) {
        EdgeCropText(text, DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
    }
}

@Composable
private fun MsHairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(LocalDoradoColors.current.border),
    )
}

@Composable
private fun MsAvatar(name: String, size: Dp = 26.dp, presence: Presence? = null) {
    val colors = LocalDoradoColors.current
    Box {
        Box(Modifier.size(size).background(colors.tile), contentAlignment = Alignment.Center) {
            EdgeCropText(name.take(1).lowercase(), DoradoTokens.TYPE_LIST_SECONDARY.dp, color = colors.accent)
        }
        if (presence != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(5.dp)
                    .background(
                        when (presence) {
                            Presence.AVAILABLE -> colors.accent
                            Presence.BUSY, Presence.AWAY -> colors.textSecondary
                            Presence.OFFLINE -> colors.textInactive
                        },
                    ),
            )
        }
    }
}

@Composable
private fun MsRichText(text: String, modifier: Modifier = Modifier) {
    val colors = LocalDoradoColors.current
    val spans = remember(text) { EmoticonEncoder.encode(text) }
    val annotated = remember(spans) {
        buildAnnotatedString {
            spans.forEach { span ->
                when (span) {
                    is RichSpan.Text -> append(span.text)
                    is RichSpan.Emoticon -> withStyle(SpanStyle(color = colors.accent)) { append(span.code) }
                }
            }
        }
    }
    BasicText(
        text = annotated,
        style = TextStyle(
            fontFamily = Selawik,
            fontSize = DoradoTokens.TYPE_LIST.sp,
            color = colors.textPrimary,
            lineHeight = (DoradoTokens.TYPE_LIST * 1.3f).sp,
        ),
        modifier = modifier,
    )
}

@Composable
private fun MsLineField(placeholder: String, value: String, onChange: (String) -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .border(0.5.dp, colors.border)
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        if (value.isEmpty()) {
            EdgeCropText(placeholder, DoradoTokens.TYPE_LIST_SECONDARY.dp, color = colors.textInactive)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Offline Windows Live Messenger client. Sign-in, presence, conversation
 * history, contacts, options and the rich-text (emoticon) encoder are local;
 * the account and options persist in `graph.appState`, sending appends to the
 * thread and reports that nothing can be delivered.
 */
@Composable
fun MessengerApp() {
    val graph = LocalDoradoGraph.current
    val colors = LocalDoradoColors.current
    val menus = LocalContextMenu.current
    val scope = rememberCoroutineScope()
    val nowPlaying by graph.controller.nowPlaying.collectAsState()

    var loaded by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(MsScreen.SIGNIN) }
    var account by remember { mutableStateOf(MessengerSeed.account) }
    var options by remember { mutableStateOf(MessengerCodec.MsgrOptions()) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var presenceChoice by remember { mutableStateOf(Presence.AVAILABLE) }
    var signingIn by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf(MessengerSeed.contacts) }
    var conversations by remember { mutableStateOf(MessengerSeed.conversations) }
    var threadId by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var friendsPivot by remember { mutableStateOf(0) }
    var signinPivot by remember { mutableStateOf(false) }
    var personalEdit by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }

    fun announce(message: String) {
        toast = message
    }

    fun contactById(id: String): MsgrContact? = contacts.firstOrNull { it.id == id }

    fun openThreadFor(contact: MsgrContact) {
        val existing = conversations.firstOrNull { it.contactIds == listOf(contact.id) }
        if (existing == null) {
            conversations = conversations + MsgrConversation(
                id = "c-" + contact.id,
                title = contact.name,
                contactIds = listOf(contact.id),
            )
        }
        threadId = existing?.id ?: "c-" + contact.id
        contacts = contacts.map { if (it.id == contact.id) it.copy(unread = 0) else it }
        screen = MsScreen.THREAD
    }

    fun sendText(media: String? = null, nudge: Boolean = false) {
        val id = threadId ?: return
        val conversation = conversations.firstOrNull { it.id == id } ?: return
        val body = if (nudge) "you sent a nudge" else input.trim()
        if (body.isEmpty() && media == null) return
        val message = MsgrMessage(
            from = "you",
            text = media ?: body,
            time = "now",
            mine = true,
            media = if (media != null) media else if (nudge) "nudge" else null,
            nudge = nudge,
        )
        conversations = conversations.map {
            if (it.id == id) it.copy(messages = it.messages + message) else it
        }
        input = ""
        announce("not delivered — service offline")
    }

    fun back() {
        when (screen) {
            MsScreen.SIGNIN -> graph.nav.pop()
            MsScreen.HUB -> graph.nav.pop()
            MsScreen.CHATS, MsScreen.FRIENDS, MsScreen.SOCIAL, MsScreen.OPTIONS -> screen = MsScreen.HUB
            MsScreen.THREAD -> {
                threadId = null
                screen = MsScreen.CHATS
            }
            MsScreen.ABOUT -> screen = MsScreen.OPTIONS
        }
    }

    LaunchedEffect(Unit) {
        MessengerCodec.decodeAccount(graph.appState.get("messenger.account"))?.let {
            account = it
            email = it.email
        }
        options = MessengerCodec.decodeOptions(graph.appState.get("messenger.options"))
        personalEdit = account.personalMessage
        loaded = true
    }

    LaunchedEffect(account, loaded) {
        if (!loaded) return@LaunchedEffect
        // The display-name field mutates per keystroke; debounce the Room write
        // so typing does not queue one upsert per character (H-08).
        delay(400)
        graph.appState.put("messenger.account", MessengerCodec.encodeAccount(account))
    }

    LaunchedEffect(options, loaded) {
        if (loaded) graph.appState.put("messenger.options", MessengerCodec.encodeOptions(options))
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2400)
            toast = null
        }
    }

    val title = when (screen) {
        MsScreen.SIGNIN -> "messenger"
        MsScreen.HUB -> account.displayName
        MsScreen.CHATS -> "chats"
        MsScreen.THREAD -> conversations.firstOrNull { it.id == threadId }?.title ?: "chat"
        MsScreen.FRIENDS -> "friends"
        MsScreen.SOCIAL -> "social"
        MsScreen.OPTIONS -> "options"
        MsScreen.ABOUT -> "about"
    }

    DetailScaffold(title = title, onBack = { back() }) {
        Box(Modifier.fillMaxSize()) {
            when (screen) {
                MsScreen.SIGNIN -> {
                    Column(Modifier.fillMaxSize()) {
                        MsBanner("offline — no live service; sign in to a local copy")
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 48.dp, vertical = 12.dp),
                        ) {
                            EdgeCropText("welcome", DoradoTokens.TYPE_NOW_TITLE.dp, color = colors.accent)
                            EdgeCropText(
                                "sign in to windows live — locally",
                                DoradoTokens.TYPE_LIST_SECONDARY.dp,
                                color = colors.textSecondary,
                            )
                            Spacer(Modifier.height(8.dp))
                            MsLineField("name", account.displayName) { account = account.copy(displayName = it) }
                            Spacer(Modifier.height(5.dp))
                            MsLineField("e-mail address", email) { email = it }
                            Spacer(Modifier.height(5.dp))
                            MsLineField("password", password) { password = it }
                            Spacer(Modifier.height(6.dp))
                            MsPivots(
                                Presence.entries.map { if (it == Presence.OFFLINE) "appear offline" else it.label },
                                Presence.entries.indexOf(presenceChoice),
                            ) { presenceChoice = Presence.entries[it] }
                            Spacer(Modifier.height(8.dp))
                            MsAction(
                                if (signingIn) "signing in…" else "sign in",
                                enabled = email.isNotBlank() && password.isNotBlank() && !signingIn,
                                accent = true,
                            ) { signingIn = true }
                            Spacer(Modifier.height(6.dp))
                            MsAction("change account") {
                                password = ""
                                announce("enter credentials to switch accounts")
                            }
                        }
                    }
                    LaunchedEffect(signingIn) {
                        if (signingIn) {
                            delay(700)
                            account = account.copy(presence = presenceChoice, email = email.ifBlank { account.email })
                            screen = MsScreen.HUB
                            signingIn = false
                            announce("signed in — presence is local")
                        }
                    }
                }

                MsScreen.HUB -> {
                    Column(Modifier.fillMaxSize()) {
                        MsBanner("offline — messages stay on this device")
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                        ) {
                            Row(
                                Modifier.clickable { screen = MsScreen.OPTIONS },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MsAvatar(account.displayName, size = 34.dp, presence = account.presence)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    EdgeCropText(account.displayName, DoradoTokens.TYPE_NOW_META.dp)
                                    MsRichText(account.personalMessage)
                                }
                                EdgeCropText(account.presence.label, DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                            }
                            Spacer(Modifier.height(10.dp))
                            val unread = contacts.sumOf { it.unread }
                            listOf(
                                "social" to "",
                                "friends" to "${contacts.size} contacts",
                                "chats" to if (unread > 0) "$unread new" else "${conversations.size} conversations",
                                "options" to "presence, sounds, privacy",
                            ).forEach { (label, detail) ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            screen = when (label) {
                                                "social" -> MsScreen.SOCIAL
                                                "friends" -> MsScreen.FRIENDS
                                                "chats" -> MsScreen.CHATS
                                                else -> MsScreen.OPTIONS
                                            }
                                        }
                                        .padding(vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    EdgeCropText(label, DoradoTokens.TYPE_MENU_ITEM.dp, modifier = Modifier.weight(1f))
                                    EdgeCropText(detail, DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                                }
                                MsHairline()
                            }
                        }
                    }
                }

                MsScreen.CHATS -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                    ) {
                        if (conversations.isEmpty()) {
                            EdgeCropText("no conversations yet", DoradoTokens.TYPE_LIST.dp, color = colors.textSecondary)
                        }
                        conversations.forEach { conversation ->
                            val contact = contactById(conversation.contactIds.firstOrNull().orEmpty())
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            threadId = conversation.id
                                            contact?.let { c ->
                                                contacts = contacts.map { if (it.id == c.id) it.copy(unread = 0) else it }
                                            }
                                            screen = MsScreen.THREAD
                                        },
                                        onLongClick = {
                                            menus.show(
                                                conversation.title,
                                                listOf(
                                                    MenuAction("open chat") {
                                                        threadId = conversation.id
                                                        screen = MsScreen.THREAD
                                                    },
                                                    MenuAction("delete conversation") {
                                                        conversations = conversations - conversation
                                                    },
                                                ),
                                            )
                                        },
                                    )
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MsAvatar(conversation.title, presence = contact?.presence)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Row {
                                        EdgeCropText(
                                            conversation.title,
                                            DoradoTokens.TYPE_LIST.dp,
                                            fontWeight = if ((contact?.unread ?: 0) > 0) FontWeight.SemiBold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f),
                                        )
                                        EdgeCropText(
                                            conversation.messages.lastOrNull()?.time.orEmpty(),
                                            DoradoTokens.TYPE_CAPTION.dp,
                                            color = colors.textInactive,
                                        )
                                    }
                                    MsRichText(
                                        conversation.messages.lastOrNull()?.text.orEmpty(),
                                        modifier = Modifier.padding(top = 1.dp),
                                    )
                                }
                                when {
                                    contact?.typing == true -> EdgeCropText("typing…", DoradoTokens.TYPE_CAPTION.dp, color = colors.accent)
                                    (contact?.unread ?: 0) > 0 -> EdgeCropText("${contact?.unread}", DoradoTokens.TYPE_LIST_SECONDARY.dp, color = colors.accent)
                                }
                            }
                            MsHairline()
                        }
                    }
                }

                MsScreen.THREAD -> {
                    val conversation = conversations.firstOrNull { it.id == threadId }
                    val participants = conversation?.contactIds.orEmpty().mapNotNull { contactById(it) }
                    val enabled = conversationEnabled(conversation?.contactIds.orEmpty(), contacts)
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(colors.elevated)
                                .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            participants.forEach { participant ->
                                EdgeCropText(
                                    participant.name,
                                    DoradoTokens.TYPE_CAPTION.dp,
                                    color = if (participant.presence == Presence.AVAILABLE) colors.accent else colors.textSecondary,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            EdgeCropText(
                                participants.firstOrNull()?.presence?.label.orEmpty(),
                                DoradoTokens.TYPE_CAPTION.dp,
                                color = colors.textSecondary,
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(colors.background)
                                .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 3.dp),
                        ) {
                            MsRichText(participants.firstOrNull()?.personal.orEmpty())
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 4.dp),
                        ) {
                            conversation?.messages?.forEach { message ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        EdgeCropText(
                                            if (message.mine) "you" else conversation.title,
                                            DoradoTokens.TYPE_LIST_SECONDARY.dp,
                                            color = if (message.mine) colors.accent else colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (options.showTimestamps) {
                                            Spacer(Modifier.width(6.dp))
                                            EdgeCropText(message.time, DoradoTokens.TYPE_CAPTION.dp, color = colors.textInactive)
                                        }
                                        message.media?.let {
                                            Spacer(Modifier.width(6.dp))
                                            EdgeCropText("· $it", DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                                        }
                                    }
                                    if (options.showEmoticons) {
                                        MsRichText(message.text)
                                    } else {
                                        EdgeCropText(message.text, DoradoTokens.TYPE_LIST.dp)
                                    }
                                }
                                MsHairline()
                            }
                        }
                        if (enabled) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .background(colors.elevated)
                                    .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 4.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) {
                                        MsLineField("type a message", input) { input = it }
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    MsAction("send", enabled = input.isNotBlank(), accent = true) { sendText() }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    MsAction("nudge") { sendText(nudge = true) }
                                    MsAction("share now playing") {
                                        sendText(
                                            media = nowPlaying?.let { "shared ${it.title} — ${it.artist}" }
                                                ?: "shared (nothing playing)",
                                        )
                                    }
                                    if (options.showEmoticons) {
                                        MsAction(":)") { input = "$input :)" }
                                    }
                                }
                            }
                        } else {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .background(colors.elevated)
                                    .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 5.dp),
                            ) {
                                EdgeCropText(
                                    "contact appears offline — input disabled",
                                    DoradoTokens.TYPE_CAPTION.dp,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }

                MsScreen.FRIENDS -> {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MsPivots(listOf("by status", "alphabetical"), friendsPivot) { friendsPivot = it }
                            Spacer(Modifier.weight(1f))
                            MsAction("options") { screen = MsScreen.OPTIONS }
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = DoradoTokens.EDGE.dp),
                        ) {
                            partitionGroups(contacts, MessengerSeed.groups).forEach { (group, members) ->
                                EdgeCropText(
                                    group,
                                    DoradoTokens.TYPE_CROSSBAR.dp,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                                )
                                sortContacts(members, friendsPivot == 0).forEach { contact ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = { openThreadFor(contact) },
                                                onLongClick = {
                                                    menus.show(
                                                        contact.name,
                                                        listOf(
                                                            MenuAction("send instant message") { openThreadFor(contact) },
                                                            MenuAction("block") {
                                                                contacts = contacts - contact
                                                                announce("${contact.name} blocked locally")
                                                            },
                                                            MenuAction("delete contact") {
                                                                contacts = contacts - contact
                                                                announce("${contact.name} removed from the local list")
                                                            },
                                                        ),
                                                    )
                                                },
                                            )
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        MsAvatar(contact.name, presence = contact.presence)
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            EdgeCropText(contact.name, DoradoTokens.TYPE_LIST.dp)
                                            MsRichText(contact.personal)
                                        }
                                        EdgeCropText(
                                            contact.presence.label,
                                            DoradoTokens.TYPE_CAPTION.dp,
                                            color = if (contact.presence == Presence.AVAILABLE) colors.accent else colors.textInactive,
                                        )
                                    }
                                    MsHairline()
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                MsScreen.SOCIAL -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                    ) {
                        MessengerSeed.social.forEach { (name, text) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                MsAvatar(name, presence = contactById(name)?.presence)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    EdgeCropText(name, DoradoTokens.TYPE_LIST.dp, fontWeight = FontWeight.SemiBold)
                                    MsRichText(text)
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 2.dp)) {
                                        MsAction("like") { announce("liked locally") }
                                        MsAction("comment") { announce("comments need the live service") }
                                    }
                                }
                            }
                            MsHairline()
                        }
                    }
                }

                MsScreen.OPTIONS -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                    ) {
                        EdgeCropText("personal", DoradoTokens.TYPE_CROSSBAR.dp, color = colors.textSecondary)
                        Box(Modifier.padding(top = 4.dp)) {
                            MsLineField("personal message", personalEdit) { personalEdit = it }
                        }
                        MsAction("save personal message", accent = true) {
                            account = account.copy(personalMessage = personalEdit)
                            announce("personal message saved")
                        }
                        Spacer(Modifier.height(6.dp))
                        MsPivots(
                            Presence.entries.map { if (it == Presence.OFFLINE) "appear offline" else it.label },
                            Presence.entries.indexOf(account.presence),
                        ) { account = account.copy(presence = Presence.entries[it]) }
                        Spacer(Modifier.height(8.dp))
                        EdgeCropText("messages", DoradoTokens.TYPE_CROSSBAR.dp, color = colors.textSecondary)
                        MsAction("emoticons: ${if (options.showEmoticons) "on" else "off"}") {
                            options = options.copy(showEmoticons = !options.showEmoticons)
                        }
                        MsAction("timestamps: ${if (options.showTimestamps) "on" else "off"}") {
                            options = options.copy(showTimestamps = !options.showTimestamps)
                        }
                        MsAction("auto-correct: ${if (options.autoCorrect) "on" else "off"}") {
                            options = options.copy(autoCorrect = !options.autoCorrect)
                        }
                        MsAction("sounds: ${if (options.sounds) "on" else "off"}") {
                            options = options.copy(sounds = !options.sounds)
                        }
                        MsAction("sort contacts by status: ${if (options.byStatus) "on" else "off"}") {
                            options = options.copy(byStatus = !options.byStatus)
                            friendsPivot = if (options.byStatus) 0 else 1
                        }
                        Spacer(Modifier.height(8.dp))
                        EdgeCropText("privacy", DoradoTokens.TYPE_CROSSBAR.dp, color = colors.textSecondary)
                        MsAction("about") { screen = MsScreen.ABOUT }
                        MsAction("sign out") {
                            screen = MsScreen.SIGNIN
                            password = ""
                            announce("signed out — local copy kept")
                        }
                        MsAction("forget this account") {
                            scope.launch { graph.appState.clear("messenger.account") }
                            account = MessengerSeed.account
                            email = ""
                            password = ""
                            screen = MsScreen.SIGNIN
                            announce("account removed")
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                MsScreen.ABOUT -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                    ) {
                        MockBody(
                            "a clean-room re-creation of windows live messenger as it shipped on the zune hd " +
                                "marketplace. the msnp service is long gone, so this build signs in to a local copy.",
                        )
                        Spacer(Modifier.height(8.dp))
                        MockBody(
                            "contacts, conversations and the social feed are synthetic. emoticons render as " +
                                "accent tokens; typing and presence never leave the device.",
                        )
                    }
                }
            }

            toast?.let { message ->
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(colors.elevated)
                        .border(0.5.dp, colors.border)
                        .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 5.dp),
                ) {
                    EdgeCropText(message, DoradoTokens.TYPE_LIST_SECONDARY.dp, color = colors.accent)
                }
            }
        }
    }
}
