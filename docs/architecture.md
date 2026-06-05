# Architecture - Kern

## Overview

Kern is a single-Activity Android app built with Kotlin and Jetpack Compose. It registers intent filters for supported MIME types, so any file manager or app on the device can hand a file directly to Kern via "Open with". Kern reads and writes files in place - no copying to an internal folder, no cloud sync, no data leaving the device.

## Key components

| Component | Location | Responsibility |
|-----------|----------|----------------|
| File browser | `src/browser/` | Scans Documents + Downloads, renders the file list |
| Intent receiver | `src/browser/IntentHandler.kt` | Receives "Open with" intents, routes to the correct editor |
| CSV editor | `src/editors/csv/` | OpenCSV-backed table editor |
| Excel editor | `src/editors/excel/` | Apache POI xlsx |
| Word editor | `src/editors/word/` | Apache POI docx |
| PowerPoint editor | `src/editors/pptx/` | Apache POI pptx |
| PDF editor | `src/editors/pdf/` | Compose PDF UI |
| PDF bridge | `src/pdf-bridge/` | Native PDF bridge (JNI/NDK) |
| Shared | `src/shared/` | Compose theme, common components, file utilities |

## Navigation

Single Activity. Compose `NavHost` with these destinations:

```
FileBrowser (home)
  -> CsvEditor(filePath)
  -> ExcelEditor(filePath)
  -> WordEditor(filePath)
  -> PdfEditor(filePath)
  -> PptEditor(filePath)
```

External "Open with" intents bypass FileBrowser and land directly on the correct editor.

## File access model

- Primary: `MANAGE_EXTERNAL_STORAGE` (scoped guidance toward Documents + Downloads)
- Secondary: SAF (Storage Access Framework) for files opened via picker or "Open with"
- Intent filters registered for supported MIME types
- No app-owned folder. Files are read and written at their original path.

## PDF bridge

PDF rendering is delegated to a native engine through a JNI/NDK bridge declared in `src/pdf-bridge/`. The Kotlin side exposes a small handle-based surface (open, page count, render, close); the native library is loaded at runtime.

## Data flow

1. User taps a file in the browser, or shares a file to Kern via "Open with".
2. `IntentHandler` resolves the MIME type, picks the editor, and passes the URI.
3. The editor opens the file (via SAF URI or direct path) and loads it into memory.
4. Compose renders the editable state.
5. On save, the editor writes back to the original URI/path.

## Dependencies

| Library | Purpose | License |
|---------|---------|---------|
| Apache POI | xlsx, docx, pptx | Apache 2.0 |
| OpenCSV | CSV parsing | Apache 2.0 |
| Jetpack Compose | UI | Apache 2.0 |
| Kotlin Coroutines | Async file ops | Apache 2.0 |
