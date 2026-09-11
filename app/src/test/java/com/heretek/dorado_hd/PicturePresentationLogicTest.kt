package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.screens.pictureDateLabel
import com.heretek.dorado_hd.ui.screens.wrapPictureIndex
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * B4 picture presentation: bucket navigation wraps at either end and the
 * caption date is omitted when MediaStore has no DATE_TAKEN.
 */
class PicturePresentationLogicTest {

    @Test
    fun `index stays inside a populated bucket`() {
        assertEquals(0, wrapPictureIndex(0, 3))
        assertEquals(2, wrapPictureIndex(2, 3))
    }

    @Test
    fun `index wraps forward past the end`() {
        assertEquals(0, wrapPictureIndex(3, 3))
        assertEquals(1, wrapPictureIndex(7, 3))
    }

    @Test
    fun `index wraps backward past the start`() {
        assertEquals(2, wrapPictureIndex(-1, 3))
        assertEquals(1, wrapPictureIndex(-8, 3))
    }

    @Test
    fun `empty or invalid bucket maps to zero`() {
        assertEquals(0, wrapPictureIndex(5, 0))
        assertEquals(0, wrapPictureIndex(-5, -2))
    }

    @Test
    fun `unknown date has no caption label`() {
        assertNull(pictureDateLabel(0L))
        assertNull(pictureDateLabel(-1L))
    }

    @Test
    fun `date label is month day year`() {
        val millis = LocalDate.of(2009, 9, 12)
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
        assertEquals("September 12, 2009", pictureDateLabel(millis, ZoneId.of("UTC")))
    }
}
