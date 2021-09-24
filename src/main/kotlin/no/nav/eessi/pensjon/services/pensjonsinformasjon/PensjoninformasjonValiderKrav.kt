package no.nav.eessi.pensjon.services.pensjonsinformasjon

import no.nav.pensjon.v1.kravhistorikk.V1KravHistorikk
import no.nav.pensjon.v1.kravhistorikkliste.V1KravHistorikkListe
import no.nav.pensjon.v1.sak.V1Sak
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

object PensjoninformasjonValiderKrav {
    private val logger: Logger by lazy { LoggerFactory.getLogger(PensjoninformasjonValiderKrav::class.java) }


    /**
     * Skal validere på kravtype og kravårrsak Krav SED P2000 Alder og P2200 Uførep
     * https://confluence.adeo.no/pages/viewpage.action?pageId=338181302
     *
     * FORSTEG_BH       Førstegangsbehandling (ingen andre) skal vi avslutte
     * F_BH_KUN_UTL     Førstegangsbehandling utland (ingen andre) skal vi avslutte
     * F_BH_BO_UTL      Førstegangsbehandling bosatt utland ikke finnes skal vi avslutte
     * F_BH_MED_UTL     Førstegangsbehandling Norge/utland ikke finnes sakl vi avslutte
     *
     */
    fun validerGyldigKravtypeOgArsak(sak: V1Sak, bucType: String) {
        logger.info("start på validering av $bucType")

        validerGyldigKravtypeOgArsakFelles(sak)

        val forsBehanBoUtlanTom = finnKravHistorikk("F_BH_BO_UTL", sak.kravHistorikkListe).isNullOrEmpty()
        val forsBehanMedUtlanTom = finnKravHistorikk("F_BH_MED_UTL", sak.kravHistorikkListe).isNullOrEmpty()
        logger.debug("forsBehanBoUtlanTom: $forsBehanBoUtlanTom, forsBehanMedUtlanTom: $forsBehanMedUtlanTom")
        if (forsBehanBoUtlanTom and forsBehanMedUtlanTom) {
            logger.warn("Det er ikke markert for bodd/arbeidet i utlandet. Krav SED blir ikke opprettet")
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Det er ikke markert for bodd/arbeidet i utlandet. Krav SED blir ikke opprettet")
        }
        logger.info("avslutt på validering av $bucType, fortsetter med preutfylling")
    }

    /**
     * Skal validere på kravtype og kravårrsak Krav SED P2100 Gjenlev
     * https://confluence.adeo.no/pages/viewpage.action?pageId=338181302
     *
     * FORSTEG_BH       Førstegangsbehandling (ingen andre) skal vi avslutte
     * F_BH_KUN_UTL     Førstegangsbehandling utland (ingen andre) skal vi avslutte
     *
     * Kravårsak:
     * GJNL_SKAL_VURD  Gjenlevendetillegg skal vurderes     hvis ikke finnes ved P2100 skal vi avslutte
     * TILST_DOD       Dødsfall tilstøtende                 hvis ikke finnes ved
     *
     */
    fun validerGyldigKravtypeOgArsakGjenlevnde(sak: V1Sak, bucType: String) {
        logger.info("Start på validering av $bucType")
        val validSaktype = listOf("ALDER", "UFOREP")

        validerGyldigKravtypeOgArsakFelles(sak)

        if (hentKravhistorikkForGjenlevende(sak.kravHistorikkListe) == null && validSaktype.contains(sak.sakType)) {
            logger.warn("Ikke korrkt kravårsak for $bucType og (Alder/Uførep")
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingen gyldig kravårsak funnet for ALDER eller UFØREP for utfylling av en krav SED")
        }
        logger.info("Avslutter på validering av $bucType, fortsetter med preutfylling")
    }

    //felles kode for validering av P2000, P2100 og P2200
    private fun validerGyldigKravtypeOgArsakFelles(sak: V1Sak) {
        val finnesKunUtland = finnKravHistorikk("F_BH_KUN_UTL", sak.kravHistorikkListe)
        if (finnesKunUtland.size == sak.kravHistorikkListe.kravHistorikkListe.size)  {
            logger.warn("Søknad gjelder Førstegangsbehandling kun utland. Se egen rutine på navet")
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Søknad gjelder Førstegangsbehandling kun utland. Se egen rutine på navet")
        }

        val fortegBH = finnKravHistorikk("FORSTEG_BH", sak.kravHistorikkListe)
        if (fortegBH.size == sak.kravHistorikkListe.kravHistorikkListe.size)  {
            logger.warn("Det er ikke markert for bodd/arbeidet i utlandet. Krav SED kan ikke opprettet")
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Det er ikke markert for bodd/arbeidet i utlandet. Krav SED kan ikke opprettet")
        }
    }

    private  fun finnKravHistorikk(kravType: String, kravHistorikkListe: V1KravHistorikkListe): List<V1KravHistorikk> {
        if (kravHistorikkListe.kravHistorikkListe == null) {
            logger.error("KravHistorikkListe er tom")
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "KravHistorikkListe er tom")
        }
        return kravHistorikkListe.kravHistorikkListe
            .sortedBy { kravHistorikk -> kravHistorikk.mottattDato.toGregorianCalendar() }
            .filter { kravHistorikk -> kravType == kravHistorikk.kravType }
    }

    private fun hentKravhistorikkForGjenlevende(kravHistorikkListe: V1KravHistorikkListe): V1KravHistorikk? {
        val kravHistorikk = kravHistorikkListe.kravHistorikkListe.filter { krav -> krav.kravArsak == "GJNL_SKAL_VURD" || krav.kravArsak == "TILST_DOD"  }
        if (kravHistorikk.isNotEmpty()) {
            return kravHistorikk.first()
        }
        logger.error("Fant ikke Kravhistorikk med bruk av kravårsak: ${"GJNL_SKAL_VURD"} eller ${"TILST_DOD"} ")
        return null
    }

}