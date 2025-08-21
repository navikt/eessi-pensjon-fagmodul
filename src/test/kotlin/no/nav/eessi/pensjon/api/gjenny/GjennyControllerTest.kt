package no.nav.eessi.pensjon.api.gjenny

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_02
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.api.PrefillController
import no.nav.eessi.pensjon.fagmodul.api.SedController
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService.BucViewKilde.AVDOD
import no.nav.eessi.pensjon.fagmodul.eux.EuxPrefillService
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.shared.api.ApiRequest
import no.nav.eessi.pensjon.utils.mapJsonToAny
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
import org.springframework.test.web.servlet.post

@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@ComponentScan(basePackages = ["no.nav.eessi.pensjon.api.gjenny"])
@WebMvcTest(GjennyController::class)
@MockkBeans(
    MockkBean(name = "sedController", classes = [SedController::class], relaxed = true),
    MockkBean(name = "kodeverkClient", classes = [KodeverkClient::class], relaxed = true),
    MockkBean(name = "euxKlient", classes = [EuxKlientAsSystemUser::class], relaxed = true),
    MockkBean(name = "gcpStorageService", classes = [GcpStorageService::class], relaxed = true),
    MockkBean(name = "prefillController", classes = [PrefillController::class], relaxed = true)
)
class GjennyControllerTest {

    @MockkBean
    private lateinit var euxPrefillService: EuxPrefillService

    @SpykBean
    private lateinit var euxInnhentingService: EuxInnhentingService

    @MockkBean
    private lateinit var innhentingService: InnhentingService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `returnerer bucer for avdød`() {
        val endpointUrl = "/gjenny/rinasaker/$AKTOERID/avdodfnr/$AVDOD_FNR"
        val listeOverBucerForAvdod = listOf(EuxInnhentingService.BucView(AKTOERID, P_BUC_02, AVDOD_FNR, AVDOD_FNR, AVDOD_FNR, AVDOD))

        every { euxInnhentingService.hentBucViewAvdod(any(), any()) } returns listeOverBucerForAvdod
        every { innhentingService.hentRinaSakIderFraJoarksMetadataForOmstilling(any()) } returns listOf("123456", "1234567")
        every { euxInnhentingService.lagBucViews(any(), any(), any(), any()) } returns listeOverBucerForAvdod

        val expected = """
           "[{\"euxCaseId\":\"12345678901\",\"buctype\":\"P_BUC_02\",\"aktoerId\":\"12345678900\",\"saknr\":\"12345678900\",\"avdodFnr\":\"12345678900\",\"kilde\":\"AVDOD\"}]"
        """.trimIndent()

        val result = mockMvc.get(endpointUrl).andReturn().response.contentAsString.toJson()
        assertEquals(expected, result)
    }

    @Test
    fun `Ved innhenting av bucer for gjennybrukere returneres alle bucer utenom pbuc01 og pbuc03 for avdod og gjenlevende`() {
        val endpointUrl = "/gjenny/rinasaker/$AKTOERID/avdodfnr/$AVDOD_FNR"
        val euxCaseId = "123456"
        val listeOverBucerForAvdod = listOf(
            EuxInnhentingService.BucView(euxCaseId, P_BUC_02, AKTOERID, null, AVDOD_FNR, AVDOD),
            EuxInnhentingService.BucView(euxCaseId, P_BUC_01, AKTOERID, null, AVDOD_FNR, AVDOD)
        )

        every { euxInnhentingService.hentBucViewAvdod(any(), any()) } returns listeOverBucerForAvdod
        every { innhentingService.hentRinaSakIderFraJoarksMetadataForOmstilling(any()) } returns listOf("123456", "1234567")
        every { euxInnhentingService.lagBucViews(any(), any(), any(), any()) } returns listeOverBucerForAvdod

        val expected = """
           "[{\"euxCaseId\":\"123456\",\"buctype\":\"P_BUC_02\",\"aktoerId\":\"12345678901\",\"saknr\":null,\"avdodFnr\":\"12345678900\",\"kilde\":\"SAF\"}]"
        """.trimIndent()

        val result = mockMvc.get(endpointUrl).andReturn().response.contentAsString.toJson()
        assertEquals(expected, result)
    }

    @Test
    fun `Ved innhenting av bucer for gjennybrukere returneres alle bucer utenom pbuc01 og pbuc03 `() {
        val endpointUrl = "/gjenny/rinasaker/$AKTOERID"
        val euxCaseId = "123456"
        val listeOverBucerForAvdod = listOf(
            EuxInnhentingService.BucView(euxCaseId, P_BUC_02, AKTOERID, null, AVDOD_FNR, AVDOD),
            EuxInnhentingService.BucView(euxCaseId, P_BUC_01, AKTOERID, null, AVDOD_FNR, AVDOD)
        )

        val listeOverBucViews = listOf(
            EuxInnhentingService.BucView("123456", P_BUC_02, AKTOERID, null, AVDOD_FNR, AVDOD),
            EuxInnhentingService.BucView("1234567", P_BUC_01, AKTOERID, null, AVDOD_FNR, AVDOD)
        )

        every { innhentingService.hentFnrfraAktoerService(any()) } returns NorskIdent(AVDOD_FNR)
        every { innhentingService.hentRinaSakIderFraJoarksMetadata(any()) } returns listOf("123456", "1234567")
        every { euxInnhentingService.hentBucViewBruker(any(), any(), null) } returns listeOverBucViews
        every { euxInnhentingService.lagBucViews(any(), any(), any(), any()) } returns listeOverBucerForAvdod

        val expected = """
           "[{\"euxCaseId\":\"123456\",\"buctype\":\"P_BUC_02\",\"aktoerId\":\"12345678901\",\"saknr\":null,\"avdodFnr\":\"12345678900\",\"kilde\":\"AVDOD\"}]"
        """.trimIndent()

        val result = mockMvc.get(endpointUrl).andReturn().response.contentAsString.toJson()
        assertEquals(expected, result)
    }

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

    @Test
    fun `leggTilInstitusjon returnerer DocumentsItem ved POST til sed_add`() {
        val request = mockk<ApiRequest>(relaxed = true)
        val forventetItem = DocumentsItem().apply { message = "sed fra utlandet" }
        every { euxPrefillService.addInstutionAndDocument(any()) } returns forventetItem

        val resultat = mockMvc.post("/gjenny/sed/add") {
            contentType = MediaType.APPLICATION_JSON
            content = ObjectMapper().writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
        }.andReturn()

        assert(mapJsonToAny<DocumentsItem>(resultat.response.contentAsString).message == forventetItem.message)
    }

//    @Test
//    fun `prefillSed returnerer DocumentsItem ved POST til sed_replysed_parentid`() {
//        val request = ApiRequest(/* fyll inn nødvendige felter */)
//        val parentId = "parent123"
//        val forventetItem = DocumentsItem(/* fyll inn nødvendige felter */)
//        every { euxInnhentingService.addDocumentToParent(request.copy(gjenny = true), parentId) } returns forventetItem
//
//        mockMvc.post("/gjenny/sed/replysed/$parentId") {
//            contentType = MediaType.APPLICATION_JSON
//            content = ObjectMapper().writeValueAsString(request)
//        }.andExpect {
//            status { isOk() }
//            content { json(ObjectMapper().writeValueAsString(forventetItem)) }
//        }
//    }
//
//    @Test
//    fun `oppdaterSed returnerer true ved PUT til sed_document_euxcaseid_documentid`() {
//        val euxcaseid = "case123"
//        val documentid = "doc456"
//        val sedPayload = "payload"
//        every { sedController.updateSed(euxcaseid, documentid, sedPayload) } returns true
//
//        mockMvc.put("/gjenny/sed/document/$euxcaseid/$documentid") {
//            contentType = MediaType.APPLICATION_JSON
//            content = sedPayload
//        }.andExpect {
//            status { isOk() }
//            content { string("true") }
//        }
//    }
}

private const val AKTOERID = "12345678901"
private const val AVDOD_FNR = "12345678900"
