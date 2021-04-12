package no.nav.eessi.pensjon.integrationtest.sed

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.fagmodul.eux.EuxKlient
import no.nav.eessi.pensjon.security.sts.STSService
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertNotNull

@SpringBootTest(
    classes = [UnsecuredWebMvcTestLauncher::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc
class SedApiIntegrationSpringTest {

    @MockBean
    private lateinit var stsService: STSService

    @MockBean
    private lateinit var euxKlient: EuxKlient

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    @Throws(Exception::class)
    fun `Gitt forespørel etter P5000 så Map trygdetid`() {
        val p5000json = String(Files.readAllBytes(Paths.get("src/test/resources/json/nav/P5000-NAV.json")))
        doReturn(p5000json).`when`(euxKlient).getSedOnBucByDocumentIdAsJson(any(), any())

        val result = mockMvc.perform(get("/sed/get/1234/5678"))
            .andExpect(status().isOk)
            .andReturn()

        val response  = result.response.getContentAsString(charset("UTF-8"))
        val p5000 = mapJsonToAny(response, typeRefs<P5000>())
        assertNotNull(p5000.p5000Pensjon?.trygdetid!!.size == 1)
        assertNotNull(p5000.p5000Pensjon?.medlemskap!!.size == 1)

    }

    @Test
    @Throws(Exception::class)
    fun `Gitt forespørel etter P5000 som er tom av pensjon`() {
        val p5000json = String(Files.readAllBytes(Paths.get("src/test/resources/json/nav/p5000/P5000_tomsed-nav.json")))
        doReturn(p5000json).`when`(euxKlient).getSedOnBucByDocumentIdAsJson(any(), any())

        val result = mockMvc.perform(get("/sed/get/1234/5678"))
            .andExpect(status().isOk)
            .andReturn()

        val response  = result.response.getContentAsString(charset("UTF-8"))
        val p5000 = mapJsonToAny(response, typeRefs<P5000>())
        assertNotNull(p5000)

        val validP5000 = """
            {
              "sed" : "P5000",
              "sedGVer" : "4",
              "sedVer" : "2",
              "nav" : {
                "eessisak" : [ {
                  "institusjonsid" : "NO:NAVAT07",
                  "institusjonsnavn" : "NAV ACCEPTANCE TEST 07",
                  "saksnummer" : "22935783",
                  "land" : "NO"
                } ],
                "bruker" : {
                  "mor" : null,
                  "far" : null,
                  "person" : {
                    "pin" : [ {
                      "institusjonsnavn" : "NAV ACCEPTANCE TEST 07",
                      "institusjonsid" : "NO:NAVAT07",
                      "sektor" : null,
                      "identifikator" : "20066521894",
                      "land" : "NO",
                      "institusjon" : null
                    } ],
                    "pinland" : null,
                    "statsborgerskap" : [ {
                      "land" : "NO"
                    } ],
                    "etternavn" : "PENN",
                    "fornavn" : "SMIDIG",
                    "kjoenn" : "K",
                    "foedested" : null,
                    "foedselsdato" : "1965-06-20",
                    "sivilstand" : null,
                    "relasjontilavdod" : null,
                    "rolle" : null
                  },
                  "adresse" : {
                    "gate" : "NORDBYLA 8",
                    "bygning" : null,
                    "by" : "AKSDAL",
                    "postnummer" : "5570",
                    "region" : null,
                    "land" : "NO",
                    "kontaktpersonadresse" : null,
                    "datoforadresseendring" : null,
                    "postadresse" : null,
                    "startdato" : null
                  },
                  "arbeidsforhold" : null,
                  "bank" : null
                },
                "ektefelle" : null,
                "barn" : null,
                "verge" : null,
                "krav" : null,
                "sak" : null,
                "annenperson" : null
              },
              "pensjon" : null
            }
        """.trimIndent()

        JSONAssert.assertEquals(response, validP5000, false)

    }

}

