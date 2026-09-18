package no.nav.eessi.pensjon.fagmodul.api

import io.mockk.mockk
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.fagmodul.api.vedlegg.VedleggService
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.gcp.GcpStorageService
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.web.client.RestTemplate

@TestConfiguration
class EuxInnhentingServiceTestConfig {
    @Bean
    fun euxInnhentingService(
        euxKlient: EuxKlientAsSystemUser,
        gcpStorageService: GcpStorageService,
        euxNavIdentRestTemplateV2: RestTemplate
    ): EuxInnhentingService = EuxInnhentingService(
        environment = "q2",
        euxKlient = euxKlient,
        gcpService = gcpStorageService,
        euxNavIdentRestTemplateV2 = euxNavIdentRestTemplateV2,
        vedleggService = mockk<VedleggService>(relaxed = true)
    )
}
