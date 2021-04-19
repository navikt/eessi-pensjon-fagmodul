

package no.nav.eessi.pensjon.fagmodul.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.eux.EuxPrefillService
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.prefill.ApiRequest
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.fagmodul.prefill.PersonDataService
import no.nav.eessi.pensjon.fagmodul.prefill.PrefillService
import no.nav.eessi.pensjon.fagmodul.prefill.klient.PrefillKlient
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.typeRefs
import no.nav.eessi.pensjon.vedlegg.VedleggService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.ResponseEntity
import org.springframework.web.util.UriComponentsBuilder

@ExtendWith(MockitoExtension::class)
class SedControllerTest {

    @Spy
    lateinit var mockEuxPrefillService: EuxPrefillService

    @Spy
    lateinit var mockEuxInnhentingService: EuxInnhentingService

    @Spy
    lateinit var auditLogger: AuditLogger

    @Mock
    lateinit var mockPrefillSEDService: PrefillSEDService

    @Mock
    lateinit var personDataService: PersonDataService

    @Mock
    lateinit var vedleggService: VedleggService

    @Mock
    lateinit var prefillKlient: PrefillKlient

    private lateinit var sedController: SedController

    @BeforeEach
    fun setUp() {
        mockEuxPrefillService.initMetrics()
        personDataService.initMetrics()

        val prefillService = PrefillService(mockPrefillSEDService)
        prefillService.initMetrics()

        val innhentingService = InnhentingService(personDataService, vedleggService, prefillKlient)
        innhentingService.initMetrics()

        this.sedController = SedController(
            mockEuxInnhentingService,
            auditLogger
        )
    }

    @Test
    fun `create frontend request`() {
        val json = "{\"institutions\":[{\"country\":\"NO\",\"institution\":\"DUMMY\"}],\"buc\":\"P_BUC_06\",\"sed\":\"P6000\",\"sakId\":\"123456\",\"aktoerId\":\"0105094340092\"}"

        //map json request back to FrontendRequest obj
        val map = jacksonObjectMapper()
        val req = map.readValue(json, ApiRequest::class.java)


        assertEquals("P_BUC_06", req.buc)
        assertEquals("DUMMY", req.institutions!![0].institution)
        assertEquals("123456", req?.sakId)
    }

    @Test
    fun getDocumentfromRina() {

        val sed = SED(SedType.P2000)
        doReturn(sed).`when`(mockEuxInnhentingService).getSedOnBucByDocumentId("2313", "23123123123")

        val result = sedController.getDocument("2313", "23123123123")
        assertEquals(sed.toJson(), result)
    }

    @Test
    fun `check rest api path correct`() {
        val path = "/sed/get/{rinanr}/{documentid}"
        val uriParams = mapOf("rinanr" to "123456789", "documentid" to "DOC1223213234234")
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)
        assertEquals("/sed/get/123456789/DOC1223213234234", builder.path)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString   buc_01 returns 1 sed`() {
        val mockBuc = mapJsonToAny(javaClass.getResource("/json/buc/P_BUC_01_4.2_tom.json").readText(), typeRefs<Buc>())
        val buc = "P_BUC_01"
        val rinanr = "1000101"

        doReturn(mockBuc).whenever(mockEuxInnhentingService).getBuc(rinanr)

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson( listOf(SedType.P2000)))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(1, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString   buc_06 returns 4 seds`() {
        val mockBuc = mapJsonToAny(javaClass.getResource("/json/buc/buc_P_BUC_06_4.2_tom.json").readText(), typeRefs<Buc>())
        val buc = "P_BUC_06"
        val rinanr = "1000101"

        doReturn(mockBuc).whenever(mockEuxInnhentingService).getBuc(rinanr)

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse =
            ResponseEntity.ok(mapAnyToJson(listOf(SedType.P5000, SedType.P6000, SedType.P7000, SedType.P10000)))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(4, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString   buc_06 returns 3 seds if a sed already exists`() {
        val mockBuc = mapJsonToAny(javaClass.getResource("/json/buc/buc-P_BUC_06_4.2_P5000.json").readText(), typeRefs<Buc>())
        val buc = "P_BUC_06"
        val rinanr = "1000101"

        doReturn(mockBuc).whenever(mockEuxInnhentingService).getBuc(rinanr)

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson(listOf(SedType.P10000, SedType.P6000, SedType.P7000)))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(3, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString buc_01 returns lots of seds`() {
        val mockBuc = mapJsonToAny(javaClass.getResource("/json/buc/buc-22909_v4.1.json").readText(), typeRefs<Buc>())
        val buc = "P_BUC_01"
        val rinanr = "1000101"

        doReturn(mockBuc).whenever(mockEuxInnhentingService).getBuc(rinanr)

        val actualResponse = sedController.getSeds(buc, rinanr)

        val sedList = listOf(SedType.H020, SedType.H070, SedType.H120, SedType.P10000, SedType.P3000_NO, SedType.P4000, SedType.P5000, SedType.P6000, SedType.P7000, SedType.P8000 )
        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson(sedList))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(10, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString buc_06 returns 0 seds if a sed is sent`() {
        val mockBuc = mapJsonToAny(javaClass.getResource("/json/buc/buc-P_BUC_06-P5000_Sendt.json").readText(), typeRefs<Buc>())
        val buc = "P_BUC_06"
        val rinanr = "1000101"

        doReturn(mockBuc).whenever(mockEuxInnhentingService).getBuc(rinanr)

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson( listOf<String>()))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(0, list.size)
    }

}

