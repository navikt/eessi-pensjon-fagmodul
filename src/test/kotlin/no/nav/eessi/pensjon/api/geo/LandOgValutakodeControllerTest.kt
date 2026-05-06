package no.nav.eessi.pensjon.api.geo

import com.ninjasquad.springmockk.MockkBean
import io.mockk.MockKAnnotations
import io.mockk.every
import no.nav.eessi.pensjon.fagmodul.api.FrontEndResponse
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.client.RestTemplate

@WebMvcTest(LandOgValutakodeController::class)
@ComponentScan(basePackages = ["no.nav.eessi.pensjon.api.geo"])
@ActiveProfiles("unsecured-webmvctest")
class LandOgValutakodeControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @MockkBean
    lateinit var kodeverkClient: KodeverkClient

    @MockkBean
    lateinit var restTemplate: RestTemplate

    lateinit var kodeverkService: KodeverkService

    @BeforeEach
    fun before() {
        MockKAnnotations.init(this, relaxed = true, relaxUnitFun = true)
        kodeverkService = KodeverkService(restTemplate)
    }

    @Test
    fun testerLandOgValutakoderAkseptertAvRina() {
        every {
            restTemplate.exchange(any<String>(), any<HttpMethod>(), any<HttpEntity<String>>(), eq(String::class.java))
        } returns javaClass.getResource("/json/kodeverk/landOgValutakoderFraTen.json")!!.readText().let {
            org.springframework.http.ResponseEntity.ok(it)
        }

        val repsonse = mvc.perform(
            get("/landogvalutakoder/rina")
                .param("format", "json")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().response

        val response = mapJsonToAny<FrontEndResponse<*>>(repsonse.contentAsString)
        JSONAssert.assertEquals(resultatFraRina(), response.result.toString(), false)
    }

    fun resultatFraRina(): String {
        return """
            {v4.2={euEftaLand=[{landkode=AUT, landnavn=Østerrike}, {landkode=BEL, landnavn=Belgia}], verdensLand=[{landkode=ABW, landnavn=Aruba}, {landkode=AFG, landnavn=Afghanistan}], statsborgerskap=[{landkode=AFG, landnavn=Afghanistan}, {landkode=ALB, landnavn=Albania}], verdensLandHistorisk=[{landkode=ABW, landnavn=Aruba}, {landkode=AFG, landnavn=Afghanistan}], euEftaValuta=[{valutakode=EUR, valutanavn=Euro}, {valutakode=NOK, valutanavn=Norske kroner}], verdensValuta=[{valutakode=USD, valutanavn=Amerikanske dollar}, {valutakode=GBP, valutanavn=Britiske pund}]}, v4.3={euEftaLand=[{landkode=AUT, landnavn=Østerrike}, {landkode=BEL, landnavn=Belgia}], verdensLand=[{landkode=ABW, landnavn=Aruba}, {landkode=AFG, landnavn=Afghanistan}], statsborgerskap=[{landkode=AFG, landnavn=Afghanistan}, {landkode=ALB, landnavn=Albania}], verdensLandHistorisk=[{landkode=ABW, landnavn=Aruba}, {landkode=AFG, landnavn=Afghanistan}], euEftaValuta=[{valutakode=EUR, valutanavn=Euro}, {valutakode=NOK, valutanavn=Norske kroner}], verdensValuta=[{valutakode=USD, valutanavn=Amerikanske dollar}, {valutakode=GBP, valutanavn=Britiske pund}]}}
        """.trimIndent()
    }
}
