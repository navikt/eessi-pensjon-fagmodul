package no.nav.eessi.pensjon.fagmodul.prefill

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.utils.mapAnyToJson
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
        val periodeInfo: Any? = null,
        val buc: String? = null,
        val sed: String? = null,
        val documentid: String? = null,
        val euxCaseId: String? = null,
        val institutions: List<InstitusjonItem>? = null,
        val subjectArea: String? = null,
        val skipSEDkey: List<String>? = null,
        val mockSED: Boolean? = null
) {

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
                    logger.info("ALL SED on existing Rina -> SED: ${request.sed} -> euxCaseId: ${request.sakId}")
                    val pinid = fodselsnr
                    PrefillDataModel().apply {
                        penSaksnummer = request.sakId
                        sed = SED(request.sed)
                        aktoerID = request.aktoerId
                        personNr = pinid
                        euxCaseID = request.euxCaseId
                        institution = request.institutions
                        vedtakId = request.vedtakId ?: ""
                        if (request.buc == "P_BUC_02") {
                            avdod = request.avdodfnr ?: throw MangelfulleInndataException("Mangler Personnr på Avdød")
                            avdodAktorID = avdodaktoerID ?: throw MangelfulleInndataException("Mangler AktoerId på Avdød")
                        }
                        partSedAsJson[request.sed] = request.payload ?: "{}"
                        skipSedkey = request.skipSEDkey
                                ?: listOf("PENSED") //skipper all pensjon utfylling untatt kravdato
                    }
                }
                else -> throw MangelfulleInndataException("Mangler SED, eller ugyldig type SED")
            }
        }

        //validatate request and convert to PrefillDataModel
        fun buildPrefillDataModelConfirm(request: ApiRequest, fodselsnr: String, avdodaktoerID: String?): PrefillDataModel {
            return when {
                request.buc == null -> throw MangelfulleInndataException("Mangler BUC")
                request.sed == null -> throw MangelfulleInndataException("Mangler SED")
                request.aktoerId == null -> throw MangelfulleInndataException("Mangler AktoerID")

                SEDType.isValidSEDType(request.sed) -> {
                    PrefillDataModel().apply {
                        penSaksnummer = request.sakId
                        sed = SED(request.sed)
                        aktoerID = request.aktoerId
                        personNr = fodselsnr
                        vedtakId = request.vedtakId ?: ""
                        partSedAsJson[request.sed] = request.payload ?: "{}"
                    if (request.buc == "P_BUC_02") {
                        avdod = request.avdodfnr ?: throw MangelfulleInndataException("Mangler Personnr på Avdød")
                        avdodAktorID = avdodaktoerID ?: throw MangelfulleInndataException("Mangler AktoerId på Avdød")
                    }
                    if (request.sed == SEDType.P4000.name)    {
                        request.periodeInfo
                    }
//                    if (request.payload != null) {
//                        partSedAsJson[request.sed] = request.payload
//                    }
                        skipSedkey = request.skipSEDkey ?: listOf("PENSED")
                    }
                }
                else -> throw MangelfulleInndataException("Mangler SED, eller ugyldig type SED")
            }
        }

        //validatate request and convert to PrefillDataModel
        fun buildPrefillDataModelOnNew(request: ApiRequest, fodselsnr: String): PrefillDataModel {
            return when {
                //request.sakId == null -> throw MangelfulleInndataException("Mangler Saksnummer")
                request.sed == null -> throw MangelfulleInndataException("Mangler SED")
                request.aktoerId == null -> throw MangelfulleInndataException("Mangler AktoerID")
                request.buc == null -> throw MangelfulleInndataException("Mangler BUC")
                request.subjectArea == null -> throw MangelfulleInndataException("Mangler Subjekt/Sektor")
                request.institutions == null -> throw MangelfulleInndataException("Mangler Institusjoner")

                //Denne validering og utfylling kan benyttes på SED P2000,P2100,P2200
                SEDType.isValidSEDType(request.sed) -> {
                    logger.info("ALL SED on new RinaCase -> SED: ${request.sed}")
                    val pinid = fodselsnr
                    PrefillDataModel().apply {
                        penSaksnummer = request.sakId
                        buc = request.buc
                        rinaSubject = request.subjectArea
                        sed = SED(request.sed)
                        aktoerID = request.aktoerId
                        personNr = pinid
                        institution = request.institutions
                        vedtakId = request.vedtakId ?: ""
                        partSedAsJson[request.sed] = request.payload ?: "{}"
                        skipSedkey = request.skipSEDkey ?: listOf("PENSED")
                    }
                }
                else -> throw MangelfulleInndataException("Mangler SED, eller ugyldig type SED")
            }
        }
    }
}

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
class MangelfulleInndataException(message: String) : IllegalArgumentException(message)
