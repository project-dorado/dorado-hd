package com.heretek.dorado_hd

import com.heretek.dorado_hd.data.model.MediaFormats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the HD side of the shared media-format contract. The desktop
 * `MediaFormatsTests` asserts the same lists, so a one-sided change fails here.
 */
class MediaFormatsTest {

    @Test
    fun hdPlayableExtensions_areASubsetOfIngest() {
        MediaFormats.hdPlayableExtensions.forEach { extension ->
            assertTrue("$extension must be ingestable", MediaFormats.ingestExtensions.contains(extension))
        }
    }

    @Test
    fun hdPlayableExtensions_matchTheDesktopContract() {
        assertEquals(
            listOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "mp4", "m4v"),
            MediaFormats.hdPlayableExtensions,
        )
    }

    @Test
    fun ingestExtensions_matchTheDesktopContract() {
        assertEquals(
            listOf("mp3", "m4a", "m4b", "wma", "mp4", "m4v", "flac", "ogg", "opus", "aac"),
            MediaFormats.ingestExtensions,
        )
    }

    @Test
    fun wma_isNotPlayable_andTranscodesToAac() {
        assertFalse(MediaFormats.isHdPlayable("wma"))
        assertTrue(MediaFormats.needsTranscode("wma"))
        assertEquals("m4a", MediaFormats.transcodeTargetFor("wma"))
    }

    @Test
    fun playableFormats_areCopiedVerbatim() {
        listOf("mp3", "m4a", "flac", ".OGG", "  opus  ").forEach { extension ->
            assertTrue(extension, MediaFormats.isHdPlayable(extension))
            assertNull(extension, MediaFormats.transcodeTargetFor(extension))
            assertFalse(extension, MediaFormats.needsTranscode(extension))
        }
    }

    @Test
    fun unknownFormats_fallBackToAac() {
        assertEquals("m4a", MediaFormats.transcodeTargetFor("xyz"))
    }
}
