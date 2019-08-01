package no.nav.eessi.pensjon.fagmodul.prefill.sed

import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.sed.krav.PrefillP2000
import no.nav.eessi.pensjon.fagmodul.prefill.sed.krav.PrefillP2200
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPensjon
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPerson
import no.nav.eessi.pensjon.fagmodul.prefill.sed.krav.KravDataFromPEN
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillPersonDataFromTPS
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.PrefillP6000
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PrefillFactory(private val prefillNav: PrefillNav,
                     dataFromTPS: PrefillPersonDataFromTPS,
                     private val eessiInformasjon: EessiInformasjon,
                     private val sakPensiondata: KravDataFromPEN) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillFactory::class.java) }

    private val prefillPerson = PrefillPerson(prefillNav, PrefillPensjon(dataFromTPS))

    fun createPrefillClass(prefillData: PrefillDataModel): Prefill<SED> {
        val sedValue = SEDType.valueOf(prefillData.getSEDid())

        logger.info("mapping prefillClass to SED: $sedValue")

        return when (sedValue) {
            //Status hva gjendstår
            SEDType.P6000 -> {
                PrefillP6000(sakPensiondata, eessiInformasjon)
            }
            //Status hva gjendstår
            SEDType.P2000 -> {
                //PrefillDefaultSED(prefillPerson)
                PrefillP2000(sakPensiondata)
            }
            //Status hva gjendstår
            SEDType.P2200 -> {
                PrefillP2200(sakPensiondata)
            }
            SEDType.P2100 -> {
                PrefillP2100()
            }
            //P3000_NO vil aldre gå dennee vei! men fra EU-SED->Nav-SED->PESYS
            //P3000_SE, PL, DK, DE, UK, ol vil gå denne veien.
            SEDType.P3000 -> {
                PrefillP3000(prefillPerson)
            }
            SEDType.P4000 -> {
                PrefillP4000(prefillPerson)
            }
            SEDType.P7000 -> {
                PrefillP7000(prefillNav)
            }
            SEDType.P8000 -> {
                PrefillP8000(prefillPerson)
            }
            SEDType.X005 -> {
                PrefillX005(prefillNav)
            }
            else -> {
                //P5000, P8000, P7000, P8000  - P9000 og P10000
                PrefillDefaultSED(prefillPerson)
            }
        }
    }
}