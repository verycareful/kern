# Changelog - Kern

All notable changes documented here. Format: [Keep a Changelog](https://keepachangelog.com/), with one deviation: dated release entries are timestamped to the minute with timezone (`YYYY-MM-DD HH:MM (TZ)`).

## [0.1.10.0] - 2026-08-28 13:25 IST

### Added
- **Word Editor Rich-Text Engine**: POI XWPF block and run model over `doc.bodyElements` with non-destructive surgical round-trip serialization
- **Character Formatting Toolbar**: Bold, Italic, Underline, Strikethrough, dynamic Caret style tracking, font size picker sheet, and color palette with 12 theme accents, recent colors, and custom hex slots
- **Paragraph & List Formatting**: Title, Heading 1, Heading 2, and Body paragraph styles; Left, Center, Right, and Justify alignments; Bullet and Numbered lists; Indent increase/decrease
- **Editable Tables**: In-place table cell text editing, dynamic row insertion (+Row), column insertion (+Col), and row/column deletion (-Row/-Col)
- **Embedded Media**: Extraction and inline rendering of document raster images
- **Editing Tools**: 30-step bounded snapshot Undo and Redo history stack; collapsible Find & Replace bar with live match navigation, match counts, and Replace/Replace All; live word and character statistics in document footer

### Results
- 34 of 35 unit tests passed (15s, local Gradle `testDebugUnitTest`). Expected failure in `read_paragraphWithHyperlinkIsNotEditable` because hyperlinks are now editable in Phase 4/5; test suite will be updated in the mandatory tests-only 0.1.10.1 release

## [0.1.9.1] - 2026-08-28 13:08 IST

### Tests
- **Settings Screen Instrumented Coverage** (`SettingsScreenTest.kt`): smoke verification and license dialog display, scroll, and dismissal
- **Settings Persistence Instrumented Coverage** (`KernSettingsTest.kt`): SharedPreferences-backed persistence and state flow for theme mode, accent color, density, and scan toggles
- **Segmented Control Instrumented Coverage** (`KernSegmentedTest.kt`): click handling and index selection
- **Theme & Token Unit Tests** (`ThemeTokensTest.kt`): token resolution, light/dark contrasts, and format color mappings
- **Document Format Unit Tests** (`DocumentFormatTest.kt`): MIME type and file extension matching
- **Spreadsheet Label Unit Tests** (`SpreadsheetLabelTest.kt`): column index to letter label conversions
- **Word Document Unit Tests** (`WordDocumentTest.kt`): block/run model parsing, run formatting preservation, null-safe run attributes, and table index stability

### Results
- All unit and instrumented suites passed in Android Studio; visual inspection and interaction verified on device over USB debugging

## [0.1.9.0] - 2026-06-26 18:55 IST

### Added
- **Design system**: full custom token layer (`KernColorScheme`, `KernTheme`, `KernType`, `KernRadius`, `Density`) over Material 3, with light/dark neutrals, 12 user-selectable accent colours, per-format identity hues, and a locked Soft 6dp radius set
- **Typography**: four bundled font families (Outfit for UI, Quicksand for the wordmark, IBM Plex Mono for metadata, Sora for EPUB reading) shipped as `res/font/*.ttf` with SIL OFL licenses
- **Brand assets**: theme-aware chevron mark and wordmark lockup PNGs (`res/drawable-nodpi/`)
- **Shared UI component library** (`KernComponents.kt`): `KernIconButton`, `KernSegmented`, `FileBadge`, `OnDevicePill`, `SectionLabel`, `KernDivider`, `KernToggle`, `KernTopBar`, `KernBottomSheet`, `SheetActionRow`, `EditorToolbar`, `ToolbarButton`, `ToolbarSeparator`
- **Semantic icon set** (`KernIcons.kt`): Material Outlined mappings plus a custom GitHub vector
- **Settings screen** (`src/settings/`): header, centered brand lockup, accent-soft privacy hero, and grouped surface cards for Appearance (Theme + Density), Accent (12-swatch picker), Files & storage (scan toggles), Privacy & permissions (locked network/analytics, live storage-access status), and About (version, license, source code, PDF engine)
- **Settings persistence** (`KernSettings.kt`): observable SharedPreferences-backed store for theme mode, accent, density, scan-documents, and scan-downloads
- **License viewer**: bundled AGPL-3.0 text (`res/raw/license.txt`) displayed in a full-screen scrollable dialog from the About section
- **About links**: Source code row opens the Kern GitHub repo; PDF engine row opens the Qyra GitHub repo via external intents (no network permission needed)

### Changed
- Reskinned every screen to the new design system: file browser (header, tabs, search, pill chips, list rows, grid cards, sort/actions sheets, empty/permission states), CSV editor, Excel editor (cell ref bar, header tiles, sheet tabs), Word editor (paper page, formatting toolbar), EPUB editor (Sora reading body, chapter nav, TOC sheet), PowerPoint editor (16:9 canvas, thumbnail rail, slide toolbar), PDF editor (page/zoom pills, tools bottom sheet)
- `EditorChrome`: migrated to `KernTopBar` + tokens; added toolbar and extra-actions slots
- `GridEditorScreen`: sunken ref pill, accent header selection, format-hue header tints, bottom `EditorToolbar`, zoom badge
- `FileBrowserScreen`: full reskin with `BrandMark`, `OnDevicePill`, `KernSegmented` tabs, and format-hue integration
- `KernTopBar` now applies `statusBarsPadding()` and `EditorToolbar` applies `navigationBarsPadding()`, fixing the top-bar/status-bar clash across all editors and the settings screen
- `Theme.kt`: rewrote `KernTheme` composable to resolve tokens, project onto Material 3 `ColorScheme` (legacy call sites keep working), and set status-bar appearance
- Navigation: added `Destinations.SETTINGS` route wired from the browser's settings cog

### Fixed
- **Scan toggles are now functional**: `FileScanner` skips a folder whose toggle is off, so its files no longer appear or open in the app
- **Browser no longer rescans on every return**: scans once on first load, on a toggle change, or after a permission grant; returning from a file shows the cached list
- **Storage access status is live**: the Privacy & permissions row reflects the real permission state and lets you tap to grant when missing (previously hardcoded "GRANTED")
- **Status-bar clash**: settings screen, editor top bars, and file viewers now respect system insets
- **Navigation-bar clash**: editor bottom toolbars apply nav-bar padding

### Results
- 15 tests across 5 suites, all passed (Android Studio)
- Build verified in Development Phone using USB debugging; all changes are UI/design, no new automated tests in this release (tests-only 0.1.9.1 is due next)

## [0.1.8.1] - 2026-06-13 19:38 IST

### Tests
- Added `applyEdits_preservesColumnWidthsAndRowHeights` to `ExcelDocumentTest.kt` to cover POI serialization and dimension calculation of custom row heights and column widths.
- Verified removal of `.skill-extracted` from Git tracking tree.

### Results
- 15 tests across 5 suites, all passed (Android Studio)

## [0.1.8.0] - 2026-06-13 19:27 IST

### Added
- Resizable grid columns and rows with explicit spawned drag handles (#12)
- Double-tap auto-resize to fit column/row content lengths
- Support for persistence of grid dimensions in Excel (.xlsx) using Apache POI

### Changed
- Replaced invisible drag boundaries with explicit trailing-edge drag handles for selected headers
- Improved subcompose layout scrolling performance with binary search and offset mapping for dynamic dimensions
- Used `Modifier.zIndex` and non-consuming pointer input for flawless handle rendering and zero-lag sensitivity

### Results
- UI drag handles render perfectly and respond consistently without interfering with 2D grid panning
- 14 tests across 5 suites, all passed (local JVM via Gradle `testDebugUnitTest`)

## [0.1.7.11] - 2026-06-13 18:45 IST

### Changed
- Audited and updated minor/patch dependencies: `androidx.compose:compose-bom` to `2026.05.01` and `org.apache.poi` to `5.5.1` (closes #11)

### Results
- Build and tests pass with new library versions

## [0.1.7.10] - 2026-06-13 18:35 IST

### Changed
- Refactored `Modifier.clickable` usages on `Surface` components (such as in `FileBrowserScreen`) to use the native `onClick` parameter, adhering to Material 3 standards (closes #8)

### Results
- 14 tests across 5 suites, all passed (local JVM via Gradle `testDebugUnitTest`); UI-only change, component interaction and ripples verified manually on device

## [0.1.7.9] - 2026-06-13 18:20 IST

### Changed
- Refactored `mutableStateOf<Int>` to `mutableIntStateOf` across all `ViewModel`s and `GridEditorScreen` to reduce boxing allocations and improve Compose performance (closes #7)

### Results
- 14 tests across 5 suites, all passed (local JVM via Gradle `testDebugUnitTest`)

## [0.1.7.8] - 2026-06-13 18:10 IST

### Fixed
- Long or instructional messages (save/export errors, PDF tool errors) now display in a dismissible `AlertDialog` instead of a `Snackbar`. Short transient confirmations ("Saved", "Exported", "Deleted") remain as snackbars. Follows the Material 3 guideline: snackbars are for single-line confirmations only (closes #9)

### Changed
- `PdfEditorViewModel`: split `toolMessage` into `toolMessage` (success, snackbar) and `toolError` (errors/instructional, dialog); added `consumeToolError()`
- `EditorChrome` and `GridEditorScreen`: save/export result callbacks now branch on success (snackbar) vs failure (AlertDialog with title + body)

### Results
- 14 tests across 5 suites, all passed (local JVM via Gradle `testDebugUnitTest`); UI-only change, save/export and PDF tool feedback channels verified manually on device

## [0.1.7.7] - 2026-06-07 21:42 IST

### Fixed
- Excel merged cells now render with true visual spanning for every merge shape. A merged region (horizontal, vertical, or cross) appears as a single cell whose size covers the full spanned area: `colSpan * cellWidth` wide and `rowSpan * cellHeight` tall. Non-origin positions inside a merge are not rendered at all (the origin cell covers them), and the spanning cell looks identical to a normal cell, just larger. Clicking anywhere inside it selects the merge origin. Previously every cell in a merged region appeared as a separate, indistinguishable cell (closes #1)

### Changed
- Grid body now renders through a single `SubcomposeLayout` that virtualises both axes together, replacing the `LazyColumn` + per-row `LazyRow` approach. Only cells whose pixel rectangles intersect the viewport are subcomposed; merged origins overlapping the viewport from outside are subcomposed too, so a partially-scrolled span is never blank. This architecture is what makes true 2D merge spanning expressible. Scroll is driven by `detectDragGestures` (both axes move on one finger drag) with per-axis fling via `Animatable.animateDecay`. The frozen column header and row gutter are offset-positioned against the same pixel scroll state

### Results
- 14 tests across 5 suites, all passed (0.987s, local JVM via Gradle `testDebugUnitTest`); merged-cell spanning and 2D scroll verified manually on device with a multi-region `.xlsx`

## [0.1.7.6] - 2026-06-07 11:30 IST

### Changed
- Grid horizontal axis now uses `LazyRow` instead of a plain `Row` with `horizontalScroll`. Only the columns visible in the viewport are composed; off-screen columns are discarded and recycled as you scroll, matching the existing vertical behaviour (`LazyColumn`). The column header and each data row maintain their own `LazyListState` and stay in sync via a bidirectional coroutine effect (closes #2)

### Results
- 14 tests across 5 suites, all passed (local JVM via Gradle `testDebugUnitTest`); the laziness improvement is UI/perf behaviour, verified manually on device

## [0.1.7.5] - 2026-06-07 11:22 IST

### Fixed
- Add Row / Add Column now insert at the selection (a new row just below the selected row, a new column just to the right) instead of always appending to the end, in both the CSV and Excel editors (closes #6)

### Changed
- Excel: a mid-grid insert now shifts existing rows/columns structurally on save (POI `shiftRows`/`shiftColumns`) and re-keys pending edits, so formulas and formatting are preserved and saved cells stay aligned (rather than the edit-only save misaligning after an insert)

### Results
- 14 tests across 5 suites, all passed (local JVM via Gradle `testDebugUnitTest`); insert-at-selection and the Excel insert/edit/save/reopen cycle verified manually on device

## [0.1.7.4] - 2026-06-07 11:10 IST

### Fixed
- Excel: switching sheets now starts at the top-left instead of inheriting the previous sheet's scroll position. Both the horizontal and vertical grid scroll offsets reset when the active sheet changes (closes #3)

### Results
- 14 tests across 5 suites, all passed (local JVM via Gradle `testDebugUnitTest`); the per-sheet scroll reset is UI behavior, verified manually on device.

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