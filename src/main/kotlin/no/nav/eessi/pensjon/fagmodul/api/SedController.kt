package no.nav.eessi.pensjon.fagmodul.api

import no.nav.eessi.pensjon.eux.model.buc.PreviewPdf
import no.nav.eessi.pensjon.eux.model.document.P6000Dokument
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.metrics.MetricsHelper
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
        return sed.toJson()
    }

    @PutMapping("/put/{euxcaseid}/{documentid}")
    fun putDocument(
        @PathVariable("euxcaseid", required = true) euxcaseid: String,
        @PathVariable("documentid", required = true) documentid: String,
        @RequestBody sedPayload: String): Boolean {

        val validsed = try {
            logger.debug("Følgende SED payload: $sedPayload")

            val sed = SED.fromJsonToConcrete(sedPayload)
            logger.info("Følgende SED prøves å oppdateres: ${sed.type}, rinaid: $euxcaseid")

            //hvis P5000.. .
            val validSed = if (sed is P5000)  {
                sed.updateFromUI() //må alltid kjøres. sjekk og oppdatert trydetid. punkt 5.2.1.3.1
            } else {
                sed
            }
            validSed

        } catch (ex: Exception) {
            logger.error("Feil ved oppdatering av SED", ex)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Data er ikke gyldig SEDformat")
        }
        logger.debug("Følgende SED prøves å oppdateres til RINA: ${validsed.toJsonSkipEmpty()}")

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

    @Protected
    @PostMapping("/post/pdf")
    fun lagPdf(@RequestBody pdfJson: String): ResponseEntity<String> {
        return pdfGenerert.measure {
            return@measure try {
                val response = euxInnhentingService.lagPdf(pdfJson)
                if (response) {
                    logger.info("Lager PDF")
                    ResponseEntity.ok().body("Sed er sendt til Rina")
                } else {
                    logger.error("Noe gikk galt under oppretting av PDF")
                    ResponseEntity.badRequest().body("PDF ble IKKE laget")
                }
            } catch (ex: Exception) {
                logger.error("Noe uforutsett skjedde ved generering av Pdf for Sed")
                ResponseEntity.badRequest().body("PDF ble IKKE generert")
            }
        }
    }
}
