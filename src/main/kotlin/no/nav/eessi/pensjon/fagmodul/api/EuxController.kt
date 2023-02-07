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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.HttpStatusCodeException
import javax.annotation.PostConstruct

@RestController
@RequestMapping("/eux")
class EuxController(
    private val euxInnhentingService: EuxInnhentingService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {

    private val logger = LoggerFactory.getLogger(EuxController::class.java)

    private lateinit var paakobledeland: MetricsHelper.Metric
    private lateinit var euxKodeverk: MetricsHelper.Metric
    private lateinit var euxKodeverkLand: MetricsHelper.Metric
    private lateinit var euxInstitusjoner: MetricsHelper.Metric

    val backupList = listOf("AT", "BE", "BG", "CH", "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "HR", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV", "MT", "NL", "NO", "PL", "PT", "RO", "SE", "SI", "SK", "UK")

    @PostConstruct
    fun initMetrics() {
        paakobledeland = metricsHelper.init("paakobledeland", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        euxKodeverk = metricsHelper.init("euxKodeverk", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        euxKodeverkLand = metricsHelper.init("euxKodeverkLand", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        euxInstitusjoner  = metricsHelper.init("euxInstitusjoner", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }

    @Unprotected
    @GetMapping("/rinaurl")
    fun getRinaUrl2020() : ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("rinaUrl" to euxInnhentingService.getRinaUrl()))
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

}