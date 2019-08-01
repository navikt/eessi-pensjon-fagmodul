package no.nav.eessi.pensjon.fagmodul.prefill.sed

import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPerson

class PrefillP3000(private val prefillPerson: PrefillPerson) : Prefill<SED> {

    override fun prefill(prefillData: PrefillDataModel): SED {
        //setter NAVSED til P3000_NO vi skal vel ikke sende den ut?
        return prefillPerson.prefill(prefillData)
    }
}