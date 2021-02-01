package no.nav.eessi.pensjon.integrationtest

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.fagmodul.personoppslag.PersonPDLMock
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentType
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.security.sts.STSService
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpEntity
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.client.RestTemplate

@SpringBootTest(classes = [UnsecuredWebMvcTestLauncher::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc
class SedPrefillPDLIntegrationSpringTest {

    @MockBean
    lateinit var stsService: STSService

    @MockBean(name = "pensjonsinformasjonOidcRestTemplate")
    lateinit var restTemplate: RestTemplate

    @MockBean
    lateinit var kodeverkClient: KodeverkClient

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    lateinit var personService: PersonService

    companion object {
        const val SAK_ID = "12345"

        const val FNR_OVER_60 = "09035225916"   // SLAPP SKILPADDE
        const val FNR_VOKSEN = "11067122781"    // KRAFTIG VEGGPRYD
        const val FNR_VOKSEN_2 = "22117320034"  // LEALAUS KAKE
        const val FNR_BARN = "12011577847"      // STERK BUSK

        const val AKTOER_ID = "0123456789000"
        const val AKTOER_ID_2 = "0009876543210"
    }


    @Test
    @Throws(Exception::class)
    fun `prefill sed P2000 alder return valid sedjson`() {
        doReturn(PrefillTestHelper.readXMLresponse("P2000-AP-UP-21337890.xml")).`when`(restTemplate).exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))
        doReturn("QX").`when`(kodeverkClient).finnLandkode2(any())

        doReturn(NorskIdent(FNR_VOKSEN)).`when`(personService).hentIdent(IdentType.NorskIdent, AktoerId(AKTOER_ID))
        doReturn(PersonPDLMock.createWith(true, fnr = FNR_VOKSEN, aktoerid = AKTOER_ID)).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN))
        val apijson = dummyApijson(sed = "P2001", sakid = "21337890", aktoerId = AKTOER_ID)

        val result = mockMvc.perform(post("/sed/pdl/prefill")
                .contentType(MediaType.APPLICATION_JSON)
                .content(apijson))
                .andDo(print())
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()

        val actual = result.response.getContentAsString(charset("UTF-8"))

        val excpected = """
{
  "sed" : "P2000",
  "sedGVer" : "4",
  "sedVer" : "1",
  "nav" : {
    "eessisak" : [ {
      "institusjonsid" : "NO:noinst002",
      "institusjonsnavn" : "NOINST002, NO INST002, NO",
      "saksnummer" : "21337890",
      "land" : "NO"
    } ],
    "bruker" : {
      "person" : {
        "pin" : [ {
          "institusjonsnavn" : "NOINST002, NO INST002, NO",
          "institusjonsid" : "NO:noinst002",
          "identifikator" : "11067122781",
          "land" : "NO"
        } ],
        "statsborgerskap" : [ {
          "land" : "QX"
        } ],
        "etternavn" : "Testesen",
        "fornavn" : "Test",
        "kjoenn" : "M",
        "foedselsdato" : "1988-07-12"
      },
      "adresse" : {
        "gate" : "Oppoverbakken 66",
        "by" : "SØRUMSAND",
        "postnummer" : "1920",
        "land" : "QX"
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
        JSONAssert.assertEquals(excpected, actual , true)

    }

    @Test
    @Throws(Exception::class)
    fun `prefill sed P2100 P_BUC_02 Gjenlevende har med avdod skal returnere en gyldig SED`() {

        doReturn(NorskIdent(FNR_VOKSEN)).whenever(personService).hentIdent(IdentType.NorskIdent, AktoerId(AKTOER_ID))
        doReturn(AktoerId(AKTOER_ID_2)).whenever(personService).hentIdent(IdentType.AktoerId, NorskIdent(FNR_VOKSEN_2))

        doReturn(PersonPDLMock.createWith(true, "Lever", "Gjenlev", FNR_VOKSEN, AKTOER_ID)).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN))
        doReturn(PersonPDLMock.createWith(true, "Avdød", "Død", FNR_VOKSEN_2, AKTOER_ID_2, true)).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN_2))

        doReturn(PrefillTestHelper.readXMLresponse("P2100-GL-UTL-INNV.xml")).`when`(restTemplate).exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))

        doReturn("QX").whenever(kodeverkClient).finnLandkode2(any())

        val apijson = dummyApijson(sakid = "22874955", aktoerId = AKTOER_ID, sed = "P2101", buc = "P_BUC_02", fnravdod = FNR_VOKSEN_2)

        val result = mockMvc.perform(post("/sed/pdl/prefill")
            .contentType(MediaType.APPLICATION_JSON)
            .content(apijson))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val actual = result.response.getContentAsString(charset("UTF-8"))

        val expected = """
{
  "sed" : "P2100",
  "sedGVer" : "4",
  "sedVer" : "1",
  "nav" : {
    "eessisak" : [ {
      "institusjonsid" : "NO:noinst002",
      "institusjonsnavn" : "NOINST002, NO INST002, NO",
      "saksnummer" : "22874955",
      "land" : "NO"
    } ],
    "bruker" : {
      "person" : {
        "pin" : [ {
          "institusjonsnavn" : "NOINST002, NO INST002, NO",
          "institusjonsid" : "NO:noinst002",
          "identifikator" : "22117320034",
          "land" : "NO"
        } ],
        "statsborgerskap" : [ {
          "land" : "QX"
        } ],
        "etternavn" : "Død",
        "fornavn" : "Avdød",
        "kjoenn" : "M",
        "foedselsdato" : "1921-07-12"
      }
    }
  },
  "pensjon" : {
    "gjenlevende" : {
      "person" : {
        "pin" : [ {
          "institusjonsnavn" : "NOINST002, NO INST002, NO",
          "institusjonsid" : "NO:noinst002",
          "identifikator" : "11067122781",
          "land" : "NO"
        } ],
        "statsborgerskap" : [ {
          "land" : "QX"
        } ],
        "etternavn" : "Gjenlev",
        "fornavn" : "Lever",
        "kjoenn" : "M",
        "foedselsdato" : "1988-07-12"
      },
      "adresse" : {
        "gate" : "Oppoverbakken 66",
        "by" : "SØRUMSAND",
        "postnummer" : "1920",
        "land" : "QX"
      }
    }
  }
}
          """.trimIndent()

        println(actual)
        JSONAssert.assertEquals(expected, actual, true)
    }


    @Test
    @Throws(Exception::class)
    fun `prefill sed P2000 alder return valid sedjson checkk tps to pdl`() {
        doReturn(NorskIdent(FNR_VOKSEN)).`when`(personService).hentIdent(IdentType.NorskIdent, AktoerId(AKTOER_ID))
        doReturn(PersonPDLMock.createWith()).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN))

        doReturn(PrefillTestHelper.readXMLresponse("P2000-AP-UP-21337890.xml")).`when`(restTemplate).exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))
        doReturn("QX").`when`(kodeverkClient).finnLandkode2(any())

        val apijson = dummyApijson(sed = "P2001", sakid = "21337890", aktoerId = AKTOER_ID)

        val validResponse = """
            {
              "sed" : "P2000",
              "sedGVer" : "4",
              "sedVer" : "1",
              "nav" : {
                "eessisak" : [ {
                  "institusjonsid" : "NO:noinst002",
                  "institusjonsnavn" : "NOINST002, NO INST002, NO",
                  "saksnummer" : "21337890",
                  "land" : "NO"
                } ],
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
                  },
                  "adresse" : {
                    "gate" : "Oppoverbakken 66",
                    "by" : "SØRUMSAND",
                    "postnummer" : "1920",
                    "land" : "QX"
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

        val result = mockMvc.perform(post("/sed/pdl/prefill")
            .contentType(MediaType.APPLICATION_JSON)
            .content(apijson))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        print(response)

        JSONAssert.assertEquals(response, validResponse, false)

    }

}
