package no.nav.eessi.eessifagmodul.prefill

import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.prefill.nav.PrefillPerson
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillP8000(private val prefillPerson: PrefillPerson) : Prefill<SED> {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP8000::class.java) }

    override fun prefill(prefillData: PrefillDataModel): SED {

//        val p8000 = SED.create("P8000")
//
//        var navsed = prefillPerson.prefill(prefillData)
//
//        p8000.nav = Nav(
//                eessisak = navsed.nav?.eessisak,
//                bruker = Bruker(person = navsed.nav?.bruker?.person)
//        )

        val p8000 = SED.create("P8000")

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

                //bruker = Bruker(person = navsed.nav?.bruker?.person)
                bruker = Bruker(
                        person = Person(
                                etternavn = person?.etternavn,
                                fornavn = person?.fornavn,
                                foedselsdato = person?.foedselsdato,
                                kjoenn = person?.kjoenn
                        )
                )
        )
        p8000.nav?.bruker?.person?.pin = listOf(
                PinItem(
                        identifikator = perspin?.identifikator,
                        land = perspin?.land,
                        institusjon = Institusjon(
                                institusjonsid = perspin?.institusjon?.institusjonsid,
                                institusjonsnavn = perspin?.institusjon?.institusjonsnavn
                        )

                )
        )



        p8000.pensjon = null

        logger.debug("Tilpasser P8000 forenklet preutfylling, Ferdig.")

        prefillData.sed = p8000

        return prefillData.sed
    }

}