package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak

import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper.PrefillPensjonReduksjon
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper.PrefillPensjonSak
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper.PrefillPensjonTilleggsinformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper.PrefillPensjonVedtak
import no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak.hjelper.VedtakPensjonDataHelper.harBoddArbeidetUtland
import no.nav.eessi.pensjon.fagmodul.sedmodel.AndreinstitusjonerItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.Bruker
import no.nav.eessi.pensjon.fagmodul.sedmodel.Pensjon
import no.nav.eessi.pensjon.fagmodul.sedmodel.VedtakItem
import no.nav.eessi.pensjon.utils.simpleFormat
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException


/**
 * Hjelpe klasse for utfyller ut NAV-SED-P6000 med pensjondata med vedtak fra PESYS.
 */
object PrefillP6000Pensjon {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP6000Pensjon::class.java) }

    fun createPensjon(pensjoninformasjon: Pensjonsinformasjon, gjenlevende: Bruker?, vedtakId: String, andreinstitusjonerItem: AndreinstitusjonerItem?): Pensjon {

        //Sjekk opp om det er Bodd eller Arbeid utland. (hvis ikke avslutt)
        if (!harBoddArbeidetUtland(pensjoninformasjon)) throw IkkeGyldigStatusPaaSakException("Har ikke bodd eller arbeidet i utlandet. Avslutter vedtak")
        //Sjekk opp om det finnes et dato fattet vedtak. (hvis ikke avslutt)
        if (pensjoninformasjon.vedtak.datoFattetVedtak == null) {
            throw IkkeGyldigStatusPaaSakException("Vedtaket mangler dato for FattetVedtak. Avslutter")
        }

        //prefill Pensjon obj med data fra PESYS. (pendata)
        logger.debug("4.1       VedtakItem")
        return createPensjonEllerTom(pensjoninformasjon, gjenlevende, andreinstitusjonerItem)

    }

    private fun createPensjonEllerTom(pensjoninformasjon: Pensjonsinformasjon, gjenlevende: Bruker?, andreinstitusjonerItem: AndreinstitusjonerItem?): Pensjon {
        val vilkar = pensjoninformasjon.vilkarsvurderingListe
        val ytelse = pensjoninformasjon.ytelsePerMaanedListe
        val erAvslag = "AVSL" == pensjoninformasjon.vilkarsvurderingListe?.vilkarsvurderingListe?.maxBy{ it.fom.simpleFormat() }?.avslagHovedytelse

        return if (erAvslag || (vilkar == null && ytelse == null) || ytelse.ytelsePerMaanedListe.isNullOrEmpty()) {
            logger.warn("Avslag, Ingen vilkarsvurderingListe og ytelsePerMaanedListe oppretter Vedtak SED P6000 uten pensjoninformasjon")
            val avslagPensjon = createPensjon(pensjoninformasjon, gjenlevende, andreinstitusjonerItem)
            val avslagVedtak = avslagPensjon.vedtak?.firstOrNull()
//          Selvplukk av avslagdata:
//          Type pensjon
//          Type vedtak (resulat)
//          Avslagsgrunner
            Pensjon(
                vedtak = listOf(VedtakItem(type = avslagVedtak?.type, resultat = avslagVedtak?.resultat, avslagbegrunnelse = avslagVedtak?.avslagbegrunnelse )),
                sak = avslagPensjon.sak,
                tilleggsinformasjon = avslagPensjon.tilleggsinformasjon
            )

        } else {
            createPensjon(pensjoninformasjon, gjenlevende, andreinstitusjonerItem)
        }
    }

    private fun createPensjon(pensjoninformasjon: Pensjonsinformasjon, gjenlevende: Bruker?, andreinstitusjonerItem: AndreinstitusjonerItem?): Pensjon {
        return Pensjon(
                gjenlevende = gjenlevende,
                //4.1
                vedtak = listOf(PrefillPensjonVedtak.createVedtakItem(pensjoninformasjon)),
                //5.1
                reduksjon = PrefillPensjonReduksjon.createReduksjon(pensjoninformasjon),
                //6.1
                sak = PrefillPensjonSak.createSak(pensjoninformasjon),
                //6.x
                tilleggsinformasjon = PrefillPensjonTilleggsinformasjon.createTilleggsinformasjon(pensjoninformasjon, andreinstitusjonerItem)
        )
    }
}

class IkkeGyldigStatusPaaSakException(message: String) : ResponseStatusException(HttpStatus.BAD_REQUEST, message)
