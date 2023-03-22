package no.nav.eessi.pensjon.fagmodul.eux

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
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
        return response.statusCode.is4xxClientError || response.statusCode.is5xxServerError
    }
    @Throws(IOException::class)
    override fun handleError(httpResponse: ClientHttpResponse) {
        logger.error("Error ved henting fra EUX")
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
                        throw KanIkkeOppretteSedFeilmelding("Postnummer i PDLadresse er for lang til å preutfylle postnummer i sed")
                    }
                } else -> throw GenericUnprocessableEntity("En feil har oppstått")
            }
        }
        throw Exception("Ukjent Feil oppstod: ${httpResponse.statusText}")
    }

    open fun jacksonMapper(): ObjectMapper {
        val mapper = ObjectMapper().apply {
            configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        }
        return MappingJackson2HttpMessageConverter(mapper).objectMapper
    }

    @Throws(IOException::class)
    private fun logResponse(response: ClientHttpResponse) {
        logger.warn("""
            Status code  : {}, ${response.statusCode}
            Status text  : {}, ${response.statusText}
            Headers      : {}, ${response.headers}
            Response body: {}, ${StreamUtils.copyToString(response.body, Charset.defaultCharset())}
        """.trimIndent())
    }
}