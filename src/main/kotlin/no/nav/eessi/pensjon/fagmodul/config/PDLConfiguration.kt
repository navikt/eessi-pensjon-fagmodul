package no.nav.eessi.pensjon.fagmodul.config

import no.nav.common.token_client.builder.AzureAdTokenClientBuilder
import no.nav.common.token_client.client.AzureAdOnBehalfOfTokenClient
import no.nav.eessi.pensjon.personoppslag.pdl.PdlToken
import no.nav.eessi.pensjon.personoppslag.pdl.PdlTokenCallBack
import no.nav.eessi.pensjon.personoppslag.pdl.PdlTokenImp
import no.nav.eessi.pensjon.utils.getToken
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Primary
@Profile("prod", "test")
@Component("PdlTokenComponent")
@Order(Ordered.HIGHEST_PRECEDENCE)
class PDLConfiguration(private val tokenValidationContextHolder: TokenValidationContextHolder): PdlTokenCallBack {

    @Value("\${AZURE_APP_PDL_CLIENT_ID}")
    private lateinit var pdlClientId: String

    override fun callBack(): PdlToken {

        val navidentTokenFromUI = getToken(tokenValidationContextHolder).tokenAsString

        val tokenClient: AzureAdOnBehalfOfTokenClient = AzureAdTokenClientBuilder.builder()
            .withNaisDefaults()
            .buildOnBehalfOfTokenClient()

        val accessToken: String = tokenClient.exchangeOnBehalfOfToken(
            "api://$pdlClientId/.default",
            navidentTokenFromUI
        )
        println("PDL token on Behalf: $accessToken")

        return PdlTokenImp(accessToken)
    }

}

