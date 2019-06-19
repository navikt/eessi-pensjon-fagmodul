package no.nav.eessi.eessifagmodul.services

import no.nav.eessi.eessifagmodul.models.*
import no.nav.eessi.eessifagmodul.prefill.PrefillDataModel
import no.nav.eessi.eessifagmodul.prefill.PrefillSED
import no.nav.eessi.eessifagmodul.services.eux.BucSedResponse
import no.nav.eessi.eessifagmodul.services.eux.BucUtils
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
     will also add any new institutions on existing case eg add or send a X005 sed.
     */
    @Throws(EuxGenericServerException::class, SedDokumentIkkeOpprettetException::class)
    fun prefillAndAddInstitusionAndSedOnExistingCase(dataModel: PrefillDataModel): ShortDocumentItem {
        logger.debug("Prøver å legge til Deltaker/Institusions på buc samt prefillSed og sende inn til Rina ")

        val  bucUtil = euxService.getBucUtils(dataModel.euxCaseID)

        addInstitution(bucUtil, dataModel)
        //ferdig med å legge til X005 eller Institusjon/Deltaker

        //prefiller orginal SED og oppreter denne inn i RRina
        //val data = prefillSed(dataModel)
        logger.debug("Prøver å sende SED:${dataModel.getSEDid()} inn på buc: ${dataModel.euxCaseID}")
        return prefillAndAddSedOnExistingCase(dataModel)
    }

    //Legger til Deltakere på buc eller oppretter X005
    fun addInstitution(bucUtil: BucUtils, data: PrefillDataModel) {
        val deltakerListe = addInstitutionsOrCreateX005(data, bucUtil)

        logger.debug("DeltakerListe (InstitusjonItem) size: ${deltakerListe.size}")
        if (deltakerListe.isNotEmpty()) {
            val bucX005 = bucUtil.findFirstDocumentItemByType("X005")

            //bucX005 er null kan vi legge til Deltakere/Institusjon på vanlig måte
            if (bucX005 == null) {
                logger.debug("X005 finnes ikke på buc, legger til Deltakere/Institusjon på vanlig måte")
                euxService.addDeltagerInstitutions(data.euxCaseID, deltakerListe)

            //bucX005 ikke er null må det sendes en X005 sed for hver ny Deltaker/Institusjon
            } else {
                logger.debug("X005 finnes på buc, Sed X005 prefills og sendes inn")
                addX005(data, deltakerListe)
            }
        }
    }

    //skal legge til Deltakere/Instiusjon på vanlig måte.
    // frem til det finnes er lagt inn og sendt en SED ut av RINA
    fun addInstitutionsOrCreateX005(data: PrefillDataModel, bucUtil: BucUtils): List<InstitusjonItem> {
        val bucListe = bucUtil.getParticipantsExclusiveCaseownerAsInstitusjonItem()
        val dataListe = data.getInstitutionsList()

        logger.debug("Prøver å filtere ut like InstitusjonItem i Buc og Nye(fra Requestkall) BucSize: ${bucListe.size} mot dataListe: ${dataListe.size} ")

        return bucUtil.matchParticipantsToInstitusjonItem(bucListe, dataListe)
    }


    //Oppretter NavSedX005 og sender til Rina
    fun addX005(data: PrefillDataModel, deltakerListe: List<InstitusjonItem>): Boolean {

        val resultX005 = mutableListOf<String>()

        val datax005 = PrefillDataModel().apply {
            sed = SED.create(SEDType.X005.name)
            penSaksnummer = data.penSaksnummer
            personNr = data.personNr
            euxCaseID = data.euxCaseID
        }
        logger.debug("Prefill X005")
        val x005 = prefillSED.prefill(datax005)
        val sedX005 = x005.sed

        //opprette en X005 pr unik institusjon i filtrerteInstitusjon
        deltakerListe.forEach {
            try {
                logger.debug("Legger til Institusjon på X005 ${it.institution}")
                //ID og Navn på X005 er påkrevd må hente innn navn fra UI.
                val institusjonX005 = InstitusjonX005(
                        id = checkAndConvertInstituion(it),
                        navn = it.name ?: checkAndConvertInstituion(it)
                )
                sedX005.nav?.sak?.leggtilinstitusjon?.institusjon = institusjonX005
                val docresult = euxService.opprettSedOnBuc(sedX005, datax005.euxCaseID)
                //sjekk på vellykket X005
                if (docresult.caseId == data.euxCaseID) {
                    logger.debug("X005 Sed response fra Rina: $docresult")
                    resultX005.add(docresult.documentId)
                }
            } catch (sx: SedDokumentIkkeOpprettetException) {
                logger.warn("Feiler ved innsending av X005 til Rina.")
                throw sx
            } catch (ex: Exception) {
                logger.warn("Feiler ved å legge til en X005 til rina på caseid: ${datax005.euxCaseID}")
                //??? bør vi kaste en exc?
            }
        }
        return deltakerListe.size == resultX005.size
    }

    //sjekker på Instisjon legger ut ID til rina som <XX:ZZZZZ>
    fun checkAndConvertInstituion(item: InstitusjonItem): String {
        val institution = item.institution ?: ""
        val country = item.country ?: ""
        if (institution.contains(":")) {
            return institution
        }
        return "$country:$institution"
    }

    /**
    service function to prefill sed and call eux to put sed on existing type
     */
    @Throws(EuxGenericServerException::class, SedDokumentIkkeOpprettetException::class)
    fun prefillAndAddSedOnExistingCase(dataModel: PrefillDataModel): ShortDocumentItem {

        val data = prefillSed(dataModel)
        val navSed = data.sed

        val docresult = euxService.opprettSedOnBuc(navSed, data.euxCaseID)
        return euxService.getBucUtils(docresult.caseId).findDocument(docresult.documentId)
}

    fun addDeltakerInstitutions(data: PrefillDataModel): Boolean {
        try {
            val participantList = euxService.getBucUtils(data.euxCaseID).getParticipants().orEmpty()
            if (participantList.size <= 1) {
                logger.debug("legger til deltaker på buc: ${data.euxCaseID}")
                return euxService.addDeltagerInstitutions(data.euxCaseID, data.institution)
            }
        } catch (ex: Exception) {
            logger.warn("Error ved oppretting av deltaker på BUC ${data.euxCaseID}")
        }
        return false
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
    fun getFirstInstitution(institutions: List<InstitusjonItem>): InstitusjonItem {
        return institutions.firstOrNull() ?: throw IkkeGyldigKallException("institujson kan ikke være tom")
    }



}