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
import org.springframework.web.server.ResponseStatusException



@RestController
@RequestMapping("/eux")
class EuxController(
    private val euxInnhentingService: EuxInnhentingService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private val logger = LoggerFactory.getLogger(EuxController::class.java)

    private lateinit var rinaUrl: MetricsHelper.Metric
    private lateinit var sedsendt: MetricsHelper.Metric
    private lateinit var euxKodeverk: MetricsHelper.Metric
    private lateinit var paakobledeland: MetricsHelper.Metric
    private lateinit var euxKodeverkLand: MetricsHelper.Metric
    private lateinit var euxInstitusjoner: MetricsHelper.Metric

    init {
            rinaUrl = metricsHelper.init("RinaUrl")
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


}