package no.nav.eessi.eessifagmodul.prefill

import no.nav.eessi.eessifagmodul.models.SED
import no.nav.eessi.eessifagmodul.prefill.nav.PrefillPerson

class PrefillP3000(private val prefillPerson: PrefillPerson) : Prefill<SED> {

    override fun prefill(prefillData: PrefillDataModel): SED {

        //setter NAVSED til P3000_NO vi skal vel ikke sende den ut?
        //prefillData.sed.sed = "P3000_NO"
        return prefillPerson.prefill(prefillData)
    }

}