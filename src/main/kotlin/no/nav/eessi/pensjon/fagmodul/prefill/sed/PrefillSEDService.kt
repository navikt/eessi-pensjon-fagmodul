package no.nav.eessi.pensjon.fagmodul.prefill.sed

import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonData
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillGjenlevende
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillSed
import no.nav.eessi.pensjon.fagmodul.prefill.sed.krav.EPSaktype.ALDER
import no.nav.eessi.pensjon.fagmodul.prefill.sed.krav.EPSaktype.UFOREP
import no.nav.eessi.pensjon.fagmodul.prefill.sed.krav.PrefillP2000
import no.nav.eessi.pensjon.fagmodul.prefill.sed.krav.PrefillP2100
import no.nav.eessi.pensjon.fagmodul.prefill.sed.krav.PrefillP2200
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.PrefillP6000
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.pensjon.v1.sak.V1Sak
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PrefillSEDService(private val prefillNav: PrefillNav,
                        private val personV3Service: PersonV3Service,
                        private val eessiInformasjon: EessiInformasjon,
                        private val pensjonsinformasjonService: PensjonsinformasjonService) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillSEDService::class.java) }

    fun prefill(prefillData: PrefillDataModel): SED {

        val sedType = SEDType.valueOf(prefillData.getSEDType())

        logger.debug("mapping prefillClass to SED: $sedType")

        return when (sedType) {
            //krav
            SEDType.P2000 -> PrefillP2000(prefillNav).prefill(prefillData, hentPersonerMedBarn(prefillData), hentRelevantPensjonSak(prefillData, { pensakType -> pensakType == ALDER.name }))
            SEDType.P2200 -> PrefillP2200(prefillNav).prefill(prefillData, hentPersonerMedBarn(prefillData), hentRelevantPensjonSak(prefillData, { pensakType -> pensakType == UFOREP.name }))
            SEDType.P2100 -> {
                val sedpair = PrefillP2100(prefillNav).prefill(prefillData, hentPersonerMedBarn(prefillData), hentRelevantPensjonSak(prefillData, { pensakType -> listOf("ALDER", "BARNEP", "GJENLEV", "UFOREP").contains(pensakType) }))
                prefillData.melding = sedpair.first
                sedpair.second
            }

            //vedtak
            SEDType.P6000 -> PrefillP6000(prefillNav, eessiInformasjon, pensjonsinformasjonService.hentVedtak(prefillData)).prefill(prefillData, hentPersonerMedBarn(prefillData))

            //andre
            SEDType.P4000 -> PrefillP4000(getPrefillSed(prefillData)).prefill(prefillData, hentPersoner(prefillData))
            SEDType.P7000 -> PrefillP7000(getPrefillSed(prefillData)).prefill(prefillData, hentPersoner(prefillData))
            SEDType.P8000 -> PrefillP8000(getPrefillSed(prefillData)).prefill(prefillData, hentPersoner(prefillData))

            SEDType.P10000 -> PrefillP10000(getPrefillSed(prefillData)).prefill(prefillData, hentPersoner(prefillData))

            SEDType.X005 -> PrefillX005(prefillNav).prefill(prefillData, hentPersoner(prefillData))
            SEDType.H020, SEDType.H021 -> PrefillH02X(getPrefillSed(prefillData)).prefill(prefillData, hentPersoner(prefillData))
            else ->
                //P3000_NO vil aldre gå dennee vei! men fra EU-SED->Nav-SED->PESYS
                //P3000_SE, PL, DK, DE, UK, ol vil gå denne veien.
                //P5000, - P9000, P14000 og og andre
                getPrefillSed(prefillData).prefill(prefillData, hentPersoner(prefillData))
        }
    }

    private fun getPrefillSed(prefillData: PrefillDataModel): PrefillSed {
        val pensjonGjenlevende = PrefillGjenlevende(personV3Service, prefillNav).prefill(prefillData)
        return PrefillSed(prefillNav, pensjonGjenlevende)
    }


    fun hentRelevantPensjonSak(prefillData: PrefillDataModel, akseptabelSakstypeForSed: (String) -> Boolean): V1Sak? {
        return pensjonsinformasjonService.hentRelevantPensjonSak(prefillData, akseptabelSakstypeForSed)
    }


    fun hentPersonerMedBarn(prefillData: PrefillDataModel): PersonData {
        return hentPersoner(prefillData, true)
    }

    //Henter inn alle personer fra ep-personoppslag  først før preutfylling
    private fun hentPersoner(prefillData: PrefillDataModel, fyllUtBarnListe: Boolean = false): PersonData {
        logger.info("Henter hovedperson/forsikret")
        val forsikretPerson = personV3Service.hentBruker(prefillData.bruker.norskIdent)

        val gjenlevendeEllerAvdod = if (prefillData.avdod != null) {
            personV3Service.hentBruker(prefillData.avdod.norskIdent)
        } else {
           forsikretPerson
        }

        val (ektepinid, ekteTypeValue) = filterEktefelleRelasjon(forsikretPerson)

        logger.info("Henter ektefelle/partner (ekteType: $ekteTypeValue)")
        val ektefelleBruker = if (ektepinid.isBlank()) null else personV3Service.hentBruker(ektepinid)

        val barnBrukereFraTPS = if (forsikretPerson == null || !fyllUtBarnListe) emptyList() else hentBarnFraTps(forsikretPerson)

        return PersonData(gjenlevendeEllerAvdod = gjenlevendeEllerAvdod, forsikretPerson = forsikretPerson!!, ektefelleBruker = ektefelleBruker, ekteTypeValue = ekteTypeValue, barnBrukereFraTPS = barnBrukereFraTPS)
    }

    fun hentBarnFraTps(hovedPerson: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person) =
            hovedPerson.harFraRolleI
                    .filter { relasjon -> PrefillNav.Companion.RelasjonEnum.BARN.erSamme(relasjon.tilRolle.value) }
                    .map { relasjon -> (relasjon.tilPerson.aktoer as PersonIdent).ident.ident }
                    .mapNotNull { barnPin ->
                        logger.info("Henter barn fra TPS")
                        personV3Service.hentBruker(barnPin)
                    }

    private fun filterEktefelleRelasjon(bruker: Bruker?): Pair<String, String> {
        val validRelasjoner = listOf("EKTE", "REPA", "SAMB")

        if (bruker == null) return Pair("", "")
        var ektepinid = ""
        var ekteTypeValue = ""

        bruker.harFraRolleI.forEach {
            val relasjon = it.tilRolle.value

            if (validRelasjoner.contains(relasjon)) {

                ekteTypeValue = it.tilRolle.value
                val tilperson = it.tilPerson
                val pident = tilperson.aktoer as PersonIdent

                ektepinid = pident.ident.ident
                if (ektepinid.isNotBlank()) {
                    return@forEach
                }
            }
        }
        return Pair(ektepinid, ekteTypeValue)
    }

}
