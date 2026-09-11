package com.heretek.dorado_hd.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.heretek.dorado_hd.TestDoradoApp

/**
 * Harness validation: proves a registered app renders through the real host
 * path at all three parity configurations without throwing. The catalog-wide
 * suite (`AppSmokeSuite`) builds on this.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestDoradoApp::class)
class MiniAppHarnessTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `calculator renders at every parity configuration`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            androidx.compose.foundation.layout.Row {
                HarnessMode.entries.forEach { mode ->
                    MiniAppHarness("calculator", mode)
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        val root = compose.onRoot().fetchSemanticsNode()
        assertTrue("harness produced an empty semantics tree", root.children.isNotEmpty())
    }
}
