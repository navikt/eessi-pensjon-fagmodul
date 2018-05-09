package no.nav.eessi.eessifagmodul.controllers

import com.google.common.collect.Lists
import com.nhaarman.mockito_kotlin.whenever
import no.nav.eessi.eessifagmodul.models.Institusjon
import no.nav.eessi.eessifagmodul.services.InstitutionService
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.InjectMocks
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import kotlin.math.exp


@RunWith(MockitoJUnitRunner::class)
class InstitutionControllerTest {

    @InjectMocks
    lateinit var institutionController : InstitutionController

    @Mock
    lateinit var mockService : InstitutionService

    @Test
    fun testInstitutionsById() {
        //`when`(mockService.getInstitutionByID("DK")).thenReturn(response)
        //institutionController.service = mockService

        val expected = Institusjon("SE", "Sverige")
        val response : ResponseEntity<Institusjon> = ResponseEntity(expected, HttpStatus.OK)

        whenever(mockService.getInstitutionByID("DK")).thenReturn(response)

        val result = institutionController.getInstitutionsById("DK")
        assertNotNull(result)
        assertEquals(expected, result!!)
        assertEquals(expected.landkode, result.landkode)
        assertEquals(expected.navn, result.navn)
    }

    @Test
    fun testInstitutionsByTopic() {
        val topic = "TodyTopicIsGreen"
        val result = institutionController.getInstitutionsByTopic(topic)
        assertNotNull(result)
        val inst : Institusjon = if (result != null) result else throw NullPointerException("Expression 'result' must not be null")
        assertEquals(topic, inst.navn)
        assertEquals("SE", inst.landkode)
    }

    @Test
    fun testInstitutionsByTopicNullTopic() {
        val topic : String? = null
        val result = institutionController.getInstitutionsByTopic(topic)
        assertNull(result)
    }

    @Test
    fun testInstitutionsByCountry() {
        //`when`(mockrestTemp.getForObject (anyString(),eq(type::class.java))).thenReturn(expected)
        //service.restTemplate = mockrestTemp

        val countryID = "FI"
        val result = institutionController.getInstitutionsByCountry(countryID)
        assertNotNull(result)
        assertEquals(countryID, result.landkode)
        assertEquals("Sverige", result.navn)
    }
    
    @Test
    fun testAllInstitutions() {

        val expected = Lists.newArrayList(Institusjon("SE","Sverige"), Institusjon("DK","Danmark"),Institusjon("FI","Finland"))

        val response : ResponseEntity<List<Institusjon>> = ResponseEntity(expected, HttpStatus.OK)
        whenever(mockService.getAllInstitutions()).thenReturn(response)

        val result = institutionController.getAllInstitutions()
        assertNotNull(result)
        val res : List<Institusjon> = result
        assertEquals(3, res.size)
        assertEquals(Institusjon::class.java, res.get(1)::class.java)
        assertEquals("DK", res.get(1).landkode)
        res.forEach { println(it ) }
    }
}