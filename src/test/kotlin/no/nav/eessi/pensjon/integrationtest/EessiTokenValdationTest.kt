package no.nav.eessi.pensjon.integrationtest

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
import io.mockk.every
import no.nav.eessi.pensjon.UnsecuredWebMvcTestLauncher
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.fagmodul.config.RestTemplateConfig
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.pensjonsinformasjon.clients.PensjonsinformasjonClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.vedlegg.client.EuxVedleggClient
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.client.RestTemplate
import java.io.ByteArrayOutputStream
import java.util.*

@SpringBootTest(
    classes = [RestTemplateConfig::class, UnsecuredWebMvcTestLauncher::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles(profiles = ["unsecured-webmvctest"])
@EmbeddedKafka
@EnableMockOAuth2Server
@AutoConfigureMockMvc
@MockkBeans(
    MockkBean(name = "kodeverkRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "prefillOAuthTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "euxSystemRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "safRestOidcRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "pdlRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "euxNavIdentRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "restEuxTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "safGraphQlOidcRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "pensjonsinformasjonClient", classes = [PensjonsinformasjonClient::class]),
    MockkBean(name = "personService", classes = [PersonService::class]),
    MockkBean(name = "gcpStorageService", classes = [GcpStorageService::class]),
    MockkBean(name = "euxKlient", classes = [EuxKlientAsSystemUser::class])
)
class EessiTokenValdationTest {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var euxNavIdentRestTemplate: RestTemplate

    @Autowired
    private lateinit var euxVedleggClient: EuxVedleggClient

    @Test
    fun `se at applikasjonen starter med prod konfig og at tilstanden ok`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `Konfigurasjon skal håndtere en pdf med dokumenter større enn 20b`() {
        val mockedResponse = "Expected response content"
        val queryUrl = "/buc/rina123/sed/doc123/vedlegg?Filnavn=example&Filtype=application/pdf&synkron=true"

        every {
            euxNavIdentRestTemplate.exchange(
                queryUrl,
                HttpMethod.POST,
                any(),
                String::class.java
            )
        } returns ResponseEntity.ok(mockedResponse)

        euxVedleggClient.leggTilVedleggPaaDokument(
            aktoerId = "123456789",
            rinaSakId = "rina123",
            rinaDokumentId = "doc123",
            filInnhold = Base64.getEncoder().encodeToString(createLargePdf(200000)),
            fileName = "example.pdf",
            filtype = "application/pdf"
        )
    }

    fun createLargePdf(numberOfPages: Int): ByteArray {
        val documentRet = PDDocument()
        val outputStream = ByteArrayOutputStream()

        try {
            val fontStream = this::class.java.getResourceAsStream("/fonts/helvetica-light.ttf")
            val font = PDType0Font.load(documentRet, fontStream)

            for (i in 1..numberOfPages) {
                val page = PDPage(PDRectangle.LETTER)
                documentRet.addPage(page)

                PDPageContentStream(documentRet, page).use { contentStream ->
                    contentStream.beginText()
                    contentStream.setFont(font, 12f) // Set the font properly
                    contentStream.newLineAtOffset(50f, 750f)
                    contentStream.showText("This is page $i of $numberOfPages")
                    contentStream.endText()
                }
            }

            documentRet.save(outputStream)
        } finally {
            documentRet.close()
        }
        val sizeInBytes = outputStream.size()
        println("PDF size in memory: $sizeInBytes bytes (${sizeInBytes / 1024 / 1024} MB)")
        return outputStream.toByteArray() // returner en tom PDF hvis noe går galt
    }
}
