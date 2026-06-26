package dev.kern.shared.ui

import android.net.Uri
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.kern.shared.theme.KernTheme
import kotlinx.coroutines.launch

/**
 * Shared editor chrome: a titled top bar with save-state subtitle, back, Save, and
 * Export (Save-as) actions, an optional bottom toolbar, plus loading/error handling
 * and a content slot. Reused by the document and slide editors; the host wires its
 * ViewModel via callbacks.
 *
 * @param hue the file-format identity colour (title square, save-state).
 * @param toolbar optional bottom toolbar (e.g. an [EditorToolbar]).
 * @param content rendered only once loaded; receives a Modifier carrying the
 *        scaffold inset padding to use as its root.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorChrome(
    title: String,
    dirty: Boolean,
    loading: Boolean,
    error: String?,
    hue: Color,
    onSave: (onResult: (Boolean, String?) -> Unit) -> Unit,
    exportMimeType: String,
    exportFileName: String,
    onExportToUri: (Uri, onResult: (Boolean, String?) -> Unit) -> Unit,
    toolbar: (@Composable () -> Unit)? = null,
    extraActions: (@Composable () -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    UnsavedChangesGuard(dirty)
    val snackbar = remember { SnackbarHostState() }
    var errorDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    val scope = rememberCoroutineScope()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val colors = KernTheme.colors

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(exportMimeType),
    ) { uri ->
        if (uri != null) {
            onExportToUri(uri) { ok, msg ->
                if (ok) scope.launch { snackbar.showSnackbar("Exported") }
                else errorDialog = "Export failed" to (msg ?: "Unknown error")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = colors.bg,
        topBar = {
            KernTopBar(
                title = title,
                monoTitle = true,
                formatColor = hue,
                subtitle = if (dirty) "● Unsaved changes" else "Saved · on-device",
                subtitleColor = if (dirty) colors.accent else null,
                onBack = { backDispatcher?.onBackPressed() },
                actions = {
                    extraActions?.invoke()
                    KernIconButton(
                        KernIcons.Save,
                        "Save",
                        onClick = {
                            onSave { ok, msg ->
                                if (ok) scope.launch { snackbar.showSnackbar("Saved") }
                                else errorDialog = "Save failed" to (msg ?: "This file is read-only. Use Export to save a copy.")
                            }
                        },
                        enabled = dirty,
                        tint = if (dirty) colors.accent else null,
                    )
                    KernIconButton(KernIcons.Download, "Export a copy", onClick = { exportLauncher.launch(exportFileName) })
                },
            )
        },
        bottomBar = { toolbar?.invoke() },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.accent)
                error != null -> Text(
                    text = error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = colors.danger,
                )
                else -> content(Modifier.fillMaxSize())
            }
        }
    }

    errorDialog?.let { (dialogTitle, body) ->
        AlertDialog(
            onDismissRequest = { errorDialog = null },
            containerColor = colors.surface,
            title = { Text(dialogTitle) },
            text = { Text(body) },
            confirmButton = { TextButton(onClick = { errorDialog = null }) { Text("OK", color = colors.accent) } },
        )
    }
}
