package no.nav.eessi.pensjon.api.geo

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.ninjasquad.springmockk.MockkBean
import io.mockk.MockKAnnotations
import io.mockk.every
import no.nav.eessi.pensjon.fagmodul.api.FrontEndResponse
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import org.slf4j.LoggerFactory

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
    private val logger: Logger = LoggerFactory.getLogger(LandOgValutakodeController::class.java) as Logger
    private val listAppender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun before() {
        MockKAnnotations.init(this, relaxed = true, relaxUnitFun = true)
        kodeverkService = KodeverkService(restTemplate)
        listAppender.start()
        logger.addAppender(listAppender)
    }

    @AfterEach
    fun after() {
        logger.detachAppender(listAppender)
        listAppender.stop()
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
        assertEquals(resultatFraRina(), response.result.toString())
        assertTrue(listAppender.list.any { it.message.contains("landOgValutakoderAkseptertAvRina tid: ") })
    }

    fun resultatFraRina(): String {
        return """
            {v4.2={euEftaLand=[{landkode=AUT, landnavn=Østerrike}, {landkode=BEL, landnavn=Belgia}], verdensLand=[{landkode=ABW, landnavn=Aruba}, {landkode=AFG, landnavn=Afghanistan}], statsborgerskap=[{landkode=AFG, landnavn=Afghanistan}, {landkode=ALB, landnavn=Albania}], verdensLandHistorisk=[{landkode=ABW, landnavn=Aruba}, {landkode=AFG, landnavn=Afghanistan}], euEftaValuta=[{valutakode=EUR, valutanavn=Euro}, {valutakode=NOK, valutanavn=Norske kroner}], verdensValuta=[{valutakode=USD, valutanavn=Amerikanske dollar}, {valutakode=GBP, valutanavn=Britiske pund}]}, v4.3={euEftaLand=[{landkode=AUT, landnavn=Østerrike}, {landkode=BEL, landnavn=Belgia}], verdensLand=[{landkode=ABW, landnavn=Aruba}, {landkode=AFG, landnavn=Afghanistan}], statsborgerskap=[{landkode=AFG, landnavn=Afghanistan}, {landkode=ALB, landnavn=Albania}], verdensLandHistorisk=[{landkode=ABW, landnavn=Aruba}, {landkode=AFG, landnavn=Afghanistan}], euEftaValuta=[{valutakode=EUR, valutanavn=Euro}, {valutakode=NOK, valutanavn=Norske kroner}], verdensValuta=[{valutakode=USD, valutanavn=Amerikanske dollar}, {valutakode=GBP, valutanavn=Britiske pund}]}, v4.4=null}
        """.trimIndent()
    }

    @Test
    fun `testerLandOgValutakoderRina returnerer 500 naar kallet mot rina feiler`() {
        every {
            restTemplate.exchange(any<String>(), any<HttpMethod>(), any<HttpEntity<String>>(), eq(String::class.java))
        } throws RuntimeException("Rina er nede")

        val repsonse = mvc.perform(
            get("/landogvalutakoder/rina")
                .param("format", "json")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isInternalServerError())
            .andReturn().response

        val response = mapJsonToAny<FrontEndResponse<*>>(repsonse.contentAsString)
        assertEquals("INTERNAL_SERVER_ERROR", response.status)
    }
}
