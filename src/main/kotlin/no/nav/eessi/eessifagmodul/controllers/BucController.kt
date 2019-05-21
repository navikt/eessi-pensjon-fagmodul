package no.nav.eessi.eessifagmodul.controllers

import io.swagger.annotations.ApiOperation
import no.nav.eessi.eessifagmodul.services.eux.BucUtils
import no.nav.eessi.eessifagmodul.services.eux.EuxService
import no.nav.eessi.eessifagmodul.services.eux.RinaAksjon
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.Buc
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.Creator
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.ShortDocumentItem
import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import no.nav.security.oidc.api.Protected
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Protected
@RestController
@RequestMapping("/buc")
class BucController(private val euxService: EuxService) {

    @ApiOperation("Henter opp hele BUC på valgt caseid")
    @GetMapping("/{rinanr}")
    fun getBuc(@PathVariable(value = "rinanr", required = true) rinanr: String): Buc {
        return euxService.getBuc(rinanr)
    }

    @ApiOperation("Henter opp hele BUC på valgt caseid")
    @GetMapping("/{rinanr}/name")
    fun getProcessDefinitionName(@PathVariable(value = "rinanr", required = true) rinanr: String): String? {
        return getBucUtils(rinanr).getProcessDefinitionName()
    }

    @ApiOperation("Henter opp den opprinelige inststusjon på valgt caseid (buc)")
    @GetMapping("/{rinanr}/creator")
    fun getCreator(@PathVariable(value = "rinanr", required = true) rinanr: String): Creator? {
        return getBucUtils(rinanr).getCreator()
    }

    @ApiOperation("Henter opp den opprinelige inststusjon landkode på valgt caseid (buc)")
    @GetMapping("/{rinanr}/creator/countryCode")
    fun getCreatorCountryCode(@PathVariable(value = "rinanr", required = true) rinanr: String): String? {
        return mapAnyToJson(getBucUtils(rinanr).getCreatorContryCode())
    }

    @ApiOperation("Henter opp internationalid på caseid (buc)")
    @GetMapping("/{rinanr}/internationalId")
    fun getInternationalId(@PathVariable(value = "rinanr", required = true) rinanr: String): String? {
        return getBucUtils(rinanr).getInternatinalId()
    }

    @ApiOperation("Henter opp den opprinelige inststusjon på valgt caseid (buc)")
    @GetMapping("/{rinanr}/allDocuments")
    fun getAllDocuments(@PathVariable(value = "rinanr", required = true) rinanr: String): List<ShortDocumentItem> {
        return getBucUtils(rinanr).getAllDocuments()
    }

    @ApiOperation("Henter opp mulige aksjon som kan utføres på valgt buc, filtert på sed starter med 'P'")
    @GetMapping("/{rinanr}/aksjoner", "/{rinanr}/aksjoner/{filter}")
    fun getMuligeAksjoner(@PathVariable(value = "rinanr", required = true) rinanr: String,
                          @PathVariable(value = "filter", required = false) filter: String? = null): List<RinaAksjon> {

        val list = getBucUtils(rinanr).getRinaAksjon()
        if (filter == null) {
            return list
        }
        return getMuligeAksjonerFilter(list, filter)
    }

    private fun getMuligeAksjonerFilter(list: List<RinaAksjon>, filter: String = ""): List<RinaAksjon> {
        return list.filter { it.dokumentType?.startsWith(filter)!! }.toList()
    }

    private fun getBucUtils(rinanr: String): BucUtils {
        return euxService.getBucUtils(rinanr)
    }

}
