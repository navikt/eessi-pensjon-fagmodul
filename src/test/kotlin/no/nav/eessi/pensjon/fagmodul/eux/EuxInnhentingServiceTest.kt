package no.nav.eessi.pensjon.fagmodul.eux

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.eux.klient.Properties
import no.nav.eessi.pensjon.eux.klient.Rinasak
import no.nav.eessi.pensjon.eux.klient.Traits
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_02
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.fagmodul.api.BucController
import no.nav.eessi.pensjon.fagmodul.api.EuxController
import no.nav.eessi.pensjon.fagmodul.api.PrefillController
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService.BucView
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService.BucViewKilde
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService.BucViewKilde.BRUKER
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService.BucViewKilde.SAF
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.eessi.pensjon.utils.validateJson
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.web.client.HttpClientErrorException
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

private const val SAKSNR = "1111111"
private const val FNR = "13057065487"
private const val AKTOERID = "1234568"
private const val INTERNATIONAL_ID = "e94e1be2daff414f8a49c3149ec00e66"

@SpringJUnitConfig(classes = [EuxInnhentingService::class ])
@MockkBeans(
    MockkBean(name = "gcpStorageService", classes = [GcpStorageService::class], relaxed = true)
)
internal class EuxInnhentingServiceTest {

    @MockkBean( relaxed = true)
    private lateinit var euxKlient: EuxKlientAsSystemUser
    @Autowired
    private lateinit var gcpStorageService: GcpStorageService
    private lateinit var euxInnhentingService: EuxInnhentingService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        euxInnhentingService = EuxInnhentingService("q2", euxKlient, gcpStorageService)
    }

    @Test
    fun `Sjekker at vi faar alle instanser ved kall til getbuc`() {
        val euxCaseId = "12345"
        val json = javaClass.getResource("/json/buc/P_BUC_02_4.2_P2100.json")!!.readText()

        every { euxKlient.getBucJsonAsNavIdent(any()) } returns json
        val result = euxInnhentingService.getBuc(euxCaseId)

        val creator = """
            {
              "name" : "Z990638",
              "organisation" : {
                "address" : {
                  "country" : "NO"
                },
                "activeSince" : "2018-08-26T22:00:00.000+0000",
                "acronym" : "NAV ACCT 07",
                "countryCode" : "NO",
                "name" : "NAV ACCEPTANCE TEST 07",
                "id" : "NO:NAVAT07"
              },
              "id" : "AWjw_fiySVr4y0o3ykMJ",
              "type" : "User"
            }
        """.trimIndent()

        val subject = """
            {
              "birthday" : "2019-03-13",
              "surname" : "STRIELA",
              "sex" : "f",
              "name" : "NYDELIG"
            }
        """.trimIndent()

        assertEquals(creator, result.creator?.toJsonSkipEmpty())
        assertEquals(subject, result.subject?.toJsonSkipEmpty())
        assertEquals(INTERNATIONAL_ID, result.internationalId)
        assertEquals("3893690", result.id)
        assertEquals("P_BUC_02", result.processDefinitionName)
        assertEquals("v4.2", result.processDefinitionVersion)
        assertEquals("2020-04-14T09:11:37.000+0000", result.lastUpdate)
        assertEquals("2020-04-14T09:01:39.537+0000", result.startDate)
    }

    @Test
    fun getBucViewBruker() {
        val euxCaseId = "3893690"
        val rinaSaker = listOf(Rinasak(euxCaseId, P_BUC_02.name, Traits(), "", Properties(), "open"))

        every { euxKlient.getRinasaker(eq(FNR), any()) } returns rinaSaker
        every { euxKlient.getBucJsonAsNavIdent(euxCaseId) } returns Buc(id = "3893690", processDefinitionName = "P_BUC_03").toJson()

        val result = euxInnhentingService.hentBucViewBruker(FNR, AKTOERID, SAKSNR)
        assertEquals(1, result.size)
        assertEquals(bucView(euxCaseId, BRUKER), result[0])
    }

    @Test
    fun getBucViewBrukerSaf() {
        val euxCaseId = "3893690"
        val json = javaClass.getResource("/json/buc/P_BUC_02_4.2_P2100.json")!!.readText()

        every { euxKlient.getBucJsonAsNavIdent(any()) } returns json

        val result = euxInnhentingService.lagBucViews(AKTOERID, SAKSNR, listOf(euxCaseId), SAF)
        assertEquals(1, result.size)
        assertEquals(bucView(euxCaseId, SAF), result[0])
    }

    @Test
    fun `forventer et korrekt navsed P6000 ved kall til getSedOnBucByDocumentId`() {
        val json = javaClass.getResource("/json/nav/P6000-NAV.json")!!.readText()
        assertTrue(validateJson(json))

        every { euxKlient.getSedOnBucByDocumentIdNotAsSystemUser(any(), any(), any()) } returns json

        val result = euxInnhentingService.getSedOnBucByDocumentId("12345678900", "0bb1ad15987741f1bbf45eba4f955e80")
        assertEquals(SedType.P6000, result.type)
        result as P6000

        assertEquals("234", result.pensjon?.vedtak?.firstOrNull()?.delvisstans?.utbetaling?.beloepBrutto)
        assertEquals("BE", result.pensjon?.tilleggsinformasjon?.annen?.institusjonsadresse?.land)
    }

    @Test
    fun `Calling eux-rina-api to create BucSedAndView gets one BUC per rinaid`() {
        val rinasakerJson = javaClass.getResource("/json/rinasaker/rinasaker_34567890111.json")!!.readText()
        val rinasaker = mapJsonToAny<List<Rinasak>>(rinasakerJson)
        val bucJson = File("src/test/resources/json/buc/buc-158123_2_v4.1.json").readText()
        every { euxKlient.getBucJsonAsNavIdent(any()) } returns bucJson

        val result = euxInnhentingService.getBucAndSedView(rinasaker.map{ it.id!! }.toList())
        assertEquals(rinasaker.size, result.size)
    }

    @Test
    fun `Calling eux-rina-api to create BucSedAndView returns a result`() {
        val bucJson = javaClass.getResource("/json/buc/buc-158123_2_v4.1.json")!!.readText()
        every { euxKlient.getBucJsonAsNavIdent(any()) } returns bucJson

        val euxCaseId = "158123"
        val result = euxInnhentingService.getBucAndSedView(listOf(euxCaseId))
        println(result)
        assertEquals(1, result.size)
        assertEquals(euxCaseId, result.first().caseId)
    }

    @Test
    fun callingEuxServiceForSinglemenuUI_AllOK() {
        val euxCaseId = "158123"
        val bucStr = javaClass.getResource("/json/buc/buc-158123_2_v4.1.json")!!.readText()

        every { euxKlient.getBucJsonAsNavIdent(any()) } returns bucStr

        val firstJson = euxInnhentingService.getSingleBucAndSedView(euxCaseId)

        assertEquals(euxCaseId, firstJson.caseId)
        assertTrue(validateJson(bucStr))
        assertEquals(18, firstJson.seds?.size)
    }

    @ParameterizedTest
    @CsvSource("M_BUC_03a, open", "M_BUC_03b, open", "P_BUC_01, open", "H_BUC_07, open", "P_BUC_03, open", "P_BUC_06, closed")
    fun `Test filter list av rinasak ta bort elementer av archived og ugyldige buc samt spesielle a og b bucer`(bucType: String, status: String) {
        assertTrue(euxInnhentingService.erRelevantForVisningIEessiPensjon(Rinasak("723", bucType, null, "PO", null, status)))
    }

    @ParameterizedTest
    @CsvSource(
        "H_BUC_02, open", "P_BUC_01, archived", "FF_BUC_01, closed", "M_BUC_02, archived", "FP_BUC_01, open", "H_BUC_02, open",
        "P_BUC_01, archived", "FF_BUC_01, closed", "FF_BUC_01, archived", "H_BUC_01, archived", "P_BUC_07, archived",
        )
    fun `Test filter lister rinasaker ta bort elementer av archived og ugyldige buc samt spesielle a og b bucer`(bucType: String, status: String) {
        assertFalse(euxInnhentingService.erRelevantForVisningIEessiPensjon(Rinasak("723", bucType, null, "PO", null, status)))
    }

    @Test
    fun callingEuxServiceListOfRinasaker_Ok() {
        val fnr = "12345678900"
        val euxCaseId = "8877665511"
        val jsonRinasaker = javaClass.getResource("/json/rinasaker/rinasaker_12345678901.json")!!.readText()
        val jsonEnRinasak = javaClass.getResource("/json/rinasaker/rinasaker_ensak.json")!!.readText()
        val orgRinasaker = mapJsonToAny<List<Rinasak>>(jsonRinasaker)
        val enSak = mapJsonToAny<List<Rinasak>>(jsonEnRinasak)

        every { euxKlient.getRinasaker(fnr = eq(fnr), euxCaseId = null) } returns orgRinasaker
        every { euxKlient.getRinasaker(fnr = null, euxCaseId = euxCaseId) } returns enSak

        val result = euxInnhentingService.getRinasaker(fnr, listOf(euxCaseId))

        assertEquals(154, orgRinasaker.size)
        assertEquals(orgRinasaker.size + 1, result.size)
    }

    @Test
    fun `henter rinaid fra saf og rina hvor begge er tomme`() {
        val euxCaseId = "12345678900"
        every { euxKlient.getRinasaker(eq(euxCaseId), null) } returns listOf<Rinasak>()

        val result = euxInnhentingService.getRinasaker(euxCaseId, emptyList())
        assertEquals(0, result.size)
    }

    @Test
    fun `Gitt at det finnes relasjon til gjenlevende i avdøds SEDer når avdøds SEDer hentes så hentes alle med samme avdodnr`() {
        val avdodFnr = "12345678910"
        val gjenlevendeFnr = "1234567890000"
        val rinasakid = "3893690"

        val rinaSaker = listOf(Rinasak(rinasakid, "P_BUC_02", Traits(), "", Properties(), "open"))
        every { euxKlient.getRinasaker(eq(avdodFnr), any()) } returns rinaSaker

        val actual = euxInnhentingService.hentBucViewAvdod(avdodFnr, gjenlevendeFnr, rinasakid)

        assertEquals(1, actual.size)
        assertEquals(rinasakid, actual.first().euxCaseId)
        assertEquals(P_BUC_02, actual.first().buctype)
    }

    @Test
    fun `Henter buc og dokumentID` () {
        val euxCaseId = "123"
        val json = javaClass.getResource("/json/buc/P_BUC_02_4.2_P2100.json")!!.readText()
        val buc = mapJsonToAny<Buc>(json)

        every { euxKlient.getBucJsonAsNavIdent(any()) } returns json

        val actual = euxInnhentingService.hentBucOgDocumentIdAvdod(listOf(euxCaseId))

        assert(actual[0].rinaidAvdod == euxCaseId)
        assert(actual[0].buc.id == buc.id)
        assert(actual[0].buc.internationalId == "e94e1be2daff414f8a49c3149ec00e66")
    }

    @Test
    fun `Henter flere buc og dokumentID fra avdod` () {
        val json = javaClass.getResource("/json/buc/P_BUC_02_4.2_P2100.json")!!.readText()
        every { euxKlient.getBucJsonAsNavIdent(any()) } returns json andThen json

        val actual = euxInnhentingService.hentBucOgDocumentIdAvdod(listOf("123","321"))
        assertEquals(2, actual.size)
    }

    @Test
    fun `Henter buc og dokumentID feiler ved henting av buc fra eux`() {
        every { euxKlient.getBucJsonAsNavIdent(any()) } throws HttpClientErrorException(HttpStatus.UNAUTHORIZED)
        assertThrows<Exception> { euxInnhentingService.hentBucOgDocumentIdAvdod(listOf("123")) }
    }

    @Test
    fun `Gitt det finnes et json dokument p2100 når avdod buc inneholder en dokumentid så hentes sed p2100 fra eux`() {
        val rinaid = "12344"
        val dokumentid = "3423432453255"
        val documentsItem = listOf(DocumentsItem(type = SedType.P2100, id = dokumentid, direction = "OUT"))
        val buc = Buc(processDefinitionName = "P_BUC_02", documents = documentsItem)
        val docs = listOf(BucOgDocumentAvdod(rinaid, buc, dokumentid))

        every { euxKlient.getSedOnBucByDocumentIdNotAsSystemUser(rinaid, dokumentid, any()) } returns SedType.P2100.name

        val actual = euxInnhentingService.hentDocumentJsonAvdod(docs)

        assertEquals(1, actual.size)
        assertEquals(SedType.P2100.name, actual.single().dokumentJson)
        assertEquals(rinaid, actual.single().rinaidAvdod)
    }

    @Test
    fun `Gitt det finnes et json dokument p2100 når avdød buc inneholder en dokumentid så feiler det ved hentig fra eux`() {
        val rinaid = "12344"
        val dokumentid = "3423432453255"
        val documentsItem = listOf(DocumentsItem(type = SedType.P2100, id = dokumentid, direction = "OUT"))
        val buc = Buc(processDefinitionName = "P_BUC_02", documents = documentsItem)
        val docs = listOf(BucOgDocumentAvdod(rinaid, buc, dokumentid))

        every {euxKlient.getSedOnBucByDocumentIdNotAsSystemUser(rinaid, dokumentid, any()) } throws HttpClientErrorException(HttpStatus.MULTI_STATUS)

        assertThrows<Exception> { euxInnhentingService.hentDocumentJsonAvdod(docs) }
    }

    @Test
    fun `Gitt det finnes en p2100 med gjenlevende Når det filtrers på gjenlevende felt og den gjenlevndefnr Så gis det gyldige buc`() {
        val rinaid = "123123"
        val gjenlevendeFnr = "1234567890000"
        val sedjson = javaClass.getResource("/json/nav/P2100-PinNO-NAV.json")!!.readText()
        val docs = listOf(BucOgDocumentAvdod(rinaid, Buc(id = rinaid, processDefinitionName = "P_BUC_02"), sedjson))

        val actual = euxInnhentingService.filterGyldigBucGjenlevendeAvdod(docs, gjenlevendeFnr)
        assertEquals(1, actual.size)
    }

    //Kommenterer ut disse for å sjekke nytten av dem..
//    @Test
//    fun `Gitt det ikke finnes en p2100 med gjenlevende Når det filtrers på gjenlevende felt og den gjenlevndefnr Så ingen buc`() {
//        val rinaid = "123123"
//        val gjenlevendeFnr = "12345678123123"
//        val sedjson = javaClass.getResource("/json/nav/P2100-PinNO-NAV.json")!!.readText()
//        val docs = listOf(BucOgDocumentAvdod(rinaid, Buc(id = rinaid, processDefinitionName = "P_BUC_02"), sedjson))
//
//        val actual = euxInnhentingService.filterGyldigBucGjenlevendeAvdod(docs, gjenlevendeFnr)
//        assertEquals(0, actual.size)
//    }
//
//    @Test
//    fun `Sjekk om Pin node ikke finnes i SED logger feil`() {
//        val manglerPinGjenlevende = """
//            {
//              "sed" : "P2000",
//              "sedGVer" : "4",
//              "sedVer" : "1",
//              "nav" : {
//                "bruker" : {
//                  "person" : {
//                    "pin" : [ {
//                      "institusjonsnavn" : "NOINST002, NO INST002, NO",
//                      "institusjonsid" : "NO:noinst002",
//                      "identifikator" : "3123",
//                      "land" : "NO"
//                    } ],
//                    "statsborgerskap" : [ {
//                      "land" : "QX"
//                    } ],
//                    "etternavn" : "Testesen",
//                    "fornavn" : "Test",
//                    "kjoenn" : "M",
//                    "foedselsdato" : "1988-07-12"
//                  }
//                },
//                "krav" : {
//                  "dato" : "2018-06-28"
//                }
//              },
//              "pensjon" : {
//                "kravDato" : {
//                  "dato" : "2018-06-28"
//                }
//              }
//            }
//        """.trimIndent()
//        val data = listOf(BucOgDocumentAvdod("2321", Buc(), manglerPinGjenlevende))
//        val result = euxInnhentingService.filterGyldigBucGjenlevendeAvdod(data, "23123")
//        assertEquals(0, result.size)
//    }

    @Test
    fun `check apiRequest for prefill X010 contains X009 payload`() {
        val x009Json = javaClass.getResource("/json/nav/X009-NAV.json")!!.readText()
        val apiRequest = EuxTestUtils.apiRequestWith("1000000", emptyList(), buc = P_BUC_01, sed = SedType.X010)

        every { euxKlient.getSedOnBucByDocumentIdNotAsSystemUser(any(), any(), any()) } returns x009Json

        val json = euxInnhentingService.checkForX010AndAddX009(apiRequest, "20000000")
        assert(json.toJson().contains("X009"))
        JSONAssert.assertEquals(json.payload, payload(), JSONCompareMode.LENIENT)
    }

    private fun payload(): String {
        val payload = """
                {
                  "sed" : "X009",
                  "nav" : {
                    "sak" : {
                      "kontekst" : {
                        "bruker" : {
                          "mor" : null,
                          "far" : null,
                          "person" : {
                            "pin" : null,
                            "pinland" : null,
                            "statsborgerskap" : null,
                            "etternavn" : "æøå",
                            "etternavnvedfoedsel" : null,
                            "fornavn" : "æøå",
                            "fornavnvedfoedsel" : null,
                            "tidligerefornavn" : null,
                            "tidligereetternavn" : null,
                            "kjoenn" : "M",
                            "foedested" : null,
                            "foedselsdato" : "æøå",
                            "sivilstand" : null,
                            "relasjontilavdod" : null,
                            "rolle" : null,
                            "kontakt" : null,
                            "doedsdato" : null
                          },
                          "adresse" : null,
                          "arbeidsforhold" : null,
                          "bank" : null
                        },
                        "refusjonskrav" : {
                          "antallkrav" : "æøå",
                          "id" : "æøå"
                        },
                        "arbeidsgiver" : {
                          "identifikator" : [ {
                            "id" : "æøå",
                            "type" : "registrering"
                          } ],
                          "adresse" : {
                            "gate" : "æøå",
                            "bygning" : "æøå",
                            "by" : "æøå",
                            "postnummer" : "æøå",
                            "postkode" : null,
                            "region" : "æøå",
                            "land" : "NO",
                            "kontaktpersonadresse" : null,
                            "datoforadresseendring" : null,
                            "postadresse" : null,
                            "startdato" : null,
                            "type" : null,
                            "annen" : null
                          },
                          "navn" : "æøå"
                        }
                      },
                      "leggtilinstitusjon" : null,
                      "paaminnelse" : {
                        "svar" : null,
                        "sende" : [ {
                          "type" : "dokument",
                          "detaljer" : "æøå"
                        } ]
                      }
                    }
                  },
                  "sedGVer" : "4",
                  "sedVer" : "2",
                  "pensjon" : null
                }
            """.trimIndent()
        return payload
    }

    @Test
    fun `Sjekk om Pin node finnes i Sed returnerer BUC`() {
        val sedfilepath = "src/test/resources/json/nav/P2100-PinNO-NAV.json"
        val sedjson = String(Files.readAllBytes(Paths.get(sedfilepath)))

        val data = listOf(BucOgDocumentAvdod("23123", Buc(id = "2131", processDefinitionName = "P_BUC_02"), sedjson))
        val result = euxInnhentingService.filterGyldigBucGjenlevendeAvdod(data, "1234567890000")
        assertEquals(1, result.size)
    }

    private fun bucView(euxCaseId: String, bucViewKilde: BucViewKilde) = BucView(
        euxCaseId = euxCaseId,
        buctype = P_BUC_02,
        aktoerId = AKTOERID,
        saknr = SAKSNR,
        avdodFnr = null,
        kilde = bucViewKilde
    )
}
