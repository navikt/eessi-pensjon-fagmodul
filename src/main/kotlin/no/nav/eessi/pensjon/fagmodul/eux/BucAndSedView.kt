package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ShortDocumentItem
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
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
        val creator: InstitusjonItem? = null,
        val sakType: String? = null,
        val status: String? = null,
        val startDate: Long? = null,
        val lastUpdate: Long? = null,
        val institusjon: List<InstitusjonItem>? = null,
        val seds: List<ShortDocumentItem>? = null,
        val error: String? = null,
        val readOnly: Boolean? = false,
        val subject: BucAndSedSubject? = null
) {
    override fun toString(): String {
        return toJson()
    }

    companion object {
        fun fromErr(error: String?): BucAndSedView {
            return BucAndSedView(
                    type = "",
                    caseId = "",
                    error = error
            )
        }

        fun checkForReadOnly(buc: Buc): Boolean {
            return when (buc.processDefinitionName) {
                "R_BUC_02" -> true
                else -> false
            }
        }

        fun from(buc: Buc) = from(buc, null)

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
                    startDate = bucUtil.getStartDateLong(),
                    lastUpdate = bucUtil.getLastDateLong(),
                    status = bucUtil.getStatus(),
                    institusjon = bucUtil.getParticipants().map {
                        InstitusjonItem(
                                country = it.organisation?.countryCode ?: "",
                                institution = it.organisation?.id ?: "",
                                name = it.organisation?.name,
                                acronym = it.organisation?.acronym
                        )
                    },
                    seds = bucUtil.getAllDocuments(),
                    subject = subject
            )
        }

        fun subject(gjenlevendeFnr: String, avdodFnr: String): BucAndSedSubject = BucAndSedSubject(
                    gjenlevende = SubjectFnr(gjenlevendeFnr),
                    avdod = SubjectFnr(avdodFnr)
            )

    }
}


