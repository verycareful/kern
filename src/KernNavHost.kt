package dev.kern

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.kern.browser.FileBrowserScreen
import dev.kern.editors.csv.CsvEditorScreen
import dev.kern.editors.excel.ExcelEditorScreen
import dev.kern.editors.pdf.PdfEditorScreen
import dev.kern.editors.pptx.PptEditorScreen
import dev.kern.editors.word.WordEditorScreen
import dev.kern.shared.DocumentFormat

/**
 * Navigation graph for Kern. One Activity, Compose NavHost.
 *
 * External "Open with" intents bypass the browser and land directly on an editor
 * (see [MainActivity]); in-app navigation starts at [Destinations.BROWSER].
 */
object Destinations {
    const val BROWSER = "browser"
    const val ARG_FILE_PATH = "filePath"

    const val CSV = "csv"
    const val EXCEL = "excel"
    const val WORD = "word"
    const val POWERPOINT = "pptx"
    const val PDF = "pdf"

    /** Route a resolved format to its editor destination base (without the argument). */
    fun routeFor(format: DocumentFormat): String = when (format) {
        DocumentFormat.CSV -> CSV
        DocumentFormat.EXCEL -> EXCEL
        DocumentFormat.WORD -> WORD
        DocumentFormat.POWERPOINT -> POWERPOINT
        DocumentFormat.PDF -> PDF
        // EPUB editing is deferred to R.1; route to PDF viewer scope for now.
        DocumentFormat.EPUB -> PDF
    }
}

@Composable
fun KernNavHost(
    startDestination: String = Destinations.BROWSER,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Destinations.BROWSER) {
            FileBrowserScreen(
                onOpenDocument = { format, filePath ->
                    navController.navigate("${Destinations.routeFor(format)}/$filePath")
                },
            )
        }

        editorDestination(Destinations.CSV) { CsvEditorScreen(filePath = it) }
        editorDestination(Destinations.EXCEL) { ExcelEditorScreen(filePath = it) }
        editorDestination(Destinations.WORD) { WordEditorScreen(filePath = it) }
        editorDestination(Destinations.POWERPOINT) { PptEditorScreen(filePath = it) }
        editorDestination(Destinations.PDF) { PdfEditorScreen(filePath = it) }
    }
}

/** Shared helper: every editor takes a single nullable filePath string argument. */
private fun androidx.navigation.NavGraphBuilder.editorDestination(
    base: String,
    screen: @Composable (filePath: String?) -> Unit,
) {
    composable(
        route = "$base/{${Destinations.ARG_FILE_PATH}}",
        arguments = listOf(
            navArgument(Destinations.ARG_FILE_PATH) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { backStackEntry ->
        screen(backStackEntry.arguments?.getString(Destinations.ARG_FILE_PATH))
    }
}
