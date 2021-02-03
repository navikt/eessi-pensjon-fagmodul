package no.nav.eessi.pensjon.api.person

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doNothing
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.fagmodul.models.FamilieRelasjonType.FAR
import no.nav.eessi.pensjon.fagmodul.models.FamilieRelasjonType.MOR
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerId
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3IkkeFunnetException
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonClient
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import no.nav.pensjon.v1.avdod.V1Avdod
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.person.V1Person
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Familierelasjon
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Familierelasjoner
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Kjoenn
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Kjoennstyper
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Landkoder
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personnavn
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Statsborgerskap
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonResponse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.ComponentScan
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status


@WebMvcTest(PersonController::class)
@ComponentScan(basePackages = ["no.nav.eessi.pensjon.api.person"])
@ActiveProfiles("unsecured-webmvctest")
class PersonControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @MockBean
    lateinit var mockAktoerregisterService: AktoerregisterService

    @MockBean
    lateinit var mockPersonV3Service: PersonV3Service

    @MockBean
    lateinit var auditLogger: AuditLogger

    @MockBean
    lateinit var mockPensjonClient: PensjonsinformasjonClient

    @MockBean
    lateinit var pdlService: PersonService

    @Test
    fun `getPerson should return Person as json`() {

        doNothing().whenever(auditLogger).log(any(), any())
        whenever(mockAktoerregisterService.hentGjeldendeIdent(IdentGruppe.NorskIdent, AktoerId(anAktorId))).thenReturn(NorskIdent(anFnr))
        whenever(mockPersonV3Service.hentPersonResponse(anFnr)).thenReturn(hentPersonResponse)

        val response = mvc.perform(
            get("/person/$anAktorId")
                .accept(MediaType.APPLICATION_JSON))
            .andReturn().response

        JSONAssert.assertEquals(personAsJson, response.contentAsString, false)
    }

    @Test
    fun `getNameOnly should return names as json`() {
        doNothing().whenever(auditLogger).log(any(), any())
        whenever(mockAktoerregisterService.hentGjeldendeIdent(IdentGruppe.NorskIdent, AktoerId(anAktorId))).thenReturn(NorskIdent(anFnr))
        whenever(mockPersonV3Service.hentPersonResponse(anFnr)).thenReturn(hentPersonResponse)

        val response = mvc.perform(
            get("/personinfo/$anAktorId")
                .accept(MediaType.APPLICATION_JSON))
            .andReturn().response

        JSONAssert.assertEquals(namesAsJson, response.contentAsString, false)
    }

    @Test
    fun `should return NOT_FOUND hvis personen ikke finnes i TPS`() {
        doThrow(PersonV3IkkeFunnetException("Error is Expected")).whenever(mockPersonV3Service).hentPersonResponse(anFnr)
        doNothing().whenever(auditLogger).log(any(), any())
        whenever(mockAktoerregisterService.hentGjeldendeIdent(IdentGruppe.NorskIdent, AktoerId(anAktorId))).thenReturn(NorskIdent(anFnr))

        mvc.perform(
            get("/personinfo/$anAktorId")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound)
    }

    @Test
    fun `getDeceased should return a list of deceased parents given a remaining, living child` (){
        val aktoerId = "1234568"
        val vedtaksId = "22455454"
        val fnrGjenlevende = "13057065487"
        val avdodMorfnr = "310233213123"
        val avdodFarfnr = "101020223123"

        val mockPensjoninfo = Pensjonsinformasjon()
        mockPensjoninfo.avdod = V1Avdod()
        mockPensjoninfo.person = V1Person()
        mockPensjoninfo.avdod.avdodMor = avdodMorfnr
        mockPensjoninfo.avdod.avdodFar = avdodFarfnr
        mockPensjoninfo.person.aktorId = aktoerId

        val avdodMorTPSBruker = lagTPSBruker(avdodMorfnr, "Fru", "Blyant")
        val avdodFarTPSBruker = lagTPSBruker(avdodFarfnr, "Hr", "Blyant")
        val gjenlevendeBarnTSPBruker = lagTPSBruker(fnrGjenlevende, "Liten", "Blyant")
                .medVoksen(avdodMorfnr, MOR.name)
                .medVoksen(avdodFarfnr, FAR.name)

        doReturn(mockPensjoninfo).whenever(mockPensjonClient).hentAltPaaVedtak(vedtaksId)
        doReturn(avdodMorTPSBruker).whenever(mockPersonV3Service).hentBruker(avdodMorfnr)
        doReturn(avdodFarTPSBruker).whenever(mockPersonV3Service).hentBruker(avdodFarfnr)

        doReturn(NorskIdent(fnrGjenlevende)).whenever(mockAktoerregisterService).hentGjeldendeIdent(IdentGruppe.NorskIdent, AktoerId(aktoerId))
        doReturn(HentPersonResponse().withPerson(gjenlevendeBarnTSPBruker)).whenever(mockPersonV3Service).hentPersonResponse(fnrGjenlevende)

        val response = mvc.perform(
                get("/person/$aktoerId/avdode/vedtak/$vedtaksId")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn().response

        val actual = mapJsonToAny(response.contentAsString, typeRefs<List<PersonController.PersoninformasjonAvdode>>())
        val avdodFarResponse = actual.first()
        val avdodMorResponse = actual.last()

        assertTrue(avdodMorResponse.fnr == avdodMorfnr)
        assertTrue(avdodMorResponse.relasjon == MOR.name)
        assertTrue(avdodFarResponse.fnr == avdodFarfnr)
        assertTrue(avdodFarResponse.relasjon == FAR.name)
    }


    @Test
    fun `getDeceased should return a list of one parent given a remaining, living child` (){
        val aktoerId = "1234568"
        val vedtaksId = "22455454"
        val fnrGjenlevende = "13057065487"
        val avdodMorfnr = "310233213123"

        val mockPensjoninfo = Pensjonsinformasjon()
        mockPensjoninfo.avdod = V1Avdod()
        mockPensjoninfo.person = V1Person()
        mockPensjoninfo.avdod.avdodMor = avdodMorfnr
        mockPensjoninfo.person.aktorId = aktoerId

        val relasjonMor = "MORA"
        val avdodMorTPSBruker = lagTPSBruker(avdodMorfnr, "Stor", "Blyant")
        val gjenlevendeBarnTSPBruker = lagTPSBruker(fnrGjenlevende, "Liten", "Blyant").medVoksen(avdodMorfnr, relasjonMor)

        doReturn(mockPensjoninfo).whenever(mockPensjonClient).hentAltPaaVedtak(vedtaksId)
        doReturn(avdodMorTPSBruker).whenever(mockPersonV3Service).hentBruker(avdodMorfnr)
        doReturn(NorskIdent(fnrGjenlevende)).whenever(mockAktoerregisterService).hentGjeldendeIdent(IdentGruppe.NorskIdent, AktoerId(aktoerId))
        doReturn(HentPersonResponse().withPerson(gjenlevendeBarnTSPBruker)).whenever(mockPersonV3Service).hentPersonResponse(fnrGjenlevende)

        val response = mvc.perform(
                get("/person/$aktoerId/avdode/vedtak/$vedtaksId")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn().response

        val actual = mapJsonToAny(response.contentAsString, typeRefs<List<PersonController.PersoninformasjonAvdode>>()).first()
        assertTrue(actual.fnr == avdodMorfnr)
        assertTrue(actual.relasjon == MOR.name)
    }


    @Test
    fun `getDeceased should return an empty list when both partents are alive` (){
        val vedtaksId = "22455454"
        val fnrGjenlevende = "13057065487"
        val avdodMorfnr = "310233213123"

        val mockPensjoninfo = Pensjonsinformasjon()
        mockPensjoninfo.avdod = V1Avdod()
        mockPensjoninfo.person = V1Person()

        val gjenlevendeBarnTSPBruker = lagTPSBruker(fnrGjenlevende, "Liten", "Blyant").medVoksen(avdodMorfnr, "MOR")
        doReturn(mockPensjoninfo).whenever(mockPensjonClient).hentAltPaaVedtak(vedtaksId)
        doReturn(NorskIdent(fnrGjenlevende)).whenever(mockAktoerregisterService).hentGjeldendeIdent(eq(IdentGruppe.NorskIdent), any<AktoerId>())
        doReturn(HentPersonResponse().withPerson(gjenlevendeBarnTSPBruker)).whenever(mockPersonV3Service).hentPersonResponse(fnrGjenlevende)

        val response = mvc.perform(
                get("/person/$fnrGjenlevende/avdode/vedtak/$vedtaksId")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn().response
        val list : List<String>  = mapJsonToAny(response.contentAsString, typeRefs())
        assert(list.isEmpty())
    }


    private val anAktorId = "012345"
    private val anFnr = "01010123456"

    private val hentPersonResponse =
            HentPersonResponse()
                    .withPerson(Person()
                            .withPersonnavn(Personnavn()
                                    .withFornavn("OLA")
                                    .withEtternavn("NORDMANN")
                                    .withSammensattNavn("NORDMANN OLA")))

    private val personAsJson = """{
                  "person": {
                    "diskresjonskode": null,
                    "bostedsadresse": null,
                    "sivilstand": null,
                    "statsborgerskap": null,
                    "harFraRolleI": [],
                    "aktoer": null,
                    "kjoenn": null,
                    "personnavn": { "fornavn": "OLA", "etternavn": "NORDMANN", "mellomnavn": null, "sammensattNavn": "NORDMANN OLA", "endringstidspunkt": null, "endretAv": null, "endringstype": null},
                    "personstatus": null,
                    "postadresse": null,
                    "doedsdato": null,
                    "foedselsdato": null
                  }
                }"""

    private val namesAsJson = """{ fornavn: "OLA", etternavn: "NORDMANN", mellomnavn: null, fulltNavn: "NORDMANN OLA"}"""

    private fun lagTPSBruker(fnr: String, fornavn: String, etternavn: String) =
            Bruker()
                    .withPersonnavn(Personnavn()
                            .withEtternavn(etternavn)
                            .withFornavn(fornavn))
                    .withKjoenn(Kjoenn().withKjoenn(Kjoennstyper().withValue("M")))
                    .withAktoer(PersonIdent().withIdent(no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent().withIdent(fnr)))
                    .withStatsborgerskap(Statsborgerskap().withLand(Landkoder().withValue("NOR")))

    private fun Bruker.medVoksen(barnetsPin: String, foreldreType: String): Bruker =
            this
                    .withHarFraRolleI(Familierelasjon()
                            .withTilRolle(Familierelasjoner()
                                    .withValue(foreldreType))
                            .withTilPerson(Person()
                                    .withAktoer(PersonIdent()
                                            .withIdent(no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent()
                                                    .withIdent(barnetsPin)))))
}
