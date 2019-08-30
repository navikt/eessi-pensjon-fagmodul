package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ShortDocumentItem
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem

data class BucAndSedView(
        val type: String,
        val creator: InstitusjonItem? = null,
        val caseId: String,
        val sakType: String? = null,
        val aktoerId: String? =null,
        val status: String? = null,
        val startDate: Long? = null,
        val lastUpdate: Long? = null,
        val institusjon: List<InstitusjonItem>? = null,
        val seds: List<ShortDocumentItem>? = null
) {
    companion object {
        fun from(buc: Buc, euxCaseId: String, aktoerid: String): BucAndSedView {
            val bucUtil = BucUtils(buc)
            return BucAndSedView(
                    type = bucUtil.getProcessDefinitionName()!!,
                    creator = InstitusjonItem(
                            country = bucUtil.getCreator()?.organisation?.countryCode ?: "",
                            institution = bucUtil.getCreator()?.organisation?.id ?: "",
                            name = bucUtil.getCreator()?.name
                    ),
                    caseId = euxCaseId,
                    sakType = "",
                    startDate = bucUtil.getStartDateLong(),
                    lastUpdate = bucUtil.getLastDateLong(),
                    aktoerId = aktoerid,
                    status = bucUtil.getStatus(),
                    institusjon = bucUtil.getParticipants().map {
                        InstitusjonItem(
                                country = it.organisation?.countryCode ?: "",
                                institution = it.organisation?.id ?: "",
                                name = it.organisation?.name
                        )
                    },
                    seds = bucUtil.getAllDocuments()
            )
        }
    }
}


