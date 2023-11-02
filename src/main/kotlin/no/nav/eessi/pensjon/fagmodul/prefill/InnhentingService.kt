package no.nav.eessi.pensjon.fagmodul.prefill

import jakarta.annotation.PostConstruct
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.fagmodul.prefill.klient.PrefillKlient
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.*
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe.*
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonService
import no.nav.eessi.pensjon.shared.api.ApiRequest
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.vedlegg.VedleggService
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@Service
class InnhentingService(
    private val personService: PersonService,
    private val vedleggService: VedleggService,
    private val prefillKlient: PrefillKlient,
    private val pensjonsinformasjonService: PensjonsinformasjonService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private lateinit var HentPerson: MetricsHelper.Metric
    private lateinit var addInstutionAndDocumentBucUtils: MetricsHelper.Metric

    private val logger = LoggerFactory.getLogger(InnhentingService::class.java)
    init {
        HentPerson = metricsHelper.init("HentPerson", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
        addInstutionAndDocumentBucUtils = metricsHelper.init(
            "AddInstutionAndDocumentBucUtils",
            ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST)
        )
    }

    //TODO hentFnrEllerNpidForAktoerIdfraPDL burde ikke tillate null eller tom AktoerId
    private fun hentFnrEllerNpidForAktoerIdfraPDL(aktoerid: String): Ident? {
        if (aktoerid.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Fant ingen aktoerident")

        val fnr = personService.hentIdent(FOLKEREGISTERIDENT, AktoerId(aktoerid))
        if(fnr?.id?.isNotEmpty() == true) return fnr.also { logger.info("Returnerer FNR for aktoerId: $aktoerid") }

        val npid = personService.hentIdent(NPID, AktoerId(aktoerid))
        if(npid?.id?.isNotEmpty() == true) return npid.also { logger.info("Returnerer NPID for aktoerId: $aktoerid") }
        return null
    }

    //Hjelpe funksjon for å validere og hente aktoerid for evt. avdodfnr fra UI (P2100) - PDL
    fun getAvdodId(bucType: BucType, avdodIdent: String?): String? {
        if (avdodIdent?.isBlank() == true) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ident har tom input-verdi")

        val fnrEllerNpid = Fodselsnummer.fra(avdodIdent)
        val ident = if (fnrEllerNpid?.erNpid != true) avdodIdent?.let { NorskIdent(it) }
        else avdodIdent?.let { Npid(it) }

        return when (bucType) {
            P_BUC_02 -> {
                if (avdodIdent == null) {
                    logger.warn("Mangler fnr for avdød")
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Mangler fnr for avdød")
                }
                personService.hentIdent(AKTORID, ident!!)?.id
            }

            P_BUC_05, P_BUC_06, P_BUC_10 -> {
                if (avdodIdent == null) return null
                return try {
                    personService.hentIdent(AKTORID, ident!!)?.id
                } catch (ex: Exception) {
                    throw ResponseStatusException(HttpStatus.NOT_FOUND, "Korrekt aktoerIdent ikke funnet")
                }
            }
            else -> null
        }

    }

    fun hentFnrfraAktoerService(aktoerid: String?): Ident? = aktoerid?.let { hentFnrEllerNpidForAktoerIdfraPDL(it) }
    fun hentFnrEllerNpidfraAktoerService(aktoerId: Ident): Ident = hentFnrEllerNpidForAktoerIdfraPDL(aktoerId.id) as Ident

    fun hentRinaSakIderFraJoarksMetadata(aktoerid: String): List<String> =
        vedleggService.hentRinaSakIderFraMetaData(aktoerid)

    fun hentPreutyltSed(apiRequest: ApiRequest): String = prefillKlient.hentPreutfyltSed(apiRequest)

    fun hentPensjoninformasjonVedtak(vedtakId: String) = pensjonsinformasjonService.hentAltPaaVedtak(vedtakId)

    fun hentAvdodeFnrfraPensjoninformasjon(pensjoninformasjon: Pensjonsinformasjon): List<String>? =
        pensjonsinformasjonService.hentGyldigAvdod(pensjoninformasjon)

}