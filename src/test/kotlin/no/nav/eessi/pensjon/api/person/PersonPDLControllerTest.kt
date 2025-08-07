package no.nav.eessi.pensjon.api.person

import com.ninjasquad.springmockk.MockkBean
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_02
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_06
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.ActionOperation
import no.nav.eessi.pensjon.eux.model.buc.ActionsItem
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.pensjonsinformasjon.clients.PensjonsinformasjonClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonoppslagException
import no.nav.eessi.pensjon.personoppslag.pdl.model.*
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonService
import no.nav.eessi.pensjon.shared.person.FodselsnummerGenerator
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.pensjon.v1.avdod.V1Avdod
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.person.V1Person
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata as PDLMetaData

private const val AKTOERID = "1234568"
private const val VEDTAK_ID = "22455454"
private const val RINA_NR = "1002342345689"

private const val FNR = "01010123456"
private const val AVDOD_FNR = "18077443335"
private const val AVDOD_MOR_FNR = "310233213123"
private const val AVDOD_FAR_FNR = "101020223123"
private const val GJENLEVENDE_FNR = "13057065487"

@WebMvcTest(PersonPDLController::class)
@ContextConfiguration(classes = [PersonPDLControllerTest.Config::class])
@ComponentScan(basePackages = ["no.nav.eessi.pensjon.api.person"])
@ActiveProfiles("unsecured-webmvctest")
class PersonPDLControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @MockkBean
    lateinit var auditLogger: AuditLogger

    @MockkBean
    lateinit var pdlService: PersonService

    @MockkBean
    lateinit var euxService: EuxInnhentingService

    @Autowired
    lateinit var mockPensjonClient: PensjonsinformasjonClient

    @TestConfiguration
    class Config {
        @Bean
        fun mockPensjonClient(): PensjonsinformasjonClient {
            return mockk(relaxed = true)
        }

        @Bean
        fun pensjonsinformasjonService(): PensjonsinformasjonService {
            return PensjonsinformasjonService(mockPensjonClient())
        }
    }

    @BeforeEach
    fun before() {
        MockKAnnotations.init(this, relaxed = true, relaxUnitFun = true)
        justRun { auditLogger.log(any(), any()) }
    }

    @Test
    fun testGetAktoeridEndpoint() {
        every { pdlService.hentAktorId(FNR) } returns AktoerId(AKTOERID)

        mvc.perform(get("/person/pdl/aktoerid/$FNR")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().string(AKTOERID))
    }

    @Test
    fun `getPerson should return Person as json`() {
        every { pdlService.hentPerson(any()) } returns lagPerson(etternavn = "NORDMANN", fornavn = "OLA")
        val response = mvc.perform(
            get("/person/pdl/$AKTOERID")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andReturn().response

        JSONAssert.assertEquals(personResponseAsJson3, response.contentAsString, false)
    }

    @Test
    fun `getNameOnly should return names as json`() {
        every {pdlService.hentPerson(any())  } returns lagPerson(etternavn = "NORDMANN", fornavn = "OLA")
        val response = mvc.perform(
            get("/person/pdl/info/${AKTOERID}")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andReturn().response

        JSONAssert.assertEquals(namesAsJson, response.contentAsString, false)
    }

    @Test
    fun `should return NOT_FOUND hvis personen ikke finnes`() {
        every { pdlService.hentPerson(any()) } throws PersonoppslagException("Fant ikke person", "not_found")

        mvc.perform(
            get("/person/pdl/info/${AKTOERID}")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PDL getDeceased should return a list of deceased parents given a remaining, living child`() {
        val mockPensjoninfo = Pensjonsinformasjon()
        mockPensjoninfo.avdod = V1Avdod()
        mockPensjoninfo.person = V1Person()
        mockPensjoninfo.avdod.avdodMor = AVDOD_MOR_FNR
        mockPensjoninfo.avdod.avdodFar = AVDOD_FAR_FNR
        mockPensjoninfo.person.aktorId = AKTOERID

        val avdodMor = lagPerson(
            AVDOD_MOR_FNR, "Fru", "Blyant",
            listOf(
                ForelderBarnRelasjon(
                    GJENLEVENDE_FNR,
                    Familierelasjonsrolle.BARN,
                    Familierelasjonsrolle.MOR,
                    mockMeta()
                )
            ),
            listOf(Sivilstand(Sivilstandstype.GIFT, LocalDate.of(2000, 10, 2), AVDOD_FAR_FNR, mockMeta()))
        )
        val avdodFar = lagPerson(
            AVDOD_FAR_FNR, "Hr", "Blyant",
            listOf(
                ForelderBarnRelasjon(
                    GJENLEVENDE_FNR,
                    Familierelasjonsrolle.BARN,
                    Familierelasjonsrolle.FAR,
                    mockMeta()
                )
            ),
            listOf(Sivilstand(Sivilstandstype.GIFT, LocalDate.of(2000, 10, 2), AVDOD_MOR_FNR, mockMeta()))
        )

        val barn = lagPerson(
            GJENLEVENDE_FNR, "Liten", "Blyant",
            listOf(
                ForelderBarnRelasjon(AVDOD_FAR_FNR, Familierelasjonsrolle.FAR, Familierelasjonsrolle.BARN, mockMeta()),
                ForelderBarnRelasjon(AVDOD_MOR_FNR, Familierelasjonsrolle.MOR, Familierelasjonsrolle.BARN, mockMeta())
            )
        )
        every { mockPensjonClient.hentAltPaaVedtak(VEDTAK_ID) } returns mockPensjoninfo
        every { pdlService.hentPerson(NorskIdent(AVDOD_MOR_FNR)) } returns avdodMor
        every { pdlService.hentPerson(NorskIdent(AVDOD_FAR_FNR)) } returns avdodFar
        every { pdlService.hentPerson(AktoerId(AKTOERID)) } returns barn

        val response = mvc.perform(
            get("/person/pdl/$AKTOERID/avdode/vedtak/$VEDTAK_ID")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andReturn().response

        val actual = mapJsonToAny<List<PersonPDLController.PersoninformasjonAvdode>>(response.contentAsString)
        val avdodFarResponse = actual.first()
        val avdodMorResponse = actual.last()

        assertEquals(AVDOD_MOR_FNR, avdodMorResponse.fnr)
        assertEquals(Familierelasjonsrolle.MOR.name, avdodMorResponse.relasjon)
        assertEquals(AVDOD_FAR_FNR, avdodFarResponse.fnr)
        assertEquals(Familierelasjonsrolle.FAR.name, avdodFarResponse.relasjon)
    }

    @Test
    fun `getDeceased should return a list of one parent given a remaining, living child`() {
        val mockPensjoninfo = Pensjonsinformasjon()
        mockPensjoninfo.avdod = V1Avdod()
        mockPensjoninfo.person = V1Person()
        mockPensjoninfo.avdod.avdodMor = AVDOD_MOR_FNR
        mockPensjoninfo.person.aktorId = AKTOERID

        val avdodmor = lagPerson(
            AVDOD_MOR_FNR, "Stor", "Blyant",
            listOf(
                ForelderBarnRelasjon(
                    GJENLEVENDE_FNR,
                    Familierelasjonsrolle.BARN,
                    Familierelasjonsrolle.MOR,
                    mockMeta()
                )
            )
        )
        val barn = lagPerson(
            GJENLEVENDE_FNR, "Liten", "Blyant",
            listOf(
                ForelderBarnRelasjon(
                    AVDOD_MOR_FNR,
                    Familierelasjonsrolle.MOR,
                    Familierelasjonsrolle.BARN,
                    mockMeta()
                ))
        )

        every { mockPensjonClient.hentAltPaaVedtak(VEDTAK_ID) } returns mockPensjoninfo
        every { pdlService.hentPerson(NorskIdent(AVDOD_MOR_FNR)) } returns avdodmor
        every { pdlService.hentPerson(AktoerId(AKTOERID)) } returns barn

        val response = mvc.perform(
            get("/person/pdl/$AKTOERID/avdode/vedtak/$VEDTAK_ID")
                .accept(MediaType.APPLICATION_JSON)
        ).andReturn().response

        val result = mapJsonToAny<List<PersonPDLController.PersoninformasjonAvdode?>>(response.contentAsString)

        assertEquals(1, result.size)
        val element = result.firstOrNull()
        assertEquals(AVDOD_MOR_FNR, element?.fnr)
        assertEquals(Familierelasjonsrolle.MOR.name, element?.relasjon)

    }

    @Test
    fun `getDeceased with npid should return a list of one parent given a remaining, living child`() {
        val npidGjenlevende = "01220049651"

        val mockPensjoninfo = Pensjonsinformasjon()
        mockPensjoninfo.avdod = V1Avdod()
        mockPensjoninfo.person = V1Person()
        mockPensjoninfo.avdod.avdodMor = AVDOD_MOR_FNR
        mockPensjoninfo.person.aktorId = AKTOERID

        val avdodmor = lagPerson(
            AVDOD_MOR_FNR, "Stor", "Blyant",
            listOf(
                ForelderBarnRelasjon(
                    npidGjenlevende,
                    Familierelasjonsrolle.BARN,
                    Familierelasjonsrolle.MOR,
                    mockMeta()
                )
            )
        )
        val barn = lagPerson(
            npidGjenlevende, "Liten", "Blyant",
            listOf(
                ForelderBarnRelasjon(
                    AVDOD_MOR_FNR,
                    Familierelasjonsrolle.MOR,
                    Familierelasjonsrolle.BARN,
                    mockMeta()
                ))
        )

        every { mockPensjonClient.hentAltPaaVedtak(VEDTAK_ID) } returns mockPensjoninfo
        every { pdlService.hentPerson(NorskIdent(AVDOD_MOR_FNR)) } returns avdodmor
        every { pdlService.hentPerson(AktoerId(AKTOERID)) } returns barn

        val response = mvc.perform(
            get("/person/pdl/$AKTOERID/avdode/vedtak/$VEDTAK_ID")
                .accept(MediaType.APPLICATION_JSON)
        ).andReturn().response

        val result = mapJsonToAny<List<PersonPDLController.PersoninformasjonAvdode?>>(response.contentAsString)

        assertEquals(1, result.size)
        val element = result.firstOrNull()
        assertEquals(AVDOD_MOR_FNR, element?.fnr)
        assertEquals(Familierelasjonsrolle.MOR.name, element?.relasjon)

    }

    @Test
    fun `getDeceased should return an empty list when both partents are alive`() {
        val mockPensjoninfo = Pensjonsinformasjon()
        mockPensjoninfo.person = V1Person()
        mockPensjoninfo.person.aktorId = AKTOERID

        every { mockPensjonClient.hentAltPaaVedtak(VEDTAK_ID) } returns mockPensjoninfo

        val barn = lagPerson(
            GJENLEVENDE_FNR, "Liten", "Blyant",
            listOf(
                ForelderBarnRelasjon(
                    "231231231231",
                    Familierelasjonsrolle.MOR,
                    Familierelasjonsrolle.BARN,
                    mockMeta()
                )
            )
        )

        every { pdlService.hentPerson(any()) } returns barn

        val response = mvc.perform(
            get("/person/pdl/$AKTOERID/avdode/vedtak/$VEDTAK_ID")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andReturn().response

        val list: List<PersonPDLController.PersoninformasjonAvdode?> = mapJsonToAny(response.contentAsString)
        assertEquals(emptyList<PersonPDLController.PersoninformasjonAvdode?>(), list)
    }

    @Test
    fun `Dodsdato sjekk for sjekk om vedtak inneholder avdod hvis null return tom liste`() {
        val pen = Pensjonsinformasjon()
        val penavdod = V1Avdod()
        pen.avdod = penavdod

        every { mockPensjonClient.hentAltPaaVedtak(any()) } returns pen

        val result = mvc.perform(
            get("/person/vedtak/$VEDTAK_ID/buc/$RINA_NR/avdodsdato")
            .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().is2xxSuccessful)
            .andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))
        assertEquals("[ ]", response)
    }

    @Test
    fun `avdodsdato sjekk for vedtak inneholder en avdod returneres den`() {
        val doedsPerson = lagPerson(AVDOD_FNR).copy(doedsfall = Doedsfall(LocalDate.of(2020, 6, 20), null, mockMeta()))
        val pen = Pensjonsinformasjon()
        val penavdod = V1Avdod()
        penavdod.avdod = AVDOD_FNR
        pen.avdod = penavdod

        every { mockPensjonClient.hentAltPaaVedtak(any()) } returns pen
        every { pdlService.hentPerson(any()) } returns doedsPerson

        val result = mvc.perform(
            get("/person/vedtak/$VEDTAK_ID/buc/$RINA_NR/avdodsdato")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().is2xxSuccessful)
            .andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))
        val expected = """
            [ {
              "doedsdato" : "2020-06-20",
              "sammensattNavn" : "Fornavn Etternavn",
              "ident" : "18077443335"
            } ]
        """.trimIndent()
        assertEquals(expected, response)
    }

    @Test
    fun `avdodsdato sjekk for vedtak inneholder to avdod i pbuc02 returneres den tidligere valgte avdod ut fra p2100 og returneres`() {
        val avdodfnr2 = FodselsnummerGenerator.generateFnrForTest(49)
        val documentid = "23242342a234vd423452asddf"

        val doedsPerson = lagPerson(AVDOD_FNR).copy(doedsfall = Doedsfall(LocalDate.of(2020, 6, 20), null, mockMeta()))
        val sedP2100 = P2100(
            nav = Nav(
                bruker = Bruker(
                    person = Person(
                        pin = listOf(
                            PinItem(
                                land = "NO",
                                identifikator = AVDOD_FNR
                            )
                        )
                    )
                )
            ), pensjon = null
        )
        val buc = Buc(
            id = RINA_NR,
            processDefinitionName = P_BUC_02.name,
            documents = listOf(DocumentsItem(id = documentid, direction = "OUT", type = SedType.P2100))
        )

        val pen = Pensjonsinformasjon()
        val penavdod = V1Avdod()
        penavdod.avdodMor = AVDOD_FNR
        penavdod.avdodFar = avdodfnr2
        pen.avdod = penavdod

        every { euxService.getBuc(any()) } returns buc
        every { euxService.getSedOnBucByDocumentId(any(), any()) } returns sedP2100
        every { mockPensjonClient.hentAltPaaVedtak(any()) } returns pen
        every { pdlService.hentPerson(any()) } returns doedsPerson

        val result = mvc.perform(
            get("/person/vedtak/$VEDTAK_ID/buc/$RINA_NR/avdodsdato")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().is2xxSuccessful)
            .andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))
        val expected = """
            [ {
              "doedsdato" : "2020-06-20",
              "sammensattNavn" : "Fornavn Etternavn",
              "ident" : "18077443335"
            } ]
        """.trimIndent()
        assertEquals(expected, response)
    }

    @Test
    fun `avdodsdato sjekk for vedtak inneholder to avdod i pbuc02 som ikke finnes i SED returneres tom liste`() {
        val avdodfnr = FodselsnummerGenerator.generateFnrForTest(48)
        val avdodfnr2 = FodselsnummerGenerator.generateFnrForTest(49)
        val documentid = "23242342a234vd423452asddf"

        val doedsPerson = lagPerson(avdodfnr).copy(doedsfall = Doedsfall(LocalDate.of(2010, 6, 20), null, mockMeta()))
        val sedP2100 = P2100(nav = Nav(bruker = Bruker(person = Person(pin = listOf(PinItem(land = "NO", identifikator = AVDOD_FNR))))), pensjon = null)
        val buc = Buc(
            id = RINA_NR,
            processDefinitionName = P_BUC_02.name,
            documents = listOf(DocumentsItem(id = documentid, direction = "OUT", type = SedType.P2100))
        )

        val pen = Pensjonsinformasjon()
        val penavdod = V1Avdod()
        penavdod.avdodMor = avdodfnr
        penavdod.avdodFar = avdodfnr2
        pen.avdod = penavdod

        every { euxService.getBuc(any()) } returns buc
        every { euxService.getSedOnBucByDocumentId(any(), any()) } returns sedP2100
        every { mockPensjonClient.hentAltPaaVedtak(any()) } returns pen
        every { pdlService.hentPerson(any()) } returns doedsPerson

        val result = mvc.perform(
            get("/person/vedtak/$VEDTAK_ID/buc/$RINA_NR/avdodsdato")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().is2xxSuccessful)
            .andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))
        assertEquals("[ ]", response)
    }


    @Test
    fun `avdodsdato sjekk for vedtak inneholder to avdod i pbuc06 og P5000 returneres den tidligere valgte avdod ut fra P5000 og returneres`() {
        val avdodfnr2 = FodselsnummerGenerator.generateFnrForTest(49)
        val documentid = "23242342a234vd423452asddf"

        val doedsPerson = lagPerson(
            AVDOD_FNR,
            fornavn = "AVDØD",
            etternavn = "HELTAVØD"
        ).copy(doedsfall = Doedsfall(LocalDate.of(2007, 6, 20), null, mockMeta()))
        val sedP5000 = P5000(
            nav = Nav(
                bruker = Bruker(
                    person = Person(
                        pin = listOf(
                            PinItem(
                                land = "NO",
                                identifikator = AVDOD_FNR
                            )
                        )
                    )
                )
            ), pensjon = null
        )
        val buc = Buc(
            id = RINA_NR, processDefinitionName = P_BUC_06.name,
            actions = listOf(
                ActionsItem(SedType.P5000, documentId = documentid, operation = ActionOperation.Send),
                ActionsItem(SedType.P5000, documentId = documentid, operation = ActionOperation.Update)
            ),
            documents = listOf(DocumentsItem(id = documentid, direction = "OUT", type = SedType.P5000))
        )

        val pen = Pensjonsinformasjon()
        val penavdod = V1Avdod()
        penavdod.avdodMor = AVDOD_FNR
        penavdod.avdodFar = avdodfnr2
        pen.avdod = penavdod

        every { euxService.getBuc(any()) } returns buc
        every { euxService.getSedOnBucByDocumentId(any(), any()) } returns sedP5000
        every { mockPensjonClient.hentAltPaaVedtak(any()) } returns pen
        every { pdlService.hentPerson(any()) } returns doedsPerson

        val result = mvc.perform(
            get("/person/vedtak/$VEDTAK_ID/buc/$RINA_NR/avdodsdato")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().is2xxSuccessful)
            .andReturn()
        val response = result.response.getContentAsString(charset("UTF-8"))
        val expected = """
            [ {
              "doedsdato" : "2007-06-20",
              "sammensattNavn" : "AVDØD HELTAVØD",
              "ident" : "18077443335"
            } ]
        """.trimIndent()
        assertEquals(expected, response)
    }



    private val personResponseAsJson3 = """
        {
          "identer": [
            {
              "ident": "01010123456",
              "gruppe": "FOLKEREGISTERIDENT"
            }
          ],
          "navn": {
            "fornavn": "OLA",
            "mellomnavn": null,
            "etternavn": "NORDMANN",
            "forkortetNavn": null,
            "gyldigFraOgMed": null,
            "folkeregistermetadata": null,
            "metadata": {
              "endringer": [
                {
                  "kilde": "DOLLY",
                  "registrert": "2010-04-01T10:12:03",
                  "registrertAv": "Dolly",
                  "systemkilde": "FREG",
                  "type": "OPPRETT"
                }
              ],
              "historisk": false,
              "master": "FREG",
              "opplysningsId": "fdsa234-sdfsf234-sfsdf234"
            },
            "sammensattNavn": "OLA NORDMANN",
            "sammensattEtterNavn": "NORDMANN OLA"
          },
          "adressebeskyttelse": [],
          "bostedsadresse": null,
          "oppholdsadresse": null,
          "kontaktadresse": null,
          "kontaktinformasjonForDoedsbo": null,
          "statsborgerskap": [
            {
              "land": "NOR",
              "gyldigFraOgMed": "2010-10-11",
              "gyldigTilOgMed": "2020-10-02",
              "metadata": {
                "endringer": [
                  {
                    "kilde": "DOLLY",
                    "registrert": "2010-04-01T10:12:03",
                    "registrertAv": "Dolly",
                    "systemkilde": "FREG",
                    "type": "OPPRETT"
                  }
                ],
                "historisk": false,
                "master": "FREG",
                "opplysningsId": "fdsa234-sdfsf234-sfsdf234"
              }
            }
          ],
          "foedested" : null,
          "foedselsdato" : null,
          "geografiskTilknytning": null,
          "kjoenn": {
            "kjoenn": "MANN",
            "folkeregistermetadata": {
              "gyldighetstidspunkt": "2000-10-01T12:10:31"
            },
            "metadata": {
              "endringer": [
                {
                  "kilde": "DOLLY",
                  "registrert": "2010-04-01T10:12:03",
                  "registrertAv": "Dolly",
                  "systemkilde": "FREG",
                  "type": "OPPRETT"
                }
              ],
              "historisk": false,
              "master": "FREG",
              "opplysningsId": "fdsa234-sdfsf234-sfsdf234"
            }
          },
          "doedsfall": null,
          "forelderBarnRelasjon": [],
          "sivilstand": [],
          "kontaktadresse":null,
          "kontaktinformasjonForDoedsbo":null,          
          "utenlandskIdentifikasjonsnummer":[]
        }
          """.trimIndent()

    private val namesAsJson =  """{ fornavn: "OLA", etternavn: "NORDMANN", mellomnavn: null, fulltNavn: "NORDMANN OLA"}""".trimIndent()

    private fun mockMeta() : PDLMetaData {
        return PDLMetaData(
            listOf(Endring(
                "DOLLY",
                LocalDateTime.of(2010, 4, 1, 10, 12, 3),
                "Dolly",
                "FREG",
                Endringstype.OPPRETT
            )),
            false,
            "FREG",
            "fdsa234-sdfsf234-sfsdf234"
        )
    }

    private fun lagPerson(
        fnr: String = FNR ,
        fornavn: String = "Fornavn",
        etternavn: String = "Etternavn",
        familierlasjon: List<ForelderBarnRelasjon> = emptyList(),
        sivilstand: List<Sivilstand> = emptyList()
    ) = PdlPerson(
        listOf(IdentInformasjon(fnr, IdentGruppe.FOLKEREGISTERIDENT)),
        Navn(fornavn, null,  etternavn, null, null, null, mockMeta()),
        emptyList(),
        null,
        null,
        listOf(
            Statsborgerskap(
                "NOR",
                LocalDate.of(2010, 10, 11),
                LocalDate.of(2020, 10, 2),
                mockMeta()
            )
        ),
        null,
        null,
        null,
        Kjoenn(
            KjoennType.MANN,
            Folkeregistermetadata(LocalDateTime.of(2000, 10, 1, 12, 10, 31)),
            mockMeta()
        ),
        null,
        familierlasjon,
        sivilstand,
        null,
        null,
        emptyList()
    )

}
