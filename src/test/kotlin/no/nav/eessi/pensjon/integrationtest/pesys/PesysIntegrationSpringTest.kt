package no.nav.eessi.pensjon.integrationtest.pesys

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.security.sts.STSService
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.util.ResourceUtils
import org.springframework.web.client.RestTemplate

@SpringBootTest(classes = [UnsecuredWebMvcTestLauncher::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc
class PesysIntegrationSpringTest {

    @MockkBean
    private lateinit var stsService: STSService

    @MockkBean(name = "euxUsernameOidcRestTemplate")
    private lateinit var restTemplate: RestTemplate

    @MockkBean
    private lateinit var kodeverkClient: KodeverkClient

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `Uføre utlandskrav returnerer json ut fra behandle P2200`() {

        val bucid = "998777"
        val sedid = "5a61468eb8cb4fd78c5c44d75b9bb890"

        every { kodeverkClient.finnLandkode(any())  } returns "SWE"

        //euxrest kall buc
        val buc03 = ResourceUtils.getFile("classpath:json/buc/buc-1297512-kravP2200_v4.2.json").readText()
        val rinabucpath = "/buc/$bucid"

        every { restTemplate.exchange( eq(rinabucpath), eq(HttpMethod.GET), any(), eq(String::class.java)) } returns ResponseEntity.ok().body( buc03 )

        //euxrest kall til p2200
        val sedurl = "/buc/$bucid/sed/$sedid"
        val sedP2200 = ResourceUtils.getFile("classpath:json/nav/P2200-NAV-FRA-UTLAND-KRAV.json").readText()

        every { restTemplate.exchange( eq(sedurl), eq(HttpMethod.GET), any(), eq(String::class.java)) } returns ResponseEntity.ok().body( sedP2200 )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/pesys/hentKravUtland/$bucid")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        print(response)

        val validResponse = """
            {
                "errorMelding" : null,
                "mottattDato" : "2021-03-26",
                "iverksettelsesdato" : "2020-12-01",
                "fremsattKravdato" : "2021-03-01",
                "uttaksgrad" : "0",
                "vurdereTrygdeavtale" : true,
                "personopplysninger" : {
                "statsborgerskap" : ""
                },
                "utland" : null,
                "sivilstand" : null,
                "soknadFraLand" : "SWE",
                "initiertAv" : "BRUKER",
                "virkningsDato": null
            }
        """.trimIndent()

        JSONAssert.assertEquals(response, validResponse, true)

    }

    @Test
    fun `Alderpensjon utlandskrav returnerer json ut fra behandle P2000`() {

        val bucid = "998777"
        val sedid = "5a61468eb8cb4fd78c5c44d75b9bb890"

        every { kodeverkClient.finnLandkode(any())  } returns "SWE"

        //euxrest kall buc
        val buc01 = ResourceUtils.getFile("classpath:json/buc/buc-1297512-kravP2000_v4.2.json").readText()
        val rinabucpath = "/buc/$bucid"

        every { restTemplate.exchange( eq(rinabucpath), eq(HttpMethod.GET), any(), eq(String::class.java)) } returns ResponseEntity.ok().body( buc01 )

        //euxrest kall til p2000
        val sedurl = "/buc/$bucid/sed/$sedid"
        val sedP2000 = ResourceUtils.getFile("classpath:json/nav/P2000-NAV-FRA-UTLAND-KRAV.json").readText()

        every { restTemplate.exchange( eq(sedurl), eq(HttpMethod.GET), any(), eq(String::class.java)) } returns ResponseEntity.ok().body( sedP2000 )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/pesys/hentKravUtland/$bucid")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        print(response)

        val validResponse = """
            {
                "errorMelding" : null,
                "mottattDato" : "2021-03-26",
                "iverksettelsesdato" : "2021-04-01",
                "fremsattKravdato" : "2021-03-01",
                "uttaksgrad" : "100",
                "vurdereTrygdeavtale" : true,
                "personopplysninger" : {
                    "statsborgerskap" : "SWE"
                },
                "utland" : null,
                "sivilstand" : {
                    "valgtSivilstatus" : "UGIF",
                    "sivilstatusDatoFom" : "2008-09-30"
                },
                "soknadFraLand" : "SWE",
                "initiertAv" : "BRUKER",
                "virkningsDato": null,
                "utland": {
                    "utlandsopphold": []
                }
            }
        """.trimIndent()

        JSONAssert.assertEquals(validResponse, response, true)

    }

    @Test
    fun `Alderpensjon utlandskrav returnerer json ut fra behandle P2000 fra SE`() {

        val bucid = "998777"
        val sedid = "5a61468eb8cb4fd78c5c44d75b9bb890"

        every { kodeverkClient.finnLandkode(any())  } returns "SWE"

        //euxrest kall buc
        val p2000json = """{"nav":{"bruker":{"person":{"sivilstand":[{"status":"gift","fradato":"2006-01-03"}],"kjoenn":"K","etternavn":"MASKIN","fornavn":"LITEN\t","foedselsdato":"1953-09-24","pin":[{"land":"NO","identifikator":"64095349631"}],"statsborgerskap":[{"land":"NO"}]}},"krav":{"dato":"2021-02-10"}},"sedGVer":"4","sedVer":"2","sed":"P2000"}        """.trimIndent()

        val buc01 = ResourceUtils.getFile("classpath:json/buc/buc-1297512-kravP2000_v4.2.json").readText()
        val rinabucpath = "/buc/$bucid"

        every { restTemplate.exchange( eq(rinabucpath), eq(HttpMethod.GET), any(), eq(String::class.java)) } returns ResponseEntity.ok().body( buc01 )

        //euxrest kall til p2000
        val sedurl = "/buc/$bucid/sed/$sedid"
        every { restTemplate.exchange( eq(sedurl), eq(HttpMethod.GET), any(), eq(String::class.java)) } returns ResponseEntity.ok().body( p2000json )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/pesys/hentKravUtland/$bucid")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        print(response)

        val validResponse = """
            {
                "errorMelding" : null,
                "mottattDato" : "2021-03-26",
                "iverksettelsesdato" : "2021-03-01",
                "fremsattKravdato" : "2021-02-10",
                "uttaksgrad" : "100",
                "vurdereTrygdeavtale" : true,
                "personopplysninger" : {
                    "statsborgerskap" : "SWE"
                },
                "utland" : null,
                "sivilstand" : {
                    "valgtSivilstatus" : "GIFT",
                    "sivilstatusDatoFom" : "2006-01-03"
                },
                "soknadFraLand" : "SWE",
                "initiertAv" : "BRUKER",
                "virkningsDato": null,
                "utland": {
                    "utlandsopphold": []
                }
            }
        """.trimIndent()

        JSONAssert.assertEquals(validResponse, response, true)

    }


    @Test
    fun `Ugydlig BUC medfører BAD_REQUEST`() {
        val bucid = "998777"

        val bucjson = """
            {
              "id": "$bucid",
              "processDefinitionName": "P_BUC_02"
            }
            
        """.trimIndent()

        val rinabucpath = "/buc/$bucid"

        every { restTemplate.exchange( eq(rinabucpath), eq(HttpMethod.GET), any(), eq(String::class.java)) } returns ResponseEntity.ok().body( bucjson )


        val expectedError = """Ugyldig BUC, Ikke korrekt type KRAV."""

        mockMvc.perform(
            MockMvcRequestBuilders.get("/pesys/hentKravUtland/$bucid")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.status().reason(Matchers.equalTo(expectedError)))
    }

    @Test
    fun `Ugydlig BUC ingen Caseowner medfører NOT_FOUND`() {
        val bucid = "998777"

        val bucjson = """
            {
              "id": "$bucid",
              "processDefinitionName": "P_BUC_01"
            }
            
        """.trimIndent()

        val rinabucpath = "/buc/$bucid"

        every { restTemplate.exchange( eq(rinabucpath), eq(HttpMethod.GET), any(), eq(String::class.java)) } returns ResponseEntity.ok().body( bucjson )


        val expectedError = """Ingen CaseOwner funnet på BUC med id: 998777"""

        mockMvc.perform(
            MockMvcRequestBuilders.get("/pesys/hentKravUtland/$bucid")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isNotFound)
            .andExpect(MockMvcResultMatchers.status().reason(Matchers.equalTo(expectedError)))
    }

    @Test
    fun `Ugyldig BUC ingen gyldige dokumenter funnet kaster en BAD_REQUEST`() {
        val bucid = "998777"

        val bucjson = """
            {
              "id": "$bucid",
              "processDefinitionName": "P_BUC_01",
                "participants": [
                {
                  "role": "CaseOwner",
                  "organisation": {
                    "id": "NO:NAVAT05",
                    "address": {
                      "street": null,
                      "town": null,
                      "postalCode": null,
                      "region": null,
                      "country": "NO"
                    },
                    "contactMethods": null,
                    "registryNumber": null,
                    "name": "NAV ACCEPTANCE TEST 05",
                    "acronym": "NAV ACCT 05",
                    "countryCode": "NO",
                    "activeSince": "2018-08-26T22:00:00.000+0000",
                    "accessPoint": null,
                    "location": null,
                    "assignedBUCs": null
                  },
                 "selected": false
              }],
              "documents": []
            }
            
        """.trimIndent()

        val rinabucpath = "/buc/$bucid"
        every { restTemplate.exchange( eq(rinabucpath), eq(HttpMethod.GET), any(), eq(String::class.java)) } returns ResponseEntity.ok().body( bucjson )

        val expectedError = """Ingen dokument metadata funnet i BUC med id: 998777."""

        mockMvc.perform(
            MockMvcRequestBuilders.get("/pesys/hentKravUtland/$bucid")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.status().reason(Matchers.equalTo(expectedError)))
    }

}