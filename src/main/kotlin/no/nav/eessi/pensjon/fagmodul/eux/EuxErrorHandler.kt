package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.eux.klient.*
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.client.ClientHttpResponse
import org.springframework.util.StreamUtils
import org.springframework.web.client.DefaultResponseErrorHandler
import java.io.IOException
import java.nio.charset.Charset


/**
 * kaster en lokal exception basert på http status for server/klient -exception
 */
open class EuxErrorHandler : DefaultResponseErrorHandler() {

    private val logger = LoggerFactory.getLogger(EuxErrorHandler::class.java)

    @Throws(IOException::class)
    override fun hasError(response: ClientHttpResponse): Boolean {
        if(response.statusCode != HttpStatus.OK) {
            logger.warn("******************* EuxErrorHandler: ${response.statusCode} ********************")
        }
        return response.statusCode.is4xxClientError || response.statusCode.is5xxServerError
    }
    @Throws(IOException::class)
    override fun handleError(httpResponse: ClientHttpResponse) {
        logResponse(httpResponse)

        if (httpResponse.statusCode.is5xxServerError) {
            when (httpResponse.statusCode) {
                HttpStatus.INTERNAL_SERVER_ERROR -> throw EuxRinaServerException("Rina serverfeil, kan også skyldes ugyldig input")
                HttpStatus.GATEWAY_TIMEOUT -> throw GatewayTimeoutException("Venting på respons fra Rina resulterte i en timeout")
                else -> throw GenericUnprocessableEntity("En feil har oppstått")
            }

        } else if (httpResponse.statusCode.is4xxClientError) {
            when (httpResponse.statusCode) {
                HttpStatus.UNAUTHORIZED -> throw RinaIkkeAutorisertBrukerException("Authorization token required for Rina.")
                HttpStatus.FORBIDDEN -> throw ForbiddenException("Forbidden, Ikke tilgang")
                HttpStatus.NOT_FOUND -> throw IkkeFunnetException("Ikke funnet")
                HttpStatus.CONFLICT -> throw EuxConflictException("En konflikt oppstod under kall til Rina")
                HttpStatus.BAD_REQUEST -> {
                    if (StreamUtils.copyToString(httpResponse.body, Charset.defaultCharset()).contains("postalCode")) {
                        throw KanIkkeOppretteSedFeilmelding("Postnummer overskrider maks antall tegn (25) i PDL.")
                    }
                    throw GenericUnprocessableEntity("Bad request, en feil har oppstått")
                } else -> throw GenericUnprocessableEntity("En feil har oppstått")
            }
        }
        throw Exception("Ukjent Feil oppstod: ${httpResponse.statusText}")
    }

    @Throws(IOException::class)
    private fun logResponse(response: ClientHttpResponse) {
        val errorMsg = StreamUtils.copyToString(response.body, Charset.defaultCharset())
        val statusCode = response.statusCode
        val statusText = response.statusText

        val logMessage = """
        Status code  : $statusCode
        Status text  : $statusText
        Response body: $errorMsg """.trimIndent()

        if (errorMsg.contains("Could not find RINA case with id")) {
            logger.warn(logMessage)
        } else {
            logger.error(logMessage)
        }
    }
}