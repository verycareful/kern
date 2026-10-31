package dev.kern.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.kern.shared.theme.KernRadius
import dev.kern.shared.theme.KernTheme
import dev.kern.shared.theme.KernType
import dev.kern.shared.ui.KernDivider
import dev.kern.shared.ui.KernIconButton
import dev.kern.shared.ui.KernIcons

/**
 * Full-screen list of the open source software in Kern, built from the notices the
 * build bundles (see [Notices]). Tapping an entry shows its licence texts, read
 * from the assets only when the entry is opened.
 */
@Composable
internal fun NoticesDialog(onDismiss: () -> Unit) {
    val colors = KernTheme.colors
    val context = LocalContext.current
    val notices = remember {
        runCatching { context.assets.open(Notices.INDEX_PATH).bufferedReader().use { Notices.parse(it.readText()) } }
            .getOrDefault(emptyList())
    }
    var openTitle by remember { mutableStateOf<String?>(null) }
    val texts = remember { mutableMapOf<String, List<String>>() }
    fun paragraphsFor(id: String): List<String> = texts.getOrPut(id) {
        runCatching { context.assets.open(Notices.textPath(id)).bufferedReader().use { Notices.paragraphs(it.readText()) } }
            .getOrDefault(listOf("Licence text unavailable."))
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            color = colors.surface,
            shape = RoundedCornerShape(KernRadius.field),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Notices", style = KernType.sectionTitle, color = colors.text, modifier = Modifier.weight(1f))
                    KernIconButton(KernIcons.Close, "Close", onDismiss, tint = colors.text)
                }
                KernDivider()
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            "Kern is built with the open source software below. Tap an entry for its licence.",
                            style = KernType.body,
                            color = colors.textMid,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    if (notices.isEmpty()) {
                        item {
                            Text("Notices unavailable.", style = KernType.body, color = colors.danger, modifier = Modifier.padding(16.dp))
                        }
                    }
                    for (notice in notices) {
                        val open = openTitle == notice.title
                        item(key = "head:${notice.title}") {
                            NoticeHeader(notice, open, onToggle = { openTitle = if (open) null else notice.title })
                        }
                        if (open) {
                            if (notice.url.isNotBlank()) {
                                item(key = "url:${notice.title}") {
                                    Text(
                                        notice.url,
                                        style = KernType.meta,
                                        color = colors.accent,
                                        modifier = Modifier
                                            .clickable { openUrl(context, notice.url) }
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                    )
                                }
                            }
                            for (id in notice.textIds) {
                                items(paragraphsFor(id)) { paragraph ->
                                    Text(
                                        paragraph,
                                        style = KernType.meta.copy(lineHeight = 16.sp),
                                        color = colors.textMid,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                        item(key = "div:${notice.title}") { KernDivider() }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeHeader(notice: Notice, open: Boolean, onToggle: () -> Unit) {
    val colors = KernTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(notice.title, style = KernType.body.copy(fontWeight = FontWeight.SemiBold), color = colors.text)
            Text(notice.subtitle, style = KernType.meta, color = colors.textDim, maxLines = if (open) Int.MAX_VALUE else 2)
            Text(notice.license, style = KernType.meta, color = colors.accent)
        }
        TrailingIcon(if (open) KernIcons.ChevronDown else KernIcons.Chevron)
    }
}
