package no.nav.eessi.pensjon.fagmodul.eux

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.*
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.typeRefs
import no.nav.eessi.pensjon.utils.validateJson
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.web.client.HttpClientErrorException
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId

private const val SAKSNR = "1111111"
private const val FNR = "13057065487"
private const val AKTOERID = "1234568"
private const val INTERNATIONAL_ID = "e94e1be2daff414f8a49c3149ec00e66"

private const val s = "158123"

@SpringJUnitConfig(classes = [EuxInnhentingService::class])
internal class EuxInnhentingServiceTest {

    @MockkBean(name = "fagmodulEuxKlient", relaxed = true)
    private lateinit var euxKlient: EuxKlient

    @Autowired
    private lateinit var euxInnhentingService: EuxInnhentingService


    @BeforeEach
    fun setUp() {
    }

    @Test
    fun `Sjekker at vi faar alle instanser ved kall til getbuc`() {
        val euxCaseId = "12345"
        val json = javaClass.getResource("/json/buc/P_BUC_02_4.2_P2100.json")!!.readText()

        every { euxKlient.getBucJsonAsNavIdent(any()) } returns json
        val result = euxInnhentingService.getBuc(euxCaseId)

        val creator = """
            {
              "organisation" : {
                "acronym" : "NAV ACCT 07",
                "countryCode" : "NO",
                "name" : "NAV ACCEPTANCE TEST 07",
                "id" : "NO:NAVAT07"
              }
            }
        """.trimIndent()

        val subject = """
            {
              "birthday" : "2019-03-13",
              "address" : null,
              "surname" : "STRIELA",
              "sex" : "f",
              "contactMethods" : null,
              "name" : "NYDELIG",
              "pid" : null,
              "id" : null,
              "title" : null,
              "age" : null
            }
        """.trimIndent()


        assertEquals(creator, result.creator?.toJson())
        assertEquals(subject, result.subject?.toJson())
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
        val rinaSaker = listOf(Rinasak(euxCaseId, BucType.P_BUC_02.name, Traits(), "", Properties(), "open", internationalId = INTERNATIONAL_ID))
        every { euxKlient.getRinasaker(eq(FNR), any(), any(), eq( "\"open\"")) } returns rinaSaker

        val result = euxInnhentingService.getBucViewBruker(FNR, AKTOERID, SAKSNR)
        assertEquals(1, result.size)
        assertEquals(BucView(euxCaseId=euxCaseId, buctype= BucType.P_BUC_02, aktoerId= AKTOERID, saknr= SAKSNR, avdodFnr=null, kilde=BucViewKilde.BRUKER, internationalId= INTERNATIONAL_ID), result[0])
    }

    @Test
    fun getBucViewBrukerSaf() {
        val euxCaseId = "3893690"
        val json = javaClass.getResource("/json/buc/P_BUC_02_4.2_P2100.json")!!.readText()
        every { euxKlient.getBucJsonAsNavIdent(any()) } returns json

        val result = euxInnhentingService.getBucViewBrukerSaf(AKTOERID, SAKSNR, listOf(euxCaseId))
        assertEquals(1, result.size)
        assertEquals(BucView(euxCaseId=euxCaseId, buctype= BucType.P_BUC_02, aktoerId= AKTOERID, saknr= SAKSNR, avdodFnr=null, kilde=BucViewKilde.SAF, internationalId= INTERNATIONAL_ID), result[0])
    }

    @Test
    fun `forventer et korrekt navsed P6000 ved kall til getSedOnBucByDocumentId`() {
        val json = javaClass.getResource("/json/nav/P6000-NAV.json")!!.readText()
        Assertions.assertTrue(validateJson(json))

        every { euxKlient.getSedOnBucByDocumentIdAsJson(any(), any()) } returns json

        val result = euxInnhentingService.getSedOnBucByDocumentId("12345678900", "0bb1ad15987741f1bbf45eba4f955e80")
        assertEquals(SedType.P6000, result.type)
        result as P6000

        assertEquals("234", result.p6000Pensjon?.vedtak?.firstOrNull()?.delvisstans?.utbetaling?.beloepBrutto)
        assertEquals("BE", result.p6000Pensjon?.tilleggsinformasjon?.annen?.institusjonsadresse?.land)

    }

    @Test
    fun `Calling eux-rina-api to create BucSedAndView gets one BUC per rinaid`() {
        val rinasakerJson = javaClass.getResource("/json/rinasaker/rinasaker_34567890111.json")!!.readText()
        val rinasaker = mapJsonToAny(rinasakerJson, typeRefs<List<Rinasak>>())
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
        assertEquals(1, result.size)
        assertEquals(euxCaseId, result.first().caseId)
    }

    @Test
    fun callingEuxServiceForSinglemenuUI_AllOK() {
        val euxCaseId = "158123"
        val bucStr = javaClass.getResource("/json/buc/buc-158123_2_v4.1.json")!!.readText()

        every { euxKlient.getBucJsonAsNavIdent(any()) } returns bucStr

        val firstJson = euxInnhentingService.getSingleBucAndSedView(euxCaseId)

        var lastUpdate: Long = 0
        firstJson.lastUpdate?.let { lastUpdate = it }

        assertEquals(euxCaseId, firstJson.caseId)
        Assertions.assertTrue(validateJson(bucStr))
        assertEquals("2019-05-20T16:35:34",  Instant.ofEpochMilli(lastUpdate).atZone(ZoneId.systemDefault()).toLocalDateTime().toString())
        assertEquals(18, firstJson.seds?.size)
    }

    @Test
    fun `Test filter list av rinasak ta bort elementer av archived`() {
        val dummyList = listOf(
                Rinasak("723", "P_BUC_01", null, "PO", null, "open", null),
                Rinasak("2123", "P_BUC_03", null, "PO", null, "open", null),
                Rinasak("423", "H_BUC_01", null, "PO", null, "archived", null),
                Rinasak("234", "P_BUC_06", null, "PO", null, "closed", null),
                Rinasak("8423", "P_BUC_07", null, "PO", null, "archived", null)
        )

        val result = euxInnhentingService.getFilteredArchivedaRinasakerSak(dummyList)
        assertEquals(3, result.size)
        assertEquals("2123", result.first().id)
    }

    @Test
    fun `Test filter list av rinasak ta bort elementer av archived og ugyldige buc`() {
        val dummyList = listOf(
                Rinasak("723", "FP_BUC_01", null, "PO", null, "open", null),
                Rinasak("2123", "H_BUC_02", null, "PO", null, "open", null),
                Rinasak("423", "P_BUC_01", null, "PO", null, "archived", null),
                Rinasak("234", "FF_BUC_01", null, "PO", null, "closed", null),
                Rinasak("8423", "FF_BUC_01", null, "PO", null, "archived", null),
                Rinasak("8223", "H_BUC_07", null, "PO", null, "open", null)
        )

        val result = euxInnhentingService.getFilteredArchivedaRinasakerSak(dummyList)
        assertEquals(1, result.size)
        assertEquals("8223", result.first().id)
    }

    @Test
    fun `Test filter list av rinasak ta bort elementer av archived og ugyldige buc samt spesielle a og b bucer`() {
        val dummyList = listOf(
                Rinasak("723", "M_BUC_03a", null, "PO", null, "open", null),
                Rinasak("2123", "H_BUC_02", null, "PO", null, "open", null),
                Rinasak("423", "P_BUC_01", null, "PO", null, "archived", null),
                Rinasak("234", "FF_BUC_01", null, "PO", null, "closed", null),
                Rinasak("8423", "M_BUC_02", null, "PO", null, "archived", null),
                Rinasak("8223", "M_BUC_03b", null, "PO", null, "open", null),
                Rinasak("6006777", "P_BUC_01", null, "PO", null, "open", null)
        )

        val result = euxInnhentingService.getFilteredArchivedaRinasakerSak(dummyList)
        assertEquals(2, result.size)
        assertEquals("723", result.first().id)
        assertEquals("8223", result.last().id)
    }

    @Test
    fun callingEuxServiceListOfRinasaker_Ok() {
        val fnr = "12345678900"
        val euxCaseId = "8877665511"
        val jsonRinasaker = javaClass.getResource("/json/rinasaker/rinasaker_12345678901.json")!!.readText()
        val jsonEnRinasak = javaClass.getResource("/json/rinasaker/rinasaker_ensak.json")!!.readText()
        val orgRinasaker = mapJsonToAny(jsonRinasaker, typeRefs<List<Rinasak>>())
        val enSak = mapJsonToAny(jsonEnRinasak, typeRefs<List<Rinasak>>())

        every { euxKlient.getRinasaker(eq(fnr), status = "\"open\"") } returns orgRinasaker
        every { euxKlient.getRinasaker(euxCaseId = euxCaseId, status = "\"open\"") } returns enSak

        val result = euxInnhentingService.getRinasaker(fnr, listOf(euxCaseId))

        assertEquals(154, orgRinasaker.size)
        assertEquals(orgRinasaker.size + 1, result.size)
    }

    @Test
    fun `henter rinaid fra saf og rina hvor begge er tomme`() {
        val euxCaseId = "12345678900"
        every { euxKlient.getRinasaker(eq(euxCaseId), null, null, null) } returns listOf<Rinasak>()

        val result = euxInnhentingService.getRinasaker(euxCaseId, emptyList())
        assertEquals(0, result.size)
    }

    @Test
    fun `Gitt at det finnes relasjon til gjenlevende i avdøds SEDer når avdøds SEDer hentes så hentes alle med samme avdodnr`() {
        val avdodFnr = "12345678910"
        val gjenlevendeFnr = "1234567890000"
        val rinasakid = "3893690"

        val rinaSaker = listOf(Rinasak(rinasakid, "P_BUC_02", Traits(), "", Properties(), "open"))
        every { euxKlient.getRinasaker(eq(avdodFnr), any(), any(), eq( "\"open\"")) } returns rinaSaker

        val actual = euxInnhentingService.getBucViewAvdod(avdodFnr, gjenlevendeFnr, rinasakid)

        assertEquals(1, actual.size)
        assertEquals(rinasakid, actual.first().euxCaseId)
        assertEquals(BucType.P_BUC_02, actual.first().buctype)
    }

    @Test
    fun `Henter buc og dokumentID` () {
        val euxCaseId = "123"
        val json = javaClass.getResource("/json/buc/P_BUC_02_4.2_P2100.json")!!.readText()
        val buc = mapJsonToAny(json, typeRefs<Buc>())

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
        assertThrows<Exception> {
            euxInnhentingService.hentBucOgDocumentIdAvdod(listOf("123"))
        }
    }

    @Test
    fun `Gitt det finnes et json dokument p2100 når avdod buc inneholder en dokumentid så hentes sed p2100 fra eux`() {
        val rinaid = "12344"
        val dokumentid = "3423432453255"
        val documentsItem = listOf(DocumentsItem(type = SedType.P2100, id = dokumentid, direction = "OUT"))
        val buc = Buc(processDefinitionName = "P_BUC_02", documents = documentsItem)
        val docs = listOf(BucOgDocumentAvdod(rinaid, buc, dokumentid))

        every { euxKlient.getSedOnBucByDocumentIdAsJson(rinaid, dokumentid) } returns SedType.P2100.name

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

        every {euxKlient.getSedOnBucByDocumentIdAsJson(rinaid, dokumentid)  } throws  HttpClientErrorException(HttpStatus.MULTI_STATUS)

        assertThrows<Exception> {
            euxInnhentingService.hentDocumentJsonAvdod(docs)
        }
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

    @Test
    fun `Gitt det ikke finnes en p2100 med gjenlevende Når det filtrers på gjenlevende felt og den gjenlevndefnr Så ingen buc`() {
        val rinaid = "123123"
        val gjenlevendeFnr = "12345678123123"
        val sedjson = javaClass.getResource("/json/nav/P2100-PinNO-NAV.json")!!.readText()
        val docs = listOf(BucOgDocumentAvdod(rinaid, Buc(id = rinaid, processDefinitionName = "P_BUC_02"), sedjson))

        val actual = euxInnhentingService.filterGyldigBucGjenlevendeAvdod(docs, gjenlevendeFnr)

        assertEquals(0, actual.size)
    }

    @Test
    fun `Sjekk om Pin node ikke finnes i SED logger feil`() {
        val manglerPinGjenlevende = """
            {
              "sed" : "P2000",
              "sedGVer" : "4",
              "sedVer" : "1",
              "nav" : {
                "bruker" : {
                  "person" : {
                    "pin" : [ {
                      "institusjonsnavn" : "NOINST002, NO INST002, NO",
                      "institusjonsid" : "NO:noinst002",
                      "identifikator" : "3123",
                      "land" : "NO"
                    } ],
                    "statsborgerskap" : [ {
                      "land" : "QX"
                    } ],
                    "etternavn" : "Testesen",
                    "fornavn" : "Test",
                    "kjoenn" : "M",
                    "foedselsdato" : "1988-07-12"
                  }
                },
                "krav" : {
                  "dato" : "2018-06-28"
                }
              },
              "pensjon" : {
                "kravDato" : {
                  "dato" : "2018-06-28"
                }
              }
            }
        """.trimIndent()
        val data = listOf(BucOgDocumentAvdod("2321", Buc(), manglerPinGjenlevende))
        val result = euxInnhentingService.filterGyldigBucGjenlevendeAvdod(data, "23123")
        assertEquals(0, result.size)

    }

    @Test
    fun `Sjekk om Pin node finnes i Sed returnerer BUC`() {

        val sedfilepath = "src/test/resources/json/nav/P2100-PinNO-NAV.json"
        val sedjson = String(Files.readAllBytes(Paths.get(sedfilepath)))

        val data = listOf(BucOgDocumentAvdod("23123", Buc(id = "2131", processDefinitionName = "P_BUC_02"), sedjson))
        val result = euxInnhentingService.filterGyldigBucGjenlevendeAvdod(data, "1234567890000")
        assertEquals(1, result.size)
    }

}