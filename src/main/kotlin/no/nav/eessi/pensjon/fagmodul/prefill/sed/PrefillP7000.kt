package no.nav.eessi.pensjon.fagmodul.prefill.sed

import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillSed
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillP7000(private val prefillSed: PrefillSed) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP7000::class.java) }

    fun prefill(prefillData: PrefillDataModel, personData: PersonDataCollection): P7000 {

        val sed = prefillSed.prefill(prefillData, personData)
        logger.debug("Tilpasser P7000 forenklet preutfylling")

        val person = sed.nav?.bruker?.person
        val perspin = person?.pin?.firstOrNull()
        val eessielm = sed.nav?.eessisak?.firstOrNull()

        val p7000 = P7000(
                nav = Nav(
                        eessisak = listOf(
                            EessisakItem(
                                land = eessielm?.land,
                                saksnummer = eessielm?.saksnummer,
                                institusjonsid = eessielm?.institusjonsid,
                                institusjonsnavn = eessielm?.institusjonsnavn
                        )
                        ),
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
                        ),
                        //mappe om etternavn til mappingfeil
                        ektefelle = Ektefelle(person = Person(etternavn = sed.nav?.bruker?.person?.etternavn))
                ),
                //mappe om kjoenn for mappingfeil
                p7000Pensjon = P7000Pensjon(
                        //TODO trenger vi denne lenger? er mapping ok eller fortsatt feil?
                        bruker = Bruker(person = Person(kjoenn = sed.nav?.bruker?.person?.kjoenn)),
                        gjenlevende = sed.pensjon?.gjenlevende
                )
        )

        logger.debug("Tilpasser P7000 forenklet preutfylling, Ferdig.")
        return p7000
    }

}
