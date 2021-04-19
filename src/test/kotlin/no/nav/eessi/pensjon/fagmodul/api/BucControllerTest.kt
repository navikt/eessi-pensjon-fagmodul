
package no.nav.eessi.pensjon.fagmodul.api

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedView
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Properties
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Rinasak
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Traits
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Organisation
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.fagmodul.prefill.PersonDataService
import no.nav.eessi.pensjon.fagmodul.prefill.PrefillService
import no.nav.eessi.pensjon.fagmodul.prefill.klient.PrefillKlient
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentType
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjoninformasjonException
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.typeRefs
import no.nav.eessi.pensjon.vedlegg.VedleggService
import no.nav.pensjon.v1.avdod.V1Avdod
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.person.V1Person
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.server.ResponseStatusException

@ExtendWith(MockitoExtension::class)
class BucControllerTest {

    @Spy
    lateinit var auditLogger: AuditLogger

    @Spy
    lateinit var mockEuxInnhentingService: EuxInnhentingService

    @Mock
    lateinit var mockPensjonsinformasjonService: PensjonsinformasjonService

    @Mock
    private lateinit var personDataService: PersonDataService

    @Mock
    lateinit var prefillService: PrefillService

    @Mock
    lateinit var vedleggService: VedleggService

    @Mock
    lateinit var prefillKlient: PrefillKlient

    private lateinit var bucController: BucController

    @BeforeEach
    fun before() {

        val innhentingService = InnhentingService(personDataService, vedleggService, prefillKlient)
        innhentingService.initMetrics()

        bucController = BucController(
            "default",
            mockEuxInnhentingService,
            auditLogger,
            mockPensjonsinformasjonService,
            innhentingService
        )
        bucController.initMetrics()
    }


    @Test
    fun `gets valid bucs fagmodul can handle excpect list`() {
        val result = bucController.getBucs()
        assertEquals(10, result.size)
    }

    @Test
    fun `get valud buc json and convert to object ok`() {
        val gyldigBuc = javaClass.getResource("/json/buc/buc-279020big.json").readText()
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())

        doReturn(buc).whenever(mockEuxInnhentingService).getBuc(any())

        val result = bucController.getBuc("1213123123")
        assertEquals(buc, result)
    }

    @Test
    fun `check for creator of current buc`() {
        val gyldigBuc = javaClass.getResource("/json/buc/buc-279020big.json").readText()
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())

        doReturn(buc).whenever(mockEuxInnhentingService).getBuc(any())

        val result = bucController.getCreator("1213123123")
        assertEquals(buc.creator, result)
    }


    @Test
    fun getProcessDefinitionName() {
        val gyldigBuc = javaClass.getResource("/json/buc/buc-279020big.json").readText()
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())
        doReturn(buc).whenever(mockEuxInnhentingService).getBuc(any())

        val result = bucController.getProcessDefinitionName("1213123123")
        assertEquals("P_BUC_03", result)
    }

    @Test
    fun getBucDeltakere() {
        val expected = listOf(ParticipantsItem("asdas", Organisation(), false))
        doReturn(expected).whenever(mockEuxInnhentingService).getBucDeltakere(any())

        val result = bucController.getBucDeltakere("1213123123")
        assertEquals(expected.toJson(), result)
    }

    @Test
    fun `gitt at det finnes en gydlig euxCaseid og Buc skal det returneres en liste over sedid`() {
        val gyldigBuc = javaClass.getResource("/json/buc/buc-279020big.json").readText()

        val mockEuxRinaid = "123456"
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())

        doReturn(buc).whenever(mockEuxInnhentingService).getBuc(any())

        val actual = bucController.getAllDocuments(mockEuxRinaid)

        Assertions.assertNotNull(actual)
        assertEquals(25, actual.size)
    }

    @Test
    fun `hent MuligeAksjoner på en buc`() {
        val gyldigBuc = javaClass.getResource("/json/buc/buc-279020big.json").readText()
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())

        doReturn(buc).whenever(mockEuxInnhentingService).getBuc("279029")

        val actual = bucController.getMuligeAksjoner("279029")
        assertEquals(8, actual.size)
        assertTrue(actual.containsAll(listOf(SedType.H020, SedType.P10000, SedType.P6000)))
    }

    @Test
    fun `create BucSedAndView returns one valid element`() {
        val aktoerId = "123456789"
        val fnr = "10101835868"

        doReturn(fnr).whenever(personDataService).hentFnrfraAktoerService(any())

        val rinaSaker = listOf(Rinasak("1234","P_BUC_01", Traits(), "", Properties(), "open"))
        doReturn(rinaSaker).whenever(mockEuxInnhentingService).getRinasaker(fnr, aktoerId, emptyList())
        doReturn(Buc()).whenever(mockEuxInnhentingService).getBuc(any())

        val actual = bucController.getBucogSedView(aktoerId)
        assertEquals(1,actual.size)
    }

    @Test
    fun `create BucSedAndView fails on rinasaker throw execption`() {
        val aktoerId = "123456789"
        val fnr = "10101835868"

        doReturn(NorskIdent(fnr)).whenever(personDataService).hentIdent(IdentType.NorskIdent, AktoerId(aktoerId))
        doThrow(RuntimeException::class).whenever(mockEuxInnhentingService).getRinasaker(fnr, aktoerId, emptyList())

        assertThrows<ResponseStatusException> {
            bucController.getBucogSedView(aktoerId)
        }
        try {
            bucController.getBucogSedView(aktoerId)
            fail("skal ikke komme hit")
        } catch (ex: Exception) {
            assertEquals("500 INTERNAL_SERVER_ERROR \"Feil ved henting av rinasaker på borger\"", ex.message)
        }

    }

    @Test
    fun `create BucSedAndView fails on the view and entity with error`() {
        val aktoerId = "123456789"
        val fnr = "10101835868"

        doReturn(fnr).whenever(personDataService).hentFnrfraAktoerService(any())

        doThrow(RuntimeException("Feiler ved BUC")).whenever(mockEuxInnhentingService).getBuc(any())

        val rinaSaker = listOf(Rinasak("1234","P_BUC_01", Traits(), "", Properties(), "open"))
        doReturn(rinaSaker).whenever(mockEuxInnhentingService).getRinasaker(fnr, aktoerId, emptyList())

        val actual =  bucController.getBucogSedView(aktoerId)
        assertTrue(actual.first().toJson().contains("Feiler ved BUC"))

    }

    @Test
    fun `Gitt en gjenlevende med vedtak som inneholder avdød Når BUC og SED forsøkes å hentes Så returner alle SED og BUC tilhørende gjenlevende`() {
        val gjenlevendeAktoerid = "1234568"
        val vedtaksId = "22455454"
        val fnrGjenlevende = "13057065487"
        val avdodfnr = "12312312312"

        // pensjonsinformasjonsKLient
        val mockPensjoninfo = Pensjonsinformasjon()
        mockPensjoninfo.avdod = V1Avdod()
        mockPensjoninfo.person = V1Person()
        mockPensjoninfo.avdod.avdod = avdodfnr
        mockPensjoninfo.person.aktorId = gjenlevendeAktoerid

        doReturn(mockPensjoninfo).whenever(mockPensjonsinformasjonService).hentMedVedtak(vedtaksId)
        doReturn(listOf(avdodfnr)).whenever(mockPensjonsinformasjonService).hentGyldigAvdod(any())

        doReturn(fnrGjenlevende).whenever(personDataService).hentFnrfraAktoerService(any())

        val documentsItem = listOf(DocumentsItem(type = SedType.P2100))
        val avdodView = listOf(BucAndSedView.from(Buc(id = "123", processDefinitionName = "P_BUC_02", documents = documentsItem), fnrGjenlevende, avdodfnr ))
        doReturn(avdodView).whenever(mockEuxInnhentingService).getBucAndSedViewAvdod(fnrGjenlevende, avdodfnr)

        val rinaSaker = listOf(Rinasak(id = "123213", processDefinitionId = "P_BUC_03", status = "open"))
        doReturn(rinaSaker).whenever(mockEuxInnhentingService).getRinasaker(any(), any(), any())

        val documentsItemP2200 = listOf(DocumentsItem(type = SedType.P2200))
        val buc = Buc(id = "23321", processDefinitionName = "P_BUC_03", documents = documentsItemP2200)
        doReturn(buc).whenever(mockEuxInnhentingService).getBuc(any())


        val actual = bucController.getBucogSedViewVedtak(gjenlevendeAktoerid, vedtaksId)
        assertEquals(2, actual.size)
        assertTrue(actual.contains( avdodView.first() ))
    }

    @Test
    fun `Gitt en gjenlevende med vedtak som inneholder avdodfar og avdodmor Når BUC og SED forsøkes å hentes Så returner alle SED og BUC tilhørende gjenlevende`() {
        val aktoerId = "1234568"
        val vedtaksId = "22455454"
        val fnrGjenlevende = "13057065487"
        val avdodMorfnr = "310233213123"
        val avdodFarfnr = "101020223123"

        // pensjonsinformasjonsKLient
        val mockPensjoninfo = Pensjonsinformasjon()
        mockPensjoninfo.avdod = V1Avdod()
        mockPensjoninfo.person = V1Person()
        mockPensjoninfo.avdod.avdodMor = avdodMorfnr
        mockPensjoninfo.avdod.avdodFar = avdodFarfnr
        mockPensjoninfo.person.aktorId = aktoerId

        doReturn(mockPensjoninfo).`when`(mockPensjonsinformasjonService).hentMedVedtak(vedtaksId)
        doReturn(listOf(avdodFarfnr, avdodMorfnr)).whenever(mockPensjonsinformasjonService).hentGyldigAvdod(any())

        doReturn(fnrGjenlevende).whenever(personDataService).hentFnrfraAktoerService(any())

        val rinaSaker = listOf<Rinasak>()
        doReturn(rinaSaker).whenever(mockEuxInnhentingService).getRinasaker(any(), any(), any())

        val documentsItem1 = listOf(DocumentsItem(type = SedType.P2100))

        val buc1 = Buc(id = "123", processDefinitionName = "P_BUC_02", documents = documentsItem1)
        val avdodView1 = listOf(BucAndSedView.from(buc1, fnrGjenlevende, avdodMorfnr))

        val buc2 = Buc(id = "231", processDefinitionName = "P_BUC_02", documents = documentsItem1)
        val avdodView2 = listOf(BucAndSedView.from(buc2, fnrGjenlevende, avdodFarfnr))

        doReturn(avdodView1).`when`(mockEuxInnhentingService).getBucAndSedViewAvdod(fnrGjenlevende, avdodMorfnr)
        doReturn(avdodView2).`when`(mockEuxInnhentingService).getBucAndSedViewAvdod(fnrGjenlevende, avdodFarfnr)

        val actual = bucController.getBucogSedViewVedtak(aktoerId, vedtaksId)
        assertEquals(2, actual.size)
        assertEquals("P_BUC_02", actual.first().type)
        assertEquals("P_BUC_02", actual.last().type)
        assertEquals("231", actual.first().caseId)
        assertEquals("123", actual.last().caseId)
        assertEquals(avdodMorfnr, actual.last().subject?.avdod?.fnr)
        assertEquals(fnrGjenlevende, actual.last().subject?.gjenlevende?.fnr)

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

        doReturn(mockPensjoninfo).whenever(mockPensjonsinformasjonService).hentMedVedtak(vedtaksId)
        doReturn(null).whenever(mockPensjonsinformasjonService).hentGyldigAvdod(any())

        doReturn(fnrGjenlevende).whenever(personDataService).hentFnrfraAktoerService(any())

        val rinaSaker = listOf<Rinasak>(Rinasak("1234","P_BUC_01", Traits(), "", Properties(), "open"))
        doReturn(rinaSaker).whenever(mockEuxInnhentingService).getRinasaker(fnrGjenlevende, aktoerId, emptyList())

        val documentsItem = listOf(DocumentsItem(type = SedType.P2000))
        val buc = Buc(processDefinitionName = "P_BUC_01", documents = documentsItem)

        doReturn(buc).whenever(mockEuxInnhentingService).getBuc(any())

        val actual = bucController.getBucogSedViewVedtak(aktoerId, vedtaksId)
        assertEquals(1, actual.size)
        assertEquals("P_BUC_01", actual.first().type)

    }

    @Test
    fun `Gitt en gjenlevende med feil på vedtak Når BUC og SED forsøkes å hentes Så kastes det en Exception`() {
        val aktoerId = "1234568"
        val vedtaksId = "22455454"

        doThrow(PensjoninformasjonException("Error, Error")).whenever(mockPensjonsinformasjonService).hentMedVedtak(vedtaksId)

        assertThrows<PensjoninformasjonException> {
            bucController.getBucogSedViewVedtak(aktoerId, vedtaksId)
        }
    }

    @Test
    fun `Gitt en gjenlevende med avdodfnr Når BUC og SED forsøkes å hentes kastes det en Exceptiopn ved getBucAndSedViewAvdod`() {
        val aktoerId = "1234568"
        val fnrGjenlevende = "13057065487"
        val avdodfnr = "12312312312312312312312"

        //aktoerService.hentPinForAktoer
        doReturn(NorskIdent(fnrGjenlevende)).whenever(personDataService).hentIdent(IdentType.NorskIdent, AktoerId(aktoerId))
        doThrow(HttpClientErrorException::class).whenever(mockEuxInnhentingService).getBucAndSedViewAvdod(fnrGjenlevende, avdodfnr)

        assertThrows<Exception> {
            bucController.getBucogSedViewGjenlevende(aktoerId, avdodfnr)
        }
    }

}

