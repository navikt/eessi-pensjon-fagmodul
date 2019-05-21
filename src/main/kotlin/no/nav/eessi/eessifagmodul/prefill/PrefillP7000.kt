package no.nav.eessi.eessifagmodul.prefill

import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.prefill.nav.PrefillNav
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillP7000(private val prefillNav: PrefillNav) : Prefill<SED> {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP7000::class.java) }

    override fun prefill(prefillData: PrefillDataModel): SED {

        val p7000 = SED.create("P7000")

        val navsed = prefillNav.prefill(prefillData)

        logger.debug("Tilpasser P7000 forenklet preutfylling")
        val person = navsed.bruker?.person
        val perspin = navsed.bruker?.person?.pin?.get(0)

        p7000.nav = Nav(
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
        p7000.nav?.bruker?.person?.pin = listOf(
                PinItem(
                        identifikator = perspin?.identifikator,
                        land = perspin?.land,
                        institusjon = Institusjon(
                                institusjonsid = perspin?.institusjon?.institusjonsid,
                                institusjonsnavn = perspin?.institusjon?.institusjonsnavn
                        )
                )
        )
        //mappe om etternavn til mappingfeil
        p7000.nav?.ektefelle = Ektefelle(person = Person(etternavn = navsed.bruker?.person?.etternavn))

        //mappe om kjoenn for mappingfeil
        p7000.pensjon = Pensjon(bruker = Bruker(person = Person(kjoenn = navsed.bruker?.person?.kjoenn)))

        logger.debug("Tilpasser P7000 forenklet preutfylling, Ferdig.")

        prefillData.sed = p7000

        return prefillData.sed
    }

}