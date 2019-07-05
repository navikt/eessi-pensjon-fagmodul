package no.nav.eessi.eessifagmodul.security.sts

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import no.nav.eessi.eessifagmodul.utils.typeRef
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

data class SecurityTokenResponse(
        @JsonProperty("access_token")
        val accessToken: String,
        @JsonProperty("token_type")
        val tokenType: String,
        @JsonProperty("expires_in")
        val expiresIn: Long
)
/**
 * Denne STS tjenesten benyttes ved kall mot nye REST tjenester sånn som Aktørregisteret
 */
@Service
class STSService(private val securityTokenExchangeBasicAuthRestTemplate: RestTemplate) {

    private val logger = LoggerFactory.getLogger(STSService::class.java)

    fun getSystemOidcToken(): String {
        try {
            val uri = UriComponentsBuilder.fromPath("/")
                    .queryParam("grant_type", "client_credentials")
                    .queryParam("scope", "openid")
                    .build().toUriString()

            logger.info("Kaller STS for å bytte username/password til OIDC token")
            val responseEntity = securityTokenExchangeBasicAuthRestTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    null,
                    typeRef<SecurityTokenResponse>())

            logger.debug("SecurityTokenResponse ${mapAnyToJson(responseEntity)} ")
            validateResponse(responseEntity)
            return responseEntity.body!!.accessToken
        } catch (ex: Exception) {
            logger.error("Feil ved bytting av username/password til OIDC token: ${ex.message}", ex)
            throw SystembrukerTokenException(ex.message!!)
        }
    }

    private fun validateResponse(responseEntity: ResponseEntity<SecurityTokenResponse>) {
        if (responseEntity.statusCode.isError)
            throw RuntimeException("SecurityTokenExchange received http-error ${responseEntity.statusCode}:${responseEntity.statusCodeValue}")
    }
}

@ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
class SystembrukerTokenException(message: String) : Exception(message)