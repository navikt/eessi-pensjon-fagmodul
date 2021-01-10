package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonData
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Bruker
import no.nav.eessi.pensjon.fagmodul.sedmodel.Pensjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.pensjon.v1.sak.V1Sak
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrefillP2100(private val prefillNav: PrefillNav) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP2100::class.java) }


    fun prefill(prefillData: PrefillDataModel, personData: PersonData, sak: V1Sak): Pair<String?, SED> {
        require(prefillData.avdod != null ) { "avdod er påkrevet for p2100" }

        val sedType = prefillData.getSEDType()

        logger.debug("\n\n----------------------------------------------------------"
                + "\nSaktype                : ${sak.sakType} "
                + "\nSøker sakId            : ${prefillData.penSaksnummer} "
                + "\nSøker avdodaktor       : ${prefillData.avdod.aktorId} "
                + "\nerGyldigEtterlatt      : ${prefillData.avdod.aktorId.isNotEmpty()} "
                + "\nSøker gjenlevaktoer    : ${prefillData.bruker.aktorId} "
                + "\n------------------| Preutfylling [$sedType] START |------------------ \n")

        val sed = prefillData.sed

        //henter opp persondata
        sed.nav = prefillNav.prefill(penSaksnummer = prefillData.penSaksnummer, bruker = prefillData.bruker, avdod = prefillData.avdod, personData = personData , brukerInformasjon = prefillData.getPersonInfoFromRequestData())


        PrefillP2xxxPensjon.validerGyldigKravtypeOgArsakGjenlevnde(sak, sed.sed)
        var melding: String? = ""
        try {
            sed.pensjon = Pensjon()
                val meldingOmPensjon = PrefillP2xxxPensjon.createPensjon(
                        prefillData.bruker.norskIdent,
                        prefillData.penSaksnummer,
                        sak,
                        prefillData.andreInstitusjon,
                        eventuellGjenlevende(prefillData, personData.forsikretPerson),
                        prefillData.kravId)
                melding = meldingOmPensjon.melding
                sed.pensjon = meldingOmPensjon.pensjon
                if (prefillData.isMinimumPrefill()) {
                    sed.pensjon = Pensjon(
                            kravDato = meldingOmPensjon.pensjon.kravDato,
                            gjenlevende = meldingOmPensjon.pensjon.gjenlevende
                    ) //vi skal ha blank pensjon ved denne toggle, men vi må ha med kravdato
                }
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
        }

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

}
