package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.eux.klient.EuxConflictException
import no.nav.eessi.pensjon.eux.klient.EuxRinaServerException
import no.nav.eessi.pensjon.eux.klient.ForbiddenException
import no.nav.eessi.pensjon.eux.klient.GatewayTimeoutException
import no.nav.eessi.pensjon.eux.klient.GenericUnprocessableEntity
import no.nav.eessi.pensjon.eux.klient.IkkeFunnetException
import no.nav.eessi.pensjon.eux.klient.RinaIkkeAutorisertBrukerException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.ResponseErrorHandler
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream


/**
 * kaster en lokal exception basert på http status for server/klient -exception
 */
open class EuxErrorHandler : ResponseErrorHandler {

    private val logger = LoggerFactory.getLogger(EuxErrorHandler::class.java)

    @Throws(IOException::class)
    override fun hasError(response: ClientHttpResponse): Boolean {
        return response.statusCode.is4xxClientError || response.statusCode.is5xxServerError
    }
    @Throws(IOException::class)
    override fun handleError(httpResponse: ClientHttpResponse) {
        logger.error("Error ved henting fra EUX. Response:\n ${ReReadableClientHttpResponse(httpResponse)}")
        if (httpResponse.statusCode.is5xxServerError) {
            when (httpResponse.statusCode) {
                HttpStatus.INTERNAL_SERVER_ERROR -> throw EuxRinaServerException("Rina serverfeil, kan også skyldes ugyldig input")
                HttpStatus.GATEWAY_TIMEOUT -> throw GatewayTimeoutException("Venting på respons fra Rina resulterte i en timeout")
                HttpStatus.BAD_REQUEST -> if (httpResponse.body.toString().contains("postalCode"))throw EuxRinaServerException("Postnummer i PDLadresse er for lang til å preutfylle postnummer i sed")
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

    private class ReReadableClientHttpResponse(original: ClientHttpResponse) : ClientHttpResponse by original {
        val originalBody = original.body.readBytes()

        override fun getBody(): InputStream {
            return ByteArrayInputStream(originalBody)
        }
    }
}