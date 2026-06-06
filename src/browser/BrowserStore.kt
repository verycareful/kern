package dev.kern.browser

import android.content.Context

/**
 * Tiny persistence for the browser, backed by SharedPreferences (no extra
 * dependency). Keeps the set of pinned file paths and an ordered list of recently
 * opened file paths. All values are absolute filesystem paths.
 *
 * SharedPreferences hands back the SAME Set instance for `getStringSet`, so it is
 * never mutated in place - every write builds a fresh set.
 */
class BrowserStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("kern_browser", Context.MODE_PRIVATE)

    fun pins(): Set<String> = prefs.getStringSet(KEY_PINS, emptySet()).orEmpty().toSet()

    /** Toggles [path]'s pinned state; returns true if it is now pinned. */
    fun togglePin(path: String): Boolean {
        val set = pins().toMutableSet()
        val pinned = if (!set.add(path)) {
            set.remove(path); false
        } else true
        prefs.edit().putStringSet(KEY_PINS, set).apply()
        return pinned
    }

    fun recents(): List<String> =
        prefs.getString(KEY_RECENTS, "").orEmpty().split('\n').filter { it.isNotEmpty() }

    /** Records [path] as the most recently opened, de-duplicated, capped at [MAX_RECENTS]. */
    fun recordOpen(path: String) {
        val list = recents().toMutableList()
        list.remove(path)
        list.add(0, path)
        while (list.size > MAX_RECENTS) list.removeAt(list.size - 1)
        prefs.edit().putString(KEY_RECENTS, list.joinToString("\n")).apply()
    }

    /** Drops [path] from both pins and recents (used after a delete). */
    fun forget(path: String) {
        val pins = pins().toMutableSet().apply { remove(path) }
        val recents = recents().toMutableList().apply { remove(path) }
        prefs.edit()
            .putStringSet(KEY_PINS, pins)
            .putString(KEY_RECENTS, recents.joinToString("\n"))
            .apply()
    }

    private companion object {
        const val KEY_PINS = "pins"
        const val KEY_RECENTS = "recents"
        const val MAX_RECENTS = 50
    }
}
