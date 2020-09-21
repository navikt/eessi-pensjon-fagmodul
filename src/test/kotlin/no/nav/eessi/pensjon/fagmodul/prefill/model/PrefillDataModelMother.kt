package no.nav.eessi.pensjon.fagmodul.prefill.model

import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED

object PrefillDataModelMother {
    fun initialPrefillDataModel(sedType: String,
                                pinId: String,
                                vedtakId: String = "12312312",
                                penSaksnummer: String = "12345",
                                avdod: PersonId? = null,
                                kravDato: String? = null,
                                kravId: String? = null) =
            PrefillDataModel(penSaksnummer, bruker = PersonId(pinId, "123456789"), avdod = avdod).apply {
                rinaSubject = "Pensjon"
                sed = SED(sedType)
                this.vedtakId = vedtakId
                buc = "P_BUC_99"
                institution = listOf(InstitusjonItem(country = "NO", institution = "DUMMY"))
                this.kravDato = kravDato
                this.kravId = kravId
            }
}

