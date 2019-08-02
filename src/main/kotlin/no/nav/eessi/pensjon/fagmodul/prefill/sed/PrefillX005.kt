package no.nav.eessi.pensjon.fagmodul.prefill.sed

import no.nav.eessi.pensjon.fagmodul.sedmodel.Kontekst
import no.nav.eessi.pensjon.fagmodul.sedmodel.Leggtilinstitusjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.Navsak
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Person
import no.nav.eessi.pensjon.fagmodul.sedmodel.Bruker
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillX005(private val prefillNav: PrefillNav) : Prefill<SED> {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillX005::class.java) }

    override fun prefill(prefillData: PrefillDataModel): SED {

        val navsed = prefillNav.prefill(prefillData)

        logger.debug("Tilpasser X005 forenklet preutfylling")
        val person = navsed.bruker?.person

        val x005 = SED(
                sed = "X005",
                nav = Nav(
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
                                        institusjon = prefillData.institusjonX005,
                                        grunn = null
                                )
                        )
                )
        )
        logger.debug("Tilpasser X005 forenklet preutfylling, Ferdig.")

        prefillData.sed = x005
        return prefillData.sed
    }

}