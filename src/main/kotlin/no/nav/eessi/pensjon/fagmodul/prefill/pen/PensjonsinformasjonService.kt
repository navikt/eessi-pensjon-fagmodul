package no.nav.eessi.pensjon.fagmodul.prefill.pen

import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
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
class PensjonsinformasjonService(private val pensjonsinformasjonClient: PensjonsinformasjonClient) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PensjonsinformasjonService::class.java) }

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
    fun hentPensjonInformasjon(aktoerId: String): Pensjonsinformasjon {
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

    fun hentPensjonInformasjonNullHvisFeil(aktoerId: String) =
        try {
            hentPensjonInformasjon(aktoerId)
        } catch (pen: PensjoninformasjonException) {
            logger.error(pen.message)
            null
        }

    fun hentVedtak(prefillData: PrefillDataModel): Pensjonsinformasjon {
        val vedtakId = prefillData.vedtakId

        if (vedtakId.isBlank()) throw ManglendeVedtakIdException("Mangler vedtakID")

        logger.debug("----------------------------------------------------------")
        val starttime = System.nanoTime()

        logger.debug("Starter [vedtak] Preutfylling Utfylling Data")

        logger.debug("vedtakId: $vedtakId")
        val pensjonsinformasjon = hentMedVedtak(vedtakId)

        logger.debug("Henter pensjondata fra PESYS")

        val endtime = System.nanoTime()
        val tottime = endtime - starttime

        logger.debug("Metrics")
        logger.debug("Ferdig hentet pensjondata fra PESYS. Det tok ${(tottime / 1.0e9)} sekunder.")
        logger.debug("----------------------------------------------------------")

        return pensjonsinformasjon
    }

    fun hentRelevantPensjonSak(prefillData: PrefillDataModel, akseptabelSakstypeForSed: (String) -> Boolean): V1Sak? {

        val aktorId = prefillData.bruker.aktorId
        val penSaksnummer = prefillData.penSaksnummer
        val sedType = prefillData.getSEDType()

        return hentPensjonInformasjonNullHvisFeil(aktorId)?.let {
            val sak: V1Sak = PensjonsinformasjonService.finnSak(penSaksnummer, it)

            if (!akseptabelSakstypeForSed(sak.sakType)) {
                logger.warn("Du kan ikke opprette ${sedTypeAsText(sedType)} i en ${sakTypeAsText(sak.sakType)} (PESYS-saksnr: $penSaksnummer har sakstype ${sak.sakType})")
                throw FeilSakstypeForSedException("Du kan ikke opprette ${sedTypeAsText(sedType)} i en ${sakTypeAsText(sak.sakType)} (PESYS-saksnr: $penSaksnummer har sakstype ${sak.sakType})")
            }
            sak
        }
    }

    private fun sakTypeAsText(sakType: String?) =
            when (sakType) {
                "UFOREP" -> "uføretrygdsak"
                "ALDER" -> "alderspensjonssak"
                "GJENLEV" -> "gjenlevendesak"
                "BARNEP" -> "barnepensjonssak"
                null -> "[NULL]"
                else -> "$sakType-sak"
            }

    private fun sedTypeAsText(sedType: String) =
            when (sedType) {
                "P2000" -> "alderspensjonskrav"
                "P2100" -> "gjenlevende-krav"
                "P2200" -> "uføretrygdkrav"
                else -> sedType
            }

}

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class IkkeGyldigKallException(message: String) : IllegalArgumentException(message)


@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class ManglendeVedtakIdException(message: String) : IllegalArgumentException(message)

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class FeilSakstypeForSedException(override val message: String?, override val cause: Throwable? = null) : IllegalArgumentException()

