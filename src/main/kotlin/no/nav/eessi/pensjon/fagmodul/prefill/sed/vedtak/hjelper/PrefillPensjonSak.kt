package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper

import no.nav.eessi.pensjon.eux.model.sed.KravtypeItem
import no.nav.eessi.pensjon.eux.model.sed.Sak
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object PrefillPensjonSak {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillPensjonSak::class.java) }

    //6.1..
    fun createSak(pendata: Pensjonsinformasjon): Sak {
        logger.debug("PrefillPensjonReduksjon")

        logger.debug("6         Sak")
        return Sak(
                //6.1 --
                artikkel54 = createArtikkel54(pendata),
                //6.5.1
                kravtype = createKravBegrensetInnsyn(),
        )
    }

    private fun createKravBegrensetInnsyn(): List<KravtypeItem> {
        logger.debug("6.5.1         Information innsyn")
        return listOf(
                KravtypeItem(
                        //6.5.1 $pensjon.sak.kravtype[x].datoFrist
                        datoFrist = "six weeks from the date the decision is received",
                )
        )
    }

    /*
        6.1
        HVIS sakstyper er uføretrygd,
        SÅ skal det velges «[0] No»

        HVIS sakstype er alderspensjon eller gjenlevendepensjon,
        SÅ skal det ikke velges noen.
    */
    private fun createArtikkel54(pendata: Pensjonsinformasjon): String? {
        logger.debug("6.1       createArtikkel54")
        val ksakUfor = KSAK.valueOf(pendata.sakAlder.sakType)

        if (KSAK.UFOREP == ksakUfor) {
            return "0"
        }
        return null
    }
}
