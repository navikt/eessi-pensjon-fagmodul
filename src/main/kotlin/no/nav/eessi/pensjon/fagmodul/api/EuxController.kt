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
    private lateinit var sedsendt: MetricsHelper.Metric
    private lateinit var euxKodeverk: MetricsHelper.Metric
    private lateinit var paakobledeland: MetricsHelper.Metric
    private lateinit var euxKodeverkLand: MetricsHelper.Metric
    private lateinit var euxInstitusjoner: MetricsHelper.Metric

    init {
            rinaUrl = metricsHelper.init("RinaUrl")
            sedsendt = metricsHelper.init("resend")
            sedsendt = metricsHelper.init("sedsendt")
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
    fun getPaakobledeland(@PathVariable(value = "buctype") bucType: BucType): ResponseEntity<String> {
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
                ResponseEntity.ok(landlist.toJson())
            } catch (sce: HttpStatusCodeException) {
                ResponseEntity.status(sce.statusCode).body(errorBody(sce.responseBodyAsString))
            } catch (ex: Exception) {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.message?.let { errorBody(it) })
            }
        }
    }

    @Protected
    @GetMapping("/institutions/{buctype}", "/institutions/{buctype}/{countrycode}")
    fun getEuxInstitusjoner(
        @PathVariable("buctype", required = true) buctype: String,
        @PathVariable("countrycode", required = false) landkode: String? = ""
    ): List<InstitusjonItem> {
        return euxInstitusjoner.measure {
            logger.info("Henter ut liste over alle Institusjoner i Rina")
            return@measure euxInnhentingService.getInstitutions(buctype, landkode)
        }
    }

    @Protected
    @PostMapping("/buc/{rinasakId}/sed/{dokumentId}/send")
    fun sendSeden(
        @PathVariable("rinasakId", required = true) rinaSakId: String,
        @PathVariable("dokumentId", required = false) dokumentId: String
    ): ResponseEntity<String> {
        return sedsendt.measure {
            return@measure try {
                val response = euxInnhentingService.sendSed(rinaSakId, dokumentId)
                if (response) {
                    logger.info("Sed er sendt til Rina")
                    ResponseEntity.ok().body("Sed er sendt til Rina")
                } else {
                    logger.error("Sed ble ikke sendt til Rina")
                    ResponseEntity.badRequest().body("Sed ble IKKE sendt til Rina")
                }
            } catch (ex: Exception) {
                logger.error("Sed ble ikke sendt til Rina")
                ResponseEntity.badRequest().body("Sed ble IKKE sendt til Rina")
            }
        }
    }
    @Protected
    @PostMapping("/buc/{rinasakId}/sed/{dokumentId}/sendto")
    fun sendSedMedMottakere(
        @PathVariable("rinasakId") rinaSakId: String,
        @PathVariable("dokumentId") dokumentId: String,
        @RequestBody mottakere: List<String>
    ): ResponseEntity<String> {
        return sedsendt.measure {
            logger.info("Sender sed:$rinaSakId til mottakere: $mottakere")
            if (mottakere.isNullOrEmpty()) {
                logger.error("Mottakere er tom eller null")
                return@measure ResponseEntity.badRequest().body("Mottakere kan ikke være tom")
            }
            try {
                val response = euxInnhentingService.sendSedTilMottakere(rinaSakId, dokumentId, mottakere)
                if (response) {
                    logger.info("Sed er sendt til Rina:$rinaSakId, dokument:$dokumentId og mottakerne er lagt til: $mottakere")
                    return@measure ResponseEntity.ok().body("Sed er sendt til Rina")
                }
                logger.error("Sed ble ikke sendt til Rina:$rinaSakId, dokument:$dokumentId og mottakerne er lagt til: $mottakere")
                return@measure ResponseEntity.badRequest().body("Sed ble IKKE sendt til Rina")
            } catch (ex: Exception) {
                return@measure handleSendSedException(ex, rinaSakId, dokumentId)
            }
        }
    }

    @Protected
    @PostMapping("/cpi/resend/liste")
    fun resendtDokumenter(
        @RequestBody dokumentListe: String
    ): ResponseEntity<String> {
        return resend.measure {
            logger.info("Resender dokumentliste")
            if (dokumentListe.isEmpty()) {
                logger.error("Dokumentlisten er tom eller null")
                return@measure ResponseEntity.badRequest().body("Dokumentlisten kan ikke være tom")
            }
            try {
                val response = euxInnhentingService.reSendRinasaker(dokumentListe)
                if (response) {
                    logger.info("Resendte dokumenter er resendt til Rina")
                    return@measure ResponseEntity.ok().body("Sederer resendt til Rina")
                }
                logger.error("Resendte dokumenter ble IKKE resendt til Rina")
                return@measure ResponseEntity.badRequest().body("Seder ble IKKE resendt til Rina")
            } catch (ex: Exception) {
                return@measure handleReSendDocumentException(ex, dokumentListe)
            }
        }
    }

    private fun handleSendSedException(ex: Exception, rinaSakId: String, dokumentId: String): ResponseEntity<String> {
        logger.error("Sed ble ikke sendt til Rina: $rinaSakId, dokument: $dokumentId", ex)
        return ResponseEntity.badRequest().body("Sed ble IKKE sendt til Rina")
    }

    private fun handleReSendDocumentException(ex: Exception, dokumentliste: String): ResponseEntity<String> {
        logger.error("Seder ble ikke resendt til Rina", ex)
        return ResponseEntity.badRequest().body("Seder ble IKKE resendt til Rina")
    }
}
