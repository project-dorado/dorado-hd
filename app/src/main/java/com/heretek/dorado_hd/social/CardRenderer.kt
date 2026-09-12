package com.heretek.dorado_hd.social

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.heretek.dorado_hd.R

/**
 * Immutable inputs for the Zune Card renderer. Colors are plain ARGB ints so
 * the renderer can be exercised without a Compose runtime; callers supply them
 * from [com.heretek.dorado_hd.design.LocalDoradoColors] tokens.
 */
data class ZuneCardData(
    val displayName: String,
    val handle: String = "",
    val tracks: Int,
    val hearts: Int,
    val plays: Int,
    val memberSince: String = "",
    val offline: Boolean = false,
    val background: Int,
    val accent: Int,
    val textPrimary: Int,
    val textSecondary: Int,
)

/**
 * Renders the local Zune Card (M5/D2) to an ARGB_8888 [Bitmap] on a fixed
 * 480x272 logical grid that is scaled to the requested pixel size. Every draw
 * call is deterministic for fixed inputs, so the output is repeatable and can
 * be golden/pixel-asserted under Robolectric.
 *
 * The mark is our own: an equalizer-bar glyph plus the `dorado` wordmark. No
 * Microsoft artwork or fonts are used; the shipped Selawik face is injected as
 * a [Typeface] (see [selawik]) so the core render stays testable.
 */
class CardRenderer(private val typeface: Typeface? = null) {

    fun render(
        card: ZuneCardData,
        width: Int = CARD_WIDTH,
        height: Int = CARD_HEIGHT,
    ): Bitmap {
        require(width > 0 && height > 0) { "card bitmap must be positive" }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(card.background)
        canvas.save()
        canvas.scale(width / LOGICAL_WIDTH, height / LOGICAL_HEIGHT)

        val accent = fill(card.accent)
        val primary = fill(card.textPrimary)
        val secondary = fill(card.textSecondary)
        val typeface = typeface

        // Accent spine down the left edge (zero corner radius, canon §2).
        canvas.drawRect(0f, 0f, SPINE, LOGICAL_HEIGHT, accent)

        // Dorado mark: three rising equalizer bars + wordmark.
        for (i in BAR_HEIGHTS.indices) {
            val x = MARK_X + i * (MARK_BAR_W + MARK_GAP)
            val barHeight = BAR_HEIGHTS[i]
            canvas.drawRect(x, MARK_BASE - barHeight, x + MARK_BAR_W, MARK_BASE, accent)
        }
        canvas.drawText(
            "dorado",
            MARK_X + BAR_HEIGHTS.size * (MARK_BAR_W + MARK_GAP) + 8f,
            MARK_BASE,
            text(card.textPrimary, 22f, typeface),
        )

        canvas.drawText("zune card", CONTENT_X, 42f, text(card.accent, 13f, typeface))

        // Name crops at the right edge, the Zune typography gesture (canon §2).
        canvas.save()
        canvas.clipRect(CONTENT_X, 62f, LOGICAL_WIDTH - CONTENT_X, 100f)
        canvas.drawText(
            card.displayName.ifBlank { "you" },
            CONTENT_X,
            96f,
            text(card.textPrimary, 34f, typeface),
        )
        canvas.restore()

        val sub = buildString {
            if (card.handle.isNotBlank()) append("@${card.handle}   ")
            append("member since ")
            append(card.memberSince.ifBlank { "september 2009" })
        }
        canvas.drawText(sub, CONTENT_X, 124f, text(card.textSecondary, 13f, typeface))

        stat(canvas, CONTENT_X, "tracks", card.tracks, primary, secondary, typeface)
        stat(canvas, CONTENT_X + STAT_STEP, "hearts", card.hearts, primary, secondary, typeface)
        stat(canvas, CONTENT_X + 2 * STAT_STEP, "plays", card.plays, primary, secondary, typeface)

        canvas.drawRect(CONTENT_X, 238f, LOGICAL_WIDTH - CONTENT_X, 239f, secondary)
        canvas.drawText(
            if (card.offline) "offline - local zune card" else "made with dorado-hd",
            CONTENT_X,
            258f,
            text(card.textSecondary, 11f, typeface),
        )

        canvas.restore()
        return bitmap
    }

    private fun stat(
        canvas: Canvas,
        x: Float,
        label: String,
        value: Int,
        primary: Paint,
        secondary: Paint,
        typeface: Typeface?,
    ) {
        canvas.drawText(value.toString(), x, 182f, text(primary.color, 30f, typeface))
        canvas.drawText(label, x, 202f, text(secondary.color, 12f, typeface))
    }

    private fun fill(argb: Int): Paint = Paint().apply {
        color = argb
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private fun text(argb: Int, size: Float, typeface: Typeface?): Paint = Paint().apply {
        color = argb
        this.textSize = size
        style = Paint.Style.FILL
        isAntiAlias = true
        this.typeface = typeface
    }

    companion object {
        const val LOGICAL_WIDTH = 480f
        const val LOGICAL_HEIGHT = 272f
        const val CARD_WIDTH = 960
        const val CARD_HEIGHT = 540

        private const val SPINE = 5f
        private const val CONTENT_X = 26f
        private const val STAT_STEP = 150f
        private const val MARK_X = 26f
        private const val MARK_BASE = 52f
        private const val MARK_BAR_W = 6f
        private const val MARK_GAP = 3f

        private val BAR_HEIGHTS = floatArrayOf(12f, 20f, 28f)

        /** The shipped OFL Selawik light face; null when resources are unavailable. */
        fun selawik(context: Context): Typeface? =
            runCatching { ResourcesCompat.getFont(context, R.font.selawkl) }.getOrNull()
    }
}
