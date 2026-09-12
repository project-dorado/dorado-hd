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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.heretek.dorado_hd.data.security.PinAttemptLimiter
import com.heretek.dorado_hd.data.security.PinLock
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoMotion
import com.heretek.dorado_hd.design.DoradoTokens
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The wake shade (canon §5): a software shade covering the UI after the app
 * leaves the foreground; slide it up to reveal the home screen, exactly as
 * the device did over the user's wallpaper. Translucent, so the wallpaper
 * shows through; user drags the shade up to dismiss.
 *
 * When [pinRequired] the slide gesture is replaced by the device's numeric
 * PIN keypad (`HudPinLockScene`): tokenized, zero radius, no Material chrome.
 * Wrong entries are rate-limited by [PinAttemptLimiter] (5 tries → 30 s).
 */
@Composable
fun LockShade(
    visible: Boolean,
    onUnlock: () -> Unit,
    pinRequired: Boolean = false,
    verifyPin: suspend (String) -> Boolean = { true },
    modifier: Modifier = Modifier,
) {
    val colors = LocalDoradoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    // While shaded, system Back dismisses the shade — but never bypasses a PIN.
    if (visible) {
        androidx.activity.compose.BackHandler { if (!pinRequired) onUnlock() }
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
                .then(
                    if (pinRequired) {
                        Modifier
                    } else {
                        Modifier.pointerInput(Unit) {
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
                        }
                    },
                ),
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

            if (pinRequired) {
                PinLockPanel(
                    verifyPin = verifyPin,
                    onUnlock = onUnlock,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
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
            }
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

/**
 * The device PIN keypad. Digits only; submit with `ok`, back out with `del`.
 * Failures feed [PinAttemptLimiter] so five wrong entries block the pad for
 * 30 s. No plaintext PIN is retained after a submission.
 */
@Composable
private fun PinLockPanel(
    verifyPin: suspend (String) -> Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDoradoColors.current
    val scope = rememberCoroutineScope()
    val limiter = remember { PinAttemptLimiter() }
    var entered by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var cooldownMs by remember { mutableStateOf(0L) }
    var locked by remember { mutableStateOf(false) }

    LaunchedEffect(locked) {
        while (locked) {
            val remaining = limiter.remainingCooldownMs()
            cooldownMs = remaining
            if (remaining <= 0L) locked = false else delay(250)
        }
    }

    fun submit() {
        if (entered.length < PinLock.MIN_LENGTH) {
            message = "enter ${PinLock.MIN_LENGTH}-${PinLock.MAX_LENGTH} digits"
            return
        }
        val candidate = entered
        entered = ""
        scope.launch {
            if (verifyPin(candidate)) {
                limiter.onSuccess()
                message = null
                onUnlock()
            } else {
                limiter.onFailure()
                if (limiter.isLocked()) {
                    message = null
                    locked = true
                } else {
                    message = "incorrect pin"
                }
            }
        }
    }

    Column(
        modifier = modifier.padding(horizontal = DoradoTokens.EDGE.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = "enter pin",
            style = TextStyle(
                fontFamily = Selawik,
                fontSize = DoradoTokens.TYPE_LIST.sp,
                color = colors.textSecondary,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(PinLock.MAX_LENGTH) { index ->
                val filled = index < entered.length
                Box(
                    Modifier
                        .size(10.dp)
                        .background(if (filled) colors.accent else Color.Transparent)
                        .border(0.5.dp, colors.border),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val status = when {
            locked -> "locked · ${cooldownMs / 1000 + 1}s"
            message != null -> message!!
            else -> " "
        }
        BasicText(
            text = status,
            style = TextStyle(
                fontFamily = Selawik,
                fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp,
                color = if (locked) colors.accent else colors.textSecondary,
            ),
        )
        Spacer(Modifier.height(8.dp))
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("del", "0", "ok"),
        )
        rows.forEach { keys ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                keys.forEach { key ->
                    val enabled = !locked &&
                        !(key == "ok" && entered.length < PinLock.MIN_LENGTH) &&
                        !(key == "del" && entered.isEmpty())
                    Box(
                        Modifier
                            .size(width = 46.dp, height = 30.dp)
                            .background(colors.tile)
                            .border(0.5.dp, colors.border)
                            .clickable(enabled = enabled) {
                                when (key) {
                                    "del" -> entered = entered.dropLast(1)
                                    "ok" -> submit()
                                    else -> if (entered.length < PinLock.MAX_LENGTH) entered += key
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = key,
                            style = TextStyle(
                                fontFamily = Selawik,
                                fontSize = DoradoTokens.TYPE_LIST.sp,
                                color = if (enabled) colors.textPrimary else colors.textInactive,
                            ),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
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
