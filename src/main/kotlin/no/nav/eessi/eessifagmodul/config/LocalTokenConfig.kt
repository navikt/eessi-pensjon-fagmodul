package no.nav.eessi.eessifagmodul.config

import no.nav.security.oidc.test.support.spring.TokenGeneratorConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile

@Import(TokenGeneratorConfiguration::class)
@Configuration
@Profile("local")
class LocalTokenConfig {}