package no.nav.eessi.eessifagmodul.controllers

import no.nav.eessi.eessifagmodul.models.IkkeGyldigKallException
import no.nav.eessi.eessifagmodul.models.InstitusjonItem
import no.nav.eessi.eessifagmodul.models.PersonDetail
import no.nav.eessi.eessifagmodul.models.SED
import no.nav.eessi.eessifagmodul.prefill.PrefillDataModel
import no.nav.eessi.eessifagmodul.services.aktoerregister.AktoerregisterService
import no.nav.eessi.eessifagmodul.services.eux.EuxService
import no.nav.eessi.eessifagmodul.services.pensjonsinformasjon.PensjonsinformasjonService
import no.nav.eessi.eessifagmodul.services.personv3.PersonV3Service
import no.nav.eessi.eessifagmodul.utils.NavFodselsnummer
import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.sak.V1Sak
import no.nav.security.oidc.api.Protected
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.*
import java.util.*

@Protected
@CrossOrigin
@RestController
@Profile("test", "local")
@RequestMapping("/api/experiments")
class ExperimentController {

    @Autowired
    private lateinit var pensjonsinformasjonService: PensjonsinformasjonService

    @Autowired
    private lateinit var personV3Service: PersonV3Service

    @Autowired
    private lateinit var euxService: EuxService

    @Autowired
    private lateinit var aktoerregisterService: AktoerregisterService

    @GetMapping("/testPensjonsinformasjon/{vedtaksId}")
    fun testPensjonsinformasjon(@PathVariable("vedtaksId") vedtaksId: String): String {
        val response = pensjonsinformasjonService.hentAltPaaVedtak(vedtaksId)
        return mapAnyToJson(response)
    }

    @GetMapping("/testPensjonsinformasjonkrav/{fnr}")
    fun testPensjonsinformasjonSakFnr(@PathVariable("fnr") fnr: String): Pensjonsinformasjon {
        return pensjonsinformasjonService.hentAltPaaFnr(fnr)
    }

    @GetMapping("/testPensjonsinformasjonkrav/{fnr}/{sakid}")
    fun testPensjonsinformasjonSakFnrOgSak(@PathVariable("fnr") fnr: String, @PathVariable("sakid") sakId: String): V1Sak? {
        return pensjonsinformasjonService.hentAltPaaSak(sakId, pensjonsinformasjonService.hentAltPaaFnr(fnr))
    }

    @GetMapping("/testPensjonPerson/{fnr}/{sakId}")
    fun testPensjonPersonInfo(@PathVariable("fnr") fnrId: String, @PathVariable("sakId") sakId: String): PersonDetail {
        val vSak = pensjonsinformasjonService.hentAltPaaSak(sakId, pensjonsinformasjonService.hentAltPaaFnr(fnrId))
                ?: throw IkkeGyldigKallException("Feiler henting av sak på sakId")

        val sakType = vSak.sakType

        val bucId: String
        bucId = when (sakType) {
            "ALDER" -> "P_BUC_01"
            "GJENLEV" -> "P_BUC_02"
            "UFOREP" -> "P_BUC_03"
            else -> "UKJENT"
        }

        val aktoerId = aktoerregisterService.hentGjeldendeAktorIdForNorskIdent(fnrId)
        val personv3 = personV3Service.hentPerson(fnrId)
        val kjoenn = personv3.person.kjoenn.kjoenn.value
        val personNavn = personv3.person.personnavn.sammensattNavn

        val sivilStand = personv3.person.sivilstand.sivilstand.value
        val personStatus = personv3.person.personstatus.personstatus.value


        val navfnr = NavFodselsnummer(fnrId)


        return PersonDetail(
                sakType = sakType,
                buc = bucId,
                aktoerId = aktoerId,
                fnr = fnrId,
                personNavn = personNavn,
                kjoenn = kjoenn,
                fodselDato = navfnr.getBirthDate(),
                aar16Dato = navfnr.getYearWhen16(),
                alder = navfnr.getAge(),
                sivilStand = sivilStand,
                persomStatus = personStatus,
                euxCaseId = null
        )
    }

    @GetMapping("/testAktoer/{ident}")
    fun testAktoer(@PathVariable("ident") ident: String): String {
        return aktoerregisterService.hentGjeldendeAktorIdForNorskIdent(ident)
    }

    @GetMapping("/testAktoerTilIdent/{ident}")
    fun testAktoerTilIdent(@PathVariable("ident") ident: String): String {
        return aktoerregisterService.hentGjeldendeNorskIdentForAktorId(ident)
    }

    @GetMapping("/testPerson/{ident}")
    fun testPerson(@PathVariable("ident") ident: String): HentPersonResponse {
        return personV3Service.hentPerson(ident)
    }

    @GetMapping("/institusjoner/{buctype}", "/institusjoner/{buctype}/{land}")
    fun getEuxInstitusjoner(@PathVariable("buctype", required = true) buctype: String, @PathVariable("land", required = false) landkode: String? = ""): List<String> {
        return euxService.getInstitutions(buctype, landkode).sorted()
    }

    //TODO remove when done!
    private fun mockSED(request: SedController.ApiRequest): SED {
        val sed: SED?
        when {
            request.payload == null -> throw IkkeGyldigKallException("Mangler PayLoad")
            request.sed == null -> throw IkkeGyldigKallException("Mangler SED")
            else -> {
                val seds = SED.fromJson(request.payload)
                sed = seds
            }
        }
        return sed
    }

    //TODO remove when done!! test and send existing sed json to rina
    @PostMapping("/testingsed")
    fun testingDocument(@RequestBody request: SedController.ApiRequest): String {
        //TODO remove when done!
        if (!checkNotNull(request.mockSED)) {
            throw IkkeGyldigKallException("Ikke MOCK!")
        }
        val korrid = UUID.randomUUID()
        val penSaksnr = request.sakId
        val sedObj = mockSED(request)

        //sjekk opprett sed på ekisternede buc
        return if (request.euxCaseId != null) {
            val data = PrefillDataModel().apply {
                penSaksnummer = penSaksnr
                sed = sedObj
                euxCaseID = request.euxCaseId
            }
            euxService.opprettSedOnBuc(data.sed, data.euxCaseID)
            data.euxCaseID

            //sjekk opprett buc og sed
        } else {
            val bucId = request.buc ?: throw IkkeGyldigKallException("Mangler BUC")
            val institutin = request.institutions ?: throw IkkeGyldigKallException("Mangler pensjonSaksnr")

            val data = PrefillDataModel().apply {
                penSaksnummer = penSaksnr
                buc = bucId
                institution = institutin
                sed = sedObj
            }
            val response = euxService.opprettBucSed(data.sed, data.buc, getFirstInstitution(data.getInstitutionsList()), data.penSaksnummer)
            print(" caseID:  ${response.caseId},   documentId:  ${response.documentId} ")
            response.caseId
        }
    }

    //muligens midlertidig metode for å sende kun en mottaker til EUX.
    private fun getFirstInstitution(institutions: List<InstitusjonItem>): String {
        institutions.forEach {
            return it.institution ?: throw IkkeGyldigKallException("institujson kan ikke være tom")
        }
        throw IkkeGyldigKallException("Mangler mottaker register (InstitusjonItem)")
    }

}

