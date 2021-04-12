package no.nav.eessi.pensjon.fagmodul.prefill.sed

import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.fagmodul.models.BrukerInformasjon
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PersonId
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillX005(private val prefillNav: PrefillPDLNav)  {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillX005::class.java) }

    fun prefill(penSaksnummer: String,
                bruker: PersonId,
                avdod: PersonId?,
                brukerinformasjon: BrukerInformasjon?,
                institusjon: InstitusjonItem,
                personData: PersonDataCollection): X005 {

        val navsed = prefillNav.prefill(
            penSaksnummer = penSaksnummer,
            bruker = bruker,
            avdod = avdod,
            personData = personData,
            brukerInformasjon = brukerinformasjon,
        )

        val institusjonX005 = InstitusjonX005(
                   id = institusjon.checkAndConvertInstituion(),
                    navn = institusjon.name ?: institusjon.checkAndConvertInstituion()
        )

        logger.debug("Tilpasser X005 forenklet preutfylling")
        val person = navsed.bruker?.person

        return X005(
                type = SedType.X005,
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
                                        institusjon = institusjonX005
                                )
                        )
                )
        ).also {
            logger.debug("Tilpasser X005 forenklet preutfylling, Ferdig.")
        }
    }

}
