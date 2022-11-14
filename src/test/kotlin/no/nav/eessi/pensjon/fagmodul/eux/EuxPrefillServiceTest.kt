package no.nav.eessi.pensjon.fagmodul.eux

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.fagmodul.eux.EuxTestUtils.Companion.apiRequestWith
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.util.UriComponentsBuilder
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId

class EuxPrefillServiceTest {

    private lateinit var euxPrefillService: EuxPrefillService
    private lateinit var euxinnhentingService: EuxInnhentingService

    @MockK(relaxed = true)
    lateinit var euxKlient: EuxKlient

    var statistikkHandler: StatistikkHandler = mockk()

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        euxPrefillService = EuxPrefillService(euxKlient, statistikkHandler)
        euxinnhentingService = EuxInnhentingService("q2", euxKlient)
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
        val json = javaClass.getResource("/json/nav/P6000-NAV.json").readText()
        assertTrue(validateJson(json))

        every { euxKlient.getSedOnBucByDocumentIdAsJson(any(), any()) } returns json

        val result = euxinnhentingService.getSedOnBucByDocumentId("12345678900", "0bb1ad15987741f1bbf45eba4f955e80")
        assertEquals(SedType.P6000, result.type)
        result as P6000

        assertEquals("234", result.p6000Pensjon?.vedtak?.firstOrNull()?.delvisstans?.utbetaling?.beloepBrutto)
        assertEquals("BE", result.p6000Pensjon?.tilleggsinformasjon?.annen?.institusjonsadresse?.land)

    }

    @Test
    fun `Calling eux-rina-api to create BucSedAndView gets one BUC per rinaid`() {
        val rinasakerJson = javaClass.getResource("/json/rinasaker/rinasaker_34567890111.json").readText()
        val rinasaker = mapJsonToAny(rinasakerJson, typeRefs<List<Rinasak>>())

        val bucJson = File("src/test/resources/json/buc/buc-158123_2_v4.1.json").readText()

        every { euxKlient.getBucJsonAsNavIdent(any()) } returns bucJson

        val result = euxinnhentingService.getBucAndSedView(rinasaker.map{ it.id!! }.toList())

        assertEquals(rinasaker.size, result.size)
    }

    @Test
    fun `Calling eux-rina-api to create BucSedAndView returns a result`() {
        val rinasakid = "158123"

        val bucJson = javaClass.getResource("/json/buc/buc-158123_2_v4.1.json").readText()

        every { euxKlient.getBucJsonAsNavIdent(any()) } returns bucJson

        val result = euxinnhentingService.getBucAndSedView(listOf(rinasakid))

        assertEquals(1, result.size)

        val firstBucAndSedView = result.first()
        assertEquals("158123", firstBucAndSedView.caseId)
    }

    @Test
    fun callingEuxServiceForSinglemenuUI_AllOK() {
        val bucStr = javaClass.getResource("/json/buc/buc-158123_2_v4.1.json").readText()
        assertTrue(validateJson(bucStr))

        every { euxKlient.getBucJsonAsNavIdent(any()) } returns bucStr

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
                Rinasak("723", "P_BUC_01", null, "PO", null, "open", null),
                Rinasak("2123", "P_BUC_03", null, "PO", null, "open", null),
                Rinasak("423", "H_BUC_01", null, "PO", null, "archived", null),
                Rinasak("234", "P_BUC_06", null, "PO", null, "closed", null),
                Rinasak("8423", "P_BUC_07", null, "PO", null, "archived", null)
        )

        val result = euxinnhentingService.getFilteredArchivedaRinasakerSak(dummyList)
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

        val result = euxinnhentingService.getFilteredArchivedaRinasakerSak(dummyList)
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

        val result = euxinnhentingService.getFilteredArchivedaRinasakerSak(dummyList)
        assertEquals(2, result.size)
        assertEquals("723", result.first().id)
        assertEquals("8223", result.last().id)
    }

    @Test
    fun callingEuxServiceListOfRinasaker_Ok() {
        val jsonRinasaker = javaClass.getResource("/json/rinasaker/rinasaker_12345678901.json").readText()
        val orgRinasaker = mapJsonToAny(jsonRinasaker, typeRefs<List<Rinasak>>())

        every { euxKlient.getRinasaker(eq("12345678900"), status = "\"open\"") } returns orgRinasaker

        val jsonEnRinasak = javaClass.getResource("/json/rinasaker/rinasaker_ensak.json").readText()

        val enSak = mapJsonToAny(jsonEnRinasak, typeRefs<List<Rinasak>>())

        every { euxKlient.getRinasaker(euxCaseId = "8877665511", status = "\"open\"") } returns enSak

        val result = euxinnhentingService.getRinasaker("12345678900", listOf("8877665511"))

        assertEquals(154, orgRinasaker.size)
        assertEquals(orgRinasaker.size + 1, result.size)
    }

    @Test
    fun `henter rinaid fra saf og rina hvor begge er tomme`() {

        every { euxKlient.getRinasaker(eq("12345678900"), null, null, null) } returns listOf<Rinasak>()
        val result = euxinnhentingService.getRinasaker("12345678900", emptyList())

        assertEquals(0, result.size)
    }

    @Test
    fun `Gitt at det finnes relasjon til gjenlevende i avdøds SEDer når avdøds SEDer hentes så hentes alle med samme avdodnr`() {
        val avdodFnr = "12345678910"
        val gjenlevendeFnr = "1234567890000"
        val rinasakid = "3893690"

        val rinaSaker = listOf(Rinasak(rinasakid, "P_BUC_02", Traits(), "", Properties(), "open"))
        every { euxKlient.getRinasaker(eq(avdodFnr), any(), any(), eq( "\"open\"")) } returns rinaSaker

        val actual = euxinnhentingService.getBucViewAvdod(avdodFnr, gjenlevendeFnr, rinasakid)

        assertEquals(1, actual.size)
        assertEquals(rinasakid, actual.first().euxCaseId)
        assertEquals(BucType.P_BUC_02, actual.first().buctype)
    }

    @Test
    fun `Henter buc og dokumentID` () {
        val json = javaClass.getResource("/json/buc/P_BUC_02_4.2_P2100.json").readText()

        val buc = mapJsonToAny(json, typeRefs<Buc>())

        every { euxKlient.getBucJsonAsNavIdent(any()) } returns json

        val actual = euxinnhentingService.hentBucOgDocumentIdAvdod(listOf("123"))

        assert(actual[0].rinaidAvdod == "123")
        assert(actual[0].buc.id == buc.id)
        assert(actual[0].buc.internationalId == "e94e1be2daff414f8a49c3149ec00e66")

    }

    @Test
    fun `Henter flere buc og dokumentID fra avdod` () {
        val json = javaClass.getResource("/json/buc/P_BUC_02_4.2_P2100.json").readText()

        every { euxKlient.getBucJsonAsNavIdent(any()) } returns json andThen json

        val actual = euxinnhentingService.hentBucOgDocumentIdAvdod(listOf("123","321"))
        assertEquals(2, actual.size)
    }

    @Test
    fun `Henter buc og dokumentID feiler ved henting av buc fra eux`() {

        every { euxKlient.getBucJsonAsNavIdent(any()) } throws HttpClientErrorException(HttpStatus.UNAUTHORIZED)
        assertThrows<Exception> {
            euxinnhentingService.hentBucOgDocumentIdAvdod(listOf("123"))
        }
    }

    @Test
    fun `Gitt det finnes et json dokument p2100 når avdød buc inneholder en dokumentid så hentes sed p2100 fra eux`() {
        val rinaid = "12344"
        val dokumentid = "3423432453255"

        val documentsItem = listOf(DocumentsItem(type = SedType.P2100, id = dokumentid, direction = "OUT"))
        val buc = Buc(processDefinitionName = "P_BUC_02", documents = documentsItem)

        val docs = listOf(BucOgDocumentAvdod(rinaid, buc, dokumentid))

        every { euxKlient.getSedOnBucByDocumentIdAsJson(rinaid, dokumentid) } returns "P2100"

        val actual = euxinnhentingService.hentDocumentJsonAvdod(docs)

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
        val sedjson = javaClass.getResource("/json/nav/P2100-PinNO-NAV.json").readText()

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

    @Test
    fun `check apiRequest for prefill X010 contains X009 payload`() {
        val x009Json = javaClass.getResource("/json/nav/X009-NAV.json").readText()
        every { euxKlient.getSedOnBucByDocumentIdAsJson(any(), any()) } returns x009Json

        val apiRequest = apiRequestWith("1000000", emptyList(), buc = "P_BUC_01", sed = "X010")

        val json = euxinnhentingService.checkForX010AndAddX009(apiRequest, "20000000").toJson()

        val payload = """
                  "payload" : "{\n  \"sed\" : \"X009\",\n  \"nav\" : {\n    \"sak\" : {\n      \"kontekst\" : {\n        \"bruker\" : {\n          \"mor\" : null,\n          \"far\" : null,\n          \"person\" : {\n            \"pin\" : null,\n            \"pinland\" : null,\n            \"statsborgerskap\" : null,\n            \"etternavn\" : \"æøå\",\n            \"etternavnvedfoedsel\" : null,\n            \"fornavn\" : \"æøå\",\n            \"fornavnvedfoedsel\" : null,\n            \"tidligerefornavn\" : null,\n            \"tidligereetternavn\" : null,\n            \"kjoenn\" : \"M\",\n            \"foedested\" : null,\n            \"foedselsdato\" : \"æøå\",\n            \"sivilstand\" : null,\n            \"relasjontilavdod\" : null,\n            \"rolle\" : null\n          },\n          \"adresse\" : null,\n          \"arbeidsforhold\" : null,\n          \"bank\" : null\n        },\n        \"refusjonskrav\" : {\n          \"antallkrav\" : \"æøå\",\n          \"id\" : \"æøå\"\n        },\n        \"arbeidsgiver\" : {\n          \"identifikator\" : [ {\n            \"id\" : \"æøå\",\n            \"type\" : \"registrering\"\n          } ],\n          \"adresse\" : {\n            \"gate\" : \"æøå\",\n            \"bygning\" : \"æøå\",\n            \"by\" : \"æøå\",\n            \"postnummer\" : \"æøå\",\n            \"postkode\" : null,\n            \"region\" : \"æøå\",\n            \"land\" : \"NO\",\n            \"kontaktpersonadresse\" : null,\n            \"datoforadresseendring\" : null,\n            \"postadresse\" : null,\n            \"startdato\" : null,\n            \"type\" : null,\n            \"annen\" : null\n          },\n          \"navn\" : \"æøå\"\n        }\n      },\n      \"leggtilinstitusjon\" : null,\n      \"paaminnelse\" : {\n        \"svar\" : null,\n        \"sende\" : [ {\n          \"type\" : \"dokument\",\n          \"detaljer\" : \"æøå\"\n        } ]\n      }\n    }\n  },\n  \"sedGVer\" : \"4\",\n  \"sedVer\" : \"2\",\n  \"pensjon\" : null\n}",
        """.trimIndent()

       assert(json.contains(payload))
    }
}
