package no.nav.eessi.pensjon.api.person

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.swagger.annotations.ApiOperation
import no.nav.eessi.pensjon.fagmodul.models.FamilieRelasjonType
import no.nav.eessi.pensjon.fagmodul.models.FamilieRelasjonType.FAR
import no.nav.eessi.pensjon.fagmodul.models.FamilieRelasjonType.MOR
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerId
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterIkkeFunnetException
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.aktoerregister.ManglerAktoerIdException
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Familierelasjonsrolle
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentType
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonClient
import no.nav.security.token.support.core.api.Protected
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import javax.annotation.PostConstruct
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId as AktoerPDLId
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person as PersonPDL

/**
 * Controller for å kalle NAV interne registre
 *
 * @property aktoerregisterService
 * @property personService
 * @property pensjonsinformasjonClient
 */
@Protected
@RestController
class PersonController(
    private val aktoerregisterService: AktoerregisterService,
    private val personService: PersonV3Service,
    private val pdlService: PersonService,
    private val auditLogger: AuditLogger,
    private val pensjonsinformasjonClient: PensjonsinformasjonClient,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

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

    @Profile("test")
    @GetMapping("/person/{fnr}/{tpsboolean}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun gerPersonFraBegge(
        @PathVariable("fnr", required = true) fnr: String,
        @PathVariable("tpsboolean", required = true) tps: Boolean
    ): Any? {

        return if (tps) {
            logger.debug("henter person fra TPS")
            hentPersonTps(fnr).person
        } else {
            logger.debug("henter person fra PDL")
            pdlService.hentPerson(NorskIdent(fnr))
        }

    }

    @ApiOperation("henter ut personinformasjon fra pdl for en aktørId")
    @GetMapping("/pdl/person/{aktoerid}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getPDLPerson(@PathVariable("aktoerid", required = true) aktoerid: String): ResponseEntity<PersonPDL?> {

        val fnr = pdlService.hentIdent(IdentType.NorskIdent, AktoerPDLId(aktoerid)).id
        try {
            return ResponseEntity.ok().body(pdlService.hentPerson(NorskIdent(fnr)))
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Feil! ${ex.message}")
        }

    }

    @ApiOperation("henter ut alle avdøde for en aktørId og vedtaksId der aktør er gjenlevende")
    @GetMapping("/person/{aktoerId}/avdode/vedtak/{vedtaksId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getDeceased(
        @PathVariable("aktoerId", required = true) gjenlevendeAktoerId: String,
        @PathVariable("vedtaksId", required = true) vedtaksId: String
    ): ResponseEntity<Any> {

        logger.debug("Henter informasjon om avdøde $gjenlevendeAktoerId fra vedtak $vedtaksId")
        auditLogger.log("/person/{$gjenlevendeAktoerId}/vedtak", "getDeceased")

        return PersonControllerHentPersonAvdod.measure {

            val pensjonInfo = pensjonsinformasjonClient.hentAltPaaVedtak(vedtaksId)

            val gjenlevende = hentPerson(gjenlevendeAktoerId).person as Person

            val avdode = mapOf(
                pensjonInfo.avdod?.avdod to null,
                pensjonInfo.avdod?.avdodFar to FAR,
                pensjonInfo.avdod?.avdodMor to MOR
            )

            val avdodeMedFnr = avdode
                .filter { (fnr, _) -> isNumber(fnr) }
                .map { (fnr, rolle) -> pairPersonFnr(fnr!!, rolle, gjenlevende)}

            logger.info("Det ble funnet ${avdodeMedFnr.size} avdøde for den gjenlevende med aktørID: $gjenlevendeAktoerId")

            ResponseEntity.ok(avdodeMedFnr)
        }
    }

    @ApiOperation("henter ut alle avdøde for en aktørId og vedtaksId der aktør er gjenlevende")
    @GetMapping("/personpdl/{aktoerId}/avdode/vedtak/{vedtaksId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getDeceasedPDL(
        @PathVariable("aktoerId", required = true) gjenlevendeAktoerId: String,
        @PathVariable("vedtaksId", required = true) vedtaksId: String
    ): ResponseEntity<Any> {

        logger.debug("Henter informasjon om avdøde $gjenlevendeAktoerId fra vedtak $vedtaksId")
        auditLogger.log("/person/{$gjenlevendeAktoerId}/vedtak", "getDeceased")

        return PersonControllerHentPersonAvdod.measure {

        val pensjonInfo = pensjonsinformasjonClient.hentAltPaaVedtak(vedtaksId)
        val gjenlevende = pdlService.hentPerson(AktoerPDLId(gjenlevendeAktoerId))

        val avdode = mapOf(
            pensjonInfo.avdod?.avdod to null,
            pensjonInfo.avdod?.avdodFar to Familierelasjonsrolle.FAR,
            pensjonInfo.avdod?.avdodMor to Familierelasjonsrolle.MOR
        )

        val avdodeMedFnr = avdode
            .filter { (fnr, _) -> isNumber(fnr) }
            .map { (fnr, rolle) -> pairPersonPDLFnr(fnr!!, rolle, gjenlevende)}

        logger.info("Det ble funnet ${avdodeMedFnr.size} avdøde for den gjenlevende med aktørID: $gjenlevendeAktoerId")

            ResponseEntity.ok(avdodeMedFnr)
        }
    }

    private fun pairPersonPDLFnr(
        avdodFnr: String,
        avdodRolle: Familierelasjonsrolle?,
        gjenlevende: PersonPDL?
    ): PersoninformasjonAvdode {

        val avdode = pdlService.hentPerson(NorskIdent(avdodFnr))
        val avdodNavn = avdode?.navn

        val relasjon = avdodRolle ?: gjenlevende?.sivilstand?.firstOrNull { it.relatertVedSivilstand == avdodFnr }?.type
        return PersoninformasjonAvdode(
            fnr = avdodFnr,
            fulltNavn = avdodNavn?.sammensattNavn,
            fornavn = avdodNavn?.fornavn,
            mellomnavn = avdodNavn?.mellomnavn,
            etternavn = avdodNavn?.etternavn,
            relasjon = relasjon?.name
        )
    }


    private fun pairPersonFnr(avdodFnr: String, avdodRolle: FamilieRelasjonType?, gjenlevende: Person?): PersoninformasjonAvdode {

        val avdode = personService.hentBruker(avdodFnr)
        val avdodNavn = avdode?.personnavn

        val relasjon = if (avdodRolle == null) {
            val familierelasjon =
                gjenlevende?.harFraRolleI?.firstOrNull { (it.tilPerson.aktoer as PersonIdent).ident.ident == avdodFnr }
            familierelasjon?.tilRolle?.value?.toUpperCase()
        } else {
            avdodRolle.name
        }

        return PersoninformasjonAvdode(
            fnr = avdodFnr,
            fulltNavn = avdodNavn?.sammensattNavn,
            fornavn = avdodNavn?.fornavn,
            mellomnavn = avdodNavn?.mellomnavn,
            etternavn = avdodNavn?.etternavn,
            relasjon = relasjon
        )
    }

    private fun hentAlleAvdode(avdode: Map<String?, Familierelasjonsrolle?>): Map<String?, Familierelasjonsrolle?> {
        return avdode.filter { isNumber(it.key) }
    }

    private fun isNumber(s: String?): Boolean {
        return if (s.isNullOrEmpty()) false else s.all { Character.isDigit(it) }
    }

    @ApiOperation("henter ut navn for en aktørId")
    @GetMapping("/personinfo/{aktoerid}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getNameOnly(@PathVariable("aktoerid", required = true) aktoerid: String): ResponseEntity<Personinformasjon> {
        auditLogger.log("/personinfo/{$aktoerid}", "getNameOnly")

        return PersonControllerHentPersonNavn.measure {
            val person = hentPerson(aktoerid).person
            ResponseEntity.ok(
                Personinformasjon(
                    person.personnavn.sammensattNavn,
                    person.personnavn.fornavn,
                    person.personnavn.mellomnavn,
                    person.personnavn.etternavn
                )
            )
        }
    }

    private fun hentPersonTps(norskIdent: String) = personService.hentPersonResponse(norskIdent)

    private fun hentPerson(aktoerid: String): HentPersonResponse {
        logger.info("Henter personinformasjon for aktørId: $aktoerid")
        if (aktoerid.isBlank()) {
            throw ManglerAktoerIdException("Tom input-verdi")
        }
        val norskIdent =
            aktoerregisterService.hentGjeldendeIdent(IdentGruppe.NorskIdent, AktoerId(aktoerid))?.id
                ?: throw AktoerregisterIkkeFunnetException("NorskIdent for aktoerId $aktoerid ikke funnet.")

        return hentPersonTps(norskIdent)
    }

    /**
     * Personinformasjon fra TPS ( PersonV3 )
     */
    data class Personinformasjon(
        var fulltNavn: String? = null,
        var fornavn: String? = null,
        var mellomnavn: String? = null,
        var etternavn: String? = null
    )

    data class PersoninformasjonAvdode(
        var fnr: String? = null,
        var aktorId: String? = null,
        var fulltNavn: String? = null,
        var fornavn: String? = null,
        var mellomnavn: String? = null,
        var etternavn: String? = null,
        var relasjon: String? = null
    )
}
