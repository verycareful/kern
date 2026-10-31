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
| EPUB reader | `src/editors/epub/` | jsoup-backed chapter text editing |
| Text and Markdown editor | `src/editors/text/` | Raw source editor for `.md`, `.txt`, `.rst`, `.org`; Markdown toolbar and syntax colouring |
| Markdown preview | `src/editors/markdown/` | commonmark-java renderer and the offline WebView preview |
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
  -> EpubEditor(filePath)
  -> TextEditor(filePath) for Markdown and plain text
```

External "Open with" intents bypass FileBrowser and land directly on the correct editor.

## File access model

- Primary: `MANAGE_EXTERNAL_STORAGE` (scoped guidance toward Documents + Downloads)
- Secondary: SAF (Storage Access Framework) for files opened via picker or "Open with"
- Intent filters registered for supported MIME types
- No app-owned folder. Files are read and written at their original path.

## PDF bridge

PDF rendering is delegated to a native engine through a JNI/NDK bridge declared in `src/pdf-bridge/`. The Kotlin side exposes a small handle-based surface (open, page count, render, close); the native library is loaded at runtime.

## Markdown preview

The editor always works on the raw Markdown. Preview renders it in two steps:

1. `MarkdownRenderer` parses the text with commonmark-java (CommonMark plus the GitHub extensions: tables, task lists, strikethrough, autolinks, footnotes, alerts, heading anchors, YAML front matter) and adds `$...$` / `$$...$$` maths, `math` and `mermaid` fences, and `:shortcode:` emoji. Images next to the file are inlined as `data:` URIs.
2. `MarkdownPage` wraps the body in a page whose scripts and styles come from `assets/preview/` (KaTeX, Mermaid, highlight.js, `preview.js`, `preview.css`), loaded in a WebView with no file or content access.

The page's content security policy runs only the bundled scripts: script tags, event handlers and `javascript:` links written in a document are refused, and images other than `data:` URIs are not loaded, so the preview never reaches the network. Web, mail and phone links open in the app the system picks.

Text files are saved in the encoding, byte order mark and line endings they were opened with, so an unedited file is written back byte for byte.

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
| jsoup | EPUB XHTML, Markdown image inlining | MIT |
| commonmark-java | Markdown parsing and HTML rendering | BSD-2-Clause |
| KaTeX (bundled asset) | Maths in the Markdown preview | MIT |
| Mermaid (bundled asset) | Diagrams in the Markdown preview | MIT |
| highlight.js (bundled asset) | Code colouring in the Markdown preview | BSD-3-Clause |
| gemoji (bundled data) | Emoji shortcode list | MIT |
| Jetpack Compose | UI | Apache 2.0 |
| Kotlin Coroutines | Async file ops | Apache 2.0 |
