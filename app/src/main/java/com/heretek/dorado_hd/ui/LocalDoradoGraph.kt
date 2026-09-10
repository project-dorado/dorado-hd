package com.heretek.dorado_hd.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.heretek.dorado_hd.DoradoGraph

val LocalDoradoGraph = staticCompositionLocalOf<DoradoGraph> {
    error("DoradoGraph not provided")
}
