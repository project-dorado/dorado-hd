package com.heretek.dorado_hd.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.heretek.dorado_hd.data.model.AudiobookPart
import com.heretek.dorado_hd.data.model.AudiobookProgress
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.design.components.KineticList
import com.heretek.dorado_hd.design.components.firstLetterOf
import com.heretek.dorado_hd.media.AudiobookSpeeds
import com.heretek.dorado_hd.media.PlaybackSource
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.launch

/**
 * The music crossbar's `audiobooks` pivot (post-device extension; the device
 * shipped `GemLibraryAudiobookPartScene`, parity audit §2). Books identified by
 * the scanner are listed with author and resume progress.
 */
@Composable
fun AudiobooksTab(onOpenBook: (Long) -> Unit) {
    val graph = LocalDoradoGraph.current
    val books by graph.audiobooks.books().collectAsState(initial = emptyList())

    if (books.isEmpty()) {
        EdgeCropText(
            text = "no audiobooks — import books in settings",
            fontSize = DoradoTokens.TYPE_LIST.dp,
            alpha = 0.5f,
            modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 12.dp),
        )
        return
    }

    KineticList(
        items = books,
        key = { it.id },
        letter = { firstLetterOf(it.title) },
        bottomPadding = 44.dp,
        rowContent = { book, _ ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(DoradoTokens.ROW_HEIGHT.dp)
                    .clickable { onOpenBook(book.id) }
                    .padding(horizontal = DoradoTokens.EDGE.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    EdgeCropText(text = book.title, fontSize = DoradoTokens.TYPE_LIST.dp)
                    EdgeCropText(
                        text = "${book.author} · ${book.progressLabel}",
                        fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
                        color = LocalDoradoColors.current.textSecondary,
                    )
                }
            }
        },
    )
}

/**
 * Book detail: the resume CTA, the ordered parts list with played ticks, and
 * the compact audiobook player (current part + time left, bookmark, speed).
 */
@Composable
fun AudiobookBookScreen(bookId: Long, onBack: () -> Unit) {
    val graph = LocalDoradoGraph.current
    val colors = LocalDoradoColors.current
    val scope = rememberCoroutineScope()
    val book by graph.audiobooks.book(bookId).collectAsState(initial = null)
    val parts by graph.audiobooks.parts(bookId).collectAsState(initial = emptyList())
    val nowPlaying by graph.controller.nowPlaying.collectAsState()
    val positionMs by graph.controller.positionMs.collectAsState()
    val durationMs by graph.controller.durationMs.collectAsState()
    val currentIndex by graph.controller.currentIndex.collectAsState()
    val playingBookId by graph.controller.currentAudiobookId.collectAsState()
    val source by graph.controller.source.collectAsState()
    val speed by graph.controller.speed.collectAsState()

    val current = book
    if (current == null) {
        // First frame (flow not emitted yet) or the book vanished in a rescan:
        // keep the cropped header/back contract rather than a blank screen.
        DetailScaffold(title = "audiobooks", onBack = onBack) {}
        return
    }
    val audiobookPlaying = source == PlaybackSource.AUDIOBOOK
    val thisBookPlaying = audiobookPlaying && playingBookId == current.id

    fun start(index: Int, position: Long) {
        if (parts.isEmpty()) return
        val tracks = parts.map { it.toTrack(current) }
        graph.controller.playAudiobook(
            bookId = current.id,
            tracks = tracks,
            startIndex = index.coerceIn(0, tracks.lastIndex),
            startPositionMs = position.coerceAtLeast(0L),
        )
    }

    DetailScaffold(title = current.title, onBack = onBack) {
        Column(Modifier.fillMaxSize()) {
            EdgeCropText(
                text = "${current.author} · ${current.partCount} parts · ${AudiobookProgress.format(current.totalDurationMs)}",
                fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
                color = colors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 4.dp),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val resume = current.resume
                if (resume != null && resume.partIndex < parts.size) {
                    CtaText(
                        text = "resume part ${resume.partIndex + 1}",
                        onClick = { start(resume.partIndex, resume.positionMs) },
                    )
                    Spacer(Modifier.width(DoradoTokens.EDGE.dp))
                }
                CtaText(text = "play", onClick = { start(0, 0L) })
                val bookmark = current.bookmark
                if (bookmark != null && bookmark.partIndex < parts.size) {
                    Spacer(Modifier.width(DoradoTokens.EDGE.dp))
                    CtaText(
                        text = "bookmark · part ${bookmark.partIndex + 1} · ${AudiobookProgress.format(bookmark.positionMs)}",
                        onClick = { start(bookmark.partIndex, bookmark.positionMs) },
                        muted = true,
                    )
                }
            }

            Box(Modifier.weight(1f)) {
                KineticList(
                    items = parts,
                    key = { it.id },
                    letter = { null },
                    bottomPadding = 44.dp,
                    showAlphabet = false,
                    rowContent = { part, _ ->
                        PartRow(
                            part = part,
                            playing = thisBookPlaying && nowPlaying?.mediaId == part.mediaId,
                            played = current.resume?.let { part.index < it.partIndex } == true,
                            onClick = {
                                val resume = current.resume
                                if (thisBookPlaying && nowPlaying?.mediaId == part.mediaId) {
                                    graph.controller.toggle()
                                } else if (resume != null && resume.partIndex == part.index) {
                                    start(part.index, resume.positionMs)
                                } else {
                                    start(part.index, 0L)
                                }
                            },
                        )
                    },
                )
            }

            CompactPlayer(
                playing = audiobookPlaying,
                thisBook = thisBookPlaying,
                partIndex = currentIndex,
                partCount = current.partCount,
                remainingMs = AudiobookProgress.remainingMs(positionMs, durationMs),
                speed = speed,
                onBookmark = {
                    val part = currentIndex
                    if (thisBookPlaying && part >= 0) {
                        scope.launch { graph.audiobooks.saveBookmark(current.id, part, positionMs) }
                    }
                },
                onSpeed = { graph.controller.setSpeed(it) },
            )
            // Clear the shell's MiniPlayer, which is drawn over the canvas bottom.
            Spacer(Modifier.height(DoradoTokens.MINI_PLAYER_HEIGHT.dp))
        }
    }
}

@Composable
private fun PartRow(
    part: AudiobookPart,
    playing: Boolean,
    played: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(DoradoTokens.ROW_HEIGHT.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = DoradoTokens.EDGE.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EdgeCropText(
            text = "%02d".format(part.index + 1),
            fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
            color = colors.textSecondary,
            modifier = Modifier.width(28.dp),
        )
        Column(Modifier.weight(1f)) {
            EdgeCropText(
                text = part.title,
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = if (playing) colors.accent else colors.textPrimary,
                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Light,
            )
            EdgeCropText(
                text = AudiobookProgress.format(part.durationMs),
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = colors.textSecondary,
            )
        }
        if (playing) {
            EdgeCropText(text = "playing", fontSize = DoradoTokens.TYPE_CAPTION.dp, color = colors.accent)
        } else if (played) {
            EdgeCropText(text = "played", fontSize = DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
        }
    }
}

/**
 * Compact player affordance: what part is being read, time remaining, a
 * bookmark action and the reading-speed steps.
 */
@Composable
private fun CompactPlayer(
    playing: Boolean,
    thisBook: Boolean,
    partIndex: Int,
    partCount: Int,
    remainingMs: Long,
    speed: Float,
    onBookmark: () -> Unit,
    onSpeed: (Float) -> Unit,
) {
    val colors = LocalDoradoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = DoradoTokens.EDGE.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EdgeCropText(
            text = if (playing) {
                "part ${(partIndex + 1).coerceIn(1, partCount.coerceAtLeast(1))} · ${AudiobookProgress.format(remainingMs)} left"
            } else {
                "no book playing"
            },
            fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        if (playing && thisBook) {
            CtaText(text = "bookmark", onClick = onBookmark)
            Spacer(Modifier.width(DoradoTokens.EDGE.dp))
        }
        AudiobookSpeeds.STEPS.forEach { step ->
            EdgeCropText(
                text = AudiobookSpeeds.label(step),
                fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
                color = if (AudiobookSpeeds.isStep(speed) && speed == step) colors.accent else colors.textSecondary,
                fontWeight = if (speed == step) FontWeight.SemiBold else FontWeight.Light,
                modifier = Modifier
                    .clickable { onSpeed(step) }
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun CtaText(text: String, onClick: () -> Unit, muted: Boolean = false) {
    val colors = LocalDoradoColors.current
    EdgeCropText(
        text = text,
        fontSize = DoradoTokens.TYPE_LIST.dp,
        color = if (muted) colors.textSecondary else colors.accent,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
