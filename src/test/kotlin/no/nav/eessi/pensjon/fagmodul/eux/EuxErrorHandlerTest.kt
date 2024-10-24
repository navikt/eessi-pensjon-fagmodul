package no.nav.eessi.pensjon.fagmodul.eux

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.eux.klient.IkkeFunnetException
import no.nav.eessi.pensjon.shared.retry.IOExceptionRetryInterceptor
import org.hamcrest.core.StringContains
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.retry.annotation.EnableRetry
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers
import org.springframework.test.web.client.response.MockRestResponseCreators
import org.springframework.web.client.RestTemplate

@ActiveProfiles(profiles = ["retryConfigOverride"])
@SpringJUnitConfig(classes = [
    TestEuxClientRetryConfig::class,
    EuxKlientRetryLogger::class,
    EuxInnhentingService::class,
    EuxErrorHandlerTest.Config::class]
)
@EnableRetry
class EuxErrorHandlerTest {

    @Autowired
    private lateinit var euxNavIdentRestTemplate: RestTemplate

    @Autowired
    private lateinit var euxInnhentingService: EuxInnhentingService

    private lateinit var server: MockRestServiceServer

    val logger: Logger = LoggerFactory.getLogger("no.nav.eessi") as Logger
    val listAppender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun setUp() {
        listAppender.start()
        logger.addAppender(listAppender)
        server = MockRestServiceServer.bindTo(euxNavIdentRestTemplate).build()
    }

    @AfterEach
    fun tearDown() {
        listAppender.stop()
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
    fun `logging av calling class skal inneholde informasjon kallende klient`() {
        val euxCaseId = "123456"
        server.expect(ExpectedCount.times(3), MockRestRequestMatchers.requestTo(StringContains.containsString("/buc/$euxCaseId"))).andRespond(
            MockRestResponseCreators.withStatus(HttpStatus.NOT_FOUND)
        )
        assertThrows<IkkeFunnetException> {
            euxInnhentingService.getBuc(euxCaseId)
        }
        assertTrue(inneholderMelding("Calling class: no.nav.eessi.pensjon.eux.klient.EuxKlientLib.getBucJson"))
    }

    fun inneholderMelding(melding: String): Boolean {
        val logsList: List<ILoggingEvent> = listAppender.list
        return logsList.any { logEvent ->
            logEvent.message.contains(melding)
        }
    }
}