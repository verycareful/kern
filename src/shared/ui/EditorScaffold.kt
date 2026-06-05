package dev.kern.shared.ui

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.kern.shared.theme.KernTheme

/**
 * Shared chrome for every editor screen: a titled top bar and a content slot.
 *
 * Right now each editor passes a placeholder body. As formats are implemented
 * (0.1.1.0 onward) the body becomes the real editing surface. The design session
 * is expected to evolve this scaffold into the shared toolbar referenced in the
 * release plan (consistent toolbar across all editors).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScaffold(
    title: String,
    filePath: String?,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(title) }) },
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
                text = "$title editor",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = filePath?.let { "Opened: $it" }
                    ?: "No file yet. This is a stub surface for the design session.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditorScaffoldPreview() {
    KernTheme {
        EditorScaffold(title = "CSV", filePath = null)
    }
}
