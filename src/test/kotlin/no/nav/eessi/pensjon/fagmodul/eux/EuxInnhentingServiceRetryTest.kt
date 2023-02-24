package no.nav.eessi.pensjon.fagmodul.eux

import io.mockk.mockk
import no.nav.eessi.pensjon.eux.klient.EuxKlientForSystemUser
import no.nav.eessi.pensjon.eux.klient.IkkeFunnetException
import no.nav.eessi.pensjon.shared.retry.IOExceptionRetryInterceptor
import org.hamcrest.core.StringContains
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.retry.annotation.EnableRetry
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers
import org.springframework.test.web.client.response.MockRestResponseCreators
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import java.io.IOException


@ActiveProfiles(profiles = ["retryConfigOverride"])
@SpringJUnitConfig(classes = [
    TestEuxClientRetryConfig::class,
    EuxKlientRetryLogger::class,
    EuxInnhentingService::class,
    EuxInnhentingServiceRetryTest.Config::class]
)
@EnableRetry
/**
 * Ser til at retry for metoder i EuxInnhentingService slår inn, og at EuxErrorHandler behandler typen
 */
internal class EuxInnhentingServiceRetryTest {

    @Autowired
    private lateinit var euxSystemRestTemplate: RestTemplate

    @Autowired
    private lateinit var euxNavIdentRestTemplate: RestTemplate

    @Autowired
    private lateinit var euxKlient: EuxKlientForSystemUser

    @Autowired
    private lateinit var euxInnhentingService: EuxInnhentingService

    private lateinit var server: MockRestServiceServer

    @BeforeEach
    fun setUp() {
        server = MockRestServiceServer.bindTo(euxNavIdentRestTemplate).build()
    }

    @TestConfiguration
    class Config {
        @Bean
        fun euxNavIdentRestTemplate(): RestTemplate {
            return RestTemplateBuilder()
                .errorHandler(EuxErrorHandler())
                .additionalInterceptors(IOExceptionRetryInterceptor())
                .build()
        }
        @Bean
        fun euxSystemRestTemplate(): RestTemplate = mockk()
        @Bean
        fun euxKlient(): EuxKlientForSystemUser = EuxKlientForSystemUser(euxNavIdentRestTemplate(), euxSystemRestTemplate())
    }

    @Test
    fun `gitt at det finnes en gyldig euxCaseid og Buc og en exception kastes, så skal retry benyttes før endelig exception til slutt`() {
        val euxCaseId = "123456"
        server.expect(ExpectedCount.times(3), MockRestRequestMatchers.requestTo(StringContains.containsString("/buc/$euxCaseId"))).andRespond(
            MockRestResponseCreators.withStatus(HttpStatus.NOT_FOUND)
        )
        assertThrows<IkkeFunnetException> {
            euxInnhentingService.getBuc(euxCaseId)
        }
    }

    @Test
    fun `gitt et kall til getRinaSaker som kaster en IOException og fanges av IOExceptionRetryInterceptor`() {
        repeat(3){
            server.expect(MockRestRequestMatchers.requestTo(StringContains.containsString("/rinasaker"))).andRespond { throw IOException("take $it") }
        }

        assertThrows<ResourceAccessException> {
            euxKlient.getRinasaker("12345678900", null)
        }
        server.verify()
    }
}
@Profile("retryConfigOverride")
@Component("euxKlientRetryConfig")
data class TestEuxClientRetryConfig(val initialRetryMillis: Long = 10L)
