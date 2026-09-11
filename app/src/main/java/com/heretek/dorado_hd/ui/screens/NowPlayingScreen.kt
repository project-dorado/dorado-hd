package com.heretek.dorado_hd.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.heretek.dorado_hd.R
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoMotion
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.components.AlbumArt
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.design.components.SpectrumVisualizer
import com.heretek.dorado_hd.data.model.RepeatMode
import com.heretek.dorado_hd.data.model.Rating
import com.heretek.dorado_hd.data.model.Track
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.nav.DoradoDestination
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SKIP_DRAG_THRESHOLD = DoradoTokens.SKIP_DRAG_PX.toFloat()

/**
 * The Zune HD Now Playing: a metadata card floating over artist photography.
 * Swipe sideways to skip; tap for the transport overlay; idle and it becomes
 * a slow-scrolling screensaver — the device behavior, verified against the
 * period reviews in docs/zune-hd-ui-canon.md.
 */
@Composable
fun NowPlayingScreen(canvasWidth: Dp) {
    val graph = LocalDoradoGraph.current
    val controller = graph.controller
    val colors = LocalDoradoColors.current

    val track by controller.nowPlaying.collectAsState()
    val isPlaying by controller.isPlaying.collectAsState()
    val positionMs by controller.positionMs.collectAsState()
    val durationMs by controller.durationMs.collectAsState()
    val shuffle by controller.shuffle.collectAsState()
    val repeat by controller.repeat.collectAsState()
    val rating by controller.currentRating.collectAsState()

    var overlay by remember { mutableStateOf(false) }
    var screensaver by remember { mutableStateOf(false) }
    var interactionKey by remember { mutableStateOf(0) }

    val current = track

    // Idle → screensaver, exactly as the device does after a few seconds.
    LaunchedEffect(interactionKey, current?.mediaId, isPlaying) {
        if (current == null) return@LaunchedEffect
        delay(DoradoTokens.IDLE_SCREENSAVER_MS)
        if (!overlay) screensaver = true
    }

    fun poke() {
        interactionKey++
        screensaver = false
    }

    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EdgeCropText(
                text = "nothing playing",
                fontSize = DoradoTokens.TYPE_NOW_META.dp,
                alpha = 0.4f,
            )
        }
        return
    }

    val backdropFile by rememberArtistBackdropFile(current)
    val washColors by rememberArtWash(current)

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(overlay, screensaver) {
                if (!overlay) {
                    // Tap-to-show overlay: works whether the screensaver is up
                    // (device combined dismiss + overlay) or not.
                    detectTapGestures(onTap = {
                        screensaver = false
                        overlay = true
                        interactionKey++
                    })
                }
            }
            .pointerInput(overlay, screensaver) {
                if (!overlay && !screensaver) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onHorizontalDrag = { change, amount ->
                            totalDrag += amount
                            change.consume()
                        },
                        onDragEnd = {
                            if (totalDrag < -SKIP_DRAG_THRESHOLD) controller.next()
                            else if (totalDrag > SKIP_DRAG_THRESHOLD) controller.previous()
                        },
                    )
                }
            },
    ) {
        // Layer 0: the backdrop — artist photography or a palette wash.
        BackdropLayer(current, backdropFile, washColors)

        // The floating card.
        AnimatedVisibility(
            visible = !screensaver,
            enter = fadeIn(DoradoMotion.pivot()),
            exit = fadeOut(DoradoMotion.pivot()),
        ) {
            Column(Modifier.fillMaxSize()) {
                // Explicit back arrow, per the canon.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = (DoradoTokens.EDGE - 13).coerceAtLeast(0).dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { graph.nav.pop() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "back",
                            tint = colors.textPrimary.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DoradoTokens.EDGE.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    AlbumArt(
                        model = current.albumArtUri,
                        contentDescription = current.album,
                        modifier = Modifier.size(96.dp),
                    )
                    Column(Modifier.padding(start = 14.dp)) {
                        EdgeCropText(
                            text = current.title,
                            fontSize = DoradoTokens.TYPE_NOW_TITLE.dp,
                            // Canon §4: the song title tappable → the album's track list.
                            modifier = Modifier.clickable {
                                poke()
                                graph.nav.push(DoradoDestination.Album(current.albumId))
                            },
                        )
                        EdgeCropText(
                            text = current.artist,
                            fontSize = DoradoTokens.TYPE_NOW_META.dp,
                            alpha = 0.85f,
                            modifier = Modifier.clickable {
                                poke()
                                graph.nav.push(DoradoDestination.Artist(current.artistId))
                            },
                        )
                        EdgeCropText(
                            text = current.album,
                            fontSize = DoradoTokens.TYPE_NOW_META.dp,
                            alpha = 0.85f,
                            modifier = Modifier.clickable {
                                poke()
                                graph.nav.push(DoradoDestination.Album(current.albumId))
                            },
                        )
                        EdgeCropText(
                            text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                            fontSize = DoradoTokens.TYPE_CAPTION.dp,
                            alpha = 0.6f,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        EdgeCropText(
                            text = "lyrics",
                            fontSize = DoradoTokens.TYPE_LIST.dp,
                            color = colors.accent,
                            modifier = Modifier
                                .clickable {
                                    poke()
                                    graph.nav.push(DoradoDestination.Lyrics)
                                }
                                .padding(top = 8.dp),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Bottom row: shuffle, repeat, and the tri-state heart.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shuffle),
                        contentDescription = "shuffle",
                        tint = if (shuffle) colors.accent else colors.textInactive,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { controller.setShuffle(!shuffle) },
                    )
                    Icon(
                        painter = painterResource(
                            if (repeat == RepeatMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
                        ),
                        contentDescription = "repeat",
                        tint = if (repeat != RepeatMode.OFF) colors.accent else colors.textInactive,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { controller.cycleRepeat() },
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        painter = painterResource(R.drawable.ic_heart_broken),
                        contentDescription = "dislike",
                        tint = if (rating == Rating.BROKEN) colors.accent else colors.textInactive,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable {
                                controller.setRating(if (rating == Rating.BROKEN) Rating.NONE else Rating.BROKEN)
                            },
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_heart),
                        contentDescription = "favorite",
                        tint = if (rating == Rating.HEART) colors.accent else colors.textInactive,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable {
                                controller.setRating(if (rating == Rating.HEART) Rating.NONE else Rating.HEART)
                            },
                    )
                }
            }
        }

        // The screensaver: slowly drifting metadata over the photography.
        AnimatedVisibility(
            visible = screensaver,
            enter = fadeIn(DoradoMotion.pivot()),
            exit = fadeOut(DoradoMotion.pivot()),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                screensaver = false
                                overlay = true
                                interactionKey++
                            },
                        )
                    },
            ) {
                ScreensaverLayer(current, positionMs, durationMs, isPlaying)
            }
        }

        // Transport overlay.
        AnimatedVisibility(
            visible = overlay,
            enter = fadeIn(DoradoMotion.pivot()),
            exit = fadeOut(DoradoMotion.pivot()),
        ) {
            TransportOverlay(
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                onDismiss = {
                    overlay = false
                    interactionKey++
                },
            )
        }
    }
}

/** Artist photography with the Zune Ken-Burns drift, or a palette wash. */
@Composable
private fun BackdropLayer(
    track: Track,
    backdropFile: File?,
    washColors: Pair<Color, Color>?,
) {
    val colors = LocalDoradoColors.current
    if (backdropFile != null) {
        val transition = rememberInfiniteTransition(label = "kenburns")
        val scale by transition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(20_000), AnimRepeatMode.Reverse),
            label = "kb-scale",
        )
        val pan by transition.animateFloat(
            initialValue = -8f,
            targetValue = 8f,
            animationSpec = infiniteRepeatable(tween(26_000), AnimRepeatMode.Reverse),
            label = "kb-pan",
        )
        AsyncImage(
            model = android.net.Uri.fromFile(backdropFile),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = pan
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to colors.background.copy(alpha = 0.15f),
                        0.55f to colors.background.copy(alpha = 0.35f),
                        1f to colors.background.copy(alpha = 0.75f),
                    ),
                ),
        )
    } else {
        val top = washColors?.first ?: colors.tile
        val bottom = washColors?.second ?: colors.background
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to top, 1f to bottom)),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background.copy(alpha = 0.25f)),
        )
    }
}

@Composable
private fun ScreensaverLayer(
    track: Track,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
) {
    val colors = LocalDoradoColors.current
    val transition = rememberInfiniteTransition(label = "saver-drift")
    val drift by transition.animateFloat(
        initialValue = 30f,
        targetValue = -110f,
        animationSpec = infiniteRepeatable(tween(18_000), AnimRepeatMode.Reverse),
        label = "saver-y",
    )

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .graphicsLayer { translationY = drift }
                .padding(start = DoradoTokens.EDGE.dp, end = 80.dp),
        ) {
            EdgeCropText(text = track.title, fontSize = DoradoTokens.TYPE_SAVER_TITLE.dp)
            EdgeCropText(
                text = track.artist,
                fontSize = DoradoTokens.TYPE_SAVER_ARTIST.dp,
                alpha = 0.9f,
            )
            EdgeCropText(
                text = track.album,
                fontSize = DoradoTokens.TYPE_SAVER_ALBUM.dp,
                alpha = 0.7f,
            )
            EdgeCropText(
                text = formatTime(durationMs),
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                alpha = 0.5f,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        SpectrumVisualizer(
            isPlaying = isPlaying,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = DoradoTokens.EDGE.dp, end = 84.dp, bottom = DoradoTokens.EDGE.dp),
        )
        AlbumArt(
            model = track.albumArtUri,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(DoradoTokens.EDGE.dp)
                .size(56.dp)
                .graphicsLayer { alpha = 0.92f },
        )
    }
}

/**
 * The transport overlay: play/pause center, volume up/down at top/bottom,
 * previous/next at the sides; vertical swipes adjust volume.
 */
@Composable
private fun TransportOverlay(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val graph = LocalDoradoGraph.current
    val controller = graph.controller
    val colors = LocalDoradoColors.current
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    var volumePulse by remember { mutableStateOf(0) }
    var showQueue by remember { mutableStateOf(false) }

    if (showQueue) {
        QueueOverlay(onDismiss = { showQueue = false })
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.68f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            }
            .pointerInput(Unit) {
                var accumulated = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        accumulated += dragAmount
                        if (abs2(accumulated) > 18f) {
                            val steps = (abs2(accumulated) / 18f).roundToInt()
                            accumulated = 0f
                            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                            // Swipe up = louder, per the device behavior.
                            val target = (now + (if (dragAmount < 0) steps else -steps)).coerceIn(0, max)
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                            volumePulse = target
                        }
                    },
                )
            },
    ) {
        // Back arrow (top-left)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = (DoradoTokens.EDGE - 13).coerceAtLeast(0).dp, top = 2.dp)
                .size(48.dp)
                .clickable { graph.nav.pop() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = "back",
                tint = colors.textPrimary.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp),
            )
        }
        // Volume up (top)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
                .clickable {
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (now < max) {
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, now + 1, 0)
                        volumePulse = now + 1
                    }
                },
        ) {
            OverlayGlyph(text = "+")
        }
        // Volume down (bottom) — above the scrubber.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 52.dp)
                .clickable {
                    val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (now > 0) {
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, now - 1, 0)
                        volumePulse = now - 1
                    }
                },
        ) {
            OverlayGlyph(text = "–")
        }
        // Previous (left)
        Icon(
            painter = painterResource(R.drawable.ic_prev),
            contentDescription = "previous",
            tint = colors.textPrimary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 26.dp)
                .size(34.dp)
                .clickable { controller.previous() },
        )
        // Next (right)
        Icon(
            painter = painterResource(R.drawable.ic_next),
            contentDescription = "next",
            tint = colors.textPrimary,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 26.dp)
                .size(34.dp)
                .clickable { controller.next() },
        )
        // Play/pause (center)
        Icon(
            painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = "play pause",
            tint = colors.textPrimary,
            modifier = Modifier
                .align(Alignment.Center)
                .size(58.dp)
                .clickable { controller.toggle() },
        )
        // Transient volume readout
        if (volumePulse > 0) {
            androidx.compose.foundation.text.BasicText(
                text = "volume $volumePulse",
                style = TextStyle(
                    fontFamily = Selawik,
                    fontSize = DoradoTokens.TYPE_LIST.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp),
            )
        }

        // Showlist / queue (top-right) — the device's GemQueueList.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = (DoradoTokens.EDGE - 13).coerceAtLeast(0).dp, top = 2.dp)
                .size(48.dp)
                .clickable { showQueue = true },
            contentAlignment = Alignment.Center,
        ) {
            OverlayGlyph(text = "\u2261")
        }

        // Scrubber + elapsed/remaining (device HUD ProgressSlider).
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 4.dp),
        ) {
            SeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = { controller.seekTo(it) },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TimeLabel(formatTime(positionMs))
                TimeLabel("-" + formatTime((durationMs - positionMs).coerceAtLeast(0)))
            }
        }
    }
}

/** The device's showlist: the current queue, with tap-to-jump and long-press remove. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueOverlay(onDismiss: () -> Unit) {
    val graph = LocalDoradoGraph.current
    val colors = LocalDoradoColors.current
    val queue by graph.controller.queue.collectAsState()
    val currentIndex by graph.controller.currentIndex.collectAsState()

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.95f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(DoradoTokens.EDGE.dp),
        ) {
            EdgeCropText(
                text = "showlist",
                fontSize = DoradoTokens.TYPE_NOW_META.dp,
                color = colors.accent,
            )
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(queue, key = { index, t -> "$index:${t.mediaId}" }) { index, t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(DoradoTokens.ROW_HEIGHT.dp)
                            .combinedClickable(
                                onClick = {
                                    graph.controller.seekToIndex(index)
                                    onDismiss()
                                },
                                onLongClick = { graph.controller.removeAt(index) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EdgeCropText(
                            text = t.title,
                            fontSize = DoradoTokens.TYPE_LIST.dp,
                            color = if (index == currentIndex) colors.accent else colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        EdgeCropText(
                            text = t.artist,
                            fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
                            alpha = 0.55f,
                        )
                    }
                }
            }
        }
    }
}

/** Hairline seek line with horizontal drag-seek (device HUD ProgressSlider). */
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    val colors = LocalDoradoColors.current
    var dragging by remember { mutableStateOf(false) }
    var dragX by remember { mutableStateOf(0f) }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(20.dp),
    ) {
        val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val progress = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
        val shown = if (dragging) (dragX / width).coerceIn(0f, 1f) else progress
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(durationMs, width) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragX = offset.x
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragX = change.position.x
                        },
                        onDragEnd = {
                            dragging = false
                            if (durationMs > 0) {
                                onSeek(((dragX / width).coerceIn(0f, 1f) * durationMs).toLong())
                            }
                        },
                        onDragCancel = { dragging = false },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.border),
            )
            Box(
                Modifier
                    .fillMaxWidth(shown)
                    .height(1.dp)
                    .background(colors.accent),
            )
        }
    }
}

@Composable
private fun TimeLabel(text: String) {
    val colors = LocalDoradoColors.current
    androidx.compose.foundation.text.BasicText(
        text = text,
        style = TextStyle(
            fontFamily = Selawik,
            fontSize = DoradoTokens.TYPE_CAPTION.sp,
            color = colors.textSecondary,
        ),
    )
}

@Composable
private fun OverlayGlyph(text: String) {
    val colors = LocalDoradoColors.current
    androidx.compose.foundation.text.BasicText(
        text = text,
        style = TextStyle(
            fontFamily = Selawik,
            fontWeight = FontWeight.Light,
            fontSize = 34.sp,
            color = colors.textPrimary,
        ),
    )
}

/** Cached artist background photography fetch. */
@Composable
private fun rememberArtistBackdropFile(track: Track): androidx.compose.runtime.State<File?> {
    val graph = LocalDoradoGraph.current
    val file = androidx.compose.runtime.produceState<File?>(initialValue = null, key1 = track.artist) {
        val enabled = graph.artistImages.settingsSnapshot?.artistImagesEnabled ?: true
        if (!enabled) {
            value = null
        } else {
            value = graph.artistImages.backgroundFor(track.artist)
        }
    }
    return file
}

/** Palette wash extracted from the album art when no photo is available. */
@Composable
private fun rememberArtWash(track: Track): androidx.compose.runtime.State<Pair<Color, Color>?> {
    val context = LocalContext.current
    return androidx.compose.runtime.produceState<Pair<Color, Color>?>(initialValue = null, key1 = track.albumId) {
        value = withContext(Dispatchers.IO) {
            try {
                val bitmap: Bitmap? = context.contentResolver.openInputStream(track.albumArtUri)?.use { input ->
                    android.graphics.BitmapFactory.decodeStream(
                        input,
                        null,
                        android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 },
                    )
                }
                if (bitmap == null) {
                    null
                } else {
                    val palette = androidx.palette.graphics.Palette.from(bitmap).generate()
                    val vibrant = palette.getVibrantColor(
                        palette.getMutedColor(android.graphics.Color.parseColor("#22303C")),
                    )
                    val vibrantBright = palette.getLightVibrantColor(vibrant)
                    val a = android.graphics.Color.valueOf(
                        androidx.core.graphics.ColorUtils.blendARGB(vibrant, android.graphics.Color.WHITE, 0.15f),
                    )
                    val b = android.graphics.Color.valueOf(
                        androidx.core.graphics.ColorUtils.blendARGB(
                            if (vibrantBright != vibrant) vibrantBright else vibrant,
                            android.graphics.Color.BLACK,
                            0.72f,
                        ),
                    )
                    Color(a.red(), a.green(), a.blue()) to Color(b.red(), b.green(), b.blue())
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}

private fun abs2(v: Float): Float = if (v < 0) -v else v

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
