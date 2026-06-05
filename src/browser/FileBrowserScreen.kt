package dev.kern.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.kern.shared.DocumentFormat
import dev.kern.shared.theme.KernTheme

/**
 * Home screen: lists documents in Documents + Downloads and routes taps to editors.
 *
 * Stub: the file scan (SAF + MANAGE_EXTERNAL_STORAGE, see ADR 003) is not wired yet,
 * so this currently renders the empty state. The design session owns the visual
 * language here - file rows, type icons, grouping, search.
 *
 * @param onOpenDocument invoked with the resolved format and an encoded file path
 *        when the user opens a document.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    onOpenDocument: (format: DocumentFormat, filePath: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(dev.kern.R.string.browser_title)) })
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
