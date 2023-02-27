package no.nav.eessi.pensjon.fagmodul.eux

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.justRun
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.eux.model.SedType.P2000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.services.statistikk.StatistikkHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.util.UriComponentsBuilder

class EuxPrefillServiceTest {

    private lateinit var euxPrefillService: EuxPrefillService
    private lateinit var euxinnhentingService: EuxInnhentingService

    @MockK(relaxed = true)
    lateinit var euxKlientForSystemUser: EuxKlientAsSystemUser

    var statistikkHandler: StatistikkHandler = mockk()

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        euxPrefillService = EuxPrefillService(euxKlientForSystemUser, statistikkHandler)
        euxinnhentingService = EuxInnhentingService("q2", euxKlientForSystemUser)
        euxPrefillService.initMetrics()
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
        val sed = SED(P2000)
        val bucVersion = "v4.2"

        euxPrefillService.updateSEDVersion(sed, bucVersion)
        assertEquals(bucVersion, "v${sed.sedGVer}.${sed.sedVer}")
    }

    @Test
    fun `update SED Version from old version to same version`() {
        val sed = SED(P2000)
        val bucVersion = "v4.1"

        euxPrefillService.updateSEDVersion(sed, bucVersion)
        assertEquals(bucVersion, "v${sed.sedGVer}.${sed.sedVer}")
    }

    @Test
    fun `update SED Version from old version to unknown new version`() {
        val sed = SED(P2000)
        val bucVersion = "v4.4"

        euxPrefillService.updateSEDVersion(sed, bucVersion)
        assertEquals("v4.1", "v${sed.sedGVer}.${sed.sedVer}")
    }

    @Test
    fun `create buc skal oppdatere statistikk og returnere rinaId`() {
        val euxRinaId =  "12345"
        every { euxKlientForSystemUser.createBuc(any()) } returns euxRinaId
        justRun { statistikkHandler.produserBucOpprettetHendelse(eq( euxRinaId), any()) }

        assertEquals(
            euxRinaId,
            euxPrefillService.createBuc(
                javaClass.getResource("/json/buc/buc-4326040-rina2020docs-P_BUC_01.json")?.readText()!!
            )
        )
    }

}
