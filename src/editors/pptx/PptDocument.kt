package dev.kern.editors.pptx

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Apache POI bridge for .pptx presentations (XSLF). Extracts the editable text of
 * each slide's text shapes (in shape order) for a text-centric editor. POI cannot
 * render slide graphics on Android (that needs java.awt), so this edits text, not
 * a visual canvas. Saving re-opens the original and replaces only edited shapes'
 * text, keeping every other shape and slide intact.
 *
 * No Android/Compose dependencies, so it stays testable.
 */
object PptDocument {

    /** slides[s] = the text of each text shape on slide s, in shape order. */
    data class Parsed(val slides: List<List<String>>)

    fun read(bytes: ByteArray): Parsed {
        XMLSlideShow(ByteArrayInputStream(bytes)).use { ppt ->
            val slides = ppt.slides.map { slide ->
                slide.shapes.filterIsInstance<XSLFTextShape>().map { it.text ?: "" }
            }
            return Parsed(slides.ifEmpty { listOf(emptyList()) })
        }
    }

    /** Writes [edits] (slide index, text-shape index -> new text) back into the original. */
    fun applyEditsAndSerialize(originalBytes: ByteArray, edits: Map<Pair<Int, Int>, String>): ByteArray {
        XMLSlideShow(ByteArrayInputStream(originalBytes)).use { ppt ->
            val slides = ppt.slides
            for ((key, text) in edits) {
                val (slideIndex, shapeIndex) = key
                if (slideIndex < 0 || slideIndex >= slides.size) continue
                val textShapes = slides[slideIndex].shapes.filterIsInstance<XSLFTextShape>()
                textShapes.getOrNull(shapeIndex)?.setText(text)
            }
            ByteArrayOutputStream().use { out ->
                ppt.write(out)
                return out.toByteArray()
            }
        }
    }
}
