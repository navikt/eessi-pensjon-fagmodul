package no.nav.eessi.pensjon.fagmodul.api

import io.swagger.v3.oas.annotations.Operation
import no.nav.eessi.pensjon.eux.model.document.P6000Dokument
import no.nav.eessi.pensjon.eux.model.sed.MedlemskapItem
import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.eux.model.sed.P5000Pensjon
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.TotalSum
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.PreviewPdf
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.Kodeverk
import no.nav.eessi.pensjon.fagmodul.models.KodeverkResponse
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@Protected
@RestController
@RequestMapping("/sed")
class SedController(
    private val euxInnhentingService: EuxInnhentingService,
    private val auditlogger: AuditLogger,
    @Value("\${eessipen-eux-rina.url}")
    private val euxrinaurl: String
) {

    private val logger = LoggerFactory.getLogger(SedController::class.java)

    @Operation(description = "Henter liste over P6000 som kan ingå i preutfyll for P7000")
    @GetMapping("/getP6000/{euxcaseid}")
    fun getDocumentP6000list(@PathVariable("euxcaseid", required = true) euxcaseid: String): List<P6000Dokument>? {
        val bucUtils = BucUtils(euxInnhentingService.getBuc(euxcaseid))
        return bucUtils.getAllP6000AsDocumentItem(euxrinaurl)

    }

    @Deprecated("Benytt getPdfFromRina", ReplaceWith("getPdfFromRina"))
    @Operation(description= "Hent pdf fra Rina")
    @GetMapping("/get/P6000pdf/{euxcaseid}/{documentid}")
    fun getPdfP6000FromRina(
        @PathVariable("euxcaseid", required = true) euxcaseid: String,
        @PathVariable("documentid", required = true) documentid: String): PreviewPdf {
        return euxInnhentingService.getPdfContents(euxcaseid, documentid)
    }

    @Operation(description =  "Hent pdf fra Rina")
    @GetMapping("/get/{euxcaseid}/{documentid}/pdf")
    fun getPdfFromRina(
        @PathVariable("euxcaseid", required = true) euxcaseid: String,
        @PathVariable("documentid", required = true) documentid: String): PreviewPdf {
        return euxInnhentingService.getPdfContents(euxcaseid, documentid)
    }

    @Operation(description = "Henter ut en SED fra et eksisterende Rina document. krever unik dokumentid fra valgt SED, ny api kall til eux")
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

    @Operation(description = "Oppdaterer en SED i RINA med denne versjon av JSON. krever dokumentid, euxcaseid samt json")
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

    @Deprecated("Benytt tjenesten i EuxController", ReplaceWith("EuxController"))
    @Operation(description = "Henter ut en liste over landkoder ut fra kodeverktjenesten eux")
    @GetMapping( "/landkoder")
    fun getCountryCode(): List<String> {
        return euxInnhentingService.getKodeverk(Kodeverk.LANDKODER).mapNotNull{ it.kode }.toList()
    }

    @Deprecated("Benytt tjenesten i EuxController", ReplaceWith("EuxController"))
    @Operation(description = "Henter ut en liste over kodeverk fra eux")
    @GetMapping( "/kodeverk/{kodeverk}")
    fun getKodeverk(@PathVariable("kodeverk", required = true) kodeverk: Kodeverk ): List<KodeverkResponse> {
        return euxInnhentingService.getKodeverk(kodeverk)
    }

    @Deprecated("Benytt tjenesten i EuxController", ReplaceWith("EuxController"))
    @Operation(description = "Henter ut en liste over registrerte institusjoner innenfor spesifiserte EU-land. ny api kall til eux")
    @GetMapping("/institutions/{buctype}", "/institutions/{buctype}/{countrycode}")
    fun getEuxInstitusjoner(
        @PathVariable("buctype", required = true) buctype: String,
        @PathVariable("countrycode", required = false) landkode: String? = ""
    ): List<InstitusjonItem> {
        logger.info("Henter ut liste over alle Institusjoner i Rina")
        return euxInnhentingService.getInstitutions(buctype, landkode)
    }

    @Operation(description = "henter liste over seds som kan opprettes til valgt rinasak")
    @GetMapping("/seds/{buctype}/{rinanr}")
    fun getSeds(
        @PathVariable(value = "buctype", required = true) bucType: String,
        @PathVariable(value = "rinanr", required = true) euxCaseId: String
    ): ResponseEntity<String?> {
        val resultListe = BucUtils(euxInnhentingService.getBuc(euxCaseId)).getFiltrerteGyldigSedAksjonListAsString()
        logger.info("Henter lite over SED som kan opprettes på buctype: $bucType seds: $resultListe")
        return ResponseEntity.ok().body(resultListe.toJsonSkipEmpty())
    }

    //Ektend P5000 updateFromUI før den sendes og oppdateres i Rina.
    //punkt 5.2.1.3.1 i settes til "0" når gyldigperiode == "0"
    fun P5000.updateFromUI(): P5000 {
        val pensjon = this.p5000Pensjon
        val medlemskapboarbeid = pensjon?.medlemskapboarbeid
        val gyldigperiode = medlemskapboarbeid?.gyldigperiode
        val erTom = medlemskapboarbeid?.medlemskap.let { it == null || it.isEmpty() }
        if (gyldigperiode == "0" && erTom) {
           logger.info("P5000 setter 5.2.1.3.1 til 0 ")
            return this.copy(p5000Pensjon = P5000Pensjon(
                trygdetid = listOf(MedlemskapItem(sum = TotalSum(aar = "0"))),
                medlemskapboarbeid = medlemskapboarbeid,
                separatP5000sendes = "0"
            ))
        }
        return this
    }

}
