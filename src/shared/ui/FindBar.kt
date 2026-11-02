package dev.kern.shared.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kern.shared.find.FindQuery
import dev.kern.shared.find.FindTarget
import dev.kern.shared.theme.KernColorScheme
import dev.kern.shared.theme.KernRadius
import dev.kern.shared.theme.KernTheme
import dev.kern.shared.theme.KernType

private val FIELD_TEXT_SIZE = 14.sp
private const val MATCH_ALPHA = 0.28f
private const val CURRENT_MATCH_ALPHA = 0.7f

/** How editors paint matches: every match lightly, the current one strongly. */
object FindHighlight {
    fun match(colors: KernColorScheme): Color = colors.accent.copy(alpha = MATCH_ALPHA)
    fun current(colors: KernColorScheme): Color = colors.accent.copy(alpha = CURRENT_MATCH_ALPHA)
    fun matchStyle(colors: KernColorScheme) = SpanStyle(background = match(colors))
    fun currentStyle(colors: KernColorScheme) = SpanStyle(background = current(colors))
}
private val OPTION_CHIP_HEIGHT = 30.dp
private val OPTION_CHIP_MIN_WIDTH = 40.dp

/** The magnifier that opens and closes the find bar; editors place it left of Save. */
@Composable
fun FindButton(target: FindTarget) {
    KernIconButton(
        KernIcons.Search,
        if (target.find.isOpen) "Close find" else "Find in document",
        onClick = target.find::toggle,
        active = target.find.isOpen,
    )
}

/**
 * The find bar, shown under the top bar while [FindTarget.find] is open.
 *
 * First row: the find field with the match count, previous, next and close. Second row:
 * the match case and regex toggles, the bar's message, and the switch that shows the
 * replace row where the format can replace. Back closes the bar before it leaves the editor.
 */
@Composable
fun FindBar(target: FindTarget, modifier: Modifier = Modifier) {
    val find = target.find
    if (!find.isOpen) return
    val colors = KernTheme.colors
    val count = target.matchCount
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler(onBack = find::close)

    Column(modifier.fillMaxWidth().background(colors.bg)) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FindField(
                    value = find.query,
                    onValueChange = { find.query = it },
                    placeholder = "Find",
                    trailing = countLabel(find.query, count, find.currentIn(count), invalid = target.supportsOptions && find.compiled is FindQuery.Invalid),
                    trailingIsWarning = find.query.isNotEmpty() && count == 0,
                    imeAction = ImeAction.Search,
                    onIme = { find.next(count) },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                KernIconButton(KernIcons.ArrowUp, "Previous match", onClick = { find.previous(count) }, enabled = count > 0)
                KernIconButton(KernIcons.ArrowDown, "Next match", onClick = { find.next(count) }, enabled = count > 0)
                KernIconButton(KernIcons.Close, "Close find", onClick = find::close)
            }
            Row(
                modifier = Modifier.padding(top = 6.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val options = target.supportsOptions
                OptionChip("Aa", "Match case", selected = options && find.matchCase, enabled = options) {
                    find.matchCase = !find.matchCase
                }
                OptionChip(".*", "Regular expression", selected = options && find.regex, enabled = options) {
                    find.regex = !find.regex
                }
                val invalid = if (options) find.compiled as? FindQuery.Invalid else null
                val note = invalid?.message ?: find.message?.text
                val noteIsError = invalid != null || find.message?.isError == true
                Text(
                    text = note.orEmpty(),
                    style = KernType.meta,
                    color = if (noteIsError) colors.danger else colors.textMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (target.canReplace) {
                    OptionChip(
                        "Replace",
                        if (find.isReplaceOpen) "Hide replace" else "Show replace",
                        selected = find.isReplaceOpen,
                    ) { find.isReplaceOpen = !find.isReplaceOpen }
                }
            }
            if (target.canReplace && find.isReplaceOpen) {
                Row(
                    modifier = Modifier.padding(top = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FindField(
                        value = find.replacement,
                        onValueChange = { find.replacement = it },
                        placeholder = if (find.regex && target.supportsOptions) "Replace (\$1 for group 1)" else "Replace with",
                        imeAction = ImeAction.Done,
                        onIme = target::replaceCurrent,
                        modifier = Modifier.weight(1f),
                    )
                    ToolbarButton(KernIcons.Pen, "Replace this match", onClick = target::replaceCurrent, label = "Replace", enabled = count > 0)
                    ToolbarButton(KernIcons.Check, "Replace all matches", onClick = target::replaceAll, label = "All", enabled = count > 0)
                }
            }
        }
        KernDivider()
    }
}

private fun countLabel(query: String, count: Int, current: Int, invalid: Boolean): String = when {
    query.isEmpty() || invalid -> ""
    count == 0 -> "0/0"
    else -> "${current + 1}/$count"
}

@Composable
private fun FindField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    imeAction: ImeAction,
    onIme: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String = "",
    trailingIsWarning: Boolean = false,
) {
    val colors = KernTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = colors.text, fontSize = FIELD_TEXT_SIZE, fontFamily = FontFamily.Monospace),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = imeAction, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onAny = { onIme() }),
        modifier = modifier
            .clip(RoundedCornerShape(KernRadius.field))
            .background(colors.sunken)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = colors.textDim, fontSize = FIELD_TEXT_SIZE, maxLines = 1)
                    inner()
                }
                if (trailing.isNotEmpty()) {
                    Text(
                        trailing,
                        style = KernType.meta,
                        color = if (trailingIsWarning) colors.danger else colors.textMid,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
    )
}

/** A small text toggle: the match case and regex switches, and the replace row switch. */
@Composable
private fun OptionChip(
    label: String,
    description: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = KernTheme.colors
    val content = when {
        !enabled -> colors.textDim.copy(alpha = 0.45f)
        selected -> colors.accent
        else -> colors.textMid
    }
    Box(
        modifier = Modifier
            .height(OPTION_CHIP_HEIGHT)
            .widthIn(min = OPTION_CHIP_MIN_WIDTH)
            .clip(RoundedCornerShape(KernRadius.innerSmall))
            .background(if (selected) colors.accentSoft else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Switch
                this.selected = selected
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = KernType.chip.copy(fontFamily = FontFamily.Monospace), color = content, maxLines = 1)
    }
}
