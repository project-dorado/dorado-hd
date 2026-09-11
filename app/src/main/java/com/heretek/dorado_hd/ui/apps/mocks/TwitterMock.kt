package com.heretek.dorado_hd.ui.apps.mocks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.delay

private enum class TwScreen { LOGIN, SHELL, COMPOSE, SEARCH, PROFILE, THREAD, SETTINGS, ABOUT, DETAIL }

private enum class TwTab { TIMELINE, REPLIES, FAVOURITES, DMS }

@Composable
private fun TwAction(
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
private fun TwPivots(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
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
private fun TwBanner(text: String) {
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
private fun TwHairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(LocalDoradoColors.current.border),
    )
}

@Composable
private fun TwAvatar(handle: String, size: androidx.compose.ui.unit.Dp = 26.dp) {
    val colors = LocalDoradoColors.current
    Box(Modifier.size(size).background(colors.tile), contentAlignment = Alignment.Center) {
        EdgeCropText(
            handle.trimStart('@').take(1).lowercase(),
            DoradoTokens.TYPE_LIST_SECONDARY.dp,
            color = colors.accent,
        )
    }
}

@Composable
private fun TwRichText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.Dp = DoradoTokens.TYPE_LIST.dp,
    color: androidx.compose.ui.graphics.Color? = null,
) {
    val colors = LocalDoradoColors.current
    val annotated = remember(text) {
        buildAnnotatedString {
            splitTweet(text).forEach { token ->
                when (token.kind) {
                    TweetTokenKind.TEXT -> append(token.text)
                    else -> withStyle(SpanStyle(color = colors.accent)) { append(token.text) }
                }
            }
        }
    }
    BasicText(
        text = annotated,
        style = TextStyle(
            fontFamily = Selawik,
            fontSize = fontSize.value.sp,
            color = color ?: colors.textPrimary,
            lineHeight = (fontSize.value * 1.3f).sp,
        ),
        modifier = modifier,
    )
}

@Composable
private fun TwLineField(placeholder: String, value: String, onChange: (String) -> Unit) {
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
private fun TwBlockField(placeholder: String, value: String, onChange: (String) -> Unit, height: androidx.compose.ui.unit.Dp) {
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
private fun TwTweetRow(
    tweet: Tweet,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(Modifier.clickable(onClick = onOpenProfile)) {
            TwAvatar(tweet.handle)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EdgeCropText(
                    tweet.author.uppercase(),
                    DoradoTokens.TYPE_LIST_SECONDARY.dp,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                EdgeCropText(tweet.age, DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
            }
            EdgeCropText(tweet.handle, DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
            Box(Modifier.clickable(onClick = onOpenDetail)) {
                TwRichText(tweet.text, modifier = Modifier.padding(vertical = 3.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TwAction("heart", accent = favorite, onClick = onToggleFavorite)
                Spacer(Modifier.weight(1f))
                EdgeCropText("reply", DoradoTokens.TYPE_CAPTION.dp, color = colors.textInactive)
            }
        }
    }
    TwHairline()
}

/**
 * Offline Twitter client for the retired OAuth-era backend. Four-tab shell over
 * a fixed local archive; favourites, the compose draft and the remembered
 * handle persist in `graph.appState`.
 */
@Composable
fun TwitterApp() {
    val graph = LocalDoradoGraph.current
    val colors = LocalDoradoColors.current

    var loaded by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(TwScreen.LOGIN) }
    var tab by remember { mutableStateOf(TwTab.TIMELINE) }
    var timelinePivot by remember { mutableStateOf(0) }
    var dmPivot by remember { mutableStateOf(0) }
    var favourites by remember { mutableStateOf(emptySet<Long>()) }
    var draft by remember { mutableStateOf("") }
    var composeIsDm by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signingIn by remember { mutableStateOf(false) }
    var profileHandle by remember { mutableStateOf("@you") }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var threadId by remember { mutableStateOf<Long?>(null) }
    var replyText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var myTweets by remember { mutableStateOf(emptyList<Tweet>()) }
    var localReplies by remember { mutableStateOf(emptyMap<Long, List<DmMessage>>()) }
    var localThreads by remember { mutableStateOf(emptyList<DmThread>()) }
    var following by remember { mutableStateOf(true) }
    var toast by remember { mutableStateOf<String?>(null) }

    val cache = remember { TweetCache() }

    fun announce(message: String) {
        toast = message
    }

    fun allTweets(): List<Tweet> = myTweets + TwitterSeed.tweets + TwitterSeed.replies + TwitterSeed.profile

    /** Seed flag XOR explicit toggle: tapping a heart flips the effective state. */
    fun isFavorite(tweet: Tweet): Boolean = tweet.favorite != (tweet.id in favourites)

    fun openProfile(handle: String) {
        profileHandle = handle
        screen = TwScreen.PROFILE
    }

    fun openThread(id: Long) {
        threadId = id
        screen = TwScreen.THREAD
    }

    fun toggleFavorite(id: Long) {
        favourites = if (id in favourites) favourites - id else favourites + id
    }

    fun sendCompose() {
        val state = composeState(draft)
        if (composeIsDm && state.target != ComposeTarget.DM) {
            announce("start with @handle to choose a recipient")
            return
        }
        if (!state.enabled) {
            announce(if (state.overLimit) "that is too long" else "nothing to send")
            return
        }
        if (state.target == ComposeTarget.DM) {
            val handle = state.recipient ?: return
            val body = dmTarget(draft)?.second.orEmpty()
            // Store/lookup by id everywhere: a new handle gets a real thread,
            // so it appears in the DM list and opens by threadId (A-12).
            val update = appendLocalDm(TwitterSeed.dms, localThreads, localReplies, handle, body)
            localThreads = update.threads
            localReplies = update.replies
            draft = ""
            screen = TwScreen.SHELL
            tab = TwTab.DMS
            announce("direct message kept locally — service offline")
        } else {
            val tweet = Tweet(
                id = (myTweets.maxOfOrNull { it.id } ?: 900L) + 1,
                author = "you",
                handle = "@you",
                text = draft.trim(),
                age = "now",
            )
            myTweets = listOf(tweet) + myTweets
            draft = ""
            screen = TwScreen.SHELL
            tab = TwTab.TIMELINE
            timelinePivot = 0
            announce("tweet archived locally — service offline")
        }
    }

    fun back() {
        when (screen) {
            TwScreen.LOGIN -> graph.nav.pop()
            TwScreen.SHELL -> graph.nav.pop()
            TwScreen.COMPOSE -> {
                screen = TwScreen.SHELL
                composeIsDm = false
            }
            TwScreen.SEARCH, TwScreen.PROFILE, TwScreen.SETTINGS, TwScreen.DETAIL, TwScreen.THREAD -> {
                screen = TwScreen.SHELL
                detailId = null
                threadId = null
            }
            TwScreen.ABOUT -> screen = TwScreen.SETTINGS
        }
    }

    LaunchedEffect(Unit) {
        favourites = TwitterCodec.decodeIds(graph.appState.get("twitter.favs"))
        draft = TwitterCodec.decodeDraft(graph.appState.get("twitter.draft"))
        username = graph.appState.get("twitter.user") ?: ""
        if (username.isNotBlank()) screen = TwScreen.SHELL
        loaded = true
    }

    LaunchedEffect(favourites, loaded) {
        if (loaded) graph.appState.put("twitter.favs", TwitterCodec.encodeIds(favourites))
    }

    LaunchedEffect(draft, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(400)
        graph.appState.put("twitter.draft", TwitterCodec.encodeDraft(draft))
    }

    LaunchedEffect(username, loaded) {
        if (loaded && username.isNotBlank()) graph.appState.put("twitter.user", username)
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2400)
            toast = null
        }
    }

    val title = when (screen) {
        TwScreen.LOGIN, TwScreen.SHELL -> "twitter"
        TwScreen.COMPOSE -> if (composeState(draft).target == ComposeTarget.DM) "new direct message" else "new tweet"
        TwScreen.SEARCH -> "search"
        TwScreen.PROFILE -> profileHandle
        TwScreen.THREAD -> "direct message"
        TwScreen.SETTINGS -> "settings"
        TwScreen.ABOUT -> "about"
        TwScreen.DETAIL -> "tweet"
    }

    DetailScaffold(title = title, onBack = { back() }) {
        Box(Modifier.fillMaxSize()) {
            when (screen) {
                TwScreen.LOGIN -> {
                    Column(Modifier.fillMaxSize()) {
                        TwBanner("offline archive — the timeline is frozen, compose still works locally")
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 48.dp, vertical = 14.dp),
                        ) {
                            EdgeCropText("twitter", DoradoTokens.TYPE_NOW_TITLE.dp, color = colors.accent)
                            Spacer(Modifier.height(4.dp))
                            EdgeCropText(
                                "sign in to the archived flock",
                                DoradoTokens.TYPE_LIST_SECONDARY.dp,
                                color = colors.textSecondary,
                            )
                            Spacer(Modifier.height(10.dp))
                            TwLineField("username or email", username) { username = it }
                            Spacer(Modifier.height(6.dp))
                            TwLineField("password", password) { password = it }
                            Spacer(Modifier.height(10.dp))
                            TwAction(
                                if (signingIn) "signing in…" else "log in",
                                enabled = username.isNotBlank() && password.isNotBlank() && !signingIn,
                                accent = true,
                            ) { signingIn = true }
                            if (loaded && username.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                TwAction("continue as $username") { screen = TwScreen.SHELL }
                            }
                            Spacer(Modifier.height(8.dp))
                            EdgeCropText(
                                "any credentials open the local archive; nothing is sent.",
                                DoradoTokens.TYPE_CAPTION.dp,
                                color = colors.textInactive,
                            )
                        }
                    }
                    LaunchedEffect(signingIn) {
                        if (signingIn) {
                            delay(650)
                            screen = TwScreen.SHELL
                            signingIn = false
                        }
                    }
                }

                TwScreen.SHELL -> {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(colors.elevated)
                                .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            listOf("timeline", "replies", "favourites", "dms").forEachIndexed { index, label ->
                                EdgeCropText(
                                    text = label,
                                    fontSize = DoradoTokens.TYPE_LIST.dp,
                                    color = if (tab.ordinal == index) colors.accent else colors.textSecondary,
                                    modifier = Modifier.clickable { tab = TwTab.entries[index] },
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            EdgeCropText(
                                username.uppercase().take(15),
                                DoradoTokens.TYPE_CAPTION.dp,
                                color = colors.textSecondary,
                            )
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = DoradoTokens.EDGE.dp),
                        ) {
                            when (tab) {
                                TwTab.TIMELINE -> {
                                    Row(Modifier.padding(vertical = 6.dp)) {
                                        TwPivots(listOf("timeline", "following", "followers"), timelinePivot) {
                                            timelinePivot = it
                                        }
                                    }
                                    when (timelinePivot) {
                                        0 -> {
                                            val archive = cache.merge(TwitterSeed.tweets, myTweets)
                                            archive.forEach { tweet ->
                                                TwTweetRow(
                                                    tweet = tweet,
                                                    favorite = isFavorite(tweet),
                                                    onToggleFavorite = { toggleFavorite(tweet.id) },
                                                    onOpenProfile = { openProfile(tweet.handle) },
                                                    onOpenDetail = {
                                                        detailId = tweet.id
                                                        screen = TwScreen.DETAIL
                                                    },
                                                )
                                            }
                                            if (archive.size >= 20) TwAction("more") { announce("end of the cached page") }
                                        }
                                        1 -> TwitterSeed.following.forEach { name ->
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clickable { openProfile("@" + name.replace(" ", "")) }
                                                    .padding(vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                TwAvatar("@" + name)
                                                Spacer(Modifier.width(8.dp))
                                                EdgeCropText(name, DoradoTokens.TYPE_LIST.dp, modifier = Modifier.weight(1f))
                                                EdgeCropText("following", DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                                            }
                                            TwHairline()
                                        }
                                        else -> TwitterSeed.followers.forEach { name ->
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clickable { openProfile("@" + name.replace(" ", "")) }
                                                    .padding(vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                TwAvatar("@" + name)
                                                Spacer(Modifier.width(8.dp))
                                                EdgeCropText(name, DoradoTokens.TYPE_LIST.dp, modifier = Modifier.weight(1f))
                                                EdgeCropText("follows you", DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                                            }
                                            TwHairline()
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                }

                                TwTab.REPLIES -> {
                                    TwitterSeed.replies.forEach { tweet ->
                                        TwTweetRow(
                                            tweet = tweet,
                                            favorite = isFavorite(tweet),
                                            onToggleFavorite = { toggleFavorite(tweet.id) },
                                            onOpenProfile = { openProfile(tweet.handle) },
                                            onOpenDetail = {
                                                detailId = tweet.id
                                                screen = TwScreen.DETAIL
                                            },
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                }

                                TwTab.FAVOURITES -> {
                                    val liked = allTweets().filter { isFavorite(it) }
                                    if (liked.isEmpty()) {
                                        Spacer(Modifier.height(20.dp))
                                        EdgeCropText(
                                            "no favourites yet — tap a heart",
                                            DoradoTokens.TYPE_LIST.dp,
                                            color = colors.textSecondary,
                                        )
                                    }
                                    liked.forEach { tweet ->
                                        TwTweetRow(
                                            tweet = tweet,
                                            favorite = true,
                                            onToggleFavorite = { toggleFavorite(tweet.id) },
                                            onOpenProfile = { openProfile(tweet.handle) },
                                            onOpenDetail = {
                                                detailId = tweet.id
                                                screen = TwScreen.DETAIL
                                            },
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                }

                                TwTab.DMS -> {
                                    Row(Modifier.padding(vertical = 6.dp)) {
                                        TwPivots(listOf("inbox", "sent"), dmPivot) { dmPivot = it }
                                    }
                                    allDmThreads(TwitterSeed.dms, localThreads).forEach { thread ->
                                        val messages = threadMessages(thread, localReplies)
                                        val show = if (dmPivot == 0) {
                                            messages.any { !it.mine }
                                        } else {
                                            messages.any { it.mine }
                                        }
                                        if (show) {
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clickable { openThread(thread.id) }
                                                    .padding(vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                TwAvatar(thread.handle)
                                                Spacer(Modifier.width(8.dp))
                                                Column(Modifier.weight(1f)) {
                                                    EdgeCropText(thread.with, DoradoTokens.TYPE_LIST.dp)
                                                    EdgeCropText(
                                                        messages.lastOrNull()?.text.orEmpty(),
                                                        DoradoTokens.TYPE_CAPTION.dp,
                                                        color = colors.textSecondary,
                                                    )
                                                }
                                                EdgeCropText(
                                                    messages.lastOrNull()?.age.orEmpty(),
                                                    DoradoTokens.TYPE_CAPTION.dp,
                                                    color = colors.textInactive,
                                                )
                                            }
                                            TwHairline()
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                }
                            }
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(colors.elevated)
                                .border(0.5.dp, colors.border)
                                .navigationBarsPadding()
                                .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TwAction("tweet", accent = true) {
                                composeIsDm = false
                                draft = ""
                                screen = TwScreen.COMPOSE
                            }
                            Spacer(Modifier.weight(1f))
                            TwAction("search") { screen = TwScreen.SEARCH }
                            TwAction("settings") { screen = TwScreen.SETTINGS }
                        }
                    }
                }

                TwScreen.COMPOSE -> {
                    val state = composeState(draft)
                    Column(Modifier.fillMaxSize()) {
                        TwBanner("offline archive — posts are kept on this device")
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
                        ) {
                            TwPivots(
                                listOf("tweet", "direct message"),
                                if (composeIsDm) 1 else 0,
                            ) { composeIsDm = it == 1 }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                EdgeCropText(
                                    text = if (state.target == ComposeTarget.DM) {
                                        "→ direct message to ${state.recipient}"
                                    } else {
                                        "${state.remaining} characters left"
                                    },
                                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                                    color = if (state.overLimit) colors.accent else colors.textSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (state.overLimit) {
                                    EdgeCropText("too long", DoradoTokens.TYPE_CAPTION.dp, color = colors.accent)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            TwBlockField(
                                if (composeIsDm) "@handle your message" else "what's happening?",
                                draft,
                                { draft = it },
                                72.dp,
                            )
                            Spacer(Modifier.height(4.dp))
                            EdgeCropText(
                                "start with @ to send a direct message instead",
                                DoradoTokens.TYPE_CAPTION.dp,
                                color = colors.textInactive,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TwAction(
                                    "send",
                                    enabled = state.enabled && (!composeIsDm || state.target == ComposeTarget.DM),
                                    accent = true,
                                ) { sendCompose() }
                                TwAction("close") { back() }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                TwScreen.SEARCH -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                    ) {
                        TwLineField("search the archive", searchQuery) { searchQuery = it }
                        Spacer(Modifier.height(6.dp))
                        val results = allTweets().filter {
                            val needle = searchQuery.trim().lowercase()
                            needle.isNotEmpty() && (
                                it.text.lowercase().contains(needle) ||
                                    it.author.lowercase().contains(needle) ||
                                    it.handle.lowercase().contains(needle)
                                )
                        }
                        if (results.isEmpty()) {
                            EdgeCropText(
                                if (searchQuery.isBlank()) "type to search" else "nothing in the archive",
                                DoradoTokens.TYPE_LIST.dp,
                                color = colors.textSecondary,
                            )
                        }
                        results.take(10).forEach { tweet ->
                            TwTweetRow(
                                tweet = tweet,
                                favorite = isFavorite(tweet),
                                onToggleFavorite = { toggleFavorite(tweet.id) },
                                onOpenProfile = { openProfile(tweet.handle) },
                                onOpenDetail = {
                                    detailId = tweet.id
                                    screen = TwScreen.DETAIL
                                },
                            )
                        }
                    }
                }

                TwScreen.PROFILE -> {
                    val profileTweets = allTweets().filter { it.handle.equals(profileHandle, ignoreCase = true) }
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TwAvatar(profileHandle, size = 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                EdgeCropText(
                                    profileHandle.trimStart('@'),
                                    DoradoTokens.TYPE_NOW_META.dp,
                                    color = colors.textPrimary,
                                )
                                EdgeCropText(profileHandle, DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                            }
                        }
                        EdgeCropText(
                            "making mixtapes for a device that no longer connects.",
                            DoradoTokens.TYPE_CAPTION.dp,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            EdgeCropText("${if (following) TwitterSeed.following.size else 0} following", DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                            EdgeCropText("${TwitterSeed.followers.size} followers", DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                            EdgeCropText("${profileTweets.size} posts", DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TwAction(if (following) "unfollow" else "follow", accent = true) { following = !following }
                            TwAction("direct message") {
                                draft = "@${profileHandle.trimStart('@')} "
                                composeIsDm = true
                                screen = TwScreen.COMPOSE
                            }
                            TwAction("block") { announce("blocking needs the live service") }
                        }
                        Spacer(Modifier.height(6.dp))
                        profileTweets.forEach { tweet ->
                            TwRichText(tweet.text, modifier = Modifier.padding(vertical = 4.dp))
                            EdgeCropText(tweet.age, DoradoTokens.TYPE_CAPTION.dp, color = colors.textInactive)
                            TwHairline()
                        }
                    }
                }

                TwScreen.DETAIL -> {
                    val tweet = allTweets().firstOrNull { it.id == detailId }
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                    ) {
                        if (tweet == null) {
                            EdgeCropText("tweet not found", DoradoTokens.TYPE_LIST.dp, color = colors.textSecondary)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TwAvatar(tweet.handle, size = 30.dp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    EdgeCropText(tweet.author, DoradoTokens.TYPE_LIST.dp, fontWeight = FontWeight.SemiBold)
                                    EdgeCropText(tweet.handle, DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                                }
                                EdgeCropText(tweet.age, DoradoTokens.TYPE_CAPTION.dp, color = colors.textInactive)
                            }
                            Spacer(Modifier.height(8.dp))
                            TwRichText(tweet.text, fontSize = DoradoTokens.TYPE_NOW_META.dp)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                TwAction(
                                    "heart",
                                    accent = isFavorite(tweet),
                                ) { toggleFavorite(tweet.id) }
                                TwAction("reply") {
                                    draft = "${tweet.handle} "
                                    composeIsDm = false
                                    screen = TwScreen.COMPOSE
                                }
                            }
                        }
                    }
                }

                TwScreen.THREAD -> {
                    val thread = allDmThreads(TwitterSeed.dms, localThreads).firstOrNull { it.id == threadId }
                    val messages = thread?.let { threadMessages(it, localReplies) }.orEmpty()
                    Column(Modifier.fillMaxSize()) {
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
                        ) {
                            messages.forEach { message ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    TwAvatar(if (message.mine) "@you" else thread?.handle.orEmpty())
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Row {
                                            EdgeCropText(
                                                if (message.mine) "you" else thread?.with.orEmpty(),
                                                DoradoTokens.TYPE_LIST_SECONDARY.dp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            EdgeCropText(message.age, DoradoTokens.TYPE_CAPTION.dp, color = colors.textInactive)
                                        }
                                        TwRichText(message.text)
                                    }
                                }
                                TwHairline()
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    TwLineField("reply", replyText) { replyText = it }
                                }
                                Spacer(Modifier.width(6.dp))
                                TwAction("send", enabled = replyText.isNotBlank(), accent = true) {
                                    val id = threadId ?: return@TwAction
                                    localReplies = localReplies + (
                                        id to ((localReplies[id] ?: emptyList()) + DmMessage("you", replyText.trim(), "now", mine = true))
                                        )
                                    replyText = ""
                                    announce("direct message kept locally — offline")
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                TwScreen.SETTINGS -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                    ) {
                        MockBody("account")
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TwAvatar("@you", size = 30.dp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                EdgeCropText(username.ifBlank { "you" }, DoradoTokens.TYPE_LIST.dp)
                                EdgeCropText("@you", DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
                            }
                            TwAction("log out", accent = true) {
                                username = ""
                                screen = TwScreen.LOGIN
                                announce("logged out of the archive")
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        MockBody("settings")
                        Spacer(Modifier.height(4.dp))
                        TwAction("terms and attribution") { screen = TwScreen.ABOUT }
                        Spacer(Modifier.height(8.dp))
                        EdgeCropText(
                            "cached timeline, replies, favourites and dms render offline. no network calls.",
                            DoradoTokens.TYPE_CAPTION.dp,
                            color = colors.textSecondary,
                        )
                    }
                }

                TwScreen.ABOUT -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                    ) {
                        MockBody(
                            "a clean-room re-creation of the twitter app that shipped on the zune hd marketplace. " +
                                "the original api was switched off, so this build reads a frozen local archive.",
                        )
                        Spacer(Modifier.height(8.dp))
                        MockBody("all accounts, tweets and messages are synthetic. nothing is transmitted.")
                    }
                }
            }

            if (toast != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        // Reserve the shell bar so the toast never covers it (H-11).
                        .padding(bottom = if (screen == TwScreen.SHELL) 42.dp else 0.dp)
                        .background(colors.elevated)
                        .border(0.5.dp, colors.border)
                        .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 5.dp),
                ) {
                    EdgeCropText(toast.orEmpty(), DoradoTokens.TYPE_LIST_SECONDARY.dp, color = colors.accent)
                }
            }
        }
    }
}
