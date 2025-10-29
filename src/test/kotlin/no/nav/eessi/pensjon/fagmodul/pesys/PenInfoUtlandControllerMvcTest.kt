package no.nav.eessi.pensjon.fagmodul.pesys

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.eux.model.SedMetadata
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.kodeverk.Postnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(PensjonsinformasjonUtlandController::class)
@ComponentScan(basePackages = ["no.nav.eessi.pensjon.fagmodul.pesys"])
@ActiveProfiles("unsecured-webmvctest")
class PenInfoUtlandControllerMvcTest {

    @Autowired
    lateinit var mvc: MockMvc

    @MockkBean
    lateinit var pensjonsinformasjonUtlandService: PensjonsinformasjonUtlandService

    @MockkBean
    lateinit var gcpStorageService: GcpStorageService

    @MockkBean
    lateinit var euxInnhentingService: EuxInnhentingService

    @MockkBean
    lateinit var kodeverkClient: KodeverkClient

    @MockkBean
    lateinit var trygdeTidService: HentTrygdeTid

    @MockkBean
    lateinit var euxKlientLib: EuxKlientLib

    @TestConfiguration
    class Config {
        @Bean
        fun gcpStorageService(): GcpStorageService {
            return GcpStorageService("gjenny", "p8000", "p6000", "saksApi",
                mockk())
        }
    }

    @Test
    fun `avdodsdato sjekk for vedtak inneholder to avdod i pbuc06 og P5000 returneres den tidligere valgte avdod ut fra P5000 og returneres`() {
        val gcpDetlajerP6000 = """
            {
              "pesysId" : "22580170",
              "rinaSakId" : "1446704",
              "dokumentId" : [ "a6bacca841cf4c7195d694729151d4f3", "b152e3cf041a4b829e56e6b1353dd8cb" ]
            }
        """.trimIndent()

        val metadata = SedMetadata(sedTittel = "Vedtak om pensjon", sedType = "P6000", sedId = "a6bacca841cf4c7195d694729151d4f3", status = "sent")

        every { gcpStorageService.hentGcpDetlajerForP6000(any())} returns gcpDetlajerP6000
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser(any(), any())
        } returns javaClass.getResource("/json/sed/${"P6000-InnvilgedePensjoner.json"}")?.readText()
            ?.let { json -> mapJsonToAny<P6000>(json) }!!
        every { kodeverkClient.hentPostSted(any()) } returns Postnummer("123456", "Oslo")
        every { euxKlientLib.hentSedMetadata(any(), any()) } returns metadata

        val repsonse = mvc.perform(
            MockMvcRequestBuilders.get("/pesys/hentP6000Detaljer")
                .param("pesysId", "22580170")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful)
                .andExpect(status().isOk())
                .andReturn()
        println(repsonse.response.contentAsString)
        //sakstype
        assert( repsonse.response.contentAsString.contains(""""sakstype":"Gjenlevende""""))
        //vurderingsperiode
        assert( repsonse.response.contentAsString.contains("""six weeks from the date the decision is received"""))
        //institusjon
        assert( repsonse.response.contentAsString.contains(""""institusjonsid":"NO:NAVAT07","institusjonsnavn":"NAV ACCEPTANCE TEST 07","saksnummer":"1003563","land":"NO""""))
        //VurderingNyAdresse
        assert( repsonse.response.contentAsString.contains("""institusjonsadresse":"Postboks 6600 Etterstad","postnummer":"0607","bygningsnavn":null,"land":"NO","region":null,"poststed":"Oslo"""))
        //innehaver
        assert( repsonse.response.contentAsString.contains("""innehaver":{"fornavn":"KOGNITIV","etternavn":"AKROBAT","etternavnVedFoedsel":null,"foedselsdato":"1986-08-16","adresselinje":null,"poststed":"Oslo","postnummer":"1554","landkode":"NO","pin":[{"institusjonsnavn":"NAV ACCEPTANCE TEST 07","institusjonsid":"NO:NAVAT07","sektor":null,"identifikator":"16888697822","land":"NO","institusjon":null}]}"""))
    }
}