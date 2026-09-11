package com.heretek.dorado_hd.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.heretek.dorado_hd.TestDoradoApp
import com.heretek.dorado_hd.ui.apps.DoradoApps
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden screenshot regression for every registry app, captured at device size
 * (480x272, mdpi) through the real host path.
 *
 * Goldens are recorded on first run and compared on later runs with a small
 * tolerance (pixels whose channels differ by more than 16, up to 1% of the
 * frame). Re-record after an intentional visual change with
 * `-Dgolden.update=true`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = TestDoradoApp::class, qualifiers = "w600dp-h400dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GoldenCaptureTest(private val slug: String) {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun goldenMatches() {
        compose.mainClock.autoAdvance = false
        compose.setContent { MiniAppHarness(slug, HarnessMode.DEVICE) }
        compose.mainClock.advanceTimeBy(900)
        val bitmap = compose.onNodeWithTag("miniapp-harness").captureToImage().asAndroidBitmap()
        assertTrue("empty capture for $slug", bitmap.width > 0 && bitmap.height > 0)

        val dir = File("src/test/goldens")
        dir.mkdirs()
        val golden = File(dir, "$slug.png")
        val update = System.getProperty("golden.update") == "true"
        if (!golden.exists() || update) {
            golden.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return
        }
        val expected = BitmapFactory.decodeFile(golden.absolutePath)
        assertTrue(
            "golden size ${expected.width}x${expected.height} != capture ${bitmap.width}x${bitmap.height} for $slug",
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
        assertTrue("golden mismatch for $slug: %.2f%% pixels differ".format(ratio * 100), ratio < 0.01)
    }

    companion object {
        /**
         * Screens whose entry frame renders the wall clock or today's date.
         * They are covered by the smoke, back-contract and layout suites plus
         * the emulator crawl instead of pixel goldens.
         */
        private val DYNAMIC_TIME_SCREENS = setOf("alarm", "calendar", "weather", "notes")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun apps(): List<Array<Any>> =
            DoradoApps.all.map { it.id }.filterNot { it in DYNAMIC_TIME_SCREENS }
                .map { arrayOf(it as Any) }
    }
}
