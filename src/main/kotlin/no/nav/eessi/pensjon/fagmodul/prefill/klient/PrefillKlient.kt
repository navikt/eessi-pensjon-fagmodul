package no.nav.eessi.pensjon.fagmodul.prefill.klient

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.fagmodul.prefill.ApiRequest
import no.nav.eessi.pensjon.fagmodul.prefill.SedAndType
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import javax.annotation.PostConstruct

/**
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Component
class PrefillKlient(
        private val prefillOidcRestTemplate: RestTemplate,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillKlient::class.java) }
    private val mapper = jacksonObjectMapper()

    private lateinit var prefillSed: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        prefillSed = metricsHelper.init("prefillSed")
    }

    fun hentPreutfyltSed(request: ApiRequest): SedAndType {
        val path = "/sed/prefill"

        return prefillSed.measure {
            return@measure try {
                logger.info("Kaller Joark for å generere en journalpost: $path")
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON

                val response = prefillOidcRestTemplate.exchange(
                        path,
                        HttpMethod.POST,
                        HttpEntity(request.toString(), headers),
                        String::class.java)
                mapper.readValue(response.body, SedAndType::class.java)

            } catch (ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under henting av preutfylt SED ex: ", ex)
                throw RuntimeException("En feil oppstod under henting av preutfylt SED ex: ${ex.message} body: ${ex.responseBodyAsString}")
            } catch (ex: Exception) {
                logger.error("En feil oppstod under henting av preutfylt SED ex: ", ex)
                throw RuntimeException("En feil oppstod under henting av preutfylt SED ex: ${ex.message}")
            }
        }
    }
}