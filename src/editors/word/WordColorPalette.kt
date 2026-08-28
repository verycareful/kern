package dev.kern.editors.word

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.kern.shared.theme.AccentColor
import dev.kern.shared.theme.KernRadius
import dev.kern.shared.theme.KernTheme
import dev.kern.shared.theme.KernType
import dev.kern.shared.ui.KernBottomSheet
import dev.kern.shared.ui.SectionLabel

private fun hexOf(c: Color): String = "%06X".format(c.toArgb() and 0xFFFFFF)

/** The 12 theme accents, reused as the on-brand text-colour palette. */
val BrandPalette: List<String> = AccentColor.entries.map { hexOf(it.color) }

/** A general document-colour palette (neutrals + saturated + tints). */
val GeneralPalette: List<String> = listOf(
    "000000", "434343", "666666", "999999", "B7B7B7", "CCCCCC",
    "D9D9D9", "EFEFEF", "F3F3F3", "FFFFFF", "980000", "FF0000",
    "FF9900", "FFFF00", "00FF00", "00FFFF", "4A86E8", "0000FF",
    "9900FF", "FF00FF", "E6B8AF", "F4CCCC", "FCE5CD", "FFF2CC",
    "D9EAD3", "D0E0E3", "C9DAF8", "D9D2E9",
)

/**
 * Text-colour picker: an "Automatic" reset, the brand palette, a general palette, a
 * recently-used row, and two editable custom slots that accept a typed or pasted hex.
 * Every choice applies to the current selection and dismisses the sheet.
 */
@Composable
fun WordColorPicker(
    current: String?,
    recent: List<String>,
    customSlots: List<String?>,
    onPick: (String?) -> Unit,
    onSetCustom: (Int, String) -> Boolean,
    onDismiss: () -> Unit,
) {
    val colors = KernTheme.colors
    fun pick(hex: String?) {
        onPick(hex)
        onDismiss()
    }

    KernBottomSheet(onDismiss = onDismiss, title = "Text colour") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(KernRadius.innerSmall))
                    .clickable { pick(null) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(KernRadius.innerSmall))
                        .background(colors.sunken)
                        .border(1.dp, colors.borderSoft, RoundedCornerShape(KernRadius.innerSmall)),
                    contentAlignment = Alignment.Center,
                ) { Text("A", style = KernType.chip, color = colors.textMid) }
                Text("Automatic (default)", style = KernType.body, color = colors.text)
            }

            SectionLabel("Brand")
            SwatchGrid(BrandPalette, current, ::pick)

            SectionLabel("Palette")
            SwatchGrid(GeneralPalette, current, ::pick)

            if (recent.isNotEmpty()) {
                SectionLabel("Recent")
                SwatchGrid(recent, current, ::pick)
            }

            SectionLabel("Custom")
            customSlots.forEachIndexed { index, slot ->
                CustomSlotRow(
                    existing = slot,
                    onApply = { input -> if (onSetCustom(index, input)) { onDismiss(); true } else false },
                    onPickExisting = { pick(it) },
                )
            }
        }
    }
}

@Composable
private fun SwatchGrid(hexes: List<String>, current: String?, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        hexes.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { hex -> Swatch(hex, selected = hex.equals(current, ignoreCase = true), onClick = { onPick(hex) }) }
            }
        }
    }
}

@Composable
private fun Swatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    val colors = KernTheme.colors
    val ring = if (selected) colors.accent else colors.borderSoft
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(KernRadius.innerSmall))
            .background(WordRichText.hexToColor(hex))
            .border(if (selected) 2.dp else 1.dp, ring, RoundedCornerShape(KernRadius.innerSmall))
            .clickable(onClick = onClick),
    )
}

@Composable
private fun CustomSlotRow(
    existing: String?,
    onApply: (String) -> Boolean,
    onPickExisting: (String) -> Unit,
) {
    val colors = KernTheme.colors
    var text by remember(existing) { mutableStateOf(existing ?: "") }
    var invalid by remember { mutableStateOf(false) }
    val preview = WordRichText.normalizeHex(text)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(KernRadius.innerSmall))
                .background(preview?.let { WordRichText.hexToColor(it) } ?: colors.sunken)
                .border(1.dp, colors.borderSoft, RoundedCornerShape(KernRadius.innerSmall))
                .clickable(enabled = preview != null) { preview?.let(onPickExisting) },
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(KernRadius.innerSmall))
                .background(colors.sunken)
                .border(1.dp, if (invalid) colors.danger else colors.borderSoft, RoundedCornerShape(KernRadius.innerSmall))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("#", style = KernType.meta, color = colors.textDim)
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(7); invalid = false },
                singleLine = true,
                textStyle = TextStyle(fontFamily = dev.kern.shared.theme.PlexMonoFamily, fontSize = KernType.meta.fontSize, color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.weight(1f),
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(KernRadius.innerSmall))
                .background(if (preview != null) colors.accentSoft else colors.sunken)
                .clickable { if (!onApply(text)) invalid = true }
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text("Set", style = KernType.chip, color = if (preview != null) colors.accent else colors.textDim)
        }
    }
    if (invalid) {
        Spacer(Modifier.size(2.dp))
        Text("Enter a hex like 2E68C4", style = KernType.caption, color = colors.danger)
    }
}
