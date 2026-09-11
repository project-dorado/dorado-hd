package com.heretek.dorado_hd

import com.heretek.dorado_hd.data.official.OfficialCatalog
import org.junit.Assert.assertTrue
import org.junit.Test

/** M15 — frozen marketplace catalog search. */
class OfficialCatalogTest {

    @Test
    fun `search matches titles case-insensitively`() {
        assertTrue(OfficialCatalog.search("chess").any { it.title == "Chess" })
        assertTrue(OfficialCatalog.search("CHESS").isNotEmpty())
    }

    @Test
    fun `blank query yields nothing`() {
        assertTrue(OfficialCatalog.search("   ").isEmpty())
    }

    @Test
    fun `unmatched query yields nothing`() {
        assertTrue(OfficialCatalog.search("zzzz-not-a-package").isEmpty())
    }
}
