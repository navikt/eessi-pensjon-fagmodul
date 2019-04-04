package no.nav.eessi.eessifagmodul.prefill

import no.nav.eessi.eessifagmodul.models.IkkeGyldigKallException
import no.nav.eessi.eessifagmodul.models.PensjoninformasjonException
import no.nav.eessi.eessifagmodul.services.pensjonsinformasjon.PensjonsinformasjonService
import no.nav.pensjon.v1.brukersbarn.V1BrukersBarn
import no.nav.pensjon.v1.ektefellepartnersamboer.V1EktefellePartnerSamboer
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.sak.V1Sak
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * hjelpe klass for utfylling av alle SED med pensjondata fra PESYS.
 * sakid eller vedtakid.
 */
@Component
class PensjonsinformasjonHjelper(private val pensjonsinformasjonService: PensjonsinformasjonService, private val eessiInfo: EessiInformasjon) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PensjonsinformasjonHjelper::class.java) }

    //hjelemetode for Vedtak P6000 P5000
    fun hentMedVedtak(prefillData: PrefillDataModel): Pensjonsinformasjon {
        val vedtakId = if (prefillData.vedtakId.isNotBlank()) prefillData.vedtakId else throw IkkeGyldigKallException("Mangler vedtakID")
        val pendata: Pensjonsinformasjon = pensjonsinformasjonService.hentAltPaaVedtak(vedtakId)

        createInstitusionReview(prefillData)

        logger.info("Pensjonsinformasjon: $pendata"
                + "\nPensjonsinformasjon.vedtak: ${pendata.vedtak}"
                + "\nPensjonsinformasjon.vedtak.virkningstidspunkt: ${pendata.vedtak.virkningstidspunkt}"
                + "\nPensjonsinformasjon.sak: ${pendata.sakAlder}"
                + "\nPensjonsinformasjon.trygdetidListe: ${pendata.trygdetidListe}"
                + "\nPensjonsinformasjon.vilkarsvurderingListe: ${pendata.vilkarsvurderingListe}"
                + "\nPensjonsinformasjon.ytelsePerMaanedListe: ${pendata.ytelsePerMaanedListe}"
                + "\nPensjonsinformasjon.trygdeavtale: ${pendata.trygdeavtale}"
                + "\nPensjonsinformasjon.person: ${pendata.person}"
//                + "\nPensjonsinformasjon.person.pin: ${pendata.person.pid}")
                + "")
        return pendata
    }

    //hjelpe metode for å hente ut date for SAK/krav P2x00 fnr benyttes
    fun hentMedFnr(prefillData: PrefillDataModel): Pensjonsinformasjon {
        val fnr = if (prefillData.personNr.isNotBlank()) prefillData.personNr else throw IkkeGyldigKallException("Mangler Fnr")
        val pendata: Pensjonsinformasjon = pensjonsinformasjonService.hentAltPaaFnr(fnr)
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

    fun createInstitusionReview(prefillData: PrefillDataModel) {
        logger.info("Henter ut lokal kontakt, institusjon (NAV Utland)")
        eessiInfo.mapEssiInformasjonTilPrefillDataModel(prefillData)
        logger.info("Andreinstitusjoner: ${prefillData.andreInstitusjon} ")
    }

}