package no.nav.eessi.pensjon.fagmodul.prefill.pen

import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonClient
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
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

    //hjelemetode for Vedtak P6000 P5000
    fun hentMedVedtak(vedtakId: String): Pensjonsinformasjon {
        if (vedtakId.isBlank()) throw IkkeGyldigKallException("Mangler vedtakID")
        return pensjonsinformasjonClient.hentAltPaaVedtak(vedtakId)
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