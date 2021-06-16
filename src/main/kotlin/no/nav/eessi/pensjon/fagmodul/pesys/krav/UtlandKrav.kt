package no.nav.eessi.pensjon.fagmodul.pesys.krav

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.pesys.Sivilstatus
import no.nav.eessi.pensjon.fagmodul.pesys.SkjemaFamilieforhold
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.slf4j.LoggerFactory
import java.time.LocalDate

open class UtlandKrav {

    private val logger = LoggerFactory.getLogger(UtlandKrav::class.java)

    /**
     *  Mottatt dato = P2200 felt 9.1. Dato for krav *
     *  Krav fremsatt dato = P2200 er sendt fra CO
     *  Antatt virkningsdato = 3 måneder før Mottatt dato
     *  Eks:
     *  Mottatt dato  Antatt virkningsdato
     *  02.04.2021  01.01.2021
     *  17.04.2021  01.02.2021
     */
    fun iverksettDatoUfore(kravSed: SED): LocalDate? {
        val kravdato = LocalDate.parse(kravSed.nav?.krav?.dato) ?: return null
        return kravdato.withDayOfMonth(1).minusMonths(3)
    }

    fun iverksettDatoAlder(kravSed: SED, mottattDato: LocalDate?): LocalDate? {
        val virkningsDato = virkningsDato(kravSed, mottattDato)
        if (virkningsDato != null && virkningsDato.isBefore(LocalDate.now()))
            return virkningsDato.plusMonths(1)
        return virkningsDato
    }


    /**
     * Felt 9.4.4
     * 1. Ved utsettelsesdato mellom 1-15 skal virkningsDato bli første i  inneværende mnd
     * 2. Ved utsettelsesdato mellom 16-siste dag i mnd, skal virkningsDato bli første dag i neste mnd
     * 3. Dersom ingenting er angitt, så sett Antatt virkningsdato til den første i måneden etter Mottatt dato.
     */
    fun virkningsDato(kravSed: SED, mottattDato: LocalDate?): LocalDate? {
        val utsettelseDato = kravSed.pensjon?.utsettelse?.firstOrNull()?.tildato

        if (utsettelseDato != null) {
            return utsettelseDato.let {
                var virkningsDato = LocalDate.parse(it)

                if (virkningsDato.dayOfMonth > 15) {
                    virkningsDato = virkningsDato.plusMonths(1)
                }
                virkningsDato = virkningsDato.withDayOfMonth(1)
                virkningsDato
            }
        } else if (mottattDato != null) {
            var virkningsDato = mottattDato.withDayOfMonth(1)
            virkningsDato = virkningsDato.plusMonths(1)
            return virkningsDato
        }
        return null
    }

    fun fremsettKravDato(doc: DocumentsItem, bucUtils: BucUtils): LocalDate {
        val local = bucUtils.getDateTime(doc.lastUpdate)
        val date = local.toLocalDate()
        return LocalDate.of(date.year, date.monthOfYear, date.dayOfMonth)
    }


    fun finnStatsborgerskapsLandkode3(kodeverkClient: KodeverkClient, kravSed: SED): String {
        val statsborgerskap = kravSed.nav?.bruker?.person?.statsborgerskap?.firstOrNull { it.land != null }
        return statsborgerskap?.let { kodeverkClient.finnLandkode(it.land!!) } ?: ""
    }

    fun sivilstand(kravSed: SED): SkjemaFamilieforhold? {

        val sivilstand = kravSed.nav?.bruker?.person?.sivilstand?.maxByOrNull { LocalDate.parse(it.fradato) }
        val sivilstatus = sivilstand?.status?.let { Sivilstatus.valueOf(it) }

        logger.debug("Sivilstatus: $sivilstatus")
        if (sivilstatus == null || sivilstand.fradato == null) return null
        return SkjemaFamilieforhold(
            valgtSivilstatus = sivilstatus,
            sivilstatusDatoFom = sivilstand.fradato.let { LocalDate.parse(it) }
        )
    }

}