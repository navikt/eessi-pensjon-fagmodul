package no.nav.eessi.pensjon.fagmodul.prefill.pen

import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjoninformasjonException
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonClient
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.sak.V1Sak
import no.nav.pensjon.v1.vedtak.V1Vedtak
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

/**
 * hjelpe klass for utfylling av alle SED med pensjondata fra PESYS.
 * sakid eller vedtakid.
 */
@Component
class PensjonsinformasjonService(private val pensjonsinformasjonClient: PensjonsinformasjonClient) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PensjonsinformasjonService::class.java) }

    companion object {
        //hjelpe metode for å hente ut valgt V1SAK på vetak/SAK fnr og sakid benyttes
        fun finnSak(sakId: String, pendata: Pensjonsinformasjon): V1Sak? {
            if (sakId.isBlank()) throw ManglendeSakIdException("Mangler sakId")
            return PensjonsinformasjonClient.finnSak(sakId, pendata)
        }
    }

    //hjelemetode for Vedtak P6000 P5000
    fun hentMedVedtak(vedtakId: String): Pensjonsinformasjon {
        if (vedtakId.isBlank()) throw IkkeGyldigKallException("Mangler vedtakID")
        return pensjonsinformasjonClient.hentAltPaaVedtak(vedtakId)
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

    fun hentVedtak(vedtakId: String): Pensjonsinformasjon {
        logger.debug("----------------------------------------------------------")
        val starttime = System.nanoTime()

        logger.debug("Starter [vedtak] Preutfylling Utfylling Data")

        logger.debug("vedtakId: $vedtakId")
        val pensjonsinformasjon = hentMedVedtak(vedtakId)

        logger.debug("Henter pensjondata fra PESYS")

        val endtime = System.nanoTime()
        val tottime = endtime - starttime

        logger.debug("Ferdig hentet pensjondata fra PESYS. Det tok ${(tottime / 1.0e9)} sekunder.")
        logger.debug("----------------------------------------------------------")

        return pensjonsinformasjon
    }

    fun hentRelevantPensjonSak(prefillData: PrefillDataModel, akseptabelSakstypeForSed: (String) -> Boolean): V1Sak? {
        val aktorId = prefillData.bruker.aktorId
        val penSaksnummer = prefillData.penSaksnummer
        val sedType = prefillData.sedType

        logger.debug("penSaksnummer: $penSaksnummer")

        if (penSaksnummer.isBlank()) throw ManglendeSakIdException("Mangler sakId")

        val peninfo = hentPensjonInformasjonNullHvisFeil(aktorId) ?:
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ingen pensjoninformasjon funnet")

        return peninfo.let {
            val sak = finnSak(penSaksnummer, it) ?: return null

            if (!akseptabelSakstypeForSed(sak.sakType)) {
                logger.warn("Du kan ikke opprette ${SedTypeAsText(sedType)} i en ${sakTypeAsText(sak.sakType)} (PESYS-saksnr: $penSaksnummer har sakstype ${sak.sakType})")
                throw FeilSakstypeForSedException("Du kan ikke opprette ${SedTypeAsText(sedType)} i en ${sakTypeAsText(sak.sakType)} (PESYS-saksnr: $penSaksnummer har sakstype ${sak.sakType})")
            }
            sak
        }
    }

    fun hentRelevantVedtakHvisFunnet(vedtakId : String): V1Vedtak? {
        if (vedtakId.isBlank()) return null
        return  hentMedVedtak(vedtakId).vedtak
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

    private fun SedTypeAsText(sedType: SedType) =
            when (sedType) {
                SedType.P2000 -> "alderspensjonskrav"
                SedType.P2100 -> "gjenlevende-krav"
                SedType.P2200 -> "uføretrygdkrav"
                else -> sedType.name
            }

    fun hentGyldigAvdod(peninfo: Pensjonsinformasjon) : List<String>? {
        val avdod = peninfo.avdod
        val avdodMor = avdod?.avdodMor
        val avdodFar = avdod?.avdodFar
        val annenAvdod = avdod?.avdod

        return when {
            annenAvdod != null && avdodFar == null && avdodMor == null -> listOf(annenAvdod)
            annenAvdod == null && avdodFar != null && avdodMor == null -> listOf(avdodFar)
            annenAvdod == null && avdodFar == null && avdodMor != null -> listOf(avdodMor)
            annenAvdod == null && avdodFar != null && avdodMor != null -> listOf(avdodFar, avdodMor)
            annenAvdod == null && avdodFar == null && avdodMor == null -> null
            else -> {
                logger.error("Ukjent feil ved henting av buc detaljer for gjenlevende")
                throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Ukjent feil ved henting av buc detaljer for gjenlevende")
            }
        }
    }

}

class IkkeGyldigKallException(reason: String): ResponseStatusException(HttpStatus.BAD_REQUEST, reason)

class ManglendeSakIdException(reason: String): ResponseStatusException(HttpStatus.BAD_REQUEST, reason)

class FeilSakstypeForSedException(reason: String): ResponseStatusException(HttpStatus.BAD_REQUEST, reason)

