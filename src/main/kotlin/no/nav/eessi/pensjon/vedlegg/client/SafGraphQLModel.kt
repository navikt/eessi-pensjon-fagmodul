package no.nav.eessi.pensjon.vedlegg.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.eessi.pensjon.utils.mapAnyToJson

/**
 * Request og responsemodell for SAF GraphQL tjeneste
 * se https://confluence.adeo.no/display/BOA/saf+-+Utviklerveiledning
 */

data class SafRequest(
        val query: String = "query dokumentoversiktBruker(\$brukerId: BrukerIdInput!, \$foerste: Int!, \$journalstatuser: [Journalstatus]) {dokumentoversiktBruker(brukerId: \$brukerId, journalstatuser: \$journalstatuser, foerste:\$foerste) {" +
                "journalposter {" +
                    "tilleggsopplysninger {" +
                        "nokkel " +
                        "verdi " +
                    "}" +
                    "journalpostId " +
                    "datoOpprettet " +
                    "tittel " +
                    "tema " +
                    "dokumenter {" +
                        "dokumentInfoId " +
                        "tittel " +
                        "dokumentvarianter {" +
                            "filnavn " +
                            "variantformat" +
                        "} " +
                    "} " +
                    "relevanteDatoer {" +
                        "dato " +
                        "datotype " +
                    "}" +
                "}}}",
        val variables: Variables
) {
    fun toJson(): String {
        return mapAnyToJson(this, false)
    }
}

data class Variables(
        val brukerId: BrukerId,
        val foerste: Int,
        val journalstatuser: List<String> = listOf("JOURNALFOERT", "FERDIGSTILT", "EKSPEDERT", "MOTTATT")
)


data class BrukerId(
        val id: String,
        val type: BrukerIdType
)

enum class BrukerIdType {
    FNR,
    AKTOERID
}

/**
 * https://confluence.adeo.no/display/BOA/Enum%3A+Variantformat
 */
enum class VariantFormat {
    ARKIV,
    FULLVERSJON,
    PRODUKSJON,
    PRODUKSJON_DLF,
    SLADDET,
    ORIGINAL
}

data class HentMetadataResponse (val data: Data) {
    fun toJson(): String {
        return mapAnyToJson(this, false)
    }
}

data class Data(val dokumentoversiktBruker: DokumentoversiktBruker)

data class DokumentoversiktBruker(val journalposter: List<Journalpost>)


@JsonInclude(JsonInclude.Include.NON_NULL)
data class Journalpost(
        val tilleggsopplysninger: List<Map<String, String>>,
        val journalpostId: String,
        val datoOpprettet: String,
        val tittel: String?,
        val tema: String,
        val dokumenter: List<Dokument>,
        val relevanteDatoer: List<RelevantDato>? = null
)

// https://confluence.adeo.no/display/BOA/Type%3A+RelevantDato
class RelevantDato (
    val dato: String,
    val datotype: String
)

data class Dokument(
        val dokumentInfoId: String,
        val tittel: String?,
        val dokumentvarianter: List<Dokumentvarianter>
)

data class Dokumentvarianter(
        val filnavn: String?,
        val variantformat: VariantFormat
)

class HentdokumentInnholdResponse (
        val filInnhold: String,
        val fileName: String,
        val contentType: String
)
{
    fun toJson(): String {
        return mapAnyToJson(this, false)
    }
}



