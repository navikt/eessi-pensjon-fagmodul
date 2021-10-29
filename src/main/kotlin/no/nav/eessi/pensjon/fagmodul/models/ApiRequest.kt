package no.nav.eessi.pensjon.fagmodul.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.fagmodul.models.*
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class ApiSubject(
    val gjenlevende: SubjectFnr? = null,
    val avdod: SubjectFnr? = null
)

class SubjectFnr(
        val fnr: String? = null
)

//Samme som SedRequest i frontend-api
@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiRequest(
    val sakId: String,
    val vedtakId: String? = null,
    val kravId: String? = null,
    val kravDato: String? = null,
    val kravType: KravType? = null,
    val aktoerId: String? = null,
    val fnr: String? = null,
    val payload: String? = null,
    val buc: String? = null,
    val sed: String? = null,
    val documentid: String? = null,
    val euxCaseId: String? = null,
    val institutions: List<InstitusjonItem>? = null,
    val subjectArea: String? = null,
    val avdodfnr: String? = null, //kun P2100 på P_BUC_02
    val subject: ApiSubject? = null, //P_BUC_02 alle andre seder etter P2100
    //P8000-P_BUC_05
    val referanseTilPerson: ReferanseTilPerson? = null,

) {

    fun riktigAvdod(): String? {
        return subject?.avdod?.fnr ?: avdodfnr
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ApiRequest::class.java)

        //validatate request and convert to PrefillDataModel
        fun buildPrefillDataModelOnExisting(request: ApiRequest, fodselsnr: String, avdodaktoerID: String? = null): PrefillDataModel {
            val sedType = if (request.sed.isNullOrBlank())
                throw ResponseStatusException(HttpStatus.BAD_REQUEST,"SedType mangler")
            else
                SedType.from(request.sed) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST,"SedType ${request.sed} er ikke gyldig")

            return when {
                request.buc == null -> throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Mangler BUC")
                request.aktoerId == null -> throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Mangler AktoerID")
                request.euxCaseId == null -> throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Mangler euxCaseId (RINANR)")
                request.institutions == null -> throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Mangler Institusjoner")
                else -> {
                    logger.info("ALL SED on existing Rina SED: ${request.sed} -> euxCaseId: ${request.euxCaseId} -> sakNr: ${request.sakId} ")
                    PrefillDataModel(
                            penSaksnummer = request.sakId,
                            bruker = PersonId(fodselsnr, request.aktoerId),
                            avdod = populerAvdodHvisGjenlevendePensjonSak(request, avdodaktoerID),
                            sedType = sedType,
                            buc = request.buc,
                            euxCaseID = request.euxCaseId,
                            institution = request.institutions,
                            refTilPerson = request.referanseTilPerson,
                            vedtakId = request.vedtakId,
                            kravDato = request.kravDato,
                            kravType = request.kravType,
                            kravId = request.kravId
                          ).apply {
                        partSedAsJson[sedType.name] = request.payload ?: "{}"
                    }
                }
            }
        }

        private fun populerAvdodHvisGjenlevendePensjonSak(request: ApiRequest, avdodaktoerID: String?): PersonId? {
            return when(request.buc) {
                "P_BUC_02" -> populerAvdodPersonId(request, avdodaktoerID, true)
                "P_BUC_05","P_BUC_06","P_BUC_10" -> populerAvdodPersonId(request, avdodaktoerID)
                else -> null
            }
        }

        private fun populerAvdodPersonId(request: ApiRequest, avdodaktoerID: String?, kreverAvdod: Boolean = false): PersonId? {
            if (kreverAvdod && avdodaktoerID == null) {
               logger.error("Mangler fnr for avdød")
               throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Mangler fnr for avdød")
            }
            request.riktigAvdod() ?: return null
            val avdodNorskIdent1 = request.riktigAvdod() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Mangler Personnr på Avdød")
            val avdodAktorId1 = avdodaktoerID ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Mangler AktoerId på Avdød")
            return  PersonId(avdodNorskIdent1, avdodAktorId1)
        }

    }
}