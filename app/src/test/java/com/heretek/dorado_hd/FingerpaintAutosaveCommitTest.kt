package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.FingerpaintBrush
import com.heretek.dorado_hd.ui.apps.games.FingerpaintEngine
import com.heretek.dorado_hd.ui.apps.games.FingerpaintTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A-29 regression: a brush change commits the live stroke instead of dropping
 * it, and the committed session survives encode/decode.
 */
class FingerpaintAutosaveCommitTest {

    @Test
    fun `commitStrokes keeps the live stroke before a brush change`() {
        var session = FingerpaintEngine.soloSession(seed = 1)
        val brushA = FingerpaintBrush(colorIndex = 1)
        val strokeA = FingerpaintEngine.beginStroke(0, brushA, 10f, 10f)
        val afterA = FingerpaintEngine.commitStrokes(session.document, listOf(strokeA))
        assertEquals(1, afterA.strokes.size)
        session = session.copy(document = afterA)

        // Changing the brush does not touch the document until a new live
        // stroke is committed; the first stroke must still be present.
        val brushB = brushA.withColor(3)
        val strokeB = FingerpaintEngine.beginStroke(0, brushB, 30f, 30f)
        val afterB = FingerpaintEngine.commitStrokes(session.document, listOf(strokeB))
        assertEquals(2, afterB.strokes.size)

        val restored = FingerpaintEngine.decode(FingerpaintEngine.encode(session.copy(document = afterB)))
        assertNotNull(restored)
        assertEquals(2, restored!!.document.strokes.size)
        assertEquals(1, restored.document.strokes[0].colorIndex)
        assertEquals(3, restored.document.strokes[1].colorIndex)
    }

    @Test
    fun `commitStrokes empty batch is a no-op and the document identity holds`() {
        val session = FingerpaintEngine.soloSession(seed = 2)
        val same = FingerpaintEngine.commitStrokes(session.document, emptyList())
        assertTrue(same === session.document)
    }

    @Test
    fun `eraser live stroke is committed as an erase, not a mark`() {
        var session = FingerpaintEngine.soloSession(seed = 3)
        val brush = FingerpaintBrush(colorIndex = 0)
        session = session.copy(
            document = FingerpaintEngine.commitStrokes(
                session.document,
                listOf(FingerpaintEngine.beginStroke(0, brush, 5f, 5f)),
            ),
        )
        val eraser = FingerpaintEngine.beginStroke(0, brush.withTool(FingerpaintTool.ERASER), 5f, 5f)
        val erased = FingerpaintEngine.commitStrokes(session.document, listOf(eraser))
        assertTrue(erased.strokes.isEmpty())
    }
}
