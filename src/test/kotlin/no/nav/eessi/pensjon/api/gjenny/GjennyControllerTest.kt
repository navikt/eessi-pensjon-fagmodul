package no.nav.eessi.pensjon.api.gjenny

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.eux.klient.Rinasak
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_02
import no.nav.eessi.pensjon.fagmodul.api.PrefillController
import no.nav.eessi.pensjon.fagmodul.api.SedController
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService.BucViewKilde.AVDOD
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endring
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Folkeregistermetadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.ForelderBarnRelasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kjoenn
import no.nav.eessi.pensjon.personoppslag.pdl.model.KjoennType
import no.nav.eessi.pensjon.personoppslag.pdl.model.Navn
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.PdlPerson
import no.nav.eessi.pensjon.personoppslag.pdl.model.Sivilstand
import no.nav.eessi.pensjon.personoppslag.pdl.model.Statsborgerskap
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata as PDLMetaData

private const val AKTOERID = "12345678900"
private const val AKTOERID_LEV = "12345678501"
private const val AVDOD_FNR = "12345678800"
private const val GJENLEV_FNR = "12345678503"

@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@ComponentScan(basePackages = ["no.nav.eessi.pensjon.api.gjenny"])
@WebMvcTest(GjennyController::class)
@MockkBeans(
    MockkBean(name = "sedController", classes = [SedController::class], relaxed = true),
    MockkBean(name = "kodeverkClient", classes = [KodeverkClient::class], relaxed = true),
    MockkBean(name = "euxKlient", classes = [EuxKlientAsSystemUser::class], relaxed = true),
    MockkBean(name = "euxNavIdentRestTemplateV2", classes = [RestTemplate::class]),
    MockkBean(name = "gcpStorageService", classes = [GcpStorageService::class], relaxed = true),
    MockkBean(name = "prefillController", classes = [PrefillController::class], relaxed = true)
)
class GjennyControllerTest {

    @SpykBean
    private lateinit var euxInnhentingService: EuxInnhentingService

    @MockkBean
    private lateinit var innhentingService: InnhentingService

    @MockkBean
    private lateinit var personService: PersonService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `returnerer bucer for avdød`() {
        val euxCaseId = "123456"
        val endpointUrl = "/gjenny/rinasaker/$AKTOERID/avdodfnr/$AVDOD_FNR"
        val listeOverBucerForAvdod = listOf(
            Rinasak(euxCaseId, P_BUC_02.name),
            Rinasak(euxCaseId, P_BUC_01.name),
        )
        val pdlPerson = lagPerson(AVDOD_FNR)
        every { personService.hentPerson(NorskIdent(AVDOD_FNR))} returns pdlPerson
        every { innhentingService.hentFnrfraAktoerService(AKTOERID)} returns NorskIdent(AVDOD_FNR)
        every { euxInnhentingService.hentBucViewGjenlevende(any()) } returns listeOverBucerForAvdod
        every { innhentingService.hentRinaSakIderFraJoarksMetadataForOmstilling(any()) } returns listOf("123456", "12345678901")

        val expected = """
           "[{\"euxCaseId\":\"$euxCaseId\",\"buctype\":\"P_BUC_02\",\"aktoerId\":\"$AKTOERID\",\"saknr\":null,\"avdodFnr\":\"$AVDOD_FNR\",\"kilde\":\"SAF\"}]"
        """.trimIndent()

        val result = mockMvc.get(endpointUrl).andReturn().response.contentAsString.toJson()
        assertEquals(expected, result)
    }

    @Test
    fun `Ved innhenting av bucer for gjennybrukere returneres alle bucer utenom pbuc01 og pbuc03 for avdod og gjenlevende`() {
        val endpointUrl = "/gjenny/rinasaker/$AKTOERID/avdodfnr/$AVDOD_FNR"
        val euxCaseId = "123456"
        val listeOverBucerForAvdod = listOf(
            Rinasak(euxCaseId, P_BUC_02.name),
            Rinasak(euxCaseId, P_BUC_01.name),
        )

        val pdlPerson = lagPerson(AVDOD_FNR)
        every { personService.hentPerson(NorskIdent(AVDOD_FNR))} returns pdlPerson
        every { innhentingService.hentFnrfraAktoerService(AKTOERID)} returns NorskIdent(AVDOD_FNR)
        every { euxInnhentingService.hentBucViewGjenlevende(any()) } returns listeOverBucerForAvdod
        every { innhentingService.hentRinaSakIderFraJoarksMetadataForOmstilling(any()) } returns listOf("123456", "1234567")

        val expected = """
           "[{\"euxCaseId\":\"123456\",\"buctype\":\"P_BUC_02\",\"aktoerId\":\"$AKTOERID\",\"saknr\":null,\"avdodFnr\":\"$AVDOD_FNR\",\"kilde\":\"SAF\"}]"
        """.trimIndent()

        val result = mockMvc.get(endpointUrl).andReturn().response.contentAsString.toJson()
        assertEquals(expected, result)
    }

    @Test
    fun `Ved innhenting av bucer for gjenny bruker og av dod returneres kun bucer som de to har i felles`() {
        val endpointUrl = "/gjenny/rinasaker/$AKTOERID_LEV/avdodfnr/$AVDOD_FNR"
        val euxCaseId = "123456"
        val listeOverBucerForAvdod = listOf(
            Rinasak(euxCaseId, P_BUC_02.name),
            Rinasak("321654", P_BUC_02.name),
        )

        val listeOverBucerForGjenlev = listOf(
            Rinasak(euxCaseId, P_BUC_02.name),
        )

        val pdlPersonAvdod = lagPerson(AVDOD_FNR)

        every { personService.hentPerson(NorskIdent(AVDOD_FNR))} returns pdlPersonAvdod
        every { innhentingService.hentFnrfraAktoerService(AKTOERID_LEV)} returns NorskIdent(GJENLEV_FNR)
        every { euxInnhentingService.hentBucViewGjenlevende(eq(AVDOD_FNR)) } returns listeOverBucerForAvdod
        every { euxInnhentingService.hentBucViewGjenlevende(eq(GJENLEV_FNR)) } returns listeOverBucerForGjenlev
        every { innhentingService.hentRinaSakIderFraJoarksMetadataForOmstilling(eq(AVDOD_FNR)) } returns listOf("123456", "321654")
        every { innhentingService.hentRinaSakIderFraJoarksMetadataForOmstilling(eq(AKTOERID_LEV)) } returns listOf("123456")

        val expected = """
           "[{\"euxCaseId\":\"123456\",\"buctype\":\"P_BUC_02\",\"aktoerId\":\"$AKTOERID_LEV\",\"saknr\":null,\"avdodFnr\":\"$AVDOD_FNR\",\"kilde\":\"SAF\"}]"
        """.trimIndent()

        val result = mockMvc.get(endpointUrl).andReturn().response.contentAsString.toJson()
        assertEquals(expected, result)
    }

    @Test
    fun `Ved innhenting av bucer for gjennybrukere returneres alle bucer utenom pbuc01 og pbuc03 `() {
        val endpointUrl = "/gjenny/rinasaker/$AKTOERID"
        val euxCaseId = "123456"
        val listeOverBucerForAvdod = listOf(
            bucviews(euxCaseId),
            bucviews(euxCaseId, P_BUC_01)
        )

        val listeOverBucViews = listOf(
            bucviews("123456", P_BUC_02),
            bucviews("1234567", P_BUC_01),
        )

        every { innhentingService.hentFnrfraAktoerService(any()) } returns NorskIdent(AVDOD_FNR)
        every { innhentingService.hentRinaSakIderFraJoarksMetadata(any()) } returns listOf("123456", "1234567")
        every { euxInnhentingService.hentBucViewBruker(any(), any(), null) } returns listeOverBucViews
        every { euxInnhentingService.lagBucViews(any(), any(), any(), any()) } returns listeOverBucerForAvdod

        val expected = """
           "[{\"euxCaseId\":\"123456\",\"buctype\":\"P_BUC_02\",\"aktoerId\":\"$AKTOERID\",\"saknr\":null,\"avdodFnr\":\"$AVDOD_FNR\",\"kilde\":\"AVDOD\"}]"
        """.trimIndent()

        val result = mockMvc.get(endpointUrl).andReturn().response.contentAsString.toJson()
        assertEquals(expected, result)
    }

    private fun bucviews(rinasakId: String, bucType: BucType = P_BUC_02): EuxInnhentingService.BucView =
        EuxInnhentingService.BucView(rinasakId, bucType, AKTOERID, null, AVDOD_FNR, AVDOD)

    @Test
    fun `getRinasakerBrukerkontekstGjenny burde gi en OK og en tom liste`() {
        every { innhentingService.hentFnrfraAktoerService(any()) } returns null
        every { innhentingService.hentRinaSakIderFraJoarksMetadata(any()) } returns listOf("12345")
        every { euxInnhentingService.lagBucViews(any(), any(), any(), any()) } returns emptyList()

        val result = mockMvc.get("/gjenny/rinasaker/123456")
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
            }
            .andReturn()

        val responseContent = result.response.contentAsString
        val bucViews: List<EuxInnhentingService.BucView> = ObjectMapper().readValue(responseContent)

        assertTrue(bucViews.isEmpty(), "Expected an empty list in the response")
    }

    @Test
    fun `getbucs burde gi en liste av godkjente bucs `() {
        mockMvc.get("/gjenny/bucs")
            .andExpect {
                status { isOk() }
                content { string("[\"P_BUC_02\",\"P_BUC_04\",\"P_BUC_05\",\"P_BUC_06\",\"P_BUC_07\",\"P_BUC_08\",\"P_BUC_09\",\"P_BUC_10\"]") }
            }
    }

    fun lagPerson(
        fnr: String = AVDOD_FNR ,
        fornavn: String = "Fornavn",
        etternavn: String = "Etternavn",
        familierlasjon: List<ForelderBarnRelasjon> = emptyList(),
        sivilstand: List<Sivilstand> = emptyList()
    ) = PdlPerson(
        listOf(IdentInformasjon(fnr, IdentGruppe.AKTORID)),
        Navn(fornavn, null,  etternavn, null, null, null, mockMeta()),
        emptyList(),
        null,
        null,
        listOf(
            Statsborgerskap(
                "NOR",
                LocalDate.of(2010, 10, 11),
                LocalDate.of(2020, 10, 2),
                mockMeta()
            )
        ),
        null,
        null,
        null,
        Kjoenn(
            KjoennType.MANN,
            Folkeregistermetadata(LocalDateTime.of(2000, 10, 1, 12, 10, 31)),
            mockMeta()
        ),
        null,
        familierlasjon,
        sivilstand,
        null,
        null,
        emptyList()
    )

    private fun mockMeta() : PDLMetaData {
        return PDLMetaData(
            listOf(Endring(
                "DOLLY",
                LocalDateTime.of(2010, 4, 1, 10, 12, 3),
                "Dolly",
                "FREG",
                Endringstype.OPPRETT
            )),
            false,
            "FREG",
            "fdsa234-sdfsf234-sfsdf234"
        )
    }
}


