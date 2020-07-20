package no.nav.eessi.pensjon.api.person

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.swagger.annotations.ApiOperation
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonClient
import no.nav.security.oidc.api.Protected
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import javax.annotation.PostConstruct

/**
 * Controller for å kalle NAV interne registre
 *
 * @property aktoerregisterService
 * @property personService
 * @property pensjonsinformasjonClient
 */
@Protected
@RestController
class PersonController(private val aktoerregisterService: AktoerregisterService,
                       private val personService: PersonV3Service,
                       private val auditLogger: AuditLogger,
                       private val pensjonsinformasjonClient: PensjonsinformasjonClient,
                       @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger = LoggerFactory.getLogger(PersonController::class.java)

    private lateinit var PersonControllerHentPerson: MetricsHelper.Metric
    private lateinit var PersonControllerHentPersonNavn: MetricsHelper.Metric
    private lateinit var PersonControllerHentPersonAvdod: MetricsHelper.Metric


    @PostConstruct
    fun initMetrics() {
        PersonControllerHentPerson = metricsHelper.init("PersonControllerHentPerson")
        PersonControllerHentPersonNavn = metricsHelper.init("PersonControllerHentPersonNavn")
        PersonControllerHentPersonAvdod = metricsHelper.init("PersonControllerHentPersonAvdod")

    }

    @ApiOperation("henter ut personinformasjon for en aktørId")
    @GetMapping("/person/{aktoerid}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getPerson(@PathVariable("aktoerid", required = true) aktoerid: String): ResponseEntity<Any> {
        auditLogger.log("/person/{$aktoerid}", "getPerson")

        return PersonControllerHentPerson.measure {
            val person = hentPerson(aktoerid)
            ResponseEntity.ok(person)
        }
    }

    @ApiOperation("henter ut alle avdøde for en aktørId og vedtaksId der aktør er gjenlevende")
    @GetMapping("/person/avdode/{aktoerId}/vedtak/{vedtaksId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getDeceased(@PathVariable("aktoerId", required = true) aktoerId: String,
                    @PathVariable("vedtaksId", required = true) vedtaksId: String): ResponseEntity<Any> {

        auditLogger.log("/person/{$aktoerId}/vetak", "getDeceased")

        val peninfo = pensjonsinformasjonClient.hentAltPaaVedtak(vedtaksId)

        val avdodInfo = peninfo.avdod
        val person = peninfo.person

        val hentAlleAvdode = hentAlleAvdode(avdodInfo.avdodFarAktorId, avdodInfo.avdodMorAktorId)

        if (hentAlleAvdode.isNotEmpty() && person.aktorId == aktoerId) {
            return PersonControllerHentPersonAvdod.measure {

                ResponseEntity.ok( hentAlleAvdode.map { it
                    val fnr = aktoerregisterService.hentGjeldendeNorskIdentForAktorId(it.toString())
                    val select = hentPerson(it)

                    val person = select.person
                    PersoninformasjonAvdode(
                            fnr,
                            person.personnavn.sammensattNavn,
                            person.personnavn.fornavn,
                            person.personnavn.mellomnavn,
                            person.personnavn.etternavn)

                }.toList())
            }
        }
        return PersonControllerHentPersonAvdod.measure {
                ResponseEntity.notFound().build()
        }
    }

    private fun hentAlleAvdode(avdodMor: String?, avdodFar: String?): List<String> {

        var avdodeAktorIdList = mutableListOf<String>()

        if(isNumber(avdodMor)) {
            avdodeAktorIdList.add(avdodMor.toString())
        }

        if(isNumber(avdodFar)) {
            avdodeAktorIdList.add(avdodFar.toString())
        }

        return avdodeAktorIdList
    }

    fun isNumber(s: String?): Boolean {
        return if (s.isNullOrEmpty()) false else s.all { Character.isDigit(it) }
    }

    @ApiOperation("henter ut navn for en aktørId")
    @GetMapping("/personinfo/{aktoerid}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getNameOnly(@PathVariable("aktoerid", required = true) aktoerid: String): ResponseEntity<Personinformasjon> {
        auditLogger.log("/personinfo/{$aktoerid}", "getNameOnly")

        return PersonControllerHentPersonNavn.measure {
            val person = hentPerson(aktoerid).person
            ResponseEntity.ok(
                    Personinformasjon(person.personnavn.sammensattNavn,
                            person.personnavn.fornavn,
                            person.personnavn.mellomnavn,
                            person.personnavn.etternavn)
            )
        }
    }

    private fun hentPerson(aktoerid: String): HentPersonResponse {
        logger.info("Henter personinformasjon for aktørId: $aktoerid")
        val norskIdent: String = aktoerregisterService.hentGjeldendeNorskIdentForAktorId(aktoerid)
        return personService.hentPersonResponse(norskIdent)
    }

    /**
     * Personinformasjon fra TPS ( PersonV3 )
     */
    data class Personinformasjon(var fulltNavn: String? = null,
                                 var fornavn: String? = null,
                                 var mellomnavn: String? = null,
                                 var etternavn: String? = null)

    data class PersoninformasjonAvdode (var fnd: String? = null,
                                        var fulltNavn: String? = null,
                                        var fornavn: String? = null,
                                        var mellomnavn: String? = null,
                                        var etternavn: String? = null)
}
