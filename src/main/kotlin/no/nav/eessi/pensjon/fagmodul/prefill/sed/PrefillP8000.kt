package no.nav.eessi.pensjon.fagmodul.prefill.sed

import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.models.ReferanseTilPerson
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillSed
import no.nav.eessi.pensjon.fagmodul.sedmodel.Adresse
import no.nav.eessi.pensjon.fagmodul.sedmodel.AnmodningOmTilleggsInfo
import no.nav.eessi.pensjon.fagmodul.sedmodel.Bruker
import no.nav.eessi.pensjon.fagmodul.sedmodel.EessisakItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Pensjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.Person
import no.nav.eessi.pensjon.fagmodul.sedmodel.PinItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.services.pensjonsinformasjon.EPSaktype
import no.nav.eessi.pensjon.services.pensjonsinformasjon.KravHistorikkHelper.hentKravhistorikkForGjenlevende
import no.nav.pensjon.v1.sak.V1Sak
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillP8000(private val prefillSed: PrefillSed) {

    private enum class PersonenRolle(val value: String) {
        SOEKER_ETTERRLATTEPENSJON("01")
    }

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP8000::class.java) }

    fun prefillSed(prefillData: PrefillDataModel, personData: PersonDataCollection, sak: V1Sak?): SED {
        val navsed = prefillSed.prefill(prefillData, personData)
        val eessielm = navsed.nav?.eessisak
        val gjenlevendeBruker: Bruker? = navsed.pensjon?.gjenlevende
        val avDodBruker = navsed.nav?.bruker
        logger.debug("gjenlevendeBruker: ${gjenlevendeBruker?.person?.fornavn} PIN: ${gjenlevendeBruker?.person?.pin?.firstOrNull()?.identifikator} ")
        logger.debug("avDodBruker: ${avDodBruker?.person?.fornavn} PIN: ${avDodBruker?.person?.pin?.firstOrNull()?.identifikator} ")

        val kravhistorikkGjenlev = sak?.kravHistorikkListe?.let { hentKravhistorikkForGjenlevende(it) }


        return if (prefillData.refTilPerson == ReferanseTilPerson.SOKER && sak?.sakType in listOf(EPSaktype.ALDER.name, EPSaktype.UFOREP.name) && gjenlevendeBruker != null) {
            logger.info("Prefill P8000 forenklet preutfylling for gjenlevende uten avdød, Ferdig.")
            sedP8000(eessielm, gjenlevendeBruker.person, gjenlevendeBruker.adresse, prefillData, null)
        } else if (prefillData.refTilPerson == ReferanseTilPerson.SOKER && sak?.sakType in listOf(EPSaktype.ALDER.name, EPSaktype.UFOREP.name) && kravhistorikkGjenlev != null) {
            logger.info("Prefill P8000 forenklet preutfylling for gjenlevende med revurdering uten avdød, Ferdig.")
            sedP8000(eessielm, gjenlevendeBruker?.person, gjenlevendeBruker?.adresse, prefillData, null)
        } else {
            logger.info("Prefill P8000 forenklet preutfylling med gjenlevende, Ferdig.")
            sedP8000(eessielm, avDodBruker?.person, avDodBruker?.adresse,  prefillData, utfyllAnnenperson(gjenlevendeBruker))
        }

    }

    private fun sedP8000(eessielm: List<EessisakItem>?, forsikretPerson: Person?, adresse: Adresse?, prefillData: PrefillDataModel, annenPerson: Bruker?): SED {
        logger.info("forsikretPerson: ${forsikretPerson != null} annenPerson: ${annenPerson != null}"  )
        val forsikretPersonPin = forsikretPerson?.pin?.firstOrNull()
        return SED(
                type = SEDType.P8000,
                nav = Nav(
                        eessisak = eessielm,
                        bruker = Bruker(
                                person = Person(
                                        etternavn = forsikretPerson?.etternavn,
                                        fornavn = forsikretPerson?.fornavn,
                                        foedselsdato = forsikretPerson?.foedselsdato,
                                        kjoenn = forsikretPerson?.kjoenn,
                                        pin = listOf(
                                                PinItem(
                                                        identifikator = forsikretPersonPin?.identifikator,
                                                        land = forsikretPersonPin?.land
                                                )
                                        )
                                ),
                                adresse = Adresse(
                                        gate = adresse?.gate,
                                        by = adresse?.by,
                                        land = adresse?.land
                                )
                        ),
                        annenperson = utfyllAnnenperson(annenPerson)
                ),
                pensjon = utfyllReferanseTilPerson(prefillData)
        )

    }

    private fun utfyllReferanseTilPerson(prefillData: PrefillDataModel): Pensjon? {
        val refTilperson = prefillData.refTilPerson ?: return null
        return Pensjon(anmodning = AnmodningOmTilleggsInfo(referanseTilPerson = refTilperson.verdi))
    }

    private fun utfyllAnnenperson(gjenlevende: Bruker?): Bruker? {
        if (gjenlevende == null) return null
        gjenlevende.person?.rolle = PersonenRolle.SOEKER_ETTERRLATTEPENSJON.value
        return gjenlevende
    }
}