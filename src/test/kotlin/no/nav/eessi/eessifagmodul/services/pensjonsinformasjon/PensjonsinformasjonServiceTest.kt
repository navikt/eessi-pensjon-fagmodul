package no.nav.eessi.eessifagmodul.services.pensjonsinformasjon

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.whenever
import no.nav.eessi.eessifagmodul.models.IkkeFunnetException
import no.nav.eessi.eessifagmodul.models.PensjoninformasjonException
import no.nav.eessi.eessifagmodul.utils.simpleFormat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.util.ResourceUtils
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ActiveProfiles("test")
@RunWith(MockitoJUnitRunner.Silent::class)
class PensjonsinformasjonServiceTest {

    @Mock
    private lateinit var mockrestTemplate: RestTemplate

    lateinit var pensjonsinformasjonService: PensjonsinformasjonService

    @Before
    fun setup() {
        pensjonsinformasjonService = PensjonsinformasjonService(mockrestTemplate, RequestBuilder())
    }

    @Test
    fun hentAlt() {
        val mockResponseEntity = createResponseEntityFromJsonFile("classpath:pensjonsinformasjon/full-generated-response.xml")
        whenever(mockrestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))).thenReturn(mockResponseEntity)
        val data = pensjonsinformasjonService.hentAltPaaVedtak("1243")
        // TODO: add asserts

        assertNotNull(data.vedtak, "Vedtak er null")
        assertEquals("2016-09-11", data.vedtak.virkningstidspunkt.simpleFormat())
    }

    @Test(expected = PensjoninformasjonException::class)
    fun `PensjonsinformasjonService| hentAlt paa vedtak feiler`() {
        val mockResponseEntity = createResponseEntityFromJsonFile("classpath:pensjonsinformasjon/full-generated-response.xml")

        whenever(mockrestTemplate.exchange(
                any<String>(),
                any(),
                any<HttpEntity<Unit>>(),
                ArgumentMatchers.eq(String::class.java))
        ).thenThrow(ResourceAccessException("IOException"))

        pensjonsinformasjonService.hentAltPaaVedtak("1243")
    }


    private fun createResponseEntityFromJsonFile(filePath: String, httpStatus: HttpStatus = HttpStatus.OK): ResponseEntity<String?> {
        val mockResponseString = ResourceUtils.getFile(filePath).readText()
        return ResponseEntity(mockResponseString, httpStatus)
    }

    @Test
    fun `Sjekker om pensjoninformasjon XmlCalendar kan være satt eller null også sette simpleFormat`() {
        val mockResponseEntity = createResponseEntityFromJsonFile("classpath:pensjonsinformasjon/full-generated-response.xml")
        whenever(mockrestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))).thenReturn(mockResponseEntity)
        val data = pensjonsinformasjonService.hentAltPaaVedtak("1243")

        var result = data.ytelsePerMaanedListe.ytelsePerMaanedListe.get(0)

        assertEquals("2008-02-06", result.fom.simpleFormat())
        assertEquals("2015-08-04", result.tom?.let { it.simpleFormat() })

        result = data.ytelsePerMaanedListe.ytelsePerMaanedListe.get(1)

        assertNotNull(result)
        assertNotNull(result.fom)

        assertEquals("2008-02-06", result.fom.simpleFormat())
        assertEquals(null, result.tom?.let { it.simpleFormat() })

    }

    @Test
    fun `hentAltpåSak| mock data med to saktyper en skal komme ut`() {
        val mockResponseEntity = createResponseEntityFromJsonFile("classpath:pensjonsinformasjon/krav/P2000_21975717_AP_UTLAND.xml")
        whenever(mockrestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))).thenReturn(mockResponseEntity)
        val data = pensjonsinformasjonService.hentAltPaaFnr("1231233")

        val sak = pensjonsinformasjonService.hentAltPaaSak("21975717", data)

        sak?.let {
            assertEquals("21975717", it.sakId.toString())
            assertEquals("ALDER", it.sakType)
        }

    }


    @Test
    fun `hentPensjonSakType | mock response ok`() {
        val mockResponseEntity = createResponseEntityFromJsonFile("classpath:pensjonsinformasjon/krav/AP_FORSTEG_BH.xml")

        doReturn(mockResponseEntity).whenever(mockrestTemplate).exchange(
                ArgumentMatchers.any(String::class.java),
                ArgumentMatchers.any(HttpMethod::class.java),
                ArgumentMatchers.any(HttpEntity::class.java),
                ArgumentMatchers.eq(String::class.java))

        val response = pensjonsinformasjonService.hentKunSakType("22580170", "12345678901")

        assertEquals("ALDER", response?.sakType)
        assertEquals("22580170", response?.sakId)

    }

    @Test(expected = IkkeFunnetException::class)
    fun `hentPensjonSakType | mock response ingen sak eller data`() {
        val mockResponseEntity = createResponseEntityFromJsonFile("classpath:pensjonsinformasjon/empty-pensjon-response.xml")

        doReturn(mockResponseEntity).whenever(mockrestTemplate).exchange(
                ArgumentMatchers.any(String::class.java),
                ArgumentMatchers.any(HttpMethod::class.java),
                ArgumentMatchers.any(HttpEntity::class.java),
                ArgumentMatchers.eq(String::class.java))

        pensjonsinformasjonService.hentKunSakType("22580170", "12345678901")

    }

    @Test(expected = PensjoninformasjonException::class)
    fun `hentPensjonSakType | mock response feil fra pesys execption kastet`() {
        doThrow(ResourceAccessException("INTERNAL_SERVER_ERROR")).whenever(mockrestTemplate).exchange(
                ArgumentMatchers.any(String::class.java),
                ArgumentMatchers.any(HttpMethod::class.java),
                ArgumentMatchers.any(HttpEntity::class.java),
                ArgumentMatchers.eq(String::class.java))

        pensjonsinformasjonService.hentKunSakType("22580170", "12345678901")
    }

}