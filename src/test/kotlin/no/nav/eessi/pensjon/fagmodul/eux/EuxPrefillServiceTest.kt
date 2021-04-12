package no.nav.eessi.pensjon.fagmodul.eux

import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Properties
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Rinasak
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Traits
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.services.statistikk.StatistikkHandler
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.typeRefs
import no.nav.eessi.pensjon.utils.validateJson
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.util.UriComponentsBuilder
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId

@ExtendWith(MockitoExtension::class)
class EuxPrefillServiceTest {

    private lateinit var euxPrefillService: EuxPrefillService
    private lateinit var euxinnhentingService: EuxInnhentingService

    @Mock
    private lateinit var euxKlient: EuxKlient

    @Mock
    private lateinit var statistikkHandler: StatistikkHandler


    @BeforeEach
    fun setup() {
        euxPrefillService = EuxPrefillService(euxKlient, statistikkHandler)
        euxinnhentingService = EuxInnhentingService(euxKlient)

    }

    @Test
    fun `Opprett Uri component path`() {
        val path = "/type/{RinaSakId}/sed"
        val uriParams = mapOf("RinaSakId" to "12345")
        val builder = UriComponentsBuilder.fromUriString(path)
                .queryParam("KorrelasjonsId", "c0b0c068-4f79-48fe-a640-b9a23bf7c920")
                .buildAndExpand(uriParams)
        val str = builder.toUriString()
        assertEquals("/type/12345/sed?KorrelasjonsId=c0b0c068-4f79-48fe-a640-b9a23bf7c920", str)
    }


    @Test
    fun `forventer et korrekt navsed P6000 ved kall til getSedOnBucByDocumentId`() {
        val filepath = "src/test/resources/json/nav/P6000-NAV.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))

        whenever(euxKlient.getSedOnBucByDocumentIdAsJson(any(), any())).thenReturn(json)

        val result = euxinnhentingService.getSedOnBucByDocumentId("12345678900", "0bb1ad15987741f1bbf45eba4f955e80")

        assertEquals(SedType.P6000, result.type)
    }

    @Test
    fun `Calling eux-rina-api to create BucSedAndView gets one BUC per rinaid`() {
        val rinasakerJson = File("src/test/resources/json/rinasaker/rinasaker_34567890111.json").readText()
        val rinasaker = mapJsonToAny(rinasakerJson, typeRefs<List<Rinasak>>())

        val bucJson = File("src/test/resources/json/buc/buc-158123_2_v4.1.json").readText()

        doReturn(bucJson)
                .whenever(euxKlient)
                .getBucJson(any())

        val result = euxinnhentingService.getBucAndSedView(rinasaker.map{ it.id!! }.toList())

        assertEquals(rinasaker.size, result.size)
    }

    @Test
    fun `Calling eux-rina-api to create BucSedAndView returns a result`() {
        val rinasakid = "158123"

        val bucJson = javaClass.getResource("/json/buc/buc-158123_2_v4.1.json").readText()

        doReturn(bucJson)
                .whenever(euxKlient)
                .getBucJson(eq(rinasakid))

        val result = euxinnhentingService.getBucAndSedView(listOf(rinasakid))

        assertEquals(1, result.size)

        val firstBucAndSedView = result.first()
        assertEquals("158123", firstBucAndSedView.caseId)
    }

    @Test
    fun callingEuxServiceForSinglemenuUI_AllOK() {
        val bucjson = "src/test/resources/json/buc/buc-158123_2_v4.1.json"
        val bucStr = String(Files.readAllBytes(Paths.get(bucjson)))
        assertTrue(validateJson(bucStr))

        doReturn(bucStr)
                .whenever(euxKlient)
                .getBucJson(any())

        val firstJson = euxinnhentingService.getSingleBucAndSedView("158123")

        assertEquals("158123", firstJson.caseId)
        var lastUpdate: Long = 0
        firstJson.lastUpdate?.let { lastUpdate = it }
        assertEquals("2019-05-20T16:35:34",  Instant.ofEpochMilli(lastUpdate).atZone(ZoneId.systemDefault()).toLocalDateTime().toString())
        assertEquals(18, firstJson.seds?.size)
    }

    @Test
    fun `Test filter list av rinasak ta bort elementer av archived`() {
        val dummyList = listOf(
                Rinasak("723","P_BUC_01",null,"PO",null,"open"),
                Rinasak("2123","P_BUC_03",null,"PO",null,"open"),
                Rinasak("423","H_BUC_01",null,"PO",null,"archived"),
                Rinasak("234","P_BUC_06",null,"PO",null,"closed"),
                Rinasak("8423","P_BUC_07",null,"PO",null,"archived")
        )

        val result = euxinnhentingService.getFilteredArchivedaRinasaker(dummyList)
        assertEquals(3, result.size)
        assertEquals("2123", result.first())
    }

    @Test
    fun `Test filter list av rinasak ta bort elementer av archived og ugyldige buc`() {
        val dummyList = listOf(
                Rinasak("723","FP_BUC_01",null,"PO",null,"open"),
                Rinasak("2123","H_BUC_02",null,"PO",null,"open"),
                Rinasak("423","P_BUC_01",null,"PO",null,"archived"),
                Rinasak("234","FF_BUC_01",null,"PO",null,"closed"),
                Rinasak("8423","FF_BUC_01",null,"PO",null,"archived"),
                Rinasak("8223","H_BUC_07",null,"PO",null,"open")
        )

        val result = euxinnhentingService.getFilteredArchivedaRinasaker(dummyList)
        assertEquals(1, result.size)
        assertEquals("8223", result.first())
    }

    @Test
    fun `Test filter list av rinasak ta bort elementer av archived og ugyldige buc samt spesielle a og b bucer`() {
        val dummyList = listOf(
                Rinasak("723","M_BUC_03a",null,"PO",null,"open"),
                Rinasak("2123","H_BUC_02",null,"PO",null,"open"),
                Rinasak("423","P_BUC_01",null,"PO",null,"archived"),
                Rinasak("234","FF_BUC_01",null,"PO",null,"closed"),
                Rinasak("8423","M_BUC_02",null,"PO",null,"archived"),
                Rinasak("8223","M_BUC_03b",null,"PO",null,"open")
        )

        val result = euxinnhentingService.getFilteredArchivedaRinasaker(dummyList)
        assertEquals(2, result.size)
        assertEquals("723", result.first())
        assertEquals("8223", result.last())
    }

    @Test
    fun callingEuxServiceListOfRinasaker_Ok() {
        val filepathRinasaker = "src/test/resources/json/rinasaker/rinasaker_12345678901.json"
        val jsonRinasaker = String(Files.readAllBytes(Paths.get(filepathRinasaker)))
        assertTrue(validateJson(jsonRinasaker))
        val orgRinasaker = mapJsonToAny(jsonRinasaker, typeRefs<List<Rinasak>>())

        doReturn(orgRinasaker).whenever(euxKlient).getRinasaker(eq("12345678900"), eq(null), eq(null), eq(null))

        val filepathEnRinasak = "src/test/resources/json/rinasaker/rinasaker_ensak.json"
        val jsonEnRinasak = String(Files.readAllBytes(Paths.get(filepathEnRinasak)))
        assertTrue(validateJson(jsonEnRinasak))
        val enSak = mapJsonToAny(jsonEnRinasak, typeRefs<List<Rinasak>>())

        doReturn(enSak).whenever(euxKlient).getRinasaker(eq(null), eq("8877665511"), eq(null), eq(null))

        val result = euxinnhentingService.getRinasaker("12345678900", "1111111111111", listOf("8877665511"))

        assertEquals(154, orgRinasaker.size)
        assertEquals(orgRinasaker.size + 1, result.size)
    }

    @Test
    fun `henter rinaid fra saf og rina hvor begge er tomme`() {

        doReturn( listOf<Rinasak>()) .whenever(euxKlient).getRinasaker(eq("12345678900"), eq(null), eq(null), eq(null))

        val result = euxinnhentingService.getRinasaker("12345678900", "1111111111111", emptyList())

        assertEquals(0, result.size)
    }

    @Test
    fun `Gitt at det finnes relasjon til gjenlevende i avdøds SEDer når avdøds SEDer hentes så filtreres den gjenlevendes BUCer in`() {
        val avdodFnr = "12345678910"
        val gjenlevendeFnr = "1234567890000"
        val rinasakid = "3893690"

        val rinaSaker = listOf<Rinasak>(Rinasak(rinasakid,"P_BUC_02", Traits(), "", Properties(), "open"))
        doReturn(rinaSaker).whenever(euxKlient).getRinasaker(avdodFnr, null, "P_BUC_02", "\"open\"")

        val bucfilepath = "src/test/resources/json/buc/P_BUC_02_4.2_P2100.json"
        val json = String(Files.readAllBytes(Paths.get(bucfilepath)))

        doReturn(json).whenever(euxKlient).getBucJson(any())


        val sedfilepath = "src/test/resources/json/nav/P2100-PinNO-NAV.json"
        val sedjson = String(Files.readAllBytes(Paths.get(sedfilepath)))

        doReturn(sedjson).whenever(euxKlient).getSedOnBucByDocumentIdAsJson(eq(rinasakid), any())

        val actual = euxinnhentingService.getBucAndSedViewAvdod(gjenlevendeFnr, avdodFnr)

        assertEquals(1, actual.size)
        assertEquals(rinasakid, actual.first().caseId)
        assertEquals("P_BUC_02", actual.first().type)
    }

    @Test
    fun `Gitt at det ikke finnes relasjon til gjenlevende i avdøds SEDer når avdøds SEDer hentes så filtreres den gjenlevendes BUCer bort`() {
        val avdodFnr = "12345678910"
        val gjenlevendeFnr = "1345134531234"
        val rinasakid = "3893690"

        val rinaSaker = listOf<Rinasak>(Rinasak(rinasakid, "P_BUC_02", Traits(), "", Properties(), "open"))
        doReturn(rinaSaker).whenever(euxKlient).getRinasaker(avdodFnr, null, "P_BUC_02", "\"open\"")

        val bucfilepath = "src/test/resources/json/buc/P_BUC_02_4.2_P2100.json"
        val json = String(Files.readAllBytes(Paths.get(bucfilepath)))

        doReturn(json).whenever(euxKlient).getBucJson(any())


        val sedfilepath = "src/test/resources/json/nav/P2100-PinNO-NAV.json"
        val sedjson = String(Files.readAllBytes(Paths.get(sedfilepath)))

        doReturn(sedjson).whenever(euxKlient).getSedOnBucByDocumentIdAsJson(eq(rinasakid), any())

        val actual = euxinnhentingService.getBucAndSedViewAvdod(gjenlevendeFnr, avdodFnr)
        assertEquals(0, actual.size)
    }

    @Test
    fun `Gitt at det finnes relasjon til gjenlevende i avdøds SEDer når avdøds SEDer hentes så kastes det en error ved henting av seddocument fra eux`() {
        val avdodFnr = "12345678910"
        val gjenlevendeFnr = "1234567890000"
        val rinasakid = "3893690"

        val rinaSaker = listOf<Rinasak>(Rinasak(rinasakid,"P_BUC_02", Traits(), "", Properties(), "open"))
        doReturn(rinaSaker).whenever(euxKlient).getRinasaker(avdodFnr, null, "P_BUC_02", "\"open\"")

        val bucfilepath = "src/test/resources/json/buc/P_BUC_02_4.2_P2100.json"
        val json = String(Files.readAllBytes(Paths.get(bucfilepath)))

        doReturn(json).whenever(euxKlient).getBucJson(any())

        doThrow(HttpClientErrorException(HttpStatus.BAD_GATEWAY, "bad error")).whenever(euxKlient).getSedOnBucByDocumentIdAsJson(any(), any())

        assertThrows<Exception> {
            euxinnhentingService.getBucAndSedViewAvdod(gjenlevendeFnr, avdodFnr)
        }

    }

    @Test
    fun `Henter buc og dokumentID` () {
        val bucfilepath = "src/test/resources/json/buc/P_BUC_02_4.2_P2100.json"
        val json = String(Files.readAllBytes(Paths.get(bucfilepath)))
        val buc = mapJsonToAny(json, typeRefs<Buc>())

        doReturn(json).whenever(euxKlient).getBucJson(any())

        var actual = euxinnhentingService.hentBucOgDocumentIdAvdod(listOf("123"))

        assert(actual[0].rinaidAvdod == "123")
        assert(actual[0].buc.id == buc.id)
    }

    @Test
    fun `Henter flere buc og dokumentID fra avdod` () {
        val bucfilepath = "src/test/resources/json/buc/P_BUC_02_4.2_P2100.json"
        val json = String(Files.readAllBytes(Paths.get(bucfilepath)))

        doReturn(json)
        .doReturn(json)
         .whenever(euxKlient).getBucJson(any())

        var actual = euxinnhentingService.hentBucOgDocumentIdAvdod(listOf("123","321"))
        assertEquals(2, actual.size)
    }

    @Test
    fun `Henter buc og dokumentID feiler ved henting av buc fra eux` () {
        doThrow(HttpClientErrorException::class).whenever(euxKlient).getBucJson(any())
        assertThrows<Exception> {
            euxinnhentingService.hentBucOgDocumentIdAvdod(listOf("123"))
        }
      }

    @Test
    fun `Gitt det finnes et json dokument p2100 når avdød buc inneholder en dokumentid så hentes sed p2100 fra eux`() {
        val rinaid = "12344"
        val dokumentid = "3423432453255"

        val documentsItem = listOf(DocumentsItem(type = SedType.P2100, id = dokumentid))
        val buc = Buc(processDefinitionName = "P_BUC_02", documents = documentsItem)

        val docs = listOf(BucOgDocumentAvdod(rinaid, buc, dokumentid))

        doReturn("P2100").whenever(euxKlient).getSedOnBucByDocumentIdAsJson(rinaid, dokumentid)

        val actual = euxinnhentingService.hentDocumentJsonAvdod(docs)

        assertEquals(1, actual.size)
        assertEquals(SedType.P2100.name, actual.single().dokumentJson)
        assertEquals(rinaid, actual.single().rinaidAvdod)
  }

    @Test
    fun `Gitt det finnes et json dokument p2100 når avdød buc inneholder en dokumentid så feiler det ved hentig fra eux`() {
        val rinaid = "12344"
        val dokumentid = "3423432453255"

        val documentsItem = listOf(DocumentsItem(type = SedType.P2100, id = dokumentid))
        val buc = Buc(processDefinitionName = "P_BUC_02", documents = documentsItem)

        val docs = listOf(BucOgDocumentAvdod(rinaid, buc, dokumentid))

        doThrow(HttpClientErrorException::class).whenever(euxKlient).getSedOnBucByDocumentIdAsJson(rinaid, dokumentid)

        assertThrows<Exception> {
            euxinnhentingService.hentDocumentJsonAvdod(docs)
        }

    }

    @Test
    fun `Gitt det finnes en p2100 med gjenlevende Når det filtrers på gjenlevende felt og den gjenlevndefnr Så gis det gyldige buc`() {
        val rinaid = "123123"
        val gjenlevendeFnr = "1234567890000"
        val sedjson = javaClass.getResource("/json/nav/P2100-PinNO-NAV.json").readText()

        val docs = listOf(BucOgDocumentAvdod(rinaid, Buc(id = rinaid, processDefinitionName = "P_BUC_02"), sedjson))

        val actual = euxinnhentingService.filterGyldigBucGjenlevendeAvdod(docs, gjenlevendeFnr)

        assertEquals(1, actual.size)

    }

    @Test
    fun `Gitt det ikke finnes en p2100 med gjenlevende Når det filtrers på gjenlevende felt og den gjenlevndefnr Så ingen buc`() {
        val rinaid = "123123"
        val gjenlevendeFnr = "12345678123123"
        val sedfilepath = "src/test/resources/json/nav/P2100-PinNO-NAV.json"
        val sedjson = String(Files.readAllBytes(Paths.get(sedfilepath)))

        val docs = listOf(BucOgDocumentAvdod(rinaid, Buc(id = rinaid, processDefinitionName = "P_BUC_02"), sedjson))

        val actual = euxinnhentingService.filterGyldigBucGjenlevendeAvdod(docs, gjenlevendeFnr)

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
        val data = listOf<BucOgDocumentAvdod>(BucOgDocumentAvdod("2321", Buc(), manglerPinGjenlevende))
        val result = euxinnhentingService.filterGyldigBucGjenlevendeAvdod(data, "23123")
        assertEquals(0, result.size)

    }

    @Test
    fun `Sjekk om Pin node finnes i Sed returnerer BUC`() {

        val sedfilepath = "src/test/resources/json/nav/P2100-PinNO-NAV.json"
        val sedjson = String(Files.readAllBytes(Paths.get(sedfilepath)))

        val data = listOf<BucOgDocumentAvdod>(BucOgDocumentAvdod("23123", Buc(id = "2131", processDefinitionName = "P_BUC_02"), sedjson))
        val result = euxinnhentingService.filterGyldigBucGjenlevendeAvdod(data, "1234567890000")
        assertEquals(1, result.size)

    }

    @Test
    fun `Sjekk P_BUC_02 etter gjennlevende person kun et resultat skal vises`() {
        val sedjson = javaClass.getResource("/json/nav/P2100-PinNO-NAV.json").readText()
        val sedDKjson = javaClass.getResource("/json/nav/P2100-PinDK-NAV.json").readText()

        val gjenlevendeFnr = "1234567890000"
        val avdodFnr = "01010100001"

        // 02
        val euxCaseId  = "1"
        val rinaSakerBuc02 = listOf(dummyRinasak(euxCaseId, "P_BUC_02"), dummyRinasak("10", "P_BUC_02"))
        doReturn(rinaSakerBuc02).whenever(euxKlient).getRinasaker(avdodFnr, null, "P_BUC_02", "\"open\"")


        val docItems = listOf(DocumentsItem(id = "1", type = SedType.P2100), DocumentsItem(id = "2", type = SedType.P4000))
        val buc = Buc(id = "1", processDefinitionName = "P_BUC_02", documents = docItems)
        doReturn(buc.toJson()).whenever(euxKlient).getBucJson(euxCaseId)

        val docDKItems = listOf(DocumentsItem(id = "20", type = SedType.P2100), DocumentsItem(id = "40", type = SedType.P4000))
        val DKbuc = Buc(id = "10", processDefinitionName = "P_BUC_02", documents = docDKItems)
        doReturn(DKbuc.toJson()).whenever(euxKlient).getBucJson("10")


        doReturn(sedjson).whenever(euxKlient).getSedOnBucByDocumentIdAsJson("1", "1")
        doReturn(sedDKjson).whenever(euxKlient).getSedOnBucByDocumentIdAsJson("10", "20")


        // 05
        doReturn(emptyList<Rinasak>()).whenever(euxKlient).getRinasaker(avdodFnr, null, "P_BUC_05", "\"open\"")


        val result = euxinnhentingService.getBucAndSedViewAvdod(gjenlevendeFnr, avdodFnr)

        assertEquals(1, result.size)
        assertFalse(result.isEmpty())
        assertEquals("P_BUC_02", result[0].type)
        assertEquals(gjenlevendeFnr, result[0].subject?.gjenlevende?.fnr)
        assertEquals(avdodFnr, result[0].subject?.avdod?.fnr)
    }

    @Test
    fun `Sjekk P_BUC_02 etter gjennlevende og P_BUC_05 liste med 2 resultat skal vises`() {
        val sedjson = javaClass.getResource("/json/nav/P2100-PinNO-NAV.json").readText()
        val sedDKjson = javaClass.getResource("/json/nav/P2100-PinDK-NAV.json").readText()
        val sedP8000json = javaClass.getResource("/json/nav/P8000_NO-NAV.json").readText()
        val sedP8000DKjson = javaClass.getResource("/json/nav/P8000_DK-NAV.json").readText()

        val gjenlevendeFnr = "1234567890000"
        val avdodFnr = "01010100001"

        // 02
        val euxCaseId  = "1"
        val rinaSakerBuc02 = listOf(dummyRinasak(euxCaseId, "P_BUC_02"), dummyRinasak("10", "P_BUC_02"))
        doReturn( rinaSakerBuc02).whenever(euxKlient).getRinasaker(avdodFnr, null, "P_BUC_02", "\"open\"")


        val docItems = listOf(DocumentsItem(id = "1", type = SedType.P2100), DocumentsItem(id = "2", type = SedType.P4000))
        val buc = Buc(id = "1", processDefinitionName = "P_BUC_02", documents = docItems)
        doReturn(buc.toJson()).whenever(euxKlient).getBucJson(euxCaseId)

        val docDKItems = listOf(DocumentsItem(id = "20", type = SedType.P2100), DocumentsItem(id = "40", type = SedType.P4000))
        val DKbuc = Buc(id = "10", processDefinitionName = "P_BUC_02", documents = docDKItems)
        doReturn(DKbuc.toJson()).whenever(euxKlient).getBucJson("10")

        //sed no P2100
        doReturn(sedjson).whenever(euxKlient).getSedOnBucByDocumentIdAsJson("1", "1")
        //sed dk P2100
        doReturn(sedDKjson).whenever(euxKlient).getSedOnBucByDocumentIdAsJson("10", "20")


        // 05
        val rinaSakerBuc05 = listOf(dummyRinasak("100", "P_BUC_05"),dummyRinasak("200", "P_BUC_05"))
        doReturn(rinaSakerBuc05).whenever(euxKlient).getRinasaker(avdodFnr, null, "P_BUC_05", "\"open\"")

        //buc05no
        val docP8000Items = listOf(DocumentsItem(id = "2000", type = SedType.P8000), DocumentsItem(id = "4000", type = SedType.P6000))
        val buc05 = Buc(id = "100", processDefinitionName = "P_BUC_05", documents = docP8000Items)

        //buc05dk
        val docP8000DKItems = listOf(DocumentsItem(id = "2200", type = SedType.P8000), DocumentsItem(id = "4200", type = SedType.P6000))
        val buc05DK = Buc(id = "200", processDefinitionName = "P_BUC_05", documents = docP8000DKItems)

        doReturn(buc05.toJson()).whenever(euxKlient).getBucJson("100")
        doReturn(buc05DK.toJson()).whenever(euxKlient).getBucJson("200")

        //sed no P8000
        doReturn(sedP8000json).whenever(euxKlient).getSedOnBucByDocumentIdAsJson("100", "2000")
        //sed dk P8000
        doReturn(sedP8000DKjson).whenever(euxKlient).getSedOnBucByDocumentIdAsJson("200", "2200")

        val result = euxinnhentingService.getBucAndSedViewAvdod(gjenlevendeFnr, avdodFnr).sortedBy { it.caseId }

        assertEquals(2, result.size)
        assertFalse(result.isEmpty())
        assertEquals("P_BUC_02", result[0].type)
        assertEquals("P_BUC_05", result[1].type)
        assertEquals(gjenlevendeFnr, result[0].subject?.gjenlevende?.fnr)
        assertEquals(gjenlevendeFnr, result[1].subject?.gjenlevende?.fnr)
        assertEquals(avdodFnr, result[0].subject?.avdod?.fnr)
        assertEquals(avdodFnr, result[1].subject?.avdod?.fnr)
    }




    private fun dummyRinasak(rinaSakId: String, bucType: String): Rinasak {
        return Rinasak(rinaSakId, bucType, Traits(), "", Properties(), "open")
    }

    @Test
    fun `update SED Version from old version to new version`() {
        val sed = SED(SedType.P2000)
        val bucVersion = "v4.2"

        euxPrefillService.updateSEDVersion(sed, bucVersion)
        assertEquals(bucVersion, "v${sed.sedGVer}.${sed.sedVer}")
    }

    @Test
    fun `update SED Version from old version to same version`() {
        val sed = SED(SedType.P2000)
        val bucVersion = "v4.1"

        euxPrefillService.updateSEDVersion(sed, bucVersion)
        assertEquals(bucVersion, "v${sed.sedGVer}.${sed.sedVer}")
    }


    @Test
    fun `update SED Version from old version to unknown new version`() {
        val sed = SED(SedType.P2000)
        val bucVersion = "v4.4"

        euxPrefillService.updateSEDVersion(sed, bucVersion)
        assertEquals("v4.1", "v${sed.sedGVer}.${sed.sedVer}")
    }

}
