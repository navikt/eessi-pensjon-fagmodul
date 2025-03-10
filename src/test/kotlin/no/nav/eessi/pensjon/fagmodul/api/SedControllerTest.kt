package no.nav.eessi.pensjon.fagmodul.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.*
import io.mockk.impl.annotations.MockK
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_06
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.eux.model.sed.P8000
import no.nav.eessi.pensjon.eux.model.sed.P8000Frontend
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.eux.EuxPrefillService
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.fagmodul.prefill.klient.PrefillKlient
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonService
import no.nav.eessi.pensjon.shared.api.ApiRequest
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.eessi.pensjon.vedlegg.VedleggService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
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

    @MockK
    lateinit var gcpStorageService: GcpStorageService

    private lateinit var sedController: SedController

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        InnhentingService(personService, vedleggService, prefillKlient, pensjonsinformasjonService)

        this.sedController = SedController(
            mockEuxInnhentingService,
            mockk(relaxed = true),
            "http://rinaurl/cpi",
            gcpStorageService
        )
    }

    @Test
    fun `create frontend request`() {
        val json =
            "{\"institutions\":[{\"country\":\"NO\",\"institution\":\"DUMMY\"}],\"buc\":\"P_BUC_06\",\"sed\":\"P6000\",\"sakId\":\"123456\",\"aktoerId\":\"0105094340092\"}"

        //map json request back to FrontendRequest obj
        val map = jacksonObjectMapper()
        val req = map.readValue(json, ApiRequest::class.java)


        assertEquals(P_BUC_06, req.buc)
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
    fun `putDokument skal lagre p8000 med options`() {
        val slot = slot<String>()
        every { gcpStorageService.lagreP8000Options(any(), capture(slot)) } just Runs

        val p8000sed = mapJsonToAny<P8000Frontend>(javaClass.getResource("/json/sed/P8000-NAV.json")!!.readText())
        sedController.putDocument("123456", "222222", p8000sed.toJson())

        val expectedJson = mockP8000()
        JSONAssert.assertEquals(expectedJson, slot.captured, false)
    }

    @Test
    fun `getDocument skal hente p8000 med options`() {
        val p8000sed = mapJsonToAny<P8000>(javaClass.getResource("/json/nav/P8000_NO-NAV.json")!!.readText())
        every { mockEuxInnhentingService.getSedOnBucByDocumentId(any(), any()) } returns p8000sed
        every { gcpStorageService.hentP8000(any()) } returns mockP8000()
        val p8000Frontend = sedController.getDocument("123456", "222222")

        // verifiserer at options blir hentet og konvertert til json
        val p8000FromJson = mapJsonToAny<P8000Frontend>(p8000Frontend)
        JSONAssert.assertEquals(p8000FromJson.options?.toJsonSkipEmpty(), mockP8000(), false)

        assertEquals(p8000FromJson.nav?.bruker?.person?.pin?.firstOrNull()?.identifikator, "9876543210")
    }


    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString   buc_01 returns 1 sed`() {
        val mockBuc = mapJsonToAny<Buc>(javaClass.getResource("/json/buc/P_BUC_01_4.2_tom.json").readText())
        val buc = "P_BUC_01"
        val rinanr = "1000101"

        every { mockEuxInnhentingService.getBuc(rinanr) } returns mockBuc

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson(listOf(SedType.P2000)))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny<List<String>>(actualResponse.body!!)
        assertEquals(1, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString   buc_06 returns 4 seds`() {
        val mockBuc =  mapJsonToAny<Buc>(javaClass.getResource("/json/buc/buc_P_BUC_06_4.2_tom.json").readText())
        val buc = "P_BUC_06"
        val rinanr = "1000101"

        every { mockEuxInnhentingService.getBuc(rinanr) } returns mockBuc

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse =
            ResponseEntity.ok(mapAnyToJson(listOf(SedType.P5000, SedType.P6000, SedType.P7000, SedType.P10000)))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny<List<String>>(actualResponse.body!!)
        assertEquals(4, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString buc_06 ingen documents skal retutnere 4 gyldige sedtyper`() {
        val mockBuc = mapJsonToAny<Buc>(javaClass.getResource("/json/buc/P_BUC_06_emptyDocumentsList.json").readText())
        val buc = "P_BUC_06"
        val rinanr = "434164"

        every { mockEuxInnhentingService.getBuc(rinanr) } returns mockBuc

        val actualResponse = sedController.getSeds(buc, rinanr)
        val expectedResponse = ResponseEntity.ok(mapAnyToJson(listOf(SedType.P5000, SedType.P6000, SedType.P7000, SedType.P10000)))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny<List<String>>(actualResponse.body!!)
        println(list.toJson())

        assertEquals(4, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString   buc_06 returns 3 seds if a sed already exists`() {
        val mockBuc =
            mapJsonToAny<Buc>(javaClass.getResource("/json/buc/buc-P_BUC_06_4.2_P5000.json").readText())
        val buc = "P_BUC_06"
        val rinanr = "1000101"

        every { mockEuxInnhentingService.getBuc(rinanr) } returns mockBuc

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse =
            ResponseEntity.ok().body(mapAnyToJson(listOf(SedType.P10000, SedType.P6000, SedType.P7000)))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny<List<String>>(actualResponse.body!!)
        assertEquals(3, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString buc_01 returns lots of seds`() {
        val mockBuc = mapJsonToAny<Buc>(javaClass.getResource("/json/buc/buc-22909_v4.1.json").readText())
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

        val list = mapJsonToAny<List<String>>(actualResponse.body!!)
        assertEquals(10, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString buc_06 returns 0 seds if a sed is sent`() {
        val mockBuc =
            mapJsonToAny<Buc>(javaClass.getResource("/json/buc/buc-P_BUC_06-P5000_Sendt.json").readText())
        val buc = "P_BUC_06"
        val rinanr = "1000101"

        every { mockEuxInnhentingService.getBuc(rinanr) } returns mockBuc

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson(listOf<String>()))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny<List<String>>(actualResponse.body!!)
        assertEquals(0, list.size)
    }

    @Test
    fun `sjekk for gyldig liste av beregninger i P5000oppdatering`() {

        val mockP5000 = mapJsonToAny<P5000>(javaClass.getResource("/json/nav/P5000OppdateringNAV.json").readText())

        val mockP5000Rina = mapJsonToAny<P5000>(javaClass.getResource("/json/nav/P5000OppdateringRinaNav.json").readText())

        val trygdetidberegning = mockP5000.pensjon?.trygdetid?.map { it.beregning }
        val medlemskapsberegning = mockP5000.pensjon?.medlemskapboarbeid?.medlemskap?.map { it.beregning }

        val trygdetidberegningRina = mockP5000Rina.pensjon?.trygdetid?.map { it.beregning }
        val medlemskapsberegningRina = mockP5000Rina.pensjon?.medlemskapboarbeid?.medlemskap?.map { it.beregning }

        assertEquals(3, trygdetidberegning?.size)
        assertEquals(6, medlemskapsberegning?.size)

        assertEquals(3, trygdetidberegningRina?.size)
        assertEquals(6, medlemskapsberegningRina?.size)
    }

    @Test
    fun `mockinnhenting`() {

        val json = javaClass.getResource("/json/nav/P5000OppdateringNAV.json").readText()

        val mockP5000 = SED.fromJsonToConcrete(json) as P5000

        val trygdetidberegning = mockP5000.pensjon?.trygdetid?.map { it.beregning }
        val medlemskapsberegning = mockP5000.pensjon?.medlemskapboarbeid?.medlemskap?.map { it.beregning }

        assertEquals(3, trygdetidberegning?.size)
        assertEquals(6, medlemskapsberegning?.size)

    }


    fun mockP8000() :String {
        return """{
              "type" : {
                "bosettingsstatus" : "UTL",
                "spraak" : "nb",
                "ytelse" : "UT"
              },
              "ofteEtterspurtInformasjon" : {
                "tiltak" : {
                  "value" : true
                },
                "medisinskInformasjon" : {
                  "value" : true
                },
                "naavaerendeArbeid" : {
                  "value" : true
                },
                "dokumentasjonPaaArbeidINorge" : {
                  "value" : true
                }
              }
            }""".trimIndent()
    }

}

