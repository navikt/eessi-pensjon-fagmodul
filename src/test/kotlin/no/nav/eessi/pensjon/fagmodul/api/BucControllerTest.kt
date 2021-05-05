
package no.nav.eessi.pensjon.fagmodul.api

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
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
import no.nav.eessi.pensjon.fagmodul.prefill.klient.PrefillKlient
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentType
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjoninformasjonException
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonService
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.typeRefs
import no.nav.eessi.pensjon.vedlegg.VedleggService
import no.nav.pensjon.v1.avdod.V1Avdod
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.person.V1Person
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.server.ResponseStatusException


class BucControllerTest {

    @SpyK
    var auditLogger: AuditLogger = AuditLogger()

    @SpyK
    var mockEuxInnhentingService: EuxInnhentingService = EuxInnhentingService()

    @MockK
    lateinit var mockPensjonsinformasjonService: PensjonsinformasjonService

    @MockK
    lateinit var vedleggService: VedleggService

    @MockK
    lateinit var personService: PersonService

    @MockK
    lateinit var prefillKlient: PrefillKlient

    lateinit var bucController: BucController

    @BeforeEach
    fun before() {
        MockKAnnotations.init(this, relaxed = true, relaxUnitFun = true)
        val innhentingService = InnhentingService(personService, vedleggService, prefillKlient, mockPensjonsinformasjonService)
        innhentingService.initMetrics()

        bucController = BucController(
            "default",
            mockEuxInnhentingService,
            auditLogger,
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

        every { mockEuxInnhentingService.getBuc(any()) } returns buc
        val result = bucController.getBuc("1213123123")
        assertEquals(buc, result)
    }

    @Test
    fun `check for creator of current buc`() {
        val gyldigBuc = javaClass.getResource("/json/buc/buc-279020big.json").readText()
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())

        every { mockEuxInnhentingService.getBuc(any()) } returns buc

        val result = bucController.getCreator("1213123123")
        assertEquals(buc.creator, result)
    }


    @Test
    fun getProcessDefinitionName() {
        val gyldigBuc = javaClass.getResource("/json/buc/buc-279020big.json").readText()
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())
        every { mockEuxInnhentingService.getBuc(any()) } returns buc

        val result = bucController.getProcessDefinitionName("1213123123")
        assertEquals("P_BUC_03", result)
    }

    @Test
    fun getBucDeltakere() {
        val expected = listOf(ParticipantsItem("asdas", Organisation(), false))
        every { mockEuxInnhentingService.getBucDeltakere(any()) } returns expected

        val result = bucController.getBucDeltakere("1213123123")
        assertEquals(expected.toJson(), result)
    }

    @Test
    fun `gitt at det finnes en gydlig euxCaseid og Buc skal det returneres en liste over sedid`() {
        val gyldigBuc = javaClass.getResource("/json/buc/buc-279020big.json").readText()

        val mockEuxRinaid = "123456"
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())

        every { mockEuxInnhentingService.getBuc(any()) } returns buc


        val actual = bucController.getAllDocuments(mockEuxRinaid)

        Assertions.assertNotNull(actual)
        assertEquals(25, actual.size)
    }

    @Test
    fun `hent MuligeAksjoner på en buc`() {
        val gyldigBuc = javaClass.getResource("/json/buc/buc-279020big.json").readText()
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())

        every { mockEuxInnhentingService.getBuc(eq("279029")) } returns buc

        val actual = bucController.getMuligeAksjoner("279029")
        assertEquals(8, actual.size)
        assertTrue(actual.containsAll(listOf(SedType.H020, SedType.P10000, SedType.P6000)))
    }

    @Test
    fun `create BucSedAndView returns one valid element`() {
        val aktoerId = "123456789"
        val fnr = "10101835868"

        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(aktoerId)) } returns NorskIdent(fnr)

        val rinaSaker = listOf(Rinasak("1234","P_BUC_01", Traits(), "", Properties(), "open"))

        every { mockEuxInnhentingService.getRinasaker(fnr, aktoerId, emptyList())} returns rinaSaker
        every { mockEuxInnhentingService.getBuc(any()) } returns Buc()

        val actual = bucController.getBucogSedView(aktoerId)
        assertEquals(1,actual.size)
    }

    @Test
    fun `create BucSedAndView fails on rinasaker throw execption`() {
        val aktoerId = "123456789"
        val fnr = "10101835868"

        every {  personService.hentIdent(eq(IdentType.NorskIdent), eq(AktoerId(aktoerId)))} returns  NorskIdent(fnr)

        every { mockEuxInnhentingService.getRinasaker(fnr, aktoerId, emptyList()) } throws RuntimeException()

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

        every { personService.hentIdent(eq(IdentType.NorskIdent), eq(AktoerId(aktoerId))) } returns NorskIdent(fnr)

        every { mockEuxInnhentingService.getBuc(any()) } throws RuntimeException("Feiler ved BUC")

        val rinaSaker = listOf(Rinasak("1234","P_BUC_01", Traits(), "", Properties(), "open"))
        every { mockEuxInnhentingService.getRinasaker(fnr, aktoerId, emptyList()) } returns rinaSaker

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

        every { mockPensjonsinformasjonService.hentMedVedtak(vedtaksId) } returns mockPensjoninfo
        every { mockPensjonsinformasjonService.hentGyldigAvdod(any())} returns listOf(avdodfnr)
        every { personService.hentIdent(eq(IdentType.NorskIdent), eq(AktoerId(gjenlevendeAktoerid))) } returns NorskIdent(fnrGjenlevende)

        val documentsItem = listOf(DocumentsItem(type = SedType.P2100))
        val avdodView = listOf(BucAndSedView.from(Buc(id = "123", processDefinitionName = "P_BUC_02", documents = documentsItem), fnrGjenlevende, avdodfnr ))

        every { mockEuxInnhentingService.getBucAndSedViewAvdod(fnrGjenlevende, avdodfnr) } returns avdodView

        val rinaSaker = listOf(Rinasak(id = "123213", processDefinitionId = "P_BUC_03", status = "open"))
        every { mockEuxInnhentingService.getRinasaker(any(), any(), any()) } returns rinaSaker

        val documentsItemP2200 = listOf(DocumentsItem(type = SedType.P2200))
        val buc = Buc(id = "23321", processDefinitionName = "P_BUC_03", documents = documentsItemP2200)

        every { mockEuxInnhentingService.getBuc(any())} returns buc


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

        every { mockPensjonsinformasjonService.hentMedVedtak(vedtaksId) } returns mockPensjoninfo
        every { mockPensjonsinformasjonService.hentGyldigAvdod(any())} returns listOf(avdodFarfnr, avdodMorfnr)
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(aktoerId)) } returns NorskIdent(fnrGjenlevende)

        val rinaSaker = listOf<Rinasak>()

        every {  mockEuxInnhentingService.getRinasaker(any(), any(), any()) } returns rinaSaker
        val documentsItem1 = listOf(DocumentsItem(type = SedType.P2100))

        val buc1 = Buc(id = "123", processDefinitionName = "P_BUC_02", documents = documentsItem1)
        val avdodView1 = listOf(BucAndSedView.from(buc1, fnrGjenlevende, avdodMorfnr))

        val buc2 = Buc(id = "231", processDefinitionName = "P_BUC_02", documents = documentsItem1)
        val avdodView2 = listOf(BucAndSedView.from(buc2, fnrGjenlevende, avdodFarfnr))

        every { mockEuxInnhentingService.getBucAndSedViewAvdod(fnrGjenlevende, avdodMorfnr) } returns avdodView1
        every { mockEuxInnhentingService.getBucAndSedViewAvdod(fnrGjenlevende, avdodFarfnr) } returns avdodView2

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

        every { mockPensjonsinformasjonService.hentMedVedtak(vedtaksId) } returns mockPensjoninfo
        every { mockPensjonsinformasjonService.hentGyldigAvdod(any()) } returns null
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(aktoerId)) } returns NorskIdent(fnrGjenlevende)

        val rinaSaker = listOf<Rinasak>(Rinasak("1234","P_BUC_01", Traits(), "", Properties(), "open"))
        every { mockEuxInnhentingService.getRinasaker(fnrGjenlevende, aktoerId, emptyList()) } returns rinaSaker

        val documentsItem = listOf(DocumentsItem(type = SedType.P2000))
        val buc = Buc(processDefinitionName = "P_BUC_01", documents = documentsItem)

        every { mockEuxInnhentingService.getBuc(any()) } returns buc

        val actual = bucController.getBucogSedViewVedtak(aktoerId, vedtaksId)
        assertEquals(1, actual.size)
        assertEquals("P_BUC_01", actual.first().type)

    }

    @Test
    fun `Gitt en gjenlevende med feil på vedtak Når BUC og SED forsøkes å hentes Så kastes det en Exception`() {
        val aktoerId = "1234568"
        val vedtaksId = "22455454"

        every { mockPensjonsinformasjonService.hentMedVedtak(vedtaksId) } throws PensjoninformasjonException("Error, Error")
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
        every { personService.hentIdent(IdentType.NorskIdent, AktoerId(aktoerId)) } returns NorskIdent(fnrGjenlevende)
        every { mockEuxInnhentingService.getBucAndSedViewAvdod(fnrGjenlevende, avdodfnr)} throws HttpClientErrorException(HttpStatus.BAD_REQUEST)

        assertThrows<Exception> {
            bucController.getBucogSedViewGjenlevende(aktoerId, avdodfnr)
        }
    }

}

