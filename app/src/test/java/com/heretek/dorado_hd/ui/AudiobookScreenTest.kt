package com.heretek.dorado_hd.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.heretek.dorado_hd.DoradoApp
import com.heretek.dorado_hd.DoradoGraph
import com.heretek.dorado_hd.TestDoradoApp
import com.heretek.dorado_hd.data.scan.AudiobookGrouping
import com.heretek.dorado_hd.design.DoradoTheme
import com.heretek.dorado_hd.ui.components.LocalContextMenu
import com.heretek.dorado_hd.ui.components.MenuController
import com.heretek.dorado_hd.ui.screens.AudiobookBookScreen
import com.heretek.dorado_hd.ui.screens.AudiobooksTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose smoke gate for the D4 audiobook surfaces: the book list and the
 * book detail (parts + resume CTA) render through the real graph without
 * throwing and expose their text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestDoradoApp::class)
class AudiobookScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun book() = AudiobookGrouping.Book(
        key = "dir:/Books/Smoke Alpha",
        title = "Smoke Alpha",
        author = "Jane Author",
        albumId = 7L,
        source = AudiobookGrouping.Source.DIRECTORY,
        parts = listOf(1L, 2L).mapIndexed { index, mediaId ->
            AudiobookGrouping.File(
                mediaId = mediaId,
                title = "Part ${index + 1}",
                artist = "Jane Author",
                album = "Smoke Alpha",
                albumId = 7L,
                genre = "unknown",
                durationMs = 60_000L,
                dateAdded = 0L,
                path = "/Books/Smoke Alpha/${index + 1}.mp3",
                uri = "content://media/external/audio/media/$mediaId",
            )
        },
    )

    /**
     * The app's first-launch MediaStore scan asynchronously rebuilds (and so
     * wipes) the audiobook catalog; wait until it has completed before seeding
     * a fixture, otherwise the scan deletes it mid-test.
     */
    private suspend fun seed(graph: DoradoGraph) {
        withTimeout(15_000) {
            while (!graph.settingsFlow.first().libraryScanned) delay(25)
        }
        graph.audiobooks.rebuild(listOf(book()))
    }

    @Test
    fun `book list renders the title and progress`() {
        val graph = ApplicationProvider.getApplicationContext<DoradoApp>().graph
        runBlocking { seed(graph) }

        compose.setContent {
            DoradoTheme {
                val menus = remember { MenuController() }
                CompositionLocalProvider(
                    LocalDoradoGraph provides graph,
                    LocalContextMenu provides menus,
                ) {
                    Box(Modifier.requiredSize(480.dp, 272.dp)) {
                        AudiobooksTab(onOpenBook = {})
                    }
                }
            }
        }

        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Smoke Alpha", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Smoke Alpha", substring = true).assertExists()
        compose.onNodeWithText("Jane Author", substring = true).assertExists()
        compose.onNodeWithText("not started", substring = true).assertExists()
    }

    @Test
    fun `book detail renders parts and the resume affordances`() {
        val graph = ApplicationProvider.getApplicationContext<DoradoApp>().graph
        val bookId = runBlocking {
            seed(graph)
            graph.audiobooks.books().first().first { it.title == "Smoke Alpha" }.id
        }

        compose.setContent {
            DoradoTheme {
                val menus = remember { MenuController() }
                CompositionLocalProvider(
                    LocalDoradoGraph provides graph,
                    LocalContextMenu provides menus,
                ) {
                    Box(Modifier.requiredSize(480.dp, 272.dp)) {
                        AudiobookBookScreen(bookId = bookId, onBack = {})
                    }
                }
            }
        }

        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Part 1", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Part 1", substring = true).assertExists()
        compose.onNodeWithText("Part 2", substring = true).assertExists()
        compose.onNodeWithText("play", substring = false).assertExists()
        compose.onNodeWithText("bookmark", substring = false).assertDoesNotExist()
    }
}
