package no.nav.eessi.pensjon.fagmodul.api

import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.fagmodul.eux.*
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Vedlegg
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.vedlegg.client.HentdokumentInnholdResponse
import no.nav.eessi.pensjon.vedlegg.client.VariantFormat
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import no.nav.eessi.pensjon.vedlegg.VedleggService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.*
import org.springframework.util.LinkedMultiValueMap
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

@ExtendWith(MockitoExtension::class)
class BucControllerTest {

    @Spy
    lateinit var auditLogger: AuditLogger

    @Mock
    lateinit var mockVedleggService: VedleggService

    @Spy
    lateinit var mockEuxService: EuxService

    @Mock
    lateinit var mockAktoerIdHelper: AktoerregisterService

    private lateinit var bucController: BucController

    @BeforeEach
    fun before() {
        this.bucController = BucController(mockEuxService, mockVedleggService, mockAktoerIdHelper, auditLogger)
    }


    @Test
    fun `gets valid bucs fagmodul can handle excpect list`() {
        val result = bucController.getBucs()
        Assertions.assertEquals(10, result.size)
    }

    @Test
    fun `gitt at det finnes en gydlig euxCaseid og Buc skal det returneres en liste over sedid`() {
        val gyldigBuc = String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-279020big.json")))

        val mockEuxRinaid = "123456"
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())

        doReturn(buc).whenever(mockEuxService).getBuc(any())

        val actual = bucController.getAllDocuments(mockEuxRinaid)

        Assertions.assertNotNull(actual)
        Assertions.assertEquals(25, actual.size)
    }

    @Test
    fun `gitt Et Gyldig PutVedleggTilDokument Saa Kall EuxPutVedleggPaaDokument`() {
        val etVedleggBase64 = String(Base64.getEncoder().encode(Files.readAllBytes(Paths.get("src/test/resources/etbilde.pdf"))))

        val rinasakid = "456"
        val rinadocid = "7892"

        val vedlegg = Vedlegg("enfil.pdf", etVedleggBase64)
        val filtype = "application/pdf".split("/")[1]

        doReturn(HentdokumentInnholdResponse(vedlegg.filInnhold,
                vedlegg.filnavn,
                "application/pdf")).whenever(mockVedleggService).hentDokumentInnhold(any(), any(), any())

        doNothing().whenever(mockEuxService).leggTilVedleggPaaDokument(any(), any(), any(), any(), any())


        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA

        val disposition = ContentDisposition
                .builder("form-data")
                .name("file")
                .filename("")
                .build().toString()

        val attachmentMeta = LinkedMultiValueMap<String, String>()
        attachmentMeta.add(HttpHeaders.CONTENT_DISPOSITION, disposition)
        val dokumentInnholdBinary = Base64.getDecoder().decode(vedlegg.filInnhold)
        val attachmentPart = HttpEntity(dokumentInnholdBinary, attachmentMeta)

        val body = LinkedMultiValueMap<String, Any>()
        body.add("multipart", attachmentPart)

        bucController.putVedleggTilDokument("123",
                rinasakid,
                rinadocid,
                "1",
                "2",
                VariantFormat.ARKIV )

        verify(mockEuxService, times(1)).leggTilVedleggPaaDokument(
                eq("123"),
                eq(rinasakid),
                eq(rinadocid),
                any(),
                eq(filtype)
        )
    }
}