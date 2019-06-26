package no.nav.eessi.eessifagmodul.person

import io.swagger.annotations.ApiOperation
import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.services.aktoerregister.AktoerregisterService
import no.nav.eessi.eessifagmodul.services.personv3.PersonV3Service
import no.nav.eessi.eessifagmodul.metrics.getCounter
import no.nav.security.oidc.api.Protected
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val logger = LoggerFactory.getLogger(NavRegistreOppslagController::class.java)

/**
 * Controller for å kalle NAV interne registre
 *
 * @property aktoerregisterService
 * @property personService
 */
@Protected
@RestController
class NavRegistreOppslagController(private val aktoerregisterService: AktoerregisterService,
                                private val personService: PersonV3Service) {

    /**
     * Kaller AktørRegisteret , bytter aktørId mot Fnr/Dnr ,
     * deretter kalles PersonV3 hvor personinformasjon hentes
     *
     * @param aktoerid
     */
    @ApiOperation("henter ut personinformasjon for en aktørId")
    @GetMapping("/personinfo/{aktoerid}")
    fun getDocument(@PathVariable("aktoerid", required = true) aktoerid: String): ResponseEntity<Personinformasjon> {
        logger.info("Henter personinformasjon for aktørId")

        val norskIdent: String
        var personresp = HentPersonResponse()

        try {
            norskIdent = aktoerregisterService.hentGjeldendeNorskIdentForAktorId(aktoerid)
            personresp = personService.hentPerson(norskIdent)

        } catch (are: AktoerregisterException) {
            logger.error("Kall til Akørregisteret feilet på grunn av: " + are.message)
            getCounter("PERSONINFORMASJONFEIL").increment()
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(AktoerregisterException::class.simpleName)
        } catch (arife: AktoerregisterIkkeFunnetException) {
            logger.error("Kall til Akørregisteret feilet på grunn av: " + arife.message)
            getCounter("PERSONINFORMASJONFEIL").increment()
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(AktoerregisterException::class.simpleName)
        } catch (sbe: PersonV3SikkerhetsbegrensningException) {
            logger.error("Kall til PersonV3 med feilet på grunn av sikkerhetsbegrensning")
            getCounter("PERSONINFORMASJONFEIL").increment()
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(PersonV3SikkerhetsbegrensningException::class.simpleName)
        } catch (ife: PersonV3IkkeFunnetException) {
            logger.error("Kall til PersonV3 feilet på grunn av person ikke funnet")
            getCounter("PERSONINFORMASJONFEIL").increment()
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(PersonV3IkkeFunnetException::class.simpleName)
        }

        getCounter("PERSONINFORMASJONOK").increment()
        return ResponseEntity.ok(Personinformasjon(personresp.person.personnavn.sammensattNavn,
                personresp.person.personnavn.fornavn,
                personresp.person.personnavn.mellomnavn,
                personresp.person.personnavn.etternavn))
    }
}

