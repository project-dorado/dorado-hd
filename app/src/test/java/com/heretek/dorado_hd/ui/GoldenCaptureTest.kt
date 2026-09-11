package com.heretek.dorado_hd.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.heretek.dorado_hd.TestDoradoApp
import com.heretek.dorado_hd.ui.apps.AppClock
import com.heretek.dorado_hd.ui.apps.DoradoApps
import java.io.File
import java.time.ZoneId
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden screenshot regression for every registry app at device (480x272) and
 * adaptive landscape, captured through the real host path with the wall clock
 * frozen so entry frames are deterministic.
 *
 * Goldens are recorded on first run and compared on later runs with a small
 * tolerance (pixels whose channels differ by more than 16, up to 1% of the
 * frame). Re-record after an intentional visual change with
 * `-Dgolden.update=true`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = TestDoradoApp::class, qualifiers = "w600dp-h400dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GoldenCaptureTest(private val slug: String, private val mode: HarnessMode) {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun freezeClock() {
        AppClock.millis = { FIXED_NOW }
        AppClock.zone = ZoneId.of("UTC")
    }

    @After
    fun restoreClock() = AppClock.reset()

    @Test
    fun goldenMatches() {
        compose.mainClock.autoAdvance = false
        compose.setContent { MiniAppHarness(slug, mode) }
        compose.mainClock.advanceTimeBy(900)
        val tag = "miniapp-harness-${mode.name.lowercase()}"
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        assertTrue("empty capture for $slug", bitmap.width > 0 && bitmap.height > 0)

        val dir = File("src/test/goldens")
        dir.mkdirs()
        val suffix = if (mode == HarnessMode.DEVICE) "" else "-${mode.name.lowercase()}"
        val golden = File(dir, "$slug$suffix.png")
        val update = System.getProperty("golden.update") == "true"
        if (!golden.exists() || update) {
            golden.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return
        }
        val expected = BitmapFactory.decodeFile(golden.absolutePath)
        assertTrue(
            "golden size ${expected.width}x${expected.height} != capture ${bitmap.width}x${bitmap.height} for $slug$suffix",
            expected.width == bitmap.width && expected.height == bitmap.height,
        )
        val a = IntArray(bitmap.width * bitmap.height)
        val b = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(a, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        expected.getPixels(b, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var differing = 0
        for (i in a.indices) {
            val p = a[i]
            val q = b[i]
            if (
                abs((p ushr 24 and 0xFF) - (q ushr 24 and 0xFF)) > 16 ||
                abs((p ushr 16 and 0xFF) - (q ushr 16 and 0xFF)) > 16 ||
                abs((p ushr 8 and 0xFF) - (q ushr 8 and 0xFF)) > 16 ||
                abs((p and 0xFF) - (q and 0xFF)) > 16
            ) {
                differing++
            }
        }
        expected.recycle()
        val ratio = differing.toDouble() / a.size
        assertTrue("golden mismatch for $slug$suffix: %.2f%% pixels differ".format(ratio * 100), ratio < 0.01)
    }

    companion object {
        /** 2024-06-15T12:34:00Z — fixed wall clock for deterministic frames. */
        private const val FIXED_NOW = 1718454840000L

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1}")
        fun cases(): List<Array<Any>> = DoradoApps.all.flatMap { app ->
            listOf(
                arrayOf(app.id as Any, HarnessMode.DEVICE as Any),
                arrayOf(app.id as Any, HarnessMode.LANDSCAPE as Any),
            )
        }
    }
}
