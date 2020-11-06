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
import no.nav.eessi.pensjon.fagmodul.prefill.tps.NavFodselsnummer
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.eessi.pensjon.utils.toJson
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
                        private val pensjonsinformasjonService: PensjonsinformasjonService,
                        private val aktorRegisterService: AktoerregisterService) {

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

            SEDType.P8000 -> {
                if (prefillData.buc == "P_BUC_05") {
                    PrefillP8000(getPrefillSed(prefillData)).prefill(prefillData, hentPersoner(prefillData), hentRelevantPensjonSak(prefillData) { pensakType -> listOf("ALDER", "BARNEP", "GJENLEV", "UFOREP", "GENRL", "OMSORG").contains(pensakType) })
                } else {
                    PrefillP8000(getPrefillSed(prefillData)).prefill(prefillData, hentPersoner(prefillData), null)
                }
            }


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


    fun hentRelevantPensjonSak(prefillData: PrefillDataModel, akseptabelSakstypeForSed: (String) -> Boolean): V1Sak {
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

    fun hentBarnFraTps(hovedPerson: no.nav.tjeneste.virksomhet.person.v3.informasjon.Person): List<Bruker> {
        logger.info("henter ut relasjon BARN")
        val barnepinlist = hovedPerson.harFraRolleI
            .filter { relasjon -> PrefillNav.Companion.RelasjonEnum.BARN.erSamme(relasjon.tilRolle.value) }
            .filter { relasjon -> relasjon.tilPerson.doedsdato == null }
            .map { relasjon ->  (relasjon.tilPerson.aktoer as PersonIdent).ident.ident }
            .filter { barnPin -> NavFodselsnummer(barnPin).validate() }
            logger.info("prøver å hente ut alle barn (filtrert) på hovedperson: " + barnepinlist.size )

            return barnepinlist
                    .filter { barnPin -> NavFodselsnummer(barnPin).isUnder18Year() }
                    .mapNotNull { barnPin ->
                logger.info("Henter barn fra TPS med aktoerid: ${hentAktoerId(barnPin)}")
                personV3Service.hentBruker(barnPin)
            }
    }

    private fun hentAktoerId(pin: String): String? {
        if (!NavFodselsnummer(pin).validate()) return null
        return try {
            aktorRegisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(pin))?.id
        } catch (ex: Exception) {
            logger.warn("Fant ikke aktoerid")
            null
        }
    }

    private fun filterEktefelleRelasjon(bruker: Bruker?): Pair<String, String> {
        val validRelasjoner = listOf("EKTE", "REPA", "SAMB")
        if (bruker == null) return Pair("", "")

        val relasjoner = bruker.harFraRolleI.map {
            relasjon ->
            val pident = relasjon.tilPerson.aktoer as PersonIdent
            val ektepinid = pident.ident.ident
            "Relasjon: ${relasjon.tilRolle.value} Endring: ${relasjon.endringstype} Aktoer: ${hentAktoerId(ektepinid)}"
        }
        logger.info("Hovedperson har følgende relasjoner : ${relasjoner.toJson()}")

        val result = bruker.harFraRolleI
                .filter { relasjon -> relasjon.tilPerson.doedsdato == null }
                .filter { relasjon -> validRelasjoner.contains(relasjon.tilRolle.value) }
                .map {
                    relasjon ->
                    val ekteType = relasjon.tilRolle.value
                    val pident = relasjon.tilPerson.aktoer as PersonIdent
                    val ektepinid = pident.ident.ident
                    Pair(ektepinid, ekteType)
                }.filter { pair -> NavFodselsnummer(pair.first).validate() }
                .firstOrNull()

        return result ?: Pair("", "")

    }

}
