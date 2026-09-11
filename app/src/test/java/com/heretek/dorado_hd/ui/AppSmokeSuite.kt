package com.heretek.dorado_hd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.heretek.dorado_hd.TestDoradoApp
import com.heretek.dorado_hd.ui.apps.DoradoApps
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 0 parity smoke gate: every registered mini-app renders through the
 * real host path at DEVICE (480x272), adaptive PORTRAIT and adaptive
 * LANDSCAPE without throwing and with a non-empty semantics tree.
 *
 * This is the catalog-wide safety net: additions to `DoradoApps` are covered
 * automatically, and composition-time crashes fail here before they ship.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = TestDoradoApp::class)
class AppSmokeSuite(private val slug: String) {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersAllParityConfigurations() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Column {
                HarnessMode.entries.forEach { mode ->
                    MiniAppHarness(slug, mode)
                }
            }
        }
        compose.mainClock.advanceTimeBy(750)
        val root = compose.onRoot().fetchSemanticsNode()
        assertTrue(
            "'$slug' rendered an empty semantics tree (all modes)",
            root.children.isNotEmpty(),
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun apps(): List<Array<Any>> =
            DoradoApps.all.map { arrayOf(it.id as Any) }
    }
}
