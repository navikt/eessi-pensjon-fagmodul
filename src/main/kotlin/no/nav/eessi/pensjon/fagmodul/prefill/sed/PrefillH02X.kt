package no.nav.eessi.pensjon.fagmodul.prefill.sed

import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillSed
import no.nav.eessi.pensjon.fagmodul.sedmodel.Bruker
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Person
import no.nav.eessi.pensjon.fagmodul.sedmodel.PinLandItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED

class PrefillH02X(private val prefillSed: PrefillSed)  {

    fun prefill(prefillData: PrefillDataModel, personData: PersonDataCollection): SED {

        val personSed = prefillSed.prefill(
                prefillData = prefillData,
                personData = personData
        )

        val pinitem = personSed.nav?.bruker?.person?.pin?.first()

        val pinLand = PinLandItem(
                oppholdsland = pinitem?.land,
                kompetenteuland = pinitem?.identifikator
        )

        return SED(
            type = prefillData.sedType,
            nav = Nav(
                bruker = Bruker(
                    person = Person(
                        pinland = pinLand
                    )
                )
            )
        )

    }
}