package no.nav.eessi.pensjon.fagmodul.api

import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.fagmodul.eux.*
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Vedlegg
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Organisation
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.security.sts.typeRef
import no.nav.eessi.pensjon.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.services.arkiv.HentdokumentInnholdResponse
import no.nav.eessi.pensjon.services.arkiv.SafService
import no.nav.eessi.pensjon.services.arkiv.VariantFormat
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.*
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

@ExtendWith(MockitoExtension::class)
class BucControllerTest {

    @Mock
    lateinit var mockEuxrestTemplate: RestTemplate

    lateinit var mockEuxKlient: EuxKlient

    @Spy
    lateinit var auditLogger: AuditLogger

    @Mock
    lateinit var mockSafService: SafService

    @Mock
    lateinit var mockAktoerIdHelper: AktoerregisterService

    private lateinit var bucController: BucController

    @BeforeEach
    fun before() {
        mockEuxrestTemplate.errorHandler = DefaultResponseErrorHandler()
        mockEuxrestTemplate.interceptors = listOf( RequestResponseLoggerInterceptor() )
        this.mockEuxKlient = EuxKlient(mockEuxrestTemplate)
        this.bucController = BucController(mockEuxKlient, mockSafService, mockAktoerIdHelper, auditLogger)
    }


    @Test
    fun `gets valid bucs fagmodul can handle excpect list`() {
        val result = bucController.getBucs()
        Assertions.assertEquals(10, result.size)
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
                "application/pdf")).whenever(mockSafService).hentDokumentInnhold(any(), any(), any())


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

        val requestEntity = HttpEntity(body, headers)

        val queryUrl = UriComponentsBuilder
                .fromPath("/buc/")
                .path(rinasakid)
                .path("/sed/")
                .path(rinadocid)
                .path("/vedlegg")
                .queryParam("Filnavn", vedlegg.filnavn.replaceAfterLast(".", "").removeSuffix("."))
                .queryParam("Filtype", filtype)
                .queryParam("synkron", true)
                .build().toUriString()

        bucController.putVedleggTilDokument("123",
                rinasakid,
                rinadocid,
                "1",
                "2",
                VariantFormat.ARKIV )

        verify(mockEuxrestTemplate, times(1)).exchange( queryUrl , HttpMethod.POST, requestEntity, String::class.java)
    }

    @Test
    fun `gitt at det finnes en gydlig euxCaseid og Buc skal det returneres en liste over sedid`() {
        val gyldigBuc = String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-279020big.json")))

        val mockEuxRinaid = "123456"
        val mockResponse = ResponseEntity.ok().body(gyldigBuc)

        doReturn(mockResponse).whenever(mockEuxrestTemplate).exchange(any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))

        val actual = bucController.getAllDocuments(mockEuxRinaid)

        Assertions.assertNotNull(actual)
        Assertions.assertEquals(25, actual.size)
    }

    @Test
    fun `gitt at det finnes en gydlig euxCaseid og Buc, ved feil skal det prøves noen ganger også returneres en liste over sedid`() {
        val gyldigBuc = String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-279020big.json")))

        val mockEuxRinaid = "123456"
        val mockResponse = ResponseEntity.ok().body(gyldigBuc)

        doThrow(HttpClientErrorException(HttpStatus.UNAUTHORIZED, "This did not work 1"))
        .doThrow(HttpClientErrorException(HttpStatus.UNAUTHORIZED, "This did not work 2"))
                .doReturn(mockResponse)
        .whenever(mockEuxrestTemplate).exchange(any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))

        val actual = bucController.getAllDocuments(mockEuxRinaid)

        Assertions.assertNotNull(actual)
        Assertions.assertEquals(25, actual.size)
    }


    @Test
    fun `gitt at det finnes en gydlig euxCaseid og Buc, ved feil skal det prøves noen ganger så exception til slutt`() {
        val mockEuxRinaid = "123456"

        doThrow(HttpServerErrorException(HttpStatus.BAD_GATEWAY,"This did not work 1"))
                .doThrow(HttpServerErrorException(HttpStatus.BAD_GATEWAY,"This did not work 2"))
                .doThrow(HttpServerErrorException(HttpStatus.BAD_GATEWAY,"This did not work 3"))
                .whenever(mockEuxrestTemplate).exchange(any<String>(), eq(HttpMethod.GET), eq(null), eq(String::class.java))

        assertThrows<GenericUnprocessableEntity> {
            bucController.getAllDocuments(mockEuxRinaid)
        }
    }

    @Test
    fun `gitt at det finnes en gydlig euxCaseid skal det returneres en liste av Buc deltakere`() {
        val mockEuxRinaid = "123456"
        val mockResponse = ResponseEntity.ok().body(listOf(
           ParticipantsItem(organisation = Organisation(countryCode = "DK", id = "DK006")),
           ParticipantsItem(organisation = Organisation(countryCode = "PL", id = "PolishAcc"))
        ))
        doReturn(mockResponse).whenever(mockEuxrestTemplate).exchange(
                any<String>(),
                eq(HttpMethod.GET),
                eq(null),
                eq(typeRef<List<ParticipantsItem>>()))

        val responseJson = bucController.getBucDeltakere(mockEuxRinaid)
        val response = mapJsonToAny(responseJson, typeRefs<List<ParticipantsItem>>())
        Assertions.assertEquals(2, response.size)
    }
}

