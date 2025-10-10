package no.nav.eessi.pensjon.fagmodul.api

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.shared.api.InstitusjonItem
import no.nav.eessi.pensjon.utils.errorBody
import no.nav.eessi.pensjon.utils.toJson
import no.nav.security.token.support.core.api.Protected
import no.nav.security.token.support.core.api.Unprotected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.HttpStatusCodeException


@RestController
@RequestMapping("/eux")
class EuxController(
    private val euxInnhentingService: EuxInnhentingService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private val logger = LoggerFactory.getLogger(EuxController::class.java)

    private lateinit var rinaUrl: MetricsHelper.Metric
    private lateinit var resend: MetricsHelper.Metric
    private lateinit var resendMedRinaId: MetricsHelper.Metric
    private lateinit var sedsendt: MetricsHelper.Metric
    private lateinit var euxKodeverk: MetricsHelper.Metric
    private lateinit var paakobledeland: MetricsHelper.Metric
    private lateinit var euxKodeverkLand: MetricsHelper.Metric
    private lateinit var euxInstitusjoner: MetricsHelper.Metric

    init {
            rinaUrl = metricsHelper.init("RinaUrl")
            resend = metricsHelper.init("resend")
            sedsendt = metricsHelper.init("sedsendt")
            resendMedRinaId = metricsHelper.init("resendMedRinaId")
            euxKodeverk = metricsHelper.init("euxKodeverk", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
            paakobledeland = metricsHelper.init("paakobledeland", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
            euxKodeverkLand = metricsHelper.init("euxKodeverkLand", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
            euxInstitusjoner  = metricsHelper.init("euxInstitusjoner", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }

    val backupList = listOf("AT", "BE", "BG", "CH", "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "HR", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV", "MT", "NL", "NO", "PL", "PT", "RO", "SE", "SI", "SK", "UK")

    @Unprotected
    @GetMapping("/rinaurl")
    fun getRinaUrl2020(): ResponseEntity<Map<String, String>> = rinaUrl.measure {
        return@measure ResponseEntity.ok(mapOf("rinaUrl" to euxInnhentingService.getRinaUrl()))
    }

    @Protected
    @GetMapping("/countries/{buctype}")
    fun getPaakobledeland(@PathVariable(value = "buctype") bucType: BucType): ResponseEntity<FrontEndResponse<String>> {
        return paakobledeland.measure {
            logger.info("Henter ut liste over land knyttet til buc: $bucType")
            return@measure try {
                val paakobledeLand = euxInnhentingService.getInstitutions(bucType.name)
                    .map { it.country }
                    .distinct()
                val landlist = paakobledeLand.ifEmpty {
                    logger.warn("Ingen svar fra /institusjoner?BuCType, kjører backupliste")
                    backupList
                }
                ResponseEntity.ok(FrontEndResponse(landlist.toJson(), HttpStatus.OK.name))
            } catch (sce: HttpStatusCodeException) {
                ResponseEntity.status(sce.statusCode).body(FrontEndResponse(errorBody(sce.responseBodyAsString), sce.statusCode.toString()))
            } catch (ex: Exception) {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(FrontEndResponse(ex.message?.let { errorBody(it) }, HttpStatus.INTERNAL_SERVER_ERROR.toString()))
            }
        }
    }

    @Protected
    @GetMapping("/institutions/{buctype}", "/institutions/{buctype}/{countrycode}")
    fun getEuxInstitusjoner(
        @PathVariable("buctype", required = true) buctype: String,
        @PathVariable("countrycode", required = false) landkode: String? = ""
    ): FrontEndResponse<List<InstitusjonItem>> {
        return euxInstitusjoner.measure {
            logger.info("Henter ut liste over alle Institusjoner i Rina")
            val institusjoner = euxInnhentingService.getInstitutions(buctype, landkode)
            return@measure FrontEndResponse(institusjoner, HttpStatus.OK.name)
        }
    }

    @Protected
    @PostMapping("/buc/{rinasakId}/sed/{dokumentId}/send")
    fun sendSeden(
        @PathVariable("rinasakId", required = true) rinaSakId: String,
        @PathVariable("dokumentId", required = false) dokumentId: String
    ): ResponseEntity<FrontEndResponse<String>> {
        return sedsendt.measure {
            return@measure try {
                val response = euxInnhentingService.sendSed(rinaSakId, dokumentId)
                if (response) {
                    logger.info("Sed er sendt til Rina")
                    ResponseEntity.ok().body(FrontEndResponse("Sed er sendt til Rina", HttpStatus.OK.name))
                } else {
                    logger.error("Sed ble ikke sendt til Rina")
                    ResponseEntity.badRequest().body(FrontEndResponse("Sed ble IKKE sendt til Rina", HttpStatus.BAD_REQUEST.name))
                }
            } catch (ex: Exception) {
                logger.error("Sed ble ikke sendt til Rina")
                ResponseEntity.badRequest().body(FrontEndResponse("Sed ble IKKE sendt til Rina", HttpStatus.BAD_REQUEST.name))
            }
        }
    }
    @Protected
    @PostMapping("/buc/{rinasakId}/sed/{dokumentId}/sendto")
    fun sendSedMedMottakere(
        @PathVariable("rinasakId") rinaSakId: String,
        @PathVariable("dokumentId") dokumentId: String,
        @RequestBody mottakere: List<String>
    ): ResponseEntity<FrontEndResponse<String>> {
        return sedsendt.measure {
            logger.info("Sender sed:$rinaSakId til mottakere: $mottakere")
            if (mottakere.isNullOrEmpty()) {
                logger.error("Mottakere er tom eller null")
                return@measure ResponseEntity.badRequest().body(FrontEndResponse("Mottakere kan ikke være tom", HttpStatus.BAD_REQUEST.name))
            }
            try {
                val response = euxInnhentingService.sendSedTilMottakere(rinaSakId, dokumentId, mottakere)
                if (response) {
                    logger.info("Sed er sendt til Rina:$rinaSakId, dokument:$dokumentId og mottakerne er lagt til: $mottakere")
                    return@measure ResponseEntity.ok().body(FrontEndResponse(result = "Sed er sendt til Rina", status = "OK"))
                }
                logger.error("Sed ble ikke sendt til Rina:$rinaSakId, dokument:$dokumentId og mottakerne er lagt til: $mottakere")
                return@measure ResponseEntity.badRequest().body(FrontEndResponse("Sed ble IKKE sendt til Rina", HttpStatus.BAD_REQUEST.name))
            } catch (ex: Exception) {
                return@measure handleSendSedException(ex, rinaSakId, dokumentId)
            }
        }
    }

    @Protected
    @PostMapping("/resend/buc/{RinaSakId}/sed/{DokumentId}")
    fun resendeDokumenterMedRinaId(
        @PathVariable("RinaSakId") rinaSakId: String,
        @PathVariable("DokumentId") dokumentId: String,
    ): ResponseEntity<FrontEndResponse<String>> {
        return resend.measure {
            logger.info("Resender dokument: $rinaSakId, dokument: $dokumentId")
            try {
                val response = euxInnhentingService.reSendeRinasakerMedRinaId(rinaSakId, dokumentId)
                if (response?.status == HttpStatus.OK) {
                    logger.info("Resendte dokumenter er resendt til Rina")
                    return@measure ResponseEntity.ok().body(FrontEndResponse("Seder er resendt til Rina", HttpStatus.OK.name))
                }
                logger.error("Resendte dokumenter ble IKKE resendt til Rina ${response?.status}")
                return@measure ResponseEntity.badRequest().body(FrontEndResponse(result = null, message = "Seder ble IKKE resendt til Rina: ${response?.messages}".toJson(), status = HttpStatus.BAD_REQUEST.name))
            } catch (ex: Exception) {
                return@measure ResponseEntity.badRequest().body(
                    FrontEndResponse(
                        result = null,
                        message = "Seder ble IKKE resendt til Rina: ${ex.message}",
                        status = HttpStatus.BAD_REQUEST.name
                    )
                )
            }
        }
    }

    @Protected
    @PostMapping("/resend/liste")
    fun resendtDokumenter(@RequestBody dokumentListe: String): ResponseEntity<FrontEndResponse<String>> {
        return resend.measure {
            logger.info("Dokumentliste: $dokumentListe")
            if (dokumentListe.isEmpty()) {
                logger.error("Dokumentlisten er tom eller null")
                return@measure ResponseEntity.badRequest().body(FrontEndResponse("Dokumentlisten kan ikke være tom", HttpStatus.BAD_REQUEST.name))
            }
            val formattedDokumentListe = dokumentListe
                .replace("\\\n", "\n")
                .replace("\\n", "\n")
                .replace("\n\n", "")
                .lines()
                .filter { it.isNotBlank() }
                .joinToString(separator = "\n") { it.trim() }
                .replace("\"", "")
            logger.info("Formatert dokumentliste: $formattedDokumentListe")
            try {
                val response = euxInnhentingService.reSendRinasaker(formattedDokumentListe)
                if (response?.status == HttpStatus.OK) {
                    logger.info("Resendte dokumenter er resendt til Rina")
                    return@measure ResponseEntity.ok().body(FrontEndResponse("Seder er resendt til Rina", HttpStatus.OK.name))
                }
                logger.error("Resendte dokumenter ble IKKE resendt til Rina ${response?.status}")
                return@measure ResponseEntity.badRequest().body(FrontEndResponse(result = null, message = "Seder ble IKKE resendt til Rina: ${response?.messages}",
                    status = HttpStatus.BAD_REQUEST.name))
            } catch (ex: Exception) {
                return@measure ResponseEntity.badRequest().body(
                    FrontEndResponse(
                        result = null,
                        message = "Seder ble IKKE resendt til Rina: ${ex.message} ",
                        status = HttpStatus.BAD_REQUEST.name))
            }
        }
    }

    private fun handleSendSedException(ex: Exception, rinaSakId: String, dokumentId: String): ResponseEntity<FrontEndResponse<String>> {
        logger.error("Sed ble ikke sendt til Rina: $rinaSakId, dokument: $dokumentId", ex)
        return ResponseEntity.badRequest().body(FrontEndResponse("Sed ble IKKE sendt til Rina", HttpStatus.BAD_REQUEST.name))
    }

}
