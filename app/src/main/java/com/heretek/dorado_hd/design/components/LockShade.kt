package com.heretek.dorado_hd.design.components

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoMotion
import com.heretek.dorado_hd.design.DoradoTokens
import java.util.Date
import kotlinx.coroutines.launch

/**
 * The wake shade (canon §5): a software shade covering the UI after the app
 * leaves the foreground; slide it up to reveal the home screen, exactly as
 * the device did over the user's wallpaper. Translucent, so the wallpaper
 * shows through; user drags the shade up to dismiss.
 */
@Composable
fun LockShade(
    visible: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDoradoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    // While shaded, system Back dismisses the shade instead of backgrounding.
    if (visible) {
        androidx.activity.compose.BackHandler { onUnlock() }
    }

    // System wallpaper (one-shot, decoded off the main thread). Null if the
    // user has no wallpaper or the platform refuses (permission-free API 24+).
    var wallpaper by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(context) {
        wallpaper = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { wallpaperDrawable(context) }
    }

    LaunchedEffect(visible) {
        if (visible) {
            offset.snapTo(0f)
            while (visible) {
                now = System.currentTimeMillis()
                kotlinx.coroutines.delay(15_000)
            }
        } else {
            // Reset so the next reveal never starts pre-translated.
            offset.snapTo(0f)
        }
    }

    val unlockPx = with(LocalDensity.current) { 96.dp.toPx() }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(DoradoMotion.instant()),
        exit = fadeOut(DoradoMotion.pivot()),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = -offset.value }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            scope.launch { offset.snapTo((offset.value - amount).coerceIn(0f, unlockPx * 1.4f)) }
                        },
                        onDragEnd = {
                            if (offset.value > unlockPx) {
                                // Let the slide-off animation finish before the
                                // shade is dismissed (the old code flipped
                                // visibility immediately, so the slide was never
                                // seen).
                                scope.launch {
                                    offset.animateTo(unlockPx * 1.6f, DoradoMotion.pivot())
                                    onUnlock()
                                }
                            } else {
                                scope.launch { offset.animateTo(0f, DoradoMotion.pivot()) }
                            }
                        },
                    )
                },
        ) {
            // Wallpaper underneath the translucent scrim — faithful to the
            // device's "user wallpaper behind a software shade" affordance.
            val wp = wallpaper
            if (wp != null) {
                Image(
                    bitmap = wp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Translucent scrim (alpha 0.6) so the time/date is readable on
            // any wallpaper, but the wallpaper still bleeds through.
            Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.6f)))
            Column(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = DoradoTokens.EDGE.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    text = DateFormat.getTimeFormat(context).format(Date(now)),
                    style = TextStyle(
                        fontFamily = Selawik,
                        fontWeight = FontWeight.Light,
                        fontSize = DoradoTokens.TYPE_HEADER_CROPPED.sp,
                        color = colors.textPrimary,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    text = DateFormat.getDateFormat(context).format(Date(now)),
                    style = TextStyle(
                        fontFamily = Selawik,
                        fontSize = DoradoTokens.TYPE_LIST.sp,
                        color = colors.textSecondary,
                    ),
                )
            }
            BasicText(
                text = "slide up to unlock",
                style = TextStyle(
                    fontFamily = Selawik,
                    fontSize = DoradoTokens.TYPE_NOW_META.sp,
                    color = colors.textSecondary,
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
            )
            BasicText(
                text = "dorado hd",
                style = TextStyle(
                    fontFamily = Selawik,
                    fontSize = DoradoTokens.TYPE_CROSSBAR.sp,
                    color = colors.textWatermark,
                ),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = DoradoTokens.EDGE.dp, bottom = DoradoTokens.EDGE.dp),
            )
        }
    }
}

@SuppressLint("MissingPermission")
private fun wallpaperDrawable(context: android.content.Context): androidx.compose.ui.graphics.ImageBitmap? {
    return try {
        val wm = WallpaperManager.getInstance(context)
        // WallpaperManager.getDrawable() is permission-free on API 24+; the lint
        // baseline flags it because old release notes required MANAGE_EXTERNAL_STORAGE.
        // Wrapped in try/catch so the shade still works on devices that throw.
        val drawable = wm.drawable
        drawable?.let { drawableToBitmap(it) }
    } catch (_: Exception) {
        null
    }
}

private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): androidx.compose.ui.graphics.ImageBitmap {
    val intrinsicW = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1080
    val intrinsicH = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1920
    // Downsample: a 4K wallpaper as ARGB_8888 is ~33MB.
    val scale = minOf(1f, 1080f / intrinsicW, 1920f / intrinsicH)
    val width = (intrinsicW * scale).toInt().coerceAtLeast(1)
    val height = (intrinsicH * scale).toInt().coerceAtLeast(1)
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}
