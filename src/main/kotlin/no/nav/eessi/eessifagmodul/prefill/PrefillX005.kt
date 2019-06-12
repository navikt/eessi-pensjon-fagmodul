package no.nav.eessi.eessifagmodul.prefill

import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.prefill.nav.PrefillNav
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillX005(private val prefillNav: PrefillNav) : Prefill<SED> {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillX005::class.java) }

    override fun prefill(prefillData: PrefillDataModel): SED {

        val x005 = SED.create("X005")

        val navsed = prefillNav.prefill(prefillData)

        logger.debug("Tilpasser X005 forenklet preutfylling")
        val person = navsed.bruker?.person

        x005.nav = Nav(
            sak = Navsak(
                kontekst = Kontekst(
                    bruker = Bruker(
                        person = Person(
                          fornavn = person?.fornavn,
                          etternavn = person?.etternavn,
                          foedselsdato = person?.foedselsdato,
                          kjoenn = person?.kjoenn
                        )
                    )
                ),
                leggtilinstitusjon = Leggtilinstitusjon(
                    institusjon = InstitusjonX005(
                        id = "",
                        navn = ""
                    ),
                    grunn = ""
                )
            )
        )
        logger.debug("Tilpasser X005 forenklet preutfylling, Ferdig.")

        prefillData.sed = x005
        return prefillData.sed
    }

}