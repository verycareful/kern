package dev.kern.browser

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.kern.shared.DocumentFormat
import dev.kern.shared.io.DocumentIo
import dev.kern.shared.theme.KernTheme
import kotlinx.coroutines.launch

/**
 * Home screen. The Documents + Downloads scan (SAF + MANAGE_EXTERNAL_STORAGE, see
 * ADR 003) is not wired yet, so for now opening a document goes through the system
 * file picker, which needs no storage permission. The full browse/search/grid UI
 * (per the design handoff) lands in a later version.
 *
 * @param onOpenDocument invoked with the resolved format and an encoded content URI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    onOpenDocument: (format: DocumentFormat, filePath: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri)
        val ext = DocumentIo.displayName(context, uri).substringAfterLast('.', "")
        val format = DocumentFormat.fromMimeType(mime) ?: DocumentFormat.fromExtension(ext)
        if (format == null) {
            scope.launch { snackbar.showSnackbar("Unsupported file type") }
        } else {
            DocumentIo.tryPersist(context, uri)
            onOpenDocument(format, Uri.encode(uri.toString()))
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text(stringResource(dev.kern.R.string.browser_title)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { openLauncher.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                text = { Text("Open file") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(dev.kern.R.string.browser_empty),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FileBrowserScreenPreview() {
    KernTheme {
        FileBrowserScreen(onOpenDocument = { _, _ -> })
    }
}
