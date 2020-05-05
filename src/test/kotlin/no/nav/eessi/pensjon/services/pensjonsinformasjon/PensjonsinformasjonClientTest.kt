package no.nav.eessi.pensjon.services.pensjonsinformasjon

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.utils.simpleFormat
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.util.ResourceUtils
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate

@ActiveProfiles("test")
@ExtendWith(MockitoExtension::class) // Silent?
class PensjonsinformasjonClientTest {

    @Mock
    private lateinit var mockrestTemplate: RestTemplate

    private lateinit var pensjonsinformasjonClient: PensjonsinformasjonClient

    @BeforeEach
    fun setup() {
        pensjonsinformasjonClient = PensjonsinformasjonClient(mockrestTemplate, RequestBuilder())
        pensjonsinformasjonClient.initMetrics()
    }

    @Test
    fun hentAlt() {
        val mockResponseEntity = createResponseEntityFromJsonFile("classpath:pensjonsinformasjon/full-generated-response.xml")
        whenever(mockrestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))).thenReturn(mockResponseEntity)
        val data = pensjonsinformasjonClient.hentAltPaaVedtak("1243")

        assertNotNull(data.vedtak, "Vedtak er null")
        assertEquals("2016-09-11", data.vedtak.virkningstidspunkt.simpleFormat())
    }

    @Test
    fun `PensjonsinformasjonClient  hentAlt paa vedtak feiler`() {
        whenever(mockrestTemplate.exchange(
                any<String>(),
                any(),
                any<HttpEntity<Unit>>(),
                ArgumentMatchers.eq(String::class.java))
        ).thenThrow(ResourceAccessException("IOException"))

        assertThrows<PensjoninformasjonException> {
            pensjonsinformasjonClient.hentAltPaaVedtak("1243")
        }
    }


    private fun createResponseEntityFromJsonFile(filePath: String, httpStatus: HttpStatus = HttpStatus.OK): ResponseEntity<String?> {
        val mockResponseString = ResourceUtils.getFile(filePath).readText()
        return ResponseEntity(mockResponseString, httpStatus)
    }

    @Test
    fun `Sjekker om pensjoninformasjon XmlCalendar kan være satt eller null sette simpleFormat`() {
        val mockResponseEntity = createResponseEntityFromJsonFile("classpath:pensjonsinformasjon/full-generated-response.xml")
        whenever(mockrestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))).thenReturn(mockResponseEntity)
        val data = pensjonsinformasjonClient.hentAltPaaVedtak("1243")

        var result = data.ytelsePerMaanedListe.ytelsePerMaanedListe.first()

        assertEquals("2008-02-06", result.fom.simpleFormat())
        assertEquals("2015-08-04", result.tom?.simpleFormat())

        result = data.ytelsePerMaanedListe.ytelsePerMaanedListe.getOrNull(1)

        assertNotNull(result)
        assertNotNull(result.fom)

        assertEquals("2008-02-06", result.fom.simpleFormat())
        assertEquals(null, result.tom?.simpleFormat())

    }

    @Test
    fun `hentAltpaaSak  mock data med to saktyper en skal komme ut`() {
        val mockResponseEntity = createResponseEntityFromJsonFile("classpath:pensjonsinformasjon/krav/KravAlderEllerUfore_AP_UTLAND.xml")
        whenever(mockrestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))).thenReturn(mockResponseEntity)

        val data = pensjonsinformasjonClient.hentAltPaaAktoerId("1231233")
        val sak = PensjonsinformasjonClient.finnSak("21975717", data)

        sak?.let {
            assertEquals("21975717", it.sakId.toString())
            assertEquals("ALDER", it.sakType)
        }

    }

    @Test
    fun `hentAltpaaSak  mock data med aktoerid to saktyper en skal komme ut`() {
        val mockResponseEntity = createResponseEntityFromJsonFile("classpath:pensjonsinformasjon/krav/KravAlderEllerUfore_AP_UTLAND.xml")
        whenever(mockrestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))).thenReturn(mockResponseEntity)

        val data = pensjonsinformasjonClient.hentAltPaaAktoerId("123456789011")

        assertEquals(2, data.brukersSakerListe.brukersSakerListe.size)

        val sak = PensjonsinformasjonClient.finnSak("21975717", data)

        sak?.let {
            assertEquals("21975717", it.sakId.toString())
            assertEquals("ALDER", it.sakType)
        }

    }

    @Test
    fun `hentAltpåSak  mock data med tom aktoerid to saktyper en skal komme ut`() {
        val strAktor = ""
        assertThrows<IllegalArgumentException> {
            pensjonsinformasjonClient.hentAltPaaAktoerId(strAktor)
        }
    }




    @Test
    fun `hentPensjonSakType   mock response ok`() {
        val mockResponseEntity = createResponseEntityFromJsonFile("classpath:pensjonsinformasjon/krav/AP_FORSTEG_BH.xml")

        doReturn(mockResponseEntity).whenever(mockrestTemplate).exchange(
                ArgumentMatchers.any(String::class.java),
                ArgumentMatchers.any(HttpMethod::class.java),
                ArgumentMatchers.any(HttpEntity::class.java),
                ArgumentMatchers.eq(String::class.java))

        val response = pensjonsinformasjonClient.hentKunSakType("22580170", "12345678901")

        assertEquals("ALDER", response.sakType)
        assertEquals("22580170", response.sakId)

    }

    @Test
    fun `hentPensjonSakType   mock response ingen sak eller data`() {
        val mockResponseEntity = createResponseEntityFromJsonFile("classpath:pensjonsinformasjon/empty-pensjon-response.xml")

        doReturn(mockResponseEntity).whenever(mockrestTemplate).exchange(
                ArgumentMatchers.any(String::class.java),
                ArgumentMatchers.any(HttpMethod::class.java),
                ArgumentMatchers.any(HttpEntity::class.java),
                ArgumentMatchers.eq(String::class.java))

        assertThrows<IkkeFunnetException> {
            pensjonsinformasjonClient.hentKunSakType("22580170", "12345678901")
        }
    }

    @Test
    fun `hentPensjonSakType   mock response feil fra pesys execption kastet`() {
        doThrow(ResourceAccessException("INTERNAL_SERVER_ERROR")).whenever(mockrestTemplate).exchange(
                ArgumentMatchers.any(String::class.java),
                ArgumentMatchers.any(HttpMethod::class.java),
                ArgumentMatchers.any(HttpEntity::class.java),
                ArgumentMatchers.eq(String::class.java))

        assertThrows<IkkeFunnetException> {
            pensjonsinformasjonClient.hentKunSakType("22580170", "12345678901")
        }
    }

    @Test
    fun `transform en gyldig xmlString til Persjoninformasjon forventer et gyldig object`() {
        val listOfxml = listOf("vedtak/P6000-APUtland-301.xml","krav/AP_FORSTEG_BH.xml","vedtak/P6000-UF-Avslag.xml","empty-pensjon-response.xml")

        //kjøre igjennom alle tester så ser vi!
        listOfxml.forEach { xmlFile ->
            val xml = ResourceUtils.getFile("classpath:pensjonsinformasjon/$xmlFile").readText()
            val actual = pensjonsinformasjonClient.transform(xml)

            assertNotNull(actual)
            assertEquals(Pensjonsinformasjon::class.java, actual::class.java)
        }
    }

    @Test
    fun `transform en IKKE gyldig xmlString til Persjoninformasjon forventer excpetion`() {
        val xml = "fqrqadfgadf gad23423fsdvdf"
        assertThrows<PensjoninformasjonProcessingException> {
            pensjonsinformasjonClient.transform(xml)
        }
    }

    @Test
    fun `transform en tom xmlString til Persjoninformasjon forventer excpetion`() {
        assertThrows<PensjoninformasjonProcessingException> {
            pensjonsinformasjonClient.transform("")
        }
    }

    @Test
    fun `transform en json til Persjoninformasjon forventer excpetion`() {
        val json = "\"land\": {\n" +
                "      \"value\": \"23123\",\n" +
                "      \"kodeRef\": null,\n" +
                "      \"kodeverksRef\": \"kodeverk\"\n" +
                "}\n"
        assertThrows<PensjoninformasjonProcessingException> {
            pensjonsinformasjonClient.transform(json)
        }
    }

}
