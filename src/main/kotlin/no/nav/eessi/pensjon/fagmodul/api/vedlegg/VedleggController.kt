package no.nav.eessi.pensjon.fagmodul.api.vedlegg

import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.fagmodul.api.FrontEndResponse
import no.nav.eessi.pensjon.utils.successBody
import no.nav.eessi.pensjon.fagmodul.api.vedlegg.client.HentMetadataResponse
import no.nav.eessi.pensjon.fagmodul.api.vedlegg.client.HentdokumentInnholdResponse
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.Base64

@Protected
@RestController
@RequestMapping("/saf")
class VedleggController(private val vedleggService: VedleggService,
                        private val auditlogger: AuditLogger,
                        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()) {
    private val logger = LoggerFactory.getLogger(VedleggController::class.java)

    private  var vedleggControllerMetadata: MetricsHelper.Metric
    private  var vedleggControllerInnhold: MetricsHelper.Metric
    init {
        vedleggControllerMetadata = metricsHelper.init("VedleggControllerMetadata", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        vedleggControllerInnhold = metricsHelper.init("VedleggControllerInnhold", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }

    @GetMapping("/metadata/{aktoerId}")
    fun hentDokumentMetadata(@PathVariable("aktoerId", required = true) aktoerId: String): FrontEndResponse<HentMetadataResponse> {
        auditlogger.log("hentDokumentMetadata", aktoerId)
        return vedleggControllerMetadata.measure {
            logger.info("Henter metadata for dokumenter i SAF for aktørid: $aktoerId")
            FrontEndResponse(vedleggService.hentDokumentMetadata(aktoerId), HttpStatus.OK.name)
        }
    }

    @GetMapping("/rinaiderframetadata/{aktoerId}")
    fun hentRinaIderFraMetadata(@PathVariable("aktoerId", required = true) aktoerId: String) = vedleggService.hentRinaSakIderFraMetaData(aktoerId).also {  auditlogger.log("hentRinaIderFraMetadata", aktoerId) }

    @GetMapping("/hentdokument/{journalpostId}/{dokumentInfoId}/{variantFormat}")
    fun getDokumentInnhold(@PathVariable("journalpostId", required = true) journalpostId: String,
                           @PathVariable("dokumentInfoId", required = true) dokumentInfoId: String,
                           @PathVariable("variantFormat", required = true) variantFormat: String): FrontEndResponse<HentdokumentInnholdResponse> {
        auditlogger.logBuc("getDokumentInnhold", "journalpostId:$journalpostId, documentId:$dokumentInfoId")
        return vedleggControllerInnhold.measure {
            logger.info("Henter dokumentinnhold fra SAF for journalpostId: $journalpostId, dokumentInfoId: $dokumentInfoId")
            val hentDokumentInnholdResponse = vedleggService.hentDokumentInnhold(journalpostId, dokumentInfoId, variantFormat)
            FrontEndResponse(hentDokumentInnholdResponse, HttpStatus.OK.name)
        }
    }

    @PutMapping("/vedlegg/{aktoerId}/{rinaSakId}/{rinaDokumentId}/{joarkJournalpostId}/{joarkDokumentInfoId}/{variantFormat}")
    fun putVedleggTilDokument(@PathVariable("aktoerId", required = true) aktoerId: String,
                              @PathVariable("rinaSakId", required = true) rinaSakId: String,
                              @PathVariable("rinaDokumentId", required = true) rinaDokumentId: String,
                              @PathVariable("joarkJournalpostId", required = true) joarkJournalpostId: String,
                              @PathVariable("joarkDokumentInfoId", required = true) joarkDokumentInfoId : String,
                              @PathVariable("variantFormat", required = true) variantFormat : String) : ResponseEntity<FrontEndResponse<String>> {
        auditlogger.log("putVedleggTilDokument", aktoerId)
        auditlogger.logBuc("putVedleggTilDokument", "euxCaseId:$rinaSakId, documentId:$rinaDokumentId, journalpostId:$joarkJournalpostId")
        logger.debug("Legger til vedlegg: joarkJournalpostId: $joarkJournalpostId, joarkDokumentInfoId $joarkDokumentInfoId, variantFormat: $variantFormat til " +
                "rinaSakId: $rinaSakId, rinaDokumentId: $rinaDokumentId")

        return try {
            val dokumentMetadata = vedleggService.hentDokumentMetadata(aktoerId, joarkJournalpostId, joarkDokumentInfoId)
            val dokument = vedleggService.hentDokumentInnhold(joarkJournalpostId, joarkDokumentInfoId, variantFormat)

            val documentName = dokumentMetadata?.tittel ?: dokument.fileName
            val sizeBytes = Base64.getDecoder().decode(dokument.filInnhold).size
            logger.info("Legger til vedlegg: $documentName for rinasak: $rinaSakId, størrelse: $sizeBytes bytes")
            vedleggService.leggTilVedleggPaaDokument(aktoerId,
                    rinaSakId,
                    rinaDokumentId,
                    dokument.filInnhold,
                    "$documentName.pdf",
                    dokument.contentType.split("/")[1])
            logger.info("Vedlegg er lagt til for rinasak. $rinaSakId")
            return ResponseEntity.ok(FrontEndResponse(result = successBody(), status = HttpStatus.OK.name))
        } catch (ex: Exception) {
            logger.error("PutVedleggTilDokument feiler med ${ex.message}")
            if (ex.message?.contains("403") == true) {
                val messageWithReplacedNumbers = ex.message!!.replace(Regex("\\d+"), "").trim()
                ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    FrontEndResponse(result = null, status = HttpStatus.FORBIDDEN.name, message = messageWithReplacedNumbers)
                )
            } else {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    FrontEndResponse(result = null, status = HttpStatus.INTERNAL_SERVER_ERROR.name, message = ex.message)
                )
            }
        }
    }
}
