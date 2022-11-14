package no.nav.eessi.pensjon.fagmodul.eux

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.fagmodul.eux.EuxTestUtils.Companion.apiRequestWith
import no.nav.eessi.pensjon.services.statistikk.StatistikkHandler
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.util.UriComponentsBuilder

class EuxPrefillServiceTest {

    private lateinit var euxPrefillService: EuxPrefillService
    private lateinit var euxinnhentingService: EuxInnhentingService

    @MockK(relaxed = true)
    lateinit var euxKlient: EuxKlient

    var statistikkHandler: StatistikkHandler = mockk()

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        euxPrefillService = EuxPrefillService(euxKlient, statistikkHandler)
        euxinnhentingService = EuxInnhentingService("q2", euxKlient)
    }

    @Test
    fun `Opprett Uri component path`() {
        val path = "/type/{RinaSakId}/sed"
        val uriParams = mapOf("RinaSakId" to "12345")
        val builder = UriComponentsBuilder.fromUriString(path)
                .queryParam("KorrelasjonsId", "c0b0c068-4f79-48fe-a640-b9a23bf7c920")
                .buildAndExpand(uriParams)
        val str = builder.toUriString()
        assertEquals("/type/12345/sed?KorrelasjonsId=c0b0c068-4f79-48fe-a640-b9a23bf7c920", str)
    }

    @Test
    fun `update SED Version from old version to new version`() {
        val sed = SED(SedType.P2000)
        val bucVersion = "v4.2"

        euxPrefillService.updateSEDVersion(sed, bucVersion)
        assertEquals(bucVersion, "v${sed.sedGVer}.${sed.sedVer}")
    }

    @Test
    fun `update SED Version from old version to same version`() {
        val sed = SED(SedType.P2000)
        val bucVersion = "v4.1"

        euxPrefillService.updateSEDVersion(sed, bucVersion)
        assertEquals(bucVersion, "v${sed.sedGVer}.${sed.sedVer}")
    }

    @Test
    fun `update SED Version from old version to unknown new version`() {
        val sed = SED(SedType.P2000)
        val bucVersion = "v4.4"

        euxPrefillService.updateSEDVersion(sed, bucVersion)
        assertEquals("v4.1", "v${sed.sedGVer}.${sed.sedVer}")
    }

    @Test
    fun `check apiRequest for prefill X010 contains X009 payload`() {
        val x009Json = javaClass.getResource("/json/nav/X009-NAV.json").readText()
        every { euxKlient.getSedOnBucByDocumentIdAsJson(any(), any()) } returns x009Json

        val apiRequest = apiRequestWith("1000000", emptyList(), buc = "P_BUC_01", sed = "X010")

        val json = euxinnhentingService.checkForX010AndAddX009(apiRequest, "20000000").toJson()

        val payload = """
                  "payload" : "{\n  \"sed\" : \"X009\",\n  \"nav\" : {\n    \"sak\" : {\n      \"kontekst\" : {\n        \"bruker\" : {\n          \"mor\" : null,\n          \"far\" : null,\n          \"person\" : {\n            \"pin\" : null,\n            \"pinland\" : null,\n            \"statsborgerskap\" : null,\n            \"etternavn\" : \"æøå\",\n            \"etternavnvedfoedsel\" : null,\n            \"fornavn\" : \"æøå\",\n            \"fornavnvedfoedsel\" : null,\n            \"tidligerefornavn\" : null,\n            \"tidligereetternavn\" : null,\n            \"kjoenn\" : \"M\",\n            \"foedested\" : null,\n            \"foedselsdato\" : \"æøå\",\n            \"sivilstand\" : null,\n            \"relasjontilavdod\" : null,\n            \"rolle\" : null\n          },\n          \"adresse\" : null,\n          \"arbeidsforhold\" : null,\n          \"bank\" : null\n        },\n        \"refusjonskrav\" : {\n          \"antallkrav\" : \"æøå\",\n          \"id\" : \"æøå\"\n        },\n        \"arbeidsgiver\" : {\n          \"identifikator\" : [ {\n            \"id\" : \"æøå\",\n            \"type\" : \"registrering\"\n          } ],\n          \"adresse\" : {\n            \"gate\" : \"æøå\",\n            \"bygning\" : \"æøå\",\n            \"by\" : \"æøå\",\n            \"postnummer\" : \"æøå\",\n            \"postkode\" : null,\n            \"region\" : \"æøå\",\n            \"land\" : \"NO\",\n            \"kontaktpersonadresse\" : null,\n            \"datoforadresseendring\" : null,\n            \"postadresse\" : null,\n            \"startdato\" : null,\n            \"type\" : null,\n            \"annen\" : null\n          },\n          \"navn\" : \"æøå\"\n        }\n      },\n      \"leggtilinstitusjon\" : null,\n      \"paaminnelse\" : {\n        \"svar\" : null,\n        \"sende\" : [ {\n          \"type\" : \"dokument\",\n          \"detaljer\" : \"æøå\"\n        } ]\n      }\n    }\n  },\n  \"sedGVer\" : \"4\",\n  \"sedVer\" : \"2\",\n  \"pensjon\" : null\n}",
        """.trimIndent()

        assert(json.contains(payload))
    }

}
