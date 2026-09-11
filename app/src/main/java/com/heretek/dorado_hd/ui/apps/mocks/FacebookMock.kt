package com.heretek.dorado_hd.ui.apps.mocks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import kotlinx.coroutines.delay

private enum class FbScreen {
    LOGIN, HOME, PROFILE, FRIENDS, MESSAGES, THREAD, COMPOSE, NOTIFICATIONS,
    SETTINGS, PHOTO, REQUESTS, ABOUT, FRIEND,
}

@Composable
private fun FbAction(
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
private fun FbPivots(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = LocalDoradoColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
private fun FbBanner(text: String) {
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
private fun FbHairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(LocalDoradoColors.current.border),
    )
}

@Composable
private fun FbAvatar(name: String, size: Dp = 26.dp) {
    val colors = LocalDoradoColors.current
    Box(Modifier.size(size).background(colors.tile), contentAlignment = Alignment.Center) {
        EdgeCropText(
            text = name.take(1).lowercase(),
            fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
            color = colors.accent,
        )
    }
}

@Composable
private fun FbLinkText(text: String, modifier: Modifier = Modifier, fontSize: Dp = DoradoTokens.TYPE_LIST.dp) {
    val colors = LocalDoradoColors.current
    val annotated = remember(text) {
        buildAnnotatedString {
            splitFbLinks(text).forEach { token ->
                if (token.kind == FbTokenKind.LINK) {
                    withStyle(SpanStyle(color = colors.accent)) { append(token.text) }
                } else {
                    append(token.text)
                }
            }
        }
    }
    BasicText(
        text = annotated,
        style = TextStyle(
            fontFamily = Selawik,
            fontSize = fontSize.value.sp,
            color = colors.textPrimary,
            lineHeight = (fontSize.value * 1.3f).sp,
        ),
        modifier = modifier,
    )
}

@Composable
private fun FbLineField(placeholder: String, value: String, onChange: (String) -> Unit) {
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

@Composable
private fun FbBlockField(placeholder: String, value: String, onChange: (String) -> Unit, height: Dp) {
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
private fun FbPostRow(
    post: FbPost,
    liked: Boolean,
    likeCount: Int,
    onToggleLike: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        FbAvatar(post.author)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EdgeCropText(
                    post.author,
                    DoradoTokens.TYPE_LIST.dp,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                EdgeCropText(post.age, DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
            }
            FbLinkText(post.message, modifier = Modifier.padding(vertical = 3.dp))
            if (post.attachment != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .background(colors.tile),
                    contentAlignment = Alignment.Center,
                ) {
                    EdgeCropText(post.attachment, DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                }
                Spacer(Modifier.height(3.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FbAction(
                    label = "$likeCount likes",
                    accent = liked,
                    onClick = onToggleLike,
                )
                EdgeCropText(" · ", DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                EdgeCropText("${post.comments} comments", DoradoTokens.TYPE_LIST_SECONDARY.dp, color = colors.textSecondary)
                post.album?.let {
                    Spacer(Modifier.weight(1f))
                    EdgeCropText(it, DoradoTokens.TYPE_CAPTION.dp, color = colors.textInactive)
                }
            }
        }
    }
    FbHairline()
}

/**
 * Offline Facebook client for the dead Graph-era backend. Feed, friends, wall
 * and mail render from a fixed local snapshot; likes, read state, request
 * decisions and the compose draft persist in `graph.appState`.
 */
@Composable
fun FacebookApp() {
    val graph = LocalDoradoGraph.current
    val colors = LocalDoradoColors.current
    val nowPlaying by graph.controller.nowPlaying.collectAsState()

    var loaded by remember { mutableStateOf(false) }
    var loggedIn by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(FbScreen.LOGIN) }
    var tab by remember { mutableStateOf(0) }
    var homePivot by remember { mutableStateOf(0) }
    var profilePivot by remember { mutableStateOf(0) }
    var profileOwner by remember { mutableStateOf<String?>(null) }
    var friendsGrid by remember { mutableStateOf(false) }
    var friendsQuery by remember { mutableStateOf("") }
    var liked by remember { mutableStateOf(emptySet<Long>()) }
    var read by remember { mutableStateOf(emptySet<Long>()) }
    var deletedThreads by remember { mutableStateOf(emptySet<Long>()) }
    var decisions by remember { mutableStateOf(emptyMap<String, Boolean>()) }
    var composeDraft by remember { mutableStateOf(FbCompose()) }
    var statusText by remember { mutableStateOf("") }
    var myPosts by remember { mutableStateOf(emptyList<FbPost>()) }
    var localReplies by remember { mutableStateOf(emptyMap<Long, List<FbMessage>>()) }
    var sentThreads by remember { mutableStateOf(emptyList<FbThread>()) }
    var replyText by remember { mutableStateOf("") }
    var threadId by remember { mutableStateOf<Long?>(null) }
    var msgFolder by remember { mutableStateOf(FbFolder.INBOX) }
    var feedPage by remember { mutableStateOf(0) }
    var albumIndex by remember { mutableStateOf(0) }
    var photoIndex by remember { mutableStateOf(0) }
    var notificationsRead by remember { mutableStateOf(false) }
    var settingsPivot by remember { mutableStateOf(0) }
    var autoReload by remember { mutableStateOf(true) }
    var shakeReload by remember { mutableStateOf(false) }
    var discardAsk by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var signingIn by remember { mutableStateOf(false) }

    fun announce(message: String) {
        toast = message
    }

    fun likeCount(post: FbPost): Int = post.likes + if (post.id in liked) 1 else 0

    fun toggleLike(post: FbPost) {
        liked = if (post.id in liked) liked - post.id else liked + post.id
    }

    fun openThread(id: Long) {
        threadId = id
        read = FbMail.markRead(setOf(id), read)
        screen = FbScreen.THREAD
    }

    fun openCompose() {
        composeDraft = FbCompose()
        screen = FbScreen.COMPOSE
    }

    fun publishStatus() {
        val body = statusText.trim()
        if (body.isEmpty()) return
        val post = FbPost(
            id = (myPosts.maxOfOrNull { it.id } ?: 100L) + 1,
            author = FacebookSeed.user,
            age = "now",
            message = body,
            likes = 0,
            comments = 0,
        )
        myPosts = listOf(post) + myPosts
        statusText = ""
        homePivot = 1
        announce("posted to the offline snapshot")
    }

    fun publishNowPlaying() {
        val track = nowPlaying
        statusText = if (track != null) {
            "listening to ${track.title} — ${track.artist}"
        } else {
            "listening to the quiet"
        }
    }

    fun sendMessage() {
        val check = validateFbCompose(composeDraft.to, composeDraft.subject, composeDraft.body)
        if (!check.ok) {
            announce(check.error ?: "cannot send")
            return
        }
        val id = (FacebookSeed.threads.maxOfOrNull { it.id } ?: 0L) + 100
        val thread = FbThread(
            id = id,
            folder = FbFolder.SENT,
            with = composeDraft.to.first(),
            subject = composeDraft.subject,
            snippet = composeDraft.body.lineSequence().first(),
            time = "now",
            messages = listOf(FbMessage("you", composeDraft.body, "now")),
        )
        sentThreads = listOf(thread) + sentThreads
        composeDraft = FbCompose()
        msgFolder = FbFolder.SENT
        screen = FbScreen.MESSAGES
        announce("message saved locally — service offline")
    }

    fun back() {
        when (screen) {
            FbScreen.LOGIN -> graph.nav.pop()
            FbScreen.HOME -> graph.nav.pop()
            FbScreen.PROFILE, FbScreen.FRIENDS, FbScreen.MESSAGES -> {
                screen = FbScreen.HOME
                tab = 0
            }
            FbScreen.THREAD -> {
                threadId = null
                screen = FbScreen.MESSAGES
            }
            FbScreen.COMPOSE -> {
                if (composeDraft.isBlank()) {
                    screen = FbScreen.MESSAGES
                } else {
                    discardAsk = true
                }
            }
            FbScreen.FRIEND -> {
                screen = FbScreen.FRIENDS
                profileOwner = null
            }
            FbScreen.PHOTO -> screen = FbScreen.PROFILE
            FbScreen.REQUESTS -> screen = FbScreen.FRIENDS
            FbScreen.ABOUT -> {
                // Reset the settings pivot so returning from About does not
                // immediately re-trigger the About destination (A-11).
                settingsPivot = 0
                screen = FbScreen.SETTINGS
            }
            FbScreen.NOTIFICATIONS, FbScreen.SETTINGS -> screen = FbScreen.HOME
        }
    }

    LaunchedEffect(Unit) {
        liked = FbCodec.decodeIds(graph.appState.get("facebook.liked"))
        read = FbCodec.decodeIds(graph.appState.get("facebook.read"))
        deletedThreads = FbCodec.decodeIds(graph.appState.get("facebook.deleted"))
        decisions = FbCodec.decodeDecisions(graph.appState.get("facebook.requests"))
        composeDraft = FbCodec.decodeCompose(graph.appState.get("facebook.draft")) ?: FbCompose()
        notificationsRead = graph.appState.get("facebook.notifications.read") == "1"
        autoReload = graph.appState.get("facebook.autoReload") != "0"
        shakeReload = graph.appState.get("facebook.shake") == "1"
        loggedIn = graph.appState.get("facebook.user") != null
        if (loggedIn) screen = FbScreen.HOME
        loaded = true
    }

    LaunchedEffect(liked, read, deletedThreads, decisions, loaded) {
        if (!loaded) return@LaunchedEffect
        graph.appState.put("facebook.liked", FbCodec.encodeIds(liked))
        graph.appState.put("facebook.read", FbCodec.encodeIds(read))
        graph.appState.put("facebook.deleted", FbCodec.encodeIds(deletedThreads))
        graph.appState.put("facebook.requests", FbCodec.encodeDecisions(decisions))
    }

    LaunchedEffect(composeDraft, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(400)
        graph.appState.put("facebook.draft", FbCodec.encodeCompose(composeDraft))
    }

    LaunchedEffect(notificationsRead, autoReload, shakeReload, loaded) {
        if (!loaded) return@LaunchedEffect
        graph.appState.put("facebook.notifications.read", if (notificationsRead) "1" else "0")
        graph.appState.put("facebook.autoReload", if (autoReload) "1" else "0")
        graph.appState.put("facebook.shake", if (shakeReload) "1" else "0")
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2200)
            toast = null
        }
    }

    val allThreads = FacebookSeed.threads + sentThreads
    val inShell = screen == FbScreen.HOME || screen == FbScreen.PROFILE ||
        screen == FbScreen.FRIENDS || screen == FbScreen.MESSAGES

    val title = when (screen) {
        FbScreen.LOGIN -> "facebook"
        FbScreen.HOME -> "facebook"
        FbScreen.PROFILE -> if (profileOwner == null) "profile" else profileOwner ?: "profile"
        FbScreen.FRIENDS -> "friends"
        FbScreen.MESSAGES -> "messages"
        FbScreen.THREAD -> "message"
        FbScreen.COMPOSE -> "new message"
        FbScreen.NOTIFICATIONS -> "notifications"
        FbScreen.SETTINGS -> "settings"
        FbScreen.PHOTO -> "photos"
        FbScreen.REQUESTS -> "friend requests"
        FbScreen.FRIEND -> profileOwner ?: "profile"
        FbScreen.ABOUT -> "about"
    }

    DetailScaffold(title = title, onBack = { back() }) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (screen) {
                FbScreen.LOGIN -> {
                    Column(Modifier.fillMaxSize()) {
                        FbBanner("offline snapshot — the service closed, this profile is local")
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 48.dp, vertical = 14.dp),
                        ) {
                            EdgeCropText("facebook", DoradoTokens.TYPE_NOW_TITLE.dp, color = colors.accent)
                            Spacer(Modifier.height(4.dp))
                            EdgeCropText(
                                "sign in to the archived snapshot",
                                DoradoTokens.TYPE_LIST_SECONDARY.dp,
                                color = colors.textSecondary,
                            )
                            Spacer(Modifier.height(10.dp))
                            FbLineField("email", loginEmail) { loginEmail = it }
                            Spacer(Modifier.height(6.dp))
                            FbLineField("password", loginPassword) { loginPassword = it }
                            Spacer(Modifier.height(10.dp))
                            val canLogin = loginEmail.isNotBlank() && loginPassword.isNotBlank()
                            FbAction(
                                if (signingIn) "signing in…" else "log in",
                                enabled = canLogin && !signingIn,
                                accent = true,
                            ) {
                                signingIn = true
                            }
                            Spacer(Modifier.height(8.dp))
                            EdgeCropText(
                                "any credentials open the local snapshot; nothing is transmitted.",
                                DoradoTokens.TYPE_CAPTION.dp,
                                color = colors.textInactive,
                            )
                        }
                    }
                    LaunchedEffect(signingIn) {
                        if (signingIn) {
                            delay(700)
                            loggedIn = true
                            graph.appState.put("facebook.user", loginEmail.ifBlank { "you" })
                            screen = FbScreen.HOME
                            signingIn = false
                        }
                    }
                }

                FbScreen.HOME -> {
                    Column(Modifier.fillMaxSize()) {
                        FbBanner("offline snapshot — posts and likes never leave this device")
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = DoradoTokens.EDGE.dp),
                        ) {
                            Row(
                                Modifier.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FbPivots(listOf("news feed", "status updates", "photos"), homePivot) { homePivot = it }
                            }
                            if (homePivot != 2) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.weight(1f)) {
                                        FbLineField("what's on your mind?", statusText) { statusText = it }
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    FbAction("now playing") { publishNowPlaying() }
                                    FbAction("post", enabled = statusText.isNotBlank(), accent = true) {
                                        publishStatus()
                                    }
                                }
                            }
                            if (homePivot == 2) {
                                FacebookSeed.albums.forEachIndexed { index, (album, photos) ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                albumIndex = index
                                                photoIndex = 0
                                                screen = FbScreen.PHOTO
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(Modifier.size(34.dp).background(colors.tile))
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            EdgeCropText(album, DoradoTokens.TYPE_LIST.dp)
                                            EdgeCropText(
                                                "${photos.size} photos",
                                                DoradoTokens.TYPE_CAPTION.dp,
                                                color = colors.textSecondary,
                                            )
                                        }
                                    }
                                    FbHairline()
                                }
                            } else {
                                val feed = if (homePivot == 1) {
                                    myPosts
                                } else {
                                    FeedCache.merge(myPosts, FacebookSeed.posts)
                                }
                                if (feed.isEmpty()) {
                                    Spacer(Modifier.height(20.dp))
                                    EdgeCropText(
                                        "nothing posted yet — say something",
                                        DoradoTokens.TYPE_LIST.dp,
                                        color = colors.textSecondary,
                                    )
                                } else {
                                    FeedCache.page(feed, feedPage).forEach { post ->
                                        FbPostRow(
                                            post = post,
                                            liked = post.id in liked,
                                            likeCount = likeCount(post),
                                            onToggleLike = { toggleLike(post) },
                                        )
                                    }
                                    if (feedPage < FeedCache.pageCount(feed) - 1) {
                                        FbAction("older posts") { feedPage++ }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }

                FbScreen.PROFILE, FbScreen.FRIEND -> {
                    val owner = profileOwner ?: FacebookSeed.user
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FbAvatar(owner, size = 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                EdgeCropText(owner, DoradoTokens.TYPE_NOW_META.dp, color = colors.textPrimary)
                                EdgeCropText(
                                    if (owner == FacebookSeed.user) FacebookSeed.personalMessage else "friend from the archive",
                                    DoradoTokens.TYPE_CAPTION.dp,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        if (screen == FbScreen.FRIEND || profileOwner == null) {
                            FbPivots(listOf("wall", "info", "photos"), profilePivot) { profilePivot = it }
                        }
                        Spacer(Modifier.height(4.dp))
                        if (screen == FbScreen.FRIEND) {
                            MockBody(
                                "$owner keeps only the public info card in this offline snapshot. " +
                                    "posts and photos were never cached.",
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FbAction("add friend", accent = true) { announce("friend request queued offline") }
                                FbAction("message") {
                                    composeDraft = FbCompose(to = listOf(owner))
                                    screen = FbScreen.COMPOSE
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        } else {
                            when (profilePivot) {
                                0 -> {
                                    Row(
                                        Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(Modifier.weight(1f)) {
                                            FbLineField("write on your wall", statusText) { statusText = it }
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        FbAction("post", enabled = statusText.isNotBlank(), accent = true) {
                                            publishStatus()
                                        }
                                    }
                                    if (myPosts.isEmpty()) {
                                        MockBody("your wall is empty in this snapshot.")
                                    } else {
                                        myPosts.forEach { post ->
                                            FbPostRow(
                                                post = post,
                                                liked = post.id in liked,
                                                likeCount = likeCount(post),
                                                onToggleLike = { toggleLike(post) },
                                            )
                                        }
                                    }
                                }
                                1 -> {
                                    listOf(
                                        "city" to "harbor",
                                        "work" to "field recordings",
                                        "school" to "the old campus",
                                        "joined" to "2009",
                                    ).forEach { (key, value) ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                            EdgeCropText(
                                                key,
                                                DoradoTokens.TYPE_LIST_SECONDARY.dp,
                                                color = colors.textSecondary,
                                                modifier = Modifier.width(70.dp),
                                            )
                                            EdgeCropText(value, DoradoTokens.TYPE_LIST.dp)
                                        }
                                        FbHairline()
                                    }
                                }
                                else -> {
                                    FacebookSeed.albums.forEachIndexed { index, (album, photos) ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    albumIndex = index
                                                    photoIndex = 0
                                                    screen = FbScreen.PHOTO
                                                }
                                                .padding(vertical = 7.dp),
                                        ) {
                                            EdgeCropText(album, DoradoTokens.TYPE_LIST.dp, modifier = Modifier.weight(1f))
                                            EdgeCropText("${photos.size}", DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                                        }
                                        FbHairline()
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                FbScreen.FRIENDS -> {
                    Column(Modifier.fillMaxSize()) {
                        FbBanner("offline snapshot — ${FacebookSeed.friends.size} friends cached")
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = DoradoTokens.EDGE.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FbAction(if (friendsGrid) "list" else "photo grid") { friendsGrid = !friendsGrid }
                                FbAction("requests") { screen = FbScreen.REQUESTS }
                            }
                            Box(Modifier.padding(bottom = 6.dp)) {
                                FbLineField("search friends", friendsQuery) { friendsQuery = it }
                            }
                            val visible = FacebookSeed.friends.filter {
                                friendsQuery.isBlank() || it.contains(friendsQuery.trim(), ignoreCase = true)
                            }
                            if (friendsGrid) {
                                visible.chunked(5).forEach { chunk ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                        chunk.forEach { friend ->
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.clickable {
                                                    profileOwner = friend
                                                    screen = FbScreen.FRIEND
                                                },
                                            ) {
                                                FbAvatar(friend, size = 40.dp)
                                                EdgeCropText(friend, DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                                            }
                                        }
                                    }
                                }
                            } else {
                                visible.forEach { friend ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                profileOwner = friend
                                                screen = FbScreen.FRIEND
                                            }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        FbAvatar(friend)
                                        Spacer(Modifier.width(8.dp))
                                        EdgeCropText(friend, DoradoTokens.TYPE_LIST.dp, modifier = Modifier.weight(1f))
                                        EdgeCropText("profile", DoradoTokens.TYPE_CAPTION.dp, color = colors.textInactive)
                                    }
                                    FbHairline()
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                FbScreen.MESSAGES -> {
                    Column(Modifier.fillMaxSize()) {
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = DoradoTokens.EDGE.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FbPivots(FbFolder.entries.map { it.label }, msgFolder.ordinal) { msgFolder = FbFolder.entries[it] }
                                Spacer(Modifier.weight(1f))
                                if (msgFolder == FbFolder.INBOX) {
                                    FbAction("compose", accent = true) { openCompose() }
                                }
                            }
                            val threads = FbMail.folderThreads(allThreads, msgFolder, read, deletedThreads)
                                .map { thread ->
                                    localReplies[thread.id]?.let { extra ->
                                        thread.copy(messages = thread.messages + extra)
                                    } ?: thread
                                }
                            if (threads.isEmpty()) {
                                Spacer(Modifier.height(20.dp))
                                EdgeCropText("this folder is empty", DoradoTokens.TYPE_LIST.dp, color = colors.textSecondary)
                            }
                            threads.forEach { thread ->
                                val unread = FbMail.effectiveUnread(thread, read)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(if (unread) colors.elevated else colors.background)
                                        .clickable { openThread(thread.id) }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    FbAvatar(thread.with)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row {
                                            EdgeCropText(
                                                thread.with,
                                                DoradoTokens.TYPE_LIST.dp,
                                                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                                                modifier = Modifier.weight(1f),
                                            )
                                            EdgeCropText(thread.time, DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                                        }
                                        EdgeCropText(thread.subject, DoradoTokens.TYPE_LIST_SECONDARY.dp, color = colors.accent)
                                        EdgeCropText(
                                            thread.snippet,
                                            DoradoTokens.TYPE_CAPTION.dp,
                                            color = colors.textSecondary,
                                        )
                                    }
                                    if (unread) {
                                        Box(Modifier.size(5.dp).background(colors.accent))
                                    }
                                }
                                FbHairline()
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                FbScreen.THREAD -> {
                    val thread = allThreads.firstOrNull { it.id == threadId }
                    val messages = thread?.messages.orEmpty() + (threadId?.let { localReplies[it] } ?: emptyList())
                    Column(Modifier.fillMaxSize()) {
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
                        ) {
                            EdgeCropText(thread?.subject ?: "message", DoradoTokens.TYPE_LIST.dp, fontWeight = FontWeight.SemiBold)
                            EdgeCropText(thread?.with.orEmpty(), DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                            Spacer(Modifier.height(6.dp))
                            messages.forEach { message ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    FbAvatar(message.from)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Row {
                                            EdgeCropText(
                                                message.from,
                                                DoradoTokens.TYPE_LIST_SECONDARY.dp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            EdgeCropText(message.time, DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                                        }
                                        FbLinkText(message.body)
                                    }
                                }
                                FbHairline()
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    FbLineField("reply", replyText) { replyText = it }
                                }
                                Spacer(Modifier.width(6.dp))
                                FbAction("send", enabled = replyText.isNotBlank(), accent = true) {
                                    val id = threadId ?: return@FbAction
                                    localReplies = localReplies + (
                                        id to ((localReplies[id] ?: emptyList()) + FbMessage("you", replyText.trim(), "now"))
                                        )
                                    replyText = ""
                                    announce("reply saved locally — offline")
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            FbAction("delete thread") {
                                threadId?.let { deletedThreads = deletedThreads + it }
                                threadId = null
                                screen = FbScreen.MESSAGES
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                FbScreen.COMPOSE -> {
                    Column(Modifier.fillMaxSize()) {
                        FbBanner("offline — messages are stored on this device")
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
                        ) {
                            EdgeCropText("to", DoradoTokens.TYPE_LIST_SECONDARY.dp, color = colors.textSecondary)
                            Spacer(Modifier.height(3.dp))
                            FacebookSeed.friends.forEach { friend ->
                                val selected = friend in composeDraft.to
                                EdgeCropText(
                                    text = friend,
                                    fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
                                    color = if (selected) colors.accent else colors.textPrimary,
                                    modifier = Modifier
                                        .clickable {
                                            composeDraft = composeDraft.copy(
                                                to = if (selected) composeDraft.to - friend else composeDraft.to + friend,
                                            )
                                        }
                                        .padding(vertical = 2.dp),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            FbLineField("subject", composeDraft.subject) { composeDraft = composeDraft.copy(subject = it) }
                            Spacer(Modifier.height(6.dp))
                            FbBlockField("message", composeDraft.body, { composeDraft = composeDraft.copy(body = it) }, 70.dp)
                            Spacer(Modifier.height(6.dp))
                            val check = validateFbCompose(composeDraft.to, composeDraft.subject, composeDraft.body)
                            EdgeCropText(
                                if (check.ok) "${FB_MAX_BODY - composeDraft.body.length} characters left"
                                else check.error.orEmpty(),
                                DoradoTokens.TYPE_CAPTION.dp,
                                color = if (check.ok) colors.textInactive else colors.accent,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FbAction("send", enabled = check.ok, accent = true) { sendMessage() }
                                FbAction("close") { back() }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                FbScreen.NOTIFICATIONS -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EdgeCropText("notifications", DoradoTokens.TYPE_LIST.dp, modifier = Modifier.weight(1f))
                            FbAction("mark all read", accent = true) { notificationsRead = true }
                        }
                        Spacer(Modifier.height(4.dp))
                        FacebookSeed.notifications.forEach { notification ->
                            val unread = notification.unread && !notificationsRead
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(22.dp).background(colors.tile), contentAlignment = Alignment.Center) {
                                    EdgeCropText(notification.kind.take(1), DoradoTokens.TYPE_CAPTION.dp, color = colors.accent)
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    EdgeCropText(
                                        notification.text,
                                        DoradoTokens.TYPE_LIST.dp,
                                        color = if (unread) colors.textPrimary else colors.textSecondary,
                                    )
                                    EdgeCropText(notification.age, DoradoTokens.TYPE_CAPTION.dp, color = colors.textInactive)
                                }
                                if (unread) Box(Modifier.size(5.dp).background(colors.accent))
                            }
                            FbHairline()
                        }
                    }
                }

                FbScreen.SETTINGS -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                    ) {
                        FbPivots(listOf("options", "about", "terms"), settingsPivot) { index ->
                            settingsPivot = index
                            // Navigate from the click, never during composition,
                            // so the pivot cannot instantly re-open About (A-11).
                            if (index == 1) screen = FbScreen.ABOUT
                        }
                        Spacer(Modifier.height(8.dp))
                        when (settingsPivot) {
                            0 -> {
                                FbAction("account: ${FacebookSeed.user}", accent = true) {}
                                Spacer(Modifier.height(6.dp))
                                FbAction("auto-reload: ${if (autoReload) "on" else "off"}") { autoReload = !autoReload }
                                Spacer(Modifier.height(6.dp))
                                FbAction("shake to reload: ${if (shakeReload) "on" else "off"}") { shakeReload = !shakeReload }
                                Spacer(Modifier.height(6.dp))
                                FbAction("notifications") { screen = FbScreen.NOTIFICATIONS }
                                Spacer(Modifier.height(12.dp))
                                FbAction("log out", accent = true) {
                                    loggedIn = false
                                    screen = FbScreen.LOGIN
                                    loginPassword = ""
                                    announce("logged out of the snapshot")
                                }
                            }
                            2 -> MockBody(
                                "terms and attribution for the archived facebook client. " +
                                    "this build is a clean-room re-creation with synthetic content only.",
                            )
                        }
                    }
                }

                FbScreen.ABOUT -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                    ) {
                        MockBody(
                            "a clean-room re-creation of the facebook app that shipped on the zune hd marketplace. " +
                                "the graph service was retired, so this build opens a frozen local snapshot.",
                        )
                        Spacer(Modifier.height(8.dp))
                        MockBody("feed, friends, wall, photos and mail are synthetic. no session or credentials are used.")
                    }
                }

                FbScreen.PHOTO -> {
                    val (album, photos) = FacebookSeed.albums[albumIndex.coerceIn(0, FacebookSeed.albums.lastIndex)]
                    Column(Modifier.fillMaxSize()) {
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
                        ) {
                            EdgeCropText(album, DoradoTokens.TYPE_NOW_META.dp, color = colors.textPrimary)
                            EdgeCropText(
                                "${photoIndex + 1} of ${photos.size} · offline placeholder",
                                DoradoTokens.TYPE_CAPTION.dp,
                                color = colors.textSecondary,
                            )
                            Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .background(colors.tile),
                                contentAlignment = Alignment.Center,
                            ) {
                                EdgeCropText(
                                    photos.getOrElse(photoIndex) { "photo" },
                                    DoradoTokens.TYPE_LIST.dp,
                                    color = colors.textSecondary,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FbAction("previous", enabled = photoIndex > 0) { photoIndex-- }
                                FbAction("next", enabled = photoIndex < photos.lastIndex) { photoIndex++ }
                                FbAction("comment", accent = true) { announce("comments need the live service") }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                FbScreen.REQUESTS -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                    ) {
                        val pending = FacebookSeed.requests.filter { it !in decisions }
                        if (pending.isEmpty()) {
                            EdgeCropText("no pending requests", DoradoTokens.TYPE_LIST.dp, color = colors.textSecondary)
                        }
                        pending.forEach { name ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FbAvatar(name)
                                Spacer(Modifier.width(8.dp))
                                EdgeCropText(name, DoradoTokens.TYPE_LIST.dp, modifier = Modifier.weight(1f))
                                FbAction("confirm", accent = true) { decisions = decisions + (name to true) }
                                FbAction("ignore") { decisions = decisions + (name to false) }
                            }
                            FbHairline()
                        }
                        if (decisions.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            MockBody("decisions are local — the service cannot deliver them.")
                        }
                    }
                }
                }
            }

                if (inShell) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    when (screen) {
                        FbScreen.HOME, FbScreen.PROFILE, FbScreen.FRIENDS, FbScreen.MESSAGES -> {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(colors.elevated)
                                    .border(0.5.dp, colors.border)
                                    .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FbAction("notifications") {
                                    screen = FbScreen.NOTIFICATIONS
                                }
                                Spacer(Modifier.weight(1f))
                                FbAction("settings") { screen = FbScreen.SETTINGS }
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(colors.background)
                                    .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                                listOf("home", "profile", "friends", "messages").forEachIndexed { index, label ->
                                    EdgeCropText(
                                        text = label,
                                        fontSize = DoradoTokens.TYPE_LIST.dp,
                                        color = if (tab == index) colors.accent else colors.textSecondary,
                                        modifier = Modifier.clickable {
                                            tab = index
                                            screen = when (index) {
                                                0 -> FbScreen.HOME
                                                1 -> {
                                                    profileOwner = null
                                                    FbScreen.PROFILE
                                                }
                                                2 -> FbScreen.FRIENDS
                                                else -> FbScreen.MESSAGES
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        else -> Unit
                    }
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
                        EdgeCropText("discard this message?", DoradoTokens.TYPE_LIST.dp)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            FbAction("discard", accent = true) {
                                composeDraft = FbCompose()
                                discardAsk = false
                                screen = FbScreen.MESSAGES
                            }
                            FbAction("keep editing") { discardAsk = false }
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
