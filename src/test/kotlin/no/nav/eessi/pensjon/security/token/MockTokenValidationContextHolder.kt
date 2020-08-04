package no.nav.eessi.pensjon.security.oidc

import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.core.context.TokenValidationContextHolder

class MockTokenValidationContextHolder : TokenValidationContextHolder {

    private var tokenValidationContext : TokenValidationContext? = null

    override fun getTokenValidationContext(): TokenValidationContext {
        return this.tokenValidationContext!!
    }

    override fun setTokenValidationContext(tokenValidationContext: TokenValidationContext?) {
        this.tokenValidationContext = tokenValidationContext
    }
}