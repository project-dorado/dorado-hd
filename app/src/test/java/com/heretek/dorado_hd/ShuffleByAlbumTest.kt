package com.heretek.dorado_hd

import com.heretek.dorado_hd.data.model.Track
import com.heretek.dorado_hd.ui.apps.ShuffleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class ShuffleByAlbumTest {

    private val uri = android.net.Uri.parse("file:///shuffle")

    private fun track(id: Long, artist: String, album: String, albumId: Long, trackNumber: Int): Track =
        Track(id, "song $id", artist, 0, album, albumId, "rock", 180_000, 0, trackNumber, "2020", uri)

    private fun album(albumId: Long, artist: String, tracks: Int = 3): ShuffleEngine.AlbumGroup =
        ShuffleEngine.AlbumGroup(
            albumId = albumId,
            title = "album $albumId",
            artist = artist,
            tracks = (1..tracks).map { n -> track(albumId * 100 + n, artist, "album $albumId", albumId, n) },
        )

    private fun fixture(): List<ShuffleEngine.AlbumGroup> = listOf(
        album(1, "A"), album(2, "A"), album(3, "B"), album(4, "B"), album(5, "C"), album(6, "C"),
        album(7, ShuffleEngine.VARIOUS_ARTISTS), album(8, ShuffleEngine.UNKNOWN_ARTIST), album(9, ShuffleEngine.SOUNDTRACK),
    )

    @Test
    fun `every scoped album appears exactly once`() {
        val state = ShuffleEngine.buildQueue(fixture(), ShuffleEngine.Scope.ARTIST, "A", rng = Random(1))!!
        assertEquals(listOf(1L, 2L), state.albums.map { it.albumId }.sorted())
        assertEquals(2, state.albums.size)
        // Track order within an album is preserved.
        assertTrue(state.albums.all { group -> group.tracks.map { it.trackNumber } == listOf(1, 2, 3) })

        val genre = ShuffleEngine.buildQueue(fixture(), ShuffleEngine.Scope.GENRE, "rock", rng = Random(1))!!
        assertEquals(9, genre.albums.size)
        assertEquals(9, genre.albums.map { it.albumId }.toSet().size)
    }

    @Test
    fun `grouped queue never places the same artist twice in a row`() {
        for (seed in 0L until 25L) {
            val state = ShuffleEngine.buildQueue(fixture(), groupByArtist = true, rng = Random(seed)) ?: error("queue")
            val artists = state.albums.map { it.artist }
            for (i in 1 until artists.size) {
                assertFalse("seed $seed placed ${artists[i]} twice at $i", artists[i - 1] == artists[i])
            }
            assertEquals(9, state.albums.size)
        }
    }

    @Test
    fun `artist scope with grouping excludes placeholder artists`() {
        val state = ShuffleEngine.buildQueue(
            fixture(),
            ShuffleEngine.Scope.ARTIST,
            scopeName = null,
            groupByArtist = true,
            rng = Random(2),
        )!!
        assertTrue(state.albums.none { it.artist in ShuffleEngine.PLACEHOLDER_ARTISTS })
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L, 6L), state.albums.map { it.albumId }.toSet())
    }

    @Test
    fun `next advances songs then albums and reshuffles at the end`() {
        val state = ShuffleEngine.buildQueue(
            listOf(album(1, "A", tracks = 2), album(2, "B", tracks = 2)),
            rng = Random(3),
        )!!
        var next = ShuffleEngine.next(state, rng = Random(4))
        assertEquals(0, next.albumIndex)
        assertEquals(1, next.songIndex)

        next = ShuffleEngine.next(next, rng = Random(5))
        assertEquals(1, next.albumIndex)
        assertEquals(0, next.songIndex)

        var lastSong = next.copy(songIndex = 1)
        assertTrue(ShuffleEngine.atQueueEnd(lastSong))
        val wrapped = ShuffleEngine.next(lastSong, rng = Random(6))
        assertEquals(state.generation + 1, wrapped.generation)
        assertEquals(0, wrapped.albumIndex)
        assertEquals(0, wrapped.songIndex)
        assertEquals(setOf(1L, 2L), wrapped.albums.map { it.albumId }.toSet())
    }

    @Test
    fun `previous starts the previous album at its last track`() {
        val state = ShuffleEngine.buildQueue(
            listOf(album(1, "A", tracks = 2), album(2, "B", tracks = 3)),
            rng = Random(7),
        )!!
        val atSecondAlbum = state.copy(albumIndex = 1, songIndex = 0)
        val previous = ShuffleEngine.previous(atSecondAlbum)
        assertEquals(0, previous.albumIndex)
        assertEquals(1, previous.songIndex)
        // At the very start, back is clamped.
        val clamped = ShuffleEngine.previous(previous.copy(songIndex = 0))
        assertEquals(0, clamped.albumIndex)
        assertEquals(0, clamped.songIndex)
    }

    @Test
    fun `retain current song moves its album to the front`() {
        val state = ShuffleEngine.buildQueue(
            fixture(),
            groupByArtist = true,
            rng = Random(8),
            retainAlbumId = 4L,
            retainSongIndex = 2,
        )!!
        assertEquals(4L, state.albums.first().albumId)
        assertEquals(2, state.songIndex)
        assertEquals(9, state.albums.size)
        assertEquals(9, state.albums.map { it.albumId }.toSet().size)
    }

    @Test
    fun `index mapping matches the flattened queue`() {
        val state = ShuffleEngine.buildQueue(fixture(), rng = Random(9))!!
        val flat = ShuffleEngine.flatten(state)
        assertEquals(9 * 3, flat.size)
        val moved = state.copy(albumIndex = 4, songIndex = 1)
        val index = ShuffleEngine.flatIndexOf(moved)
        assertEquals(4 * 3 + 1, index)
        assertEquals(ShuffleEngine.currentTrack(moved), ShuffleEngine.trackAtFlat(moved, index))
        assertNull(ShuffleEngine.trackAtFlat(moved, 999))
    }

    @Test
    fun `sync follows an external controller track`() {
        val state = ShuffleEngine.buildQueue(fixture(), rng = Random(10))!!
        val target = state.albums[3].tracks[2]
        val synced = ShuffleEngine.syncToMediaId(state, target.mediaId)!!
        assertEquals(3, synced.albumIndex)
        assertEquals(2, synced.songIndex)
        assertNull(ShuffleEngine.syncToMediaId(state, 999_999L))
    }

    @Test
    fun `prefs round trip`() {
        val prefs = ShuffleEngine.ShufflePrefs(true, ShuffleEngine.Scope.GENRE, "rock")
        val decoded = ShuffleEngine.decodePrefs(ShuffleEngine.encodePrefs(prefs))
        assertEquals(prefs, decoded)
        assertEquals(ShuffleEngine.ShufflePrefs(false, ShuffleEngine.Scope.ALL, null), ShuffleEngine.decodePrefs(null))
    }

    @Test
    fun `empty library yields no queue`() {
        assertNull(ShuffleEngine.buildQueue(emptyList()))
        assertNull(
            ShuffleEngine.buildQueue(
                listOf(ShuffleEngine.AlbumGroup(1, "empty", "A", emptyList())),
            ),
        )
    }
}
