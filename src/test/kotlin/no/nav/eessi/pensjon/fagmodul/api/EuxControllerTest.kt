
package no.nav.eessi.pensjon.fagmodul.api

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.SpyK
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.eux.EuxKlient
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestTemplate

class EuxControllerTest {

    @SpyK
    private lateinit var mockEuxInnhentingService: EuxInnhentingService

    private lateinit var euxController: EuxController

    @BeforeEach
    fun before() {
        mockEuxInnhentingService = EuxInnhentingService("Q2", EuxKlient(RestTemplate(), RestTemplate()))

        MockKAnnotations.init(this, relaxed = true, relaxUnitFun = true)

        euxController = EuxController(
            euxInnhentingService = mockEuxInnhentingService
        )
        euxController.initMetrics()

    }


    @Test
    fun `gitt en liste over landkoder over instusjoner fra eux gir tomliste sakl backuplist returneres`() {
        every { mockEuxInnhentingService.getInstitutions(any(), "") } returns emptyList()

        val result = euxController.getPaakobledeland(BucType.P_BUC_06)

        val list = mapJsonToAny(result.body!!, typeRefs<List<String>>())
        assertEquals(30, list.size)
/*
        assertEquals(EuxController.backupList.toString(), list.toString())
*/
    }

    @Test
    fun `gitt en liste over landkoder over instusjoner fra eux med liste så retureres den`() {
        every { mockEuxInnhentingService.getInstitutions(any(), "") } returns listOf(InstitusjonItem("NO", "31231","3123"))

        val result = euxController.getPaakobledeland(BucType.P_BUC_06)

        val list = mapJsonToAny(result.body!!, typeRefs<List<String>>())
        assertEquals(1, list.size)
    }
}


