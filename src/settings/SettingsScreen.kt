package dev.kern.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kern.shared.settings.LocalKernSettings
import dev.kern.shared.theme.KernRadius
import dev.kern.shared.theme.KernTheme
import dev.kern.shared.theme.KernType
import dev.kern.shared.ui.BrandLockup
import dev.kern.shared.ui.KernDivider
import dev.kern.shared.ui.KernIconButton
import dev.kern.shared.ui.KernIcons
import dev.kern.shared.ui.SectionLabel

// Settings screen (design handoff section 7). This file holds the screen scaffold,
// header, privacy hero, and the shared row/group primitives. The grouped sections
// live in SettingsGroups.kt; the license dialog + links in SettingsLinks.kt; the
// storage-access state in SettingsStorage.kt.

// Shared with SettingsGroups.kt (internal).
internal val CardPadding = 16.dp
internal val RowGap = 14.dp
internal val SwatchGap = 12.dp
internal val AccentSwatch = 30.dp
internal val AccentCheckSize = 16.dp
internal val AccentRingWidth = 2.dp
internal val TrailingIconSize = 18.dp
internal const val APP_VERSION = "0.1.10.1"
internal const val SCAN_DOCUMENTS_PATH = "/Documents"
internal const val SCAN_DOWNLOADS_PATH = "/Downloads"
internal const val PERMISSION_GRANTED = "GRANTED"

private val BrandLockupHeight = 34.dp
private val HeroIconWell = 42.dp
private val HeroIconSize = 22.dp
private val RowIconWellSize = 34.dp
private val RowIconSize = 18.dp
private val GroupSpacing = 18.dp
private val RowVerticalPadding = 13.dp
private val BottomSpacer = 40.dp
private val HeroTitleSize = 15.5.sp

/** The Settings screen. Reads and persists preferences via [LocalKernSettings]. */
@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = KernTheme.colors
    val settings = LocalKernSettings.current
    var showLicense by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().background(colors.bg).statusBarsPadding().navigationBarsPadding()) {
        SettingsHeader(onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            BrandLockupRow()
            PrivacyHero()
            Spacer(Modifier.height(GroupSpacing))
            AppearanceGroup(
                themeMode = settings.themeMode,
                onThemeMode = settings::updateThemeMode,
                density = settings.density,
                onDensity = settings::updateDensity,
            )
            AccentGroup(accent = settings.accent, onAccent = settings::updateAccent)
            FilesGroup(
                scanDocuments = settings.scanDocuments,
                onScanDocuments = settings::updateScanDocuments,
                scanDownloads = settings.scanDownloads,
                onScanDownloads = settings::updateScanDownloads,
            )
            PrivacyGroup()
            AboutGroup(onShowLicense = { showLicense = true })
            Spacer(Modifier.height(BottomSpacer))
        }
    }

    if (showLicense) LicenseDialog(onDismiss = { showLicense = false })
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    val colors = KernTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KernIconButton(KernIcons.Back, "Back", onBack, tint = colors.text)
            Text(
                "Settings",
                style = KernType.screenTitle.copy(fontSize = 22.sp),
                color = colors.text,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
        KernDivider()
    }
}

@Composable
private fun BrandLockupRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 22.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        BrandLockup(Modifier.height(BrandLockupHeight))
    }
}

@Composable
private fun PrivacyHero() {
    val colors = KernTheme.colors
    val titleColor = if (colors.dark) colors.text else colors.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardPadding)
            .clip(RoundedCornerShape(KernRadius.base))
            .background(colors.accentSoft)
            .then(if (colors.dark) Modifier else Modifier.border(1.dp, colors.borderSoft, RoundedCornerShape(KernRadius.base)))
            .padding(CardPadding),
        horizontalArrangement = Arrangement.spacedBy(RowGap),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(HeroIconWell).clip(RoundedCornerShape(KernRadius.field)).background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(KernIcons.Shield, contentDescription = null, tint = colors.accentOn, modifier = Modifier.size(HeroIconSize))
        }
        Column {
            Text(
                "Nothing leaves this device",
                style = KernType.body.copy(fontSize = HeroTitleSize, fontWeight = FontWeight.Bold),
                color = titleColor,
            )
            Text(
                "Kern has zero network permissions. No accounts, no sync, no analytics, ever.",
                style = KernType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal),
                color = colors.textMid,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/** A labelled group: mono uppercase [SectionLabel] over a bordered surface card. */
@Composable
internal fun SettingsGroup(label: String, content: @Composable () -> Unit) {
    val colors = KernTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = CardPadding).padding(bottom = GroupSpacing)) {
        SectionLabel(label, Modifier.padding(start = 6.dp, bottom = 8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(KernRadius.base))
                .border(1.dp, colors.borderSoft, RoundedCornerShape(KernRadius.base))
                .background(colors.surface),
            content = { content() },
        )
    }
}

/** A standard settings row: optional icon well, label + optional mono sublabel, trailing slot. */
@Composable
internal fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    sublabel: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = KernTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = CardPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        if (icon != null) RowIconWell(icon)
        Column(Modifier.weight(1f)) {
            Text(label, style = KernType.body, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sublabel != null) {
                Text(
                    sublabel,
                    style = KernType.meta,
                    color = colors.textMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
private fun RowIconWell(icon: ImageVector) {
    val colors = KernTheme.colors
    Box(
        modifier = Modifier.size(RowIconWellSize).clip(RoundedCornerShape(KernRadius.innerSmall)).background(colors.sunken),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(RowIconSize))
    }
}

/** Trailing mono value (version string, GRANTED, AGPL-3.0). */
@Composable
internal fun TrailingMeta(text: String, color: Color = KernTheme.colors.textMid, bold: Boolean = false) {
    Text(text, style = KernType.meta.copy(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal), color = color)
}

/** Trailing chevron / link glyph for navigable rows. */
@Composable
internal fun TrailingIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = KernTheme.colors.textDim, modifier = Modifier.size(TrailingIconSize))
}
