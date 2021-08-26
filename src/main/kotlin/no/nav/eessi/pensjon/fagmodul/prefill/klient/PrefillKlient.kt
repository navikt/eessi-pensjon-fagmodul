package no.nav.eessi.pensjon.fagmodul.prefill.klient

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.fagmodul.models.ApiRequest
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ResponseStatusException
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

    private lateinit var prefillSed: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        prefillSed = metricsHelper.init("prefillSed", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
    }

    fun hentPreutfyltSed(request: ApiRequest): String {
        val path = "/sed/prefill"

        return prefillSed.measure {
            return@measure try {
                logger.info("Henter preutfylt SED")
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON

                prefillOidcRestTemplate.exchange(
                        path,
                        HttpMethod.POST,
                        HttpEntity(request, headers),
                        String::class.java).body!!
            } catch (ex: HttpStatusCodeException) {
                if (ex.statusCode == HttpStatus.BAD_REQUEST) logger.warn(ex.message, ex)  else logger.error(ex.message, ex)
                throw ex
            } catch (ex: HttpClientErrorException) {
                if (ex.statusCode == HttpStatus.BAD_REQUEST) logger.warn(ex.message, ex)  else logger.error(ex.message, ex)
                throw ResponseStatusException(ex.statusCode, ex.message)
            } catch (ex: Exception) {
                logger.error("En feil oppstod under henting av preutfylt SED ex: ", ex)
                throw ResponseStatusException( HttpStatus.INTERNAL_SERVER_ERROR,"En feil oppstod under henting av preutfylt SED ex: ${ex.message}")
            }
        }
    }
}
