package dev.kern.shared.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Guards against losing unsaved edits on back navigation. While [dirty], a back
 * gesture (hardware/gesture back, or the top-bar back arrow, which routes through
 * the same dispatcher) is intercepted and a confirmation dialog is shown instead
 * of leaving the editor.
 *
 * On "Discard", the handler disables itself (via [pendingBack]) and the back press
 * is re-dispatched after the next recomposition, so it pops normally rather than
 * re-triggering the dialog. Reused by every editor chrome (grid + document), so
 * the behavior is identical across CSV, Excel, Word, PowerPoint, and EPUB.
 */
@Composable
fun UnsavedChangesGuard(dirty: Boolean) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var showDiscard by remember { mutableStateOf(false) }
    var pendingBack by remember { mutableStateOf(false) }

    BackHandler(enabled = dirty && !pendingBack) { showDiscard = true }

    LaunchedEffect(pendingBack) {
        if (pendingBack) {
            backDispatcher?.onBackPressed()
            pendingBack = false
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard unsaved changes?") },
            text = { Text("You have unsaved changes. Leaving will discard them.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    pendingBack = true
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) { Text("Stay") }
            },
        )
    }
}
