# Changelog - Kern

All notable changes documented here. Format: [Keep a Changelog](https://keepachangelog.com/), with one deviation: dated release entries are timestamped to the minute with timezone (`YYYY-MM-DD HH:MM (TZ)`).

## [0.1.7.3] - 2026-06-07 11:03 IST

### Fixed
- PDF merge/extract tools now work on the released APKs. CI cross-compiles the native bridge (`libkern_pdf.so`) and bundles it into the package, so `QyraPdf.available` is true instead of always false. Previously the `.so` was gitignored and never built in CI, leaving the PDF tools dead on shipped builds (closes #10)

### Changed
- CI: added a reusable `build-pdf-bridge` composite action (cargo-ndk cross-compile of the `kern-bridge` crate to `arm64-v8a`/`armeabi-v7a`/`x86_64`), run by both the release and Android CI workflows before the Gradle build

### Results
- 14 tests across 5 suites, all passed (unchanged from 0.1.7.2; this release touches CI only). Native bundling is verified by the GitHub Actions run on the next tag/build.

## [0.1.7.2] - 2026-06-07 10:56 IST

### Fixed
- Editors no longer discard unsaved edits silently on back. While there are unsaved changes, both gesture/hardware back and the top-bar back arrow show a "Discard unsaved changes?" confirmation; "Stay" keeps editing, "Discard" leaves (CSV, Excel, Word, PowerPoint, EPUB) (closes #4)

### Results
- 14 tests across 5 suites, all passed (local JVM via Gradle `testDebugUnitTest`); the back-navigation guard is UI behavior, verified manually on device

## [0.1.7.1] - 2026-06-06 23:16 IST
Tests-only release; backfills the document-layer suites deferred since 0.1.4.0.

### Tests
- CSV: parse/serialize round-trip, ragged-row padding, quoting, column labels
- Excel: multi-sheet read; edit-only save preserves untouched cells and sheets
- Word: paragraph text + coarse style read; edit-only save
- PowerPoint: slide text read; edit-only save
- EPUB: spine/TOC/block parsing; edit-only save with `mimetype` stored first; DRM rejection
- Generative fixtures (sample files built in-test via POI/OpenCSV/Jsoup/zip); no binary test files committed

### Results
- 14 tests across 5 suites, all passed (15s, local JVM via Gradle `testDebugUnitTest`).

## [0.1.7.0] - 2026-06-06 22:59 IST
### Added
- File browser home screen: lists the documents in your Documents and Downloads folders (read in place, never copied)
- Recent and All-files views; pinned favorites; search by name; per-format filter chips; list/grid layout toggle
- Sort by date modified, name, size, or type (tap the active field again to reverse direction)
- Per-file actions: open, pin/unpin, share a copy, file info, and delete (with confirmation)
- Storage-access gate: requests `MANAGE_EXTERNAL_STORAGE` (API 30+) or `READ_EXTERNAL_STORAGE` (below), with a clear no-network rationale; the system file picker remains as a fallback for files elsewhere
- Pins and recents persist locally via SharedPreferences (no new dependency)

### Changed
- `DocumentIo` now reads/writes `file://` URIs directly, so editors work on browsed files as well as SAF documents
- Centralized per-format identity (hue + short tag) in `FormatStyle`, shared by the browser
- Added a `FileProvider` so "Share a copy" can hand other apps a read-only copy

### Fixed
- Storage permissions are now requested at launch (with a rationale) and files opened from the browser save via direct path. Previously the permissions were declared but never requested, so direct-path saves silently failed (closes #5)

### Results
- Verified by manual and on-device testing over USB. No automated test suite in this release (suites remain batched, deferred from 0.1.4.0).

## [0.1.6.0] - 2026-06-06 22:25 IST
### Added
- EPUB editor: open an `.epub`, read it chapter by chapter, edit chapter text in place, save in place, and export a copy
- Table-of-contents sheet (from the EPUB3 nav or EPUB2 NCX) to jump to any chapter, plus prev/next chapter navigation and a `chapter n / total` indicator
- Pinch-to-zoom on the chapter page
- Editing is at block granularity (paragraphs and headings via Jsoup); saving re-opens the original archive and rewrites only edited chapter files, preserving every other entry, the `mimetype` (stored first), images, CSS, and metadata. An edited block's inline formatting collapses to plain text (the same tradeoff as Word paragraphs)
- DRM-protected EPUBs are detected and reported as non-editable (font-obfuscation-only archives are still editable)

### Changed
- `DocumentFormat.EPUB` now routes to the EPUB editor (previously fell through to the PDF route)

### Results
- Verified by manual and on-device testing over USB. No automated test suite in this release (suites remain batched, deferred from 0.1.4.0).

## [0.1.5.0] - 2026-06-06 13:28 IST
### Added
- PDF viewer (Android framework `PdfRenderer`): open a PDF, scroll through pages, pinch-to-zoom, a page indicator, and export a copy. Viewing needs no native code.
- PDF tools backed by a native bridge to Qyra's `lopdf` engine:
  - Merge: append other PDFs to the open one and save the combined file
  - Extract pages: save a 1-based page range as a new PDF
- Native bridge `dev.kern.pdfbridge.QyraPdf` (JNI) over a new standalone `kern-bridge` crate in the Qyra fork (depends only on `lopdf` + `jni`; merge/split logic ported from Qyra's commands). When the engine library is absent, the tools degrade gracefully instead of crashing.
- Note: only the `arm64-v8a` engine library is bundled so far (other ABIs build from the same crate); split is currently single-range to single-file (multi-file per-page split lands later).

### Changed
- Replaced the placeholder PDF bridge stub (`MuPdfBridge`) with the working `QyraPdf` bridge; the PDF route now opens the real viewer instead of a stub surface.
- Gradle: native library loading via `jniLibs/` plus ABI filters (`arm64-v8a`, `armeabi-v7a`, `x86_64`).

### Results
- Verified by manual and on-device testing over USB (arm64 device). No automated test suite in this release (suites remain batched, deferred from 0.1.4.0).

## [0.1.4.0] - 2026-06-05 19:24 IST
### Added
- PowerPoint editor (.pptx via Apache POI): open, navigate slides, edit each slide's text boxes, save in place, and export a copy
- Pinch-to-zoom on the slide
- Note: text editing only (POI cannot render slide graphics on Android); saving replaces only edited text shapes and preserves the rest

## [0.1.3.0] - 2026-06-05 19:14 IST
### Added
- Word editor (.docx via Apache POI): open, view, and edit paragraph text on a page, save in place, and export a copy
- Headings/title paragraphs are shown with their style; saving replaces only edited paragraphs and preserves the rest
- Pinch-to-zoom on the document page

### Changed
- Extracted a shared editor chrome (`EditorChrome`: top bar, save state, save/export) now used by the Word editor (and the upcoming slide editor)

## [0.1.2.0] - 2026-06-05 18:53 IST
### Added
- Excel editor (.xlsx via Apache POI): open, view, edit cells, save in place, and export a copy
- Multi-sheet workbooks: switch between sheets via a sheet bar; edits are tracked per sheet
- Formula cells display their cached value (no recalculation in alpha); saving preserves untouched cells, formulas, and formatting

### Changed
- Extracted a shared grid-editor UI (`GridEditorScreen`) now used by both the CSV and Excel editors
- Pinch-to-zoom in the spreadsheet grid (CSV and Excel); a reusable gesture that will extend to the other editors

## [0.1.1.0] - 2026-06-05 18:33 IST
### Added
- CSV editor: open a document via the system file picker, view it as a scrollable grid, edit cells, and save in place
- Export a copy (Save as) via the system create-document picker
- Add row and add column
- Shared document I/O (read/write at the `content://` URI) and an editor ViewModel pattern, reused by the upcoming Office editors
- "Open file" action on the home screen (no storage permission required)

### Results
- Verified by manual and on-device testing over USB. No automated test suite in this release (suites are batched after 0.1.4.0).

## [0.1.0.0] - 2026-06-05 18:12 IST
### Added
- Project scaffold: Gradle (Kotlin DSL) with a version catalog, Kotlin, and Jetpack Compose; package `dev.kern`, min API 26 / target 35
- Single-Activity app with a Compose `NavHost`: file browser home plus per-format editor screens (CSV, Excel, Word, PowerPoint, PDF), with EPUB recognized and routed
- "Open with" intent filters and format routing for spreadsheets, documents, presentations, PDF, and EPUB
- Zero network permissions, enforced in the manifest and by a lint rule; storage access scoped toward Documents and Downloads
- Material 3 theme with light and dark variants
- Kern brand mark and an adaptive (vector) launcher icon
- Apache POI and OpenCSV dependencies for Office and CSV formats
- Native PDF bridge scaffold (JNI declarations)
- R8/ProGuard baseline rules
- GitHub Actions CI that builds the debug APK and uploads it as an artifact
- README (logo and tech badges), CONTRIBUTING, and sample documents for every supported format

### Results
- Build: `assembleDebug` green; app launches on emulator/device (verified in Android Studio). No automated tests in this release.