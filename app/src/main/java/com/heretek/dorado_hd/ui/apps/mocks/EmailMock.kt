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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.delay

private enum class EmailScreen { ACCOUNTS, LIST, VIEWER, COMPOSE, FOLDERS, CONTACTS, SETUP, ABOUT }

private enum class EmailComposeMode(val label: String) {
    NEW("new message"),
    REPLY("reply"),
    REPLY_ALL("reply all"),
    FORWARD("forward"),
}

private enum class EmailField { TO, CC, BCC }

private data class EmailContact(val name: String, val address: String, val phone: String)

private val EMAIL_CONTACTS = listOf(
    EmailContact("alex", "alex@zmail.example", "555 0134"),
    EmailContact("dana", "dana@harbor.example", "555 0177"),
    EmailContact("jordan", "jordan@zmail.example", "555 0112"),
    EmailContact("maria", "maria@zmail.example", "555 0190"),
    EmailContact("riley", "riley@harbor.example", "555 0143"),
    EmailContact("sam", "sam@harbor.example", "555 0166"),
    EmailContact("wes", "wes@fieldnotes.example", "555 0121"),
)

@Composable
private fun EmailAction(
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
            // Keep a >=24dp touch target for 11sp action labels.
            .padding(horizontal = 5.dp, vertical = 6.dp),
    )
}

@Composable
private fun EmailPivots(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = LocalDoradoColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        labels.forEachIndexed { index, label ->
            EdgeCropText(
                text = label,
                fontSize = DoradoTokens.TYPE_CROSSBAR.dp,
                color = if (index == selected) colors.accent else colors.textSecondary,
                fontWeight = if (index == selected) FontWeight.Normal else FontWeight.Light,
                modifier = Modifier.clickable { onSelect(index) },
            )
        }
    }
}

@Composable
private fun EmailBanner(text: String) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.elevated)
            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 4.dp),
    ) {
        EdgeCropText(text = text, fontSize = DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
    }
}

@Composable
private fun EmailHairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(LocalDoradoColors.current.border),
    )
}

@Composable
private fun EmailLineField(
    placeholder: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
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
            textStyle = TextStyle(
                fontFamily = Selawik,
                fontSize = DoradoTokens.TYPE_LIST.sp,
                color = colors.textPrimary,
            ),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmailBlockField(
    placeholder: String,
    value: String,
    onChange: (String) -> Unit,
    height: androidx.compose.ui.unit.Dp,
) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .border(0.5.dp, colors.border)
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        if (value.isEmpty()) {
            EdgeCropText(placeholder, DoradoTokens.TYPE_LIST_SECONDARY.dp, color = colors.textInactive)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(
                fontFamily = Selawik,
                fontSize = DoradoTokens.TYPE_LIST.sp,
                color = colors.textPrimary,
                lineHeight = (DoradoTokens.TYPE_LIST * 1.3f).sp,
            ),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun EmailRecipientBlock(
    label: String,
    addresses: List<String>,
    onAdd: () -> Unit,
    onClear: (() -> Unit)?,
) {
    val colors = LocalDoradoColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        EdgeCropText(
            text = label,
            fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
            color = colors.textSecondary,
            modifier = Modifier.width(34.dp),
        )
        EdgeCropText(
            text = if (addresses.isEmpty()) "—" else addresses.joinToString(" · "),
            fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
            color = if (addresses.isEmpty()) colors.textInactive else colors.accent,
            modifier = Modifier.weight(1f),
        )
        EmailAction("+", onClick = onAdd)
        if (onClear != null && addresses.isNotEmpty()) EmailAction("clear", onClick = onClear)
    }
}

@Composable
private fun EmailMailRow(
    mail: Mail,
    unread: Boolean,
    selectMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectMode) onToggleSelect() else onOpen() },
                onLongClick = onToggleSelect,
            )
            .padding(vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (selectMode) {
                Box(
                    Modifier
                        .size(15.dp)
                        .border(0.5.dp, if (selected) colors.accent else colors.border),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        EdgeCropText("x", DoradoTokens.TYPE_CAPTION.dp, color = colors.accent)
                    }
                }
                Spacer(Modifier.width(6.dp))
            }
            EdgeCropText(
                text = mail.from,
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = if (unread) colors.textPrimary else colors.textSecondary,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            EdgeCropText(mail.received, DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (selectMode) Spacer(Modifier.width(21.dp))
            EdgeCropText(
                text = mail.subject,
                fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
                color = colors.accent,
                modifier = Modifier.weight(1f),
            )
            val flags = buildList {
                if (mail.replied) add("re")
                if (mail.urgent) add("!")
                if (mail.attachment != null) add("att")
            }.joinToString(" ")
            if (flags.isNotEmpty()) {
                EdgeCropText(flags, DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
            }
        }
        if (selectMode) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(21.dp))
                EdgeCropText(
                    text = mail.body.lineSequence().firstOrNull().orEmpty(),
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            EdgeCropText(
                text = mail.body.lineSequence().firstOrNull().orEmpty(),
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = colors.textSecondary,
            )
        }
    }
    EmailHairline()
}

/**
 * Offline mail client for the dead Exchange/Live backends. The seeded mailbox
 * is browsable, read state and one draft per account persist in `graph.appState`,
 * and sending appends to a local outbox labelled offline.
 */
@Composable
fun EmailApp() {
    val graph = LocalDoradoGraph.current
    val colors = LocalDoradoColors.current

    var loaded by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(EmailScreen.ACCOUNTS) }
    var chooserAbout by remember { mutableStateOf(false) }
    var activeId by remember { mutableStateOf(MailSeed.accounts.first().id) }
    var folder by remember { mutableStateOf(MailFolder.INBOX) }
    var pivot by remember { mutableStateOf(MailPivot.ALL) }
    var readState by remember { mutableStateOf(emptyMap<Long, Boolean>()) }
    var deleted by remember { mutableStateOf(emptySet<Long>()) }
    var drafts by remember { mutableStateOf(emptyMap<String, MailDraft>()) }
    var outbox by remember { mutableStateOf(emptyList<Mail>()) }
    var signature by remember { mutableStateOf("sent from my zune hd — offline") }
    var composeMode by remember { mutableStateOf(EmailComposeMode.NEW) }
    var viewerId by remember { mutableStateOf<Long?>(null) }
    var viewerRecipients by remember { mutableStateOf(false) }
    var pickingField by remember { mutableStateOf<EmailField?>(null) }
    var returnTo by remember { mutableStateOf(EmailScreen.LIST) }
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var discardAsk by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var setupType by remember { mutableStateOf(0) }
    var setupAddress by remember { mutableStateOf("") }
    var setupServer by remember { mutableStateOf("") }
    var setupSsl by remember { mutableStateOf(true) }

    val account = MailSeed.accounts.first { it.id == activeId }
    val allMails = MailSeed.mails + outbox
    val list = Mailbox.visible(allMails, activeId, folder, pivot, readState, deleted)
    val viewer = allMails.firstOrNull { it.id == viewerId }

    fun updateDraft(transform: (MailDraft) -> MailDraft) {
        val current = drafts[activeId] ?: MailDraft(activeId)
        drafts = drafts + (activeId to transform(current).copy(accountId = activeId, touched = true))
    }

    fun announce(message: String) {
        toast = message
    }

    fun openMail(mail: Mail) {
        viewerId = mail.id
        viewerRecipients = false
        readState = Mailbox.markRead(setOf(mail.id), readState)
        screen = EmailScreen.VIEWER
    }

    fun startCompose(mode: EmailComposeMode, source: Mail?) {
        val base = when (mode) {
            EmailComposeMode.NEW -> MailDraft(activeId)
            EmailComposeMode.REPLY -> MailDraft(
                accountId = activeId,
                to = source?.let { listOf(it.fromAddress) } ?: emptyList(),
                subject = source?.subject?.let { if (it.startsWith("re:", true)) it else "re: $it" } ?: "",
                quotedFrom = source?.let { "> ${it.from}: ${it.body.lineSequence().firstOrNull().orEmpty()}" },
            )
            EmailComposeMode.REPLY_ALL -> MailDraft(
                accountId = activeId,
                to = source?.let { listOf(it.fromAddress) + it.to.filter { a -> a != account.address } } ?: emptyList(),
                cc = source?.cc ?: emptyList(),
                subject = source?.subject?.let { if (it.startsWith("re:", true)) it else "re: $it" } ?: "",
                quotedFrom = source?.let { "> ${it.from}: ${it.body.lineSequence().firstOrNull().orEmpty()}" },
            )
            EmailComposeMode.FORWARD -> MailDraft(
                accountId = activeId,
                subject = source?.subject?.let { if (it.startsWith("fwd:", true)) it else "fwd: $it" } ?: "",
                quotedFrom = source?.let { "> forwarded from ${it.from}: ${it.body}" },
            )
        }
        drafts = drafts + (activeId to base)
        composeMode = mode
        screen = EmailScreen.COMPOSE
    }

    fun sendDraft() {
        val current = drafts[activeId] ?: MailDraft(activeId)
        val check = validateSend(current.to, current.cc, current.bcc)
        if (!check.ok) {
            announce(check.error ?: "cannot send")
            return
        }
        val signatureLine = if (current.signature) "\n\n$signature" else ""
        val quoted = current.quotedFrom?.let { "\n\n$it" } ?: ""
        val sent = Mail(
            id = (allMails.maxOfOrNull { it.id } ?: 0L) + 1,
            accountId = activeId,
            folder = MailFolder.SENT,
            from = "you",
            fromAddress = account.address,
            to = current.to,
            cc = current.cc,
            subject = current.subject.ifBlank { "(no subject)" },
            body = current.body + signatureLine + quoted,
            received = "now",
            unread = false,
        )
        outbox = listOf(sent) + outbox
        drafts = drafts - activeId
        folder = MailFolder.SENT
        pivot = MailPivot.ALL
        screen = EmailScreen.LIST
        announce("saved to the offline outbox")
    }

    fun back() {
        when (screen) {
            EmailScreen.ACCOUNTS -> graph.nav.pop()
            EmailScreen.LIST -> screen = EmailScreen.ACCOUNTS
            EmailScreen.VIEWER -> screen = EmailScreen.LIST
            EmailScreen.COMPOSE -> {
                val current = drafts[activeId] ?: MailDraft(activeId)
                if (current.touched && !current.isBlank()) discardAsk = true else screen = EmailScreen.LIST
            }
            EmailScreen.FOLDERS, EmailScreen.CONTACTS, EmailScreen.SETUP, EmailScreen.ABOUT -> screen = returnTo
        }
    }

    LaunchedEffect(Unit) {
        readState = MailCodec.decodeReadState(graph.appState.get("email.read"))
        deleted = MailCodec.decodeIds(graph.appState.get("email.deleted"))
        graph.appState.get("email.active")?.let { stored ->
            if (MailSeed.accounts.any { it.id == stored }) activeId = stored
        }
        graph.appState.get("email.signature")?.let { if (it.isNotBlank()) signature = it }
        drafts = MailSeed.accounts.mapNotNull { candidate ->
            MailCodec.decodeDraft(graph.appState.get("email.draft.${candidate.id}"))?.let { candidate.id to it }
        }.toMap()
        loaded = true
    }

    LaunchedEffect(readState, deleted, loaded) {
        if (!loaded) return@LaunchedEffect
        graph.appState.put("email.read", MailCodec.encodeReadState(readState))
        graph.appState.put("email.deleted", MailCodec.encodeIds(deleted))
    }

    LaunchedEffect(activeId, loaded) {
        if (loaded) graph.appState.put("email.active", activeId)
    }

    LaunchedEffect(drafts, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(400)
        MailSeed.accounts.forEach { candidate ->
            graph.appState.put(
                "email.draft.${candidate.id}",
                MailCodec.encodeDraft(drafts[candidate.id] ?: MailDraft(candidate.id)),
            )
        }
    }

    LaunchedEffect(signature, loaded) {
        if (loaded) graph.appState.put("email.signature", signature)
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2200)
            toast = null
        }
    }

    val title = when (screen) {
        EmailScreen.ACCOUNTS -> if (chooserAbout) "email — about" else "email"
        EmailScreen.LIST -> "${folder.label} — ${account.label}"
        EmailScreen.VIEWER -> "message"
        EmailScreen.COMPOSE -> composeMode.label
        EmailScreen.FOLDERS -> "folders"
        EmailScreen.CONTACTS -> "contacts"
        EmailScreen.SETUP -> "account setup"
        EmailScreen.ABOUT -> "about"
    }

    DetailScaffold(title = title, onBack = { back() }) {
        Box(Modifier.fillMaxSize()) {
            when (screen) {
                EmailScreen.ACCOUNTS -> {
                    Column(Modifier.fillMaxSize()) {
                        EmailBanner("offline mailbox — messages stay on this device")
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
                        ) {
                            EmailPivots(listOf("accounts", "about"), if (chooserAbout) 1 else 0) {
                                chooserAbout = it == 1
                            }
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = DoradoTokens.EDGE.dp),
                        ) {
                            if (!chooserAbout) {
                                MailSeed.accounts.forEach { candidate ->
                                    val unread = Mailbox.unreadCount(allMails, candidate.id, readState, deleted)
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                activeId = candidate.id
                                                folder = MailFolder.INBOX
                                                pivot = MailPivot.ALL
                                                screen = EmailScreen.LIST
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            Modifier
                                                .size(26.dp)
                                                .background(colors.tile),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            EdgeCropText(
                                                candidate.mark,
                                                DoradoTokens.TYPE_LIST.dp,
                                                color = colors.accent,
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            EdgeCropText(
                                                candidate.address,
                                                DoradoTokens.TYPE_LIST.dp,
                                                color = colors.textPrimary,
                                                modifier = Modifier.padding(bottom = 1.dp),
                                            )
                                            EdgeCropText(
                                                candidate.label,
                                                DoradoTokens.TYPE_CAPTION.dp,
                                                color = colors.textSecondary,
                                            )
                                        }
                                        if (unread > 0) {
                                            EdgeCropText(
                                                "$unread unread",
                                                DoradoTokens.TYPE_LIST_SECONDARY.dp,
                                                color = colors.accent,
                                            )
                                        }
                                    }
                                    EmailHairline()
                                }
                                Row(
                                    Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    EmailAction("settings", accent = true) {
                                        returnTo = EmailScreen.ACCOUNTS
                                        screen = EmailScreen.SETUP
                                    }
                                    EmailAction("setup a new account") {
                                        returnTo = EmailScreen.ACCOUNTS
                                        screen = EmailScreen.SETUP
                                    }
                                }
                            } else {
                                MockBody(
                                    "this client used to sync windows live, google and exchange mail. " +
                                        "those servers are gone, so the inbox you see is a frozen local copy. " +
                                        "everything still reads, replies and sends — into an outbox on this device.",
                                )
                                Spacer(Modifier.height(8.dp))
                                MockBody(
                                    "unread state, deletions and one draft per account are kept locally. " +
                                        "no network connection is attempted.",
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                EmailScreen.LIST -> {
                    Column(Modifier.fillMaxSize()) {
                        EmailBanner("offline mailbox — nothing is syncing")
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            EmailPivots(MailPivot.entries.map { it.label }, pivot.ordinal) {
                                pivot = MailPivot.entries[it]
                            }
                        }
                        // Actions live on their own line: four of them plus the
                        // pivots overflowed the adaptive width (V-02 class).
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (selectMode) {
                                EmailAction("read") {
                                    readState = Mailbox.markRead(selected, readState)
                                }
                                EmailAction("unread") {
                                    readState = Mailbox.markUnread(selected, readState)
                                }
                                EmailAction("delete") {
                                    deleted = deleted + selected
                                    selected = emptySet()
                                }
                                EmailAction("cancel") {
                                    selectMode = false
                                    selected = emptySet()
                                }
                            } else {
                                EmailAction("compose", accent = true) { startCompose(EmailComposeMode.NEW, null) }
                                EmailAction("select") { selectMode = true }
                                EmailAction("folders") {
                                    returnTo = EmailScreen.LIST
                                    screen = EmailScreen.FOLDERS
                                }
                                EmailAction("contacts") {
                                    returnTo = EmailScreen.LIST
                                    screen = EmailScreen.CONTACTS
                                }
                            }
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = DoradoTokens.EDGE.dp),
                        ) {
                            if (list.isEmpty()) {
                                Spacer(Modifier.height(24.dp))
                                EdgeCropText(
                                    "nothing in this view",
                                    DoradoTokens.TYPE_LIST.dp,
                                    color = colors.textSecondary,
                                )
                                Spacer(Modifier.height(3.dp))
                                EdgeCropText(
                                    "try another folder or pivot — the seeded mailbox has sample mail.",
                                    DoradoTokens.TYPE_CAPTION.dp,
                                    color = colors.textInactive,
                                )
                            }
                            list.forEach { mail ->
                                EmailMailRow(
                                    mail = mail,
                                    unread = Mailbox.isUnread(mail, readState),
                                    selectMode = selectMode,
                                    selected = mail.id in selected,
                                    onOpen = { openMail(mail) },
                                    onToggleSelect = {
                                        if (mail.id in selected) {
                                            selected = selected - mail.id
                                        } else {
                                            selectMode = true
                                            selected = selected + mail.id
                                        }
                                    },
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                EmailScreen.VIEWER -> {
                    val mail = viewer
                    if (mail == null) {
                        screen = EmailScreen.LIST
                    } else {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
                        ) {
                            EdgeCropText(
                                MailCodec.formatAddress(mail.from, mail.fromAddress),
                                DoradoTokens.TYPE_NOW_META.dp,
                                color = colors.textPrimary,
                            )
                            EdgeCropText(
                                mail.subject,
                                DoradoTokens.TYPE_LIST.dp,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                            EdgeCropText(mail.received, DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                            Spacer(Modifier.height(4.dp))
                            EdgeCropText(
                                "to ${mail.to.joinToString(", ")}",
                                DoradoTokens.TYPE_CAPTION.dp,
                                color = colors.textSecondary,
                            )
                            if (mail.cc.isNotEmpty()) {
                                if (viewerRecipients) {
                                    EdgeCropText(
                                        "cc ${mail.cc.joinToString(", ")}",
                                        DoradoTokens.TYPE_CAPTION.dp,
                                        color = colors.textSecondary,
                                    )
                                } else {
                                    EmailAction("show cc") { viewerRecipients = true }
                                }
                            }
                            mail.attachment?.let { attachment ->
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .border(0.5.dp, colors.border)
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                ) {
                                    EdgeCropText(
                                        "attachment: ${attachment.name} · ${attachment.size}",
                                        DoradoTokens.TYPE_LIST_SECONDARY.dp,
                                        color = colors.accent,
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            MockBody(mail.body)
                            Spacer(Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                EmailAction("reply", accent = true) { startCompose(EmailComposeMode.REPLY, mail) }
                                EmailAction("reply all") { startCompose(EmailComposeMode.REPLY_ALL, mail) }
                                EmailAction("forward") { startCompose(EmailComposeMode.FORWARD, mail) }
                                EmailAction("mark unread") {
                                    readState = Mailbox.markUnread(setOf(mail.id), readState)
                                    screen = EmailScreen.LIST
                                }
                                EmailAction("delete") {
                                    deleted = deleted + mail.id
                                    screen = EmailScreen.LIST
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.padding(top = 6.dp),
                            ) {
                                val index = list.indexOfFirst { it.id == mail.id }
                                EmailAction("older", enabled = index in 0 until list.lastIndex) {
                                    list.getOrNull(Mailbox.step(index, list.size, 1))?.let { openMail(it) }
                                }
                                EmailAction("newer", enabled = index > 0) {
                                    list.getOrNull(Mailbox.step(index, list.size, -1))?.let { openMail(it) }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                EmailScreen.COMPOSE -> {
                    val current = drafts[activeId] ?: MailDraft(activeId)
                    val sendCheck = validateSend(current.to, current.cc, current.bcc)
                    Column(Modifier.fillMaxSize()) {
                        EmailBanner("offline — sending stores this in the local outbox")
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
                        ) {
                            EmailRecipientBlock("to", current.to, {
                                pickingField = EmailField.TO
                                returnTo = EmailScreen.COMPOSE
                                screen = EmailScreen.CONTACTS
                            }, {
                                updateDraft { it.copy(to = emptyList()) }
                            })
                            if (current.cc.isNotEmpty() || pickingField == EmailField.CC) {
                                EmailRecipientBlock("cc", current.cc, {
                                    pickingField = EmailField.CC
                                    returnTo = EmailScreen.COMPOSE
                                    screen = EmailScreen.CONTACTS
                                }, {
                                    updateDraft { it.copy(cc = emptyList()) }
                                })
                            }
                            if (current.bcc.isNotEmpty() || pickingField == EmailField.BCC) {
                                EmailRecipientBlock("bcc", current.bcc, {
                                    pickingField = EmailField.BCC
                                    returnTo = EmailScreen.COMPOSE
                                    screen = EmailScreen.CONTACTS
                                }, {
                                    updateDraft { it.copy(bcc = emptyList()) }
                                })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                if (current.cc.isEmpty() && pickingField != EmailField.CC) {
                                    EmailAction("add cc") { pickingField = EmailField.CC }
                                }
                                if (current.bcc.isEmpty() && pickingField != EmailField.BCC) {
                                    EmailAction("add bcc") { pickingField = EmailField.BCC }
                                }
                            }
                            EmailLineField("subject", current.subject, onChange = { value ->
                                updateDraft { it.copy(subject = value) }
                            })
                            Spacer(Modifier.height(6.dp))
                            EmailBlockField("message", current.body, { value ->
                                updateDraft { it.copy(body = value) }
                            }, height = 84.dp)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                EmailAction(
                                    if (current.signature) "signature on" else "signature off",
                                    accent = current.signature,
                                ) { updateDraft { it.copy(signature = !it.signature) } }
                                EmailAction("priority ${current.priority.label}") {
                                    val next = MailPriority.entries[(current.priority.ordinal + 1) % MailPriority.entries.size]
                                    updateDraft { it.copy(priority = next) }
                                }
                            }
                            if (current.quotedFrom != null) {
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .border(0.5.dp, colors.border)
                                        .padding(6.dp),
                                ) {
                                    EdgeCropText(
                                        "quoted original attached",
                                        DoradoTokens.TYPE_LIST_SECONDARY.dp,
                                        color = colors.textSecondary,
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                EmailAction("send", enabled = sendCheck.ok, accent = true) { sendDraft() }
                                EmailAction("close") { back() }
                            }
                            if (!sendCheck.ok) {
                                EdgeCropText(
                                    sendCheck.error.orEmpty(),
                                    DoradoTokens.TYPE_CAPTION.dp,
                                    color = colors.textInactive,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                EmailScreen.FOLDERS -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                    ) {
                        MockBody("synced locally")
                        Spacer(Modifier.height(4.dp))
                        MailSeed.folders.filter { it.second }.forEach { (mailFolder, _) ->
                            val unread = Mailbox.unreadCount(allMails, activeId, readState, deleted, mailFolder)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        folder = mailFolder
                                        pivot = MailPivot.ALL
                                        screen = EmailScreen.LIST
                                    }
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                EdgeCropText(mailFolder.label, DoradoTokens.TYPE_LIST.dp, modifier = Modifier.weight(1f))
                                if (unread > 0) {
                                    EdgeCropText("$unread", DoradoTokens.TYPE_LIST_SECONDARY.dp, color = colors.accent)
                                }
                            }
                            EmailHairline()
                        }
                        Spacer(Modifier.height(10.dp))
                        MockBody("not synced")
                        Spacer(Modifier.height(4.dp))
                        MailSeed.folders.filterNot { it.second }.forEach { (mailFolder, _) ->
                            EdgeCropText(
                                "${mailFolder.label} — unavailable offline",
                                DoradoTokens.TYPE_LIST.dp,
                                color = colors.textInactive,
                                modifier = Modifier.padding(vertical = 7.dp),
                            )
                            EmailHairline()
                        }
                    }
                }

                EmailScreen.CONTACTS -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                    ) {
                        MockBody("local address book")
                        Spacer(Modifier.height(4.dp))
                        EMAIL_CONTACTS.forEach { contact ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val field = pickingField
                                        if (field != null) {
                                            updateDraft { draftValue ->
                                                when (field) {
                                                    EmailField.TO -> draftValue.copy(to = draftValue.to + contact.address)
                                                    EmailField.CC -> draftValue.copy(cc = draftValue.cc + contact.address)
                                                    EmailField.BCC -> draftValue.copy(bcc = draftValue.bcc + contact.address)
                                                }
                                            }
                                            pickingField = null
                                            screen = EmailScreen.COMPOSE
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                            ) {
                                EdgeCropText(contact.name, DoradoTokens.TYPE_LIST.dp)
                                EdgeCropText(
                                    "${contact.address} · ${contact.phone}",
                                    DoradoTokens.TYPE_CAPTION.dp,
                                    color = colors.textSecondary,
                                )
                            }
                            EmailHairline()
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                EmailScreen.SETUP -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                    ) {
                        MockBody("account settings — the wizard cannot reach a server, so this form is local only")
                        Spacer(Modifier.height(6.dp))
                        EmailPivots(listOf("personal mail", "exchange"), setupType) { setupType = it }
                        Spacer(Modifier.height(8.dp))
                        EmailLineField("address", setupAddress, onChange = { setupAddress = it })
                        Spacer(Modifier.height(6.dp))
                        EmailLineField("mail server", setupServer, onChange = { setupServer = it })
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EmailAction(if (setupSsl) "ssl on" else "ssl off", accent = setupSsl) { setupSsl = !setupSsl }
                            Spacer(Modifier.width(12.dp))
                            EmailAction("signature: $signature", accent = true) {
                                signature = when (signature) {
                                    "sent from my zune hd — offline" -> "— $account.label"
                                    else -> "sent from my zune hd — offline"
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            EmailAction("save", accent = true) { announce("account saved locally") }
                            EmailAction("re-authenticate") { announce("no server to authenticate with") }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                EmailScreen.ABOUT -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                    ) {
                        MockBody(
                            "a clean-room re-creation of the mail app that shipped on the zune hd marketplace. " +
                                "the original service was switched off, so this build opens a frozen local mailbox.",
                        )
                        Spacer(Modifier.height(8.dp))
                        MockBody(
                            "no accounts are contacted. no credentials are requested. all messages, contacts and " +
                                "folders here are synthetic.",
                        )
                        Spacer(Modifier.height(8.dp))
                        EdgeCropText(
                            "version 1.0 — offline build",
                            DoradoTokens.TYPE_CAPTION.dp,
                            color = colors.textInactive,
                        )
                    }
                }
            }

            if (discardAsk) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colors.background.copy(alpha = 0.88f))
                        .clickable { discardAsk = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .background(colors.elevated)
                            .border(0.5.dp, colors.border)
                            .padding(DoradoTokens.EDGE.dp),
                    ) {
                        EdgeCropText("discard this draft?", DoradoTokens.TYPE_LIST.dp)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            EmailAction("discard", accent = true) {
                                drafts = drafts - activeId
                                discardAsk = false
                                screen = EmailScreen.LIST
                            }
                            EmailAction("keep editing") { discardAsk = false }
                        }
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
