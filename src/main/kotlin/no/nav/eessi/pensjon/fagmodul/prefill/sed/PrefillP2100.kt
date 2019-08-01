package no.nav.eessi.pensjon.fagmodul.prefill.sed

import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel

class PrefillP2100 : Prefill<SED> {

    override fun prefill(prefillData: PrefillDataModel): SED {
        return prefillData.sed
    }
}