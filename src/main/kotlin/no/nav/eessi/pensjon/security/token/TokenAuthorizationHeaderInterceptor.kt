package no.nav.eessi.pensjon.security.oidc

import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.security.token.support.core.jwt.JwtToken
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse


//Find token that has longest time or just exist in context.issuer list
//compare expiretime with other token of more than one is found.
class OidcAuthorizationHeaderInterceptor(private val tokenValidationContextHolder: TokenValidationContextHolder) : ClientHttpRequestInterceptor {

    private val logger = LoggerFactory.getLogger(OidcAuthorizationHeaderInterceptor::class.java)

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
        logger.info("sjekker request header for AUTH")

        if (request.headers[HttpHeaders.AUTHORIZATION] == null) {
            val oidcToken = getIdTokenFromIssuer(tokenValidationContextHolder)
            request.headers[HttpHeaders.AUTHORIZATION] = "Bearer $oidcToken"
            logger.debug("setter HttpHeaders.AUTHORIZATION med Bearer token: $oidcToken")
        } else {
            logger.debug("HttpHeaders.AUTHORIZATION alt med token: ${request.headers[HttpHeaders.AUTHORIZATION]}")
        }
        return execution.execute(request, body)
    }

    fun getIdTokenFromIssuer(tokenValidationContextHolder: TokenValidationContextHolder): String {
        return getTokenContextFromIssuer(tokenValidationContextHolder).tokenAsString
    }

    fun getTokenContextFromIssuer(tokenValidationContextHolder: TokenValidationContextHolder): JwtToken {
        val context = tokenValidationContextHolder.tokenValidationContext
        if (context.issuers.isEmpty()) throw RuntimeException("No issuer found in context")

        //supportet token-support token-keys.:
        //var tokenkeys = listOf("isso","oidc","pesys")
        val tokenkeys = context.issuers
        logger.info("Found : ${tokenkeys.size} valid issuers")

        val foundListOfIssuers = tokenkeys.filter { key -> context.getJwtToken(key) != null }
                .map { key -> context.getJwtToken(key) }
                .sortedBy { key -> key.issuer }
                .toList()

        if (foundListOfIssuers.size == 1) {
            val tokenContext = foundListOfIssuers.first()
            logger.info("Only one ISSUER found. Returning first! issuer-key: ${tokenContext.issuer}")
            return tokenContext
        }

        logger.info("More than one ISSUER found. Number of issuers found: ${foundListOfIssuers.size}")

        //hente ut første token med utløpstid
        var longestLivingToken = foundListOfIssuers.first()

        var previousTokenExpirationTime = context.getClaims(longestLivingToken.issuer).expirationTime

        //iterere igjennom alle token for å finne den med lengst utløpstid
        for(selectedToken in foundListOfIssuers) {
            val currentTokenExpirationTime = context.getClaims(selectedToken.issuer).expirationTime

            logger.debug("Compare longestTime: $previousTokenExpirationTime with nextExpirationTime: $currentTokenExpirationTime")
            if (previousTokenExpirationTime.after(currentTokenExpirationTime)) {
                logger.debug("Do nothing after?")
            } else {
                logger.debug("longestTime before nextTime. nextTime new longestTime")
                longestLivingToken = selectedToken
                previousTokenExpirationTime = currentTokenExpirationTime
            }

        }
        logger.info("Returning following issuer: ${longestLivingToken.issuer}, exp: $previousTokenExpirationTime")
        return longestLivingToken
    }

}

