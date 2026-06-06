package dev.kern.browser

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kern.shared.DocumentFormat
import dev.kern.shared.io.DocumentIo
import dev.kern.shared.style
import dev.kern.shared.theme.KernAccent
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/**
 * Home screen: a browser over the documents in Documents + Downloads (ADR 003).
 * Recent + pinned + all-files views, search, per-format filter, sort, list/grid,
 * and per-file actions. Falls back to the system picker for files elsewhere.
 *
 * @param onOpenDocument invoked with the resolved format and an encoded URI string.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    onOpenDocument: (format: DocumentFormat, filePath: String) -> Unit,
    modifier: Modifier = Modifier,
    vm: FileBrowserViewModel = viewModel(),
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var menuDoc by remember { mutableStateOf<ScannedDoc?>(null) }
    var infoDoc by remember { mutableStateOf<ScannedDoc?>(null) }
    var deleteDoc by remember { mutableStateOf<ScannedDoc?>(null) }
    var sortOpen by remember { mutableStateOf(false) }

    // Re-check permission + rescan whenever the screen resumes (covers returning
    // from the system "all files access" settings page).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val manageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { vm.refresh() }
    val readLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.refresh() }

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
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pickLauncher.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                text = { Text("Open file") },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            BrowserHeader(layoutIsList = vm.layout == BrowserLayout.LIST, onToggleLayout = vm::toggleLayout)
            if (!vm.hasAccess) {
                PermissionGate(onGrant = ::requestAccess, onPickFile = { pickLauncher.launch(arrayOf("*/*")) })
            } else {
                SegmentedTabs(vm.view, vm::selectView)
                SearchBar(vm.query, vm::updateQuery)
                if (vm.view == BrowserView.ALL) FilterChips(vm.filter, vm::selectFilter)

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        vm.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
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
            title = { Text("Delete file?") },
            text = { Text("\"${doc.name}\" will be permanently deleted from this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val ok = vm.delete(doc)
                    deleteDoc = null
                    scope.launch { snackbar.showSnackbar(if (ok) "Deleted" else "Could not delete the file") }
                }) { Text("Delete", color = Color(0xFFD6453D)) }
            },
            dismissButton = { TextButton(onClick = { deleteDoc = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BrowserHeader(layoutIsList: Boolean, onToggleLayout: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("kern", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Row(
            modifier = Modifier
                .padding(start = 10.dp)
                .background(KernAccent.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
                .padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = KernAccent, modifier = Modifier.size(11.dp))
            Text(
                "ON-DEVICE",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = KernAccent,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Box(Modifier.weight(1f))
        IconButton(onClick = onToggleLayout) {
            Icon(
                if (layoutIsList) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                contentDescription = "Toggle layout",
            )
        }
    }
}

@Composable
private fun SegmentedTabs(view: BrowserView, onSelect: (BrowserView) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (v in BrowserView.entries) {
            val selected = v == view
            Surface(
                modifier = Modifier.weight(1f).clickable { onSelect(v) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                shadowElevation = if (selected) 1.dp else 0.dp,
            ) {
                Text(
                    text = if (v == BrowserView.RECENT) "Recent" else "All files",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 14.dp)) {
            if (query.isEmpty()) {
                Text("Search Documents & Downloads", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, contentDescription = "Clear") }
        }
    }
}

@Composable
private fun FilterChips(filter: DocumentFormat?, onSelect: (DocumentFormat?) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(selected = filter == null, onClick = { onSelect(null) }, label = { Text("All") })
        }
        items(DocumentFormat.entries) { fmt ->
            val style = fmt.style()
            FilterChip(
                selected = filter == fmt,
                onClick = { onSelect(fmt) },
                label = { Text(fmt.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = style.hue.copy(alpha = 0.16f),
                    selectedLabelColor = style.hue,
                ),
            )
        }
    }
}

@Composable
private fun RecentView(vm: FileBrowserViewModel, onOpen: (ScannedDoc) -> Unit, onMore: (ScannedDoc) -> Unit) {
    val pinned = vm.pinnedDocs
    val recent = vm.recentDocs
    Column(Modifier.fillMaxSize()) {
        if (pinned.isNotEmpty() && vm.query.isBlank()) {
            SectionLabel("Pinned")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(pinned) { doc -> Box(Modifier.width(140.dp)) { FileCard(doc, onOpen, onMore) } }
            }
        }
        SectionLabel("Recent")
        if (recent.isEmpty()) {
            EmptyState(
                title = if (vm.query.isBlank()) "Nothing opened yet" else "No matches",
                body = if (vm.query.isBlank()) "Files you open will show up here for quick access."
                else "No recently opened file matches your search.",
                modifier = Modifier.weight(1f),
            )
        } else {
            FilesList(recent, vm.layout, onOpen, onMore, Modifier.weight(1f))
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
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${(vm.filter?.label ?: "All files")}  ·  ${docs.size}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.weight(1f))
            Row(
                modifier = Modifier.clickable { onSortClick() }.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (vm.sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(vm.sort.label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 5.dp))
            }
        }
        if (docs.isEmpty()) {
            EmptyState(
                title = if (vm.query.isBlank() && vm.filter == null) "No documents" else "No matches",
                body = "Kern reads files from Documents and Downloads. Nothing is copied or uploaded.",
                modifier = Modifier.weight(1f),
            )
        } else {
            FilesList(docs, vm.layout, onOpen, onMore, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FilesList(
    docs: List<ScannedDoc>,
    layout: BrowserLayout,
    onOpen: (ScannedDoc) -> Unit,
    onMore: (ScannedDoc) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (layout == BrowserLayout.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(docs, key = { it.path }) { doc -> FileCard(doc, onOpen, onMore) }
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 96.dp)) {
            items(docs, key = { it.path }) { doc -> FileRow(doc, onOpen, onMore) }
        }
    }
}

@Composable
private fun FileRow(doc: ScannedDoc, onOpen: (ScannedDoc) -> Unit, onMore: (ScannedDoc) -> Unit) {
    val style = doc.format.style()
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(doc) }.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FormatTile(style.mono, style.hue, 40.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(doc.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${style.mono}  ·  ${prettySize(doc.size)}  ·  ${prettyDate(doc.modified)}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onMore(doc) }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
    }
}

@Composable
private fun FileCard(doc: ScannedDoc, onOpen: (ScannedDoc) -> Unit, onMore: (ScannedDoc) -> Unit) {
    val style = doc.format.style()
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(doc) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(80.dp).background(style.hue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(style.mono, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = style.hue)
            }
            Column(Modifier.padding(10.dp)) {
                Text(doc.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${prettySize(doc.size)}  ·  ${prettyDate(doc.modified)}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun FormatTile(mono: String, hue: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier.size(size).background(hue.copy(alpha = 0.14f), RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(mono, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = hue)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(44.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(
            body,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun PermissionGate(onGrant: () -> Unit, onPickFile: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = KernAccent, modifier = Modifier.size(40.dp))
        Text("Storage access needed", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 14.dp))
        Text(
            "Kern lists documents from your Documents and Downloads folders and edits them in place. It never copies your files and has no network access.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = onGrant, modifier = Modifier.padding(top = 16.dp)) { Text("Grant access") }
        TextButton(onClick = onPickFile) { Text("Or open a single file") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(vm: FileBrowserViewModel, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text("Sort by", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            for (key in SortKey.entries) {
                val selected = vm.sort == key
                ListItem(
                    headlineContent = { Text(key.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                    trailingContent = {
                        if (selected) {
                            Icon(
                                if (vm.sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    modifier = Modifier.clickable { vm.pickSort(key) },
                )
            }
            Text(
                "Tap the active field again to reverse direction.",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(doc.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(16.dp))
            ActionRow(Icons.Default.FolderOpen, "Open", onOpen)
            ActionRow(if (pinned) Icons.Default.Star else Icons.Default.StarBorder, if (pinned) "Unpin" else "Pin to top", onTogglePin)
            ActionRow(Icons.Default.Share, "Share a copy", onShare)
            ActionRow(Icons.Default.Info, "File info", onInfo)
            ActionRow(Icons.Default.Delete, "Delete", onDelete, danger = true)
        }
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, danger: Boolean = false) {
    val color = if (danger) Color(0xFFD6453D) else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(label, color = color) },
        leadingContent = { Icon(icon, contentDescription = null, tint = if (danger) Color(0xFFD6453D) else MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onClick() },
    )
}

@Composable
private fun FileInfoDialog(doc: ScannedDoc, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
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
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(76.dp))
        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

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
