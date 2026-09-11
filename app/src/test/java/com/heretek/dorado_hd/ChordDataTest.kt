package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.ChordData
import com.heretek.dorado_hd.ui.apps.ChordFinderEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 1 chord-finder rules: 12 enharmonic roots × 8 qualities, musically
 * correct voicings, fret-window shifting, and the built-in metronome maths.
 */
class ChordDataTest {

    /** Open-string pitch classes, low E → high E. */
    private val tuning = intArrayOf(4, 9, 2, 7, 11, 4)

    @Test
    fun `twelve roots and eight qualities`() {
        assertEquals(12, ChordData.ROOTS.size)
        assertEquals(12, ChordData.ROOTS.toSet().size)
        assertEquals(8, ChordData.QUALITIES.size)
        assertEquals(8, ChordData.QUALITIES.toSet().size)
    }

    @Test
    fun `quality keys map to the device names`() {
        assertEquals("Major", ChordData.qualityName("maj"))
        assertEquals("Minor", ChordData.qualityName("minor"))
        assertEquals("Major Flat Five", ChordData.qualityName("maj5"))
        assertEquals("Major Seven", ChordData.qualityName("maj7"))
        assertEquals("Dominant Seven", ChordData.qualityName("dom7"))
        assertEquals("Minor Seven", ChordData.qualityName("min7"))
        assertEquals("Augmented", ChordData.qualityName("aug"))
        assertEquals("Suspended", ChordData.qualityName("sus4"))
        assertNull(ChordData.normalizeQuality("dim"))
    }

    @Test
    fun `every root and quality resolves to voicings`() {
        for (root in ChordData.ROOTS) {
            for (quality in ChordData.QUALITIES) {
                val voicings = ChordData.voicingsFor(root, quality)
                assertTrue("no voicing for $root $quality", voicings.isNotEmpty())
                assertTrue(
                    "multiple voicings expected for $root $quality",
                    voicings.size >= 2,
                )
                assertTrue(
                    "drawn window must fit the board for $root $quality",
                    voicings.all { it.baseFret in 1..12 },
                )
            }
        }
    }

    @Test
    fun `every voicing is six strings and sounds only chord tones`() {
        for (root in ChordData.ROOTS) {
            val rootPitchClass = ChordData.pitchClassOf(root)!!
            for (quality in ChordData.QUALITIES) {
                val tones = ChordData.chordTones(quality)!!.map { (rootPitchClass + it) % 12 }.toSet()
                for (voicing in ChordData.voicingsFor(root, quality)) {
                    assertEquals("$root $quality frets", 6, voicing.frets.size)
                    assertEquals("$root $quality fingers", 6, voicing.fingers.size)
                    assertTrue("$root $quality finger range", voicing.fingers.all { it in 0..4 })
                    for ((string, fret) in voicing.frets.withIndex()) {
                        assertTrue("$root $quality fret range", fret in -1..15)
                        if (fret >= 0) {
                            val note = (tuning[string] + fret) % 12
                            assertTrue(
                                "$root $quality string $string fret $fret sounds $note, not in $tones",
                                note in tones,
                            )
                        }
                    }
                    assertTrue(
                        "$root $quality must sound its root",
                        voicing.frets.withIndex().any { (string, fret) ->
                            fret >= 0 && (tuning[string] + fret) % 12 == rootPitchClass
                        },
                    )
                }
            }
        }
    }

    @Test
    fun `high voicings shift the fret window`() {
        assertEquals(1, ChordData.baseFretFor(listOf(-1, 3, 2, 0, 1, 0)))
        assertEquals(1, ChordData.baseFretFor(listOf(3, 2, 0, 0, 0, 3)))
        assertEquals(8, ChordData.baseFretFor(listOf(8, 10, 10, 9, 8, 8)))
        val cMajor = ChordData.voicingsFor("C", "maj")
        val high = cMajor.first { (it.frets.maxOrNull() ?: 0) > 5 }
        assertEquals(
            high.frets.filter { it > 0 }.minOrNull(),
            high.baseFret,
        )
        assertTrue("C major E-form barre at fret 8", cMajor.any { it.baseFret == 8 })
    }

    @Test
    fun `enharmonic spellings share voicings`() {
        assertEquals(ChordData.voicingsFor("C#", "maj"), ChordData.voicingsFor("Db", "maj"))
        assertEquals(ChordData.voicingsFor("A#", "min7"), ChordData.voicingsFor("Bb", "min7"))
        assertEquals("Db", ChordData.enharmonicOf("C#"))
        assertEquals("A#", ChordData.enharmonicOf("Bb"))
        assertTrue(ChordData.voicingsFor("Gb", "min7").isNotEmpty())
    }

    @Test
    fun `legacy shape api keeps uncatalogued combinations null`() {
        val cMajor = ChordData.shapeFor("C", "major") ?: error("expected C major")
        assertEquals(6, cMajor.size)
        assertEquals(3, cMajor[1])
        assertNull(ChordData.shapeFor("B", "dim"))
        assertNull(ChordData.shapeFor("F", "maj7"))
        assertNull(ChordData.shapeFor("H", "maj"))
    }

    @Test
    fun `metronome weight and accent maths`() {
        assertEquals(40, ChordFinderEngine.bpmForWeight(144.0))
        assertEquals(226, ChordFinderEngine.bpmForWeight(330.0))
        assertEquals(120, ChordFinderEngine.bpmForWeight(224.0))
        assertEquals(144.0, ChordFinderEngine.weightForBpm(40), 1e-9)
        assertEquals(330.0, ChordFinderEngine.weightForBpm(226), 1e-9)
        assertEquals(500.0, ChordFinderEngine.intervalMs(120), 1e-9)
        assertTrue(ChordFinderEngine.isAccent(0))
        assertFalse(ChordFinderEngine.isAccent(1))
        assertTrue(ChordFinderEngine.isAccent(4))
        assertEquals(226, ChordFinderEngine.clampBpm(900))
        assertEquals(40, ChordFinderEngine.clampBpm(1))
    }
}
