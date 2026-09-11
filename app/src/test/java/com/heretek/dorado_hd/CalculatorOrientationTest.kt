package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.CalculatorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V-01 regression: the calculator must open in portrait/basic mode and the
 * sci/basic keys must toggle without the basic label being forced to wrap.
 */
class CalculatorOrientationTest {

    @Test
    fun `calculator opens in portrait basic mode`() {
        val state = CalculatorState()
        assertFalse("default state must be the portrait keypad", state.landscape)
        assertEquals(0, state.page)
    }

    @Test
    fun `sci and basic keys toggle landscape round trip`() {
        var state = CalculatorState()
        state = state.press("sci", landscapeMode = state.landscape)
        assertTrue("sci switches to the scientific pad", state.landscape)
        // The app forces the portrait flag when "basic" is pressed.
        state = state.press("basic", landscapeMode = false)
        assertFalse("basic returns to the portrait pad", state.landscape)
    }

    @Test
    fun `fn cycles the scientific page without entering landscape`() {
        var state = CalculatorState()
        state = state.press("fn", landscapeMode = state.landscape)
        assertEquals(1, state.page)
        assertFalse(state.landscape)
    }
}
