package com.heretek.dorado_hd

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Design-invariant audit, ported from Dorado's approach. Scans the UI
 * sources for violations of the Zune HD canon (docs/zune-hd-ui-canon.md §7).
 */
class DesignInvariantTest {

    private val appDir: File
        get() = if (File("src/main/java").exists()) File(".") else File("..")

    private val repoRoot: File
        get() = appDir.absoluteFile.normalize().parentFile ?: File(".")

    private fun sources(dir: String): List<File> {
        val root = File(appDir, "src/main/java/com/heretek/dorado_hd/$dir")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun `no corner radius anywhere`() {
        val offenders = sources("")
            .filter { it.readText().contains("RoundedCornerShape") }
            .map { it.path }
        assertTrue(
            "RoundedCornerShape is banned (canon §7, invariant 1): $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `screens consume tokens not raw colors`() {
        val offenders = (sources("ui") + sources("design/components") + sources("ui/apps"))
            .flatMap { file ->
                val text = file.readText()
                Regex("Color\\(0x[0-9A-Fa-f]{8}\\)").findAll(text)
                    .map { "${file.name}: ${it.value}" }
            }
            .toList()
        assertTrue(
            "Screens must use LocalDoradoColors tokens, not raw colors: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `no platform color constants in screens or design components`() {
        // Catches Color.Black / Color.White / Color.Red / etc. in UI surfaces.
        // ui/apps/games/ is an explicit allowlist — card faces (white) and piece
        // stones (white/black) are design intent that maps to no token.
        // Color.Transparent is allowed everywhere (zero-alpha "no fill").
        // PianoDrum.kt may use Color.White for the piano keys (canonically white).
        val nameConstant = Regex("\\bColor\\.(Black|White|Red|Green|Blue|Yellow|Gray|Cyan|Magenta|DarkGray|LightGray)\\b")
        val offenders = (sources("ui") + sources("design/components"))
            .filter { it.path.contains("ui/apps/games/").not() }
            .filter { it.name != "PianoDrum.kt" }
            .flatMap { file ->
                val text = file.readText()
                nameConstant.findAll(text).map { match -> "${file.name}: ${match.value}" }
            }
            .toList()
        assertTrue(
            "Screens must not reference platform color constants (use LocalDoradoColors tokens): $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `navigation uses motion tokens not springs`() {
        val offenders = (sources("ui") + sources("ui/apps"))
            .filter { it.readText().contains("spring") }
            .map { it.name }
        assertTrue(
            "Navigation must use DoradoMotion deceleration (canon §6, invariant 6): $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `mini-app registry is non-empty`() {
        val offenders = sources("ui/apps")
            .filter { it.name == "DoradoApps.kt" }
            .filter { !it.readText().contains("DoradoMiniApp(\"calculator\"") }
            .map { it.path }
        assertTrue("DoradoApps registry should ship utilities (canon §8)", offenders.isEmpty())
    }

    @Test
    fun `canon documents exist`() {
        assertTrue(File(repoRoot, "docs/zune-hd-ui-canon.md").exists())
        assertTrue(File(repoRoot, "docs/design-tokens.md").exists())
        assertTrue(File(repoRoot, "docs/zcp-inventory.md").exists())
        assertTrue(File(repoRoot, "NOTICE.md").exists())
    }
}
