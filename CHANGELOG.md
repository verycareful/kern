# Changelog - Kern

All notable changes documented here. Format: [Keep a Changelog](https://keepachangelog.com/), with one deviation: dated release entries are timestamped to the minute with timezone (`YYYY-MM-DD HH:MM (TZ)`).

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