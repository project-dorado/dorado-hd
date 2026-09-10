package com.heretek.dorado_hd.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class DoradoAccent(val id: Int, val label: String, val primary: Color, val bright: Color) {
    PINK(0, "zune pink", Color(0xFFFA2A55), Color(0xFFFF4D79)),
    ORANGE(1, "zune orange", Color(0xFFF09609), Color(0xFFFFA726)),
    CYAN(2, "electric cyan", Color(0xFF1BA1E2), Color(0xFF33B5E5)),
    LIME(3, "vivid lime", Color(0xFF339933), Color(0xFF4CAF50)),
    PURPLE(4, "deep purple", Color(0xFFA200FF), Color(0xFFB388FF)),
}

@Immutable
data class DoradoColors(
    val background: Color = Color(0xFF111111),
    val elevated: Color = Color(0xFF181818),
    val tile: Color = Color(0xFF202020),
    val tilePressed: Color = Color(0xFF2A2A2A),
    val border: Color = Color(0xFF2C2C2C),
    val accent: Color,
    val accentBright: Color,
    val textPrimary: Color = Color(0xFFFFFFFF),
    val textSecondary: Color = Color(0xFFFFFFFF).copy(alpha = 0.60f),
    val textInactive: Color = Color(0xFFFFFFFF).copy(alpha = 0.40f),
    val textWatermark: Color = Color(0xFFFFFFFF).copy(alpha = 0.08f),
)
