package no.nav.eessi.pensjon.fagmodul.eux

import io.mockk.mockk
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.eux.klient.IkkeFunnetException
import no.nav.eessi.pensjon.eux.model.BucType
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
import org.springframework.web.client.HttpClientErrorException
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
        fun euxKlient(): EuxKlientAsSystemUser = EuxKlientAsSystemUser(euxNavIdentRestTemplate(), euxSystemRestTemplate())
    }

    @Test
    fun `gitt det finnes en gyldig euxCaseid og Buc og en exception kastes, så skal retry benyttes før endelig exception til slutt`() {
        val euxCaseId = "123456"
        server.expect(ExpectedCount.times(3), MockRestRequestMatchers.requestTo(StringContains.containsString("/buc/$euxCaseId"))).andRespond(
            MockRestResponseCreators.withStatus(HttpStatus.NOT_FOUND)
        )
        assertThrows<IkkeFunnetException> {
            euxInnhentingService.getBuc(euxCaseId)
        }
    }

    @Test
    fun `Gitt at det finnes en gyldig euxCaseid og en exception kastes, så skal retry benyttes før HttpClientErrorException til slutt`() {
        repeat(3){
            server.expect(MockRestRequestMatchers.requestTo(StringContains.containsString("/rinasaker"))).andRespond { throw HttpClientErrorException(HttpStatus.NOT_FOUND, "Ikke funnet") }
        }

        assertThrows<HttpClientErrorException> {
            euxInnhentingService.getRinasaker("12345678900", emptyList())
            server.verify()
        }
    }

    @Test
    fun `Gitt at det finnes en gyldig euxCaseid og exception kastes, så skal retry benyttes før ResourceAccessException kastes til slutt`() {
        repeat(3){
            server.expect(MockRestRequestMatchers.requestTo(StringContains.containsString("/rinasaker"))).andRespond { throw IOException("IO EXCEPTION") }
        }

        assertThrows<ResourceAccessException> {
            euxInnhentingService.getRinasaker("12345678900", emptyList())
            server.verify()
        }
    }

    @Test
    fun `getInstitutions skal ved exception gi 3 retry før ResourceAccessException kastes til slutt`() {
        val bucType = BucType.P_BUC_01.name
        val landkode = "SE"
        repeat(3){
            server.expect(MockRestRequestMatchers.requestTo(StringContains.containsString("/institusjoner?BuCType=$bucType&LandKode=$landkode"))).andRespond {
                throw HttpClientErrorException(HttpStatus.NOT_FOUND, "Ikke funnet")
            }
        }

        assertThrows<HttpClientErrorException> {
            euxInnhentingService.getInstitutions(bucType, landkode)
            server.verify()
        }
    }

    @Test
    fun `getSedOnBucByDocumentId skal ikke gi retry når det kastes en precondition_failed`() {
        val buc = "1111"
        val sed = "2222"
        repeat(1){
            server.expect(MockRestRequestMatchers.requestTo(StringContains.containsString("/buc/$buc/sed/$sed"))).andRespond {
                throw HttpClientErrorException(HttpStatus.PRECONDITION_FAILED, "Ikke funnet")
            }
        }

        assertThrows<HttpClientErrorException> {
            euxInnhentingService.getSedOnBucByDocumentId(buc, sed)
            server.verify()
        }
    }

}
@Profile("retryConfigOverride")
@Component("euxKlientRetryConfig")
data class TestEuxClientRetryConfig(val initialRetryMillis: Long = 10L)
