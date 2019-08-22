package no.nav.eessi.pensjon.security.sts

import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.utils.typeRef
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import org.junit.Assert.assertEquals

@RunWith(MockitoJUnitRunner::class)
class STSServiceTest {

    @Mock
    private lateinit var securityTokenExchangeBasicAuthRestTemplate: RestTemplate

    private lateinit var stsService: STSService

    @Before
    fun oppStart() {

        stsService = STSService(securityTokenExchangeBasicAuthRestTemplate)

    }


    @Test
    fun tester() {


    }

    @Test
    fun getSystemOidcToken_withValidToken() {

        val mockSecurityTokenResponse = SecurityTokenResponse(
                accessToken = "LKUITDKUo96tyfhj",
                tokenType = "sts",
                expiresIn = 10L
        )


        val response = ResponseEntity.ok().body(mockSecurityTokenResponse)

        whenever(securityTokenExchangeBasicAuthRestTemplate.exchange(
                ArgumentMatchers.anyString(),
                eq(HttpMethod.GET),
                eq(null),
                eq(typeRef<SecurityTokenResponse>()))
        ).thenReturn(response)

        val result = stsService.getSystemOidcToken()

        assertEquals(mockSecurityTokenResponse.accessToken, result)

    }

    @Test(expected = SystembrukerTokenException::class)
    fun getSystemOidcToken_withError() {

        val mockSecurityTokenResponse = SecurityTokenResponse(
                accessToken = "LKUITDKUo96tyfhj",
                tokenType = "sts",
                expiresIn = 10L
        )

        val response = ResponseEntity.badRequest().body(mockSecurityTokenResponse)

        whenever(securityTokenExchangeBasicAuthRestTemplate.exchange(
                ArgumentMatchers.anyString(),
                eq(HttpMethod.GET),
                eq(null),
                eq(typeRef<SecurityTokenResponse>()))
        ).thenReturn(response)
        stsService.getSystemOidcToken()
    }


}
