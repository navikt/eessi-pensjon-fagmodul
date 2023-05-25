package no.nav.eessi.pensjon.shared.api

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

//Data struktur for bruk av apirequest(frontend) og utfyllingdata (backend)
@JsonIgnoreProperties(ignoreUnknown = true)
data class InstitusjonItem(
        val country: String,
        val institution: String,
        val name: String? = null,
        val acronym: String? = null,
        val defaultInstitution: Boolean = false,
        val liasonBody: Boolean = false,
) {

        //sjekker på Instisjon legger ut ID til rina som <XX:ZZZZZ>
        fun checkAndConvertInstituion(): String {
            if (institution.contains(":")) {
                return institution
            }
            return "$country:$institution"
        }
}