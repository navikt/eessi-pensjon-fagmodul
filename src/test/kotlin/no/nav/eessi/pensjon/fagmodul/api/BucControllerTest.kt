package no.nav.eessi.pensjon.fagmodul.api

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedView
import no.nav.eessi.pensjon.fagmodul.eux.EuxService
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Properties
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Rinasak
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Traits
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Organisation
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonClient
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.typeRefs
import no.nav.pensjon.v1.avdod.V1Avdod
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.person.V1Person
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(MockitoExtension::class)
class BucControllerTest {

    @Spy
    lateinit var auditLogger: AuditLogger

    @Spy
    lateinit var mockEuxService: EuxService

    @Mock
    lateinit var mockAktoerIdHelper: AktoerregisterService

    @Mock
    lateinit var mockPensjonClient: PensjonsinformasjonClient

    private lateinit var bucController: BucController

    @BeforeEach
    fun before() {
        this.bucController = BucController(mockEuxService, mockAktoerIdHelper, auditLogger, mockPensjonClient)
    }


    @Test
    fun `gets valid bucs fagmodul can handle excpect list`() {
        val result = bucController.getBucs()
        Assertions.assertEquals(10, result.size)
    }

    @Test
    fun `get valud buc json and convert to object ok`() {
        val gyldigBuc = String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-279020big.json")))
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())

        doReturn(buc).whenever(mockEuxService).getBuc(any())

        val result = bucController.getBuc("1213123123")
        assertEquals(buc, result)
    }

    @Test
    fun getProcessDefinitionName() {
        val gyldigBuc = String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-279020big.json")))
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())
        doReturn(buc).whenever(mockEuxService).getBuc(any())

        val result = bucController.getProcessDefinitionName("1213123123")
        assertEquals("P_BUC_03", result)
    }

    @Test
    fun getCreator() {
        val gyldigBuc = String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-279020big.json")))
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())
        doReturn(buc).whenever(mockEuxService).getBuc(any())

        val result = bucController.getCreator("1213123123")
        assertEquals("Z990787", result?.name)
    }

    @Test
    fun getBucDeltakere() {
        val expected = listOf(ParticipantsItem("asdas", Organisation(), false))
        doReturn(expected).whenever(mockEuxService).getBucDeltakere(any())

        val result = bucController.getBucDeltakere("1213123123")
        assertEquals(expected.toJson(), result)
    }

    @Test
    fun `gitt at det finnes en gydlig euxCaseid og Buc skal det returneres en liste over sedid`() {
        val gyldigBuc = String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-279020big.json")))

        val mockEuxRinaid = "123456"
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())

        doReturn(buc).whenever(mockEuxService).getBuc(any())

        val actual = bucController.getAllDocuments(mockEuxRinaid)

        Assertions.assertNotNull(actual)
        assertEquals(25, actual.size)
    }

    @Test
    fun `createBuc run ok and return id`() {
        val gyldigBuc = String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-279020big.json")))
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())

        doReturn("1231231").whenever(mockEuxService).createBuc("P_BUC_03")
        doReturn(buc).whenever(mockEuxService).getBuc(any())

        val excpeted = BucAndSedView.from(buc)
        val actual = bucController.createBuc("P_BUC_03")

        assertEquals(excpeted.toJson(), actual.toJson())
    }

    @Test
    fun `create BucSedAndView returns one valid element`() {
        val aktoerId = "123456789"
        val fnr = "10101835868"

        doReturn(fnr).whenever(mockAktoerIdHelper).hentPinForAktoer(aktoerId)

        val rinaSaker = listOf<Rinasak>(Rinasak("1234","P_BUC_01", Traits(), "", Properties(), "open"))
        doReturn(rinaSaker).whenever(mockEuxService).getRinasaker(fnr, aktoerId)

        doReturn(Buc()).whenever(mockEuxService).getBuc(any())

        val actual = bucController.getBucogSedView(aktoerId)
        assertEquals(1,actual.size)
    }

    @Test
    fun `create BucSedAndView fails on rinasaker throw execption`() {
        val aktoerId = "123456789"
        val fnr = "10101835868"

        doReturn(fnr).whenever(mockAktoerIdHelper).hentPinForAktoer(aktoerId)
        doThrow(RuntimeException::class).whenever(mockEuxService).getRinasaker(fnr, aktoerId)

        assertThrows<Exception> {
            bucController.getBucogSedView(aktoerId)
        }
        try {
            bucController.getBucogSedView(aktoerId)
            fail("skal ikke komme hit")
        } catch (ex: Exception) {
            assertEquals("Feil ved henting av rinasaker på borger", ex.message)
        }

    }

    @Test
    fun `create BucSedAndView fails on the view and entity with error`() {
        val aktoerId = "123456789"
        val fnr = "10101835868"

        doReturn(fnr).whenever(mockAktoerIdHelper).hentPinForAktoer(aktoerId)
        doThrow(RuntimeException("Feiler ved BUC")).whenever(mockEuxService).getBuc(any())

        val rinaSaker = listOf<Rinasak>(Rinasak("1234","P_BUC_01", Traits(), "", Properties(), "open"))
        doReturn(rinaSaker).whenever(mockEuxService).getRinasaker(fnr, aktoerId)

        val actual =  bucController.getBucogSedView(aktoerId)
        Assertions.assertTrue(actual.first().toJson().contains("Feiler ved BUC"))

    }

    @Test
    fun `Gitt en gjenlevende med vedtak som inneholder avdød Når BUC og SED forsøkes å hentes Så returner alle SED og BUC tilhørende gjenlevende`() {
        val aktoerId = "1234568"
        val vedtaksId = "22455454"
        val fnrGjenlevende = "13057065487"
        val avdodfnr = "12312312312312312312312"

        // pensjonsinformasjonsKLient
        val mockPensjoninfo = Pensjonsinformasjon()
        mockPensjoninfo.avdod = V1Avdod()
        mockPensjoninfo.person = V1Person()
        mockPensjoninfo.avdod.avdod = avdodfnr
        mockPensjoninfo.person.aktorId = aktoerId

        doReturn(mockPensjoninfo).whenever(mockPensjonClient).hentAltPaaVedtak(vedtaksId)

        //aktoerService.hentPinForAktoer
        doReturn(fnrGjenlevende).whenever(mockAktoerIdHelper).hentPinForAktoer(aktoerId)

        //euxService.getrinasakeravdod
        val rinaSaker = listOf("123422")
        doReturn(rinaSaker).whenever(mockEuxService).getRinasakerAvdod(avdodfnr, aktoerId, fnrGjenlevende)

        val documentsItem = listOf(DocumentsItem(type = "P2100"))
        val buc = Buc(processDefinitionName = "P_BUC_02", documents = documentsItem)


        doReturn(buc).whenever(mockEuxService).getBuc(any())

        //euxService.getBucAndSedVew()
        val actual = bucController.getBucogSedViewVedtak(aktoerId, vedtaksId)
        assertEquals(1, actual.size)
        assertEquals("P_BUC_02", actual.first().type)
    }

    @Test
    fun `Gitt en gjenlevende med vedtak uten avdød Når BUC og SED forsøkes å hentes Så returner alle SED og BUC tilhørende gjenlevende uten P_BUC_02`() {
        val aktoerId = "1234568"
        val vedtaksId = "22455454"
        val fnrGjenlevende = "13057065487"

        // pensjonsinformasjonsKLient
        val mockPensjoninfo = Pensjonsinformasjon()
        mockPensjoninfo.person = V1Person()
        mockPensjoninfo.person.aktorId = aktoerId

        doReturn(mockPensjoninfo).whenever(mockPensjonClient).hentAltPaaVedtak(vedtaksId)

        //aktoerService.hentPinForAktoer
        doReturn(fnrGjenlevende).whenever(mockAktoerIdHelper).hentPinForAktoer(aktoerId)

        //euxService.getrinasakeravdod
        val rinaSaker = listOf<Rinasak>(Rinasak("1234","P_BUC_01", Traits(), "", Properties(), "open"))
        doReturn(rinaSaker).whenever(mockEuxService).getRinasaker(fnrGjenlevende, aktoerId)

        val documentsItem = listOf(DocumentsItem(type = "P2000"))
        val buc = Buc(processDefinitionName = "P_BUC_01", documents = documentsItem)

        doReturn(buc).whenever(mockEuxService).getBuc(any())

        //euxService.getBucAndSedVew()
        val actual = bucController.getBucogSedViewVedtak(aktoerId, vedtaksId)
        assertEquals(1, actual.size)
        assertEquals("P_BUC_01", actual.first().type)
    }



}