package no.nav.eessi.pensjon.fagmodul.prefill

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.fagmodul.models.ApiRequest
import no.nav.eessi.pensjon.fagmodul.models.MangelfulleInndataException
import no.nav.eessi.pensjon.fagmodul.prefill.klient.PrefillKlient
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentType
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonService
import no.nav.eessi.pensjon.utils.Fodselsnummer
import no.nav.eessi.pensjon.vedlegg.VedleggService
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import javax.annotation.PostConstruct

@Service
class InnhentingService(
    private val personService: PersonService,
    private val vedleggService: VedleggService,
    private val prefillKlient: PrefillKlient,
    private val pensjonsinformasjonService: PensjonsinformasjonService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private lateinit var HentPerson: MetricsHelper.Metric
    private lateinit var addInstutionAndDocumentBucUtils: MetricsHelper.Metric

    private val logger = LoggerFactory.getLogger(InnhentingService::class.java)

    @PostConstruct
    fun initMetrics() {
        HentPerson = metricsHelper.init("HentPerson", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
        addInstutionAndDocumentBucUtils = metricsHelper.init(
            "AddInstutionAndDocumentBucUtils",
            ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST)
        )
    }

    private fun hentFnrfraAktoerIdfraPDL(aktoerid: String?): String {
        if (aktoerid.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Fant ingen aktoerident")
        }
        return personService.hentIdent(IdentType.NorskIdent, AktoerId(aktoerid)).id
    }


    //Hjelpe funksjon for å validere og hente aktoerid for evt. avdodfnr fra UI (P2100) - PDL
    fun getAvdodAktoerIdPDL(request: ApiRequest): String? {
        val buc = request.buc ?: throw MangelfulleInndataException("Mangler Buc")
        return when (buc) {
            "P_BUC_02" -> {
                val norskIdent = request.riktigAvdod() ?: run {
                    logger.error("Mangler fnr for avdød")
                    throw MangelfulleInndataException("Mangler fnr for avdød")
                }
                if (norskIdent.isBlank()) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ident har tom input-verdi")
                }
                personService.hentIdent(IdentType.AktoerId, NorskIdent(norskIdent)).id
            }
            "P_BUC_05", "P_BUC_06", "P_BUC_10" -> {
                val norskIdent = request.riktigAvdod() ?: return null
                if (norskIdent.isBlank()) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ident har tom input-verdi")
                }

                val gyldigNorskIdent = Fodselsnummer.fra(norskIdent)
                return try {
                    personService.hentIdent(IdentType.AktoerId, NorskIdent(norskIdent)).id
                } catch (ex: Exception) {
                    if (gyldigNorskIdent == null) logger.error("NorskIdent er ikke gyldig")
                    throw ResponseStatusException(HttpStatus.NOT_FOUND, "Korrekt aktoerIdent ikke funnet")
                }
            }
            else -> null
        }
    }

    fun hentFnrfraAktoerService(aktoerid: String?): String = hentFnrfraAktoerIdfraPDL(aktoerid)

    fun hentRinaSakIderFraMetaData(aktoerid: String): List<String> = vedleggService.hentRinaSakIderFraMetaData(aktoerid)

    fun hentPreutyltSed(apiRequest: ApiRequest): String = prefillKlient.hentPreutfyltSed(apiRequest)

    fun hentMedVedtak(vedtakId: String) = pensjonsinformasjonService.hentMedVedtak(vedtakId)

    fun hentGyldigAvdod(pensjoninformasjon: Pensjonsinformasjon) = pensjonsinformasjonService.hentGyldigAvdod(pensjoninformasjon)

}