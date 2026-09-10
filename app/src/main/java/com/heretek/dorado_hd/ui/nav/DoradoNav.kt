package com.heretek.dorado_hd.ui.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Lightweight hierarchical navigation. The Zune HD has no hardware back:
 * going back is "tap the cut-off header", which calls [pop]. Slides are
 * horizontal with the Zune deceleration curve.
 */
sealed interface DoradoDestination {
    data object Home : DoradoDestination
    data object Quickplay : DoradoDestination
    data object Music : DoradoDestination
    data class Album(val albumId: Long) : DoradoDestination
    data class Artist(val artistId: Long) : DoradoDestination
    data class Genre(val genre: String) : DoradoDestination
    data class PlaylistDetail(val playlistId: Long) : DoradoDestination
    data object NowPlaying : DoradoDestination
    data object Settings : DoradoDestination
    // Media pivots (canon §3.1, Phase 5 surfaces).
    data object Videos : DoradoDestination
    data object Pictures : DoradoDestination
    data object Radio : DoradoDestination
    data object Podcasts : DoradoDestination
    data class PodcastFeed(val feedId: Long) : DoradoDestination
    data object Marketplace : DoradoDestination
    data object Social : DoradoDestination
    data object Internet : DoradoDestination
    // Mini-app platform (canon §8).
    data class MiniApp(val appId: String) : DoradoDestination
    // A single pinned picture viewer — URI is stored in the pin's subLabel.
    data class PictureDetail(val uri: String) : DoradoDestination
}

class DoradoNav {
    private val stack = mutableStateListOf<DoradoDestination>(DoradoDestination.Home)

    val current: DoradoDestination
        get() = stack.lastOrNull() ?: DoradoDestination.Home

    val depth: Int
        get() = stack.size

    fun push(destination: DoradoDestination) {
        stack.add(destination)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun toHome() {
        stack.clear()
        stack.add(DoradoDestination.Home)
    }

    fun replaceTop(destination: DoradoDestination) {
        if (stack.isNotEmpty()) stack[stack.lastIndex] = destination else stack.add(destination)
    }
}
