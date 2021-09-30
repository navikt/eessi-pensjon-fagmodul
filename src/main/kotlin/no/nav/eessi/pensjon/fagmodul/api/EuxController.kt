package no.nav.eessi.pensjon.fagmodul.api

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.swagger.v3.oas.annotations.Operation
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.Kodeverk
import no.nav.eessi.pensjon.fagmodul.models.KodeverkResponse
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.errorBody
import no.nav.eessi.pensjon.utils.toJson
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.HttpStatusCodeException
import javax.annotation.PostConstruct

@Protected
@RestController
@RequestMapping("/eux")
class EuxController(
    @Value("\${ENV}") val environment: String,
    @Value("\${RINA_HOST_URL}") private val rinaUrl: String,
    private val euxInnhentingService: EuxInnhentingService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(EuxController::class.java)

    private lateinit var paakobledeland: MetricsHelper.Metric
    private lateinit var euxKodeverk: MetricsHelper.Metric
    private lateinit var euxKodeverkLand: MetricsHelper.Metric
    private lateinit var euxInstitusjoner: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        paakobledeland = metricsHelper.init("paakobledeland", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        euxKodeverk = metricsHelper.init("euxKodeverk", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        euxKodeverkLand = metricsHelper.init("euxKodeverkLand", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        euxInstitusjoner  = metricsHelper.init("euxInstitusjoner", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }


    @GetMapping("/rinaurl")
    @Operation(description = "direkte URL til RINA")
    fun getRinaURL(): ResponseEntity<Map<String, String>> {
        if (environment == "q1") {
            //RINA2020
            return ResponseEntity.ok(mapOf("rinaUrl" to "https://$rinaUrl/portal_new/case-management/"))
        }
        //RINA2019
        return ResponseEntity.ok(mapOf("rinaUrl" to "https://$rinaUrl/portal/#/caseManagement/"))
    }

    @Operation(description = "henter liste over subject")
    @GetMapping("/subjectarea")
    fun getSubjectArea(): List<String> {
        return listOf("Pensjon")
    }


    @Operation(description = "henter liste over alle tilknyttete land i valgt BUC")
    @GetMapping("/countries/{buctype}")
    fun getPaakobledeland(@PathVariable(value = "buctype") bucType: BucType): ResponseEntity<String> {
        return paakobledeland.measure {
            logger.info("Henter ut liste over land knyttet til buc: $bucType")
            return@measure try {
                val paakobledeLand = euxInnhentingService.getInstitutions(bucType.name)
                    .map { it.country }
                    .distinct()
                ResponseEntity.ok(paakobledeLand.toJson())
            } catch (sce: HttpStatusCodeException) {
                ResponseEntity.status(sce.statusCode).body(errorBody(sce.responseBodyAsString))
            } catch (ex: Exception) {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.message?.let { errorBody(it) })
            }
        }
    }

    //TODO
    //finnes også i SEDcontroller men skal slettes fra SedController når det er over i UI
    /************************************************************************************/

    @Operation(description = "Henter ut en liste over landkoder ut fra kodeverktjenesten eux")
    @GetMapping( "/landkoder")
    fun getCountryCode(): List<String> {
        return euxKodeverkLand.measure {
            logger.info("Henter ut liste over land fra eux-kodeverk")
            return@measure euxInnhentingService.getKodeverk(Kodeverk.LANDKODER).mapNotNull{ it.kode }.toList()
        }
    }

    @Operation(description = "Henter ut en liste over kodeverk fra eux")
    @GetMapping( "/kodeverk/{kodeverk}")
    fun getKodeverk(@PathVariable("kodeverk", required = true) kodeverk: Kodeverk): List<KodeverkResponse> {
        return euxKodeverk.measure {
            logger.info("Henter ut liste over alle kodeverk fra eux-rina")
            return@measure euxInnhentingService.getKodeverk(kodeverk)
        }
    }

    @Operation(description = "Henter ut en liste over registrerte institusjoner innenfor spesifiserte EU-land. ny api kall til eux")
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