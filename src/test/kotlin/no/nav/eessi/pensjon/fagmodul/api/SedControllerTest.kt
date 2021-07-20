package no.nav.eessi.pensjon.fagmodul.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.eux.EuxPrefillService
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.models.ApiRequest
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.fagmodul.prefill.klient.PrefillKlient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonService
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.typeRefs
import no.nav.eessi.pensjon.vedlegg.VedleggService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity
import org.springframework.web.util.UriComponentsBuilder

class SedControllerTest {

    @MockK
    lateinit var mockEuxPrefillService: EuxPrefillService

    @MockK
    lateinit var mockEuxInnhentingService: EuxInnhentingService

    @MockK
    lateinit var vedleggService: VedleggService

    @MockK
    lateinit var pensjonsinformasjonService: PensjonsinformasjonService

    @MockK
    lateinit var personService: PersonService

    @MockK
    lateinit var prefillKlient: PrefillKlient

    private lateinit var sedController: SedController

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        mockEuxPrefillService.initMetrics()

        val innhentingService = InnhentingService(personService, vedleggService, prefillKlient, pensjonsinformasjonService)
        innhentingService.initMetrics()

        this.sedController = SedController(
            mockEuxInnhentingService,
            mockk(relaxed = true),
            "http://rinaurl/cpi"
        )
    }

    @Test
    fun `create frontend request`() {
        val json =
            "{\"institutions\":[{\"country\":\"NO\",\"institution\":\"DUMMY\"}],\"buc\":\"P_BUC_06\",\"sed\":\"P6000\",\"sakId\":\"123456\",\"aktoerId\":\"0105094340092\"}"

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
        every { mockEuxInnhentingService.getSedOnBucByDocumentId("2313", "23123123123") } returns sed

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

        every { mockEuxInnhentingService.getBuc(rinanr) } returns mockBuc

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson(listOf(SedType.P2000)))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(1, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString   buc_06 returns 4 seds`() {
        val mockBuc =
            mapJsonToAny(javaClass.getResource("/json/buc/buc_P_BUC_06_4.2_tom.json").readText(), typeRefs<Buc>())
        val buc = "P_BUC_06"
        val rinanr = "1000101"

        every { mockEuxInnhentingService.getBuc(rinanr) } returns mockBuc

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse =
            ResponseEntity.ok(mapAnyToJson(listOf(SedType.P5000, SedType.P6000, SedType.P7000, SedType.P10000)))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(4, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString   buc_06 returns 3 seds if a sed already exists`() {
        val mockBuc =
            mapJsonToAny(javaClass.getResource("/json/buc/buc-P_BUC_06_4.2_P5000.json").readText(), typeRefs<Buc>())
        val buc = "P_BUC_06"
        val rinanr = "1000101"

        every { mockEuxInnhentingService.getBuc(rinanr) } returns mockBuc

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse =
            ResponseEntity.ok().body(mapAnyToJson(listOf(SedType.P10000, SedType.P6000, SedType.P7000)))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(3, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString buc_01 returns lots of seds`() {
        val mockBuc = mapJsonToAny(javaClass.getResource("/json/buc/buc-22909_v4.1.json").readText(), typeRefs<Buc>())
        val buc = "P_BUC_01"
        val rinanr = "1000101"

        every { mockEuxInnhentingService.getBuc(rinanr) } returns mockBuc

        val actualResponse = sedController.getSeds(buc, rinanr)

        val sedList = listOf(
            SedType.H020,
            SedType.H070,
            SedType.H120,
            SedType.P10000,
            SedType.P3000_NO,
            SedType.P4000,
            SedType.P5000,
            SedType.P6000,
            SedType.P7000,
            SedType.P8000
        )
        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson(sedList))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(10, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString buc_06 returns 0 seds if a sed is sent`() {
        val mockBuc =
            mapJsonToAny(javaClass.getResource("/json/buc/buc-P_BUC_06-P5000_Sendt.json").readText(), typeRefs<Buc>())
        val buc = "P_BUC_06"
        val rinanr = "1000101"

        every { mockEuxInnhentingService.getBuc(rinanr) } returns mockBuc

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson(listOf<String>()))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(0, list.size)
    }

}

