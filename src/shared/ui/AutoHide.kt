package dev.kern.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Visibility of a control that shows on activity and fades out after a pause.
 *
 * [reveal] shows the control and restarts the pause. While the control is
 * [hold]ing (a finger is on it) it never hides; [release] starts the pause again.
 */
@Stable
class AutoHide(initiallyVisible: Boolean) {
    var visible by mutableStateOf(initiallyVisible)
        private set
    var activity by mutableIntStateOf(0)
        private set
    var held by mutableStateOf(false)
        private set

    fun reveal() {
        visible = true
        activity++
    }

    fun hold() {
        held = true
        reveal()
    }

    fun release() {
        held = false
        reveal()
    }

    fun hide() {
        visible = false
    }
}

/** An [AutoHide] that hides [hideDelayMs] after the last activity, unless held. */
@Composable
fun rememberAutoHide(hideDelayMs: Long, initiallyVisible: Boolean = true): AutoHide {
    val autoHide = remember { AutoHide(initiallyVisible) }
    LaunchedEffect(autoHide, hideDelayMs) {
        snapshotFlow { autoHide.activity to autoHide.held }.collectLatest { (_, held) ->
            if (held) return@collectLatest
            delay(hideDelayMs)
            autoHide.hide()
        }
    }
    return autoHide
}
