package no.nav.eessi.eessifagmodul.config

import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import no.nav.security.oidc.context.OIDCClaims
import no.nav.security.oidc.context.OIDCRequestContextHolder
import no.nav.security.oidc.context.OIDCValidationContext
import no.nav.security.oidc.context.TokenContext
import org.apache.commons.io.FileUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.io.File
import java.nio.charset.Charset
import kotlin.test.assertFailsWith

@RunWith(MockitoJUnitRunner::class)
class OidcAuthorizationHeaderInterceptorKtTest {

    private val oidcRequestContextHolder = generateMockContextHolder(listOf("oidc"))
    private val pesysRequestContextHolder = generateMockContextHolder(listOf("pesys"))
    private val mulitpleRequestContextHolders = generateMockContextHolder(listOf("pesys", "oidc"))
    private val allMagicRequestContextHolders = generateMockContextHolder(listOf("isso","pesys","oidc"))

    private lateinit var authInterceptor: OidcAuthorizationHeaderInterceptor
    private lateinit var authInterceptorIssuer: OidcAuthorizationHeaderInterceptorSelectIssuer
    private lateinit var authInterceptorMagic: OidcAuthorizationHeaderInterceptorMagic

    @Test fun `gitt issuer oidc returner gyldig oidcIdToken`() {
        authInterceptor = OidcAuthorizationHeaderInterceptor(oidcRequestContextHolder)
        assertEquals("oidcIdToken", authInterceptor.getIdTokenFromIssuer(oidcRequestContextHolder))
    }

    @Test fun `gitt issuer pesys returner gyldig pesysIdToken`() {
        authInterceptor = OidcAuthorizationHeaderInterceptor(pesysRequestContextHolder)
        assertEquals("pesysIdToken", authInterceptor.getIdTokenFromIssuer(pesysRequestContextHolder))
    }

    @Test fun `gitt mulitple issuers returner RuntimeException`() {
        authInterceptor = OidcAuthorizationHeaderInterceptor(mulitpleRequestContextHolders)
        assertFailsWith(RuntimeException::class) { authInterceptor.getIdTokenFromIssuer(mulitpleRequestContextHolders) }
    }

    @Test
    fun `gitt mulitple issuers returner selected ussuer pesys`() {
        authInterceptorIssuer = OidcAuthorizationHeaderInterceptorSelectIssuer(mulitpleRequestContextHolders, "pesys")
        assertEquals("pesysIdToken", authInterceptorIssuer.getIdTokenFromSelectedIssuer(mulitpleRequestContextHolders, "pesys"))
    }

    @Test
    fun `gitt mulitple issuers returner selected issuer oidc`() {
        authInterceptorIssuer = OidcAuthorizationHeaderInterceptorSelectIssuer(mulitpleRequestContextHolders, "oidc")
        assertEquals("oidcIdToken", authInterceptorIssuer.getIdTokenFromSelectedIssuer(mulitpleRequestContextHolders, "oidc"))
    }

    @Test
    fun `gitt magic mulitple issuers returner pesysIdToken token`() {
        authInterceptorMagic = OidcAuthorizationHeaderInterceptorMagic(allMagicRequestContextHolders)

        val tokenfromIssuer = authInterceptorMagic.getIdTokenFromIssuer(allMagicRequestContextHolders)
        assertEquals("pesysIdToken", tokenfromIssuer)
    }

    @Test
    fun `gitt magic mulitple issuers returner issoIdToken token`() {
        val allMagicRequestContextHoldersIsso = generateMockContextHolder(listOf("isso","pesys","oidc"),true)

        authInterceptorMagic = OidcAuthorizationHeaderInterceptorMagic(allMagicRequestContextHoldersIsso)

        val tokenfromIssuer = authInterceptorMagic.getIdTokenFromIssuer(allMagicRequestContextHoldersIsso)
        assertEquals("issoIdToken", tokenfromIssuer)
    }

    @Test
    fun `gitt magic mulitple issuers returner pesys`() {
        val allMagicRequestContextHoldersIsso = generateMockContextHolder(listOf("pesys"))
        authInterceptorMagic = OidcAuthorizationHeaderInterceptorMagic(allMagicRequestContextHoldersIsso)

        val tokenfromIssuer = authInterceptorMagic.getIdTokenFromIssuer(allMagicRequestContextHoldersIsso)
        assertEquals("pesysIdToken", tokenfromIssuer)
    }

    @Test
    fun `gitt magic mulitple issuers returner oidc`() {
        val allMagicRequestContextHoldersIsso = generateMockContextHolder(listOf("oidc","isso"))
        authInterceptorMagic = OidcAuthorizationHeaderInterceptorMagic(allMagicRequestContextHoldersIsso)

        val tokenfromIssuer = authInterceptorMagic.getIdTokenFromIssuer(allMagicRequestContextHoldersIsso)
        assertEquals("oidcIdToken", tokenfromIssuer)
    }

    @Test
    fun `gitt magic mulitple issuers returner isso had longest exp`() {
        val allMagicRequestContextHoldersIsso = generateMockContextHolder(listOf("oidc","isso"), true)
        authInterceptorMagic = OidcAuthorizationHeaderInterceptorMagic(allMagicRequestContextHoldersIsso)

        val tokenfromIssuer = authInterceptorMagic.getIdTokenFromIssuer(allMagicRequestContextHoldersIsso)
        assertEquals("issoIdToken", tokenfromIssuer)
    }

    fun generateMockContextHolder(issuer: List<String>): OIDCRequestContextHolder {
        return generateMockContextHolder(issuer, false)
    }

    fun generateMockContextHolder(issuerList: List<String>, doIsso: Boolean): OIDCRequestContextHolder {
        val oidcContext = OIDCValidationContext()
        val oidcContextHolder = MockOIDCRequestContextHolder()

        val claimSetNormal: JWTClaimsSet = JWTClaimsSet.parse(FileUtils.readFileToString(File("src/test/resources/json/jwtExample.json"), Charset.forName("UTF-8")))
        val claimSetISSO: JWTClaimsSet = JWTClaimsSet.parse(FileUtils.readFileToString(File("src/test/resources/json/jwtISSOExample.json"), Charset.forName("UTF-8")))

        issuerList.iterator().forEach {
            val issuer = it
            val idToken = issuer + "IdToken"
            val tokenContext = TokenContext(issuer, idToken)
            val claimSet = generateMockClaimSet(it, doIsso, claimSetNormal, claimSetISSO)
            val jwt = PlainJWT(claimSet)
            oidcContext.addValidatedToken(issuer, tokenContext, OIDCClaims(jwt))
        }
        oidcContextHolder.setOIDCValidationContext(oidcContext)
        return oidcContextHolder
    }

    fun generateMockClaimSet(issuer: String, doIsso: Boolean, normalClaim: JWTClaimsSet, issoClaim: JWTClaimsSet): JWTClaimsSet {
        return if (doIsso && issuer == "isso") {
            issoClaim
        } else {
            normalClaim
        }
    }
}