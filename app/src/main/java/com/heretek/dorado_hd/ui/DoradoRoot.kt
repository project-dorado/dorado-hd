package com.heretek.dorado_hd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.heretek.dorado_hd.R
import com.heretek.dorado_hd.data.repo.DoradoSettings
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.DoradoMotion
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.components.AlbumArt
import com.heretek.dorado_hd.design.components.DeviceCanvas
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.design.components.LockShade
import com.heretek.dorado_hd.ui.components.MenuController
import com.heretek.dorado_hd.ui.nav.DoradoDestination
import com.heretek.dorado_hd.ui.screens.AlbumDetailScreen
import com.heretek.dorado_hd.ui.screens.ArtistDetailScreen
import com.heretek.dorado_hd.ui.screens.DeviceScreen
import com.heretek.dorado_hd.ui.screens.GenreScreen
import com.heretek.dorado_hd.ui.screens.HomePages
import com.heretek.dorado_hd.ui.screens.InternetScreen
import com.heretek.dorado_hd.ui.screens.LyricsScreen
import com.heretek.dorado_hd.ui.screens.MarketplaceScreen
import com.heretek.dorado_hd.ui.screens.MiniAppScreen
import com.heretek.dorado_hd.ui.screens.MusicScreen
import com.heretek.dorado_hd.ui.screens.NowPlayingScreen
import com.heretek.dorado_hd.ui.screens.PicturesScreen
import com.heretek.dorado_hd.ui.screens.PictureDetailScreen
import com.heretek.dorado_hd.ui.screens.PodcastFeedScreen
import com.heretek.dorado_hd.ui.screens.PodcastsScreen
import com.heretek.dorado_hd.ui.screens.PlaylistDetailScreen
import com.heretek.dorado_hd.ui.screens.RadioScreen
import com.heretek.dorado_hd.ui.screens.SettingsScreen
import com.heretek.dorado_hd.ui.screens.SocialScreen
import com.heretek.dorado_hd.ui.screens.VideoItem
import com.heretek.dorado_hd.ui.screens.VideoPlayerScreen
import com.heretek.dorado_hd.ui.screens.VideosScreen

@Composable
fun DoradoRoot() {
    val graph = LocalDoradoGraph.current
    val settings by graph.settingsFlow.collectAsState(initial = DoradoSettings())
    val menus = remember { MenuController() }
    val activity = androidx.activity.compose.LocalActivity.current
    var shaded by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Wake shade (canon §5): cover the UI after the app leaves the foreground;
    // user slides the shade up to reveal the interface.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // ON_STOP means the app is genuinely backgrounded; ON_PAUSE
                // also fires for permission dialogs and singleTask deep-link
                // relaunches, which used to raise the shade over the app.
                Lifecycle.Event.ON_STOP -> shaded = true
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    com.heretek.dorado_hd.design.DoradoTheme(accent = settings.accent) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.heretek.dorado_hd.ui.components.LocalContextMenu provides menus,
        ) {
            // safeDrawingPadding reserves status-bar + navigation-bar + cutout
            // space. It is applied to the canvas itself (not inside it) so the
            // 480x272 design canvas is never shrunk or offset by real insets,
            // and adaptive density scaling doesn't multiply them.
            DeviceCanvas(
                deviceMode = settings.deviceMode,
                modifier = Modifier
                    .safeDrawingPadding()
                    // Keep text fields visible when the keyboard opens.
                    .imePadding(),
            ) { canvasWidth, canvasHeight ->
                Box(Modifier.fillMaxSize()) {
                    NavHost(canvasWidth, canvasHeight)
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                    ) {
                        MiniPlayer(canvasWidth)
                    }
                    com.heretek.dorado_hd.ui.components.ContextMenuOverlay(menus)
                    LockShade(visible = shaded, onUnlock = { shaded = false })
                }
            }
        }
    }

    BackHandler {
        val nav = graph.nav
        if (nav.current == DoradoDestination.Home) {
            // Let the system move the task to background.
            activity?.moveTaskToBack(false)
        } else {
            nav.pop()
        }
    }
}

@Composable
private fun NavHost(canvasWidth: Dp, canvasHeight: Dp) {
    val graph = LocalDoradoGraph.current
    val nav = graph.nav
    val target = nav.current to nav.depth

    AnimatedContent(
        targetState = target,
        transitionSpec = {
            val pushing = targetState.second > initialState.second
            if (pushing) {
                (slideInHorizontally(DoradoMotion.pivot()) { it / 2 } + fadeIn(DoradoMotion.pivot())) togetherWith
                    (slideOutHorizontally(DoradoMotion.pivot()) { -it / 8 } + fadeOut(DoradoMotion.pivot()))
            } else {
                (slideInHorizontally(DoradoMotion.pivot()) { -it / 8 } + fadeIn(DoradoMotion.pivot())) togetherWith
                    (slideOutHorizontally(DoradoMotion.pivot()) { it / 2 } + fadeOut(DoradoMotion.pivot()))
            }
        },
        label = "nav",
    ) { (destination, _) ->
        when (destination) {
            DoradoDestination.Home, DoradoDestination.Quickplay -> HomePages(canvasWidth)
            DoradoDestination.Music -> MusicScreen(canvasWidth)
            is DoradoDestination.Album -> AlbumDetailScreen(destination.albumId, canvasWidth)
            is DoradoDestination.Artist -> ArtistDetailScreen(destination.artistId, canvasWidth)
            is DoradoDestination.Genre -> GenreScreen(destination.genre, canvasWidth)
            is DoradoDestination.PlaylistDetail -> PlaylistDetailScreen(destination.playlistId, canvasWidth)
            DoradoDestination.NowPlaying -> NowPlayingScreen(canvasWidth)
            DoradoDestination.Settings -> SettingsScreen(canvasWidth)
            DoradoDestination.Device -> DeviceScreen(canvasWidth)
            DoradoDestination.Lyrics -> LyricsScreen(canvasWidth)
            DoradoDestination.Videos -> VideosScreen(canvasWidth)
            DoradoDestination.Pictures -> PicturesScreen(canvasWidth)
            DoradoDestination.Radio -> RadioScreen(canvasWidth)
            DoradoDestination.Podcasts -> PodcastsScreen(canvasWidth)
            is DoradoDestination.PodcastFeed -> PodcastFeedScreen(destination.feedId, canvasWidth)
            DoradoDestination.Marketplace -> MarketplaceScreen(canvasWidth)
            DoradoDestination.Social -> SocialScreen(canvasWidth)
            DoradoDestination.Internet -> InternetScreen(canvasWidth)
            is DoradoDestination.MiniApp -> MiniAppScreen(destination.appId, canvasWidth)
            is DoradoDestination.PictureDetail -> PictureDetailScreen(destination.uri)
            is DoradoDestination.Video -> VideoPlayerScreen(
                item = VideoItem(
                    id = 0L,
                    title = destination.title,
                    artist = "",
                    uri = android.net.Uri.parse(destination.uri),
                    bucket = "",
                ),
                onExit = { graph.nav.pop() },
            )
        }
    }
}

@Composable
private fun MiniPlayer(canvasWidth: Dp) {
    val graph = LocalDoradoGraph.current
    val nav = graph.nav
    val controller = graph.controller
    val nowPlaying by controller.nowPlaying.collectAsState()
    val isPlaying by controller.isPlaying.collectAsState()
    val immersive by graph.immersive.collectAsState()
    val colors = LocalDoradoColors.current

    // Canon §8: mini-apps and fullscreen viewers open without the MiniPlayer.
    val fullscreenRoute = when (nav.current) {
        DoradoDestination.NowPlaying,
        is DoradoDestination.MiniApp,
        is DoradoDestination.Video,
        is DoradoDestination.PictureDetail,
        -> true
        else -> false
    }
    if (fullscreenRoute || immersive || nowPlaying == null) return

    Box(
        Modifier
            .fillMaxWidth()
            .height(DoradoTokens.MINI_PLAYER_HEIGHT.dp)
            .background(colors.elevated.copy(alpha = 0.95f)),
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.border),
        )
        Row(
            Modifier
                .fillMaxSize()
                .clickable { nav.push(DoradoDestination.NowPlaying) }
                .padding(horizontal = DoradoTokens.EDGE.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumArt(
                model = nowPlaying?.albumArtUri,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            EdgeCropText(
                text = "${nowPlaying?.title ?: ""} — ${nowPlaying?.artist ?: ""}",
                fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp),
                color = colors.textSecondary,
            )
            // A bare 32dp target: Material's IconButton enforces a 48dp minimum
            // touch target that would overhang the 32dp bar and steal taps.
            Box(
                Modifier
                    .size(DoradoTokens.MINI_PLAYER_HEIGHT.dp)
                    .clickable { controller.toggle() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = if (isPlaying) "pause" else "play",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
