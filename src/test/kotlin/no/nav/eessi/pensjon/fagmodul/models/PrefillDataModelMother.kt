package no.nav.eessi.pensjon.fagmodul.models

import no.nav.eessi.pensjon.eux.model.sed.SedType

object PrefillDataModelMother {

    fun initialPrefillDataModel(sedType: SedType,
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
                    vedtakId = vedtakId ?: "",
                    buc = bucType,
                    institution = institution,
                    kravDato = kravDato,
                    kravId = kravId,
                    refTilPerson = refTilPerson,
                    euxCaseID = euxCaseId
            )
}

