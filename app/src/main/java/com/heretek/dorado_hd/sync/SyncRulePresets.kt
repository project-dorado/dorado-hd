package com.heretek.dorado_hd.sync

/**
 * The rule labels the Device view cycles through, chosen so the desktop
 * `ParseTrailingCount`/mode keywords resolve to the intended [SyncMode].
 * Labels mirror the desktop Settings wording.
 */
object SyncRulePresets {
    val MUSIC = listOf("All Music (Automatic Sync)", "Selected Favorites", "Manual")
    val PODCASTS = listOf("All Unplayed Episodes", "3 Newest Episodes", "Manual")
    val MEDIA = listOf("All Videos & Pictures", "Newest 50 Items", "Manual")

    /** Next preset after [current], wrapping; unknown labels fall back to first. */
    fun next(options: List<String>, current: String): String {
        val index = options.indexOf(current)
        return if (index < 0) options.first() else options[(index + 1) % options.size]
    }
}
