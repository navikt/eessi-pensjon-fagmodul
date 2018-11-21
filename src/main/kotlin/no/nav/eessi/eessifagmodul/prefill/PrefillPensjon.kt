package no.nav.eessi.eessifagmodul.prefill

import no.nav.eessi.eessifagmodul.models.Bruker
import no.nav.eessi.eessifagmodul.models.Pensjon
import no.nav.eessi.eessifagmodul.prefill.nav.PrefillPersonDataFromTPS
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
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
            logger.debug("Preutfylling Utfylling Pensjon Gjenlevende (etterlatt)")
            gjenlevende = preutfyllingPersonFraTPS.prefillBruker(pinid)
        }

        //kun ved bruk av P5000
        //var p5000pensjon: Pensjon? = null
//        if (prefillData.validSED("P5000")) {
//            logger.debug("Preutfylling Utfylling Pensjon Medlemskap")
//            //p5000pensjon = createMedlemskapMock()
//        }

        val pensjon = Pensjon(

                //etterlattpensjon
                gjenlevende = gjenlevende
                //P5000
                /*
                sak = p5000pensjon?.sak,
                medlemskap = p5000pensjon?.medlemskap,
                medlemskapTotal = p5000pensjon?.medlemskapTotal,
                medlemskapAnnen = p5000pensjon?.medlemskapAnnen,
                trygdetid = p5000pensjon?.trygdetid
                */
        )
        logger.debug("Preutfylling Utfylling Pensjon END")

        return pensjon
    }

}