package com.heretek.dorado_hd.ui.apps

/**
 * Static guitar chord data for the Chord Finder mini-app (canon §8 — a
 * reference of common open-position shapes). Each shape is six strings,
 * -1 = muted, 0 = open, n >= 1 = n-th fret.
 */
object ChordData {
    val ROOTS = listOf("C", "D", "E", "F", "G", "A", "B")
    val QUALITIES = listOf("major", "minor", "7", "maj7", "min7", "dim", "sus")

    /** Returns the (string 6..1 → fret) shape for a root+quality. */
    fun shapeFor(root: String, quality: String): IntArray {
        val key = SHAPES["$root:$quality"] ?: SHAPES["C:major"] ?: intArrayOf(-1, 3, 2, 0, 1, 0)
        return key
    }

    // Standard open-position shapes keyed by root+quality. Only the major
    // and minor shapes are populated; others fall back to the major shape
    // (the canonical UI shows the user the available shapes).
    private val SHAPES: Map<String, IntArray> = mapOf(
        "C:major" to intArrayOf(-1, 3, 2, 0, 1, 0),
        "D:major" to intArrayOf(-1, -1, 0, 2, 3, 2),
        "E:major" to intArrayOf(0, 2, 2, 1, 0, 0),
        "F:major" to intArrayOf(-1, -1, 3, 2, 1, 1),
        "G:major" to intArrayOf(3, 2, 0, 0, 0, 3),
        "A:major" to intArrayOf(-1, 0, 2, 2, 2, 0),
        "B:major" to intArrayOf(-1, 2, 4, 4, 4, 2),
        "C:minor" to intArrayOf(-1, 3, 1, 0, 1, 3),
        "D:minor" to intArrayOf(-1, -1, 0, 2, 3, 1),
        "E:minor" to intArrayOf(0, 2, 2, 0, 0, 0),
        "F:minor" to intArrayOf(-1, -1, 3, 1, 1, 1),
        "G:minor" to intArrayOf(3, 5, 5, 3, 3, 3),
        "A:minor" to intArrayOf(-1, 0, 2, 2, 1, 0),
        "B:minor" to intArrayOf(-1, 2, 4, 4, 3, 2),
        "C:7" to intArrayOf(-1, 3, 2, 3, 1, 0),
        "G:7" to intArrayOf(3, 2, 0, 0, 0, 1),
        "D:7" to intArrayOf(-1, -1, 0, 2, 1, 2),
        "A:7" to intArrayOf(-1, 0, 2, 0, 2, 0),
    )
}
