package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.eux.klient.*
import no.nav.eessi.pensjon.kodeverk.KodeverkClient.Companion.toJson
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.*
import org.springframework.http.client.ClientHttpResponse
import org.springframework.util.StreamUtils
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.ResponseErrorHandler
import java.io.IOException
import java.nio.charset.Charset


/**
 * EuxErrorHandler er en utvidelse av DefaultResponseErrorHandler som håndterer
 * HTTP-feil for EUX/RINA integrasjon.
 *
 * Den har som ansvar å:
 *  - Sjekke om en HTTP-respons inneholder en feil (4xx/5xx statuskoder).
 *  - Logge responsen, inkludert detaljert feilmelding og responsbody.
 *  - Kaste spesifikke unntak (exceptions) basert på ulike HTTP-statuskoder, som f.eks.
 *    500 (Internal Server Error), 404 (Not Found), 401 (Unauthorized) osv.
 *  - Fange opp ukjente feil og logge disse for videre debugging.
 */
open class EuxErrorHandler : ResponseErrorHandler {

    private val logger = LoggerFactory.getLogger(EuxErrorHandler::class.java)

    @Throws(IOException::class)
    override fun hasError(response: ClientHttpResponse): Boolean {
        return response.statusCode.isError
    }

    @Throws(IOException::class)
    override fun handleError(httpResponse: ClientHttpResponse) {
        logResponse(httpResponse)

        when (httpResponse.statusCode) {
            BAD_REQUEST -> handleBadRequest(httpResponse)
            NOT_FOUND -> throw IkkeFunnetException("Ikke funnet")
            FORBIDDEN -> throw ForbiddenException("Forbidden, Ikke tilgang")
            CONFLICT -> throw EuxConflictException("En konflikt oppstod under kall til Rina")
            UNAUTHORIZED -> throw RinaIkkeAutorisertBrukerException("Authorization token required for Rina.")
            GATEWAY_TIMEOUT -> throw GatewayTimeoutException("Venting på respons fra Rina resulterte i en timeout")
            INTERNAL_SERVER_ERROR -> throw EuxRinaServerException("Rina serverfeil, kan også skyldes ugyldig input")
            else -> handleBadRequest(httpResponse)
        }
    }

    @Throws(IOException::class)
    private fun handleBadRequest(httpResponse: ClientHttpResponse) {
        val responseBody = StreamUtils.copyToString(httpResponse.body, Charset.defaultCharset())
        if (responseBody.contains("postalCode")) {
            throw KanIkkeOppretteSedFeilmelding("Postnummer overskrider maks antall tegn (25) i PDL.")
        }
        throw GenericUnprocessableEntity("Bad request, en feil har oppstått")
    }

    @Throws(IOException::class)
    private fun logResponse(response: ClientHttpResponse) {
        val errorMsg = StreamUtils.copyToString(response.body, Charset.defaultCharset())
        val callingClass = getCallingClasses()
        val logMessage = """
            Calling class: ${callingClass}
            Status code  : ${response.statusCode}
            Response body: $errorMsg""".trimIndent()

        if (errorMsg.contains("Could not find RINA case with id")) {
            logger.warn(logMessage)
        } else {
            logger.error(logMessage)
        }
    }

    private fun getCallingClasses(): List<String> {
        val stackTrace = Thread.currentThread().stackTrace

        val excludedClasses = setOf(this::class.java.name, "ResponseErrorHandler", "RestTemplate", "MetricsHelper", "Metric")

        return stackTrace.drop(2)
            .take(15)
            // filterer bort classer som ikke er relevant for logging
            .filter { element ->
                excludedClasses.none { excluded -> element.className.contains(excluded) } &&
                        element.className.contains("eessi")  // Includes only results containing "eessi"
            }
            .map { element ->
                "${element.className}.${element.methodName}"
            }
    }
}