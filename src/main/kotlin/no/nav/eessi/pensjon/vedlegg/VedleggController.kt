package no.nav.eessi.pensjon.vedlegg

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.swagger.annotations.ApiOperation
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.errorBody
import no.nav.eessi.pensjon.utils.successBody
import no.nav.eessi.pensjon.vedlegg.client.SafException
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*
import javax.annotation.PostConstruct

@Protected
@RestController
@RequestMapping("/saf")
class VedleggController(private val vedleggService: VedleggService,
                        private val auditlogger: AuditLogger,
                        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger = LoggerFactory.getLogger(VedleggController::class.java)

    private lateinit var VedleggControllerMetadata: MetricsHelper.Metric
    private lateinit var VedleggControllerInnhold: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        VedleggControllerMetadata = metricsHelper.init("VedleggControllerMetadata")
        VedleggControllerInnhold = metricsHelper.init("VedleggControllerInnhold")
    }

    @ApiOperation("Henter metadata for alle dokumenter i alle journalposter for en gitt aktørid")
    @GetMapping("/metadata/{aktoerId}")
    fun hentDokumentMetadata(@PathVariable("aktoerId", required = true) aktoerId: String): ResponseEntity<String> {
        auditlogger.log("hentDokumentMetadata", aktoerId)
        return VedleggControllerMetadata.measure {
            logger.info("Henter metadata for dokumenter i SAF for aktørid: $aktoerId")
            return@measure try {
                ResponseEntity.ok().body(vedleggService.hentDokumentMetadata(aktoerId).toJson())
            } catch (ex: SafException) {
                ResponseEntity.status(ex.httpStatus).body(errorBody(ex.message!!, UUID.randomUUID().toString()))
            }
        }
    }

    @ApiOperation("Henter dokumentInnhold for et JOARK dokument")
    @GetMapping("/hentdokument/{journalpostId}/{dokumentInfoId}/{variantFormat}")
    fun getDokumentInnhold(@PathVariable("journalpostId", required = true) journalpostId: String,
                           @PathVariable("dokumentInfoId", required = true) dokumentInfoId: String,
                           @PathVariable("variantFormat", required = true) variantFormat: String): ResponseEntity<String> {
        auditlogger.log("getDokumentInnhold")
        return VedleggControllerInnhold.measure {
            logger.info("Henter dokumentinnhold fra SAF for journalpostId: $journalpostId, dokumentInfoId: $dokumentInfoId")
            return@measure try {
                val hentDokumentInnholdResponse = vedleggService.hentDokumentInnhold(journalpostId, dokumentInfoId, variantFormat)
                ResponseEntity.ok().body(hentDokumentInnholdResponse.toJson())
            } catch (ex: SafException) {
                ResponseEntity.status(ex.httpStatus).body(errorBody(ex.message!!, UUID.randomUUID().toString()))
            }
        }
    }

    @ApiOperation("Legger til et vedlegg for det gitte dokumentet")
    @PutMapping("/vedlegg/{aktoerId}/{rinaSakId}/{rinaDokumentId}/{joarkJournalpostId}/{joarkDokumentInfoId}/{variantFormat}")
    fun putVedleggTilDokument(@PathVariable("aktoerId", required = true) aktoerId: String,
                              @PathVariable("rinaSakId", required = true) rinaSakId: String,
                              @PathVariable("rinaDokumentId", required = true) rinaDokumentId: String,
                              @PathVariable("joarkJournalpostId", required = true) joarkJournalpostId: String,
                              @PathVariable("joarkDokumentInfoId", required = true) joarkDokumentInfoId : String,
                              @PathVariable("variantFormat", required = true) variantFormat : String) : ResponseEntity<String> {
        auditlogger.log("putVedleggTilDokument", aktoerId)
        logger.debug("Legger til vedlegg: joarkJournalpostId: $joarkJournalpostId, joarkDokumentInfoId $joarkDokumentInfoId, variantFormat: $variantFormat til " +
                "rinaSakId: $rinaSakId, rinaDokumentId: $rinaDokumentId")

        return try {
            val dokument = vedleggService.hentDokumentInnhold(joarkJournalpostId, joarkDokumentInfoId, variantFormat)
            vedleggService.leggTilVedleggPaaDokument(aktoerId,
                    rinaSakId,
                    rinaDokumentId,
                    dokument.filInnhold,
                    dokument.fileName,
                    dokument.contentType.split("/")[1])
            return ResponseEntity.ok().body(successBody())
        } catch(ex: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody(ex.message!!, UUID.randomUUID().toString()))
        }
    }
}
