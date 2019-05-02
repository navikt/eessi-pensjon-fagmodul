package no.nav.eessi.eessifagmodul.services

import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.prefill.PrefillDataModel
import no.nav.eessi.eessifagmodul.prefill.PrefillSED
import no.nav.eessi.eessifagmodul.services.eux.BucSedResponse
import no.nav.eessi.eessifagmodul.services.eux.EuxService
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
    service function to prefill sed and call eux to put sed on existing buc
     */
    @Throws(EuxGenericServerException::class, SedDokumentIkkeOpprettetException::class)
    fun prefillAndAddSedOnExistingCase(dataModel: PrefillDataModel): BucSedResponse {

        val data = prefillSed(dataModel)
        val navSed = data.sed

        return euxService.opprettSedOnBuc(navSed, data.euxCaseID)
    }

    /**
     * service function to prefill sed and call eux and then return model with euxCaseId (rinaID back)
     */
    @Throws(EuxServerException::class, RinaCasenrIkkeMottattException::class)
    fun prefillAndCreateSedOnNewCase(dataModel: PrefillDataModel): BucSedResponse {

        val data = prefillSed(dataModel)
        val mottakerId = getFirstInstitution(data.institution)

        return euxService.opprettBucSed(data.sed, data.buc, mottakerId, data.penSaksnummer)
    }

    //muligens midlertidig metode for å sende kun en mottaker til EUX.
    //TODO: funksjon for å legge til flere mottaker (InstitusjonItem) til Rina/SED etter oppretting.
    private fun getFirstInstitution(institutions: List<InstitusjonItem>): String {
        institutions.forEach {
            return it.institution ?: throw IkkeGyldigKallException("institujson kan ikke være tom")
        }
        throw IkkeGyldigKallException("Mangler mottaker register (InstitusjonItem)")
    }


}