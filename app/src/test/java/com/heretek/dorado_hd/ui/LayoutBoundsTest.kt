package com.heretek.dorado_hd.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasClickAction
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
 * Layout-bounds gate: no tappable semantics node may sit entirely outside the
 * viewport unless it lives inside a scrollable ancestor (where it is still
 * reachable). Runs at the two adaptive configurations; device mode is covered
 * by the golden suite.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = TestDoradoApp::class)
class LayoutBoundsTest(private val slug: String) {

    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w440dp-h900dp-port-mdpi")
    fun noUnreachableControlsInPortrait() {
        checkBounds(HarnessMode.PORTRAIT)
    }

    @Test
    @Config(qualifiers = "w820dp-h400dp-land-mdpi")
    fun noUnreachableControlsInLandscape() {
        checkBounds(HarnessMode.LANDSCAPE)
    }

    private fun checkBounds(mode: HarnessMode) {
        compose.mainClock.autoAdvance = false
        compose.setContent { MiniAppHarness(slug, mode) }
        compose.mainClock.advanceTimeBy(600)
        val root = compose.onRoot().fetchSemanticsNode()
        val width = root.size.width.toFloat()
        val height = root.size.height.toFloat()
        val violations = compose.onAllNodes(hasClickAction())
            .fetchSemanticsNodes()
            .filter { node ->
                val b = node.boundsInRoot
                val fullyOutside = b.right <= 1f || b.left >= width - 1f ||
                    b.bottom <= 1f || b.top >= height - 1f
                fullyOutside && !hasScrollableAncestor(node)
            }
        assertTrue(
            "'$slug' (${mode.name}) has unreachable controls: " +
                violations.joinToString { it.boundsInRoot.toString() },
            violations.isEmpty(),
        )
    }

    private fun hasScrollableAncestor(node: SemanticsNode): Boolean {
        var parent = node.parent
        while (parent != null) {
            if (parent.config.contains(SemanticsActions.ScrollBy)) return true
            parent = parent.parent
        }
        return false
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun apps(): List<Array<Any>> = DoradoApps.all.map { arrayOf(it.id as Any) }
    }
}
