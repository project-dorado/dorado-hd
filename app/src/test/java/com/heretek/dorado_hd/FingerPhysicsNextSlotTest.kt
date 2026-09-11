package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.FingerPhysicsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Finger-physics fix: "next" advanced to 9 and then replayed level 9 forever;
 * [FingerPhysicsEngine.nextSlot] now ends the mode instead.
 */
class FingerPhysicsNextSlotTest {

    @Test
    fun `next advances 1 through 9`() {
        assertEquals(2, FingerPhysicsEngine.nextSlot(1))
        assertEquals(5, FingerPhysicsEngine.nextSlot(4))
        assertEquals(9, FingerPhysicsEngine.nextSlot(8))
    }

    @Test
    fun `next past the ninth level signals end of mode`() {
        assertNull(FingerPhysicsEngine.nextSlot(9))
    }
}
