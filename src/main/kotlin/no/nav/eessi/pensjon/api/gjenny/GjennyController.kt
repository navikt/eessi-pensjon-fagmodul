package no.nav.eessi.pensjon.api.gjenny

import no.nav.eessi.pensjon.eux.klient.BucSedResponse
import no.nav.eessi.pensjon.eux.klient.Rinasak
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.ActionOperation
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.fagmodul.api.FrontEndResponse
import no.nav.eessi.pensjon.fagmodul.api.P8000Frontend
import no.nav.eessi.pensjon.fagmodul.eux.*
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService.BucView
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService.BucViewKilde
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.gcp.GjennySak
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.shared.api.ApiRequest
import no.nav.eessi.pensjon.shared.api.PersonInfo
import no.nav.eessi.pensjon.shared.api.PrefillDataModel
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.eessi.pensjon.vedlegg.VedleggService
import no.nav.security.token.support.core.api.Unprotected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.server.ResponseStatusException

@Unprotected
@RestController
@RequestMapping("/gjenny")
/**
 * Endepunkter for Gjenny
 */
class GjennyController (
    private val euxInnhentingService: EuxInnhentingService,
    private val euxPrefillService: EuxPrefillService,
    private val innhentingService: InnhentingService,
    private val gcpStorageService: GcpStorageService,
    private val personService: PersonService,
    private val auditlogger: AuditLogger,
    private val vedleggService: VedleggService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
            @GetMapping("/metadata/{aktoerId}")
            fun hentMetadata(@PathVariable("aktoerId", required = true) aktoerId: String): ResponseEntity<String> {
                logger.info("Henter metadata for dokumenter i SAF for aktørid: $aktoerId via GjennyController")
                val metadata = vedleggService.hentDokumentMetadata(aktoerId)
                return ResponseEntity.ok().body(metadata.toJson())
            }
    private val logger = LoggerFactory.getLogger(GjennyController::class.java)
    private val secureLog = LoggerFactory.getLogger("secureLog")
    private val bucerForGjenny: MetricsHelper.Metric
    private val bucerForAvdodGjenny: MetricsHelper.Metric
    private val bucerForBrukerGjenny: MetricsHelper.Metric
    private val bucViewGjenny: MetricsHelper.Metric
    private val addInstutionAndDocument: MetricsHelper.Metric
    private val addDocumentToParent: MetricsHelper.Metric

    init {
        bucViewGjenny = metricsHelper.init("BucView", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucerForGjenny = metricsHelper.init("bucerForGjenny", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucerForAvdodGjenny = metricsHelper.init("bucerForAvdodGjenny", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        bucerForBrukerGjenny = metricsHelper.init("bucerForBrukerGjenny", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        addInstutionAndDocument = metricsHelper.init("AddInstutionAndDocumentGjenny", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
        addDocumentToParent = metricsHelper.init("AddDocumentToParentGjenny", ignoreHttpCodes = listOf(HttpStatus.BAD_REQUEST))
    }

    @GetMapping("/bucs", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getBucs(): FrontEndResponse<List<String>> = FrontEndResponse(ValidBucAndSed.pensjonsBucerForGjenny(), HttpStatus.OK.name)

    @PostMapping("/buc/{buctype}")
    fun createBuc(
        @PathVariable("buctype", required = true) buctype: String,
        @RequestBody(required = true) gjennySak: GjennySak
    ): FrontEndResponse<BucAndSedView> {
        auditlogger.log("createBuc")
        logger.info("Prøver å opprette en ny BUC $buctype i RINA med GjennySakId: ${gjennySak.sakId} med saktype: ${gjennySak.sakType}.")

        val euxCaseId = euxPrefillService.createdBucForType(buctype)
        logger.info("Mottatt følgende euxCaseId(RinaID): $euxCaseId")

        val buc = euxInnhentingService.getBuc(euxCaseId)
        logger.info("Følgende bucdetalj er hentet: ${buc.processDefinitionName}, id: ${buc.id}")

        return FrontEndResponse(BucAndSedView.from(buc), HttpStatus.OK.name).also {
            it.result?.caseId?.let { euxCaseId -> gcpStorageService.lagreGjennySak(euxCaseId, GjennySak(gjennySak.sakId, gjennySak.sakType)) }
        }
    }

    /**
    *
     **/
    @GetMapping("/rinasaker/brukersakergjenny")
    fun getGjenlevendeRinasakerUtenAvdodGjenny(
        @RequestBody(required = true) aktoerId: String
    ): FrontEndResponse<List<BucView>> {
        return bucerForBrukerGjenny.measure {
        logger.info("henter rinasaker for bruker")
        val fnrForAktoerId = innhentingService.hentFnrEllerNpidForAktoerIdfraPDL(aktoerId)

        val totaleBrukerSaker = sakerFraRinaOgJoark(fnrForAktoerId?.id, aktoerId)
        logger.info("Antall totale brukersaker: ${totaleBrukerSaker.size}")

        return@measure FrontEndResponse(totaleBrukerSaker
            .filter { BucType.from(it.processDefinitionId) !in listOf(P_BUC_01, P_BUC_03) }
            .map { rinasak ->
                BucView(rinasak.id!!, BucType.from(rinasak.processDefinitionId),
                    aktoerId, null, null, BucViewKilde.BRUKER
                )
            }, HttpStatus.OK.name)
        }
    }

    @GetMapping("/rinasaker/{aktoerId}/avdodfnr/{avdodfnr}")
    fun getGjenlevendeRinasakerAvdodGjenny(
        @PathVariable("aktoerId", required = true) aktoerId: String,
        @PathVariable("avdodfnr", required = true) avdodfnr: String,
    ): FrontEndResponse<List<BucView>> {
        logger.info("henter rinasaker for gjenlevende med aktoerid: $aktoerId")

        val gjenlevendeFnr = innhentingService.hentFnrfraAktoerService(aktoerId)
        val avdodAktoerId = personService.hentPerson(NorskIdent(avdodfnr))?.identer?.firstOrNull { it.gruppe == IdentGruppe.AKTORID }?.ident

        //Saker for gjenlevende eux og joark
        val totaleSakerGjenlevende = sakerFraRinaOgJoark(gjenlevendeFnr?.id, aktoerId)

        //Saker for avdød eux og joark
        val avdodSakerFraRinaOgJoark = sakerFraRinaOgJoark(avdodfnr,avdodAktoerId)

        return FrontEndResponse(totaleSakerGjenlevende.filter{ rinasak -> rinasak.id in avdodSakerFraRinaOgJoark.map { it.id }  }
        .filter { BucType.from(it.processDefinitionId) !in listOf(P_BUC_01,P_BUC_03)}
            .map { rinasak ->
                BucView(rinasak.id!!, BucType.from(rinasak.processDefinitionId), aktoerId, null, avdodfnr, BucViewKilde.SAF)
            }, HttpStatus.OK.name)
    }

    fun sakerFraRinaOgJoark(fnr: String?, aktoerId: String?) : List<Rinasak> {
        val rinsakerForFnr = fnr?.let { euxInnhentingService.hentRinasaker(fnr)} ?: emptyList()
        val gjenlevendeRinaSakIderFraJoark = aktoerId?.let { innhentingService.hentRinaSakIderFraJoarksMetadataForOmstilling(aktoerId)} ?: emptyList()
        return rinsakerForFnr.filter { it.id in gjenlevendeRinaSakIderFraJoark }.distinct()
    }

    @GetMapping("/rinasaker/{aktoerId}")
    fun getRinasakerBrukerkontekstGjenny(
        @PathVariable("aktoerId", required = true) aktoerId: String
    ): FrontEndResponse<List<BucView>> {
        return bucViewGjenny.measure {
            val start = System.currentTimeMillis()
            val timeTracking = mutableListOf<String>()

            //api: henter fnr fra aktørid
            val gjenlevendeFnr = innhentingService.hentFnrfraAktoerService(aktoerId)

            //api: henter rinasaker basert på tidligere journalførte saker fra Joark
            val rinaSakIderFraJoark = innhentingService.hentRinaSakIderFraJoarksMetadata(aktoerId)
            .also { timeTracking.add("rinaSakIderFraJoark:${it}, tid: ${System.currentTimeMillis()-start} i ms") }

            //api: bruker saker fra eux/rina
            val brukerView = gjenlevendeFnr?.let { euxInnhentingService.hentBucViewBruker(it.id, aktoerId, null) }.also {
                timeTracking.add("hentBucViewBruker, gjenlevendeFnr tid: ${System.currentTimeMillis()-start} i ms")
            }?: emptyList()

            //filter: brukersaker fra saf
            val filterBrukerRinaSakIderFraJoark = rinaSakIderFraJoark.filterNot { rinaid -> rinaid in brukerView.map { it.euxCaseId }  }

            //api: saker fra saf og eux/rina
            val safView = euxInnhentingService.lagBucViews(
                aktoerId,
                null,
                filterBrukerRinaSakIderFraJoark,
                EuxInnhentingService.BucViewKilde.SAF
            ).also {timeTracking.add("hentBucViews tid: ${System.currentTimeMillis()-start} i ms")}

            val view = (brukerView + safView)
                .filter { it.buctype !in listOf(P_BUC_01, P_BUC_03) }
                .also { logger.info("Antall for brukerview+safView: ${it.size}") }

            return@measure FrontEndResponse(view.sortedByDescending { it.avdodFnr }.distinctBy { it.euxCaseId }
                .also {
                    logger.info("Tidsbruk for getRinasakerBrukerkontekst: \n"+timeTracking.joinToString("\n").trimIndent())
                }, HttpStatus.OK.name)
        }
    }

    @PostMapping("/sed/add")
    fun leggTilInstitusjon(@RequestBody request: ApiRequest): FrontEndResponse<DocumentsItem?> {
        logger.info("Legg til institusjon fra gjenny for ${request.sed}, rinaid: ${request.euxCaseId}, sedid: ${request.documentid}")
        logger.info("Avdød fnr finnes i requesten: ${request.subject?.avdod?.fnr != null}, Subject finnes: ${request.subject != null}, gjenlevende finnes: ${request.subject?.gjenlevende != null}")
        logger.info("Legger til institusjoner og SED for " +
                "rinaId: ${request.euxCaseId} " +
                "bucType: ${request.buc} " +
                "sedType: ${request.sed} " +
                "aktoerId: ${request.aktoerId?.take(5)} " +
                "sakId: ${request.sakId} " +
                "vedtak: ${request.vedtakId} " +
                "institusjoner: ${request.institutions} " +
                "gjenny: true"
        )

        if (request.buc == null) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Mangler Buc")

        val norskIdent = innhentingService.hentFnrfraAktoerService(request.aktoerId) ?: throw HttpClientErrorException(HttpStatus.BAD_REQUEST)
        val avdodaktoerID = innhentingService.getAvdodId(BucType.from(request.buc.name)!!, request.riktigAvdod(), true)
        val dataModel = ApiRequest.buildPrefillDataModelOnExisting(request, PersonInfo(norskIdent.id, request.aktoerId), avdodaktoerID)

        val bucUtil = euxInnhentingService.kanSedOpprettes(dataModel)

        if (bucUtil.getProcessDefinitionName() != request.buc.name) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Rina Buctype og request buctype må være samme")
        }

        request.euxCaseId?.let { gcpStorageService.lagreGjennySak(request.euxCaseId, GjennySak(request.sakId!!, request.sakType!!)) }

        logger.debug("bucUtil BucType: ${bucUtil.getBuc().processDefinitionName} apiRequest Buc: ${request.buc}")

        addInstitution(request.copy(gjenny = true), dataModel, bucUtil)

        val requestMedGjenlevendeFnr = request.copy(fnr = norskIdent.id, gjenny = true)
        logger.debug("***Request med gjenlevende fnr: ${requestMedGjenlevendeFnr.toJson()} ***")
        val sed = innhentingService.hentPreutyltSed(
            euxInnhentingService.checkForP7000AndAddP6000(requestMedGjenlevendeFnr),
            bucUtil.getProcessDefinitionVersion()
        ).also { secureLog.info("Prefill av SED: $it") }

        return addInstutionAndDocument.measure {
            logger.info("******* Legge til ny SED - start *******")

            val sedType = SedType.from(request.sed?.name!!)!!
            logger.info("Prøver å sende SED: $sedType inn på BUC: ${dataModel.euxCaseID}")

            val bucAndSedResponse = euxPrefillService.opprettJsonSedOnBuc(sed, sedType, dataModel.euxCaseID, request.vedtakId)

            logger.info("Opprettet ny SED med dokumentId: ${bucAndSedResponse.documentId}")
            val sedDocument = bucUtil.findDocument(bucAndSedResponse.documentId)
            sedDocument?.message = dataModel.melding

            logger.info("Har documentItem ${sedDocument?.id}")

            val documentItem = getBucForPBuc06AndForEmptySed(dataModel.buc, bucUtil.getBuc().documents, bucAndSedResponse, sedDocument)
            logger.info("******* Legge til ny SED - slutt *******")
            FrontEndResponse(documentItem)
        }
    }

    @PostMapping("/sed/replysed/{parentid}")
    fun prefillSed(
        @RequestBody(required = true) request: ApiRequest,
        @PathVariable("parentid", required = true) parentId: String
    ): FrontEndResponse<DocumentsItem?> {
        logger.info("Prefil fra gjenny for ${request.sed}, rinaid: ${request.euxCaseId}, sedid: ${request.documentid}")

        if (request.buc == null) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Mangler Buc")

        val norskIdent = innhentingService.hentFnrfraAktoerService(request.aktoerId)
            ?: throw HttpClientErrorException(HttpStatus.BAD_REQUEST, "Mangler norsk fnr")
        val avdodaktoerID = innhentingService.getAvdodId(BucType.from(request.buc.name)!!, request.riktigAvdod(), false)
        val dataModel = ApiRequest.buildPrefillDataModelOnExisting(request, PersonInfo(norskIdent.id, request.aktoerId), avdodaktoerID)

        val bucUtil = BucUtils(euxInnhentingService.getBuc(dataModel.euxCaseID)).also { bucUtil ->
            logger.info("******* Hent BUC sjekk om svarSed kan opprettes *******")
            bucUtil.isChildDocumentByParentIdBeCreated(parentId, dataModel.sedType)
        }

        logger.info("Prøver å prefillSED (svarSED) parentId: $parentId")
        val sed = innhentingService.hentPreutyltSed(
            euxInnhentingService.checkForX010AndAddX009(request.copy(gjenny = true), parentId),
            bucUtil.getProcessDefinitionVersion()
        )

        return addDocumentToParent.measure {
            logger.info("Prøver å sende SED: ${dataModel.sedType} inn på BUC: ${dataModel.euxCaseID}")

            val docresult = euxPrefillService.opprettSvarJsonSedOnBuc(
                sed,
                dataModel.euxCaseID,
                parentId,
                request.vedtakId,
                dataModel.sedType
            )

            gcpStorageService.lagreGjennySak(request.euxCaseId!!, GjennySak(request.sakId!!, request.sakType!!))

            val parent = bucUtil.findDocument(parentId)
            val result = bucUtil.findDocument(docresult.documentId)

            val documentItem = getBucForPBuc06AndForEmptySed(dataModel.buc, bucUtil.getBuc().documents, docresult, result)

            logger.info("Buc: (${dataModel.euxCaseID}, hovedSED type: ${parent?.type}, docId: ${parent?.id}, svarSED type: ${documentItem?.type} docID: ${documentItem?.id}")
            logger.info("******* Legge til svarSED - slutt *******")
            FrontEndResponse(documentItem, HttpStatus.OK.name)
        }
    }

    @PutMapping("/sed/document/{euxcaseid}/{documentid}")
    fun oppdaterSed(
        @PathVariable("euxcaseid", required = true) euxcaseid: String,
        @PathVariable("documentid", required = true) documentid: String,
        @RequestBody sedPayload: String
    ): FrontEndResponse<Boolean> {
        val validsed = try {
            secureLog.info("Følgende SED payload: $sedPayload")
            val sed = SED.fromJsonToConcrete(sedPayload).also { secureLog.info("Følgende SED: ${it.toJson()}") }
            logger.info("Følgende SED prøves å oppdateres: ${sed.type}, rinaid: $euxcaseid")

            when (sed) {
                is P8000 -> {
                    val sedP8000Frontend = mapJsonToAny<P8000Frontend>(sedPayload)
                    sedP8000Frontend.options?.also {
                        val jsonDecoded = java.net.URLDecoder.decode(it, "UTF-8")
                        logger.info("Lagrer options for: ${sed.type}, rinaid: $euxcaseid, options: $jsonDecoded")
                        gcpStorageService.lagreP8000Options(documentid, jsonDecoded)
                    }
                    sed
                }
                is P5000 -> sed.updateFromUI()
                else -> sed
            }
        } catch (ex: Exception) {
            logger.error("Feil ved oppdatering av SED", ex)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Lagring av SED feilet, ugyldig SED: ${ex.message}", ex)
        }
        val jsonToRina = validsed.toJsonSkipEmpty().also {
            logger.debug("Følgende SED prøves å oppdateres til RINA: rinaID: $euxcaseid, documentid: $documentid")
        }
        return FrontEndResponse(euxInnhentingService.updateSedOnBuc(euxcaseid, documentid, jsonToRina).also {
            logger.info("Oppdatering av SED: $it")
        }, HttpStatus.OK.name)
    }

    // P5000 extension function - punkt 5.2.1.3.1 settes til "0" når gyldigperiode == "0"
    private fun P5000.updateFromUI(): P5000 {
        val pensjon = this.pensjon
        val medlemskapboarbeid = pensjon?.medlemskapboarbeid
        val gyldigperiode = medlemskapboarbeid?.gyldigperiode
        val erTom = medlemskapboarbeid?.medlemskap.let { it == null || it.isEmpty() }
        if (gyldigperiode == "0" && erTom) {
            logger.info("P5000 setter 5.2.1.3.1 til 0 ")
            return this.copy(pensjon = P5000Pensjon(
                trygdetid = listOf(MedlemskapItem(sum = TotalSum(aar = "0"))),
                medlemskapboarbeid = medlemskapboarbeid,
                separatP5000sendes = "0"
            ))
        }
        return this
    }

    private fun addInstitution(request: ApiRequest, dataModel: PrefillDataModel, bucUtil: BucUtils) {
        logger.info("*** Sjekker og legger til Instiusjoner på BUC eller X005 ***")
        val nyeInstitusjoner = bucUtil.findNewParticipants(dataModel.getInstitutionsList())
        val x005docs = bucUtil.findX005DocumentByTypeAndStatus()

        if (nyeInstitusjoner.isNotEmpty()) {
            logger.info("""
                eksiterendeInstiusjoner: ${bucUtil.getParticipantsAsInstitusjonItem().toJson()}
                nyeInstitusjoner: ${nyeInstitusjoner.toJson()}
            """.trimIndent())

            if (x005docs.isEmpty()) {
                euxPrefillService.checkAndAddInstitution(dataModel, bucUtil, emptyList(), nyeInstitusjoner)
            } else if (x005docs.firstOrNull { it.status == "empty" } != null) {
                val x005Liste = nyeInstitusjoner.map { nyeInstitusjonerMap ->
                    logger.debug("Prefiller X005, legger til Institusjon på X005 ${nyeInstitusjonerMap.institution}")
                    val x005request = request.copy(avdodfnr = null, sed = SedType.X005, institutions = listOf(nyeInstitusjonerMap))
                    mapJsonToAny<X005>(innhentingService.hentPreutyltSed(
                        x005request,
                        bucUtil.getProcessDefinitionVersion()
                    ))
                }
                euxPrefillService.checkAndAddInstitution(dataModel, bucUtil, x005Liste, nyeInstitusjoner)
            } else if (!bucUtil.isValidSedtypeOperation(SedType.X005, ActionOperation.Create)) { /* nada */ }
        }
    }

    private fun getBucForPBuc06AndForEmptySed(
        bucType: BucType,
        bucDocuments: List<DocumentsItem>?,
        bucSedResponse: BucSedResponse,
        orginal: DocumentsItem?
    ): DocumentsItem? {
        logger.info("Henter BUC på nytt for buctype: $bucType, inkl. ${bucDocuments?.size} SED")
        Thread.sleep(900)
        return if (bucType == P_BUC_06 || orginal == null && bucDocuments.isNullOrEmpty()) {
            val innhentetBuc = euxInnhentingService.getBuc(bucSedResponse.caseId)
            BucUtils(innhentetBuc).findDocument(bucSedResponse.documentId)
        } else {
            orginal
        }
    }

    private fun loggTimeAndViewSize(servicename: String, start: Long, viewsize: Long = 0) {
        logger.info("""
                Total view size is $viewsize
                $servicename -> total tid: ${System.currentTimeMillis() - start} in ms
                """.trimIndent()
        )
    }
}