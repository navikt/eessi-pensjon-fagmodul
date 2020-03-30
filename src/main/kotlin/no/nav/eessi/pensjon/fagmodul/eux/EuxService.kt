package no.nav.eessi.pensjon.fagmodul.eux

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucSedResponse
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.sedmodel.Krav
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.eessi.pensjon.utils.typeRefs
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.client.RestTemplate

@Service
class EuxService (private val euxKlient: EuxKlient) {

    // Vi trenger denne no arg konstruktøren for å kunne bruke @Spy med mockito
    constructor() : this(EuxKlient(RestTemplate(), MetricsHelper(SimpleMeterRegistry())))

    private val logger = LoggerFactory.getLogger(EuxService::class.java)

    @Throws(EuxGenericServerException::class, SedDokumentIkkeOpprettetException::class)
    fun opprettSvarSedOnBuc(navSED: SED, euxCaseId: String, parentDocumentId: String): BucSedResponse {
        val euxUrlpath = "/buc/{RinaSakId}/sed/{DokuemntId}/svar"
        logger.debug("prøver å kontakte eux-rina-api : $euxUrlpath")
        return euxKlient.opprettSed(euxUrlpath,
                navSED.toJsonSkipEmpty(),
                euxCaseId,
                MetricsHelper.MeterName.OpprettSvarSED,
                "Feil ved opprettSvarSed", parentDocumentId)
    }

    /**
     * Ny SED på ekisterende type
     */
    @Throws(EuxGenericServerException::class, SedDokumentIkkeOpprettetException::class)
    fun opprettSedOnBuc(navSED: SED, euxCaseId: String): BucSedResponse {
        val euxUrlpath = "/buc/{RinaSakId}/sed"
        return euxKlient.opprettSed(euxUrlpath, navSED.toJsonSkipEmpty(), euxCaseId, MetricsHelper.MeterName.OpprettSED, "Feil ved opprettSed", null)
    }

    /**
     * Henter ut sed fra rina med bucid og documentid
     */
    fun getSedOnBuc(euxCaseId: String, sedType: String?): List<SED> {
        logger.info("Prøver å hente ut en BucUtils for type $euxCaseId")
        val docid = getBuc(euxCaseId).documents ?: throw NoSuchFieldException("Fant ikke DocumentsItem")

        val sedlist = mutableListOf<SED>()
        docid.forEach {
            if (sedType != null && sedType == it.type) {
                it.id?.let { id ->
                    sedlist.add(getSedOnBucByDocumentId(euxCaseId, id))
                }
            } else {
                it.id?.let { id ->
                    sedlist.add(getSedOnBucByDocumentId(euxCaseId, id))
                }
            }
        }
        logger.info("return liste av SED for type: $sedType listSize: ${sedlist.size}")
        return sedlist
    }

    fun getBuc(euxCaseId: String): Buc {
        val body = euxKlient.getBucJson(euxCaseId)
        logger.debug("mapper buc om til BUC objekt-model")
        return mapJsonToAny(body, typeRefs())
    }

    @Throws(EuxServerException::class, SedDokumentIkkeLestException::class)
    fun getSedOnBucByDocumentId(euxCaseId: String, documentId: String): SED {
        val json = euxKlient.getSedOnBucByDocumentIdAsJson(euxCaseId, documentId)
        return SED.fromJson(json)
    }

    /**
     * Henter ut Kravtype og Fnr fra P2100 og P15000
     */
    fun hentFnrOgYtelseKravtype(euxCaseId: String, documentId: String): PinOgKrav {
        val sed = getSedOnBucByDocumentId(euxCaseId, documentId)

        //validere om SED er virkelig en P2100 eller P15000
        if (SEDType.P2100.name == sed.sed) {
            return PinOgKrav(
                    fnr = euxKlient.getFnrMedLandkodeNO(sed.pensjon?.gjenlevende?.person?.pin),
                    krav = sed.nav?.krav ?: Krav()
            )
        }
        //P15000 sjekke om det er 02 Gjenlevende eller ikke
        if (SEDType.P15000.name == sed.sed) {
            val krav = sed.nav?.krav ?: Krav()
            return if ("02" == krav.type) {
                PinOgKrav(
                        fnr = euxKlient.getFnrMedLandkodeNO(sed.pensjon?.gjenlevende?.person?.pin),
                        krav = krav
                )
            } else {
                PinOgKrav(
                        fnr = euxKlient.getFnrMedLandkodeNO(sed.nav?.bruker?.person?.pin),
                        krav = sed.nav?.krav ?: Krav()
                )
            }
        }
        throw SedDokumentIkkeGyldigException("SED gyldig SED av type P2100 eller P15000")
    }

    fun getSingleBucAndSedView(euxCaseId: String): BucAndSedView {
        return try {
            BucAndSedView.from(getBuc(euxCaseId))
        } catch (ex: Exception) {
            logger.error("Feiler ved utlevering av enkel bucandsedview ${ex.message}", ex)
            BucAndSedView.fromErr(ex.message)
        }
    }

    fun getBucAndSedView(rinasaker: List<String>): List<BucAndSedView> {
        val startTime = System.currentTimeMillis()
        val list = rinasaker
                .map { rinaid ->
                    try {
                        BucAndSedView.from(getBuc(rinaid))
                    } catch (ex: Exception) {
                        logger.error(ex.message, ex)
                        BucAndSedView.fromErr(ex.message)
                    }
                }
                .toList()

        logger.debug(" ferdig returnerer list av BucAndSedView. Antall BUC: ${list.size}")

        logger.debug(" sortert listen på startDate nyeste dato først")
        val sortlist = list.asSequence().sortedByDescending { it.startDate }.toList()

        logger.debug(" tiden tok ${System.currentTimeMillis() - startTime} ms.")
        return sortlist
    }

    fun addInstitution(euxCaseID: String, nyeInstitusjoner: List<String>) {
        logger.debug("Prøver å legge til Deltaker/Institusions på buc samt prefillSed og sende inn til Rina ")
        logger.info("X005 finnes ikke på buc, legger til Deltakere/Institusjon på vanlig måte")
        euxKlient.putBucMottakere(euxCaseID, nyeInstitusjoner)
    }

    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    class SedDokumentIkkeSendtException(message: String?) : Exception(message)

}