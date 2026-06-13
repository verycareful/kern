package dev.kern.shared.ui

import android.net.Uri
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Shared editor chrome: a titled top bar with save-state subtitle, back, Save, and
 * Export (Save-as) actions, plus loading/error handling and a content slot. Reused
 * by the document and slide editors; the host wires its ViewModel via callbacks.
 *
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
    content: @Composable (Modifier) -> Unit,
) {
    UnsavedChangesGuard(dirty)
    val snackbar = remember { SnackbarHostState() }
    var errorDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    val scope = rememberCoroutineScope()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (dirty) "Unsaved changes" else "Saved - on device",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (dirty) hue else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { backDispatcher?.onBackPressed() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onSave { ok, msg ->
                            if (ok) scope.launch { snackbar.showSnackbar("Saved") }
                            else errorDialog = "Save failed" to (msg ?: "This file is read-only. Use Export to save a copy.")
                        } },
                        enabled = dirty,
                    ) { Icon(Icons.Default.Save, contentDescription = "Save", tint = if (dirty) hue else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { exportLauncher.launch(exportFileName) }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export a copy")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Text(
                    text = error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                else -> content(Modifier.fillMaxSize())
            }
        }
    }

    errorDialog?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { errorDialog = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = { TextButton(onClick = { errorDialog = null }) { Text("OK") } },
        )
    }
}
