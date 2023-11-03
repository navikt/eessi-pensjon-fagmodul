package no.nav.eessi.pensjon.vedlegg



import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.vedlegg.client.Data
import no.nav.eessi.pensjon.vedlegg.client.DokumentoversiktBruker
import no.nav.eessi.pensjon.vedlegg.client.EuxVedleggClient
import no.nav.eessi.pensjon.vedlegg.client.HentMetadataResponse
import no.nav.eessi.pensjon.vedlegg.client.SafClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestTemplate


internal class VedleggServiceTest  {

    var safClient : SafClient = mockk()

    lateinit var vedleggService : VedleggService

    @BeforeEach
    fun setup() {
        val euxVedleggClient = EuxVedleggClient(RestTemplate())
        vedleggService = VedleggService(safClient, euxVedleggClient)
    }

    @Test
    fun `Gitt en liste av journalposter med tilhørende dokumenter Når man filtrer et konkret dokumentInfoId Så returner et dokument med dokumentInfoId`() {

        val metadataJson = javaClass.getResource("/json/saf/hentMetadataResponse.json").readText()
        val metadata = mapJsonToAny<HentMetadataResponse>(metadataJson)

        every {safClient.hentDokumentMetadata(any())  } returns metadata

        assert(vedleggService.hentDokumentMetadata("12345678910", "439560100", "453743887")?.dokumentInfoId == "453743887")
    }

    @Test
    fun `Gitt en liste av journalposter der listen er tom Så returner verdien null`() {

        val metadataJson = """
            {
              "data": {
                "dokumentoversiktBruker": {
                  "journalposter": []
                }
              }
            }
        """.trimIndent()
        val metadata = mapJsonToAny<HentMetadataResponse>(metadataJson)

        every {safClient.hentDokumentMetadata(any())  } returns metadata

        assert(vedleggService.hentDokumentMetadata("12345678910", "123", "123") == null)
    }

    @Test
    fun `Gitt en liste av journalpostermed et dokument Så returner dokumentet`() {

        val metadataJson = """
            {
              "data": {
                "dokumentoversiktBruker": {
                  "journalposter": [
                    {
                      "tilleggsopplysninger": [
                        {
                        "nokkel":"eessi_pensjon_bucid",
                        "verdi":"1111"
                        }
                      ],
                      "journalpostId": "439532144",
                      "datoOpprettet": "2018-06-08T17:06:58",
                      "tittel": "journalpost tittel",
                      "tema": "SYK",
                      "dokumenter": [
                        {
                          "dokumentInfoId": "453708906",
                          "tittel": "P2000 alderpensjon",
                          "dokumentvarianter": [
                            {
                              "filnavn": "enfil.pdf",
                              "variantformat": "ARKIV"
                            },
                            {
                              "filnavn": "enfil.png",
                              "variantformat": "PRODUKSJON"
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
              }
            }
        """.trimIndent()
        val metadata = mapJsonToAny<HentMetadataResponse>(metadataJson)

        every {safClient.hentDokumentMetadata(any())  } returns metadata

        assert(vedleggService.hentDokumentMetadata("12345678910", "439532144", "453708906")?.tittel == "P2000 alderpensjon" )
    }

    @Test
    fun testHentRinaIderFraMetadata() {
        val aktoerId = "12345"

        val metadataJson = javaClass.getResource("/json/saf/hentMetadataResponse.json").readText()
        val metadata = mapJsonToAny<HentMetadataResponse>(metadataJson)

        every {safClient.hentDokumentMetadata(any())  } returns metadata

        val result = vedleggService.hentRinaSakIderFraMetaData(aktoerId)
        assert(result.size == 1)
    }

    @Test
    fun `Skal return en tom liste ved ingen metadata i dokumenter på aktørid`() {
        val aktoerId = "12345"

        every {safClient.hentDokumentMetadata(any())  } returns HentMetadataResponse(Data(DokumentoversiktBruker(emptyList())))

        val result = vedleggService.hentRinaSakIderFraMetaData(aktoerId)
        assert(result.isEmpty())
    }
}

