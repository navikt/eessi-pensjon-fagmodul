package no.nav.eessi.pensjon.api.geo

import com.ninjasquad.springmockk.MockkBean
import io.mockk.MockKAnnotations
import io.mockk.every
import no.nav.eessi.pensjon.fagmodul.api.FrontEndResponse
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(LandkodeController::class)
@ComponentScan(basePackages = ["no.nav.eessi.pensjon.api.geo"])
@ActiveProfiles("unsecured-webmvctest")
class LandkodeControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @MockkBean
    lateinit var kodeverkClient: KodeverkClient

    @MockkBean
    lateinit var kodeverkService: KodeverkService

    @BeforeEach
    fun before() {
        MockKAnnotations.init(this, relaxed = true, relaxUnitFun = true)
    }

    @Test
    fun testLandkoderEndpoint() {
        every { kodeverkClient.hentAlleLandkoder() } returns "AU"

        val repsonse = mvc.perform(get("/landkoder/")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().response
        val response = mapJsonToAny<FrontEndResponse<*>>(repsonse.contentAsString)
        assertEquals("AU", response.result)
    }

    @Test
    fun testLandkoder2Endpoint() {
        every { kodeverkClient.hentLandkoderAlpha2() } returns listOf("AU","NO", "BE")

        val repsonse = mvc.perform(get("/landkoder/landkoder2")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().response
        val response = mapJsonToAny<FrontEndResponse<List<String>>>(repsonse.contentAsString)
        assertEquals( "[AU, NO, BE]", response.result.toString())
    }

    @Test
    fun testGetLandKoderAlpha3Endpoint() {
        every { kodeverkClient.finnLandkode("DK") } returns "DKK"

        val repsonse = mvc.perform(get("/landkoder/DK/land3")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().response
        val response = mapJsonToAny<FrontEndResponse<*>>(repsonse.contentAsString)
        assertEquals("DKK", response.result)
    }

    @Test
    fun testGetLandKoderAlpha2Endpoint() {
        every { kodeverkClient.finnLandkode("DKK") } returns "DK"

        val repsonse = mvc.perform(get("/landkoder/DKK/land2")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().response
        val response = mapJsonToAny<FrontEndResponse<*>>(repsonse.contentAsString)
        assertEquals("DK", response.result)
    }

    @Test
    @Disabled
    fun testerLandkoderAkseptertAvRina() {
        every { kodeverkService.getLandkoderAkseptertAvRina("json") } returns "DK"
        val repsonse = mvc.perform(get("/landkoder/rina")
            .param("format", "json")
//            .accept(MediaType.APPLICATION_JSON)
        )
//            .andExpect(status().isOk())
            .andReturn().response
        val response = mapJsonToAny<FrontEndResponse<*>>(repsonse.contentAsString)
        println(response)

//        assertEquals("DK", response.result)
    }

}