package dev.kern.shared.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kern.shared.DocumentFormat
import dev.kern.shared.theme.KernRadius
import dev.kern.shared.theme.KernTheme
import dev.kern.shared.theme.KernType
import dev.kern.shared.theme.monogram

// ============================================================================
// Shared Kern UI primitives. Built on the design tokens in shared.theme and
// matching the components catalogued in the design handoff README.
// ============================================================================

/** 42x42 icon button. Active = accentSoft well + accent icon. */
@Composable
fun KernIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
    iconSize: Dp = 22.dp,
    badge: Boolean = false,
) {
    val colors = KernTheme.colors
    val resolvedTint = when {
        !enabled -> colors.textDim.copy(alpha = 0.45f)
        active -> colors.accent
        tint != null -> tint
        else -> colors.textMid
    }
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(RoundedCornerShape(KernRadius.iconButton))
            .background(if (active) colors.accentSoft else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = resolvedTint, modifier = Modifier.size(iconSize))
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
            )
        }
    }
}

/** One cell of a [KernSegmented] control. */
data class SegmentItem(val label: String, val icon: ImageVector? = null)

/** Sunken-track segmented control. Selected = raised surface pill + soft shadow. */
@Composable
fun KernSegmented(
    items: List<SegmentItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
) {
    val colors = KernTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(KernRadius.segmentTrack))
            .background(colors.sunken)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEachIndexed { index, item ->
            val on = index == selectedIndex
            Row(
                modifier = Modifier
                    .then(if (fillWidth) Modifier.weight(1f) else Modifier)
                    .then(if (on) Modifier.shadow(2.dp, RoundedCornerShape(KernRadius.segmentThumb)) else Modifier)
                    .clip(RoundedCornerShape(KernRadius.segmentThumb))
                    .background(if (on) colors.surface else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.icon != null) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = if (on) colors.text else colors.textMid,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    item.label,
                    style = KernType.chip.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (on) colors.text else colors.textMid,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Outlined file-type identity badge: 1.5dp format-hue border + mono monogram. */
@Composable
fun FileBadge(
    format: DocumentFormat,
    modifier: Modifier = Modifier,
    size: Dp = KernTheme.density.tile,
    viewOnly: Boolean = false,
) {
    val color = KernTheme.formatColor(format)
    Box(
        modifier = modifier
            .size(size)
            .alpha(if (viewOnly) 0.6f else 1f)
            .border(1.5.dp, color, RoundedCornerShape(KernRadius.badge)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = format.monogram,
            style = KernType.meta.copy(fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.27f).sp),
            color = color,
        )
    }
}

/** The accent ON-DEVICE pill shown in the browser header. */
@Composable
fun OnDevicePill(modifier: Modifier = Modifier) {
    val colors = KernTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(KernRadius.pill))
            .background(colors.accentSoft)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(KernIcons.Shield, contentDescription = null, tint = colors.accent, modifier = Modifier.size(11.dp))
        Text(
            "ON-DEVICE",
            style = KernType.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
            color = colors.accent,
        )
    }
}

/** Mono uppercase section label with an optional trailing slot (count, sort). */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = KernType.sectionLabel, color = KernTheme.colors.textDim)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/** Hairline divider, optionally inset from the start (e.g. past a badge). */
@Composable
fun KernDivider(modifier: Modifier = Modifier, startIndent: Dp = 0.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(1.dp)
            .background(KernTheme.colors.borderSoft),
    )
}

/**
 * 46x28 toggle. Off = border track, on = accent track, white knob with shadow.
 * A [locked] toggle is non-interactive and shows a lock glyph in the knob.
 */
@Composable
fun KernToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    locked: Boolean = false,
) {
    val colors = KernTheme.colors
    val knobOffset by animateDpAsState(if (checked) 18.dp else 0.dp, label = "toggleKnob")
    Box(
        modifier = modifier
            .width(46.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(KernRadius.pill))
            .background(if (checked) colors.accent else colors.border)
            .then(if (enabled && !locked) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(22.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (locked) {
                Icon(KernIcons.Lock, contentDescription = null, tint = colors.textDim, modifier = Modifier.size(11.dp))
            }
        }
    }
}

/**
 * Top app bar: optional back, title (optionally mono with a leading format
 * square), optional mono subtitle, trailing actions. 48dp min height.
 */
@Composable
fun KernTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color? = null,
    onBack: (() -> Unit)? = null,
    formatColor: Color? = null,
    monoTitle: Boolean = false,
    showDivider: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = KernTheme.colors
    Column(modifier.fillMaxWidth().background(colors.bg).statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(start = if (onBack != null) 4.dp else 8.dp, end = 4.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                KernIconButton(KernIcons.Back, "Back", onBack, tint = colors.text)
                Spacer(Modifier.width(2.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onBack != null) 2.dp else 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (formatColor != null) {
                        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(formatColor))
                    }
                    Text(
                        text = title,
                        style = if (monoTitle) {
                            KernType.meta.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        } else {
                            KernType.sectionTitle.copy(fontSize = 18.sp)
                        },
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (subtitle != null) {
                    Text(subtitle, style = KernType.meta, color = subtitleColor ?: colors.textMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        if (showDivider) KernDivider()
    }
}

/**
 * Bottom sheet with the Kern scrim, surface, 16dp top corners, and grab handle.
 * Compose the sheet only while it should be shown (e.g. `if (open) KernBottomSheet { }`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KernBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = KernTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        scrimColor = colors.scrim,
        shape = RoundedCornerShape(topStart = KernRadius.sheetTop, topEnd = KernRadius.sheetTop),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 8.dp, bottom = 8.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(KernRadius.pill))
                    .background(colors.border),
            )
        },
    ) {
        if (title != null) {
            Text(
                title,
                style = KernType.sectionTitle,
                color = colors.text,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 10.dp),
            )
        }
        content()
    }
}

/**
 * A sheet row: 34dp accent icon well + label (+ optional mono sublabel), with an
 * optional trailing slot. Destructive rows render in the danger colour.
 */
@Composable
fun SheetActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = KernTheme.colors
    val tint = if (danger) colors.danger else colors.accent
    val labelColor = if (danger) colors.danger else colors.text
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(KernRadius.innerSmall))
                .background(if (danger) colors.danger.copy(alpha = 0.12f) else colors.sunken),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = KernType.body, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sublabel != null) {
                Text(sublabel, style = KernType.caption, color = colors.textMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) trailing()
    }
}

/**
 * Bottom editor toolbar: a horizontally scrollable row of [ToolbarButton]s and
 * [ToolbarSeparator]s on the app background with a hairline top border.
 */
@Composable
fun EditorToolbar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = KernTheme.colors
    Column(modifier.fillMaxWidth().background(colors.bg).navigationBarsPadding()) {
        KernDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

/** A 38dp-tall toolbar button: icon + optional label. Active = accentSoft/accent. */
@Composable
fun ToolbarButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = KernTheme.colors
    val content = when {
        !enabled -> colors.textDim.copy(alpha = 0.45f)
        active -> colors.accent
        else -> colors.textMid
    }
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(KernRadius.innerSmall))
            .background(if (active) colors.accentSoft else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (label != null) 12.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = content, modifier = Modifier.size(20.dp))
        if (label != null) {
            Text(label, style = KernType.chip, color = content, maxLines = 1)
        }
    }
}

/** A thin vertical separator between toolbar groups. */
@Composable
fun ToolbarSeparator(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(horizontal = 6.dp)
            .height(20.dp)
            .width(1.dp)
            .background(KernTheme.colors.borderSoft),
    )
}
