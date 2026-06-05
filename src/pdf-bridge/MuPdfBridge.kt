/*
 * Kern - MuPDF JNI bridge declarations.
 *
 * This file sits at the boundary between Kern and the Qyra MuPDF Rust layer and
 * therefore carries BOTH license headers, as required by CLAUDE.md:
 *
 *   Copyright (C) 2026 the Kern authors.
 *   Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 *
 *   Portions bridge to Qyra (https://github.com/zParik/Qyra), which is
 *   Licensed under the GNU General Public License v3.0 (GPL-3.0).
 *
 *   GPL-3.0 code inside an AGPL-3.0 project is license-compatible (upward).
 *   Any change to the Rust boundary MUST be coordinated with the Qyra
 *   maintainers (zParik) before merging.
 */
package dev.kern.pdfbridge

/**
 * Kotlin-side JNI declarations for the MuPDF engine compiled from Qyra's
 * `src-tauri/src/pdf/` Rust module into `mupdf_bridge.so`.
 *
 * STUB: the native library is not built or loaded yet. The `System.loadLibrary`
 * call and the `external` implementations are activated in milestone 0.1.5.0,
 * once the NDK + Rust build pipeline lands. Declarations are kept here so the
 * editor layer can be written against a stable surface.
 */
object MuPdfBridge {

    // Enabled in 0.1.5.0 when mupdf_bridge.so is produced by the NDK build.
    // init { System.loadLibrary("mupdf_bridge") }

    /** Opens a PDF document and returns an opaque native handle (0 on failure). */
    external fun openDocument(path: String): Long

    /** Total page count for an open document handle. */
    external fun pageCount(handle: Long): Int

    /** Renders a page to an ARGB_8888 byte array at the given scale. */
    external fun renderPage(handle: Long, pageIndex: Int, scale: Float): ByteArray

    /** Closes a document handle and frees native resources. */
    external fun closeDocument(handle: Long)
}
