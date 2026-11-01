package dev.kern.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kern.shared.theme.KernRadius
import dev.kern.shared.theme.KernTheme
import dev.kern.shared.theme.KernType
import dev.kern.shared.theme.PlexMonoFamily

/** Reading a typed page number against the number of pages there are. */
object PageNumber {

    sealed interface Parsed

    /** A page that exists, as a zero-based [index]. */
    data class Index(val index: Int) : Parsed

    /** Input that names no page, with the [message] to show under the field. */
    data class Invalid(val message: String) : Parsed

    /**
     * Reads [input] as a one-based number of a [unit] ("page", "slide", "chapter")
     * out of [total]. Surrounding spaces are ignored.
     */
    fun parse(input: String, total: Int, unit: String): Parsed {
        val text = input.trim()
        val range = "Enter a number from 1 to $total"
        return when {
            total <= 0 -> Invalid("There is no $unit to go to")
            text.isEmpty() -> Invalid("Enter a $unit number")
            !text.all { it in '0'..'9' } -> Invalid(range)
            else -> {
                val number = text.toIntOrNull()
                if (number == null || number !in 1..total) Invalid(range) else Index(number - 1)
            }
        }
    }
}

/**
 * Asks for a [unit] number from 1 to [total] and hands the zero-based index to
 * [onGo]. [current] (zero-based) is shown as the hint. A number out of range is
 * refused under the field rather than clamped, so the dialog never lands
 * somewhere other than where the user asked.
 */
@Composable
fun GoToPageDialog(
    unit: String,
    current: Int,
    total: Int,
    onDismiss: () -> Unit,
    onGo: (index: Int) -> Unit,
) {
    val colors = KernTheme.colors
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun submit() {
        when (val parsed = PageNumber.parse(input, total, unit)) {
            is PageNumber.Index -> onGo(parsed.index)
            is PageNumber.Invalid -> error = parsed.message
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Go to $unit") },
        text = {
            Column {
                Text("1 to $total", style = KernType.body, color = colors.textMid)
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(KernRadius.field))
                        .background(colors.sunken)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    if (input.isEmpty()) {
                        Text("${current + 1}", style = KernType.meta.copy(fontSize = 13.sp), color = colors.textDim)
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it; error = null },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        textStyle = TextStyle(fontFamily = PlexMonoFamily, fontSize = 13.sp, color = colors.text),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                error?.let { message ->
                    Text(message, style = KernType.caption, color = colors.danger, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }) { Text("Go", color = colors.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMid) }
        },
    )
}
