# Changelog - Kern

All notable changes documented here. Format: [Keep a Changelog](https://keepachangelog.com/), with one deviation: dated release entries are timestamped to the minute with timezone (`YYYY-MM-DD HH:MM (TZ)`).

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