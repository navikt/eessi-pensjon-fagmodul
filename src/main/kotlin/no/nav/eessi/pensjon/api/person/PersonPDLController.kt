package no.nav.eessi.pensjon.api.person

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.swagger.annotations.ApiOperation
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonoppslagException
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.Familierelasjonsrolle
import no.nav.eessi.pensjon.personoppslag.pdl.model.Navn
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonClient
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import javax.annotation.PostConstruct

const val PERSON_IKKE_FUNNET = "Person ikke funnet"

/**
 * Controller for å kalle NAV interne registre
 */
@Protected
@RestController
class PersonPDLController(
    private val pdlService: PersonService,
    private val auditLogger: AuditLogger,
    private val pensjonsinformasjonClient: PensjonsinformasjonClient,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(PersonPDLController::class.java)

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
    @GetMapping("/person/pdl/{aktoerid}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getPerson(@PathVariable("aktoerid", required = true) aktoerid: String): ResponseEntity<Person> {
        auditLogger.log("getPerson", aktoerid)

        return PersonControllerHentPerson.measure {
            val person = hentPerson(aktoerid)
            ResponseEntity.ok(person)
        }
    }

    @ApiOperation("henter ut alle avdøde for en aktørId og vedtaksId der aktør er gjenlevende")
    @GetMapping("/person/pdl/{aktoerId}/avdode/vedtak/{vedtaksId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getDeceased(
        @PathVariable("aktoerId", required = true) gjenlevendeAktoerId: String,
        @PathVariable("vedtaksId", required = true) vedtaksId: String
    ): ResponseEntity<List<PersoninformasjonAvdode?>> {
        logger.debug("Henter informasjon om avdøde $gjenlevendeAktoerId fra vedtak $vedtaksId")
        auditLogger.log("getDeceased", gjenlevendeAktoerId)

        return PersonControllerHentPersonAvdod.measure {

            val pensjonInfo = pensjonsinformasjonClient.hentAltPaaVedtak(vedtaksId)
            logger.debug("pensjonInfo: ${pensjonInfo.toJsonSkipEmpty()}")

            if (pensjonInfo.avdod == null) {
                logger.info("Ingen avdøde return empty list")
                return@measure ResponseEntity.ok(emptyList())
            }

            val gjenlevende = hentPerson(gjenlevendeAktoerId)
            logger.debug("gjenlevende : $gjenlevende")

            val avdode = mapOf(
                pensjonInfo.avdod?.avdod to null,
                pensjonInfo.avdod?.avdodFar to Familierelasjonsrolle.FAR,
                pensjonInfo.avdod?.avdodMor to Familierelasjonsrolle.MOR
            )

            logger.debug("avdød map : $avdode")

            val avdodeMedFnr = avdode
                .filter { (fnr, _) -> fnr?.toLongOrNull() != null }
                .map { (fnr, rolle) -> pairPersonFnr(fnr!!, rolle, gjenlevende) }

            logger.info("Det ble funnet ${avdodeMedFnr.size} avdøde for den gjenlevende med aktørID: $gjenlevendeAktoerId")

            logger.debug("result: ${avdodeMedFnr.toJsonSkipEmpty()}")
            ResponseEntity.ok(avdodeMedFnr)
        }
    }

    private fun pairPersonFnr(
        avdodFnr: String,
        avdodRolle: Familierelasjonsrolle?,
        gjenlevende: Person?
    ): PersoninformasjonAvdode {

        logger.debug("Henter avdød person")
        val avdode = pdlService.hentPerson(NorskIdent(avdodFnr))
        val avdodNavn = avdode?.navn

        val relasjon = avdodRolle ?: gjenlevende?.sivilstand?.firstOrNull { it.relatertVedSivilstand == avdodFnr }?.type

        logger.debug("return PersoninformasjonAvdode")
        return PersoninformasjonAvdode(
            fnr = avdodFnr,
            fulltNavn = avdodNavn?.sammensattNavn,
            fornavn = avdodNavn?.fornavn,
            mellomnavn = avdodNavn?.mellomnavn,
            etternavn = avdodNavn?.etternavn,
            relasjon = relasjon?.name
        )
    }

    private fun Navn.sammensattNavn() = listOfNotNull(etternavn, fornavn, mellomnavn)
        .joinToString(separator = " ")

    @ApiOperation("henter ut navn for en aktørId")
    @GetMapping("/person/pdl/info/{aktoerid}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getNameOnly(@PathVariable("aktoerid", required = true) aktoerid: String): ResponseEntity<Personinformasjon> {
        auditLogger.log("getNameOnly", aktoerid)

        return PersonControllerHentPersonNavn.measure {
            val navn = hentPerson(aktoerid).navn
            ResponseEntity.ok(
                Personinformasjon(
                    navn?.sammensattNavn(),
                    navn?.fornavn,
                    navn?.mellomnavn,
                    navn?.etternavn
                )
            )
        }
    }

    private fun hentPerson(aktoerid: String): Person {
        logger.info("Henter personinformasjon for aktørId: $aktoerid")
        if (aktoerid.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Tom input-verdi")
        }
        //https://curly-enigma-afc9cd64.pages.github.io/#_feilmeldinger_fra_pdl_api_graphql_response_errors
        return try {
            pdlService.hentPerson(AktoerId(aktoerid)) ?: throw NullPointerException(PERSON_IKKE_FUNNET)
        } catch (np: NullPointerException) {
            logger.error("PDL Person null")
            throw ResponseStatusException(HttpStatus.NOT_FOUND, PERSON_IKKE_FUNNET)
        } catch (pe: PersonoppslagException) {
            logger.error("PersonoppslagExcpetion: ${pe.message}")
            when(pe.message) {
                "not_found: Fant ikke person" -> throw ResponseStatusException(HttpStatus.NOT_FOUND, PERSON_IKKE_FUNNET)
                "unauthorized: Ikke tilgang til å se person" -> throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ikke tilgang til å se person")
                else -> throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, pe.message)
            }
        } catch (ex: Exception) {
            logger.error("Excpetion: ${ex.message}")
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Feil ved Personoppslag")
        }

    }

    /**
     * Personinformasjon
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
