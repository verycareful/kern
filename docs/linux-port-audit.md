# Linux port audit

Status: audit only. No source has been changed. Scope: everything under `src/`,
the build, the manifest and resources, CI, and docs, as of version 0.1.11.0.

Kern is about 23k lines of Kotlin in a single Android module. The part that
touches the Android platform is small. That is what makes a Linux port feasible.

## 1. Summary

| Layer | Linux-ready? | Notes |
|-------|--------------|-------|
| Document models (`*Document.kt`, `CellMerge`, `FormatStyle`, `DocumentFormat`, `Ppt*`, `Word*`) | Yes, mostly | Apache POI, OpenCSV and jsoup are pure JVM. The only Android-specific parts are comments about missing `java.awt`. |
| View-models | Needs work | 7 extend `AndroidViewModel` / take `Application`. |
| Compose UI (`editors/*Screen`, `shared/ui`, `shared/theme`) | Mostly | Compose runs on desktop. Touch-only input and Android-only resource and inset APIs need work. |
| File access (`DocumentIo`, `FileScanner`, `BrowserStore`) | No | Built on `content://`, `Uri`, `Environment`, `ContentResolver`, `SharedPreferences`. |
| PDF viewing (`PdfDocument`) | No | Uses `android.graphics.pdf.PdfRenderer`. Needs a replacement engine. |
| PDF tools (`QyraPdf`) | Close | Pure `lopdf` + `jni` cdylib. Needs a Linux target and `org.json`. |
| Build / CI / packaging | No | Android Gradle Plugin only. No desktop target, packaging or Linux CI. |

## 2. Recommended approach

Compose Multiplatform for Desktop (JVM target), sharing the existing Compose UI
and POI-based document code. A rewrite in another toolkit would discard about
80% of the code for no benefit, and POI/OpenCSV/jsoup would have to be replaced.

Restructure into a shared module plus two thin platform modules:

```
:shared    (commonMain: models, view-models, UI, theme)
:android   (existing manifest, MainActivity, FileScanner, PdfRenderer, ...)
:desktop   (main(), XDG file access, PDF engine, packaging)
```

Because `build.gradle.kts` remaps `java.srcDirs("src")`, the layout can be kept
and migrated incrementally. Move platform classes behind interfaces first, and
split modules after that.

Alternative to weigh: a JVM-only desktop module that includes the shared sources
directly by source-set path, with no KMP at all. It is cheaper but leaves
`R.font` and `R.drawable` as separate problems. KMP is recommended because it
gives a proper resources API.

## 3. Findings by area

Severity: **B** = blocks a Linux build, **F** = functional gap on Linux,
**P** = polish / parity.

### 3.1 Build system

- **B** Only `com.android.application`. Add `org.jetbrains.compose` and a
  `jvm("desktop")` target, or a separate `:desktop` module (`build.gradle.kts`).
- **B** Compose is pinned through the Android Compose BOM (`2026.05.01`).
  Desktop needs the JetBrains Compose Multiplatform plugin and its own version
  line; the two must be kept compatible with Kotlin 2.0.21 (the Compose compiler
  plugin is already in use).
- **B** `androidx.navigation:navigation-compose` 2.8.3 is Android-only at that
  version. Use `org.jetbrains.androidx.navigation:navigation-compose`, or the
  KMP-published AndroidX lifecycle/navigation artifacts.
- **F** `androidx.compose.material:material-icons-extended` has no Compose
  Multiplatform artifact after 1.7. `KernIcons.kt` should be checked for which
  icons it really uses. Vendor those as `ImageVector`s or SVGs.
- **F** `androidx.lifecycle` `viewmodel-compose` and `runtime-ktx` need their KMP
  equivalents. `lifecycle-viewmodel-ktx` and `core-ktx` are Android-only.
- **P** `gradle-wrapper.jar` is not committed (CI notes this). Commit the wrapper
  so Linux contributors can run `./gradlew`.
- **P** `abiFilters` / `jniLibs` are Android concepts. Native Linux libs need
  their own resource location (e.g. `resources/linux-x64/`).

### 3.2 Android API usage that must be abstracted

Complete list from import and symbol scans (counts are import occurrences):

| API | Where | Linux replacement |
|-----|-------|-------------------|
| `android.net.Uri` (14) | everywhere a document is addressed | Common `DocumentRef` (sealed: path or opaque handle); keep `Uri` only in `:android`. |
| `Context` / `Application` / `AndroidViewModel` (6/7/7) | all 7 view-models, `DocumentIo`, `FileScanner`, `BrowserStore`, `KernSettings` | Inject a `PlatformServices` interface; use plain `ViewModel`. |
| `ContentResolver`, `OpenableColumns` | `DocumentIo.kt` | `java.nio.file.Files`. Display name is `Path.fileName`. |
| `takePersistableUriPermission` | `DocumentIo.tryPersist` | No-op. |
| `Environment.getExternalStoragePublicDirectory`, `isExternalStorageManager` | `FileScanner.kt` | XDG user dirs (see 3.3). Access check is a plain readability test. |
| `Settings.ACTION_MANAGE_*`, `Manifest.permission`, `ContextCompat` | `FileScanner`, `SettingsStorage.kt`, `FileBrowserScreen` | Remove. Show the "grant access" UI only when running under Flatpak and the home/Documents portal is missing. |
| `SharedPreferences` | `KernSettings`, `BrowserStore` | A small `KeyValueStore` interface. Desktop impl: JSON or properties file in `$XDG_CONFIG_HOME/kern/`. |
| `ActivityResultContracts.OpenDocument / CreateDocument / OpenMultipleDocuments / StartActivityForResult / RequestPermission` (5 files) | `FileBrowserScreen`, `EditorChrome`, `GridEditorScreen`, `PdfEditorScreen`, `SettingsStorage` | Common `FilePicker` interface. Desktop impl: native GTK dialog via the xdg-desktop-portal `FileChooser`, or AWT `FileDialog` / `JFileChooser` as a first cut. |
| `Intent` (ACTION_SEND chooser, ACTION_VIEW url), `FileProvider` | `FileBrowserScreen.shareDocument`, `SettingsLinks.openUrl`, `file_paths.xml` | `openUrl`: `Desktop.browse` or `xdg-open`. "Share a copy" has no Linux equivalent. Replace with "Reveal in file manager" (`org.freedesktop.FileManager1.ShowItems` or `xdg-open` on the parent dir) and "Copy path". |
| `ParcelFileDescriptor` | `PdfDocument.kt` | Not needed. |
| `Bitmap`, `BitmapFactory`, `Color` (2/2/1) | PDF page rendering, image decoding in the Word and PPT editors | `ImageBitmap` via Skia (`org.jetbrains.skia`) or `javax.imageio`. |
| `BackHandler`, `LocalOnBackPressedDispatcherOwner` | `UnsavedChangesGuard`, `EditorChrome` | Window-close request handler plus Esc / Alt+Left. |
| `WindowCompat`, `enableEdgeToEdge`, `statusBarsPadding`, `navigationBarsPadding`, `WindowInsets.systemBars`, `rootWindowInsets.displayCutout` | `Theme.kt`, `MainActivity`, `KernComponents`, `SettingsScreen`, `PptEditorScreen` | Drop on desktop (insets are zero). Wrap in an `expect`/interface so common code stays clean. Cutout handling is irrelevant. |
| `LocalConfiguration` (orientation) | `PptEditorScreen` | Derive from window size. |
| `DateUtils.getRelativeTimeSpanString` | `FileBrowserScreen.kt` | `java.time` + a small relative-time formatter. |
| `R.font`, `R.drawable`, `R.raw`, `R.string` | `Type.kt`, `Brand.kt`, `SettingsLinks.kt` | Compose Resources (`Res.font`, `Res.drawable`, `Res.readBytes`). Move `res/font`, `res/drawable-nodpi`, `res/raw`. |
| `org.json.JSONObject` | `QyraPdf.kt` | Not on the desktop JVM classpath. Add `org.json` or kotlinx-serialization. |
| `isSystemInDarkTheme()` | `Theme.kt` | Works on desktop in recent Compose Multiplatform, but verify it follows the GTK / portal color-scheme setting. If it does not, read `org.freedesktop.appearance color-scheme` from the settings portal. |

### 3.3 File access model (largest design change)

The Android model is "edit in place via `content://`, plus a scan of Documents
and Downloads". On Linux:

- **F** Address documents by `java.nio.file.Path`. Navigation routes currently
  carry `Uri.encode(uri.toString())` (`KernNavHost`, `IntentHandler`). Route
  arguments become encoded absolute paths. Keep `file://` support for
  command-line and `.desktop` launches.
- **F** Scan roots: resolve `XDG_DOCUMENTS_DIR` and `XDG_DOWNLOAD_DIR` from
  `$XDG_CONFIG_HOME/user-dirs.dirs` (fall back to `~/Documents` and `~/Downloads`).
  Do not hard-code the English names.
- **F** `FileScanner` follows directories without checking symlinks, with
  `MAX_DEPTH = 12`. On Linux, symlink loops and large trees (network mounts,
  `~/Downloads` with `node_modules`) are likelier. Add a visited-set keyed on
  the canonical path, and run the scan off the UI thread (check
  `FileBrowserViewModel`). Consider inotify via `WatchService` instead of rescanning.
- **F** Hidden-directory filter is `name.startsWith(".")`. This is correct on Linux.
- **F** Delete (`FileBrowserViewModel`: `File(d.path).delete()`) is permanent.
  Desktop users expect trash: use `gio trash` or the FreeDesktop Trash spec
  (`~/.local/share/Trash`).
- **F** Writes (`DocumentIo.writeBytes`) truncate and write in place. A crash or
  full disk mid-write corrupts the user's file. This is already a risk on
  Android; on Linux, write to a temp file in the same directory and
  `Files.move(..., ATOMIC_MOVE)`. Preserve permissions and symlinks (write
  through the link target).
- **F** Open-file locking: no lock files (`.~lock.x#`). Decide whether to
  honour LibreOffice-style lock files, and at minimum detect external
  modification before save (compare mtime).
- **F** Flatpak or Snap packaging sandboxes the filesystem. Use the
  `xdg-desktop-portal` file chooser so access works either way, and persist
  recents as paths the portal can still open.
- **P** `BrowserStore` stores raw absolute paths for pins and recents. Prune
  entries whose file no longer exists, and use `\n` safe encoding. A path can
  legally contain a newline on Linux, and recents are `\n`-joined.

### 3.4 PDF

- **B** `PdfDocument` uses the framework `PdfRenderer`, which does not exist on
  the JVM. Pick an engine:
  - Apache PDFBox (pure Java, Apache-2.0): lowest effort, slower, weaker on
    complex files.
  - MuPDF via JNI (already the project's stated direction through Qyra): best
    fidelity, but AGPL/GPL obligations need review (README says GPL-3.0 for the
    bridge; MuPDF itself is AGPL-3.0, which is fine with this project but should
    be stated correctly).
  - PDFium (`pdfium-binaries`) via JNI/Panama: good fidelity, permissive.
  Recommendation: put an interface (`PdfPageSource`) in common code with the
  current `PdfRenderer` class as the Android implementation, then ship PDFBox
  first and evaluate MuPDF.
- **F** `PdfRenderer` serialises page access under a lock and allows one open
  page at a time. The replacement should keep the same `renderPage(index,
  widthPx)` contract so `PdfEditorScreen` does not change.
- **F** `QyraPdf` loads `libkern_pdf` with `System.loadLibrary`. On desktop,
  extract `libkern_pdf.so` from jar resources to a temp dir and `System.load` it.
  The Qyra crate must build for `x86_64-unknown-linux-gnu` and
  `aarch64-unknown-linux-gnu`. Its JNI export names
  (`Java_dev_kern_pdfbridge_QyraPdf_*`) keep working on desktop unchanged, as
  does the `jni` crate, but nothing in this repo verifies that. Add a Linux
  build to the `build-pdf-bridge` action.
- **F** Merge and split copy inputs into `cacheDir/pdf-tools`. Replace with
  `$XDG_CACHE_HOME/kern/pdf-tools` created `0700`, since it holds plaintext copies.
  Cleanup on exit (`onCleared`) spawns a raw `Thread`. On desktop, also clean on
  JVM shutdown and at next start, because a killed process leaves the copies.
- **P** The bridge is built in a separate repo (`verycareful/Qyra`, ref `main`,
  unpinned). Pin a commit for reproducible Linux builds.

### 3.5 Office and CSV/EPUB formats

- **F** Spreadsheet editor claims `.xls`, Word claims `.doc`, and PowerPoint
  claims `.ppt` (`DocumentFormat`, manifest MIME list), but the documents only
  use `XSSF`/`XWPF`/`XSLF`. Legacy formats will fail to open, on Android too.
  Either implement via `HSSF`/`HWPF`/`HSLF` (`poi-scratchpad`) or drop the
  extensions. Decide this before adding file associations on Linux.
- **P** POI on desktop can use `java.awt`. The code deliberately avoids it
  (`PptDocument.kt` lines ~213, ~597, ~667). That is still valid. Do not
  "simplify" it on desktop, and keep headless mode: set
  `-Djava.awt.headless=true` if the app does not otherwise need AWT.
- **P** POI memory use: whole files are read into a `ByteArray`, then parsed.
  Desktop users will open larger files. Add size warnings and consider
  streaming for CSV.
- **P** Fonts used in documents (Word and PPT rich-text) are resolved by name
  (`PptRichText.kt` has a list of font families). Linux font availability
  differs: check the fallback mapping against fontconfig.
- **P** CSV encoding is read as UTF-8 only (`DocumentIo.readText`). Linux files
  are often Latin-1 or have BOMs; detect BOM at least.
- **P** Line endings: CSV/EPUB write paths should preserve the source's
  line-ending style.

### 3.6 UI and input

The UI is built for touch. A desktop build needs pointer and keyboard parity.

- **F** No keyboard shortcuts anywhere (no `onKeyEvent` / `onPreviewKeyEvent`
  uses). Need at least: Ctrl+S, Ctrl+Shift+S, Ctrl+O, Ctrl+W, Ctrl+Z / Ctrl+Y,
  Ctrl+F, Ctrl+B / I / U in Word, arrow keys and Tab / Enter in the grid editor,
  PageUp/PageDown in PDF, F11 and Ctrl+/- for zoom.
- **F** Grid editor (`GridEditorScreen`) uses custom `pointerInput` with a 200 ms
  tap and 300 ms double-tap threshold, plus a manual velocity-tracker fling.
  These are tuned for fingers. Add mouse-wheel scroll, Shift+wheel for
  horizontal scroll, click-drag selection, and right-click context menus.
- **F** Zoom (`Zoom.kt` `pinchZoom`, PPT viewport) is pinch-only. Add Ctrl+wheel,
  and touchpad pinch on Wayland/X11 where delivered.
- **F** Long-press interactions: audit `combinedClickable` / long-press uses and
  give each a right-click equivalent.
- **F** Window management: add a title bar with the document name and a dirty
  indicator, minimum window size, persisted size/position, and a
  window-close confirmation wired to `UnsavedChangesGuard` (replacing `BackHandler`).
- **F** Multiple windows or tabs: Android opens one document at a time; desktop
  users expect several. Decide: one process per file, or tabs. Navigation is
  currently a single `NavHost`.
- **F** IME: the soft-keyboard handling should be re-checked against desktop
  text input (IBus/Fcitx composition), mainly in the Word editor's rich text.
- **P** Touch target sizes and density tokens (`Dimens.kt`, `Density`) are
  mobile-sized. Add a compact desktop density.
- **P** Scroll bars: none visible. Desktop expects them.
- **P** Orientation-specific layout in `PptEditorScreen` should become
  width-based.
- **P** HiDPI / fractional scaling on Wayland: test.

### 3.7 Tests

- Unit tests (`tests/editors`, `tests/shared`) are plain JVM and use no Android
  classes. They should run unchanged on a desktop target. Wire the source sets
  in the new layout, and run them on Linux in CI.
- `tests/instrumented/*` use `AndroidJUnit4` and `Context`. They stay
  Android-only. Add Compose Desktop UI tests (`ui-test-junit4` with
  `runComposeUiTest`) for the settings screen and shared components.
- Add Linux-specific tests: XDG dir resolution, trash, atomic write, path
  routing with spaces / unicode / newline, and symlink-loop scanning.
- Sample files in `sample-files/` can drive round-trip tests for every format.

### 3.8 Packaging, integration and distribution

- **B** No installable artifact. Use the Compose Gradle plugin's
  `nativeDistributions` (jpackage) for `.deb` and `.rpm`, plus an AppImage and a
  Flatpak (Flathub is where most users will look). jpackage bundles a JRE, so
  document the size cost (~100 MB+) and consider `jlink` module trimming.
- **F** Desktop entry: `kern.desktop` with `MimeType=` covering every supported
  type, `Exec=kern %f` (or `%F` if multi-open), `Icon=`, `Categories=Office;`.
  The manifest intent filters map one-to-one to these MIME types.
- **F** Icons: provide SVG plus PNG sizes (16 to 512) for hicolor. Android adaptive
  launcher XML will not carry over, but `brand/kern-mark.svg` already exists.
- **F** AppStream metainfo (`io.github.verycareful.kern.metainfo.xml`) for software
  centres and Flathub.
- **F** CLI argument handling: `kern file1 file2`, and single-instance behaviour
  so a second launch forwards its file to the running window (D-Bus
  `org.freedesktop.Application` or a lock socket).
- **F** Wayland vs X11: JVM AWT under Wayland goes through XWayland unless using
  a JBR with native Wayland support. Test both. Set `StartupWMClass` to match
  the desktop file so the dock groups windows and shows the right icon.
- **P** Flatpak manifest: permissions `--filesystem=xdg-documents`,
  `--filesystem=xdg-download`, `--socket=wayland`, `--socket=fallback-x11`,
  `--share=ipc`, **and no `--share=network`**. This keeps the privacy guarantee
  intact and checkable.
- **P** Release signing for the Android APK is still open (issue #16 per
  `release.yml`). Do not copy that debug-signed pattern for Linux. Publish
  checksums and a detached signature.

### 3.9 Privacy and the no-network invariant

The project forbids network access, enforced on Android by the manifest and
lint (`config/lint.xml`). Linux has no permission system, so enforcement moves
elsewhere:

- Add a CI check (a Gradle task or a test) that fails if network APIs
  (`java.net.Socket`, `HttpURLConnection`, `HttpClient`, `URL.openStream`) appear
  in `src/`. `openUrl` must use `xdg-open` or `Desktop.browse`, which hand off to
  another process, matching the current Android reasoning.
- Verify that JetBrains tooling and the chosen PDF engine make no network calls
  (no update checks, no telemetry, no font or CDN fetches).
- In Flatpak, deny network in the manifest (see 3.8).
- jpackage'd JREs do not phone home, but confirm with a run under
  `unshare -n` or equivalent in CI.
- Temp and cache copies of user documents (PDF tools) need `0700` directories
  and cleanup (see 3.4).
- The README's "Permissions" section must gain a Linux section.

### 3.10 CI

`.github/workflows/android.yml` runs on `ubuntu-latest` already, so runners are
available. Add:

- `desktop.yml`: build the desktop target, run unit tests, run
  `packageDeb` / `packageAppImage`, upload artifacts.
- Headless UI tests under `xvfb-run`.
- Native bridge job for Linux targets in `build-pdf-bridge`.
- Flatpak builder job, once a manifest exists.
- Matrix for x86_64 and aarch64 (GitHub now provides arm64 Linux runners).

### 3.11 Documentation

- `README.md`: platform badge and "Android 8.0+" copy, Tech stack, Permissions,
  Getting started, Project structure all describe Android only.
- `docs/architecture.md`: entire "File access model" and "Navigation" sections
  assume `content://`; "Data flow" step 2 and 3 need a desktop variant.
- `src/pdf-bridge/README.md` should describe the Linux cdylib build (plain
  `cargo build --release`) next to the cargo-ndk instructions.
- `CONTRIBUTING.md`: add Linux build and run steps.
- Licence texts: bundle the MuPDF/PDFBox/POI NOTICE files in distributions.
- Note: `CLAUDE.md` is referenced by the manifest and bridge comments but is
  gitignored; contributors cannot see "What must never change". Publish that
  section (or equivalent) in `CONTRIBUTING.md`.

## 4. Suggested order of work

1. **Decide**: engine choice for PDF (3.4), `.xls/.doc/.ppt` scope (3.5), and
   single vs multi-window (3.6).
2. **Seams**: introduce `DocumentRef`, `PlatformServices`, `FilePicker`,
   `KeyValueStore`, `PdfPageSource`. Make Android implement them with no
   behaviour change. Land on `main` with Android CI green.
3. **Resources and icons**: migrate to Compose Resources, replace
   `material-icons-extended`.
4. **View-models**: drop `AndroidViewModel`.
5. **Desktop module**: `main()`, window, XDG dirs, desktop implementations,
   atomic writes, trash.
6. **Input parity**: keyboard shortcuts, mouse wheel, right-click, scroll bars.
7. **PDF**: desktop viewer, then Qyra Linux build.
8. **Packaging**: `.deb`, AppImage, then Flatpak. `.desktop` and metainfo.
9. **CI and docs**.
10. **Hardening**: no-network check, Wayland and X11 passes, HiDPI.

Steps 1 to 4 are the only ones that touch existing Android code, and each is
independently shippable.

## 5. Existing issues found along the way

These are not Linux-specific but should be tracked:

- `.xls`, `.doc`, `.ppt` are advertised but unsupported (see 3.5).
- Writes are not atomic (see 3.3).
- `READ_MEDIA_IMAGES` is declared in the manifest but nothing in `src/` appears
  to need it (documents are not images).
- `android:allowBackup="true"` backs up `kern_browser` prefs, which include
  absolute paths of the user's files.
- `FileProvider` exposes all of external storage (`path="."`). Narrow it to the
  shared file, or copy to cache.
- Release builds are debug-signed (issue #16).
- `BrowserStore` recents use `\n` as a separator (see 3.3).
- `ThemeMode.valueOf` in `KernSettings` throws on an unknown stored value; use a
  safe lookup like `AccentColor.fromName`.

## 6. Open questions for the maintainer

1. Is a Compose Multiplatform restructure acceptable, or should the desktop app
   live in a separate repo that depends on a shared document library?
2. Target distributions and architectures (x86_64 only, or aarch64 too)?
3. Is a bundled JRE (~100 MB+ installed) acceptable?
4. PDF engine: PDFBox for simplicity, or MuPDF for fidelity?
5. Should Linux get a document lock-file and external-change policy?
6. Should the privacy invariant be enforced in CI by a source scan, a sandbox
   run, or both?
