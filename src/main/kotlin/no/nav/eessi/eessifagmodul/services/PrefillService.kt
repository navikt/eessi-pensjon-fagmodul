package no.nav.eessi.eessifagmodul.services

import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.prefill.PrefillDataModel
import no.nav.eessi.eessifagmodul.prefill.PrefillSED
import no.nav.eessi.eessifagmodul.services.eux.BucSedResponse
import no.nav.eessi.eessifagmodul.services.eux.EuxService
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.ShortDocumentItem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PrefillService(private val euxService: EuxService, private val prefillSED: PrefillSED) {

    private val logger = LoggerFactory.getLogger(PrefillService::class.java)

    private val validator = SedValidator()

    //preutfylling av sed fra TPS, PESYS, AAREG o.l skjer her..
    @Throws(SedValidatorException::class)
    fun prefillSed(dataModel: PrefillDataModel): PrefillDataModel {

        val startTime = System.currentTimeMillis()
        val data = prefillSED.prefill(dataModel)
        val endTime = System.currentTimeMillis()
        logger.debug("PrefillSED tok ${endTime - startTime} ms.")

        if (SEDType.P2000.name == data.getSEDid()) {
            validator.validateP2000(data.sed)
        }

        return data
    }

    /**
    service function to prefill sed and call eux to put sed on existing type
     */
    @Throws(EuxGenericServerException::class, SedDokumentIkkeOpprettetException::class)
    fun prefillAndAddSedOnExistingCase(dataModel: PrefillDataModel): ShortDocumentItem {

        val data = prefillSed(dataModel)
        val navSed = data.sed

        val result = euxService.addDeltagerInstitutions(data.euxCaseID, data.institution)

        if (result) {
            val docresult = euxService.opprettSedOnBuc(navSed, data.euxCaseID)

            return euxService.getBucUtils(docresult.caseId).findDocument(docresult.documentId)

        }
        throw SedDokumentIkkeOpprettetException("Feilet ved opprettelse av SED med deltagere")
    }

    /**
     * service function to prefill sed and call eux and then return model with euxCaseId (rinaID back)
     */
    @Throws(EuxServerException::class, RinaCasenrIkkeMottattException::class)
    fun prefillAndCreateSedOnNewCase(dataModel: PrefillDataModel): BucSedResponse {
        val data = prefillSed(dataModel)
        val mottaker = getFirstInstitution(data.institution)
        return euxService.opprettBucSed(data.sed, data.buc, "${mottaker.country}:${mottaker.institution}", data.penSaksnummer)
    }

    //muligens midlertidig metode for å sende kun en mottaker til EUX.
    //TODO: funksjon for å legge til flere mottaker (InstitusjonItem) til Rina/SED etter oppretting.
    private fun getFirstInstitution(institutions: List<InstitusjonItem>): InstitusjonItem {
        return institutions.first() ?: throw IkkeGyldigKallException("institujson kan ikke være tom")
    }



}