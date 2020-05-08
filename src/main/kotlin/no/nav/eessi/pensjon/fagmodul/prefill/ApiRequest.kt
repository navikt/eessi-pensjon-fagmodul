package no.nav.eessi.pensjon.fagmodul.prefill

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.google.common.base.Joiner
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonId
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.eessi.pensjon.utils.typeRefs
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

//Samme som SedRequest i frontend-api
@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiRequest(
        val sakId: String,
        val vedtakId: String? = null,
        val kravId: String? = null,
        val aktoerId: String? = null,
        val fnr: String? = null,
        val avdodfnr: String? = null,
        val payload: String? = null,
        val buc: String? = null,
        val sed: String? = null,
        val documentid: String? = null,
        val euxCaseId: String? = null,
        val institutions: List<InstitusjonItem>? = null,
        val subjectArea: String? = null,
        val skipSEDkey: List<String>? = null
) {
    fun toAudit(): String {
        val json = ApiRequest(
                sakId = sakId,
                vedtakId = vedtakId,
                avdodfnr = avdodfnr,
                buc = buc,
                sed = sed,
                euxCaseId = euxCaseId
        ).toJsonSkipEmpty()
        val map = mapJsonToAny(json, typeRefs<Map<String, String>>())
        return Joiner.on(" ").withKeyValueSeparator(": ").join(map)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ApiRequest::class.java)

        //validatate request and convert to PrefillDataModel
        fun buildPrefillDataModelOnExisting(request: ApiRequest, fodselsnr: String, avdodaktoerID: String?): PrefillDataModel {
            return when {
                request.buc == null -> throw MangelfulleInndataException("Mangler BUC")
                request.sed == null -> throw MangelfulleInndataException("Mangler SED")
                request.aktoerId == null -> throw MangelfulleInndataException("Mangler AktoerID")
                request.euxCaseId == null -> throw MangelfulleInndataException("Mangler euxCaseId (RINANR)")
                request.institutions == null -> throw MangelfulleInndataException("Mangler Institusjoner")

                SEDType.isValidSEDType(request.sed) -> {
                    logger.info("ALL SED on existing Rina -> SED: ${request.sed} -> euxCaseId: ${request.euxCaseId} -> sakNr: ${request.sakId} ")
                    val avdod: PersonId? = populerAvdodHvisGjenlevendePensjonSak(request, avdodaktoerID)
                    PrefillDataModel(penSaksnummer = request.sakId, bruker = PersonId(fodselsnr, request.aktoerId),avdod = avdod).apply {
                        sed = SED(request.sed)
                        buc = request.buc
                        euxCaseID = request.euxCaseId
                        institution = request.institutions
                        vedtakId = request.vedtakId ?: ""
                        partSedAsJson[request.sed] = request.payload ?: "{}"
                        skipSedkey = request.skipSEDkey
                                ?: listOf("PENSED") //skipper all pensjon utfylling untatt kravdato
                    }
                }
                else -> {
                    logger.error("SED: ${request.sed} er ikke støttet")
                    throw MangelfulleInndataException("SED: ${request.sed} er ikke støttet")
                }
            }
        }

        private fun populerAvdodHvisGjenlevendePensjonSak(request: ApiRequest, avdodaktoerID: String?): PersonId? {
            var avdod: PersonId? = null
            val avdodNorskIdent: String?
            val avdodAktorId: String?
            if (request.buc == "P_BUC_02") {
                avdodNorskIdent = request.avdodfnr ?: throw MangelfulleInndataException("Mangler Personnr på Avdød")
                avdodAktorId = avdodaktoerID ?: throw MangelfulleInndataException("Mangler AktoerId på Avdød")
                avdod = PersonId(avdodNorskIdent, avdodAktorId)
            }
            return avdod
        }

        //validatate request and convert to PrefillDataModel
        fun buildPrefillDataModelConfirm(request: ApiRequest, fodselsnr: String, avdodaktoerID: String?): PrefillDataModel {
            return when {
                request.buc == null -> throw MangelfulleInndataException("Mangler BUC")
                request.sed == null -> throw MangelfulleInndataException("Mangler SED")
                request.aktoerId == null -> throw MangelfulleInndataException("Mangler AktoerID")

                SEDType.isValidSEDType(request.sed) -> {
                    val avdod: PersonId? = populerAvdodHvisGjenlevendePensjonSak(request, avdodaktoerID)
                    PrefillDataModel(penSaksnummer = request.sakId, bruker = PersonId(fodselsnr, request.aktoerId), avdod = avdod).apply {
                        sed = SED(request.sed)
                        buc = request.buc
                        vedtakId = request.vedtakId ?: ""
                        partSedAsJson[request.sed] = request.payload ?: "{}"
                        skipSedkey = request.skipSEDkey ?: listOf("PENSED")
                    }
                }
                else -> {
                    logger.error("SED: ${request.sed} er ikke støttet")
                    throw MangelfulleInndataException("SED: ${request.sed} er ikke støttet")
                }
            }
        }
    }
}


@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class MangelfulleInndataException(message: String) : IllegalArgumentException(message)
