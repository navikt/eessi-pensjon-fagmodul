package no.nav.eessi.pensjon.fagmodul.config

import no.nav.eessi.pensjon.personoppslag.pdl.PdlToken
import no.nav.eessi.pensjon.personoppslag.pdl.PdlTokenCallBack
import no.nav.eessi.pensjon.personoppslag.pdl.PdlTokenImp
import no.nav.eessi.pensjon.security.sts.STSService
import no.nav.eessi.pensjon.security.token.TokenAuthorizationHeaderInterceptor
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component


@Component("PdlTokenComponent")
@Primary
@Order(Ordered.HIGHEST_PRECEDENCE)
class PdlFagmodulTokenComponent(private val token: TokenValidationContextHolder, private val securityTokenExchangeService: STSService): PdlTokenCallBack {

    override fun callBack(): PdlToken {
        return PdlUserToken(token, securityTokenExchangeService).callBack()
    }
}

internal class PdlUserToken(private val token: TokenValidationContextHolder, private val securityTokenExchangeService: STSService): PdlTokenCallBack {
    private val logger = LoggerFactory.getLogger(PdlUserToken::class.java)
    override fun callBack(): PdlToken {
        logger.info("PdlTokenCallBack: Usertoken")
        val systemToken = securityTokenExchangeService.getSystemOidcToken()
        val userToken =  TokenAuthorizationHeaderInterceptor(token).getIdTokenFromIssuer(token)
        return PdlTokenImp(systemToken = systemToken, userToken = userToken, isUserToken = true)
    }
}

