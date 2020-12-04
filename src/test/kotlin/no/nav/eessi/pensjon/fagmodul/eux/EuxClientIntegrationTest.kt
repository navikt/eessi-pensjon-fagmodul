package no.nav.eessi.pensjon.fagmodul.eux

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.whenever
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.security.sts.STSService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.client.RestTemplate

@SpringBootTest(classes = [UnsecuredWebMvcTestLauncher::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc
class EuxClientIntegrationTest {
    val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())

    @MockBean
    lateinit var stsService: STSService

    @MockBean(name = "euxOidcRestTemplate")
    lateinit var restEuxTemplate: RestTemplate

    @Autowired
    lateinit var euxClient: EuxKlient

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `Starter eux client`() {
        val opprettSedMetrics = metricsHelper.init("opprettSed")

        val euxUrlpath = "/buc/{RinaSakId}/sed"

        doReturn(ResponseEntity.ok().body("bah")).whenever(restEuxTemplate).postForEntity(
                eq(euxUrlpath),
                any(),
                eq(String::class.java))


        val sedJson = ""
        euxClient.opprettSed(sedJson, "", opprettSedMetrics, "feil")
    }
}