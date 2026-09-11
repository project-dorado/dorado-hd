package com.heretek.dorado_hd.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.heretek.dorado_hd.DoradoApp
import com.heretek.dorado_hd.DoradoGraph
import com.heretek.dorado_hd.design.DoradoTheme
import com.heretek.dorado_hd.design.components.DeviceCanvas
import com.heretek.dorado_hd.ui.components.MenuController
import com.heretek.dorado_hd.ui.screens.MiniAppScreen

/**
 * The three canonical parity configurations. Device mode renders the fixed
 * 480x272 canvas; adaptive modes exercise the density-scaled layouts at a
 * phone portrait and a wide landscape size (see design/components/DeviceCanvas.kt).
 */
enum class HarnessMode(val width: Dp, val height: Dp, val deviceMode: Boolean) {
    DEVICE(480.dp, 272.dp, deviceMode = true),
    PORTRAIT(400.dp, 800.dp, deviceMode = false),
    LANDSCAPE(800.dp, 360.dp, deviceMode = false),
}

/** The real application graph (Robolectric builds DoradoApp in every test). */
fun appGraph(): DoradoGraph =
    ApplicationProvider.getApplicationContext<DoradoApp>().graph

/**
 * Renders one registered mini-app through the real host path (registry lookup
 * + `MiniAppScreen`) inside the real theme and canvas, at [mode]'s size.
 */
@Composable
fun MiniAppHarness(slug: String, mode: HarnessMode) {
    val graph = appGraph()
    DoradoTheme {
        val menus = remember { MenuController() }
        CompositionLocalProvider(
            LocalDoradoGraph provides graph,
            com.heretek.dorado_hd.ui.components.LocalContextMenu provides menus,
        ) {
            Box(Modifier.requiredSize(mode.width, mode.height)) {
                DeviceCanvas(deviceMode = mode.deviceMode) { canvasWidth, _ ->
                    MiniAppScreen(slug, canvasWidth)
                }
            }
        }
    }
}
