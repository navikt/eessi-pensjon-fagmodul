package no.nav.eessi.pensjon.vedlegg

import no.nav.eessi.pensjon.eux.model.buc.MissingBuc
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.vedlegg.client.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.retry.RetryCallback
import org.springframework.retry.RetryContext
import org.springframework.retry.RetryListener
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.io.IOException

@Service
class VedleggService(private val safClient: SafClient,
                     private val euxVedleggClient: EuxVedleggClient,
                     @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()) {

    private final val TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY = "eessi_pensjon_bucid"
    private val logger = LoggerFactory.getLogger(VedleggService::class.java)

    private lateinit var HentRinaSakIderFraDokumentMetadata: MetricsHelper.Metric

    init {
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
    @Retryable(
        exclude = [IOException::class],
        backoff = Backoff(delayExpression = "@euxKlientVedleggServiceRetryConfig.initialRetryMillis", maxDelay = 200000L, multiplier = 3.0),
        listeners  = ["euxKlientVedleggServiceRetryLogger"]
    )
    fun hentRinaSakIderFraMetaData(aktoerId: String): List<String> =
        hentDokumentMetadata(aktoerId).data.dokumentoversiktBruker.journalposter
            .flatMap { journalpost ->
                journalpost.tilleggsopplysninger
                    .filter { it["nokkel"].equals(TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY) }
                    .filter { it["verdi"] != null }
                    .map { it["verdi"]!! }
            }.filterNot { rinaId -> MissingBuc.checkForMissingBuc(rinaId).also { if(it) logger.info("Fjernet missing buc") } }
            .distinct()
            .also { logger.info("Fant følgende RINAID fra dokument Metadata: ${it.map { str -> str }}") }

    @Retryable(
        exclude = [IOException::class],
        backoff = Backoff(delayExpression = "@euxKlientVedleggServiceRetryConfig.initialRetryMillis", maxDelay = 200000L, multiplier = 3.0),
        listeners  = ["euxKlientVedleggServiceRetryLogger"]
    )
    fun hentRinaSakerFraMetaForOmstillingstonad(aktoerId: String): List<String> =
        hentDokumentMetadata(aktoerId).data.dokumentoversiktBruker.journalposter
            .filter { it.tema.contains("omstilling") }
            .flatMap { journalpost ->
                journalpost.tilleggsopplysninger
                    .filter { it["nokkel"].equals(TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY) }
                    .filter { it["verdi"] != null }
                    .map { it["verdi"]!! }
            }.filterNot {
                if (MissingBuc.checkForMissingBuc(it)) {
                    logger.info("$it ligger i listen MissingBuc.checkForMissingBuc og filtreres bort")
                    true
                } else {
                    false
                }
            }
            .distinct()
            .also { logger.info("Fant følgende RINAID for omstilling fra dokument Metadata: ${it.map { str -> str }}") }
}


//TODO: flytte disse til et felles sted, ev en annen løsning
@Profile("!retryConfigOverride")
@Component
data class EuxKlientVedleggServiceRetryConfig(val initialRetryMillis: Long = 20000L)

@Component
class EuxKlientVedleggServiceRetryLogger : RetryListener {
    private val logger = LoggerFactory.getLogger(EuxKlientVedleggServiceRetryLogger::class.java)
    override fun <T : Any?, E : Throwable?> onError(context: RetryContext?, callback: RetryCallback<T, E>?, throwable: Throwable?) {
        logger.warn("Feil under henting fra EUX - try #${context?.retryCount } - ${throwable?.toString()}", throwable)
    }
}
