package no.nav.eessi.eessifagmodul.config

import no.nav.security.oidc.context.OIDCRequestContextHolder
import no.nav.security.oidc.context.TokenContext
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse


private val logger = LoggerFactory.getLogger(OidcAuthorizationHeaderInterceptor::class.java)

class OidcAuthorizationHeaderInterceptor(private val oidcRequestContextHolder: OIDCRequestContextHolder) : ClientHttpRequestInterceptor {

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
        logger.info("sjekker reqiest header for AUTH")
        if (request.headers[HttpHeaders.AUTHORIZATION] == null) {
            val oidcToken = getIdTokenFromIssuer(oidcRequestContextHolder)
            logger.info("Adding Bearer-token to request: $oidcToken")
            request.headers[HttpHeaders.AUTHORIZATION] = "Bearer eyAidHlwIjogIkpXVCIsICJraWQiOiAiaG9ReWxXNDZpYWoyY1VjN3d4TFF2b3pLVTMwPSIsICJhbGciOiAiUlMyNTYiIH0.eyAiYXRfaGFzaCI6ICJXR0o4NlowX0hZSXlyQXo5RVdmdURRIiwgInN1YiI6ICJaOTkyMzY2IiwgImF1ZGl0VHJhY2tpbmdJZCI6ICJhNjI3MDA3Ny1hZGU2LTQ5OWUtYmE1NC0wMzI4M2Q1MjUxMDQtNjEwNDkxIiwgImlzcyI6ICJodHRwczovL2lzc28tdC5hZGVvLm5vOjQ0My9pc3NvL29hdXRoMiIsICJ0b2tlbk5hbWUiOiAiaWRfdG9rZW4iLCAibm9uY2UiOiAidlV6UnJzVzliejFYQTFUWUxlWjFUT2dQYklSaGlTRTBnYXRCS0dnSWYybyIsICJhdWQiOiAiZWVzc2ktcGVuc2pvbi1mcm9udGVuZC1hcGktZnNzLXQ4IiwgImNfaGFzaCI6ICJZdkVJMnZpTWdXZnI3eVRsdGR4Z3RnIiwgIm9yZy5mb3JnZXJvY2sub3BlbmlkY29ubmVjdC5vcHMiOiAiMDVhMmRhM2EtYTI5Yy00ODFlLTliNzItNzljYzAwMWRlYWU2IiwgImF6cCI6ICJlZXNzaS1wZW5zam9uLWZyb250ZW5kLWFwaS1mc3MtdDgiLCAiYXV0aF90aW1lIjogMTU1NzQ4MjI1MywgInJlYWxtIjogIi8iLCAiZXhwIjogMTU1NzQ4NTg1MywgInRva2VuVHlwZSI6ICJKV1RUb2tlbiIsICJpYXQiOiAxNTU3NDgyMjUzIH0.RXcKNhpRiXOiqpBjB09cGernEviZA-Z1M0W_kpAyiB_iVpDD-pesufPrF7-vVr_ANcuVGTn250FaavkQqxU1qMkzQheVxN9xdyEKV4_0KtkrNdwoxNV5cYaLOPWUNdKRV6Q-jUURXLycEnyj37Kq66mDKO23m0-ANQlqqYameJNPKz5vdpVIWz5t549LAMsz7evvIpjtTHF1sNkCkTEPNkDUtBmhTVJugBxTRquPjAIvmAqjYChCvgzLtb6RHdW_J4xm85IZWgqpAh_76iJEpAXP_7P359jWaqvd8_uF5v2AbLLrjN4I9nTlfkqWO-kQz8vSm9jfy-CSUFmdscXOnw"
        }
        return execution.execute(request, body)
    }

    fun getIdTokenFromIssuer(oidcRequestContextHolder: OIDCRequestContextHolder): String {
        return getTokenContextFromIssuer(oidcRequestContextHolder).idToken
    }

    fun getTokenContextFromIssuer(oidcRequestContextHolder: OIDCRequestContextHolder): TokenContext {
        val context = oidcRequestContextHolder.oidcValidationContext
        if (context.issuers.isEmpty()) throw RuntimeException("No issuer found in context")
        // At this point more than one issuer is not supporteted. May be changed later.
        if (context.issuers.size > 1) throw RuntimeException("More than one issuer found in context. ")

        logger.debug("Returning token on : ${context.issuers.first()}")
        return context.getToken(context.issuers.first())
    }

}

class OidcAuthorizationHeaderInterceptorSelectIssuer(private val oidcRequestContextHolder: OIDCRequestContextHolder, private val issuer: String) : ClientHttpRequestInterceptor {

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
        logger.info("sjekker reqiest header for AUTH")
        if (request.headers[HttpHeaders.AUTHORIZATION] == null) {
            val oidcToken = getIdTokenFromSelectedIssuer(oidcRequestContextHolder, issuer)
            logger.info("Adding Bearer-token to request: $oidcToken")
            request.headers[HttpHeaders.AUTHORIZATION] = "Bearer eyAidHlwIjogIkpXVCIsICJraWQiOiAiaG9ReWxXNDZpYWoyY1VjN3d4TFF2b3pLVTMwPSIsICJhbGciOiAiUlMyNTYiIH0.eyAiYXRfaGFzaCI6ICJXR0o4NlowX0hZSXlyQXo5RVdmdURRIiwgInN1YiI6ICJaOTkyMzY2IiwgImF1ZGl0VHJhY2tpbmdJZCI6ICJhNjI3MDA3Ny1hZGU2LTQ5OWUtYmE1NC0wMzI4M2Q1MjUxMDQtNjEwNDkxIiwgImlzcyI6ICJodHRwczovL2lzc28tdC5hZGVvLm5vOjQ0My9pc3NvL29hdXRoMiIsICJ0b2tlbk5hbWUiOiAiaWRfdG9rZW4iLCAibm9uY2UiOiAidlV6UnJzVzliejFYQTFUWUxlWjFUT2dQYklSaGlTRTBnYXRCS0dnSWYybyIsICJhdWQiOiAiZWVzc2ktcGVuc2pvbi1mcm9udGVuZC1hcGktZnNzLXQ4IiwgImNfaGFzaCI6ICJZdkVJMnZpTWdXZnI3eVRsdGR4Z3RnIiwgIm9yZy5mb3JnZXJvY2sub3BlbmlkY29ubmVjdC5vcHMiOiAiMDVhMmRhM2EtYTI5Yy00ODFlLTliNzItNzljYzAwMWRlYWU2IiwgImF6cCI6ICJlZXNzaS1wZW5zam9uLWZyb250ZW5kLWFwaS1mc3MtdDgiLCAiYXV0aF90aW1lIjogMTU1NzQ4MjI1MywgInJlYWxtIjogIi8iLCAiZXhwIjogMTU1NzQ4NTg1MywgInRva2VuVHlwZSI6ICJKV1RUb2tlbiIsICJpYXQiOiAxNTU3NDgyMjUzIH0.RXcKNhpRiXOiqpBjB09cGernEviZA-Z1M0W_kpAyiB_iVpDD-pesufPrF7-vVr_ANcuVGTn250FaavkQqxU1qMkzQheVxN9xdyEKV4_0KtkrNdwoxNV5cYaLOPWUNdKRV6Q-jUURXLycEnyj37Kq66mDKO23m0-ANQlqqYameJNPKz5vdpVIWz5t549LAMsz7evvIpjtTHF1sNkCkTEPNkDUtBmhTVJugBxTRquPjAIvmAqjYChCvgzLtb6RHdW_J4xm85IZWgqpAh_76iJEpAXP_7P359jWaqvd8_uF5v2AbLLrjN4I9nTlfkqWO-kQz8vSm9jfy-CSUFmdscXOnw"
        }
        return execution.execute(request, body)
    }

    fun getIdTokenFromSelectedIssuer(oidcRequestContextHolder: OIDCRequestContextHolder, issuer: String): String {
        return getTokenContextFromSelectedIssuer(oidcRequestContextHolder, issuer).idToken
    }

    fun getTokenContextFromSelectedIssuer(oidcRequestContextHolder: OIDCRequestContextHolder, issuer: String): TokenContext {
        val context = oidcRequestContextHolder.oidcValidationContext
        if (context.issuers.isEmpty()) throw RuntimeException("No issuer found in context")
        // At this point more than one, select one to use.
        logger.debug("Returning token on issuer: $issuer with token: ${context.getToken(issuer)}")
        return context.getToken(issuer)

    }
}




