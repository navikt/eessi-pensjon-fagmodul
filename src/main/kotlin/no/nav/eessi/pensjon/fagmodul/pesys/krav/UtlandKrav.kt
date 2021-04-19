package no.nav.eessi.pensjon.fagmodul.pesys.krav

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.pesys.SkjemaFamilieforhold
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.slf4j.LoggerFactory
import java.time.LocalDate

open class UtlandKrav {

    private val logger = LoggerFactory.getLogger(UtlandKrav::class.java)

    fun iverksettDato(kravSed: SED): LocalDate? {
//        Mottatt dato = P2200 felt 9.1. Dato for krav *
//        Krav fremsatt dato = P2200 er sendt fra CO
//        Antatt virkningsdato = 3 måneder før Mottatt dato
//        Eks:
//        Mottatt dato  Antatt virkningsdato
//                02.04.2021  01.01.2021
//                17.04.2021  01.02.2021

        val kravdato = LocalDate.parse(kravSed.nav?.krav?.dato) ?: return null
        return kravdato.withDayOfMonth(1).minusMonths(3)
    }

    fun fremsettKravDato(doc: DocumentsItem, bucUtils: BucUtils): LocalDate {
        val local = bucUtils.getDateTime(doc.lastUpdate)
        val date = local.toLocalDate()
        return LocalDate.of(date.year, date.monthOfYear, date.dayOfMonth)
    }

    fun finnStatsborgerskapsLandkode3(kodeverkClient: KodeverkClient, kravSed: SED): String? {
        val statsborgerskap = kravSed.nav?.bruker?.person?.statsborgerskap?.firstOrNull { it.land != null }
        return statsborgerskap?.let { kodeverkClient.finnLandkode3(it.land!!) } ?: ""
    }

    fun sivilstand(kravSed: SED): SkjemaFamilieforhold? {
        val sivilstand = kravSed.nav?.bruker?.person?.sivilstand?.maxByOrNull { LocalDate.parse(it.fradato) }
        val sivilstatus = hentFamilieStatus(sivilstand?.status)
        logger.debug("Sivilstatus: $sivilstatus")
        if (sivilstatus == null || sivilstand?.fradato == null) return null
        return SkjemaFamilieforhold(
            valgtSivilstatus = sivilstatus,
            sivilstatusDatoFom = sivilstand.fradato.let { LocalDate.parse(it) }
        )
    }

    fun hentFamilieStatus(key: String?): String? {
        val status = mapOf(
            "01" to "UGIF",
            "02" to "GIFT",
            "03" to "SAMB",
            "04" to "REPA",
            "05" to "SKIL",
            "06" to "SKPA",
            "07" to "SEPA",
            "08" to "ENKE"
        )
        //Sivilstand for søker. Må være en gyldig verdi fra T_K_SIVILSTATUS_T:
        //ENKE, GIFT, GJES, GJPA, GJSA, GLAD, PLAD, REPA,SAMB, SEPA, SEPR, SKIL, SKPA, UGIF.
        //Pkt p2000 - 2.2.2.1. Familiestatus
        //var valgtSivilstatus: String? = null,
        return status[key]
    }

}