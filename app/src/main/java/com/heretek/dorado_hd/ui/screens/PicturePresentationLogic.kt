package com.heretek.dorado_hd.ui.screens

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Wraps a picture-presentation index into `0 until count`, so a swipe past
 * either end of the current bucket continues at the other end. Returns 0 for
 * an empty bucket.
 */
fun wrapPictureIndex(index: Int, count: Int): Int {
    if (count <= 0) return 0
    val mod = index % count
    return if (mod < 0) mod + count else mod
}

/**
 * Zune-style caption date ("September 12, 2009"); null when the picture has no
 * usable `DATE_TAKEN` (MediaStore reports 0 for unknown).
 */
fun pictureDateLabel(
    dateTaken: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.US,
): String? {
    if (dateTaken <= 0L) return null
    return DateTimeFormatter.ofPattern("MMMM d, yyyy", locale)
        .withZone(zone)
        .format(Instant.ofEpochMilli(dateTaken))
}
