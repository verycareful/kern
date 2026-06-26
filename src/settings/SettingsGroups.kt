package dev.kern.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.kern.shared.DocumentFormat
import dev.kern.shared.theme.AccentColor
import dev.kern.shared.theme.Density
import dev.kern.shared.theme.KernTheme
import dev.kern.shared.theme.KernType
import dev.kern.shared.theme.ThemeMode
import dev.kern.shared.ui.KernDivider
import dev.kern.shared.ui.KernIcons
import dev.kern.shared.ui.KernSegmented
import dev.kern.shared.ui.KernToggle
import dev.kern.shared.ui.SegmentItem

// The grouped Settings sections (Appearance, Accent, Files, Privacy, About). They
// use the row/group primitives and shared constants from SettingsScreen.kt, the
// storage-access state from SettingsStorage.kt, and links from SettingsLinks.kt.

@Composable
internal fun AppearanceGroup(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    density: Density,
    onDensity: (Density) -> Unit,
) {
    SettingsGroup("Appearance") {
        val themeIcon = if (KernTheme.colors.dark) KernIcons.Moon else KernIcons.Sun
        SettingsRow(
            icon = themeIcon,
            label = "Theme",
            sublabel = "Applies to this device",
            trailing = {
                KernSegmented(
                    items = ThemeMode.entries.map { SegmentItem(it.segmentLabel()) },
                    selectedIndex = ThemeMode.entries.indexOf(themeMode),
                    onSelect = { onThemeMode(ThemeMode.entries[it]) },
                )
            },
        )
        KernDivider()
        SettingsRow(
            icon = KernIcons.List,
            label = "Density",
            sublabel = "Row height & spacing",
            trailing = {
                KernSegmented(
                    items = Density.entries.map { SegmentItem(it.segmentLabel()) },
                    selectedIndex = Density.entries.indexOf(density),
                    onSelect = { onDensity(Density.entries[it]) },
                )
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccentGroup(accent: AccentColor, onAccent: (AccentColor) -> Unit) {
    val colors = KernTheme.colors
    SettingsGroup("Accent") {
        Column(Modifier.fillMaxWidth().padding(horizontal = CardPadding, vertical = CardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = RowGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Accent color", style = KernType.body, color = colors.text, modifier = Modifier.weight(1f))
                TrailingMeta(accent.displayName)
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SwatchGap),
                verticalArrangement = Arrangement.spacedBy(SwatchGap),
            ) {
                AccentColor.entries.forEach { entry ->
                    AccentSwatchButton(entry = entry, selected = entry == accent, onClick = { onAccent(entry) })
                }
            }
        }
    }
}

@Composable
private fun AccentSwatchButton(entry: AccentColor, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(AccentSwatch)
            .then(if (selected) Modifier.border(AccentRingWidth, entry.color, CircleShape).padding(AccentRingWidth) else Modifier)
            .clip(CircleShape)
            .background(entry.color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(KernIcons.Check, contentDescription = entry.displayName, tint = Color.White, modifier = Modifier.size(AccentCheckSize))
        }
    }
}

@Composable
internal fun FilesGroup(
    scanDocuments: Boolean,
    onScanDocuments: (Boolean) -> Unit,
    scanDownloads: Boolean,
    onScanDownloads: (Boolean) -> Unit,
) {
    val colors = KernTheme.colors
    SettingsGroup("Files & storage") {
        SettingsRow(
            icon = KernIcons.Folder,
            label = "Scan Documents",
            sublabel = SCAN_DOCUMENTS_PATH,
            trailing = { KernToggle(checked = scanDocuments, onCheckedChange = onScanDocuments) },
        )
        KernDivider()
        SettingsRow(
            icon = KernIcons.Download,
            label = "Scan Downloads",
            sublabel = SCAN_DOWNLOADS_PATH,
            trailing = { KernToggle(checked = scanDownloads, onCheckedChange = onScanDownloads) },
        )
        KernDivider()
        SettingsRow(
            icon = KernIcons.Doc,
            label = "Open files in place",
            sublabel = "No app folder, files never copied",
            trailing = { Icon(KernIcons.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(TrailingIconSize)) },
        )
    }
}

@Composable
internal fun PrivacyGroup() {
    val colors = KernTheme.colors
    val grantedColor = KernTheme.formatColor(DocumentFormat.EXCEL)
    val (storageGranted, requestStorage) = rememberStorageAccess()
    SettingsGroup("Privacy & permissions") {
        // Locked off: Kern declares zero network permissions, so this is a guarantee, not a toggle.
        SettingsRow(
            icon = KernIcons.WifiOff,
            label = "Network access",
            sublabel = "Not requested, guaranteed offline",
            trailing = { KernToggle(checked = false, onCheckedChange = {}, locked = true) },
        )
        KernDivider()
        SettingsRow(
            icon = KernIcons.Lock,
            label = "Storage access",
            sublabel = if (storageGranted) "Documents & Downloads (scoped)" else "Tap to grant all-files access",
            onClick = if (storageGranted) null else requestStorage,
            trailing = {
                TrailingMeta(
                    if (storageGranted) PERMISSION_GRANTED else "NOT GRANTED",
                    color = if (storageGranted) grantedColor else colors.danger,
                    bold = true,
                )
            },
        )
        KernDivider()
        SettingsRow(
            icon = KernIcons.Info,
            label = "Analytics & telemetry",
            sublabel = "None collected",
            trailing = { KernToggle(checked = false, onCheckedChange = {}, locked = true) },
        )
    }
}

@Composable
internal fun AboutGroup(onShowLicense: () -> Unit) {
    val context = LocalContext.current
    SettingsGroup("About") {
        SettingsRow(
            icon = KernIcons.Info,
            label = "Version",
            sublabel = "Alpha",
            trailing = { TrailingMeta(APP_VERSION, color = KernTheme.colors.textDim) },
        )
        KernDivider()
        SettingsRow(
            icon = KernIcons.Doc,
            label = "License",
            sublabel = "Tap to read the full text",
            onClick = onShowLicense,
            trailing = { TrailingMeta(LICENSE_LABEL) },
        )
        KernDivider()
        SettingsRow(
            icon = KernIcons.Github,
            label = "Source code",
            sublabel = SOURCE_URL,
            onClick = { openUrl(context, SOURCE_URL) },
            trailing = { TrailingIcon(KernIcons.Link) },
        )
        KernDivider()
        SettingsRow(
            icon = KernIcons.Page,
            label = "PDF engine",
            sublabel = "MuPDF via Qyra, GPL-3.0",
            onClick = { openUrl(context, QYRA_URL) },
            trailing = { TrailingIcon(KernIcons.Chevron) },
        )
    }
}

private fun ThemeMode.segmentLabel(): String = when (this) {
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.AUTO -> "Auto"
}

private fun Density.segmentLabel(): String = when (this) {
    Density.COMPACT -> "Compact"
    Density.COZY -> "Cozy"
}
