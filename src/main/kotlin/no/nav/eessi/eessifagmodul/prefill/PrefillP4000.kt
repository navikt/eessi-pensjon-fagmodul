package no.nav.eessi.eessifagmodul.prefill

import no.nav.eessi.eessifagmodul.models.PersonArbeidogOppholdUtland
import no.nav.eessi.eessifagmodul.models.SED
import no.nav.eessi.eessifagmodul.prefill.nav.PrefillPerson
import no.nav.eessi.eessifagmodul.utils.mapJsonToAny
import no.nav.eessi.eessifagmodul.utils.typeRefs

class PrefillP4000(private val prefillPerson: PrefillPerson) : Prefill<SED> {

    override fun prefill(prefillData: PrefillDataModel): SED {

        prefillData.getPartSEDasJson("P4000")?.let {

            val trygdeTid = mapJsonToAny(it, typeRefs<PersonArbeidogOppholdUtland>())

            val sed = prefillPerson.prefill(prefillData)
            sed.trygdetid = trygdeTid
            prefillData.sed = sed
        }

        return prefillData.sed

    }
}