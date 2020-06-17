package no.nav.eessi.pensjon.fagmodul.prefill.tps

import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class TpsPersonService(private val personV3Service: PersonV3Service) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(TpsPersonService::class.java) }

    fun hentBrukerFraTPS(ident: String) =
            try {
                personV3Service.hentPerson(ident).person as Bruker
            } catch (ex: Exception) {
                logger.error("Feil ved henting av Bruker fra TPS, sjekk ident?")
                null
            }
}
