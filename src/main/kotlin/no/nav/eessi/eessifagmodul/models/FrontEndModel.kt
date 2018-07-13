package no.nav.eessi.eessifagmodul.models

//Request from frontend
//{"institutions":[{"NO:"DUMMY"}],"buc":"P_BUC_06","sed":"P6000","caseId":"caseId","subjectArea":"pensjon"}
data class FrontendRequest(
        val subjectArea: String? = null,
        val caseId: String? = null,
        val buc: String? = null,
        val sed : String? = null,
        val institutions: List<Institusjon>? = null,
        var pinid: String? = null
)

data class Institusjon(
        val country: String? = null,
        val institution: String? =null
)

