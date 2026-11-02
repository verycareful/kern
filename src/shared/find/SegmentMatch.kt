package dev.kern.shared.find

/**
 * A match inside one segment of a document: a paragraph, a cell, a shape, a chapter
 * block. [key] says which segment; [match] is the range within that segment's text.
 */
data class SegmentMatch<K>(val key: K, val match: TextMatch)

/** Every match across [segments], in the order the segments are given. */
fun <K> TextFinder.findIn(segments: Sequence<Pair<K, CharSequence>>): List<SegmentMatch<K>> =
    segments.flatMap { (key, text) -> findAll(text).asSequence().map { SegmentMatch(key, it) } }.toList()
