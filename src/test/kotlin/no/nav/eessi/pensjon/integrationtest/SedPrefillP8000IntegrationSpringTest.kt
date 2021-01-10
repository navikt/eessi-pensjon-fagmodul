package no.nav.eessi.pensjon.integrationtest

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.fagmodul.personoppslag.BrukerMock
import no.nav.eessi.pensjon.fagmodul.prefill.model.KravType
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerId
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.eessi.pensjon.security.sts.STSService
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import no.nav.pensjon.v1.kravhistorikk.V1KravHistorikk
import no.nav.pensjon.v1.kravhistorikkliste.V1KravHistorikkListe
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.sak.V1Sak
import no.nav.pensjon.v1.vedtak.V1Vedtak
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Diskresjonskoder
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
    lateinit var stsService: STSService

    @MockBean
    lateinit var personV3Service: PersonV3Service

    @MockBean
    lateinit var aktoerService: AktoerregisterService

    @MockBean(name = "pensjonsinformasjonOidcRestTemplate")
    lateinit var restTemplate: RestTemplate

    @MockBean
    lateinit var kodeverkClient: KodeverkClient

    @MockBean
    lateinit var pensjoninformasjonservice: PensjonsinformasjonService

    @Autowired
    private lateinit var mockMvc: MockMvc


    @Test
    @Throws(Exception::class)
    fun `prefill P15000 P_BUC_10 fra vedtakskontekst hvor saktype er GJENLEV og pensjoninformasjon gir BARNEP med GJENLEV`() {

        doReturn(NorskIdent("12312312312")).`when`(aktoerService).hentGjeldendeIdent(IdentGruppe.NorskIdent, AktoerId("0105094340092"))
        doReturn(AktoerId("3323332333233323")).`when`(aktoerService).hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent("9876543210"))

        doReturn(BrukerMock.createWith(true, "Lever", "Gjenlev", "12312312312")).`when`(personV3Service).hentBruker("12312312312")
        doReturn(BrukerMock.createWith(true, "Avdød", "Død", "9876543210", erDod = true)).`when`(personV3Service).hentBruker("9876543210")

        val banrepSak = V1Sak()
        banrepSak.sakType = "BARNEP"
        banrepSak.sakId = 22915555L
        banrepSak.status = "INNV"

        doReturn(banrepSak).`when`(pensjoninformasjonservice).hentRelevantPensjonSak(any(), any())

        val pensjonsinformasjon = Pensjonsinformasjon()
        val avdod = V1Avdod()
        avdod.avdodFar = "9876543210"
        avdod.avdodFarAktorId = "3323332333233323"
        avdod.avdodMor = "12312312441"
        avdod.avdodMorAktorId = "123343242034739845719384257134513"

        pensjonsinformasjon.avdod = avdod
        pensjonsinformasjon.vedtak = V1Vedtak()

        val v1Kravhistorikk = V1KravHistorikk()
        v1Kravhistorikk.kravArsak = KravArsak.GJNL_SKAL_VURD.name

        val sak = V1Sak()
        sak.sakType = EPSaktype.BARNEP.toString()
        sak.sakId = 100
        sak.kravHistorikkListe = V1KravHistorikkListe()
        sak.kravHistorikkListe.kravHistorikkListe.add(v1Kravhistorikk)

        doReturn(pensjonsinformasjon).`when`(pensjoninformasjonservice).hentMedVedtak("123123123")
        doReturn("QX").doReturn("XQ").`when`(kodeverkClient).finnLandkode2(any())

        val apijson =  dummyApijson(sakid = "22915555", vedtakid = "123123123", aktoerId = "0105094340092", sed = "P15000", buc = "P_BUC_10", kravtype = KravType.GJENLEV, kravdato = "01-01-2020", fnravdod = "9876543210")

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
          "sed" : "P15000",
          "sedGVer" : "4",
          "sedVer" : "1",
          "nav" : {
            "eessisak" : [ {
              "institusjonsid" : "NO:noinst002",
              "institusjonsnavn" : "NOINST002, NO INST002, NO",
              "saksnummer" : "22915555",
              "land" : "NO"
            } ],
            "bruker" : {
              "person" : {
                "pin" : [ {
                  "identifikator" : "9876543210",
                  "land" : "NO"
                } ],
                "etternavn" : "Død",
                "fornavn" : "Avdød",
                "kjoenn" : "M",
                "foedselsdato" : "1921-07-12"
              }
            },
            "krav" : {
              "dato" : "01-01-2020",
              "type" : "02"
            }
          },
          "pensjon" : {
            "gjenlevende" : {
              "person" : {
                "pin" : [ {
                  "institusjonsnavn" : "NOINST002, NO INST002, NO",
                  "institusjonsid" : "NO:noinst002",
                  "identifikator" : "12312312312",
                  "land" : "NO"
                } ],
                "statsborgerskap" : [ {
                  "land" : "XQ"
                } ],
                "etternavn" : "Gjenlev",
                "fornavn" : "Lever",
                "kjoenn" : "M",
                "foedselsdato" : "1988-07-12",
                "relasjontilavdod" : {
                  "relasjon" : "06"
                },
                "rolle" : "01"
              },
              "adresse" : {
                "gate" : "Oppoverbakken 66",
                "by" : "SØRUMSAND",
                "postnummer" : "1920",
                "land" : "XQ"
              }
            }
          }
        }
        """.trimIndent()

    JSONAssert.assertEquals(response, validResponse, true)

   }



    @Test
    @Throws(Exception::class)
    fun `prefill sed P8000 - Gitt gjenlevendepensjon Og henvendelse gjelder søker SÅ skal det produseres en Gyldig P8000 med referanse til person 02`() {

        val sak = V1Sak()
        sak.sakType = EPSaktype.GJENLEV.toString()
        sak.sakId = 100
        sak.kravHistorikkListe = V1KravHistorikkListe()

        whenever((pensjoninformasjonservice).hentRelevantPensjonSak(any(), any())).thenReturn(sak)

        doReturn(NorskIdent("12312312312")).`when`(aktoerService).hentGjeldendeIdent(IdentGruppe.NorskIdent, AktoerId("0105094340092"))
        doReturn(AktoerId("3323332333233323")).`when`(aktoerService).hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent("9876543210"))

        doReturn(BrukerMock.createWith(true, "Lever", "Gjenlev", "12312312312")).`when`(personV3Service).hentBruker("12312312312")
        doReturn(BrukerMock.createWith(true, "Avdød", "Død", "9876543210")).`when`(personV3Service).hentBruker("9876543210")

        doReturn("QX").`when`(kodeverkClient).finnLandkode2(any())

        val subject = dummyApiSubjectjson("9876543210")
        val apijson = dummyApijson(sakid = "21337890", aktoerId = "0105094340092", sed = "P8000", buc = "P_BUC_05", subject = subject, refperson = "\"SOKER\"")

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
                      "identifikator" : "9876543210",
                      "land" : "NO"
                    } ],
                    "etternavn" : "Død",
                    "fornavn" : "Avdød",
                    "kjoenn" : "M",
                    "foedselsdato" : "1988-07-12"
                  },
                  "adresse" : {
                    "gate" : "Oppoverbakken 66",
                    "by" : "SØRUMSAND",
                    "land" : "QX"
                  }
                },
                "annenperson" : {
                  "person" : {
                    "pin" : [ {
                      "institusjonsnavn" : "NOINST002, NO INST002, NO",
                      "institusjonsid" : "NO:noinst002",
                      "identifikator" : "12312312312",
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
                    "land" : "QX"
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

        val v1Kravhistorikk = V1KravHistorikk()
        v1Kravhistorikk.kravArsak = KravArsak.GJNL_SKAL_VURD.name

        val sak = V1Sak()
        sak.sakType = EPSaktype.ALDER.toString()
        sak.sakId = 100
        sak.kravHistorikkListe = V1KravHistorikkListe()
        sak.kravHistorikkListe.kravHistorikkListe.add(v1Kravhistorikk)

        whenever((pensjoninformasjonservice).hentRelevantPensjonSak(any(), any())).thenReturn(sak)

        doReturn(NorskIdent("12312312312")).`when`(aktoerService).hentGjeldendeIdent(IdentGruppe.NorskIdent, AktoerId("0105094340092"))
        doReturn(AktoerId("3323332333233323")).`when`(aktoerService).hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent("9876543210"))

        doReturn(BrukerMock.createWith(true, "Lever", "Gjenlev", "12312312312")).`when`(personV3Service).hentBruker("12312312312")
        doReturn(BrukerMock.createWith(true, "Avdød", "Død", "9876543210")).`when`(personV3Service).hentBruker("9876543210")

        doReturn("QX").`when`(kodeverkClient).finnLandkode2(any())

        val subject = dummyApiSubjectjson("9876543210")
        val apijson = dummyApijson(sakid = "21337890", aktoerId = "0105094340092", sed = "P8000", buc = "P_BUC_05", subject = subject, refperson = "\"AVDOD\"")

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
                  "identifikator" : "9876543210",
                  "land" : "NO"
                } ],
                "etternavn" : "Død",
                "fornavn" : "Avdød",
                "kjoenn" : "M",
                "foedselsdato" : "1988-07-12"
              },
              "adresse" : {
                "gate" : "Oppoverbakken 66",
                "by" : "SØRUMSAND",
                "land" : "QX"
              }
            },
            "annenperson" : {
              "person" : {
                "pin" : [ {
                  "institusjonsnavn" : "NOINST002, NO INST002, NO",
                  "institusjonsid" : "NO:noinst002",
                  "identifikator" : "12312312312",
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
                "land" : "QX"
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

        val v1Kravhistorikk = V1KravHistorikk()
        v1Kravhistorikk.kravArsak = KravArsak.GJNL_SKAL_VURD.name

        val sak = V1Sak()
        sak.sakType = EPSaktype.ALDER.toString()
        sak.sakId = 100
        sak.kravHistorikkListe = V1KravHistorikkListe()
        sak.kravHistorikkListe.kravHistorikkListe.add(v1Kravhistorikk)

        whenever((pensjoninformasjonservice).hentRelevantPensjonSak(any(), any())).thenReturn(sak)

        doReturn(NorskIdent("12312312312")).`when`(aktoerService).hentGjeldendeIdent(IdentGruppe.NorskIdent, AktoerId("0105094340092"))
        doReturn(AktoerId("3323332333233323")).`when`(aktoerService).hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent("9876543210"))

        doReturn(BrukerMock.createWith(true, "Lever", "Gjenlev", "12312312312")).`when`(personV3Service).hentBruker("12312312312")
        doReturn(BrukerMock.createWith(true, "Avdød", "Død", "9876543210")).`when`(personV3Service).hentBruker("9876543210")

        doReturn("QX").doReturn("XQ").`when`(kodeverkClient).finnLandkode2(any())

        val subject = dummyApiSubjectjson("9876543210")
        val apijson = dummyApijson(sakid = "21337890", aktoerId = "0105094340092", sed = "P8000", buc = "P_BUC_05", subject = subject, refperson = "\"SOKER\"")

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
                    "land" : "XQ"
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

        doReturn(NorskIdent("12312312312")).`when`(aktoerService).hentGjeldendeIdent(IdentGruppe.NorskIdent, AktoerId("0105094340092"))
        doReturn(AktoerId("3323332333233323")).`when`(aktoerService).hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent("9876543210"))

        val barn = BrukerMock.createWith(true, "Barn", "Diskret", "12312312312")
        barn?.diskresjonskode = Diskresjonskoder().withValue("SPFO")

        doReturn(barn).`when`(personV3Service).hentBruker("12312312312")
        doReturn(BrukerMock.createWith(true, "Avdød", "Død", "9876543210")).`when`(personV3Service).hentBruker("9876543210")

        doReturn("QX").`when`(kodeverkClient).finnLandkode2(any())

        val subject = dummyApiSubjectjson("9876543210")
        val apijson = dummyApijson(sakid = "21337890", aktoerId = "0105094340092", sed = "P8000", buc = "P_BUC_05", subject = subject, refperson = "\"SOKER\"")

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
                      "identifikator" : "9876543210",
                      "land" : "NO"
                    } ],
                    "etternavn" : "Død",
                    "fornavn" : "Avdød",
                    "kjoenn" : "M",
                    "foedselsdato" : "1988-07-12"
                  },
                  "adresse" : {
                    "gate" : "Oppoverbakken 66",
                    "by" : "SØRUMSAND",
                    "land" : "QX"
                  }
                },
                "annenperson" : {
                  "person" : {
                    "pin" : [ {
                      "institusjonsnavn" : "NOINST002, NO INST002, NO",
                      "institusjonsid" : "NO:noinst002",
                      "identifikator" : "12312312312",
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


        doReturn(NorskIdent("12312312312")).`when`(aktoerService).hentGjeldendeIdent(IdentGruppe.NorskIdent, AktoerId("0105094340092"))
        doReturn(BrukerMock.createWith(true, "Alder", "Pensjon", "12312312312")).`when`(personV3Service).hentBruker("12312312312")

        doReturn("QX").doReturn("XQ").`when`(kodeverkClient).finnLandkode2(any())

        val apijson = dummyApijson(sakid = "21337890", aktoerId = "0105094340092", sed = "P8000", buc = "P_BUC_05")

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
                    "land" : "XQ"
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

