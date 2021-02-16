package no.nav.eessi.pensjon.vedlegg

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.swagger.annotations.ApiOperation
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.errorBody
import no.nav.eessi.pensjon.utils.successBody
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
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
        VedleggControllerMetadata = metricsHelper.init("VedleggControllerMetadata", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        VedleggControllerInnhold = metricsHelper.init("VedleggControllerInnhold", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }

    @ApiOperation("Henter metadata for alle dokumenter i alle journalposter for en gitt aktørid")
    @GetMapping("/metadata/{aktoerId}")
    fun hentDokumentMetadata(@PathVariable("aktoerId", required = true) aktoerId: String): ResponseEntity<String> {
        auditlogger.log("hentDokumentMetadata", aktoerId)
        return VedleggControllerMetadata.measure {
            logger.info("Henter metadata for dokumenter i SAF for aktørid: $aktoerId")
            ResponseEntity.ok().body(vedleggService.hentDokumentMetadata(aktoerId).toJson())
        }
    }

    @ApiOperation("Henter ut rina IDer fra metadata, for alle dokumenter i alle journalposter for en gitt aktørid")
    @GetMapping("/rinaiderframetadata/{aktoerId}")
    fun hentRinaIderFraMetadata(@PathVariable("aktoerId", required = true) aktoerId: String) =vedleggService.hentRinaSakIderFraMetaData(aktoerId)

    @ApiOperation("Henter dokumentInnhold for et JOARK dokument")
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
            val dokumentMetadata = vedleggService.hentDokumentMetadata(aktoerId, joarkJournalpostId, joarkDokumentInfoId)
            val dokument = vedleggService.hentDokumentInnhold(joarkJournalpostId, joarkDokumentInfoId, variantFormat)

            val documentName = dokumentMetadata?.tittel ?: dokument.fileName

            vedleggService.leggTilVedleggPaaDokument(aktoerId,
                    rinaSakId,
                    rinaDokumentId,
                    dokument.filInnhold,
                    "$documentName.pdf",
                    dokument.contentType.split("/")[1])
            return ResponseEntity.ok().body(successBody())
        } catch(ex: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody(ex.message!!, UUID.randomUUID().toString()))
        }
    }
}
