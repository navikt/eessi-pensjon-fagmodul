package no.nav.eessi.pensjon.fagmodul.prefill.person

import no.nav.eessi.pensjon.fagmodul.sedmodel.Bruker
import no.nav.eessi.pensjon.fagmodul.sedmodel.Pensjon
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillPersonDataFromTPS
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
//TODO Vil utgå når SED blir koblet direkte mot PEN/PESYS for uthenting og preutfylling av data
class PrefillPensjon(private val preutfyllingPersonFraTPS: PrefillPersonDataFromTPS) : Prefill<Pensjon> {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillPensjon::class.java) }

    override fun prefill(prefillData: PrefillDataModel): Pensjon {
        return pensjon(prefillData)
    }

    private fun pensjon(prefillData: PrefillDataModel): Pensjon {

        //min krav for vedtak,P2000,P5000,P4000?
        //validere om vi kan preutfylle for angitt SED
        //norskident pnr.
        val pinid = prefillData.personNr

        // er denne person en gjenlevende? hva må da gjøres i nav.bruker.person?
        //
        //
        //fylles ut kun når vi har etterlatt aktoerId og etterlattPinID.
        //noe vi må få fra PSAK. o.l
        var gjenlevende: Bruker? = null
        if (prefillData.erGyldigEtterlatt()) {
            logger.info("Preutfylling Utfylling Pensjon Gjenlevende (etterlatt)")
            gjenlevende = preutfyllingPersonFraTPS.prefillBruker(pinid)
        }

        val pensjon = Pensjon(

                //etterlattpensjon
                gjenlevende = gjenlevende
        )
        logger.info("Preutfylling Utfylling Pensjon END")

        return pensjon
    }
}