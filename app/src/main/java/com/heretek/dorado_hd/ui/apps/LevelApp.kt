package com.heretek.dorado_hd.ui.apps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.data.db.NoteEntity
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.design.components.KineticList
import com.heretek.dorado_hd.design.components.firstLetterOf
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import com.heretek.dorado_hd.ui.components.LocalContextMenu
import com.heretek.dorado_hd.ui.components.MenuAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/* ============================== Calculator ============================== */
@Composable
fun LevelApp() {
    val colors = LocalDoradoColors.current
    var xDeg by remember { mutableStateOf(0.0) }
    var yDeg by remember { mutableStateOf(0.0) }
    var surface by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    DisposableEffect(Unit) {
        val sm = context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val sensor = sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                // Convert accelerometer to pitch/roll in degrees.
                val ax = e.values[0].toDouble(); val ay = e.values[1].toDouble(); val az = e.values[2].toDouble()
                val roll = Math.toDegrees(kotlin.math.atan2(ax, kotlin.math.sqrt(ay * ay + az * az)))
                val pitch = Math.toDegrees(kotlin.math.atan2(ay, az))
                xDeg = -roll
                yDeg = pitch
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
        }
        sensor?.let { sm.registerListener(listener, it, android.hardware.SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(listener) }
    }

    DetailScaffold(title = "level") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EdgeCropText(
                text = if (surface) "bubble" else "surface",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.accent,
                modifier = Modifier.combinedClickable(onClick = { surface = !surface }, onLongClick = {}),
            )
            if (surface) {
                Box(
                    Modifier.fillMaxSize().background(colors.elevated),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = "roll %.1f°   pitch %.1f°".format(xDeg, yDeg),
                        style = TextStyle(
                            fontFamily = Selawik,
                            fontWeight = FontWeight.Light,
                            fontSize = DoradoTokens.TYPE_HEADER_CROPPED.sp,
                            color = colors.textPrimary,
                        ),
                    )
                }
            } else {
                BubbleLevel(xDeg, yDeg)
            }
        }
    }
}

@Composable
private fun BubbleLevel(xDeg: Double, yDeg: Double) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.elevated),
    ) {
        // Center cross lines
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.border).align(Alignment.Center))
        Box(Modifier.fillMaxHeight().width(0.5.dp).background(colors.border).align(Alignment.Center))
        // Bubble circle: position proportional to tilt, clamped.
        Box(
            Modifier
                .align(Alignment.Center)
                .size(48.dp)
                .graphicsLayer {
                    translationX = (xDeg / 30.0).toFloat().coerceIn(-1f, 1f) * 80f
                    translationY = (yDeg / 30.0).toFloat().coerceIn(-1f, 1f) * 60f
                }
                .background(if (abs(xDeg) < 1.0 && abs(yDeg) < 1.0) colors.accent else colors.tile),
        )
    }
}

/* ============================== Chord Finder ============================== */
