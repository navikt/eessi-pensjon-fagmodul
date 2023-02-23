package no.nav.eessi.pensjon.fagmodul.eux

import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.ResponseErrorHandler
import java.io.IOException


/**
 * kaster en lokal exception basert på http status for server/klient -exception
 */
open class EuxErrorHandler : ResponseErrorHandler {
    @Throws(IOException::class)
    override fun hasError(response: ClientHttpResponse): Boolean {
        return response.statusCode.is4xxClientError || response.statusCode.is5xxServerError
    }
    @Throws(IOException::class)
    override fun handleError(httpResponse: ClientHttpResponse) {
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
                else -> throw GenericUnprocessableEntity("En feil har oppstått")
            }
        }
        throw Exception("Ukjent Feil oppstod: ${httpResponse.statusText}")
    }
}