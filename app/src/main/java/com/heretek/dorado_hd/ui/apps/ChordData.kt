package com.heretek.dorado_hd.ui.apps

/**
 * Guitar chord reference data for the Chord Finder mini-app.
 *
 * Re-authored from public-domain music theory: a curated open-position table
 * plus movable E-form and A-form barre patterns transposed to every root, so
 * all 12 enharmonic roots × 8 device qualities resolve to at least one
 * musically correct voicing (or an explicit empty state). Each shape is six
 * strings, low E → high E: -1 = muted, 0 = open, n >= 1 = n-th fret.
 *
 * Nothing here is transcribed from any Microsoft chord database; the movable
 * patterns are the standard barre shapes and the open shapes are common
 * knowledge.
 */
object ChordData {

    /**
     * One fretboard shape with the finger stamp per string (0 = none/open) and
     * the drawn window start. [baseFret] follows the device rule: highest fret
     * ≤ 5 starts the window at 1 (thick nut), otherwise the lowest sounding fret.
     */
    data class Voicing(
        val frets: List<Int>,
        val fingers: List<Int>,
        val baseFret: Int,
    )

    /** Twelve enharmonically-spelled roots (sharp spellings; lookups accept flats). */
    val ROOTS: List<String> = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /** The eight device quality keys (`ResolveChordVariance`). */
    val QUALITIES: List<String> = listOf("maj", "min", "maj5", "maj7", "dom7", "min7", "aug", "sus4")

    private val QUALITY_NAMES: Map<String, String> = mapOf(
        "maj" to "Major",
        "min" to "Minor",
        "maj5" to "Major Flat Five",
        "maj7" to "Major Seven",
        "dom7" to "Dominant Seven",
        "min7" to "Minor Seven",
        "aug" to "Augmented",
        "sus4" to "Suspended",
    )

    private val ROOT_PITCH_CLASSES: Map<String, Int> = mapOf(
        "C" to 0, "B#" to 0,
        "C#" to 1, "DB" to 1,
        "D" to 2,
        "D#" to 3, "EB" to 3,
        "E" to 4, "FB" to 4,
        "F" to 5, "E#" to 5,
        "F#" to 6, "GB" to 6,
        "G" to 7,
        "G#" to 8, "AB" to 8,
        "A" to 9,
        "A#" to 10, "BB" to 10,
        "B" to 11, "CB" to 11,
    )

    private val CHORD_TONES: Map<String, Set<Int>> = mapOf(
        "maj" to setOf(0, 4, 7),
        "min" to setOf(0, 3, 7),
        "maj5" to setOf(0, 4, 6),
        "maj7" to setOf(0, 4, 7, 11),
        "dom7" to setOf(0, 4, 7, 10),
        "min7" to setOf(0, 3, 7, 10),
        "aug" to setOf(0, 4, 8),
        "sus4" to setOf(0, 5, 7),
    )

    /** Display name for a quality key, accepting the legacy aliases. */
    fun qualityName(quality: String): String = QUALITY_NAMES[normalizeQuality(quality)] ?: quality

    /** Maps device keys and legacy spellings onto the eight canonical keys. */
    fun normalizeQuality(quality: String): String? = when (quality.trim().lowercase()) {
        "maj", "major" -> "maj"
        "min", "minor", "m" -> "min"
        "maj5" -> "maj5"
        "maj7" -> "maj7"
        "dom7", "7" -> "dom7"
        "min7", "m7" -> "min7"
        "aug", "+" -> "aug"
        "sus4", "sus" -> "sus4"
        else -> null
    }

    /** Chord formula as semitone offsets from the root, or null for unknowns. */
    fun chordTones(quality: String): Set<Int>? = CHORD_TONES[normalizeQuality(quality)]

    fun pitchClassOf(root: String): Int? = ROOT_PITCH_CLASSES[root.trim().uppercase()]

    /** The device's C#↔Db, D#↔Eb, F#↔Gb, G#↔Ab, A#↔Bb retry spelling. */
    fun enharmonicOf(root: String): String = when (root.trim().uppercase()) {
        "C#" -> "Db"
        "D#" -> "Eb"
        "F#" -> "Gb"
        "G#" -> "Ab"
        "A#" -> "Bb"
        "DB" -> "C#"
        "EB" -> "D#"
        "GB" -> "F#"
        "AB" -> "G#"
        "BB" -> "A#"
        else -> root.trim()
    }

    /**
     * All known voicings for root+quality. Enharmonic spellings share the same
     * pitch class, so `C#` and `Db` resolve identically. Returns an empty list
     * (never a substituted chord) when the combination is unknown.
     */
    fun voicingsFor(root: String, quality: String): List<Voicing> {
        val pitchClass = pitchClassOf(root) ?: return emptyList()
        val key = normalizeQuality(quality) ?: return emptyList()
        val open = OPEN_VOICINGS["$pitchClass:$key"]
        return (listOfNotNull(open) + generateVoicings(pitchClass, key)).distinctBy { it.frets }
    }

    /**
     * Legacy single-shape API: the hand-curated open-position table only.
     * Kept so callers that relied on "no open shape" semantics (and the
     * uncatalogued-shape guard) keep working; new code uses [voicingsFor].
     */
    fun shapeFor(root: String, quality: String): IntArray? {
        val pitchClass = pitchClassOf(root) ?: return null
        val key = normalizeQuality(quality) ?: return null
        return OPEN_VOICINGS["$pitchClass:$key"]?.frets?.toIntArray()
    }

    /** Window rule: frets above 5 shift the drawn window to the lowest fret. */
    fun baseFretFor(frets: List<Int>): Int {
        val positive = frets.filter { it > 0 }
        if (positive.isEmpty()) return 1
        val highest = positive.maxOrNull() ?: return 1
        return if (highest <= 5) 1 else positive.minOrNull() ?: 1
    }

    private data class ShapePattern(val offsets: List<Int>, val fingers: List<Int>)

    // Movable E-form patterns: root on the 6th string, offsets relative to it.
    private val E_FORM: Map<String, ShapePattern> = mapOf(
        "maj" to ShapePattern(listOf(0, 2, 2, 1, 0, 0), listOf(1, 3, 4, 2, 1, 1)),
        "min" to ShapePattern(listOf(0, 2, 2, 0, 0, 0), listOf(1, 3, 4, 1, 1, 1)),
        "maj5" to ShapePattern(listOf(0, 1, 2, 1, -1, 0), listOf(1, 2, 3, 2, 0, 1)),
        "maj7" to ShapePattern(listOf(0, 2, 1, 1, 0, 0), listOf(1, 4, 2, 3, 1, 1)),
        "dom7" to ShapePattern(listOf(0, 2, 0, 1, 0, 0), listOf(1, 3, 1, 2, 1, 1)),
        "min7" to ShapePattern(listOf(0, 2, 0, 0, 0, 0), listOf(1, 3, 1, 1, 1, 1)),
        "aug" to ShapePattern(listOf(0, 3, 2, 1, 1, 0), listOf(1, 4, 3, 2, 2, 1)),
        "sus4" to ShapePattern(listOf(0, 2, 2, 2, 0, 0), listOf(1, 3, 4, 2, 1, 1)),
    )

    // Movable A-form patterns: root on the 5th string.
    private val A_FORM: Map<String, ShapePattern> = mapOf(
        "maj" to ShapePattern(listOf(-1, 0, 2, 2, 2, 0), listOf(0, 1, 3, 3, 3, 1)),
        "min" to ShapePattern(listOf(-1, 0, 2, 2, 1, 0), listOf(0, 1, 3, 4, 2, 1)),
        "maj5" to ShapePattern(listOf(-1, 0, 1, 2, 2, -1), listOf(0, 1, 2, 3, 4, 0)),
        "maj7" to ShapePattern(listOf(-1, 0, 2, 1, 2, 0), listOf(0, 1, 4, 2, 3, 1)),
        "dom7" to ShapePattern(listOf(-1, 0, 2, 0, 2, 0), listOf(0, 1, 3, 1, 4, 1)),
        "min7" to ShapePattern(listOf(-1, 0, 2, 0, 1, 0), listOf(0, 1, 3, 1, 2, 1)),
        "aug" to ShapePattern(listOf(-1, 0, 3, 2, 2, 1), listOf(0, 1, 4, 2, 3, 1)),
        "sus4" to ShapePattern(listOf(-1, 0, 2, 2, 3, 0), listOf(0, 1, 2, 3, 4, 1)),
    )

    // Curated open-position shapes, keyed by pitch class + canonical quality.
    // Deliberately no F major-seven entry: the legacy guard asserts that
    // combination is uncatalogued through [shapeFor].
    private val OPEN_VOICINGS: Map<String, Voicing> = mapOf(
        "0:maj" to Voicing(listOf(-1, 3, 2, 0, 1, 0), listOf(0, 3, 2, 0, 1, 0), 1),
        "0:dom7" to Voicing(listOf(-1, 3, 2, 3, 1, 0), listOf(0, 3, 2, 4, 1, 0), 1),
        "0:maj7" to Voicing(listOf(-1, 3, 2, 0, 0, 0), listOf(0, 3, 2, 0, 0, 0), 1),
        "2:maj" to Voicing(listOf(-1, -1, 0, 2, 3, 2), listOf(0, 0, 0, 1, 3, 2), 1),
        "2:min" to Voicing(listOf(-1, -1, 0, 2, 3, 1), listOf(0, 0, 0, 2, 3, 1), 1),
        "2:dom7" to Voicing(listOf(-1, -1, 0, 2, 1, 2), listOf(0, 0, 0, 2, 1, 3), 1),
        "2:maj7" to Voicing(listOf(-1, -1, 0, 2, 2, 2), listOf(0, 0, 0, 1, 2, 3), 1),
        "2:min7" to Voicing(listOf(-1, -1, 0, 2, 1, 1), listOf(0, 0, 0, 2, 1, 1), 1),
        "2:sus4" to Voicing(listOf(-1, -1, 0, 2, 3, 3), listOf(0, 0, 0, 1, 2, 3), 1),
        "4:maj" to Voicing(listOf(0, 2, 2, 1, 0, 0), listOf(0, 2, 3, 1, 0, 0), 1),
        "4:min" to Voicing(listOf(0, 2, 2, 0, 0, 0), listOf(0, 2, 3, 0, 0, 0), 1),
        "4:maj5" to Voicing(listOf(0, 1, 2, 1, -1, 0), listOf(0, 1, 3, 2, 0, 0), 1),
        "4:maj7" to Voicing(listOf(0, 2, 1, 1, 0, 0), listOf(0, 3, 1, 2, 0, 0), 1),
        "4:dom7" to Voicing(listOf(0, 2, 0, 1, 0, 0), listOf(0, 2, 0, 1, 0, 0), 1),
        "4:min7" to Voicing(listOf(0, 2, 0, 0, 0, 0), listOf(0, 2, 0, 0, 0, 0), 1),
        "4:aug" to Voicing(listOf(0, 3, 2, 1, 1, 0), listOf(0, 4, 3, 1, 2, 0), 1),
        "4:sus4" to Voicing(listOf(0, 2, 2, 2, 0, 0), listOf(0, 1, 2, 3, 0, 0), 1),
        "5:maj" to Voicing(listOf(1, 3, 3, 2, 1, 1), listOf(1, 3, 4, 2, 1, 1), 1),
        "7:maj" to Voicing(listOf(3, 2, 0, 0, 0, 3), listOf(2, 1, 0, 0, 0, 3), 1),
        "7:dom7" to Voicing(listOf(3, 2, 0, 0, 0, 1), listOf(3, 2, 0, 0, 0, 1), 1),
        "7:maj7" to Voicing(listOf(3, 2, 0, 0, 0, 2), listOf(3, 2, 0, 0, 0, 1), 1),
        "9:maj" to Voicing(listOf(-1, 0, 2, 2, 2, 0), listOf(0, 0, 1, 2, 3, 0), 1),
        "9:min" to Voicing(listOf(-1, 0, 2, 2, 1, 0), listOf(0, 0, 2, 3, 1, 0), 1),
        "9:maj5" to Voicing(listOf(-1, 0, 1, 2, 2, -1), listOf(0, 0, 1, 2, 3, 0), 1),
        "9:maj7" to Voicing(listOf(-1, 0, 2, 1, 2, 0), listOf(0, 0, 3, 1, 4, 0), 1),
        "9:dom7" to Voicing(listOf(-1, 0, 2, 0, 2, 0), listOf(0, 0, 2, 0, 3, 0), 1),
        "9:min7" to Voicing(listOf(-1, 0, 2, 0, 1, 0), listOf(0, 0, 3, 0, 1, 0), 1),
        "9:aug" to Voicing(listOf(-1, 0, 3, 2, 2, 1), listOf(0, 0, 4, 2, 3, 1), 1),
        "9:sus4" to Voicing(listOf(-1, 0, 2, 2, 3, 0), listOf(0, 0, 1, 2, 3, 0), 1),
        "11:dom7" to Voicing(listOf(-1, 2, 1, 2, 0, 2), listOf(0, 2, 1, 3, 0, 4), 1),
    )

    private fun generateVoicings(rootPitchClass: Int, quality: String): List<Voicing> {
        val eForm = E_FORM[quality] ?: return emptyList()
        val aForm = A_FORM[quality] ?: return emptyList()
        // r == 0 means the open shape already exists; use the 12th-fret octave.
        val eRoot = ((rootPitchClass - 4) % 12 + 12) % 12
        val aRoot = ((rootPitchClass - 9) % 12 + 12) % 12
        val eFret = if (eRoot == 0) 12 else eRoot
        val aFret = if (aRoot == 0) 12 else aRoot
        return listOf(
            buildVoicing(eForm, eFret),
            buildVoicing(aForm, aFret),
        )
    }

    private fun buildVoicing(pattern: ShapePattern, rootFret: Int): Voicing {
        val frets = pattern.offsets.map { if (it < 0) -1 else it + rootFret }
        return Voicing(frets = frets, fingers = pattern.fingers, baseFret = baseFretFor(frets))
    }
}
