package no.nav.eessi.pensjon.vedlegg

import no.nav.eessi.pensjon.vedlegg.client.HentMetadataResponse
import no.nav.eessi.pensjon.vedlegg.client.HentdokumentInnholdResponse
import no.nav.eessi.pensjon.vedlegg.client.SafClient
import no.nav.eessi.pensjon.vedlegg.client.VariantFormat
import org.springframework.stereotype.Service

@Service
class VedleggService(private val safClient: SafClient) {

    fun hentDokumentMetadata(aktoerId: String): HentMetadataResponse {
        return safClient.hentDokumentMetadata(aktoerId)
    }

    fun hentDokumentInnhold(journalpostId: String,
                           dokumentInfoId: String,
                           variantFormat: VariantFormat): HentdokumentInnholdResponse {
        return safClient.hentDokumentInnhold(journalpostId, dokumentInfoId, variantFormat)
    }

    fun hentRinaSakIderFraDokumentMetadata(aktoerId: String): List<String> {
        return safClient.hentRinaSakIderFraDokumentMetadata(aktoerId)
    }
}