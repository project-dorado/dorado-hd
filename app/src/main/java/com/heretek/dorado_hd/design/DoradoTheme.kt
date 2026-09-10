package com.heretek.dorado_hd.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.heretek.dorado_hd.R

/**
 * Selawik — the OFL Segoe-metric font family that stands in for Zegoe UI.
 * Users may import Zegoe in a later release; Selawik ships by default.
 */
val Selawik = FontFamily(
    Font(R.font.selawkl, FontWeight.Light),
    Font(R.font.selawksl, FontWeight(350)),
    Font(R.font.selawk, FontWeight.Normal),
    Font(R.font.selawksb, FontWeight.SemiBold),
    Font(R.font.selawkb, FontWeight.Bold),
)

val LocalDoradoColors = staticCompositionLocalOf<DoradoColors> {
    error("DoradoColors not provided")
}

@Composable
fun DoradoTheme(
    accent: DoradoAccent = DoradoAccent.PINK,
    content: @Composable () -> Unit,
) {
    val colors = DoradoColors(accent = accent.primary, accentBright = accent.bright)
    CompositionLocalProvider(LocalDoradoColors provides colors) {
        content()
    }
}
