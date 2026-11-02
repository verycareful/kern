package dev.kern.shared.find

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the find bar holds: whether it is open, the find and replace fields, the two
 * toggles, and which match is current. Every editor owns one and reads [finder] to search
 * its own content; the bar edits it.
 *
 * Changing the query or a toggle makes the first match current again and clears [message].
 */
@Stable
class FindState {
    var isOpen by mutableStateOf(false)
        private set
    var isReplaceOpen by mutableStateOf(false)

    private var queryValue by mutableStateOf("")
    private var matchCaseValue by mutableStateOf(true)
    private var regexValue by mutableStateOf(false)

    var query: String
        get() = queryValue
        set(value) {
            if (value != queryValue) { queryValue = value; restart() }
        }
    var matchCase: Boolean
        get() = matchCaseValue
        set(value) {
            if (value != matchCaseValue) { matchCaseValue = value; restart() }
        }
    var regex: Boolean
        get() = regexValue
        set(value) {
            if (value != regexValue) { regexValue = value; restart() }
        }
    var replacement by mutableStateOf("")

    /** Index of the current match among the editor's matches. */
    var current by mutableIntStateOf(0)
        private set

    /**
     * Bumped whenever the current match should be brought into view: stepping, a new
     * query, or a replace. Editors key their scrolling on it rather than on the match
     * list, so typing in the document does not jump back to a match.
     */
    var revision by mutableIntStateOf(0)
        private set

    /** A one-line note for the bar: a failed replacement, or how many were replaced. */
    var message by mutableStateOf<FindMessage?>(null)

    val compiled: FindQuery by derivedStateOf { FindQuery.compile(queryValue, FindOptions(matchCaseValue, regexValue)) }

    /** The finder for the current query, or null while the field is empty or invalid. */
    val finder: TextFinder? get() = (compiled as? FindQuery.Ready)?.finder

    /** Highlights and the match count apply only while the bar is open with a usable query. */
    val activeFinder: TextFinder? get() = if (isOpen) finder else null

    fun open() {
        isOpen = true
        reveal()
    }

    /** Asks the editor to bring the current match into view again. */
    fun reveal() { revision++ }

    /** Hides the bar. The fields keep their text for the next time it opens. */
    fun close() {
        isOpen = false
        message = null
    }

    fun toggle() = if (isOpen) close() else open()

    fun next(count: Int) {
        if (count > 0) current = (current.coerceIn(0, count - 1) + 1) % count
        reveal()
    }

    fun previous(count: Int) {
        if (count > 0) current = (current.coerceIn(0, count - 1) - 1 + count) % count
        reveal()
    }

    /** Makes match [index] current, for example the match nearest the caret. */
    fun select(index: Int) {
        current = index.coerceAtLeast(0)
        reveal()
    }

    /** [current] clamped to [count] matches; a replace can shrink the list under it. */
    fun currentIn(count: Int): Int = if (count == 0) 0 else current.coerceIn(0, count - 1)

    private fun restart() {
        current = 0
        message = null
        reveal()
    }
}

/** A note under the find field. */
data class FindMessage(val text: String, val isError: Boolean)

/**
 * An editor's side of the find bar. The bar reads [find] and [matchCount] and calls the
 * replace actions; the editor watches [FindState.current] to reveal the current match.
 */
interface FindTarget {
    val find: FindState

    /** How many matches the current query has in the document. */
    val matchCount: Int

    /** True where the format has a working editor, so the replace row is offered. */
    val canReplace: Boolean get() = false

    /**
     * False where the search engine only does case-insensitive literal search (the
     * Markdown preview, PDF); the match case and regex toggles are then greyed out.
     */
    val supportsOptions: Boolean get() = true

    fun replaceCurrent() {}

    fun replaceAll() {}
}

/** Shared wording for the replace results the bar shows. */
object FindMessages {
    fun replaced(count: Int) = FindMessage(if (count == 1) "Replaced 1 match" else "Replaced $count matches", isError = false)

    fun failed(error: Throwable) = FindMessage(error.message ?: "Could not replace", isError = true)
}
