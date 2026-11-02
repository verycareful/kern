package dev.kern.shared.find

import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** The two toggles on the find bar. Match case is on and regex off unless the user changes them. */
data class FindOptions(val matchCase: Boolean = true, val regex: Boolean = false)

/** One match in a string: [start] inclusive, [end] exclusive. A match is never empty. */
data class TextMatch(val start: Int, val end: Int)

/** The result of a replacement: the new [value] and how many matches were replaced. */
data class Replaced<T>(val value: T, val count: Int)

/** What the find field currently holds: nothing, a pattern that does not compile, or a usable finder. */
sealed interface FindQuery {
    data object Empty : FindQuery
    data class Invalid(val message: String) : FindQuery
    class Ready internal constructor(val finder: TextFinder) : FindQuery

    companion object {
        /**
         * Compiles [query] under [options]. With regex off the query is matched literally;
         * with regex on, `^` and `$` match at line boundaries.
         */
        fun compile(query: String, options: FindOptions): FindQuery {
            if (query.isEmpty()) return Empty
            var flags = if (options.regex) Pattern.MULTILINE else Pattern.LITERAL
            if (!options.matchCase) flags = flags or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
            return try {
                Ready(TextFinder(Pattern.compile(query, flags), literal = !options.regex))
            } catch (e: PatternSyntaxException) {
                Invalid(e.description ?: INVALID_PATTERN)
            }
        }

        private const val INVALID_PATTERN = "Invalid pattern"
    }
}

/**
 * Finds and replaces one compiled query in plain strings.
 *
 * Empty matches are skipped everywhere: a pattern such as `a*` or `^` would otherwise
 * match between every character, which is never what a find bar means.
 *
 * In regex mode the replacement follows `java.util.regex` syntax: `$1` and `${name}` insert
 * a group, `\$` and `\\` insert the character itself. In literal mode the replacement is
 * inserted exactly as typed. A replacement that names a missing group, or ends in a lone
 * `\`, fails with the message to show in the bar.
 */
class TextFinder internal constructor(private val pattern: Pattern, private val literal: Boolean) {

    fun findAll(text: CharSequence): List<TextMatch> {
        val matcher = pattern.matcher(text)
        val out = ArrayList<TextMatch>()
        while (matcher.find()) {
            if (matcher.end() > matcher.start()) out += TextMatch(matcher.start(), matcher.end())
        }
        return out
    }

    fun count(text: CharSequence): Int = findAll(text).size

    /** The text that [match], found in [text], is replaced with. */
    fun expand(text: CharSequence, match: TextMatch, replacement: String): Result<String> = runCatching {
        if (literal) return@runCatching replacement
        val matcher = pattern.matcher(text)
        check(
            match.end <= text.length && matcher.find(match.start) &&
                matcher.start() == match.start && matcher.end() == match.end,
        ) {
            STALE_MATCH
        }
        val out = StringBuffer()
        matcher.appendReplacement(out, replacement)
        out.substring(match.start)
    }.recoverReplacementError()

    /** [text] with only [match] replaced. */
    fun replaceAt(text: String, match: TextMatch, replacement: String): Result<String> =
        expand(text, match, replacement).map { text.substring(0, match.start) + it + text.substring(match.end) }

    /** [text] with every match replaced, in one pass. */
    fun replaceAll(text: String, replacement: String): Result<Replaced<String>> = runCatching {
        val matcher = pattern.matcher(text)
        val template = if (literal) Matcher.quoteReplacement(replacement) else replacement
        val out = StringBuffer()
        var count = 0
        while (matcher.find()) {
            if (matcher.end() == matcher.start()) continue
            matcher.appendReplacement(out, template)
            count++
        }
        matcher.appendTail(out)
        Replaced(out.toString(), count)
    }.recoverReplacementError()

    private fun <T> Result<T>.recoverReplacementError(): Result<T> = recoverCatching { e ->
        throw when (e) {
            is IndexOutOfBoundsException, is IllegalArgumentException ->
                InvalidReplacementException(e.message ?: INVALID_REPLACEMENT)
            else -> e
        }
    }

    private companion object {
        const val STALE_MATCH = "The text changed since the search ran"
        const val INVALID_REPLACEMENT = "Invalid replacement"
    }
}

/** A regex replacement that cannot be expanded, such as `$3` when the pattern has two groups. */
class InvalidReplacementException(message: String) : IllegalArgumentException(message)
