package com.heretek.dorado_hd.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics

/**
 * Touch + accessibility contract for mini-app controls.
 *
 * `appTap` replaces `pointerInput { detectTapGestures { ... } }` on buttons and
 * labels: it exposes an accessibility click action (TalkBack can activate the
 * control) and, unlike the raw gesture handler, always invokes the latest
 * lambda, so it cannot capture stale composition state. `indication = null`
 * keeps the flat Zune look (no Material ripple).
 */
@Composable
fun Modifier.appTap(
    label: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    enabled = enabled,
    onClickLabel = label,
    role = Role.Button,
    onClick = onClick,
)

/**
 * Press-and-hold control (e.g. the picture puzzle's hint button): press and
 * release are delivered, and accessibility gets a single click action that
 * performs the press/release pair.
 */
@Composable
fun Modifier.appHold(
    label: String,
    enabled: Boolean = true,
    onPress: () -> Unit,
    onRelease: () -> Unit,
): Modifier {
    val currentPress by rememberUpdatedState(onPress)
    val currentRelease by rememberUpdatedState(onRelease)
    return this
        .semantics {
            onClick(label = label) {
                currentPress()
                currentRelease()
                true
            }
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    currentPress()
                    tryAwaitRelease()
                    currentRelease()
                },
            )
        }
}

/** Marks a non-interactive region (game board, readout) for screen readers. */
fun Modifier.appDescription(description: String): Modifier = semantics {
    contentDescription = description
}
