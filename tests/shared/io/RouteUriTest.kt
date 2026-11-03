package dev.kern.shared.io

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A document URI survives the trip through an editor route unchanged. The route
 * carries `Uri.encode(uri)`, and Navigation hands the editor that argument after
 * one `Uri.decode`, which is what [throughRoute] reproduces.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RouteUriTest {

    private fun throughRoute(uri: String): Uri? =
        DocumentIo.fromRouteArgument(Uri.decode(Uri.encode(uri)))

    @Test
    fun gmail_attachment_keeps_its_escaped_hash_and_query() {
        val gmail = "content://com.google.android.gm.sapi/someone%40example.com/message_attachment_external/" +
            "%23thread-f%3A1880100351649535311/%23msg-f%3A1880100351649535311/0.1" +
            "?account_type=com.google&mimeType=application%2Fvnd.openxmlformats-officedocument" +
            ".wordprocessingml.document&rendition=1"
        val uri = throughRoute(gmail)!!
        assertEquals(gmail, uri.toString())
        assertNull(uri.fragment)
        assertEquals("1", uri.getQueryParameter("rendition"))
    }

    @Test
    fun storage_document_keeps_its_escaped_separators() {
        val doc = "content://com.android.externalstorage.documents/document/primary%3ADocuments%2Fa%25b.docx"
        assertEquals(doc, throughRoute(doc).toString())
    }

    @Test
    fun file_uri_with_a_hash_in_its_name_round_trips() {
        val file = Uri.fromFile(java.io.File("/storage/emulated/0/Documents/notes #2.md")).toString()
        assertEquals(file, throughRoute(file).toString())
    }

    @Test
    fun blank_or_missing_argument_is_no_document() {
        assertNull(DocumentIo.fromRouteArgument(null))
        assertNull(DocumentIo.fromRouteArgument(""))
    }
}
