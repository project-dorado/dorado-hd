package com.heretek.dorado_hd

import com.heretek.dorado_hd.data.official.OfficialCatalog
import com.heretek.dorado_hd.ui.apps.DoradoApps
import java.io.File
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Consistency gate for the official-app reimplementation program
 * (`docs/official-apps.json` ⇔ `OfficialCatalog` ⇔ `DoradoApps` ⇔ specs).
 *
 * The register is the program's single source of truth: every official
 * package is listed there with a wave, status and spec. This test keeps the
 * Kotlin catalog and the app registry from drifting away from it.
 */
@RunWith(RobolectricTestRunner::class)
class OfficialAppsTest {

    private val appDir: File
        get() = if (File("src/main/java").exists()) File(".") else File("..")

    private val repoRoot: File
        get() = appDir.absoluteFile.normalize().parentFile ?: File(".")

    private val register: JSONArray by lazy {
        JSONArray(File(repoRoot, "docs/official-apps.json").readText())
    }

    private fun rows(): List<Map<String, String?>> =
        (0 until register.length()).map { i ->
            val o = register.getJSONObject(i)
            o.keys().asSequence().associateWith { k ->
                if (o.isNull(k)) null else o.optString(k).ifEmpty { null }
            }
        }

    @Test
    fun `register covers all 62 official packages with unique slugs`() {
        assertEquals(62, register.length())
        val slugs = rows().map { it["slug"] }
        assertEquals(slugs.size, slugs.toSet().size)
        assertTrue(slugs.all { !it.isNullOrBlank() })
    }

    @Test
    fun `kotlin catalog matches the register title and exe for every entry`() {
        val registerByTitle = rows().associateBy { it["title"] }
        for (app in OfficialCatalog.all) {
            val row = registerByTitle[app.title]
            assertNotNull("register is missing '${app.title}'", row)
            assertEquals("exe mismatch for ${app.title}", row!!["exe"], app.exe)
        }
        assertEquals(62, OfficialCatalog.all.size)
    }

    @Test
    fun `every installed id in the register resolves to a registered mini-app`() {
        val unresolved = rows()
            .mapNotNull { it["installedId"] }
            .distinct()
            .filter { DoradoApps.byId(it) == null }
        assertTrue("installedId without a DoradoApps entry: $unresolved", unresolved.isEmpty())
    }

    @Test
    fun `native and mock apps have a spec and a resolvable id`() {
        val problems = mutableListOf<String>()
        for (row in rows().filter { it["status"] == "native" || it["status"] == "mock" }) {
            val slug = row["slug"] ?: continue
            val id = row["installedId"]
            if (id.isNullOrBlank()) problems += "$slug: missing installedId"
            if (id != null && DoradoApps.byId(id) == null) problems += "$slug: no registry entry"
            if (!File(repoRoot, "docs/apps/$slug.md").exists()) problems += "$slug: missing spec"
        }
        assertTrue("official-app consistency problems: $problems", problems.isEmpty())
    }

    @Test
    fun `every wave and status value is known`() {
        val waves = setOf("W1", "W2", "W3", "W4", "W5", "W6", "W7")
        val statuses = setOf("native", "mock", "todo")
        val bad = rows().filter { it["wave"] !in waves || it["status"] !in statuses }
        assertTrue("unknown wave/status: $bad", bad.isEmpty())
    }
}
