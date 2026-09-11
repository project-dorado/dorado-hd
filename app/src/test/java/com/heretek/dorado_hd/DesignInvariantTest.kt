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

    // ---- UI parity program guards (docs/ui-parity-audit.md) ----

    @Test
    fun `every mini-app that synthesizes audio starts its synth`() {
        // H-10: a MiniSynth that is constructed but never started makes every
        // SfxBank cue silent. Synth.kt is the class definition itself.
        val offenders = sources("ui/apps")
            .filter { it.name != "Synth.kt" }
            .filter { it.readText().contains("MiniSynth(") }
            .filterNot { it.readText().contains(".start()") }
            .map { it.name }
        assertTrue("MiniSynth without start(): $offenders", offenders.isEmpty())
    }

    @Test
    fun `unkeyed pointer input is explicitly reviewed`() {
        // A pointerInput(Unit) lambda keeps its first composition's captures;
        // files must either read live state (rememberUpdatedState) or be
        // allowlisted here with a reason.
        val reviewed = mapOf(
            "CardViews.kt" to "card chrome labels are static per call site",
            "LabyrinthApp.kt" to "touch pad writes stable state delegates",
            "MusicQuizApp.kt" to "menu taps write stable state delegates",
            "ShellGameApp.kt" to "tap handlers read the live game delegate",
            "ShuffleByAlbumApp.kt" to "transport taps write stable delegates",
            "SliderPuzzleApp.kt" to "solved overlay advances a stable screen state",
            "SnowballApp.kt" to "tap-to-pause reads live state",
        )
        val offenders = sources("ui/apps")
            .filter { it.readText().contains("pointerInput(Unit)") }
            .filterNot { it.readText().contains("rememberUpdatedState") }
            .map { it.name }
            .filterNot { it in reviewed }
        assertTrue(
            "unkeyed pointerInput without review (add to the allowlist with a reason): $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `sub-screens forward their onBack to DetailScaffold`() {
        // A function that declares onBack but never passes it to DetailScaffold
        // silently loses the hardware/header back contract (H-01).
        val scaffold = Regex("DetailScaffold\\((.*?)\\)\\s*\\{", RegexOption.DOT_MATCHES_ALL)
        val offenders = mutableListOf<String>()
        for (file in sources("ui/apps")) {
            val text = file.readText()
            for (m in Regex("fun \\w+\\(([^)]*onBack\\s*:\\s*\\(\\)\\s*->\\s*Unit[^)]*)\\)", RegexOption.DOT_MATCHES_ALL).findAll(text)) {
                val body = text.substring(m.range.last + 1)
                val nextFun = Regex("\\n(@Composable\\s+)?(private |internal )?fun ").find(body)?.range?.first
                val scoped = if (nextFun != null) body.substring(0, nextFun) else body
                for (call in scaffold.findAll(scoped)) {
                    if (!call.groupValues[1].contains("onBack")) {
                        offenders += "${file.name}: DetailScaffold without onBack"
                    }
                }
            }
        }
        assertTrue(offenders.toString(), offenders.isEmpty())
    }

    @Test
    fun `ime handling is wired structurally`() {
        val manifest = File(appDir, "src/main/AndroidManifest.xml").readText()
        assertTrue("manifest must use adjustResize", manifest.contains("windowSoftInputMode=\"adjustResize\""))
        val root = File(appDir, "src/main/java/com/heretek/dorado_hd/ui/DoradoRoot.kt").readText()
        assertTrue("DoradoRoot must apply imePadding()", root.contains("imePadding()"))
    }
}
