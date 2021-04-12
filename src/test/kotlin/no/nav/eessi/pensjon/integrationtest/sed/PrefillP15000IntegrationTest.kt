package no.nav.eessi.pensjon.integrationtest.sed

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.fagmodul.models.KravType
import no.nav.eessi.pensjon.fagmodul.prefill.PersonPDLMock
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.*
import no.nav.eessi.pensjon.security.sts.STSService
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.services.pensjonsinformasjon.EPSaktype
import no.nav.eessi.pensjon.services.pensjonsinformasjon.KravArsak
import no.nav.pensjon.v1.avdod.V1Avdod
import no.nav.pensjon.v1.kravhistorikk.V1KravHistorikk
import no.nav.pensjon.v1.kravhistorikkliste.V1KravHistorikkListe
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.sak.V1Sak
import no.nav.pensjon.v1.vedtak.V1Vedtak
import org.hamcrest.Matchers
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
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(classes = [UnsecuredWebMvcTestLauncher::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc
class PrefillP15000IntegrationTest {

    @MockBean
    lateinit var stsService: STSService

    @MockBean(name = "pensjonsinformasjonOidcRestTemplate")
    lateinit var restTemplate: RestTemplate

    @MockBean
    lateinit var kodeverkClient: KodeverkClient

    @MockBean
    lateinit var pensjoninformasjonservice: PensjonsinformasjonService

    @MockBean
    private lateinit var personService: PersonService

    @Autowired
    private lateinit var mockMvc: MockMvc

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
    fun `prefill P15000 P_BUC_10 fra vedtakskontekst hvor saktype er GJENLEV og pensjoninformasjon gir BARNEP med GJENLEV`() {
        doReturn(NorskIdent(FNR_VOKSEN_3)).whenever(personService).hentIdent(IdentType.NorskIdent, AktoerId(AKTOER_ID))
        doReturn(AktoerId(AKTOER_ID_2)).whenever(personService).hentIdent(IdentType.AktoerId, NorskIdent(FNR_VOKSEN_4))

        doReturn(PersonPDLMock.createWith(true, "Lever", "Gjenlev", FNR_VOKSEN_3, AKTOER_ID)).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN_3))
        doReturn(PersonPDLMock.createWith(true, "Avdød", "Død", FNR_VOKSEN_4, AKTOER_ID_2, true)).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN_4))

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
        doReturn("XQ").`when`(kodeverkClient).finnLandkode2(any())

        val apijson =  dummyApijson(sakid = "22915555", vedtakid = "123123123", aktoerId = AKTOER_ID, sedType = SedType.P15000, buc = "P_BUC_10", kravtype = KravType.GJENLEV, kravdato = "01-01-2020", fnravdod = "9876543210")

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
              },
              "adresse" : {
                "gate" : "Oppoverbakken 66",
                "by" : "SØRUMSAND",
                "postnummer" : "1920",
                "land" : "NO"
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
                "land" : "NO"
              }
            }
          }
        }
        """.trimIndent()

    JSONAssert.assertEquals(response, validResponse, true)

   }

    @Test
    @Throws(Exception::class)
    fun `prefill P15000 P_BUC_10 fra vedtakskontekst hvor saktype er ALDER og pensjoninformasjon returnerer ALDER med GJENLEV`() {

        doReturn(NorskIdent(FNR_VOKSEN_3)).whenever(personService).hentIdent(IdentType.NorskIdent, AktoerId(AKTOER_ID))
        doReturn(AktoerId(AKTOER_ID_2)).whenever(personService).hentIdent(IdentType.AktoerId, NorskIdent(FNR_VOKSEN_4))

        doReturn(PersonPDLMock.createWith(true, "Lever", "Gjenlev", FNR_VOKSEN_3, AKTOER_ID)).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN_3))
        doReturn(PersonPDLMock.createWith(true, "Avdød", "Død", FNR_VOKSEN_4, AKTOER_ID_2, true)).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN_4))


        val aldersak = V1Sak()
        aldersak.sakType = "ALDER"
        aldersak.sakId = 22915555L
        aldersak.status = "INNV"

        doReturn(aldersak).`when`(pensjoninformasjonservice).hentRelevantPensjonSak(any(), any())

        val pensjonsinformasjon = Pensjonsinformasjon()
        val avdod = V1Avdod()
        avdod.avdod = "9876543210"
        avdod.avdodAktorId = "3323332333233323"

        pensjonsinformasjon.avdod = avdod
        pensjonsinformasjon.vedtak = V1Vedtak()

        val v1Kravhistorikk = V1KravHistorikk()
        v1Kravhistorikk.kravArsak = KravArsak.GJNL_SKAL_VURD.name

        val sak = V1Sak()
        sak.sakType = EPSaktype.ALDER.toString()
        sak.sakId = 100
        sak.kravHistorikkListe = V1KravHistorikkListe()
        sak.kravHistorikkListe.kravHistorikkListe.add(v1Kravhistorikk)

        doReturn(pensjonsinformasjon).`when`(pensjoninformasjonservice).hentMedVedtak("123123123")
        doReturn("QX").doReturn("XQ").`when`(kodeverkClient).finnLandkode2(any())

        val apijson =  dummyApijson(sakid = "22915555", vedtakid = "123123123", aktoerId = AKTOER_ID, sedType = SedType.P15000, buc = "P_BUC_10", kravtype = KravType.ALDER, kravdato = "01-01-2020", fnravdod = FNR_VOKSEN_4)

        mockMvc.perform(post("/sed/prefill")
            .contentType(MediaType.APPLICATION_JSON)
            .content(apijson))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()
    }

    @Test
    @Throws(Exception::class)
    fun `prefill P15000 P_BUC_10 hvor saktype er ALDER men data fra pensjonsinformasjon gir UFOREP som resulterer i en bad request`() {
        doReturn(NorskIdent(FNR_VOKSEN)).whenever(personService).hentIdent(IdentType.NorskIdent, AktoerId(AKTOER_ID ))
        doReturn(PersonPDLMock.createWith(true, fnr = FNR_VOKSEN, aktoerid = AKTOER_ID)).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN))


        val aldersak = V1Sak()
        aldersak.sakType = "UFOREP"
        aldersak.sakId = 22874955
        aldersak.status = "INNV"

        doReturn(aldersak).`when`(pensjoninformasjonservice).hentRelevantPensjonSak(any(), any())

        val pensjonsinformasjon = Pensjonsinformasjon()
        pensjonsinformasjon.vedtak = V1Vedtak()
        pensjonsinformasjon.vedtak.vedtakStatus = "INNV"

        doReturn(pensjonsinformasjon).`when`(pensjoninformasjonservice).hentMedVedtak("123123123")

        val apijson = dummyApijson(sakid = "22874955", vedtakid = "123123123", aktoerId = AKTOER_ID, sedType = SedType.P15000, buc = "P_BUC_10", kravtype = KravType.ALDER, kravdato = "01-01-2020")

        mockMvc.perform(post("/sed/prefill")
            .contentType(MediaType.APPLICATION_JSON)
            .content(apijson))
            .andDo(print())
            .andExpect(status().isBadRequest)
            .andExpect(status().reason(Matchers.containsString("Du kan ikke opprette alderspensjonskrav i en uføretrygdsak (PESYS-saksnr: 22874955 har sakstype UFOREP)")))

    }

    @Test
    @Throws(Exception::class)
    fun `prefill P15000 P_BUC_10 hvor saktype er UFOREP men data fra pensjonsinformasjon gir ALDER som resulterer i en bad request`() {
        doReturn(NorskIdent(FNR_VOKSEN)).whenever(personService).hentIdent(IdentType.NorskIdent, AktoerId(AKTOER_ID ))
        doReturn(PersonPDLMock.createWith(true, fnr = FNR_VOKSEN, aktoerid = AKTOER_ID)).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN))


        val aldersak = V1Sak()
        aldersak.sakType = "ALDER"
        aldersak.sakId = 21337890
        aldersak.status = "INNV"

        doReturn(aldersak).`when`(pensjoninformasjonservice).hentRelevantPensjonSak(any(), any())

        val pensjonsinformasjon = Pensjonsinformasjon()
        pensjonsinformasjon.vedtak = V1Vedtak()
        pensjonsinformasjon.vedtak.vedtakStatus = "INNV"

        doReturn(pensjonsinformasjon).`when`(pensjoninformasjonservice).hentMedVedtak("123123123")

        val apijson = dummyApijson(sakid = "21337890", vedtakid = "123123123" , aktoerId = AKTOER_ID, sedType = SedType.P15000, buc = "P_BUC_10", kravtype = KravType.UFOREP, kravdato = "01-01-2020")

        mockMvc.perform(post("/sed/prefill")
            .contentType(MediaType.APPLICATION_JSON)
            .content(apijson))
            .andDo(print())
            .andExpect(status().isBadRequest)
            .andExpect(status().reason(Matchers.containsString("Du kan ikke opprette uføretrygdkrav i en alderspensjonssak (PESYS-saksnr: 21337890 har sakstype ALDER)")))
    }

    @Test
    @Throws(Exception::class)
    fun `prefill P15000 P_BUC_10 hvor saktype er ALDER`() {

        doReturn(NorskIdent(FNR_VOKSEN)).whenever(personService).hentIdent(IdentType.NorskIdent, AktoerId(AKTOER_ID ))
        doReturn(PersonPDLMock.createWith(true, "Lever", "Gjenlev", fnr = FNR_VOKSEN, aktoerid = AKTOER_ID)).whenever(personService).hentPerson(NorskIdent(
            FNR_VOKSEN
        ))


        val aldersak = V1Sak()
        aldersak.sakType = "ALDER"
        aldersak.sakId = 21337890
        aldersak.status = "INNV"

        doReturn(aldersak).`when`(pensjoninformasjonservice).hentRelevantPensjonSak(any(), any())

        val pensjonsinformasjon = Pensjonsinformasjon()
        pensjonsinformasjon.vedtak = V1Vedtak()
        pensjonsinformasjon.vedtak.vedtakStatus = "INNV"

        doReturn(pensjonsinformasjon).`when`(pensjoninformasjonservice).hentMedVedtak("123123123")

        doReturn("QX").doReturn("XQ").`when`(kodeverkClient).finnLandkode2(any())

        val apijson = dummyApijson(sakid = "21337890", vedtakid = "123123123" , aktoerId = AKTOER_ID, sedType = SedType.P15000, buc = "P_BUC_10", kravtype = KravType.ALDER, kravdato = "01-01-2020")

        mockMvc.perform(post("/sed/prefill")
            .contentType(MediaType.APPLICATION_JSON)
            .content(apijson))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()
    }

    @Test
    @Throws(Exception::class)
    fun `prefill P15000 P_BUC_10 hvor saktype er UFOREP`() {

        doReturn(NorskIdent(FNR_VOKSEN)).whenever(personService).hentIdent(IdentType.NorskIdent, AktoerId(AKTOER_ID ))
        doReturn(PersonPDLMock.createWith(true, "Lever", "Gjenlev", fnr = FNR_VOKSEN, aktoerid = AKTOER_ID)).whenever(personService).hentPerson(NorskIdent(
            FNR_VOKSEN
        ))

        val aldersak = V1Sak()
        aldersak.sakType = "UFOREP"
        aldersak.sakId = 22874955
        aldersak.status = "INNV"

        doReturn(aldersak).`when`(pensjoninformasjonservice).hentRelevantPensjonSak(any(), any())

        val pensjonsinformasjon = Pensjonsinformasjon()
        pensjonsinformasjon.vedtak = V1Vedtak()
        pensjonsinformasjon.vedtak.vedtakStatus = "INNV"

        doReturn(pensjonsinformasjon).`when`(pensjoninformasjonservice).hentMedVedtak("123123123")
        doReturn("QX").whenever(kodeverkClient).finnLandkode2(any())

        val apijson = dummyApijson(
            sakid = "22874955", vedtakid = "123123123" ,
            aktoerId = AKTOER_ID, sedType = SedType.P15000, buc = "P_BUC_10", kravtype = KravType.UFOREP, kravdato = "01-01-2020")

        mockMvc.perform(post("/sed/prefill")
            .contentType(MediaType.APPLICATION_JSON)
            .content(apijson))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn()
    }

    @Test
    @Throws(Exception::class)
    fun `prefill P15000 P_BUC_10 hvor saktype er GJENLEV og pensjoninformasjon gir UFOREP med GJENLEV`() {

        doReturn(NorskIdent(FNR_VOKSEN)).whenever(personService).hentIdent(IdentType.NorskIdent, AktoerId(AKTOER_ID))
        doReturn(AktoerId(AKTOER_ID_2)).whenever(personService).hentIdent(IdentType.AktoerId, NorskIdent(FNR_VOKSEN_2))

        doReturn(PersonPDLMock.createWith(true, "Lever", "Gjenlev", FNR_VOKSEN, AKTOER_ID)).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN))
        doReturn(PersonPDLMock.createWith(true, "Avdød", "Død", FNR_VOKSEN_2, AKTOER_ID_2, true)).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN_2))

        val aldersak = V1Sak()
        aldersak.sakType = "UFOREP"
        aldersak.sakId = 22915550
        aldersak.status = "INNV"

        doReturn(aldersak).`when`(pensjoninformasjonservice).hentRelevantPensjonSak(any(), any())

        val pensjonsinformasjon = Pensjonsinformasjon()
        pensjonsinformasjon.vedtak = V1Vedtak()
        pensjonsinformasjon.vedtak.vedtakStatus = "INNV"

        doReturn(pensjonsinformasjon).`when`(pensjoninformasjonservice).hentMedVedtak("123123123")
        doReturn("QX").doReturn("XQ").`when`(kodeverkClient).finnLandkode2(any())

        val apijson = dummyApijson(sakid = "22915550", vedtakid = "123123123", aktoerId = AKTOER_ID, sedType =SedType.P15000, buc = "P_BUC_10", kravtype = KravType.GJENLEV, kravdato = "01-01-2020", fnravdod = FNR_VOKSEN_2)

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
                  "saksnummer" : "22915550",
                  "land" : "NO"
                } ],
                "bruker" : {
                  "person" : {
                    "pin" : [ {
                      "identifikator" : "$FNR_VOKSEN_2",
                      "land" : "NO"
                    } ],
                    "etternavn" : "Død",
                    "fornavn" : "Avdød",
                    "kjoenn" : "M",
                    "foedselsdato" : "1921-07-12"
                  },
                  "adresse" : {
                    "gate" : "Oppoverbakken 66",
                    "by" : "SØRUMSAND",
                    "postnummer" : "1920",
                    "land" : "NO"
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
                      "identifikator" : "$FNR_VOKSEN",
                      "land" : "NO"
                    } ],
                    "statsborgerskap" : [ {
                      "land" : "XQ"
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
              }
            }
        """.trimIndent()

        JSONAssert.assertEquals(response, validResponse, true)

    }

    @Test
    @Throws(Exception::class)
    fun `prefill P15000 P_BUC_10 hvor saktype er GJENLEV og pensjoninformasjon gir BARNEP med GJENLEV`() {
        doReturn(NorskIdent(FNR_VOKSEN)).whenever(personService).hentIdent(IdentType.NorskIdent, AktoerId(AKTOER_ID))
        doReturn(AktoerId(AKTOER_ID_2)).whenever(personService).hentIdent(IdentType.AktoerId, NorskIdent(FNR_VOKSEN_2))

        doReturn(PersonPDLMock.createWith(true, "Lever", "Gjenlev", FNR_VOKSEN, AKTOER_ID)).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN))

        val avdodperson = PersonPDLMock.createWith(true, "Avdød", "Død", FNR_VOKSEN_2, AKTOER_ID_2, true)
            .copy(bostedsadresse = null, oppholdsadresse = null, kontaktadresse = null, kontaktinformasjonForDoedsbo = KontaktinformasjonForDoedsbo(
                KontaktinformasjonForDoedsboAdresse(
                    "dødsboadresse1",
                    "adresselinje2",
                    "SWE",
                    "2312",
                    "Langegatan 121"
                ),
                LocalDate.of(2010, 10,1 ),
                Folkeregistermetadata(LocalDateTime.of(2010, 10, 1, 10, 1, 2)),
                no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata(
                    emptyList(),
                    false,
                    "DOLLY",
                    "123123-123123-12--312312-312-31-23-123-1-31-23-123-12-31-23-123-12-3-123"
                ),
                KontaktinformasjonForDoedsboSkifteform.ANNET
            ))

        doReturn(avdodperson).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN_2))

        val aldersak = V1Sak()
        aldersak.sakType = "UFOREP"
        aldersak.sakId = 22915555
        aldersak.status = "INNV"

        doReturn(aldersak).`when`(pensjoninformasjonservice).hentRelevantPensjonSak(any(), any())

        val pensjonsinformasjon = Pensjonsinformasjon()
        pensjonsinformasjon.vedtak = V1Vedtak()
        pensjonsinformasjon.vedtak.vedtakStatus = "INNV"

        val avdod = V1Avdod()
        avdod.avdodFar = FNR_VOKSEN_2
        avdod.avdodFarAktorId = AKTOER_ID_2
        avdod.avdodMor = "12312312441"
        avdod.avdodMorAktorId = "123343242034739845719384257134513"
        pensjonsinformasjon.avdod = avdod

        doReturn(pensjonsinformasjon).`when`(pensjoninformasjonservice).hentMedVedtak("123123123")
        doReturn("QX").doReturn("XQ").`when`(kodeverkClient).finnLandkode2(any())
        doReturn("SE").whenever(kodeverkClient).finnLandkode2("SWE")

        val apijson = dummyApijson(sakid = "22915555", vedtakid = "123123123", aktoerId = AKTOER_ID, sedType = SedType.P15000, buc = "P_BUC_10", kravtype = KravType.GJENLEV, kravdato = "01-01-2020", fnravdod = FNR_VOKSEN_2)

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
                      "identifikator" : "$FNR_VOKSEN_2",
                      "land" : "NO"
                    } ],
                    "etternavn" : "Død",
                    "fornavn" : "Avdød",
                    "kjoenn" : "M",
                    "foedselsdato" : "1921-07-12"
                  },
                  "adresse" : {
                    "gate" : "dødsboadresse1",
                    "bygning" : "adresselinje2",
                    "by" : "Langegatan 121",
                    "postnummer" : "2312",
                    "land": "SE"
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
                      "identifikator" : "$FNR_VOKSEN",
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
                    "land" : "NO"
                  }
                }
              }
            }
        """.trimIndent()

        JSONAssert.assertEquals(response, validResponse, true)

    }

}

