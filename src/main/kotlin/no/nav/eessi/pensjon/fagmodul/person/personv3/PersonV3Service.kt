package no.nav.eessi.pensjon.fagmodul.person.personv3

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.fagmodul.metrics.TimingService
import no.nav.eessi.pensjon.fagmodul.security.sts.configureRequestSamlToken
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus
import java.lang.Exception

@Component
class PersonV3Service(private val service: PersonV3,
                      private val timingService: TimingService) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PersonV3Service::class.java) }

    private val hentperson_teller_navn = "eessipensjon_fagmodul.hentperson"
    private val hentperson_teller_type_vellykkede = counter(hentperson_teller_navn, "vellykkede")
    private val hentperson_teller_type_feilede = counter(hentperson_teller_navn, "feilede")

    final fun counter(name: String, type: String): Counter {
        return Metrics.counter(name, "type", type)
    }

    fun hentPersonPing(): Boolean {
        logger.info("Henter person fra PersonV3Service")
        configureRequestSamlToken(service)
        return try {
            service.ping()
            true
        } catch (ex: Exception) {
            logger.warn("Får ikke kontakt med tjeneste PersonV3 ping")
            throw ex
        }
    }

    fun hentPerson(fnr: String): HentPersonResponse {
        logger.info("Henter person fra PersonV3Service")
        configureRequestSamlToken(service)

        val request = HentPersonRequest().apply {
            withAktoer(PersonIdent().withIdent(
                    NorskIdent().withIdent(fnr)))

            withInformasjonsbehov(listOf(
                    Informasjonsbehov.ADRESSE,
                    Informasjonsbehov.FAMILIERELASJONER
            ))
        }
        val persontimed = timingService.timedStart("personV3")
        try {
            logger.info("Kaller PersonV3.hentPerson service")
            val resp = service.hentPerson(request)
            hentperson_teller_type_vellykkede.increment()
            timingService.timesStop(persontimed)
            return resp
        } catch (personIkkefunnet : HentPersonPersonIkkeFunnet) {
            logger.error("Kaller PersonV3.hentPerson service Feilet")
            timingService.timesStop(persontimed)
            hentperson_teller_type_feilede.increment()
            throw PersonV3IkkeFunnetException(personIkkefunnet.message)
        } catch (personSikkerhetsbegrensning: HentPersonSikkerhetsbegrensning) {
            logger.error("Kaller PersonV3.hentPerson service Feilet")
            timingService.timesStop(persontimed)
            hentperson_teller_type_feilede.increment()
            throw PersonV3SikkerhetsbegrensningException(personSikkerhetsbegrensning.message)
        }
    }

    //Experimental only
    fun hentGeografi(fnr: String): HentGeografiskTilknytningResponse {

        configureRequestSamlToken(service)

        val request = HentGeografiskTilknytningRequest().apply {
            withAktoer(PersonIdent().withIdent(
                    NorskIdent().withIdent(fnr))
            )
        }
        return service.hentGeografiskTilknytning(request)
    }

}

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class PersonV3IkkeFunnetException(message: String?): Exception(message)

@ResponseStatus(value = HttpStatus.FORBIDDEN)
class PersonV3SikkerhetsbegrensningException(message: String?): Exception(message)