package no.nav.eessi.pensjon.fagmodul.prefill.klient

import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.shared.api.ApiRequest
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ResponseStatusException

/**
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Component
class PrefillKlient(private val prefillOAuthTemplate: RestTemplate,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillKlient::class.java) }
    private lateinit var prefillSed: MetricsHelper.Metric
    init {
        prefillSed = metricsHelper.init("prefillSed", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
    }

    fun hentPreutfyltSed(request: ApiRequest): String {
        val path = "/sed/prefill"
            logger.debug("Request til preutfylling: $request")

        return try {
            logger.info("Henter preutfylt SED")
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            prefillOAuthTemplate.exchange(
                    path,
                    HttpMethod.POST,
                    HttpEntity(request, headers),
                    String::class.java).body!!

        } catch (ex1: HttpStatusCodeException) {
            logger.error("En HttpStatusCodeException oppstod under henting av preutfylt SED", ex1.cause)

            val errorMessage = ResponseErrorData.from(ex1)
            if (ex1.statusCode == HttpStatus.BAD_REQUEST) logger.warn(errorMessage.message, ex1)  else logger.error(errorMessage.message, ex1)
            throw ResponseStatusException(ex1.statusCode, errorMessage.message)

        } catch (ex2: HttpClientErrorException) {
            logger.error("En HttpClientErrorException oppstod under henting av preutfylt SED", ex2.cause)

            val errorMessage = ResponseErrorData.from(ex2)
            if (ex2.statusCode == HttpStatus.BAD_REQUEST) logger.warn(errorMessage.message, ex2)  else logger.error(ex2.message, ex2)
            throw ResponseStatusException(ex2.statusCode, errorMessage.message)

        } catch (ex3: Exception) {
            logger.error("En feil oppstod under henting av preutfylt SED ex: ", ex3)
            throw ResponseStatusException( HttpStatus.INTERNAL_SERVER_ERROR,"En feil oppstod under henting av preutfylt SED ex: ${ex3.message}")
        }

    }

    data class ResponseErrorData(
        val timestamp: String,
        val status: Int,
        val error: String,
        val message: String,
        val path: String
    ) {
        companion object {
            fun from(hsce: HttpStatusCodeException): ResponseErrorData {
                return mapJsonToAny(hsce.responseBodyAsString)
            }
            fun fromJson(json: String): ResponseErrorData {
                return mapJsonToAny(json)
            }
        }

        override fun toString(): String {
            return message
        }
    }

}

