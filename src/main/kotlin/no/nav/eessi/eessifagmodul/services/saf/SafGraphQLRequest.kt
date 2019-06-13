package no.nav.eessi.eessifagmodul.services.saf

import no.nav.eessi.eessifagmodul.utils.mapAnyToJson

/**
 * Request og responsemodell for SAF GraphQL tjeneste
 * @see https://confluence.adeo.no/display/BOA/saf+-+Utviklerveiledning
 */

data class SafRequest(
        val query: String = "query dokumentoversiktBruker(\$brukerId: BrukerIdInput!, \$foerste: Int!) {dokumentoversiktBruker(brukerId: \$brukerId, foerste:\$foerste) {journalposter {journalpostId tittel journalposttype journalstatus tema dokumenter {dokumentInfoId tittel}}}}",
        val variables: Variables
) {
    fun toJson(): String {
        return mapAnyToJson(this, false)
    }

    fun toJsonSkipEmpty(): String {
        return mapAnyToJson(this, true)
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





//
//data class SafGraphQLRequest(
//        val dokumentoversiktBrukerId: DokumentoversiktBrukerId,
//        val journalposter: Journalposter
//)
//
//data class DokumentoversiktBrukerId(
//        val brukerId: BrukerId,
//        val foerste: String = "1000"
//)
//
//data class BrukerId(
//        val id: String,
//        val type: BrukerIdType
//)
//
//enum class BrukerIdType {
//    FNR,
//    AKTOERID
//}
//
//data class Journalposter(
//        val journalpostId: Dokumenter
//)
//
//data class Dokumenter(
//        val dokumentInfoId: String
//)



