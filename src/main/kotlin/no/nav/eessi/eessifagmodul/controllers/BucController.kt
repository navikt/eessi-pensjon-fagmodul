package no.nav.eessi.eessifagmodul.controllers

import io.swagger.annotations.ApiOperation
import no.nav.eessi.eessifagmodul.services.aktoerregister.AktoerregisterService
import no.nav.eessi.eessifagmodul.services.eux.BucUtils
import no.nav.eessi.eessifagmodul.services.eux.EuxService
import no.nav.eessi.eessifagmodul.services.eux.RinaAksjon
import no.nav.eessi.eessifagmodul.services.eux.Rinasak
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.Buc
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.BucAndSedView
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.Creator
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.ShortDocumentItem
import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import no.nav.security.oidc.api.Protected
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@Protected
@RestController
@RequestMapping("/buc")
class BucController(private val euxService: EuxService,
                    aktoerregisterService: AktoerregisterService) : AktoerIdHelper(aktoerregisterService) {

    private val logger = LoggerFactory.getLogger(BucController::class.java)


    @ApiOperation("Henter opp hele BUC på valgt caseid")
    @GetMapping("/{rinanr}")
    fun getBuc(@PathVariable(value = "rinanr", required = true) rinanr: String): Buc {
        return euxService.getBuc(rinanr)
    }

    @ApiOperation("Henter opp hele BUC på valgt caseid")
    @GetMapping("/{rinanr}/name")
    fun getProcessDefinitionName(@PathVariable(value = "rinanr", required = true) rinanr: String): String? {

        logger.debug("Henter ut definisjonsnavn (buc type) på valgt Buc")

        return getBucUtils(rinanr).getProcessDefinitionName()
    }

    @ApiOperation("Henter opp den opprinelige inststusjon på valgt caseid (buc)")
    @GetMapping("/{rinanr}/creator")
    fun getCreator(@PathVariable(value = "rinanr", required = true) rinanr: String): Creator? {

        logger.debug("Henter ut Creator på valgt Buc")

        return getBucUtils(rinanr).getCreator()
    }

    @ApiOperation("Henter opp den opprinelige inststusjon landkode på valgt caseid (buc)")
    @GetMapping("/{rinanr}/creator/countryCode")
    fun getCreatorCountryCode(@PathVariable(value = "rinanr", required = true) rinanr: String): String? {

        logger.debug("Henter ut CountryCode på Creator på valgt Buc")

        return mapAnyToJson(getBucUtils(rinanr).getCreatorContryCode())
    }

    @ApiOperation("Henter opp internationalid på caseid (buc)")
    @GetMapping("/{rinanr}/internationalId")
    fun getInternationalId(@PathVariable(value = "rinanr", required = true) rinanr: String): String? {

        logger.debug("Henter ut InternationalId på valgt Buc")

        return getBucUtils(rinanr).getInternatinalId()
    }

    @ApiOperation("Henter opp den opprinelige inststusjon på valgt caseid (buc)")
    @GetMapping("/{rinanr}/allDocuments")
    fun getAllDocuments(@PathVariable(value = "rinanr", required = true) rinanr: String): List<ShortDocumentItem> {

        logger.debug("Henter ut documentId på alle dokumenter som finnes på valgt buc")

        return getBucUtils(rinanr).getAllDocuments()
    }

    @ApiOperation("Henter opp mulige aksjon som kan utføres på valgt buc, filtert på sed starter med 'P'")
    @GetMapping("/{rinanr}/aksjoner", "/{rinanr}/aksjoner/{filter}")
    fun getMuligeAksjoner(@PathVariable(value = "rinanr", required = true) rinanr: String,
                          @PathVariable(value = "filter", required = false) filter: String? = null): List<RinaAksjon> {

        logger.debug("Henter ut muligeaksjoner på valgt buc")

        val list = getBucUtils(rinanr).getRinaAksjon()
        if (filter == null) {
            return list
        }
        return getMuligeAksjonerFilter(list, filter)
    }

    @ApiOperation("Henter ut en liste over saker på valgt aktoerid. ny api kall til eux")
    @GetMapping("/rinasaker/{aktoerId}")
    fun getRinasaker(@PathVariable("aktoerId", required = true) aktoerId: String): List<Rinasak> {

        logger.debug("henter rinasaker på valgt aktoerid: $aktoerId")
        val fnr = hentAktoerIdPin(aktoerId)
        return euxService.getRinasaker(fnr)
    }

    //ny view call for bucogsed design pr 01.04-01.05)
    @ApiOperation("Henter ut en json struktur for buc og sed menyliste for ui. ny api kall til eux")
    @GetMapping("/detaljer/{aktoerid}", "/detaljer/{aktoerid}/{sakid}", "/detaljer/{aktoerId}/{sakId}/{euxcaseid}")
    fun getBucogSedView(@PathVariable("aktoerid", required = true) aktoerid: String,
                        @PathVariable("sakid", required = false) sakid: String? = "",
                        @PathVariable("euxcaseid", required = false) euxcaseid: String? = ""): List<BucAndSedView> {

        logger.debug("1 prøver å dekode til fnr fra aktoerid: $aktoerid")

        val fnr = hentAktoerIdPin(aktoerid)
        return euxService.getBucAndSedView(fnr, aktoerid, sakid, euxcaseid, euxService)

    }



    private fun getMuligeAksjonerFilter(list: List<RinaAksjon>, filter: String = ""): List<RinaAksjon> {
        return list.filter { it.dokumentType?.startsWith(filter)!! }.toList()
    }

    private fun getBucUtils(rinanr: String): BucUtils {
        return euxService.getBucUtils(rinanr)
    }

}
