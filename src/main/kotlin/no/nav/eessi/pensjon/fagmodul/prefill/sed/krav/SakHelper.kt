package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonHjelper
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillPersonDataFromTPS
import no.nav.eessi.pensjon.fagmodul.sedmodel.*
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjoninformasjonException
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.sak.V1Sak
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Hjelpe klasse for sak som fyller ut NAV-SED-P2000 med pensjondata fra PESYS.
 */
@Component
class SakHelper(private val prefillNav: PrefillNav,
                private val preutfyllingPersonFraTPS: PrefillPersonDataFromTPS,
                private val dataFromPEN: PensjonsinformasjonHjelper,
                private val kravHistorikkHelper: KravHistorikkHelper) {
    private val logger: Logger by lazy { LoggerFactory.getLogger(SakHelper::class.java) }

    /**
     *  Henter ut pensjoninformasjon med brukersSakerListe
     */
    fun getPensjoninformasjonFraSak(prefillData: PrefillDataModel): Pensjonsinformasjon {
        return dataFromPEN.hentPensjoninformasjonMedPinid(prefillData)
    }

    /**
     *  Henter ut v1Sak på brukersSakerListe ut ifra valgt sakid i prefilldatamodel
     */
    fun getPensjonSak(prefillData: PrefillDataModel, pendata: Pensjonsinformasjon): V1Sak {
        return dataFromPEN.hentMedSak(prefillData, pendata)
    }

    /**
     *  Henter ut liste av gyldige sakkType fra brukerSakListe
     */
    fun getPensjonSakTypeList(pendata: Pensjonsinformasjon): List<Saktype> {
        val ksaklist = mutableListOf<Saktype>()

        pendata.brukersSakerListe.brukersSakerListe.forEach {
            if (Saktype.isValid(it.sakType)) {
                val ksakType = Saktype.valueOf(it.sakType)
                ksaklist.add(ksakType)
            }
        }
        return ksaklist
    }

    /**
     *  Henter persondata fra TPS fyller ut sed.nav
     */
    fun createNav(prefillData: PrefillDataModel): Nav {
        logger.debug("[${prefillData.getSEDid()}] Preutfylling NAV")
        return prefillNav.prefill(prefillData)
    }

    /**
     *  Henter pensjondata fra PESYS fyller ut sed.pensjon
     */
    fun createPensjon(prefillData: PrefillDataModel, gjenlevende: Bruker? = null): Pensjon {
        logger.debug("[${prefillData.getSEDid()}]   Preutfylling PENSJON")

        val pendata: Pensjonsinformasjon = getPensjoninformasjonFraSak(prefillData)

        //hent korrekt sak fra context
        val pensak: V1Sak = getPensjonSak(prefillData, pendata)

        //4.0
        return kravHistorikkHelper.createInformasjonOmYtelserList(prefillData, pensak, gjenlevende)
    }

    /**
     *  fylles ut kun når vi har etterlatt etterlattPinID.
     *  noe vi må få fra PSAK. o.l
     */
    fun createGjenlevende(prefillData: PrefillDataModel): Bruker? {
        var gjenlevende: Bruker? = null
        if (prefillData.erGyldigEtterlatt()) {
            logger.debug("          Utfylling gjenlevende (etterlatt)")
            gjenlevende = preutfyllingPersonFraTPS.prefillBruker(prefillData.personNr)
        }
        return gjenlevende
    }

    fun hentPensjonsdata(prefillData: PrefillDataModel, sed: SED) {
        try {
            if (prefillData.kanFeltSkippes("PENSED")) {
                val pensjon = createPensjon(prefillData)
                //vi skal ha blank pensjon ved denne toggle
                //vi må ha med kravdato
                sed.pensjon = Pensjon(kravDato = pensjon.kravDato)

                //henter opp pensjondata
            } else {

                //gjenlevende hvis det finnes..
                val gjenlevende = createGjenlevende(prefillData)

                val pensjon = createPensjon(prefillData, gjenlevende)

                //legger pensjon på sed (få med oss gjenlevende/avdød)
                sed.pensjon = pensjon
            }
        } catch (pen: PensjoninformasjonException) {
            logger.error(pen.message)
            sed.pensjon = Pensjon()
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
        }
    }
}
