package dev.kern.browser

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kern.shared.DocumentFormat
import dev.kern.shared.io.DocumentIo
import dev.kern.shared.theme.KernRadius
import dev.kern.shared.theme.KernTheme
import dev.kern.shared.theme.KernType
import dev.kern.shared.theme.QuicksandFamily
import dev.kern.shared.theme.monogram
import dev.kern.shared.ui.BrandMark
import dev.kern.shared.ui.FileBadge
import dev.kern.shared.ui.KernBottomSheet
import dev.kern.shared.ui.KernDivider
import dev.kern.shared.ui.KernIconButton
import dev.kern.shared.ui.KernIcons
import dev.kern.shared.ui.KernSegmented
import dev.kern.shared.ui.OnDevicePill
import dev.kern.shared.ui.SectionLabel
import dev.kern.shared.ui.SegmentItem
import dev.kern.shared.ui.SheetActionRow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/**
 * Home screen: a browser over the documents in Documents + Downloads (ADR 003).
 * Recent + pinned + all-files views, search, per-format filter, sort, list/grid,
 * and per-file actions. Falls back to the system picker for files elsewhere.
 *
 * @param onOpenDocument invoked with the resolved format and an encoded URI string.
 * @param onOpenSettings opens the Settings screen (from the header cog).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    onOpenDocument: (format: DocumentFormat, filePath: String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    vm: FileBrowserViewModel = viewModel(),
) {
    val context = LocalContext.current
    val settings = dev.kern.shared.settings.LocalKernSettings.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var menuDoc by remember { mutableStateOf<ScannedDoc?>(null) }
    var infoDoc by remember { mutableStateOf<ScannedDoc?>(null) }
    var deleteDoc by remember { mutableStateOf<ScannedDoc?>(null) }
    var sortOpen by remember { mutableStateOf(false) }

    // Scan on first load and whenever the scan toggles change. Returning from an
    // editor re-runs this with the same prefs, which the ViewModel treats as a
    // no-op (no rescan), so the list is preserved instead of reloading.
    androidx.compose.runtime.LaunchedEffect(settings.scanDocuments, settings.scanDownloads) {
        vm.refresh(settings.scanDocuments, settings.scanDownloads)
    }

    // Catch a permission grant on resume (covers returning from the system
    // "all files access" page); a no-op once access is already held.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !vm.hasAccess) {
                vm.refresh(settings.scanDocuments, settings.scanDownloads)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val manageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { vm.refresh(settings.scanDocuments, settings.scanDownloads, force = true) }
    val readLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.refresh(settings.scanDocuments, settings.scanDownloads, force = true) }

    fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            runCatching { manageLauncher.launch(intent) }.onFailure {
                manageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            readLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri)
        val ext = DocumentIo.displayName(context, uri).substringAfterLast('.', "")
        val format = DocumentFormat.fromMimeType(mime) ?: DocumentFormat.fromExtension(ext)
        if (format == null) scope.launch { snackbar.showSnackbar("Unsupported file type") }
        else {
            DocumentIo.tryPersist(context, uri)
            onOpenDocument(format, Uri.encode(uri.toString()))
        }
    }

    fun openScanned(doc: ScannedDoc) {
        vm.recordOpen(doc)
        onOpenDocument(doc.format, Uri.encode(Uri.fromFile(File(doc.path)).toString()))
    }

    Scaffold(
        modifier = modifier,
        containerColor = KernTheme.colors.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pickLauncher.launch(arrayOf("*/*")) },
                icon = { Icon(KernIcons.Plus, contentDescription = null) },
                text = { Text("Open file") },
                containerColor = KernTheme.colors.accent,
                contentColor = KernTheme.colors.accentOn,
                shape = RoundedCornerShape(KernRadius.fab),
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            BrowserHeader(
                layoutIsList = vm.layout == BrowserLayout.LIST,
                onToggleLayout = vm::toggleLayout,
                onOpenSettings = onOpenSettings,
            )
            if (!vm.hasAccess) {
                PermissionGate(onGrant = ::requestAccess, onPickFile = { pickLauncher.launch(arrayOf("*/*")) })
            } else {
                BrowserTabs(vm.view, vm::selectView)
                SearchField(vm.query, vm::updateQuery)
                if (vm.view == BrowserView.ALL) FilterChipsRow(vm.filter, vm::selectFilter)

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        vm.loading -> CircularProgressIndicator(
                            Modifier.align(Alignment.Center),
                            color = KernTheme.colors.accent,
                        )
                        vm.view == BrowserView.RECENT -> RecentView(vm, ::openScanned) { menuDoc = it }
                        else -> AllView(vm, onSortClick = { sortOpen = true }, onOpen = ::openScanned) { menuDoc = it }
                    }
                }
            }
        }
    }

    if (sortOpen) SortSheet(vm, onClose = { sortOpen = false })

    menuDoc?.let { doc ->
        ActionsSheet(
            doc = doc,
            pinned = vm.isPinned(doc),
            onOpen = { openScanned(doc); menuDoc = null },
            onTogglePin = { vm.togglePin(doc); menuDoc = null },
            onShare = { shareDoc(context, doc); menuDoc = null },
            onInfo = { infoDoc = doc; menuDoc = null },
            onDelete = { deleteDoc = doc; menuDoc = null },
            onClose = { menuDoc = null },
        )
    }

    infoDoc?.let { doc -> FileInfoDialog(doc) { infoDoc = null } }

    deleteDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleteDoc = null },
            containerColor = KernTheme.colors.surface,
            title = { Text("Delete file?") },
            text = { Text("\"${doc.name}\" will be permanently deleted from this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val ok = vm.delete(doc)
                    deleteDoc = null
                    scope.launch { snackbar.showSnackbar(if (ok) "Deleted" else "Could not delete the file") }
                }) { Text("Delete", color = KernTheme.colors.danger) }
            },
            dismissButton = { TextButton(onClick = { deleteDoc = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BrowserHeader(
    layoutIsList: Boolean,
    onToggleLayout: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = KernTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .heightIn(min = 46.dp)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(Modifier.size(26.dp))
        Text(
            "kern",
            style = TextStyle(fontFamily = QuicksandFamily, fontWeight = FontWeight.Bold, fontSize = 21.sp),
            color = colors.text,
            modifier = Modifier.padding(start = 8.dp),
        )
        OnDevicePill(Modifier.padding(start = 10.dp))
        Spacer(Modifier.weight(1f))
        KernIconButton(KernIcons.Settings, "Settings", onOpenSettings)
        KernIconButton(
            if (layoutIsList) KernIcons.Grid else KernIcons.List,
            "Toggle layout",
            onToggleLayout,
        )
    }
}

@Composable
private fun BrowserTabs(view: BrowserView, onSelect: (BrowserView) -> Unit) {
    KernSegmented(
        items = listOf(SegmentItem("Recent"), SegmentItem("All files")),
        selectedIndex = if (view == BrowserView.RECENT) 0 else 1,
        onSelect = { onSelect(if (it == 0) BrowserView.RECENT else BrowserView.ALL) },
        fillWidth = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = KernTheme.density.screenPadding, vertical = 8.dp),
    )
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val colors = KernTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KernTheme.density.screenPadding)
            .height(46.dp)
            .border(1.dp, colors.border, RoundedCornerShape(KernRadius.field))
            .background(colors.surface, RoundedCornerShape(KernRadius.field))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KernIcons.Search, contentDescription = null, tint = colors.textMid, modifier = Modifier.size(19.dp))
        Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            if (query.isEmpty()) {
                Text("Search Documents & Downloads", color = colors.textMid, style = KernType.body.copy(fontWeight = FontWeight.Normal))
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = KernType.body.copy(color = colors.text, fontWeight = FontWeight.Normal),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            KernIconButton(KernIcons.Close, "Clear", { onChange("") }, iconSize = 18.dp)
        }
    }
}

@Composable
private fun FilterChipsRow(filter: DocumentFormat?, onSelect: (DocumentFormat?) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = KernTheme.density.screenPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            BrowserFilterChip(label = "All", color = null, active = filter == null) { onSelect(null) }
        }
        items(DocumentFormat.entries) { fmt ->
            BrowserFilterChip(label = fmt.label, color = KernTheme.formatColor(fmt), active = filter == fmt) { onSelect(fmt) }
        }
    }
}

@Composable
private fun BrowserFilterChip(label: String, color: Color?, active: Boolean, onClick: () -> Unit) {
    val colors = KernTheme.colors
    val accent = color ?: colors.accent
    val bg = if (active) accent.copy(alpha = if (colors.dark) 0.22f else 0.14f) else Color.Transparent
    val contentColor = if (active) accent else colors.textMid
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(KernRadius.pill))
            .background(bg)
            .then(if (active) Modifier else Modifier.border(1.dp, colors.border, RoundedCornerShape(KernRadius.pill)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (active && color != null) {
            Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color))
        }
        Text(
            label,
            style = KernType.chip.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium),
            color = contentColor,
        )
    }
}

@Composable
private fun RecentView(vm: FileBrowserViewModel, onOpen: (ScannedDoc) -> Unit, onMore: (ScannedDoc) -> Unit) {
    val pinned = vm.pinnedDocs
    val recent = vm.recentDocs
    val pad = KernTheme.density.screenPadding
    Column(Modifier.fillMaxSize()) {
        if (pinned.isNotEmpty() && vm.query.isBlank()) {
            SectionLabel("Pinned", Modifier.padding(start = pad, end = pad, top = 16.dp, bottom = 8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = pad),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(pinned, key = { it.path }) { doc ->
                    Box(Modifier.width(132.dp)) { FileCard(doc, pinned = true, onOpen, onMore) }
                }
            }
        }
        SectionLabel(
            "Recent",
            Modifier.padding(start = pad, end = pad, top = 16.dp, bottom = 8.dp),
            trailing = { Text("${recent.size}", style = KernType.meta, color = KernTheme.colors.textDim) },
        )
        if (recent.isEmpty()) {
            EmptyState(
                searching = vm.query.isNotBlank(),
                query = vm.query,
                modifier = Modifier.weight(1f),
            )
        } else {
            FilesList(recent, vm.layout, vm::isPinned, onOpen, onMore, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AllView(
    vm: FileBrowserViewModel,
    onSortClick: () -> Unit,
    onOpen: (ScannedDoc) -> Unit,
    onMore: (ScannedDoc) -> Unit,
) {
    val docs = vm.allDocs
    val colors = KernTheme.colors
    val pad = KernTheme.density.screenPadding
    Column(Modifier.fillMaxSize()) {
        SectionLabel(
            "${vm.filter?.label ?: "All files"}  ·  ${docs.size}",
            Modifier.padding(start = pad, end = pad, top = 14.dp, bottom = 8.dp),
            trailing = {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(KernRadius.innerSmall)).clickable { onSortClick() }.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        if (vm.sortAscending) KernIcons.ArrowUp else KernIcons.ArrowDown,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(vm.sort.label, style = KernType.meta, color = colors.textMid)
                }
            },
        )
        if (docs.isEmpty()) {
            EmptyState(searching = vm.query.isNotBlank() || vm.filter != null, query = vm.query, modifier = Modifier.weight(1f))
        } else {
            FilesList(docs, vm.layout, vm::isPinned, onOpen, onMore, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FilesList(
    docs: List<ScannedDoc>,
    layout: BrowserLayout,
    isPinned: (ScannedDoc) -> Boolean,
    onOpen: (ScannedDoc) -> Unit,
    onMore: (ScannedDoc) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pad = KernTheme.density.screenPadding
    if (layout == BrowserLayout.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = pad, end = pad, top = 4.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(docs, key = { it.path }) { doc -> FileCard(doc, isPinned(doc), onOpen, onMore) }
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 96.dp)) {
            items(docs, key = { it.path }) { doc ->
                FileRow(doc, isPinned(doc), onOpen, onMore)
                KernDivider(startIndent = 76.dp)
            }
        }
    }
}

@Composable
private fun FileRow(doc: ScannedDoc, pinned: Boolean, onOpen: (ScannedDoc) -> Unit, onMore: (ScannedDoc) -> Unit) {
    val colors = KernTheme.colors
    val hue = KernTheme.formatColor(doc.format)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(doc) }
            .heightIn(min = KernTheme.density.rowMinHeight)
            .padding(horizontal = KernTheme.density.screenPadding, vertical = KernTheme.density.rowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileBadge(doc.format, viewOnly = doc.viewOnly)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(doc.name, style = KernType.fileName, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(doc.format.monogram, style = KernType.meta.copy(fontWeight = FontWeight.SemiBold), color = hue)
                Text(
                    "  ·  ${prettySize(doc.size)}  ·  ${prettyDate(doc.modified)}${if (doc.viewOnly) "  ·  view" else ""}",
                    style = KernType.meta,
                    color = colors.textMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (pinned) {
            Icon(KernIcons.StarFilled, contentDescription = "Pinned", tint = colors.accent, modifier = Modifier.size(15.dp).padding(end = 2.dp))
        }
        KernIconButton(KernIcons.More, "More", { onMore(doc) })
    }
}

@Composable
private fun FileCard(doc: ScannedDoc, pinned: Boolean, onOpen: (ScannedDoc) -> Unit, onMore: (ScannedDoc) -> Unit) {
    val colors = KernTheme.colors
    val hue = KernTheme.formatColor(doc.format)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KernRadius.base))
            .border(1.dp, colors.borderSoft, RoundedCornerShape(KernRadius.base))
            .background(colors.surface)
            .clickable { onOpen(doc) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .background(hue.copy(alpha = if (colors.dark) 0.16f else 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                doc.format.monogram,
                style = KernType.meta.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
                color = hue,
            )
            if (pinned) {
                Icon(
                    KernIcons.StarFilled,
                    contentDescription = "Pinned",
                    tint = colors.accent,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(15.dp),
                )
            }
            if (doc.viewOnly) {
                Icon(
                    KernIcons.Lock,
                    contentDescription = "View only",
                    tint = colors.textMid,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(14.dp),
                )
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(doc.name, style = KernType.body.copy(fontSize = 13.sp), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${prettySize(doc.size)}  ·  ${prettyDate(doc.modified)}",
                style = KernType.caption,
                color = colors.textMid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(searching: Boolean, query: String, modifier: Modifier = Modifier) {
    val colors = KernTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(44.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(KernRadius.field)).background(colors.sunken),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (searching) KernIcons.Search else KernIcons.Folder,
                contentDescription = null,
                tint = colors.textDim,
                modifier = Modifier.size(30.dp),
            )
        }
        Text(
            if (searching) "No matches" else "No documents yet",
            style = KernType.sectionTitle,
            color = colors.text,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            if (searching) "Nothing in Documents or Downloads matches \"$query\"."
            else "Files you open from your file manager will appear here. Kern reads them in place: nothing is copied or uploaded.",
            style = KernType.body.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Normal),
            color = colors.textMid,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp).width(240.dp),
        )
    }
}

@Composable
private fun PermissionGate(onGrant: () -> Unit, onPickFile: () -> Unit) {
    val colors = KernTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(KernRadius.field)).background(colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(KernIcons.Shield, contentDescription = null, tint = colors.accent, modifier = Modifier.size(30.dp))
        }
        Text("Storage access needed", style = KernType.sectionTitle, color = colors.text, modifier = Modifier.padding(top = 14.dp))
        Text(
            "Kern lists documents from your Documents and Downloads folders and edits them in place. It never copies your files and has no network access.",
            style = KernType.body.copy(fontWeight = FontWeight.Normal),
            color = colors.textMid,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = onGrant, modifier = Modifier.padding(top = 16.dp)) { Text("Grant access", color = colors.accent) }
        TextButton(onClick = onPickFile) { Text("Or open a single file", color = colors.textMid) }
    }
}

@Composable
private fun SortSheet(vm: FileBrowserViewModel, onClose: () -> Unit) {
    KernBottomSheet(onDismiss = onClose, title = "Sort by") {
        for (key in SortKey.entries) {
            val selected = vm.sort == key
            SheetActionRow(
                icon = if (selected && !vm.sortAscending) KernIcons.ArrowDown else KernIcons.ArrowUp,
                label = key.label,
                sublabel = if (selected) sortDirectionLabel(key, vm.sortAscending) else null,
                onClick = { vm.pickSort(key) },
                trailing = {
                    if (selected) {
                        Icon(KernIcons.Check, contentDescription = null, tint = KernTheme.colors.accent, modifier = Modifier.size(18.dp))
                    }
                },
            )
        }
        Text(
            "Tap the active field again to reverse direction.",
            style = KernType.caption,
            color = KernTheme.colors.textDim,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
}

private fun sortDirectionLabel(key: SortKey, ascending: Boolean): String = when (key) {
    SortKey.RECENT -> if (ascending) "Oldest first" else "Newest first"
    SortKey.NAME -> if (ascending) "A to Z" else "Z to A"
    SortKey.SIZE -> if (ascending) "Smallest first" else "Largest first"
    SortKey.TYPE -> if (ascending) "A to Z" else "Z to A"
}

@Composable
private fun ActionsSheet(
    doc: ScannedDoc,
    pinned: Boolean,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    KernBottomSheet(onDismiss = onClose, title = doc.name) {
        SheetActionRow(KernIcons.Doc, "Open", onOpen)
        SheetActionRow(
            if (pinned) KernIcons.StarFilled else KernIcons.Star,
            if (pinned) "Unpin" else "Pin to top",
            onTogglePin,
        )
        SheetActionRow(KernIcons.Share, "Share a copy", onShare)
        SheetActionRow(KernIcons.Info, "File info", onInfo)
        SheetActionRow(KernIcons.Trash, "Delete", onDelete, danger = true)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun FileInfoDialog(doc: ScannedDoc, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = KernTheme.colors.surface,
        confirmButton = { TextButton(onClick = onClose) { Text("Close", color = KernTheme.colors.accent) } },
        title = { Text(doc.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                InfoLine("Type", doc.format.label)
                InfoLine("Size", prettySize(doc.size))
                InfoLine("Modified", prettyDate(doc.modified))
                InfoLine("Location", File(doc.path).parent ?: doc.path)
            }
        },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = KernType.meta, color = KernTheme.colors.textMid, modifier = Modifier.width(80.dp))
        Text(value, style = KernType.body.copy(fontWeight = FontWeight.Normal), color = KernTheme.colors.text)
    }
}

/** EPUB is read-only in this alpha; everything else is editable. */
private val ScannedDoc.viewOnly: Boolean get() = format == DocumentFormat.EPUB

private fun shareDoc(context: android.content.Context, doc: ScannedDoc) {
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(doc.path))
    val send = Intent(Intent.ACTION_SEND).apply {
        type = context.contentResolver.getType(uri) ?: doc.format.mimeTypes.firstOrNull() ?: "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share a copy"))
}

private fun prettySize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.roundToInt()} KB"
    val mb = kb / 1024.0
    return if (mb < 1024) String.format("%.1f MB", mb) else String.format("%.1f GB", mb / 1024.0)
}

private fun prettyDate(ms: Long): String =
    DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
