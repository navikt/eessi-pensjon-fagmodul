package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.sedmodel.Krav
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.utils.simpleFormat
import no.nav.pensjon.v1.kravhistorikk.V1KravHistorikk
import no.nav.pensjon.v1.kravhistorikkliste.V1KravHistorikkListe
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object KravHistorikkHelper {
    private val logger: Logger by lazy { LoggerFactory.getLogger(KravHistorikkHelper::class.java) }

    /**
     *  9.1
     *
     *  Setter kravdato på sed (denne kommer fra PESYS men opprettes i nav?!)
     */
    fun settKravdato(prefillData: PrefillDataModel, sed: SED) {
        logger.debug("Kjører settKravdato")
        if (prefillData.kanFeltSkippes("NAVSED")) {
            //sed.nav?.krav = Krav("")
            //pensjon.kravDato = null
        } else {
            logger.debug("9.1     legger til nav kravdato fra pensjon kravdato : ${sed.pensjon?.kravDato} ")
            sed.nav?.krav = sed.pensjon?.kravDato
        }
    }

    private fun sortertKravHistorikk(kravHistorikkListe: V1KravHistorikkListe): List<V1KravHistorikk> {
        val list = kravHistorikkListe.kravHistorikkListe.toList()
        return list.asSequence().sortedBy { it.mottattDato.toGregorianCalendar() }.toList()
    }

    fun hentKravHistorikkForsteGangsBehandlingUtlandEllerForsteGang(kravHistorikkListe: V1KravHistorikkListe, saktype: String): V1KravHistorikk {
        if (EPSaktype.BARNEP.name == saktype) {
            return hentKravHistorikkMedKravType(listOf(Kravtype.F_BH_MED_UTL.name, Kravtype.FORSTEG_BH.name, Kravtype.F_BH_BO_UTL.name), kravHistorikkListe)
        }
        return hentKravHistorikkMedKravType(listOf(Kravtype.F_BH_MED_UTL.name, Kravtype.FORSTEG_BH.name), kravHistorikkListe)
    }

    private fun hentKravHistorikkMedKravType(kravType: List<String>, kravHistorikkListe: V1KravHistorikkListe): V1KravHistorikk {
        val sortList = sortertKravHistorikk(kravHistorikkListe)
        sortList.forEach { kravHistorikk ->
            logger.debug("leter etter Kravtype: $kravType, fant ${kravHistorikk.kravType} med dato i ${kravHistorikk.virkningstidspunkt}")
            if (kravType.contains(kravHistorikk.kravType)) {
                logger.debug("Fant Kravhistorikk med $kravType")
                return kravHistorikk
            }
        }
        logger.warn("Fant ikke noe Kravhistorikk. med $kravType. Grunnet utsending kun utland mangler vilkårprøving/vedtak. følger ikke normal behandling")
        return V1KravHistorikk()
    }

    fun hentKravhistorikkForGjenlevende(kravHistorikkListe: V1KravHistorikkListe): V1KravHistorikk? {
            val kravHistorikk = kravHistorikkListe.kravHistorikkListe.filter { krav -> krav.kravArsak == KravArsak.GJNL_SKAL_VURD.name || krav.kravArsak == KravArsak.TILST_DOD.name  }
            if (kravHistorikk.isNotEmpty()) {
                return kravHistorikk.first()
            }
            logger.error("Fant ikke noe Kravhistorikk.. med bruk av kravårsak: ${KravArsak.GJNL_SKAL_VURD.name} ")
            return null
    }

    fun hentKravHistorikkMedKravStatusTilBehandling(kravHistorikkListe: V1KravHistorikkListe): V1KravHistorikk {
        val sortList = sortertKravHistorikk(kravHistorikkListe)
        sortList.forEach {
            logger.debug("leter etter Krav status med ${Kravstatus.TIL_BEHANDLING}, fant ${it.kravType} med virkningstidspunkt dato : ${it.virkningstidspunkt}")
            if (Kravstatus.TIL_BEHANDLING.name == it.status) {
                logger.debug("Fant Kravhistorikk med ${it.status}")
                return it
            }
        }
        logger.error("Fant ikke noe Kravhistorikk..${Kravstatus.TIL_BEHANDLING}. Mangler vilkårsprlving/vedtak. følger ikke normal behandling")
        return V1KravHistorikk()
    }

    fun hentKravHistorikkMedKravStatusAvslag(kravHistorikkListe: V1KravHistorikkListe): V1KravHistorikk {
        val sortList = sortertKravHistorikk(kravHistorikkListe)
        sortList.forEach {
            logger.debug("leter etter Krav status med ${Kravstatus.AVSL}, fant ${it.kravType} med virkningstidspunkt dato : ${it.virkningstidspunkt}")
            if (Kravstatus.AVSL.name == it.status) {
                logger.debug("Fant Kravhistorikk med ${it.status}")
                return it
            }
        }
        logger.error("Fant ikke noe Kravhistorikk..${Kravstatus.AVSL}. Mangler vilkårsprlving/vedtak. følger ikke normal behandling")
        return V1KravHistorikk()
    }


    /**
     * 9.1- 9.2
     *
     *  Fra PSAK, kravdato på alderspensjonskravet
     *  Fra PSELV eller manuell kravblankett:
     *  Fyller ut fra hvilket tidspunkt bruker ønsker å motta pensjon fra Norge.
     *  Det er et spørsmål i søknadsdialogen og på manuell kravblankett. Det er ikke nødvendigvis lik virkningstidspunktet på pensjonen.
     */
    fun createKravDato(valgtKrav: V1KravHistorikk, message: String? = ""): Krav? {
        logger.debug("9.1        Dato Krav (med korrekt data fra PESYS krav.virkningstidspunkt)")
        logger.debug("KravType   :  ${valgtKrav.kravType}")
        logger.debug("mottattDato:  ${valgtKrav.mottattDato}")
        logger.debug("--------------------------------------------------------------")

        logger.debug("Prøver å sette kravDato til Virkningstidpunkt: ${valgtKrav.kravType} og dato: ${valgtKrav.mottattDato}")
        logger.debug("$message")
        return Krav(dato = valgtKrav.mottattDato?.simpleFormat())
    }

}
