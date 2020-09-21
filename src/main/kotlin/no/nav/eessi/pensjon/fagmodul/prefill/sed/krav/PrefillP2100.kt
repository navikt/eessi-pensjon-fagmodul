package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonData
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Bruker
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Pensjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.utils.norwergianDateToSdfPattern
import no.nav.pensjon.v1.sak.V1Sak
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class PrefillP2100(private val prefillNav: PrefillNav) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP2100::class.java) }


    fun prefill(prefillData: PrefillDataModel, personData: PersonData, sak: V1Sak?): SED {
        require(prefillData.avdod != null ){ "avdod er påkrevet for p2100" }

        val sedType = prefillData.getSEDType()

        logger.debug("\n\n----------------------------------------------------------"
                + "\nSaktype                : ${sak?.sakType} "
                + "\nSøker sakId            : ${prefillData.penSaksnummer} "
                + "\nSøker avdodaktor       : ${prefillData.avdod.aktorId} "
                + "\nerGyldigEtterlatt      : ${prefillData.avdod.aktorId.isNotEmpty()} "
                + "\nSøker gjenlevaktoer    : ${prefillData.bruker.aktorId} "
                + "\n------------------| Preutfylling [$sedType] START |------------------ \n")

        val sed = prefillData.sed

        //skipper å hente persondata dersom NAVSED finnes
        if (prefillData.kanFeltSkippes("NAVSED")) {
            sed.nav = Nav()
        } else {
            //henter opp persondata
            sed.nav = prefillNav.prefill(penSaksnummer = prefillData.penSaksnummer, bruker = prefillData.bruker, avdod = prefillData.avdod, personData = personData , brukerInformasjon = prefillData.getPersonInfoFromRequestData())
        }

        try {
            sed.pensjon =
                    if (sak == null) Pensjon()
                    else {
                        val pensjon = PrefillP2xxxPensjon.createPensjon(
                                prefillData.bruker.norskIdent,
                                prefillData.penSaksnummer,
                                sak,
                                prefillData.andreInstitusjon,
                                eventuellGjenlevende(prefillData, personData.forsikretPerson),
                                prefillData.kravId)
                        if (prefillData.kanFeltSkippes("PENSED")) {
                            Pensjon(
                                    kravDato = pensjon.kravDato,
                                    gjenlevende = pensjon.gjenlevende
                            ) //vi skal ha blank pensjon ved denne toggle, men vi må ha med kravdato
                        } else {
                            pensjon
                        }
                    }
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
            // TODO Should we really swallow this?
            // TODO What's consequences by removing this? and throw it up to UI?
        }

        //kravDatoOverider(prefillData, sak)

        KravHistorikkHelper.settKravdato(prefillData, sed)

        logger.debug("-------------------| Preutfylling [$sedType] END |------------------- ")
        return prefillData.sed
    }

    //Metode for å overskrive kravdato for REVURD av UFØRE OG ALDER på gjenlevsøknad.
    fun kravDatoOverider(prefillData: PrefillDataModel, sak: V1Sak?) {
        if(sak?.kravHistorikkListe?.kravHistorikkListe?.size == 1){
            return
        }
        val sakType = sak?.sakType
        if (prefillData.kravDato == null && (sakType == EPSaktype.UFOREP.name || sakType == EPSaktype.ALDER.name))  {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Mangler kravdato på $sakType for gjenlevende krav")
        }
        val kravDato = prefillData.kravDato?.let { norwergianDateToSdfPattern(it) }
        val sed = prefillData.sed
        if (kravDato != null) {
            sed.pensjon?.kravDato?.dato = kravDato
        }
    }

    private fun eventuellGjenlevende(prefillData: PrefillDataModel, gjenlevendeBruker: no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker?): Bruker? {
        return if (prefillData.avdod != null) {
            logger.info("          Utfylling gjenlevende (etterlatt persjon.gjenlevende)")
            prefillNav.createBruker(gjenlevendeBruker!!, null, null)
        } else null
    }

}
