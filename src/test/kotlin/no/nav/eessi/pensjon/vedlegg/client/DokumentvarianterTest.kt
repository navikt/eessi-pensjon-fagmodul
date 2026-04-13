package no.nav.eessi.pensjon.vedlegg.client

import no.nav.eessi.pensjon.utils.mapAnyToJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class DokumentvarianterTest {

    @Test
    fun `filstoerrelseMB should be included in the serialized variant`() {
        val dokumentvarianter = Dokumentvarianter("test.pdf", VariantFormat.ARKIV, "5000000").also { println(it.filstoerrelseMB) }
        val json = mapAnyToJson(dokumentvarianter)
        JSONAssert.assertEquals("""
            {
              "filnavn" : "test.pdf",
              "variantformat" : "ARKIV",
              "filstoerrelse" : "5000000",
              "filstoerrelseMB" : 5.0
            }
        """.trimIndent(), json, true)
    }

    @Test
    fun `filstoerrelseMB returns correct value for small file size`() {
        val dokumentvarianter = Dokumentvarianter("test.pdf", VariantFormat.ARKIV, "500000").also { println(it.filstoerrelseMB) }
        assertEquals(0.5, dokumentvarianter.filstoerrelseMB)
    }

    @Test
    fun `filstoerrelseMB returns correct value for large file size`() {
        val dokumentvarianter = Dokumentvarianter("test.pdf", VariantFormat.ARKIV, "100000000").also { println(it.filstoerrelseMB) }
        assertEquals(100.0, dokumentvarianter.filstoerrelseMB)
    }

    @Test
    fun `filstoerrelseMB returns null when filstoerrelse is null`() {
        val dokumentvarianter = Dokumentvarianter("test.pdf", VariantFormat.ARKIV, null).also { println(it.filstoerrelseMB) }
        assertNull(dokumentvarianter.filstoerrelseMB)
    }
    @Test
    fun `filstoerrelseMB rounds to two decimal places`() {
        val dokumentvarianter = Dokumentvarianter("test.pdf", VariantFormat.ARKIV, "1234567").also { println(it.filstoerrelseMB) }
        assertEquals(1.23, dokumentvarianter.filstoerrelseMB)
    }
}

