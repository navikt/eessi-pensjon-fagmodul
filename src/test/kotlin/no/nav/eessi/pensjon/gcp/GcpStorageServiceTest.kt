package no.nav.eessi.pensjon.gcp

import com.google.cloud.WriteChannel
import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.api.gax.paging.Page
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class GcpStorageServiceTest {

    private fun buildService(storage: Storage) = GcpStorageService(
        gjennyBucket = "gjenny",
        p8000Bucket = "p8000",
        p6000Bucket = "p6000",
        vedleggBucket = "vedlegg",
        saksBehandlApiBucket = "saksbehandling-api",
        pBuc02Bucket = "pbuc02",
        gcpStorage = storage
    )


    @Test
    fun `lagreVedtakInfoPerDokument skal lagre storrelse for et enkelt dokument`() {
        val storage = mockk<Storage>()
        val channel = mockk<WriteChannel>(relaxed = true)
        val blobInfoSlot = slot<BlobInfo>()
        val writtenDataSlot = slot<java.nio.ByteBuffer>()

        every { storage.get(any<String>()) } returns mockk(relaxed = true)
        every { storage.writer(capture(blobInfoSlot)) } returns channel
        every { channel.write(capture(writtenDataSlot)) } answers { writtenDataSlot.captured.remaining() }

        val service = buildService(storage)

        service.lagreVedtakInfoForDokument("rina-123", "doc-456", "vedlegg-1", "2048")

        assertEquals(BlobId.of("vedlegg", "rina-123/doc-456/vedlegg-1"), blobInfoSlot.captured.blobId)
        val bytes = ByteArray(writtenDataSlot.captured.remaining())
        writtenDataSlot.captured.get(bytes)
        assertEquals("2048", String(bytes, StandardCharsets.UTF_8))
        verify(exactly = 0) { storage.get(any<BlobId>()) }
        verify(exactly = 1) { channel.close() }
    }

    @Test
    fun `hentSamletVedtakInfoStorrelse skal summere størrelsen på alle dokumenter lagret under en rinaSakId og euxCaseId`() {
        val storage = mockk<Storage>()
        val page = mockk<Page<Blob>>()
        val blob1 = mockk<Blob>()
        val blob2 = mockk<Blob>()

        every { blob1.name } returns "rina-123/doc-456/vedlegg-1"
        every { blob2.name } returns "rina-123/doc-456/vedlegg-2"
        every { page.iterateAll() } returns listOf(blob1, blob2)

        every { storage.get(any<String>()) } returns mockk(relaxed = true)
        every { storage.list("vedlegg", *anyVararg()) } returns page

        val blob1Innhold = mockk<Blob>()
        val blob2Innhold = mockk<Blob>()
        every { blob1Innhold.exists() } returns true
        every { blob1Innhold.getContent() } returns "2048".toByteArray()
        every { blob2Innhold.exists() } returns true
        every { blob2Innhold.getContent() } returns "512".toByteArray()
        every { storage.get(BlobId.of("vedlegg", "rina-123/doc-456/vedlegg-1")) } returns blob1Innhold
        every { storage.get(BlobId.of("vedlegg", "rina-123/doc-456/vedlegg-2")) } returns blob2Innhold

        val service = buildService(storage)

        val sum = service.hentSamletVedtakInfoStorrelse("rina-123", "doc-456")

        assertEquals(2560L, sum)
    }
}
