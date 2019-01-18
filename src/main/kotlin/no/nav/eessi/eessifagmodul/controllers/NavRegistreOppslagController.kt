import io.swagger.annotations.ApiOperation
import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.services.aktoerregister.AktoerregisterService
import no.nav.eessi.eessifagmodul.services.personv3.PersonV3Service
import no.nav.security.oidc.api.Protected
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val logger = LoggerFactory.getLogger(RegisterOppslagController::class.java)

@Protected
@RestController

/**
 * Controller for å kalle NAV interne registre
 *
 * @property aktoerregisterService
 * @property personService
 */
class RegisterOppslagController(private val aktoerregisterService: AktoerregisterService,
                                private val personService: PersonV3Service) {

    /**
     * Kaller AktørRegisteret , bytter aktørId mot Fnr/Dnr ,
     * deretter kalles PersonV3 hvor personinformasjon hentes
     *
     * @param aktoerid
     */
    @ApiOperation("henter ut personinformasjon for en aktørId")
    @GetMapping("/person/{aktoerid}")
    fun getDocument(@PathVariable("aktoerid", required = true) aktoerid: String): ResponseEntity<Personinformasjon> {
        logger.info("Henter personinformasjon for aktørId: $aktoerid")

        val norskIdent: String
        var personresp = HentPersonResponse()

        try {
            norskIdent = aktoerregisterService.hentGjeldendeNorskIdentForAktorId(aktoerid)
            personresp = personService.hentPerson(norskIdent)

        } catch (are: AktoerregisterException) {
            logger.error("Kall til Akørregisteret med aktørId: $aktoerid feilet på grunn av: " + are.message)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(AktoerregisterException::class.simpleName)
        } catch (arife: AktoerregisterIkkeFunnetException) {
            logger.error("Kall til Akørregisteret med aktørId: $aktoerid feilet på grunn av: " + arife.message)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(AktoerregisterException::class.simpleName)
        } catch (sbe: PersonV3SikkerhetsbegrensningException) {
            logger.error("Kall til PersonV3 med aktørId: $aktoerid feilet på grunn av sikkerhetsbegrensning")
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(PersonV3SikkerhetsbegrensningException::class.simpleName)
        } catch (ife: PersonV3IkkeFunnetException) {
            logger.error("Kall til PersonV3 med aktørId: $aktoerid feilet på grunn av person ikke funnet")
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(PersonV3IkkeFunnetException::class.simpleName)
        }
        return ResponseEntity.ok(Personinformasjon(personresp.person.personnavn.sammensattNavn))
    }
}

