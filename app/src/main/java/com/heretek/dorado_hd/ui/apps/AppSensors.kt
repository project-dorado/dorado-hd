package com.heretek.dorado_hd.ui.apps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.atan2
import kotlin.math.sqrt

/** Smoothed device tilt in degrees; [available] is false without hardware. */
data class TiltState(
    val rollDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val available: Boolean = false,
)

/**
 * Shared accelerometer helper for mini-apps (level, tilt-mode calculator,
 * motion games). Follows the Level app's existing convention: roll/pitch from
 * the gravity vector, UI-rate sampling, exponential smoothing.
 */
@Composable
fun rememberTilt(enabled: Boolean = true, smoothing: Float = 0.85f): State<TiltState> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(TiltState()) }
    DisposableEffect(enabled, context) {
        if (!enabled) {
            state.value = TiltState()
            return@DisposableEffect onDispose { }
        }
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) {
            state.value = TiltState(available = false)
            return@DisposableEffect onDispose { }
        }
        val smooth = FloatArray(3)
        var primed = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val raw = event.values
                if (!primed) {
                    smooth[0] = raw[0]; smooth[1] = raw[1]; smooth[2] = raw[2]
                    primed = true
                }
                for (i in 0..2) {
                    smooth[i] = smoothing * smooth[i] + (1f - smoothing) * raw[i]
                }
                val x = smooth[0]
                val y = smooth[1]
                val z = smooth[2]
                val roll = Math.toDegrees(atan2(y.toDouble(), z.toDouble())).toFloat()
                val pitch = Math.toDegrees(atan2(-x.toDouble(), sqrt((y * y + z * z).toDouble()))).toFloat()
                state.value = TiltState(roll, pitch, available = true)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }
    return state
}
