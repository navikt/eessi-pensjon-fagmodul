package no.nav.eessi.pensjon.api.person

import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_02
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_06
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.fagmodul.api.FrontEndResponse
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonoppslagException
import no.nav.eessi.pensjon.personoppslag.pdl.model.*
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PesysService
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

const val PERSON_IKKE_FUNNET = "Person ikke funnet"

/**
 * Controller for å kalle NAV interne registre
 */
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@Protected
@RestController
class PersonPDLController(
    private val pdlService: PersonService,
    private val auditLogger: AuditLogger,
    private val pensjonsinformasjonService: PesysService,
    private val euxInnhenting: EuxInnhentingService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private val logger = LoggerFactory.getLogger(PersonPDLController::class.java)

    private  var personControllerHentPerson: MetricsHelper.Metric
    private  var personControllerHentPersonNavn: MetricsHelper.Metric
    private  var personControllerHentPersonAvdod: MetricsHelper.Metric
    private  var personControllerHentAktoerid: MetricsHelper.Metric
    init {
        personControllerHentPerson = metricsHelper.init("PersonControllerHentPerson")
        personControllerHentAktoerid = metricsHelper.init("PersonControllerHentAktoerId")
        personControllerHentPersonNavn = metricsHelper.init("PersonControllerHentPersonNavn")
        personControllerHentPersonAvdod = metricsHelper.init("PersonControllerHentPersonAvdod", ignoreHttpCodes = listOf(HttpStatus.UNAUTHORIZED))
    }

    @GetMapping("/person/pdl/{aktoerid}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getPerson(@PathVariable("aktoerid", required = true) aktoerid: String): ResponseEntity<FrontEndResponse<PdlPerson>> {
        auditLogger.log("getPerson", aktoerid)

        return personControllerHentPerson.measure {
            try {
                val person = hentPerson(aktoerid)
                ResponseEntity.ok(FrontEndResponse(result = person, status = HttpStatus.OK.value().toString()))
            } catch (ex: Exception) {
                logger.error("Feil ved henting av person: ${ex.message}")
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    FrontEndResponse(
                        result = null,
                        status = HttpStatus.INTERNAL_SERVER_ERROR.value().toString(),
                        message = "Feil ved henting av person: ${ex.message}"
                    )
                )
            }
        }
    }

    @GetMapping("/person/pdl/aktoerid/{fnr}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getAktoerid(@PathVariable("fnr", required = true) fnr: String): ResponseEntity<FrontEndResponse<String>> {
        auditLogger.log("getAktoerid", fnr)

        return personControllerHentAktoerid.measure {
            val aktorid = pdlService.hentAktorId(fnr).id
            ResponseEntity.ok(FrontEndResponse(result = aktorid, status = HttpStatus.OK.name))
        }
    }

    @GetMapping("/person/pdl/{aktoerId}/avdode/vedtak/{vedtaksId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getDeceased(
        @PathVariable("aktoerId", required = true) gjenlevendeAktoerId: String,
        @PathVariable("vedtaksId", required = true) vedtaksId: String
    ): ResponseEntity<FrontEndResponse<List<PersoninformasjonAvdode?>>> {
        logger.debug("Henter informasjon om avdøde $gjenlevendeAktoerId fra vedtak $vedtaksId")
        auditLogger.log("getDeceased", gjenlevendeAktoerId)

        return personControllerHentPersonAvdod.measure {

            val pensjonInfo = pensjonsinformasjonService.hentAvdod(vedtaksId).also {
                logger.debug("pensjonInfo: ${it?.toJsonSkipEmpty()}")
            }

            if (pensjonInfo?.avdod.isNullOrEmpty() && pensjonInfo?.avdodFar.isNullOrEmpty() && pensjonInfo?.avdodMor.isNullOrEmpty()) {
                logger.info("Ingen avdøde return empty list")
                return@measure ResponseEntity.ok(FrontEndResponse(result = emptyList(), status = HttpStatus.OK.name))
            }

            val gjenlevende = hentPerson(gjenlevendeAktoerId)
            logger.debug("gjenlevende : $gjenlevende")

            val avdode = mapOf(
                pensjonInfo.avdod to null,
                pensjonInfo.avdodFar to Familierelasjonsrolle.FAR,
                pensjonInfo.avdodMor to Familierelasjonsrolle.MOR
            )

            logger.debug("avdød map : $avdode")

            val avdodeMedFnr = avdode
                .filter { (fnr, _) -> fnr?.toLongOrNull() != null }
                .map { (fnr, rolle) -> pairPersonFnr(fnr!!, rolle, gjenlevende) }

            logger.info("Det ble funnet ${avdodeMedFnr.size} avdøde for den gjenlevende med aktørID: $gjenlevendeAktoerId")

            logger.debug("result: ${avdodeMedFnr.toJsonSkipEmpty()}")
            ResponseEntity.ok(FrontEndResponse(result = avdodeMedFnr, status = HttpStatus.OK.name))

        }
    }

    @GetMapping("/person/pdl/info/{aktoerid}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getNameOnly(@PathVariable("aktoerid", required = true) aktoerid: String): ResponseEntity<FrontEndResponse<PersoninformasjonAvdode>>  {
        auditLogger.log("getNameOnly", aktoerid)

        return personControllerHentPersonNavn.measure {
            val navn = hentPerson(aktoerid).navn
            ResponseEntity.ok(
                FrontEndResponse(
                    result = PersoninformasjonAvdode(
                        fulltNavn = navn?.sammensattEtterNavn,
                        fornavn = navn?.fornavn,
                        mellomnavn = navn?.mellomnavn,
                        etternavn = navn?.etternavn
                    ), status = HttpStatus.OK.name
                )
            )
        }
    }

    @GetMapping("/person/vedtak/{vedtakid}/buc/{rinanr}/avdodsdato")
    fun getAvdodDateFromVedtakOrSed(
        @PathVariable(value = "vedtakid", required = true) vedtakid: String,
        @PathVariable(value = "rinanr", required = true) euxCaseId: String
    ): ResponseEntity<FrontEndResponse<List<DodsDatoPdl>>> {
        val vedtak = pensjonsinformasjonService.hentAvdod(vedtakid)
        val avdodlist = pensjonsinformasjonService.hentGyldigAvdod(vedtak) ?: return ResponseEntity.ok(FrontEndResponse(result = emptyList(), status = HttpStatus.OK.name))

        val avdodDato = when {
            avdodlist.size >= 2 -> hentFlereAvdode(avdodlist, euxCaseId)
            else -> hentDoedsdatoFraPDL(avdodlist.first())
        }

        return ResponseEntity.ok(FrontEndResponse(result = avdodDato, status = HttpStatus.OK.name))
    }

    private fun hentFlereAvdode(avdodlist: List<String>, euxCaseId: String): List<DodsDatoPdl> {
        val buc = euxInnhenting.getBuc(euxCaseId)
        logger.debug("name: ${buc.processDefinitionName}, actions: ${buc.actions}")

        val sedType = when (buc.processDefinitionName) {
            P_BUC_02.name -> SedType.P2100
            P_BUC_06.name -> SedType.P5000
            else -> return emptyList()
        }

        val sedAvdodident = BucUtils(buc).getAllDocuments()
            .firstOrNull { it.type == sedType && it.direction == "OUT" }
            ?.id
            ?.let { hentSedAvdodIdent(euxCaseId, it) }

        // valider sedident mot vedtakident på avdøde og henter person for doeadsdato
        return hentDoedsdatoFraPDL(avdodlist.firstOrNull { it == sedAvdodident })
    }

    private fun pairPersonFnr(
        avdodFnr: String,
        avdodRolle: Familierelasjonsrolle?,
        gjenlevende: PdlPerson?
    ): PersoninformasjonAvdode {

        logger.debug("Henter avdød person")
        val avdode = pdlService.hentPerson(Ident.bestemIdent(avdodFnr))
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

    private fun hentSedAvdodIdent(euxCaseId: String, documentId: String): String? {
        val sed = euxInnhenting.getSedOnBucByDocumentId(euxCaseId, documentId )
        return sed.let { it.nav?.bruker?.person?.pin?.firstOrNull { pin -> pin.land == "NO" && pin.identifikator != null } }?.identifikator
    }

    private fun hentDoedsdatoFraPDL(avdodIdent: String?): List<DodsDatoPdl> {
        if (avdodIdent == null || avdodIdent.isEmpty()) return emptyList()
        val avdodperson = pdlService.hentPerson(NorskIdent(avdodIdent))
        val avdoddato = avdodperson?.doedsfall?.doedsdato
        //returner litt metadata
        val result = listOf(DodsDatoPdl(
            doedsdato = avdoddato?.toString(),
            sammensattNavn = avdodperson?.navn?.sammensattNavn,
            ident = avdodIdent
        ))
        logger.debug("result: $result")
        return result
    }

    data class DodsDatoPdl(
        val doedsdato: String?,
        val sammensattNavn: String?,
        val ident: String?
    )

    private fun hentPerson(aktoerid: String): PdlPerson {
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
            logger.error("PersonoppslagException: ${pe.message}")
            when(pe.message) {
                "not_found: Fant ikke person" -> throw ResponseStatusException(HttpStatus.NOT_FOUND, PERSON_IKKE_FUNNET)
                "unauthorized: Ikke tilgang til å se person" -> throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ikke tilgang til å se person")
                else -> throw ResponseStatusException(HttpStatus.NOT_FOUND, pe.message)
            }
        } catch (ex: Exception) {
            logger.error("Exception: ${ex.message}")
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Feil ved Personoppslag")
        }
    }

    /**
     * Personinformasjon
     */
    data class PersoninformasjonAvdode(
        val fnr: String? = null,
        val aktorId: String? = null,
        val fulltNavn: String? = null,
        val fornavn: String? = null,
        val mellomnavn: String? = null,
        val etternavn: String? = null,
        val relasjon: String? = null
    )
}
