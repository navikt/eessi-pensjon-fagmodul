package no.nav.eessi.pensjon.fagmodul.prefill.pen

import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjoninformasjonException
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonService
import no.nav.pensjon.v1.brukersbarn.V1BrukersBarn
import no.nav.pensjon.v1.ektefellepartnersamboer.V1EktefellePartnerSamboer
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
class PensjonsinformasjonHjelper(private val pensjonsinformasjonService: PensjonsinformasjonService) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PensjonsinformasjonHjelper::class.java) }

    //hjelemetode for Vedtak P6000 P5000
    fun hentMedVedtak(prefillData: PrefillDataModel): Pensjonsinformasjon {

        val vedtakId = if (prefillData.vedtakId.isNotBlank()) prefillData.vedtakId else throw IkkeGyldigKallException("Mangler vedtakID")
        val pendata: Pensjonsinformasjon = pensjonsinformasjonService.hentAltPaaVedtak(vedtakId)

        logger.info("Pensjonsinformasjon: $pendata"
                + "\nPensjonsinformasjon.vedtak: ${pendata.vedtak}"
                + "\nPensjonsinformasjon.vedtak.virkningstidspunkt: ${pendata.vedtak.virkningstidspunkt}"
                + "\nPensjonsinformasjon.sak: ${pendata.sakAlder}"
                + "\nPensjonsinformasjon.trygdetidListe: ${pendata.trygdetidListe}"
                + "\nPensjonsinformasjon.vilkarsvurderingListe: ${pendata.vilkarsvurderingListe}"
                + "\nPensjonsinformasjon.ytelsePerMaanedListe: ${pendata.ytelsePerMaanedListe}"
                + "\nPensjonsinformasjon.trygdeavtale: ${pendata.trygdeavtale}"
                + "\nPensjonsinformasjon.person: ${pendata.person}"
                + "")
        return pendata
    }

    //hjelpe metode for å hente ut date for SAK/krav P2x00 fnr benyttes
    fun hentPensjoninformasjonMedPinid(prefillData: PrefillDataModel): Pensjonsinformasjon {
        val aktoer = if (prefillData.aktoerID.isNotBlank()) prefillData.aktoerID else throw IkkeGyldigKallException("Mangler AktoerId")

        //**********************************************
        //skal det gjøre en sjekk med en gang på tilgang av data? sjekke person? sjekke pensjon?
        //Nå er vi dypt inne i prefill SED også sjekker vi om vi får hentet ut noe Pensjonsinformasjon
        //hvis det inne inneholder noe data så feiler vi!
        //**********************************************

        val pendata: Pensjonsinformasjon = pensjonsinformasjonService.hentAltPaaAktoerId(aktoer)
        //val pendata: Pensjonsinformasjon = pensjonsinformasjonService.hentAltPaaFnr(fnr)
        if (pendata.brukersSakerListe == null) {
            throw PensjoninformasjonException("Ingen gyldig brukerSakerListe")
        }
        createRelasjonerBarnOgAvdod(prefillData, pendata)
        return pendata
    }

    //hjelpe metode for å hente ut valgt V1SAK på vetak/SAK fnr og sakid benyttes
    fun hentMedSak(prefillData: PrefillDataModel, pendata: Pensjonsinformasjon): V1Sak {
        val sakId = if (prefillData.penSaksnummer.isNotBlank()) prefillData.penSaksnummer else throw IkkeGyldigKallException("Mangler sakId")
        return pensjonsinformasjonService.hentAltPaaSak(sakId, pendata) ?: throw IkkeGyldigKallException("Finner ingen sak, saktype på valgt sakId")
    }

    //henter ut nødvendige familie relasjoner
    fun createRelasjonerBarnOgAvdod(dataModel: PrefillDataModel, pendata: Pensjonsinformasjon): PrefillDataModel {
        logger.info("Henter ut liste barn fra PESYS")

        val listbarmItem = mutableListOf<V1BrukersBarn>()
        if (pendata.brukersBarnListe != null) {
            pendata.brukersBarnListe.brukersBarnListe.forEach {
                listbarmItem.add(it)
            }
        }

        logger.info("Henter ut liste ektefeller/partnere fra PESYS")
        val listEktefellePartnerFnrlist = mutableListOf<V1EktefellePartnerSamboer>()
        if (pendata.ektefellePartnerSamboerListe != null) {
            pendata.ektefellePartnerSamboerListe.ektefellePartnerSamboerListe.forEach {
                listEktefellePartnerFnrlist.add(it)
            }
        }

        dataModel.partnerFnr = listEktefellePartnerFnrlist
        dataModel.barnlist = listbarmItem

        logger.info("Henter ut avdod relasjoner fra PESYS")
        dataModel.avdod = pendata.avdod?.avdod ?: ""
        dataModel.avdodMor = pendata.avdod?.avdodMor ?: ""
        dataModel.avdodFar = pendata.avdod?.avdodFar ?: ""

        return dataModel
    }
}

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class IkkeGyldigKallException(message: String) : IllegalArgumentException(message)
