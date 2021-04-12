package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper

import no.nav.eessi.pensjon.eux.model.sed.AndreinstitusjonerItem
import no.nav.eessi.pensjon.eux.model.sed.Opphoer
import no.nav.eessi.pensjon.eux.model.sed.Tilleggsinformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper.VedtakPensjonDataHelper.hentVilkarsResultatHovedytelse
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper.VedtakPensjonDataHelper.hentYtelseBelop
import no.nav.eessi.pensjon.utils.simpleFormat
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object PrefillPensjonTilleggsinformasjon {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillPensjonTilleggsinformasjon::class.java) }

    //6.2
    fun createTilleggsinformasjon(pendata: Pensjonsinformasjon, andreinstitusjonerItem: AndreinstitusjonerItem?): Tilleggsinformasjon {

        logger.debug("PrefillPensjonTilleggsinformasjon")
        logger.debug("6.2           Tilleggsinformasjon")
        return Tilleggsinformasjon(

                //6.2-6.3
                opphoer = createOpphoer(pendata),

                //6.5.2.1
                //På logges EnhetID /NAV avsender (PENSJO, UTFORP, ETTERLATP)?
                andreinstitusjoner = createAndreinstitusjonerItem(andreinstitusjonerItem),

                //6.4 //Vedtakets utstedelsesdato (angitt på vedtaket
                dato = createTilleggsInfoDato(pendata)
        )
    }

    private fun createOpphoer(pendata: Pensjonsinformasjon): Opphoer? {
        logger.debug("6.2       Opphører..")

        val dato = createOpphorerDato(pendata)
        val annulleringdato = createAnnulleringDato(pendata)

        val opphorer = Opphoer(
                //6.2
                dato = dato,
                //6.3
                annulleringdato = annulleringdato
        )

        return opphorer.takeUnless { dato == null && annulleringdato == null }
    }

    private fun createTilleggsInfoDato(pendata: Pensjonsinformasjon): String {
        //6.4 //
        logger.debug("6.4       Tilleggsinformasjon dato (dato vedtak)")
        return pendata.vedtak.datoFattetVedtak.simpleFormat()

    }


    //6.5.2.1
    private fun createAndreinstitusjonerItem(andreinstitusjonerItem: AndreinstitusjonerItem?): List<AndreinstitusjonerItem>? {
        logger.debug("6.5.2.1       AndreinstitusjonerItem (review address)")
        val data = andreinstitusjonerItem ?: return null
        return listOf(data)
    }

    /**
     * 6.2
     *
     * HVIS kravtype er Revurdering,
     * OG Resultat er Opphør,
     * OG Resultatbegrunnelse er Annullering,
     * OG vedtak er attestert
     * SÅ skal Antatt virkningsdato vises her.  Datoen skal vises i formatet DD-MM-YYYY
     */
    private fun createOpphorerDato(pendata: Pensjonsinformasjon): String? {
        logger.debug("6.2       OpphorerDato")

        val resultatbegrunnelse = hentVilkarsResultatHovedytelse(pendata)

        if ("REVURD" == pendata.vedtak.kravGjelder && "ANNULERING" == resultatbegrunnelse)
            return pendata.vedtak.virkningstidspunkt.simpleFormat()

        return null
    }

    /**
     * 6.3
     *
     * HVIS kravtype er Førstegangsbehandling,
     * OG status på kravet er endret fra «Til behandling» til «Trukket»,
     * SÅ skal Antatt virkningsdato vises her.
     *
     * HVIS kravtype er Endring uttaksgrad,
     * OG uttaksgrad er endret til null,
     * OG vedtak er attestert,
     * SÅ skal Antatt virkningsdato vises her.
     * Datoen skal vises i formatet DD-MM-YYYY
     */
    private fun createAnnulleringDato(pendata: Pensjonsinformasjon): String? {
        logger.debug("6.x       AnnulleringDato")
        val v1Vedtak = pendata.vedtak

        if (v1Vedtak.isHovedytelseTrukket && v1Vedtak.kravGjelder == "F_BH_BO_UTL") {
            return v1Vedtak.virkningstidspunkt.simpleFormat()
        }

        if ("ENDR_UTTAKSGRAD" == v1Vedtak.kravGjelder && "0" == hentYtelseBelop(pendata))
            return v1Vedtak.virkningstidspunkt.simpleFormat()

        return null
    }
}
