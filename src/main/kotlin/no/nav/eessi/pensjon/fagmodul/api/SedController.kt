package no.nav.eessi.pensjon.fagmodul.api

import io.swagger.annotations.ApiOperation
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.fagmodul.eux.BucUtils
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.security.token.support.core.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Protected
@RestController
@RequestMapping("/sed")
class SedController(
    private val euxInnhentingService: EuxInnhentingService,
    private val auditlogger: AuditLogger,
) {

    private val logger = LoggerFactory.getLogger(SedController::class.java)

    @ApiOperation("Henter ut en SED fra et eksisterende Rina document. krever unik dokumentid fra valgt SED, ny api kall til eux")
    @GetMapping("/get/{euxcaseid}/{documentid}")
    fun getDocument(
        @PathVariable("euxcaseid", required = true) euxcaseid: String,
        @PathVariable("documentid", required = true) documentid: String
    ): String {
        auditlogger.logBuc("getDocument", " euxCaseId: $euxcaseid documentId: $documentid")
        logger.info("Hente SED innhold for /${euxcaseid}/${documentid} ")
        val sed = euxInnhentingService.getSedOnBucByDocumentId(euxcaseid, documentid)
        return mapToConcreteSedJson(sed)
    }

    @ApiOperation("Henter ut en liste over registrerte institusjoner innenfor spesifiserte EU-land. ny api kall til eux")
    @GetMapping("/institutions/{buctype}", "/institutions/{buctype}/{countrycode}")
    fun getEuxInstitusjoner(
        @PathVariable("buctype", required = true) buctype: String,
        @PathVariable("countrycode", required = false) landkode: String? = ""
    ): List<InstitusjonItem> {
        logger.info("Henter ut liste over alle Institusjoner i Rina")
        return euxInnhentingService.getInstitutions(buctype, landkode)
    }

    @ApiOperation("henter liste over seds som kan opprettes til valgt rinasak")
    @GetMapping("/seds/{buctype}/{rinanr}")
    fun getSeds(
        @PathVariable(value = "buctype", required = true) bucType: String,
        @PathVariable(value = "rinanr", required = true) euxCaseId: String
    ): ResponseEntity<String?> {
        val resultListe = BucUtils(euxInnhentingService.getBuc(euxCaseId)).getFiltrerteGyldigSedAksjonListAsString()
        logger.info("Henter lite over SED som kan opprettes på buctype: $bucType seds: $resultListe")
        return ResponseEntity.ok().body(resultListe.toJsonSkipEmpty())
    }

    private fun mapToConcreteSedJson(sedJson: SED): String {
        return when (sedJson.type) {
            SedType.P4000 -> (sedJson as P4000).toJson()
            SedType.P5000 -> (sedJson as P5000).toJson()
            SedType.P6000 -> (sedJson as P6000).toJson()
            SedType.P7000 -> (sedJson as P7000).toJson()
            SedType.P8000 -> (sedJson as P8000).toJson()
            else -> sedJson.toJson()
        }
    }


}
