package no.nav.eessi.pensjon.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ErrorLogonController::class)
@Import(ErrorLogonController::class)
@ActiveProfiles("unsecured-webmvctest")
class ErrorLogonControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Test
    fun `errorlogon returnerer feilmelding`() {
        val response = mvc.perform(
            get("/errorlogon")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().response

        assertEquals("Det feiler ved logon", response.contentAsString)
    }
}
