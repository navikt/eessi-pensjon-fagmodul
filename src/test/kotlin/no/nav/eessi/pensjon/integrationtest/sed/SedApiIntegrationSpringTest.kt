package no.nav.eessi.pensjon.integrationtest.sed

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.fagmodul.eux.EuxKlient
import no.nav.eessi.pensjon.security.sts.STSService
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertNotNull

@SpringBootTest(
    classes = [UnsecuredWebMvcTestLauncher::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc
class SedApiIntegrationSpringTest {

    @MockBean
    private lateinit var stsService: STSService

    @MockBean
    private lateinit var euxKlient: EuxKlient

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    @Throws(Exception::class)
    fun `Gitt forespørel etter P5000 så Map trygdetid`() {
        val p5000json = String(Files.readAllBytes(Paths.get("src/test/resources/json/nav/P5000-NAV.json")))
        doReturn(p5000json).`when`(euxKlient).getSedOnBucByDocumentIdAsJson(any(), any())

        val result = mockMvc.perform(get("/sed/get/1234/5678"))
            .andExpect(status().isOk)
            .andReturn()

        val response  = result.response.getContentAsString(charset("UTF-8"))
        val p5000 = mapJsonToAny(response, typeRefs<P5000>())
        assertNotNull(p5000.p5000Pensjon.trygdetid!!.size == 1)
        assertNotNull(p5000.p5000Pensjon.medlemskap!!.size == 1)

    }
}

