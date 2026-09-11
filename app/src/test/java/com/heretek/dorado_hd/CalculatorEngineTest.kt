package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.AngleMode
import com.heretek.dorado_hd.ui.apps.CalcEngine
import com.heretek.dorado_hd.ui.apps.CalculatorState
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorEngineTest {

    private fun keys(vararg ks: String): CalculatorState =
        ks.fold(CalculatorState()) { s, k -> s.press(k) }

    @Test
    fun `stacked entry folds the pending operator`() {
        assertEquals(5.0, keys("2", "+", "3", "=").value, 0.0)
    }

    @Test
    fun `pressing an operator after input folds and re-arms`() {
        // 2 + 3 * 4 evaluates as (2 + 3) * 4: one pending expression at a time.
        assertEquals(20.0, keys("2", "+", "3", "*", "4", "=").value, 0.0)
    }

    @Test
    fun `repeat equals repeats the last operation`() {
        val a = keys("2", "+", "3", "=")
        assertEquals(5.0, a.value, 0.0)
        val b = a.press("=")
        assertEquals(8.0, b.value, 0.0)
        assertEquals(11.0, b.press("=").value, 0.0)
    }

    @Test
    fun `parentheses fold through the stack`() {
        assertEquals(16.0, keys("1", "0", "+", "(", "2", "*", "3", ")", "=").value, 0.0)
        assertEquals(20.0, keys("(", "2", "+", "3", ")", "*", "4", "=").value, 0.0)
    }

    @Test
    fun `divide by zero becomes Err and clears operands`() {
        val s = keys("1", "/", "0", "=")
        assertTrue(s.error)
        assertEquals("Err", s.render())
        assertEquals(null, s.pendingOp)
    }

    @Test
    fun `error state locks until a clear key`() {
        val error = keys("1", "/", "0", "=")
        assertEquals("Err", error.press("5").render())
        assertEquals("0", error.press("AC").render())
        assertFalse(error.press("AC").error)
    }

    @Test
    fun `memory store recall and clear`() {
        val stored = keys("5", "M+")
        assertEquals(5.0, stored.memory, 0.0)
        val recalled = stored.press("AC").press("MR")
        assertEquals(5.0, recalled.value, 0.0)
        assertEquals(0.0, recalled.press("MC").press("MR").value, 0.0)
    }

    @Test
    fun `angle modes convert trig input`() {
        assertEquals(1.0, keys("9", "0", "sin").value, 1e-12)
        assertEquals(1.0, keys("1", "0", "0", "grad", "sin").value, 1e-12)
        // sin(pi) in radians rounds to zero at 15 decimals.
        assertEquals(0.0, keys("rad", "pi", "sin").value, 0.0)
        // tan(45 degrees) == 1.
        assertEquals(1.0, keys("4", "5", "tan").value, 1e-12)
    }

    @Test
    fun `inverse trig returns the active unit`() {
        val deg = keys("1", "asin")
        assertEquals(90.0, deg.value, 1e-9)
        val rad = keys("1", "rad", "asin")
        assertEquals(Math.PI / 2.0, rad.value, 1e-9)
    }

    @Test
    fun `factorial caps at 180 and rejects invalid input`() {
        assertEquals(120.0, keys("5", "fact").value, 0.0)
        assertEquals(1.0, keys("0", "fact").value, 0.0)
        assertTrue(keys("1", "±", "fact").error)
        assertTrue(keys("2", ".", "5", "fact").error)
        // 180! overflows a double after the device's cap, so it maps to Err.
        assertTrue(keys("2", "0", "0", "fact").error)
    }

    @Test
    fun `yroot preserves odd signs and rejects even roots of negatives`() {
        assertEquals(-2.0, keys("8", "±", "yroot", "3", "=").value, 1e-9)
        assertTrue(keys("8", "±", "yroot", "2", "=").error)
    }

    @Test
    fun `overflow switches to exponential notation`() {
        assertTrue(CalculatorState.formatValue(1e13).contains("E+13"))
        assertTrue(CalculatorState.formatValue(1e16, landscape = true).contains("E+16"))
        assertEquals("1,234,567", CalculatorState.formatValue(1234567.0))
        assertEquals("999,999,999,999", CalculatorState.formatValue(999_999_999_999.0))
    }

    @Test
    fun `digit caps are 15 portrait and 19 landscape`() {
        var portrait = CalculatorState()
        repeat(20) { portrait = portrait.press("1") }
        assertEquals(15, portrait.display.count { it.isDigit() })

        var landscape = CalculatorState()
        repeat(20) { landscape = landscape.press("1", landscapeMode = true) }
        assertEquals(19, landscape.display.count { it.isDigit() })
    }

    @Test
    fun `negate handles minus zero entry`() {
        val zero = keys("0", "±")
        assertEquals("-0", zero.display)
        assertEquals("-5", zero.press("5").display)
        assertEquals(5.0, zero.press("5").press("±").value, 0.0)
    }

    @Test
    fun `backspace trims digits and clears to zero`() {
        val s = keys("1", "2", "3", "del")
        assertEquals("12", s.display)
        assertEquals(12.0, s.value, 0.0)
        assertEquals("0", keys("1", "del").display)
    }

    @Test
    fun `percent uses the pending base`() {
        assertEquals(220.0, keys("2", "0", "0", "+", "1", "0", "%", "=").value, 0.0)
        assertEquals(0.5, keys("5", "0", "%").value, 0.0)
    }

    @Test
    fun `statistics consume the population`() {
        val s = keys("1", "pop", "2", "pop", "3", "pop")
        assertEquals(6.0, s.press("sum").value, 0.0)
        assertEquals(3.0, s.press("count").value, 0.0)
        assertEquals(2.0, s.press("mean").value, 0.0)
        assertEquals(sqrt(2.0 / 3.0), s.press("stdev").value, 1e-12)
        assertTrue(s.press("clearPop").press("mean").error)
    }

    @Test
    fun `scientific unary functions`() {
        assertEquals(9.0, keys("8", "1", "sqrt").value, 0.0)
        assertEquals(0.25, keys("4", "inv").value, 0.0)
        assertEquals(8.0, keys("2", "cube").value, 0.0)
        assertEquals(1000.0, keys("3", "tenx").value, 0.0)
        assertEquals(1024.0, keys("1", "0", "twox").value, 0.0)
        assertEquals(1.0, keys("0", "exp").value, 0.0)
        assertEquals(2.0, keys("8", "cbrt").value, 1e-12)
    }

    @Test
    fun `power parser stays right associative`() {
        assertEquals(512.0, CalcEngine.eval("2^3^2"))
    }

    @Test
    fun `rand stays in the unit interval and advances`() {
        val a = CalculatorState().press("rand")
        val b = a.press("rand")
        assertTrue(a.value >= 0.0 && a.value < 1.0)
        assertTrue(b.value >= 0.0 && b.value < 1.0)
        assertNotEquals(a.value, b.value)
    }

    @Test
    fun `scientific page counter wraps`() {
        var s = CalculatorState()
        repeat(CalculatorState.SCI_PAGE_COUNT) { s = s.press("fn") }
        assertEquals(0, s.page)
        assertEquals(1, CalculatorState().press("fn").page)
    }

    @Test
    fun `sci toggles landscape and shares the value`() {
        val s = keys("4", "2").press("sci")
        assertTrue(s.landscape)
        assertEquals(42.0, s.value, 0.0)
        assertFalse(s.press("sci").landscape)
    }

    @Test
    fun `angle enum exposes deg rad grad`() {
        assertEquals(3, AngleMode.entries.size)
    }
}
