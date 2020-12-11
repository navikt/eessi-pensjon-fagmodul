package no.nav.eessi.pensjon.fagmodul.prefill.model

import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED

object PrefillDataModelMother {

    fun initialPrefillDataModel(sedType: String,
                                pinId: String,
                                vedtakId: String? = null,
                                penSaksnummer: String = "12345",
                                avdod: PersonId? = null,
                                kravDato: String? = null,
                                kravId: String? = null,
                                refTilPerson: ReferanseTilPerson? = null,
                                euxCaseId: String = "123456",
                                bucType: String = "P_BUC_99",
                                institution: List<InstitusjonItem> = listOf(InstitusjonItem(country = "NO", institution = "DUMMY"))) =
            PrefillDataModel(
                    penSaksnummer,
                    bruker = PersonId(pinId, "123456789"),
                    avdod = avdod,
                    sedType = sedType,
                    sed = SED(sedType),
                    vedtakId = vedtakId ?: "",
                    buc = bucType,
                    institution = institution,
                    kravDato = kravDato,
                    kravId = kravId,
                    refTilPerson = refTilPerson,
                    euxCaseID = euxCaseId
            )
}

