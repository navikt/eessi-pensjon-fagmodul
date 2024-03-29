package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_06
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.P6000
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.shared.api.ApiRequest
import no.nav.eessi.pensjon.shared.api.InstitusjonItem

open class EuxTestUtils {

    companion object {
        fun apiRequestWith(
            euxCaseId: String,
            institutions: List<InstitusjonItem> = listOf(),
            sed: SedType? = P6000,
            buc: BucType? = P_BUC_06
        ): ApiRequest {
            return ApiRequest(
                subjectArea = "Pensjon",
                sakId = "EESSI-PEN-123",
                euxCaseId = euxCaseId,
                vedtakId = "1234567",
                institutions = institutions,
                sed = sed,
                buc = buc,
                aktoerId = "0105094340092"
            )
        }

        fun createDummyBucDocumentItem(): DocumentsItem {
            return DocumentsItem(
                id = "3123123",
                type = P6000,
                status = "empty",
                allowsAttachments = true,
                direction = "OUT"
            )
        }

        fun dummyRequirement(dummyparam1: String?, dummyparam2: String?): Boolean{
            require(!(dummyparam1 == null && dummyparam2 == null)) { "Minst et søkekriterie må fylles ut for å få et resultat fra Rinasaker" }
            return true
        }
    }
}