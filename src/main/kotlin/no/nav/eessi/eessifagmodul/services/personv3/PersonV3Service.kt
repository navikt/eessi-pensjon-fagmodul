package no.nav.eessi.eessifagmodul.services.personv3

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.eessi.eessifagmodul.config.sts.configureRequestSamlTokenOnBehalfOfOidc
import no.nav.eessi.eessifagmodul.controllers.SedController
import no.nav.eessi.eessifagmodul.models.PersonV3IkkeFunnetException
import no.nav.eessi.eessifagmodul.models.PersonV3SikkerhetsbegrensningException
import no.nav.security.oidc.context.OIDCRequestContextHolder
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonPersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonSikkerhetsbegrensning
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Informasjonsbehov
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningRequest
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningResponse
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonRequest
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private val logger = LoggerFactory.getLogger(SedController::class.java)

@Component
class PersonV3Service(val service: PersonV3, val oidcRequestContextHolder: OIDCRequestContextHolder) {

    private val hentperson_teller_navn = "eessipensjon_fagmodul.hentperson"
    private val hentperson_teller_type_vellykkede = counter(hentperson_teller_navn, "vellykkede")
    private val hentperson_teller_type_feilede = counter(hentperson_teller_navn, "feilede")

    final fun counter(name: String, type: String): Counter {
        return Metrics.counter(name, "type", type)
    }

    fun hentPerson(fnr: String): HentPersonResponse {
        val token = oidcRequestContextHolder.oidcValidationContext.getToken("oidc")

        configureRequestSamlTokenOnBehalfOfOidc(service, token.idToken)

        val request = HentPersonRequest().apply {
            withAktoer(PersonIdent().withIdent(
                    NorskIdent().withIdent(fnr)))

            withInformasjonsbehov(listOf(
                    Informasjonsbehov.ADRESSE,
                    Informasjonsbehov.FAMILIERELASJONER
            ))
        }
        try {
            logger.info("Kaller PersonV3.hentPerson")
            val resp = service.hentPerson(request)
            hentperson_teller_type_vellykkede.increment()
            return resp
        } catch (personIkkefunnet : HentPersonPersonIkkeFunnet) {
            hentperson_teller_type_feilede.increment()
            throw PersonV3IkkeFunnetException(personIkkefunnet.message)
        } catch (personSikkerhetsbegrensning: HentPersonSikkerhetsbegrensning) {
            hentperson_teller_type_feilede.increment()
            throw PersonV3SikkerhetsbegrensningException(personSikkerhetsbegrensning.message)
        }
    }

    //Experimental only
    fun hentGeografi(fnr: String): HentGeografiskTilknytningResponse {

        val token = oidcRequestContextHolder.oidcValidationContext.getToken("oidc")
        configureRequestSamlTokenOnBehalfOfOidc(service, token.idToken)

        val request = HentGeografiskTilknytningRequest().apply {
            withAktoer(PersonIdent().withIdent(
                    NorskIdent().withIdent(fnr))
            )
        }
        return service.hentGeografiskTilknytning(request)
    }

}