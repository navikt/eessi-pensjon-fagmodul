package no.nav.eessi.pensjon.fagmodul.config

import no.nav.common.token_client.builder.AzureAdTokenClientBuilder
import no.nav.common.token_client.client.AzureAdOnBehalfOfTokenClient
import no.nav.eessi.pensjon.personoppslag.pdl.PdlConfiguration
import no.nav.eessi.pensjon.personoppslag.pdl.PdlToken
import no.nav.eessi.pensjon.personoppslag.pdl.PdlTokenCallBack
import no.nav.eessi.pensjon.personoppslag.pdl.PdlTokenImp
import no.nav.eessi.pensjon.utils.getToken
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.client.RestTemplate
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Profile("prod", "test")
@Configuration
class PDLConfiguration(private val tokenValidationContextHolder: TokenValidationContextHolder,
                       @Value("\${AZURE_APP_PDL_CLIENT_ID}") private val pdlClientId: String): PdlTokenCallBack {

    override fun callBack(): PdlToken {

        val decodedToken = URLDecoder.decode(getToken(tokenValidationContextHolder).encodedToken, StandardCharsets.UTF_8)

        val tokenClient: AzureAdOnBehalfOfTokenClient = AzureAdTokenClientBuilder.builder()
            .withNaisDefaults()
            .buildOnBehalfOfTokenClient()

        val accessToken: String = tokenClient.exchangeOnBehalfOfToken(
            "api://$pdlClientId/.default",
            decodedToken
        )
        return PdlTokenImp(accessToken)
    }

    @Bean
    fun pdlRestTemplate(): RestTemplate {
        return PdlConfiguration().pdlRestTemplate(this)
    }
}

