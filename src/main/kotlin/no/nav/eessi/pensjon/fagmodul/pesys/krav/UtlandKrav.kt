package no.nav.eessi.pensjon.fagmodul.pesys.krav

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.pesys.Sivilstatus
import no.nav.eessi.pensjon.fagmodul.pesys.SkjemaFamilieforhold
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.LocalDateTime

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

    fun iverksettDatoAlder(kravSed: SED, kravDato: LocalDate?): LocalDate? {
        return virkningsDato(kravSed, kravDato)
//        if (virkningsDato != null && virkningsDato.isBefore(LocalDate.now()))
//            return virkningsDato.plusMonths(1)
//        return virkningsDato
    }


    /**
     * Felt 9.4.4
     * 1. Ved utsettelsesdato mellom 1-15 skal virkningsDato bli første i  inneværende mnd
     * 2. Ved utsettelsesdato mellom 16-siste dag i mnd, skal virkningsDato bli første dag i neste mnd
     * 3. Dersom ingenting er angitt, så sett Antatt virkningsdato til den første i måneden etter Mottatt dato.
     */
    fun virkningsDato(kravSed: SED, kravDato: LocalDate?): LocalDate? {
        val utsettelseDato = kravSed.pensjon?.utsettelse?.firstOrNull()?.tildato

//        Opprettelse av automatisk krav i PESYS ved mottatt av SED P2000/P2200:
//        HVIS "soknadFraLand" : "SWE"
//        OG "fremsattKravdato" : "2021-02-12",
//        OG "mottattDato" : "2021-05-14",
//        SÅ "iverksettelsesdato" : "2021-03-01",

        val dato = if (utsettelseDato != null) {
            val utsettelse = LocalDate.parse(utsettelseDato)
            logger.debug("utsettelse dato: $utsettelse")
            utsettelse.withDayOfMonth(1)
        } else if (kravDato != null) {
            logger.debug("kravdato: $kravDato")
            kravDato.withDayOfMonth(1).plusMonths(1)
        } else {
            null
        }
        logger.debug("Hva er DATOEN: $dato")
        return dato
    }

    fun mottattDocumentDato(doc: DocumentsItem, bucUtils: BucUtils): LocalDate {
        logger.debug("document receiveDate: ${doc.receiveDate}")
        val date : LocalDateTime = bucUtils.getLocalDateTime(doc.receiveDate) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingen gylidg mottattDato funnet")
        return LocalDate.of(date.year, date.month, date.dayOfMonth)
    }


    fun finnStatsborgerskapsLandkode3(kodeverkClient: KodeverkClient, kravSed: SED): String {
        val statsborgerskap = kravSed.nav?.bruker?.person?.statsborgerskap?.firstOrNull { it.land != null }
        return statsborgerskap?.let { kodeverkClient.finnLandkode(it.land!!) } ?: ""
    }

    fun sivilstand(kravSed: SED): SkjemaFamilieforhold? {

        val sivilstand = kravSed.nav?.bruker?.person?.sivilstand?.maxByOrNull { LocalDate.parse(it.fradato) }
        val sivilstatus = sivilstand?.status?.let { Sivilstatus.getSivilStatusByStatus(it) }

        logger.debug("Sivilstatus: $sivilstatus")
        if (sivilstatus == null || sivilstand.fradato == null) return null
        return SkjemaFamilieforhold(
            valgtSivilstatus = sivilstatus,
            sivilstatusDatoFom = sivilstand.fradato.let { LocalDate.parse(it) }
        )
    }

    /**
     * PESYS støtter kun GB
     */
    fun justerAvsenderLand(avsenderLand: String): String =
        if (avsenderLand == "UK") "GB"
        else avsenderLand

}