package dev.kern.editors.epub

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kern.shared.find.FindMessages
import dev.kern.shared.find.FindState
import dev.kern.shared.find.FindTarget
import dev.kern.shared.find.SegmentMatch
import dev.kern.shared.find.findIn
import dev.kern.shared.io.DocumentIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * EPUB editor state. Chapters are loaded once; the user pages through them (TOC +
 * prev/next) and edits block text. Edits are tracked by (chapter index, block
 * index) so saving applies only them onto the original EPUB (see [EpubDocument]).
 */
class EpubEditorViewModel(app: Application) : AndroidViewModel(app), FindTarget {

    var fileName by mutableStateOf("")
        private set
    var bookTitle by mutableStateOf("")
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var currentChapter by mutableIntStateOf(0)
        private set

    private var chapters by mutableStateOf<List<EpubDocument.Chapter>>(emptyList())
    private val edits = mutableStateMapOf<Pair<Int, Int>, String>()

    private var uri: Uri? = null
    private var originalBytes: ByteArray? = null
    private var started = false

    val chapterCount: Int get() = chapters.size
    val dirty: Boolean get() = edits.isNotEmpty()
    val currentBlocks: List<EpubDocument.Block> get() = chapters.getOrNull(currentChapter)?.blocks ?: emptyList()

    fun chapterTitles(): List<String> = chapters.map { it.title }

    /** Current (possibly edited) text for a block in the current chapter. */
    fun blockText(block: EpubDocument.Block): String = blockText(currentChapter, block)

    private fun blockText(chapter: Int, block: EpubDocument.Block): String = edits[chapter to block.index] ?: block.text

    fun editBlock(block: EpubDocument.Block, text: String) = setBlockText(currentChapter, block, text)

    private fun setBlockText(chapter: Int, block: EpubDocument.Block, text: String) {
        val key = chapter to block.index
        if (text == block.text) edits.remove(key) else edits[key] = text
    }

    // Find and replace

    override val find = FindState()

    /** A block: [position] in the [chapter]'s block list (not the block's own index). */
    data class BlockSpot(val chapter: Int, val position: Int)

    val matches: List<SegmentMatch<BlockSpot>> by derivedStateOf {
        val finder = find.activeFinder ?: return@derivedStateOf emptyList()
        finder.findIn(
            chapters.asSequence().flatMapIndexed { c, chapter ->
                chapter.blocks.asSequence().mapIndexed { i, block -> BlockSpot(c, i) to blockText(c, block) }
            },
        )
    }

    override val matchCount: Int get() = matches.size
    override val canReplace: Boolean get() = true

    val currentMatch: SegmentMatch<BlockSpot>? get() = matches.getOrNull(find.currentIn(matches.size))

    override fun replaceCurrent() {
        val finder = find.finder ?: return
        val match = currentMatch ?: return
        val block = chapters.getOrNull(match.key.chapter)?.blocks?.getOrNull(match.key.position) ?: return
        finder.replaceAt(blockText(match.key.chapter, block), match.match, find.replacement)
            .onSuccess {
                setBlockText(match.key.chapter, block, it)
                find.message = null
                find.reveal()
            }
            .onFailure { find.message = FindMessages.failed(it) }
    }

    /** Replaces every match in every chapter; nothing changes if any replacement fails. */
    override fun replaceAll() {
        val finder = find.finder ?: return
        val outcome = runCatching {
            chapters.flatMapIndexed { c, chapter ->
                chapter.blocks.mapNotNull { block ->
                    val text = blockText(c, block)
                    if (finder.count(text) == 0) null else Triple(c, block, finder.replaceAll(text, find.replacement).getOrThrow())
                }
            }
        }
        val changes = outcome.getOrElse { find.message = FindMessages.failed(it); return }
        for ((c, block, result) in changes) setBlockText(c, block, result.value)
        find.message = FindMessages.replaced(changes.sumOf { it.third.count })
    }

    fun goToChapter(index: Int) {
        if (index in chapters.indices) currentChapter = index
    }

    fun nextChapter() = goToChapter(currentChapter + 1)
    fun previousChapter() = goToChapter(currentChapter - 1)

    fun start(encodedUri: String?) {
        if (started) return
        started = true
        val ctx = getApplication<Application>()
        val decoded = encodedUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(Uri.decode(it)) }
        if (decoded == null) {
            loading = false
            error = "No file was provided."
            return
        }
        uri = decoded
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    DocumentIo.tryPersist(ctx, decoded)
                    val bytes = DocumentIo.readBytes(ctx, decoded)
                    Triple(DocumentIo.displayName(ctx, decoded), bytes, EpubDocument.read(bytes))
                }
            }
            result.onSuccess { (name, bytes, parsed) ->
                fileName = name
                bookTitle = parsed.title
                originalBytes = bytes
                chapters = parsed.chapters
                loading = false
            }.onFailure {
                error = it.message ?: "Could not open the EPUB."
                loading = false
            }
        }
    }

    fun save(onResult: (ok: Boolean, message: String?) -> Unit) {
        val target = uri ?: run { onResult(false, "Nothing to save."); return }
        write(target) { ok, msg ->
            if (ok) edits.clear()
            onResult(ok, msg)
        }
    }

    fun exportTo(target: Uri, onResult: (ok: Boolean, message: String?) -> Unit) = write(target, onResult)

    private fun write(target: Uri, onResult: (ok: Boolean, message: String?) -> Unit) {
        val ctx = getApplication<Application>()
        val bytes = originalBytes ?: run { onResult(false, "Nothing to save."); return }
        val snapshot = edits.toMap()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { DocumentIo.writeBytes(ctx, target, EpubDocument.applyEditsAndSerialize(bytes, snapshot)) }
            }
            onResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }
}
