package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.shared.api.InstitusjonItem
import no.nav.eessi.pensjon.utils.toJson

class BucAndSedSubject(
        val gjenlevende: SubjectFnr? = null,
        val avdod: SubjectFnr? = null
)

class SubjectFnr(
        val fnr: String? = null
)

data class BucAndSedView(
    val type: String,
    val caseId: String,
    val internationalId: String,
    val creator: InstitusjonItem? = null,
    val sakType: String? = null,
    val status: String? = null,
    val startDate: String? = null,
    val lastUpdate: String? = null,
    val institusjon: List<InstitusjonItem>? = null,
    val seds: List<DocumentsItem>? = null,
    val error: String? = null,
    val readOnly: Boolean? = false,
    val subject: BucAndSedSubject? = null,
    val cdm: String? = null
) {
    override fun toString(): String {
        return toJson()
    }

    companion object {
        fun fromErr(error: String?): BucAndSedView {
            return BucAndSedView(
                    type = "",
                    caseId = "",
                    internationalId = "",
                    error = error
            )
        }

        fun from(buc: Buc) = from(buc, null)

        private fun checkForReadOnly(buc: Buc): Boolean {
            return when (buc.processDefinitionName) {
                "R_BUC_02" -> true
                else -> false
            }
        }

        fun from(buc: Buc, gjenlevendeFnr: String, avdodFnr: String): BucAndSedView {
            return from(buc, subject(gjenlevendeFnr, avdodFnr))
        }

        fun from(buc: Buc, subject: BucAndSedSubject? = null): BucAndSedView {
            val bucUtil = BucUtils(buc)
            return BucAndSedView(
                readOnly = checkForReadOnly(buc),
                type = bucUtil.getProcessDefinitionName() ?: "",
                creator = bucUtil.getCaseOwnerOrCreator(),
                caseId = buc.id ?: "n/a",
                internationalId = buc.internationalId ?: "n/a",
                startDate = buc.startDate,
                lastUpdate = buc.lastUpdate,
                status = buc.status,
                institusjon = bucUtil.getParticipants().map {
                    InstitusjonItem(
                        country = it.organisation?.countryCode ?: "",
                        institution = it.organisation?.id ?: "",
                        name = it.organisation?.name,
                        acronym = it.organisation?.acronym
                    )
                },
                seds = bucUtil.getAllDocuments(),
                subject = subject,
                cdm = buc.processDefinitionVersion
            )
        }

        fun subject(gjenlevendeFnr: String, avdodFnr: String): BucAndSedSubject = BucAndSedSubject(
                    gjenlevende = SubjectFnr(gjenlevendeFnr),
                    avdod = SubjectFnr(avdodFnr)
            )

    }
}


