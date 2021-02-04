package no.nav.eessi.pensjon.integrationtest

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.fagmodul.prefill.PersonPDLMock
import no.nav.eessi.pensjon.fagmodul.prefill.PersonPDLMock.medBeskyttelse
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentType
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.security.sts.STSService
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.services.pensjonsinformasjon.EPSaktype
import no.nav.eessi.pensjon.services.pensjonsinformasjon.KravArsak
import no.nav.pensjon.v1.kravhistorikk.V1KravHistorikk
import no.nav.pensjon.v1.kravhistorikkliste.V1KravHistorikkListe
import no.nav.pensjon.v1.sak.V1Sak
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
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
class SedPrefillP8000IntegrationSpringTest {

    @MockBean
    private lateinit var stsService: STSService

    @MockBean(name = "pensjonsinformasjonOidcRestTemplate")
    private lateinit var restTemplate: RestTemplate

    @MockBean
    private lateinit var kodeverkClient: KodeverkClient

    @MockBean
    private lateinit var pensjoninformasjonservice: PensjonsinformasjonService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var personService: PersonService

    private companion object {
        const val SAK_ID = "12345"

        const val FNR_OVER_60 = "09035225916"   // SLAPP SKILPADDE
        const val FNR_VOKSEN = "11067122781"    // KRAFTIG VEGGPRYD
        const val FNR_VOKSEN_2 = "22117320034"  // LEALAUS KAKE
        const val FNR_VOKSEN_3 = "12312312312"
        const val FNR_VOKSEN_4 = "9876543210"
        const val FNR_BARN = "12011577847"      // STERK BUSK

        const val AKTOER_ID = "0123456789000"
        const val AKTOER_ID_2 = "0009876543210"
    }

    @Test
    @Throws(Exception::class)
    fun `prefill sed P8000 - Gitt gjenlevendepensjon Og henvendelse gjelder søker SÅ skal det produseres en Gyldig P8000 med referanse til person 02`() {
        doReturn(NorskIdent(FNR_VOKSEN_3)).whenever(personService).hentIdent(IdentType.NorskIdent, AktoerId(AKTOER_ID))
        doReturn(AktoerId(AKTOER_ID_2)).whenever(personService).hentIdent(IdentType.AktoerId, NorskIdent(FNR_VOKSEN_4))
        doReturn(PersonPDLMock.createWith(true, "Lever", "Gjenlev", FNR_VOKSEN_3, AKTOER_ID)).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN_3))
        doReturn(PersonPDLMock.createWith(true, "Avdød", "Død", FNR_VOKSEN_4, AKTOER_ID_2, true)).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN_4))

        val sak = V1Sak()
        sak.sakType = EPSaktype.GJENLEV.toString()
        sak.sakId = 100
        sak.kravHistorikkListe = V1KravHistorikkListe()

        doReturn(sak).whenever(pensjoninformasjonservice).hentRelevantPensjonSak(any(), any())
        doReturn("QX").whenever(kodeverkClient).finnLandkode2(any())

        val subject = dummyApiSubjectjson(FNR_VOKSEN_4)
        val apijson = dummyApijson(sakid = "21337890", aktoerId = AKTOER_ID, sed = "P8000", buc = "P_BUC_05", subject = subject, refperson = "\"SOKER\"")

        val validResponse = """
            {
              "sed" : "P8000",
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
                      "identifikator" : "$FNR_VOKSEN_4",
                      "land" : "NO"
                    } ],
                    "etternavn" : "Død",
                    "fornavn" : "Avdød",
                    "kjoenn" : "M",
                    "foedselsdato" : "1921-07-12"
                  },
                  "adresse" : { }
                },
                "annenperson" : {
                  "person" : {
                    "pin" : [ {
                      "institusjonsnavn" : "NOINST002, NO INST002, NO",
                      "institusjonsid" : "NO:noinst002",
                      "identifikator" : "$FNR_VOKSEN_3",
                      "land" : "NO"
                    } ],
                    "statsborgerskap" : [ {
                      "land" : "QX"
                    } ],
                    "etternavn" : "Gjenlev",
                    "fornavn" : "Lever",
                    "kjoenn" : "M",
                    "foedselsdato" : "1988-07-12",
                    "rolle" : "01"
                  },
                  "adresse" : {
                    "gate" : "Oppoverbakken 66",
                    "by" : "SØRUMSAND",
                    "postnummer" : "1920",
                    "land" : "NO"
                  }
                }
              },
              "pensjon" : {
                "anmodning" : {
                  "referanseTilPerson" : "02"
                }
              }
            }
        """.trimIndent()

        val result = mockMvc.perform(post("/sed/prefill")
                .contentType(MediaType.APPLICATION_JSON)
                .content(apijson))
                .andDo(print())
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        JSONAssert.assertEquals(response, validResponse, false)
    }

    @Test
    @Throws(Exception::class)
    fun `prefill sed P8000 - Gitt alderpensjon Og henvendelse gjelder avdød SÅ skal det produseres en Gyldig P8000 med avdød og gjenlevende`() {
        doReturn(NorskIdent(FNR_VOKSEN_3)).whenever(personService).hentIdent(IdentType.NorskIdent, AktoerId(AKTOER_ID))
        doReturn(AktoerId(AKTOER_ID_2)).whenever(personService).hentIdent(IdentType.AktoerId, NorskIdent(FNR_VOKSEN_4))
        doReturn(PersonPDLMock.createWith(true, "Lever", "Gjenlev", FNR_VOKSEN_3, AKTOER_ID)).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN_3))
        doReturn(PersonPDLMock.createWith(true, "Avdød", "Død", FNR_VOKSEN_4, AKTOER_ID_2, true)).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN_4))

        val v1Kravhistorikk = V1KravHistorikk()
        v1Kravhistorikk.kravArsak = KravArsak.GJNL_SKAL_VURD.name

        val sak = V1Sak()
        sak.sakType = EPSaktype.ALDER.toString()
        sak.sakId = 100
        sak.kravHistorikkListe = V1KravHistorikkListe()
        sak.kravHistorikkListe.kravHistorikkListe.add(v1Kravhistorikk)

        doReturn(sak).whenever(pensjoninformasjonservice).hentRelevantPensjonSak(any(), any())
        doReturn("QX").whenever(kodeverkClient).finnLandkode2(any())

        val subject = dummyApiSubjectjson(FNR_VOKSEN_4)
        val apijson = dummyApijson(sakid = "21337890", aktoerId = AKTOER_ID, sed = "P8000", buc = "P_BUC_05", subject = subject, refperson = "\"AVDOD\"")

        val validResponse = """
        {
          "sed" : "P8000",
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
                  "identifikator" : "$FNR_VOKSEN_4",
                  "land" : "NO"
                } ],
                "etternavn" : "Død",
                "fornavn" : "Avdød",
                "kjoenn" : "M",
                "foedselsdato" : "1921-07-12"
              },
              "adresse" : { }
            },
            "annenperson" : {
              "person" : {
                "pin" : [ {
                  "institusjonsnavn" : "NOINST002, NO INST002, NO",
                  "institusjonsid" : "NO:noinst002",
                  "identifikator" : "$FNR_VOKSEN_3",
                  "land" : "NO"
                } ],
                "statsborgerskap" : [ {
                  "land" : "QX"
                } ],
                "etternavn" : "Gjenlev",
                "fornavn" : "Lever",
                "kjoenn" : "M",
                "foedselsdato" : "1988-07-12",
                "rolle" : "01"
              },
              "adresse" : {
                "gate" : "Oppoverbakken 66",
                "by" : "SØRUMSAND",
                "postnummer" : "1920",
                "land" : "NO"
              }
            }
          },
          "pensjon" : {
            "anmodning" : {
              "referanseTilPerson" : "01"
            }
          }
        }
        """.trimIndent()

        val result = mockMvc.perform(post("/sed/prefill")
                .contentType(MediaType.APPLICATION_JSON)
                .content(apijson))
                .andDo(print())
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        JSONAssert.assertEquals(response, validResponse, false)

    }


    @Test
    @Throws(Exception::class)
    fun `prefill sed P8000 - Gitt alderpensjon Og henvendelse gjelder søker SÅ skal det produseres en Gyldig P8000 med referanse der søker er gjenlevende`() {
        doReturn(NorskIdent(FNR_VOKSEN_3)).whenever(personService).hentIdent(IdentType.NorskIdent, AktoerId(AKTOER_ID))
        doReturn(AktoerId(AKTOER_ID_2)).whenever(personService).hentIdent(IdentType.AktoerId, NorskIdent(FNR_VOKSEN_4))
        doReturn(PersonPDLMock.createWith(true, "Lever", "Gjenlev", FNR_VOKSEN_3, AKTOER_ID)).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN_3))
        doReturn(PersonPDLMock.createWith(true, "Avdød", "Død", FNR_VOKSEN_4, AKTOER_ID_2, true)).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN_4))

        val v1Kravhistorikk = V1KravHistorikk()
        v1Kravhistorikk.kravArsak = KravArsak.GJNL_SKAL_VURD.name

        val sak = V1Sak()
        sak.sakType = EPSaktype.ALDER.toString()
        sak.sakId = 100
        sak.kravHistorikkListe = V1KravHistorikkListe()
        sak.kravHistorikkListe.kravHistorikkListe.add(v1Kravhistorikk)

        doReturn(sak).whenever(pensjoninformasjonservice).hentRelevantPensjonSak(any(), any())
        doReturn("QX").whenever(kodeverkClient).finnLandkode2(any())

        val subject = dummyApiSubjectjson(FNR_VOKSEN_4)
        val apijson = dummyApijson(sakid = "21337890", aktoerId = AKTOER_ID, sed = "P8000", buc = "P_BUC_05", subject = subject, refperson = "\"SOKER\"")

        val validResponse = """
            {
              "sed" : "P8000",
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
                      "identifikator" : "12312312312",
                      "land" : "NO"
                    } ],
                    "etternavn" : "Gjenlev",
                    "fornavn" : "Lever",
                    "kjoenn" : "M",
                    "foedselsdato" : "1988-07-12"
                  },
                  "adresse" : {
                    "gate" : "Oppoverbakken 66",
                    "by" : "SØRUMSAND",
                    "land" : "NO"
                  }
                }
              },
              "pensjon" : {
                "anmodning" : {
                  "referanseTilPerson" : "02"
                }
              }                      
            }
        """.trimIndent()

        val result = mockMvc.perform(post("/sed/prefill")
                .contentType(MediaType.APPLICATION_JSON)
                .content(apijson))
                .andDo(print())
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        JSONAssert.assertEquals(response, validResponse, false)

    }

    @Test
    @Throws(Exception::class)
    fun `prefill sed P8000 - Gitt barnepensjon og henvendelse gjelder søker som er barn med kode 6 SÅ skal det produseres en Gyldig P8000 med referanse der søker er gjenlevende og adresse ikke blir preutfylt `() {

        val v1Kravhistorikk = V1KravHistorikk()
        v1Kravhistorikk.kravArsak = KravArsak.GJNL_SKAL_VURD.name

        val sak = V1Sak()
        sak.sakType = EPSaktype.BARNEP.toString()
        sak.sakId = 100
        sak.kravHistorikkListe = V1KravHistorikkListe()
        sak.kravHistorikkListe.kravHistorikkListe.add(v1Kravhistorikk)

        whenever((pensjoninformasjonservice).hentRelevantPensjonSak(any(), any())).thenReturn(sak)

        doReturn(NorskIdent(FNR_BARN)).whenever(personService).hentIdent(IdentType.NorskIdent, AktoerId(AKTOER_ID))
        doReturn(AktoerId(AKTOER_ID_2)).whenever(personService).hentIdent(IdentType.AktoerId, NorskIdent(FNR_VOKSEN_4))

        val diskeBarn = PersonPDLMock.createWith(true, "Barn", "Diskret", FNR_BARN, AKTOER_ID)
                            .medBeskyttelse(AdressebeskyttelseGradering.STRENGT_FORTROLIG)
        doReturn(diskeBarn).whenever(personService).hentPerson(NorskIdent(FNR_BARN))
        doReturn(PersonPDLMock.createWith(true, "Avdød", "Død", FNR_VOKSEN_4, AKTOER_ID_2, true)).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN_4))
        doReturn("QX").`when`(kodeverkClient).finnLandkode2(any())

        val subject = dummyApiSubjectjson(FNR_VOKSEN_4)
        val apijson = dummyApijson(sakid = "21337890", aktoerId = AKTOER_ID, sed = "P8000", buc = "P_BUC_05", subject = subject, refperson = "\"SOKER\"")

        val result = mockMvc.perform(post("/sed/prefill")
                .contentType(MediaType.APPLICATION_JSON)
                .content(apijson))
                .andDo(print())
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))

        val validResponse = """
              {
              "sed" : "P8000",
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
                      "identifikator" : "$FNR_VOKSEN_4",
                      "land" : "NO"
                    } ],
                    "etternavn" : "Død",
                    "fornavn" : "Avdød",
                    "kjoenn" : "M",
                    "foedselsdato" : "1921-07-12"
                  },
                  "adresse" : { }
                },
                "annenperson" : {
                  "person" : {
                    "pin" : [ {
                      "institusjonsnavn" : "NOINST002, NO INST002, NO",
                      "institusjonsid" : "NO:noinst002",
                      "identifikator" : "$FNR_BARN",
                      "land" : "NO"
                    } ],
                    "statsborgerskap" : [ {
                      "land" : "QX"
                    } ],
                    "etternavn" : "Diskret",
                    "fornavn" : "Barn",
                    "kjoenn" : "M",
                    "foedselsdato" : "1988-07-12",
                    "rolle" : "01"
                  }
                }
              },
              "pensjon" : {
                "anmodning" : {
                  "referanseTilPerson" : "02"
                }
              }
            }
        """.trimIndent()

        JSONAssert.assertEquals(response, validResponse, false)

    }

    @Test
    @Throws(Exception::class)
    fun `prefill sed P8000 - Gitt en alderspensjon så skal det genereres en P8000 uten referanse til person`() {
        doReturn(NorskIdent(FNR_VOKSEN_3)).whenever(personService).hentIdent(IdentType.NorskIdent, AktoerId(AKTOER_ID))
        doReturn(PersonPDLMock.createWith(true, "Alder", "Pensjon", FNR_VOKSEN_3, AKTOER_ID)).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN_3))
        doReturn("QX").doReturn("XQ").whenever(kodeverkClient).finnLandkode2(any())

        val apijson = dummyApijson(sakid = "21337890", aktoerId = AKTOER_ID, sed = "P8000", buc = "P_BUC_05")

        val validResponse = """
            {
              "sed" : "P8000",
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
                      "identifikator" : "12312312312",
                      "land" : "NO"
                    } ],
                    "etternavn" : "Pensjon",
                    "fornavn" : "Alder",
                    "kjoenn" : "M",
                    "foedselsdato" : "1988-07-12"
                  },
                  "adresse" : {
                    "gate" : "Oppoverbakken 66",
                    "by" : "SØRUMSAND",
                    "land" : "NO"
                  }
                }
              },
              "pensjon" : { }
            }
        """.trimIndent()

        val result = mockMvc.perform(post("/sed/prefill")
                .contentType(MediaType.APPLICATION_JSON)
                .content(apijson))
                .andDo(print())
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()

        val response = result.response.getContentAsString(charset("UTF-8"))
        JSONAssert.assertEquals(response, validResponse, false)
    }


    private fun dummyApijson(sakid: String, vedtakid: String? = "", aktoerId: String, sed: String? = "P2000", buc: String? = "P_BUC_06", subject: String? = null, refperson: String? = null): String {
        return """
            {
              "sakId" : "$sakid",
              "vedtakId" : "$vedtakid",
              "kravId" : null,
              "aktoerId" : "$aktoerId",
              "fnr" : null,
              "avdodfnr" : null,
              "payload" : null,
              "buc" : "$buc",
              "sed" : "$sed",
              "documentid" : null,
              "euxCaseId" : "123123",
              "institutions" : [],
              "subjectArea" : "Pensjon",
              "skipSEDkey" : null,
              "referanseTilPerson" : $refperson,
              "subject" : $subject
            }
        """.trimIndent()
    }

    private fun dummyApiSubjectjson(avdodfnr: String): String {
        return """
            { 
                "gjenlevende" : null, 
                "avdod" : { "fnr": "$avdodfnr"}
            }              
        """.trimIndent()
    }
}

