package no.nav.eessi.pensjon.fagmodul.prefill


import com.ninjasquad.springmockk.MockkBean
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.fagmodul.models.ApiRequest
import no.nav.eessi.pensjon.fagmodul.models.ApiSubject
import no.nav.eessi.pensjon.fagmodul.models.SubjectFnr
import no.nav.eessi.pensjon.fagmodul.prefill.klient.PrefillKlient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentType
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonService
import no.nav.eessi.pensjon.vedlegg.VedleggService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException

private const val AVDOD_FNR = "12345566"
private const val AKTOER_ID = "1122334455"
private const val GJENLEVENDE_FNR = "23123123"
private const val ADVOD_FNR2 = "46784678467"

internal class InnhentingServiceTest {

    @MockK
    lateinit var personService: PersonService

    @MockK
    lateinit var vedleggService: VedleggService

    @MockK
    private lateinit var pensjonsinformasjonService: PensjonsinformasjonService

    @MockK
    lateinit var prefillKlient: PrefillKlient

    @MockkBean
    private lateinit var innhentingService: InnhentingService

    @BeforeEach
    fun before() {
        MockKAnnotations.init(this)
        innhentingService = InnhentingService(personService, vedleggService, prefillKlient, pensjonsinformasjonService)
        innhentingService.initMetrics()
    }

    @Test
    fun `Gitt at avdodfnr finnes paa en p2100 saa skal aktoerid for avdodfnr returneres`() {
        val apiRequest = apiRequest(SedType.P2100, P_BUC_02, AKTOER_ID, AVDOD_FNR)
        every { personService.hentIdent(eq(IdentType.AktoerId), any<Ident<*>>()) } returns AktoerId(AKTOER_ID)

        val result = innhentingService.getAvdodId(BucType.from(apiRequest.buc?.name)!!, apiRequest.riktigAvdod())

        assertEquals(AKTOER_ID, result)
    }

    @Test
    fun `Gitt at avdodfnr finnes paa en p5000 saa skal aktoerid for avdodfnr returneres`() {
        val apiRequest = apiRequest(
                SedType.P5000, P_BUC_02, AKTOER_ID, AVDOD_FNR, GJENLEVENDE_FNR,
                ApiSubject(gjenlevende = SubjectFnr(GJENLEVENDE_FNR), avdod = SubjectFnr(ADVOD_FNR2))
        )

        every { personService.hentIdent(eq(IdentType.AktoerId), any<Ident<*>>()) } returns AktoerId(AKTOER_ID)

        val result = innhentingService.getAvdodId(BucType.from(apiRequest.buc?.name)!!, apiRequest.riktigAvdod())
        assertEquals(AKTOER_ID, result)
    }

    @Test
    fun `Gitt en P2100 mangler fnr saa skal vi kaste en ResponseStatusException`() {
        val apiRequest = apiRequest(SedType.P2100, P_BUC_02, AKTOER_ID)
        assertThrows<ResponseStatusException> {
            innhentingService.getAvdodId(BucType.from(apiRequest.buc?.name)!!, apiRequest.riktigAvdod())
        }
    }

    @Test
    fun `Gitt en P15000 inneholder et ugyldig avdodfnr saa kastes det en ResponseStatusException`() {
        val apiRequest = apiRequest(SedType.P15000, P_BUC_10, AKTOER_ID, AVDOD_FNR)

        assertThrows<ResponseStatusException> {
            innhentingService.getAvdodId(BucType.from(apiRequest.buc?.name)!!, apiRequest.riktigAvdod())
        }
    }

    @Test
    fun `Gitt en P2000 saa skal getAvdodAktoerId returnere null da det ikke skal finnes avdod på en p2000`() {
        val apiRequest = apiRequest(SedType.P2000, P_BUC_01, AKTOER_ID, AVDOD_FNR)

        val result = innhentingService.getAvdodId(BucType.from(apiRequest.buc?.name)!!, apiRequest.avdodfnr)
        assertEquals(null, result)
    }

    private fun apiRequest(sedType: SedType = SedType.P2100, bucType: BucType, aktoerId: String = AKTOER_ID, avdodfnr : String? = null,  vedtakId : String? = null, subject: ApiSubject? = null) =
            ApiRequest(
                    subjectArea = "Pensjon",
                    sakId = "EESSI-PEN-123",
                    sed = sedType,
                    buc = bucType,
                    aktoerId = aktoerId,
                    avdodfnr = avdodfnr,
                    vedtakId = vedtakId,
                    subject = subject
            )

}