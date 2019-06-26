package no.nav.eessi.eessifagmodul.services.saf

import no.nav.eessi.eessifagmodul.utils.mapAnyToJson

/**
 * Request og responsemodell for SAF GraphQL tjeneste
 * @see https://confluence.adeo.no/display/BOA/saf+-+Utviklerveiledning
 */

data class SafRequest(
        val query: String = "query dokumentoversiktBruker(\$brukerId: BrukerIdInput!, \$foerste: Int!) {dokumentoversiktBruker(brukerId: \$brukerId, foerste:\$foerste) {" +
                "journalposter {" +
                    "tilleggsopplysninger {" +
                        "nokkel " +
                        "verdi " +
                    "}" +
                    "journalpostId " +
                    "tittel " +
                    "tema " +
                    "dokumenter {" +
                        "dokumentInfoId " +
                        "tittel " +
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
        val foerste: Int
)


data class BrukerId(
        val id: String,
        val type: BrukerIdType
)

enum class BrukerIdType {
    FNR,
    AKTOERID
}

data class HentMetadataResponse (val data: Data) {
    fun toJson(): String {
        return mapAnyToJson(this, false)
    }
}

data class Data(val dokumentoversiktBruker: DokumentoversiktBruker)

data class DokumentoversiktBruker(val journalposter: List<Journalpost>)

data class Journalpost(
        val tilleggsopplysninger: List<Map<String, String>>,
        val journalpostId: String,
        val tittel: String,
        val tema: String,
        val dokumenter: List<Dokument>,
        val relevanteDatoer: List<DatoHolder>
)

data class Dokument(
        val dokumentInfoId: String,
        val tittel: String
)

data class DatoHolder(
        val dato: String,
        val datotype: String
)

data class HentdokumentResponse (
        val base64: String,
        val fileName: String,
        val contentType: String
)
{
    fun toJson(): String {
        return mapAnyToJson(this, false)
    }
}



