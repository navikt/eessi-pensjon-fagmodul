package no.nav.eessi.pensjon.security.token

//import com.nimbusds.jwt.JWTClaimsSet
//import com.nimbusds.jwt.PlainJWT
//import no.nav.security.token.support.core.context.TokenValidationContext
//import no.nav.security.token.support.core.context.TokenValidationContextHolder
//import no.nav.security.token.support.core.jwt.JwtToken
//import org.junit.jupiter.api.Assertions.assertEquals
//import org.junit.jupiter.api.Test


//class TokenAuthorizationHeaderInterceptorTest {
//
//    private val allRequestContextHolders = generateMockContextHolder(listOf("isso","servicebruker","oidc"))
//    private lateinit var authInterceptor: TokenAuthorizationHeaderInterceptor
//
//    @Test
//    fun `gitt mulitple issuers returner servicebruker token`() {
//        authInterceptor = TokenAuthorizationHeaderInterceptor(allRequestContextHolders)
//
//        val token = authInterceptor.getTokenContextFromIssuer(allRequestContextHolders)
//        assertEquals("servicebruker", token.issuer)
//    }
//
//    @Test
//    fun `gitt mulitple issuers returner isso token`() {
//        val allMagicRequestContextHoldersIsso = generateMockContextHolder(listOf("isso","servicebruker","oidc"),true)
//
//        authInterceptor = TokenAuthorizationHeaderInterceptor(allMagicRequestContextHoldersIsso)
//
//        val token = authInterceptor.getTokenContextFromIssuer(allMagicRequestContextHoldersIsso)
//        assertEquals("isso", token.issuer)
//    }
//
//    @Test
//    fun `gitt mulitple issuers returner pesys`() {
//        val allMagicRequestContextHoldersIsso = generateMockContextHolder(listOf("pesys"))
//        authInterceptor = TokenAuthorizationHeaderInterceptor(allMagicRequestContextHoldersIsso)
//
//        val token = authInterceptor.getTokenContextFromIssuer(allMagicRequestContextHoldersIsso)
//        assertEquals("pesys", token.issuer)
//    }
//
//    @Test
//    fun `gitt mulitple issuers returner oidc token`() {
//        val allMagicRequestContextHoldersIsso = generateMockContextHolder(listOf("isso", "oidc"))
//        authInterceptor = TokenAuthorizationHeaderInterceptor(allMagicRequestContextHoldersIsso)
//
//        val token = authInterceptor.getTokenContextFromIssuer(allMagicRequestContextHoldersIsso)
//        assertEquals("oidc", token.issuer)
//    }
//
//    @Test
//    fun `gitt mulitple issuers returner isso had longest exp`() {
//        val allMagicRequestContextHoldersIsso = generateMockContextHolder(listOf("oidc","isso"), true)
//        authInterceptor = TokenAuthorizationHeaderInterceptor(allMagicRequestContextHoldersIsso)
//
//        val token = authInterceptor.getTokenContextFromIssuer(allMagicRequestContextHoldersIsso)
//        assertEquals("isso", token.issuer)
//    }
//
//    fun generateMockContextHolder(issuer: List<String>): TokenValidationContextHolder {
//        return generateMockContextHolder(issuer, false)
//    }
//
//    fun generateMockContextHolder(issuerList: List<String>, doIsso: Boolean): TokenValidationContextHolder {
//        val tokenMap = mutableMapOf<String, JwtToken>()
//            issuerList.forEach {issuer ->
//                val claimSet = JWTClaimsSet.parse(token(issuer, if (issuer == "isso" && doIsso) 1531259178 else 1531157178))
//                val jwt = PlainJWT(claimSet)
//                tokenMap[issuer] = JwtToken(jwt.serialize())
//            }
//
//        val tokenValidationContext = TokenValidationContext(tokenMap)
//
//        val tokenValidationContextHolder = MockTokenValidationContextHolder()
//        tokenValidationContextHolder.tokenValidationContext = tokenValidationContext
//        return tokenValidationContextHolder
//    }
//
//    private fun token(issuer: String, exp: Int) = """
//            {
//              "sub": "12345678910",
//              "aud": "aud-localhost",
//              "acr": "Level4",
//              "ver": "1.0",
//              "nbf": 1531144218,
//              "auth_time": 1531144218,
//              "iss": "$issuer",
//              "exp": $exp,
//              "nonce": "myNonce",
//              "iat": 1531144218,
//              "jti": "738c3268-177d-44da-8fad-08ad72580df2"
//            }
//        """.trimIndent()
//}
