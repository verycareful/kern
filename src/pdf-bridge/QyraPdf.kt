/*
 * Kern - Qyra PDF JNI bridge.
 *
 * This file sits at the boundary between Kern and Qyra's Rust PDF engine and
 * therefore carries BOTH license headers, as required by CLAUDE.md:
 *
 *   Copyright (C) 2026 the Kern authors.
 *   Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 *
 *   Bridges to Qyra (https://github.com/zParik/Qyra), licensed under the GNU
 *   General Public License v3.0 (GPL-3.0). GPL-3.0 code inside an AGPL-3.0
 *   project is license-compatible (upward). Any change to the Rust boundary
 *   (src-tauri/src/jni_api.rs in the Qyra fork) MUST be coordinated with the
 *   Qyra maintainers (zParik) before merging.
 */
package dev.kern.pdfbridge

import org.json.JSONObject

/**
 * Kotlin side of the Qyra native PDF bridge. The `nativeXxx` declarations map
 * 1:1 to the `Java_dev_kern_pdfbridge_QyraPdf_*` exports in the Qyra fork's
 * `src-tauri/src/jni_api.rs`, compiled into `libqyra_lib.so`.
 *
 * These ops are pure `lopdf` (no MuPDF, no ndk-context), so they operate on
 * plain filesystem paths: the caller copies the picked document(s) into the app
 * cache and passes absolute paths. Each native call returns a JSON string which
 * [Result] parses.
 *
 * The library is loaded lazily on first use. [available] reports whether the
 * `.so` is present (it is gitignored and produced by the cargo-ndk build, see
 * docs), so the UI can degrade gracefully when it has not been built yet.
 */
object QyraPdf {

    /** Outcome of a bridge call: either the produced output paths, or an error message. */
    sealed interface Result {
        data class Success(val outputPaths: List<String>) : Result
        data class Failure(val message: String) : Result
    }

    val available: Boolean by lazy {
        runCatching { System.loadLibrary("kern_pdf") }.isSuccess
    }

    /** Splits [sourcePath] into one PDF per page, written into [outDir]. */
    fun splitPerPage(sourcePath: String, outDir: String): Result =
        call { nativeSplitPerPage(sourcePath, outDir) }

    /**
     * Splits [sourcePath] by 1-based page ranges (e.g. "1-3,5,7-9"), one output
     * file per range, written into [outDir].
     */
    fun splitRanges(sourcePath: String, rangesSpec: String, outDir: String): Result =
        call { nativeSplitRanges(sourcePath, rangesSpec, outDir) }

    /** Merges [sourcePaths] (in order) into a single PDF at [outputPath]. */
    fun merge(sourcePaths: List<String>, outputPath: String): Result =
        call { nativeMerge(sourcePaths.joinToString("\n"), outputPath) }

    private inline fun call(block: () -> String): Result {
        if (!available) return Result.Failure("PDF engine (libqyra_lib.so) is not bundled in this build.")
        return runCatching { parse(block()) }
            .getOrElse { Result.Failure(it.message ?: "Native call failed") }
    }

    private fun parse(json: String): Result {
        val obj = JSONObject(json)
        if (!obj.optBoolean("ok", false)) {
            return Result.Failure(obj.optString("error", "Unknown error"))
        }
        val arr = obj.optJSONArray("paths")
        val paths = buildList {
            if (arr != null) for (i in 0 until arr.length()) add(arr.getString(i))
        }
        return Result.Success(paths)
    }

    // Native exports - see src-tauri/src/jni_api.rs in the Qyra fork.
    private external fun nativeSplitPerPage(path: String, outDir: String): String
    private external fun nativeSplitRanges(path: String, rangesSpec: String, outDir: String): String
    private external fun nativeMerge(pathsJoined: String, output: String): String
}
