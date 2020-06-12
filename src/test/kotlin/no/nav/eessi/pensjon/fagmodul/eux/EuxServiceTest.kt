package no.nav.eessi.pensjon.fagmodul.eux

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.Rinasak
import no.nav.eessi.pensjon.fagmodul.sedmodel.PinItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.utils.*
import no.nav.eessi.pensjon.vedlegg.client.SafClient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.web.util.UriComponentsBuilder
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId

@ExtendWith(MockitoExtension::class)
class EuxServiceTest {

    private lateinit var service: EuxService

    @Mock
    private lateinit var euxKlient: EuxKlient

    @Mock
    private lateinit var safClient: SafClient


    @BeforeEach
    fun setup() {
        service = EuxService(euxKlient, safClient)
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

        val orgsed = SED.fromJson(json)

        whenever(euxKlient.getSedOnBucByDocumentIdAsJson(any(), any())).thenReturn(json)

        val result = service.getSedOnBucByDocumentId("12345678900", "0bb1ad15987741f1bbf45eba4f955e80")

        assertEquals(orgsed, result)
        assertEquals("P6000", result.sed)

    }

    @Test
    fun hentYtelseKravtypeTesterPaaP15000Alderpensjon() {
        val filepath = "src/test/resources/json/nav/P15000-NAV.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))

        assertTrue(validateJson(json))

        whenever(euxKlient.getSedOnBucByDocumentIdAsJson(any(), any())).thenReturn(json)

        val result = service.hentFnrOgYtelseKravtype("1234567890","100001000010000")
        assertEquals("21712", result.fnr)
        assertEquals("01", result.krav?.type)
        assertEquals("2019-02-01", result.krav?.dato)
    }

    @Test
    fun hentYtelseKravtypeTesterPaaP15000Gjennlevende() {
        val filepath = "src/test/resources/json/nav/P15000Gjennlevende-NAV.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))

        whenever(euxKlient.getSedOnBucByDocumentIdAsJson(any(), any())).thenReturn(json)

        val orgsed = mapJsonToAny(json, typeRefs<SED>())
        JSONAssert.assertEquals(json, orgsed.toJson(), false)

        val result = service.hentFnrOgYtelseKravtype("1234567890","100001000010000")
        assertEquals("32712", result.fnr)
        assertEquals("02", result.krav?.type)
        assertEquals("2019-02-01", result.krav?.dato)

    }

    @Test
    fun feilerVedHentingAvP2100GrunnetManglendeMapping() {
        val filepath = "src/test/resources/json/nav/P2100-NAV-unfin.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))

        whenever(euxKlient.getSedOnBucByDocumentIdAsJson(any(), any())).thenReturn(json)

        assertThrows<JsonIllegalArgumentException> {
            service.hentFnrOgYtelseKravtype("1234567890","100001000010000")
        }
    }

    @Test
    fun hentYtelseKravtypeTesterPaaP15000FeilerVedUgyldigSED() {
        val filepath = "src/test/resources/json/nav/P9000-NAV.json"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))

        whenever(euxKlient.getSedOnBucByDocumentIdAsJson(any(), any())).thenReturn(json)

        assertThrows<SedDokumentIkkeGyldigException> {
            service.hentFnrOgYtelseKravtype("1234567890", "100001000010000")
        }
    }

    @Test
    fun `Calling eux-rina-api to create BucSedAndView for Frontend all OK excpect valid json`() {
        val rinasakerjson = "src/test/resources/json/rinasaker/rinasaker_34567890111.json"
        val rinasakStr = String(Files.readAllBytes(Paths.get(rinasakerjson)))
        assertTrue(validateJson(rinasakStr))

        val orgRinasaker = mapJsonToAny(rinasakStr, typeRefs<List<Rinasak>>())

        val bucjson = "src/test/resources/json/buc/buc-158123_2_v4.1.json"
        val bucStr = String(Files.readAllBytes(Paths.get(bucjson)))
        assertTrue(validateJson(bucStr))

        doReturn(bucStr)
                .whenever(euxKlient)
                .getBucJson(any())

        val result = service.getBucAndSedView(listOf(rinasakStr))

        assertNotNull(result)
        assertEquals(6, orgRinasaker.size)
        assertEquals(1, result.size)

        val firstJson = result.first()
        assertEquals("158123", firstJson.caseId)

        var lastUpdate: Long = 0
        firstJson.lastUpdate?.let { lastUpdate = it }
        assertEquals("2019-05-20T16:35:34",  Instant.ofEpochMilli(lastUpdate).atZone(ZoneId.systemDefault()).toLocalDateTime().toString())
        assertEquals(18, firstJson.seds?.size)

        val json = firstJson.toJson()
        val bucdetaljerpath = "src/test/resources/json/buc/bucdetaljer-158123.json"
        val bucdetaljer = String(Files.readAllBytes(Paths.get(bucdetaljerpath)))
        assertTrue(validateJson(bucdetaljer))
        JSONAssert.assertEquals(bucdetaljer, json, true)
    }

    @Test
    fun callingEuxServiceForSinglemenuUI_AllOK() {
        val bucjson = "src/test/resources/json/buc/buc-158123_2_v4.1.json"
        val bucStr = String(Files.readAllBytes(Paths.get(bucjson)))
        assertTrue(validateJson(bucStr))

        doReturn(bucStr)
                .whenever(euxKlient)
                .getBucJson(any())

        val firstJson = service.getSingleBucAndSedView("158123")

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

        val result = service.getFilteredArchivedaRinasaker(dummyList)
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

        val result = service.getFilteredArchivedaRinasaker(dummyList)
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

        val result = service.getFilteredArchivedaRinasaker(dummyList)
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
        doReturn(listOf("8877665511")).whenever(safClient).hentRinaSakIderFraDokumentMetadata(eq("1111111111111"))

        val result = service.getRinasaker("12345678900", "1111111111111")

        assertEquals(154, orgRinasaker.size)
        assertEquals(orgRinasaker.size + 1, result.size)
    }

    @Test
    fun `henter rinaid fra saf og rina hvor begge er tomme`() {

        doReturn( listOf<Rinasak>()) .whenever(euxKlient).getRinasaker(eq("12345678900"), eq(null), eq(null), eq(null))

        doReturn(listOf<String>("")).whenever(safClient).hentRinaSakIderFraDokumentMetadata(eq("1111111111111"))

        val result = service.getRinasaker("12345678900", "1111111111111")

        assertEquals(0, result.size)
    }


    @Test
    fun hentNorskFnrPaalisteavPin() {
        val list = listOf(
                PinItem(sektor = "03", land = "SE", identifikator = "00987654321", institusjonsnavn = "SE"),
                PinItem(sektor = "02", land = "NO", identifikator = "12345678900", institusjonsnavn = "NAV"),
                PinItem(sektor = "02", land = "DK", identifikator = "05467898321", institusjonsnavn = "DK")
        )

        val result = service.getFnrMedLandkodeNO(list)
        assertEquals("12345678900", result)
    }

    @Test
    fun hentNorskFnrPaalisteavPinListeTom() {
        val result = service.getFnrMedLandkodeNO(listOf())
        assertEquals(null, result)
    }

    @Test
    fun hentNorskFnrPaalisteavPinListeIngenNorske() {
        val list = listOf(
                PinItem(sektor = "03", land = "SE", identifikator = "00987654321", institusjonsnavn = "SE"),
                PinItem(sektor = "02", land = "DK", identifikator = "05467898321", institusjonsnavn = "DK")
        )
        val result = service.getFnrMedLandkodeNO(list)
        assertEquals(null, result)
    }
}
