package no.nav.eessi.pensjon.vedlegg

import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.errorBody
import no.nav.eessi.pensjon.utils.successBody
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@Protected
@RestController
@RequestMapping("/saf")
class VedleggController(private val vedleggService: VedleggService,
                        private val auditlogger: AuditLogger,
                        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()) {
    private val logger = LoggerFactory.getLogger(VedleggController::class.java)

    private lateinit var VedleggControllerMetadata: MetricsHelper.Metric
    private lateinit var VedleggControllerInnhold: MetricsHelper.Metric
    init {
        VedleggControllerMetadata = metricsHelper.init("VedleggControllerMetadata", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        VedleggControllerInnhold = metricsHelper.init("VedleggControllerInnhold", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }

    @GetMapping("/metadata/{aktoerId}")
    fun hentDokumentMetadata(@PathVariable("aktoerId", required = true) aktoerId: String): ResponseEntity<String> {
        auditlogger.log("hentDokumentMetadata", aktoerId)
        return VedleggControllerMetadata.measure {
            logger.info("Henter metadata for dokumenter i SAF for aktørid: $aktoerId")
            ResponseEntity.ok().body(vedleggService.hentDokumentMetadata(aktoerId).toJson())
        }
    }

    @GetMapping("/rinaiderframetadata/{aktoerId}")
    fun hentRinaIderFraMetadata(@PathVariable("aktoerId", required = true) aktoerId: String) =vedleggService.hentRinaSakIderFraMetaData(aktoerId)

    @GetMapping("/hentdokument/{journalpostId}/{dokumentInfoId}/{variantFormat}")
    fun getDokumentInnhold(@PathVariable("journalpostId", required = true) journalpostId: String,
                           @PathVariable("dokumentInfoId", required = true) dokumentInfoId: String,
                           @PathVariable("variantFormat", required = true) variantFormat: String): ResponseEntity<String> {
        auditlogger.log("getDokumentInnhold")
        return VedleggControllerInnhold.measure {
            logger.info("Henter dokumentinnhold fra SAF for journalpostId: $journalpostId, dokumentInfoId: $dokumentInfoId")
            val hentDokumentInnholdResponse = vedleggService.hentDokumentInnhold(journalpostId, dokumentInfoId, variantFormat)
            ResponseEntity.ok().body(hentDokumentInnholdResponse.toJson())
        }
    }

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
            val dokumentMetadata = vedleggService.hentDokumentMetadata(aktoerId, joarkJournalpostId, joarkDokumentInfoId)
            val dokument = vedleggService.hentDokumentInnhold(joarkJournalpostId, joarkDokumentInfoId, variantFormat)

            val documentName = dokumentMetadata?.tittel ?: dokument.fileName

            vedleggService.leggTilVedleggPaaDokument(aktoerId,
                    rinaSakId,
                    rinaDokumentId,
                    dokument.filInnhold,
                    "$documentName.pdf",
                    dokument.contentType.split("/")[1])
            logger.info("Vedlegg er lagt til for rinasak. $rinaSakId")
            return ResponseEntity.ok().body(successBody())
        } catch (ex: Exception) {
            logger.warn("PutVedleggTilDokument feiler med ${ex.message}")
            if (ex.message?.contains("403") == true) {
                val messageWithReplacedNumbers = ex.message!!.replace(Regex("\\d+"), "").trim()
                ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody(messageWithReplacedNumbers, UUID.randomUUID().toString()))
            } else {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(ex.message!!, UUID.randomUUID().toString()))
            }
        }
    }
}
