package no.nav.eessi.pensjon.fagmodul.config

import no.nav.security.token.support.test.spring.TokenGeneratorConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile

@Import(TokenGeneratorConfiguration::class)
@Configuration
@Profile("local-test")
class LocalTokenConfig