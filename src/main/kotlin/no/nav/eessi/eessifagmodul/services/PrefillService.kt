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

    //preutfylling av sed fra TPS, PESYS, AAREG o.l skjer her..
    fun prefillSed(dataModel: PrefillDataModel): PrefillDataModel {

        val data = prefillSED.prefill(dataModel)

//        val sed = data.sed
//        sed.nav?.eessisak = null
//        sed.nav?.bruker?.person?.pin
//        sed.nav?.bruker?.adresse = null
//        sed.nav?.barn = null
//        sed.nav?.bruker?.far = null
//        sed.nav?.bruker?.mor = null
//        sed.nav?.ektefelle = null
//        sed.pensjon = Pensjon()
//        val sed = SED.create(data.getSEDid())
//        //sed.nav = Nav()
//        sed.nav = data.sed.nav
//       sed.pensjon = Pensjon()
//       data.sed = sed
//         data.sed.pensjon = Pensjon()

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