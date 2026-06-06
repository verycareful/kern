package dev.kern.shared

import androidx.compose.ui.graphics.Color

/**
 * Per-format visual identity (the hue each editor uses + a short monospace tag),
 * centralized so the file browser and any chrome can render format chips, tiles,
 * and labels consistently with the editors.
 */
data class FormatStyle(val hue: Color, val mono: String)

fun DocumentFormat.style(): FormatStyle = when (this) {
    DocumentFormat.CSV -> FormatStyle(Color(0xFF0E8E9A), "CSV")
    DocumentFormat.EXCEL -> FormatStyle(Color(0xFF1F8454), "XLSX")
    DocumentFormat.WORD -> FormatStyle(Color(0xFF2E68C4), "DOCX")
    DocumentFormat.POWERPOINT -> FormatStyle(Color(0xFFD06A2C), "PPTX")
    DocumentFormat.PDF -> FormatStyle(Color(0xFFC4332E), "PDF")
    DocumentFormat.EPUB -> FormatStyle(Color(0xFF6B4FA0), "EPUB")
}
