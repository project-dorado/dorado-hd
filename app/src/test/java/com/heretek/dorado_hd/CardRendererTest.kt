package com.heretek.dorado_hd

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.heretek.dorado_hd.social.CardRenderer
import com.heretek.dorado_hd.social.ZuneCardData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric pixel tests for the Zune Card renderer (M5). Native graphics mode
 * gives real Skia rasterization so the assertions below inspect actual pixels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardRendererTest {

    private val card = ZuneCardData(
        displayName = "Jane Listener",
        handle = "janelistener",
        tracks = 1234,
        hearts = 137,
        plays = 4812,
        memberSince = "september 2009",
        offline = false,
        background = 0xFF111111.toInt(),
        accent = 0xFFFA2A55.toInt(),
        textPrimary = 0xFFFFFFFF.toInt(),
        textSecondary = 0xFF999999.toInt(),
    )

    @Test
    fun `renders requested dimensions`() {
        val bitmap = CardRenderer().render(card, width = 480, height = 270)
        assertEquals(480, bitmap.width)
        assertEquals(270, bitmap.height)
        bitmap.recycle()
    }

    @Test
    fun `renders opaque non blank pixels with the mark and stats`() {
        val bitmap = CardRenderer().render(card)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        assertTrue("every pixel is opaque", pixels.all { (it ushr 24) and 0xFF == 0xFF })
        assertTrue(
            "expected background + accent + text colors, got ${pixels.toHashSet()}",
            pixels.toHashSet().size >= 3,
        )
        assertTrue("accent mark pixels present", pixels.any { it == card.accent })
        bitmap.recycle()
    }

    @Test
    fun `deterministic for fixed input`() {
        val first = CardRenderer().render(card)
        val second = CardRenderer().render(card)
        assertTrue("same inputs must render identical pixels", first.sameAs(second))
        first.recycle()
        second.recycle()
    }

    @Test
    fun `offline flag renders without throwing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = CardRenderer(CardRenderer.selawik(context)).render(card.copy(offline = true))
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        bitmap.recycle()
    }
}
