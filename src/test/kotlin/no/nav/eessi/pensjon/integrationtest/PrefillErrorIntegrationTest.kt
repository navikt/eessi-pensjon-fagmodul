package no.nav.eessi.pensjon.integrationtest

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.fagmodul.prefill.PersonPDLMock
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentType
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.security.sts.STSService
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.services.pensjonsinformasjon.EPSaktype
import no.nav.pensjon.v1.kravhistorikk.V1KravHistorikk
import no.nav.pensjon.v1.kravhistorikkliste.V1KravHistorikkListe
import no.nav.pensjon.v1.sak.V1Sak
import no.nav.pensjon.v1.vedtak.V1Vedtak
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@SpringBootTest(classes = [UnsecuredWebMvcTestLauncher::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@AutoConfigureMockMvc
class PrefillErrorIntegrationTest {

    @MockBean
    lateinit var stsService: STSService

    @MockBean
    lateinit var kodeverkClient: KodeverkClient

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var pensjoninformasjonservice: PensjonsinformasjonService

    @MockBean
    lateinit var personService: PersonService

    private companion object {
        const val SAK_ID = "12345"
        const val FNR_VOKSEN = "11067122781"    // KRAFTIG VEGGPRYD
        const val AKTOER_ID = "0123456789000"
    }

    @Test
    @Throws(Exception::class)
    fun `prefill sed P2200 med vedtak, F_BH_BO_UTL og F_BH_MED_UTL mangler samt vedtak isBoddArbeidetUtland er false skal Exception`() {

        doReturn(NorskIdent(FNR_VOKSEN)).`when`(personService).hentIdent(IdentType.NorskIdent, AktoerId(AKTOER_ID))
        doReturn(PersonPDLMock.createWith()).whenever(personService).hentPerson(NorskIdent(FNR_VOKSEN))

        val sak = V1Sak()
        sak.sakType = EPSaktype.UFOREP.toString()
        sak.sakId = 100
        sak.kravHistorikkListe = V1KravHistorikkListe()
        val krav = V1KravHistorikk()
        krav.kravType = "REVURD"
        krav.kravId = "1"
        krav.status = "INNV"
        sak.kravHistorikkListe.kravHistorikkListe.add(krav)

        doReturn(sak).whenever(pensjoninformasjonservice).hentRelevantPensjonSak(any(), any())

        val vedtak = V1Vedtak()
        vedtak.isBoddArbeidetUtland = false
        vedtak.kravGjelder = "REVURD"

        doReturn(vedtak).whenever(pensjoninformasjonservice).hentRelevantVedtakHvisFunnet("231231231")

        val apijson = dummyApijson(sakid = "1232123123", aktoerId = AKTOER_ID, vedtakid = "231231231", sed = "P2200",  buc = "P_BUC_03")
        val expectedError = """Du kan ikke opprette krav-SED P2200 hvis ikke "bodd/arbeidet i utlandet" er krysset av""".trimIndent()

        mockMvc.perform(
            MockMvcRequestBuilders.post("/sed/prefill")
            .contentType(MediaType.APPLICATION_JSON)
            .content(apijson))
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.status().reason(Matchers.containsString(expectedError)))

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



}