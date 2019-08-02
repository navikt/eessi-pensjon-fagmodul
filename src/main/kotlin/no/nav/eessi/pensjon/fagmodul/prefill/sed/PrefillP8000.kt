package no.nav.eessi.pensjon.fagmodul.prefill.sed

import no.nav.eessi.pensjon.fagmodul.sedmodel.EessisakItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.PinItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Person
import no.nav.eessi.pensjon.fagmodul.sedmodel.Bruker
import no.nav.eessi.pensjon.fagmodul.sedmodel.Institusjon
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPerson
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillP8000(private val prefillPerson: PrefillPerson) : Prefill<SED> {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP8000::class.java) }

    override fun prefill(prefillData: PrefillDataModel): SED {

        val p8000 = SED("P8000")

        val navsed = prefillPerson.prefill(prefillData)

        logger.debug("Tilpasser P8000 forenklet preutfylling")
        val person = navsed.nav?.bruker?.person
        val eessielm = navsed.nav?.eessisak?.get(0)
        val perspin = navsed.nav?.bruker?.person?.pin?.get(0)

        p8000.nav = Nav(
                eessisak = listOf(EessisakItem(
                        land = eessielm?.land,
                        saksnummer = eessielm?.saksnummer
                )),

                bruker = Bruker(
                        person = Person(
                                etternavn = person?.etternavn,
                                fornavn = person?.fornavn,
                                foedselsdato = person?.foedselsdato,
                                kjoenn = person?.kjoenn,
                                pin = listOf(
                                        PinItem(
                                                identifikator = perspin?.identifikator,
                                                land = perspin?.land,
                                                institusjon = Institusjon(
                                                        institusjonsid = perspin?.institusjon?.institusjonsid,
                                                        institusjonsnavn = perspin?.institusjon?.institusjonsnavn
                                                )

                                        )
                                )
                        )
                )
        )

        p8000.pensjon = null

        logger.debug("Tilpasser P8000 forenklet preutfylling, Ferdig.")

        prefillData.sed = p8000

        return prefillData.sed
    }
}