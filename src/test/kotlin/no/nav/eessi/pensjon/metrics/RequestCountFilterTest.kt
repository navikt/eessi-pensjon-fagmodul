package no.nav.eessi.pensjon.metrics

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.fail
import org.junit.Test
import org.mockito.Mockito.any
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import javax.servlet.FilterChain
import javax.servlet.ServletException


class RequestCountFilterTest {

    private val meterRegistry = SimpleMeterRegistry()
    private val filter = RequestCountFilter(meterRegistry)

    private val someUri = "/abc"
    private val httpGet = "GET"
    private val httpPost = "POST"
    private val clientError = 400
    private val serverError = 500


    @Test
    fun `should call next in filter chain`() {
        val chain = MockFilterChain()

        filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), chain)

        assertNotNull(chain.request)
    }

    @Test
    fun `should count successful calls`() {
        val request = MockHttpServletRequest(httpGet, someUri)
        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertCount(1, httpGet, someUri, SUCCESS_VALUE, 200, NO_EXCEPTION_TAG_VALUE)
    }

    @Test
    fun `should count client errors as failures`() {
        val request = MockHttpServletRequest(httpGet, someUri)
        val response = MockHttpServletResponse()
        
        response.status = clientError
        filter.doFilter(request, response, MockFilterChain())

        assertCount(1, httpGet, someUri, FAILURE_VALUE, clientError, UNKNOWN_EXCEPTION_TAG_VALUE)
    }


    @Test
    fun `should count server errors as failures`() {
        val request = MockHttpServletRequest(httpPost, someUri)
        val response = MockHttpServletResponse()

        response.status = serverError
        filter.doFilter(request, response, MockFilterChain())

        assertCount(1, httpPost, someUri, FAILURE_VALUE, serverError, UNKNOWN_EXCEPTION_TAG_VALUE)
    }

    @Test
    fun `should propagate exception, but still count it`() {
        val request = MockHttpServletRequest(httpGet, someUri)
        val response = MockHttpServletResponse()

        response.status = serverError
        val mockFilterChain = mock<FilterChain>()
        whenever(mockFilterChain.doFilter(any(), any())).thenThrow(ServletException())

        try {
            filter.doFilter(request, response, mockFilterChain)
            fail("an exception should have been thrown!")
        } catch (e: ServletException) {
            // expected
        }

        assertCount(1, httpGet, someUri, FAILURE_VALUE, serverError, "ServletException")
    }

    private fun assertCount(count: Int, httpMethod: String, uri: String, type: String, status: Int, exception: String) {
        assertEquals(count.toDouble(),
                meterRegistry.counter(
                        COUNTER_METER_NAME,
                        HTTP_METHOD_TAG, httpMethod,
                        URI_TAG, uri,
                        TYPE_TAG, type,
                        STATUS_TAG, status.toString(),
                        EXCEPTION_TAG, exception
                ).count(), 0.1)
    }

}