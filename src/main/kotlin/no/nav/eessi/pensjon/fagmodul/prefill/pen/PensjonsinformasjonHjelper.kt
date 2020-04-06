package no.nav.eessi.pensjon.fagmodul.prefill.pen

import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjoninformasjonException
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonClient
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.sak.V1Sak
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * hjelpe klass for utfylling av alle SED med pensjondata fra PESYS.
 * sakid eller vedtakid.
 */
@Component
class PensjonsinformasjonHjelper(private val pensjonsinformasjonClient: PensjonsinformasjonClient) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PensjonsinformasjonHjelper::class.java) }

    companion object {
        //hjelpe metode for å hente ut valgt V1SAK på vetak/SAK fnr og sakid benyttes
        fun finnSak(sakId: String, pendata: Pensjonsinformasjon): V1Sak {
            if (sakId.isBlank()) throw IkkeGyldigKallException("Mangler sakId")
            return PensjonsinformasjonClient.finnSak(sakId, pendata) ?: throw IkkeGyldigKallException("Finner ingen sak, saktype på valgt sakId")
        }
    }

    //hjelemetode for Vedtak P6000 P5000
    fun hentMedVedtak(vedtakId: String): Pensjonsinformasjon {
        if (vedtakId.isBlank()) throw IkkeGyldigKallException("Mangler vedtakID")

        val pendata: Pensjonsinformasjon = pensjonsinformasjonClient.hentAltPaaVedtak(vedtakId)

        logger.debug("Pensjonsinformasjon: $pendata"
                + "\nPensjonsinformasjon.vedtak: ${pendata.vedtak}"
                + "\nPensjonsinformasjon.vedtak.virkningstidspunkt: ${pendata.vedtak.virkningstidspunkt}"
                + "\nPensjonsinformasjon.sak: ${pendata.sakAlder}"
                + "\nPensjonsinformasjon.trygdetidListe: ${pendata.trygdetidListe}"
                + "\nPensjonsinformasjon.vilkarsvurderingListe: ${pendata.vilkarsvurderingListe}"
                + "\nPensjonsinformasjon.ytelsePerMaanedListe: ${pendata.ytelsePerMaanedListe}"
                + "\nPensjonsinformasjon.trygdeavtale: ${pendata.trygdeavtale}"
                + "")
        return pendata
    }

    //hjelpe metode for å hente ut date for SAK/krav P2x00 fnr benyttes
    fun hentPersonInformasjonMedAktoerId(aktoerId: String): Pensjonsinformasjon {
        if (aktoerId.isBlank()) throw IkkeGyldigKallException("Mangler AktoerId")

        //**********************************************
        //skal det gjøre en sjekk med en gang på tilgang av data? sjekke person? sjekke pensjon?
        //Nå er vi dypt inne i prefill SED også sjekker vi om vi får hentet ut noe Pensjonsinformasjon
        //hvis det inne inneholder noe data så feiler vi!
        //**********************************************

        val pendata: Pensjonsinformasjon = pensjonsinformasjonClient.hentAltPaaAktoerId(aktoerId)
        if (pendata.brukersSakerListe == null) {
            throw PensjoninformasjonException("Ingen gyldig brukerSakerListe")
        }
        return pendata
    }
}

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class IkkeGyldigKallException(message: String) : IllegalArgumentException(message)
