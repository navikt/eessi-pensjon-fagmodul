package no.nav.eessi.pensjon.fagmodul.api

import no.nav.eessi.pensjon.eux.model.buc.PreviewPdf
import no.nav.eessi.pensjon.eux.model.document.P6000Dokument
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@Protected
@RestController
@RequestMapping("/sed")
class SedController(
    private val euxInnhentingService: EuxInnhentingService,
    private val auditlogger: AuditLogger,
    @Value("\${eessipen-eux-rina.url}")
    private val euxrinaurl: String,
    private val gcpStorageService: GcpStorageService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {

    private val logger = LoggerFactory.getLogger(SedController::class.java)

    private lateinit var pdfGenerert: MetricsHelper.Metric
    private lateinit var sedsendt: MetricsHelper.Metric

    init {
        pdfGenerert = metricsHelper.init("PdfGenerert")
    }

    @GetMapping("/getP6000/{euxcaseid}")
    fun getDocumentP6000list(@PathVariable("euxcaseid", required = true) euxcaseid: String): List<P6000Dokument>? {
        val bucUtils = BucUtils(euxInnhentingService.getBuc(euxcaseid))
        return bucUtils.getAllP6000AsDocumentItem(euxrinaurl)

    }

    @GetMapping("/get/{euxcaseid}/{documentid}/pdf")
    fun getPdfFromRina(
        @PathVariable("euxcaseid", required = true) euxcaseid: String,
        @PathVariable("documentid", required = true) documentid: String): PreviewPdf {
        return euxInnhentingService.getPdfContents(euxcaseid, documentid)
    }

    @GetMapping("/get/{euxcaseid}/{documentid}")
    fun getDocument(
        @PathVariable("euxcaseid", required = true) euxcaseid: String,
        @PathVariable("documentid", required = true) documentid: String
    ): String {
        auditlogger.logBuc("getDocument", " euxCaseId: $euxcaseid documentId: $documentid")
        logger.info("Hente SED innhold for /${euxcaseid}/${documentid} ")
        val sed = euxInnhentingService.getSedOnBucByDocumentId(euxcaseid, documentid)

        if(sed is P8000){
            val p8000Frontend = P8000Frontend(sed.type, sed.nav, sed.p8000Pensjon)

            logger.info("Henter options for: ${p8000Frontend.type}, rinaid: $euxcaseid, options: ${p8000Frontend.options}")
            val lagretP8000Options = gcpStorageService.hentP8000(documentid)
            if (lagretP8000Options != null) {
                p8000Frontend.options = mapJsonToAny<Options>(lagretP8000Options)
            }
            return p8000Frontend.toJsonSkipEmpty()
       }

        return sed.toJson()
    }

    @PutMapping("/put/{euxcaseid}/{documentid}")
    fun putDocument(
        @PathVariable("euxcaseid", required = true) euxcaseid: String,
        @PathVariable("documentid", required = true) documentid: String,
        @RequestBody sedPayload: String
    ): Boolean {
        val validsed = try {
            logger.debug("Følgende SED payload: $sedPayload")

            val sed = SED.fromJsonToConcrete(sedPayload)
            logger.info("Følgende SED prøves å oppdateres: ${sed.type}, rinaid: $euxcaseid")

            when (sed) {
                is P8000 -> {
                    val sedP8000Frontend = mapJsonToAny<P8000Frontend>(sedPayload)
                    sedP8000Frontend.options?.let {
                        logger.info("Lagrer options for: ${sed.type}, rinaid: $euxcaseid, options: $it")
                        gcpStorageService.lagreP8000Options(documentid, it.toJsonSkipEmpty())
                    }
                    sed
                }
                is P5000 -> sed.updateFromUI()
                else -> sed
            }
        } catch (ex: Exception) {
            logger.error("Feil ved oppdatering av SED", ex)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Data er ikke gyldig SEDformat")
        }
        logger.debug("Følgende SED prøves å oppdateres til RINA: rinaID: $euxcaseid, documentid: $documentid, validsed: ${validsed.toJsonSkipEmpty()}")
        Thread {
            val result = euxInnhentingService.updateSedOnBuc(euxcaseid, documentid, validsed.toJsonSkipEmpty())
            logger.info("Oppdatering av SED: $result")
        }.start()
        return euxInnhentingService.updateSedOnBuc(euxcaseid, documentid, validsed.toJsonSkipEmpty())
    }

    @GetMapping("/seds/{buctype}/{rinanr}")
    fun getSeds(
        @PathVariable(value = "buctype", required = true) bucType: String,
        @PathVariable(value = "rinanr", required = true) euxCaseId: String
    ): ResponseEntity<String?> {
        val resultListe = BucUtils(euxInnhentingService.getBuc(euxCaseId)).getFiltrerteGyldigSedAksjonListAsString()
        logger.info("Henter liste over SED som kan opprettes på buctype: $bucType seds: $resultListe")
        return ResponseEntity.ok().body(resultListe.toJsonSkipEmpty())
    }

    //Ektend P5000 updateFromUI før den sendes og oppdateres i Rina.
    //punkt 5.2.1.3.1 i settes til "0" når gyldigperiode == "0"
    fun P5000.updateFromUI(): P5000 {
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

    @PostMapping("/pdf")
    fun lagPdf(@RequestBody pdfJson: String): PreviewPdf? {
        logger.info("Lager PDF")
        return euxInnhentingService.lagPdf(pdfJson).also { logger.info("SED for generering av PDF: $it") }
    }
}
