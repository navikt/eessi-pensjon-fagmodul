package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonData
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Bruker
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Pensjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.pensjon.v1.sak.V1Sak
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillP2100(private val prefillNav: PrefillNav) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP2100::class.java) }


    fun prefillPDL(prefillPDLNav: PrefillPDLNav, prefillData: PrefillDataModel, personDataCollection: PersonDataCollection?, sak: V1Sak?): Pair<String?, SED> {
        require(personDataCollection != null) { "Trenger PersonDataCollection" }
        //PDL
        postLog(prefillData, sak)
        val nav =  prefillPDLNav.prefill(penSaksnummer = prefillData.penSaksnummer, bruker = prefillData.bruker, avdod = prefillData.avdod, personData = personDataCollection , brukerInformasjon = prefillData.getPersonInfoFromRequestData())
        val gjenlev = eventuellGjenlevendePDL(prefillPDLNav, prefillData, personDataCollection.forsikretPerson)

        return prefillPen(prefillData, nav, gjenlev, sak)
    }

    fun prefill(prefillData: PrefillDataModel, personData: PersonData, sak: V1Sak?): Pair<String?, SED> {
        //TPS
        postLog(prefillData, sak)
        val nav = prefillNav.prefill(penSaksnummer = prefillData.penSaksnummer, bruker = prefillData.bruker, avdod = prefillData.avdod, personData = personData , brukerInformasjon = prefillData.getPersonInfoFromRequestData())
        val gjenlev = eventuellGjenlevende(prefillData, personData.forsikretPerson)

        return prefillPen(prefillData, nav, gjenlev, sak)
    }

    private fun postLog(prefillData: PrefillDataModel, sak: V1Sak?) {
        require(prefillData.avdod != null ) { "avdod er påkrevet for p2100" }
        logger.debug("\n\n----------------------------------------------------------"
                + "\nSaktype                : ${sak?.sakType} "
                + "\nSøker sakId            : ${prefillData.penSaksnummer} "
                + "\nSøker avdodaktor       : ${prefillData.avdod.aktorId} "
                + "\nerGyldigEtterlatt      : ${prefillData.avdod.aktorId.isNotEmpty()} "
                + "\nSøker gjenlevaktoer    : ${prefillData.bruker.aktorId} "
                + "\n------------------| Preutfylling [${prefillData.sedType}] START |------------------ \n")
    }


    private fun prefillPen(prefillData: PrefillDataModel, nav: Nav, gjenlev: Bruker? = null, sak: V1Sak?): Pair<String?, SED> {
        val sedType = prefillData.getSEDType()

        PrefillP2xxxPensjon.validerGyldigKravtypeOgArsakGjenlevnde(sak, sedType)
        var melding: String? = ""
        var pensjon: Pensjon? = Pensjon()
        try {
                val meldingOmPensjon = PrefillP2xxxPensjon.createPensjon(
                        prefillData.bruker.norskIdent,
                        prefillData.penSaksnummer,
                        sak,
                        prefillData.andreInstitusjon,
                        gjenlev,
                    prefillData.kravId)
                melding = meldingOmPensjon.melding
                pensjon = meldingOmPensjon.pensjon
                if (prefillData.isMinimumPrefill()) {
                    pensjon = Pensjon(
                            kravDato = meldingOmPensjon.pensjon.kravDato,
                            gjenlevende = meldingOmPensjon.pensjon.gjenlevende
                    ) //vi skal ha blank pensjon ved denne toggle, men vi må ha med kravdato
                }
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
        }

        val sed = SED(
            "P2100",
            nav = nav,
            pensjon = pensjon
        )

        PrefillP2xxxPensjon.settKravdato(sed)

        logger.debug("-------------------| Preutfylling [$sedType] END |------------------- ")
        return Pair(melding, sed)
    }

    private fun eventuellGjenlevende(prefillData: PrefillDataModel, gjenlevendeBruker: no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker?): Bruker? {
        return if (prefillData.avdod != null) {
            logger.info("          Utfylling gjenlevende (etterlatt persjon.gjenlevende)")
            prefillNav.createBruker(gjenlevendeBruker!!, null, null)
        } else null
    }

    private fun eventuellGjenlevendePDL(prefillPDLNav: PrefillPDLNav, prefillData: PrefillDataModel, gjenlevendeBruker: Person?): Bruker? {
        return if (prefillData.avdod != null) {
            logger.info("          Utfylling gjenlevende (etterlatt persjon.gjenlevende)")
            prefillPDLNav.createBruker(gjenlevendeBruker!!)
        } else null
    }

}
