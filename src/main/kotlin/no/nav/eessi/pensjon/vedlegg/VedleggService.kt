package no.nav.eessi.pensjon.vedlegg

import no.nav.eessi.pensjon.eux.model.buc.MissingBuc
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.vedlegg.client.Dokument
import no.nav.eessi.pensjon.vedlegg.client.EuxVedleggClient
import no.nav.eessi.pensjon.vedlegg.client.HentMetadataResponse
import no.nav.eessi.pensjon.vedlegg.client.HentdokumentInnholdResponse
import no.nav.eessi.pensjon.vedlegg.client.SafClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

@Service
class VedleggService(private val safClient: SafClient,
                     private val euxVedleggClient: EuxVedleggClient,
                     @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()) {

    private final val TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY = "eessi_pensjon_bucid"
    private val logger = LoggerFactory.getLogger(VedleggService::class.java)

    private lateinit var HentRinaSakIderFraDokumentMetadata: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        HentRinaSakIderFraDokumentMetadata = metricsHelper.init("HentRinaSakIderFraDokumentMetadata", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }

    fun hentDokumentMetadata(aktoerId: String): HentMetadataResponse {
        return safClient.hentDokumentMetadata(aktoerId)
    }

    fun hentDokumentMetadata(aktoerId: String,
                             journalpostId : String,
                             dokumentInfoId: String) : Dokument? {
        val alleMetadataForAktoerId = safClient.hentDokumentMetadata(aktoerId)

        return alleMetadataForAktoerId.data.dokumentoversiktBruker.journalposter
                .filter { it.journalpostId == journalpostId }
                .flatMap { it.dokumenter }
                .firstOrNull { it.dokumentInfoId == dokumentInfoId }
    }

    fun hentDokumentInnhold(journalpostId: String,
                           dokumentInfoId: String,
                           variantFormat: String): HentdokumentInnholdResponse {
        return safClient.hentDokumentInnhold(journalpostId, dokumentInfoId, variantFormat)
    }

    fun leggTilVedleggPaaDokument(aktoerId: String,
                                  rinaSakId: String,
                                  rinaDokumentId: String,
                                  filInnhold: String,
                                  fileName: String,
                                  filtype: String) {
        euxVedleggClient.leggTilVedleggPaaDokument(aktoerId, rinaSakId, rinaDokumentId, filInnhold, fileName, filtype)
    }

    /**
     * Returnerer en distinct liste av rinaSakIDer basert på tilleggsinformasjon i journalposter for en aktør
     */
    fun hentRinaSakIderFraMetaData(aktoerId: String): List<String> =
        HentRinaSakIderFraDokumentMetadata.measure {
            hentDokumentMetadata(aktoerId).data.dokumentoversiktBruker.journalposter
                .flatMap { journalpost ->
                    journalpost.tilleggsopplysninger
                        .filter { it["nokkel"].equals(TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY) }
                        .filter { it["verdi"] != null }
                        .map { it["verdi"]!! }
                }.filterNot { MissingBuc.checkForMissingBuc(it) }
                .distinct()
                .also { logger.info("Fant følgende RINAID fra dokument Metadata: ${it.map { str -> str }}") }
        }
}