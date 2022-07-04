package no.nav.eessi.pensjon.fagmodul.prefill


import com.ninjasquad.springmockk.MockkBean
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import no.nav.eessi.pensjon.eux.model.buc.BucType
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
    fun `call getAvdodAktoerId  expect valid aktoerId when avdodfnr exist and sed is P2100`() {
        val apiRequest = ApiRequest(
            subjectArea = "Pensjon",
            sakId = "EESSI-PEN-123",
            sed = "P2100",
            buc = "P_BUC_02",
            aktoerId = "0105094340092",
            avdodfnr = "12345566"
        )
        every { personService.hentIdent(eq(IdentType.AktoerId), any<Ident<*>>()) } returns AktoerId("1122334455")

        val result = innhentingService.getAvdodId(BucType.from(apiRequest.buc)!!, apiRequest.riktigAvdod())
        assertEquals("1122334455", result)
    }

    @Test
    fun `call getAvdodAktoerId  expect valid aktoerId when avdod exist and sed is P5000`() {
        val apiRequest = ApiRequest(
            subjectArea = "Pensjon",
            sakId = "EESSI-PEN-123",
            sed = "P5000",
            buc = "P_BUC_02",
            aktoerId = "0105094340092",
            avdodfnr = "12345566",
            vedtakId = "23123123",
            subject = ApiSubject(gjenlevende = SubjectFnr("23123123"), avdod = SubjectFnr("46784678467"))
        )

        every { personService.hentIdent(eq(IdentType.AktoerId), any<Ident<*>>()) } returns AktoerId("467846784671")

        val result = innhentingService.getAvdodId(BucType.from(apiRequest.buc)!!, apiRequest.riktigAvdod())
        assertEquals("467846784671", result)
    }

    @Test
    fun `call getAvdodAktoerId  expect error when avdodfnr is missing and sed is P2100`() {
        val apiRequest = ApiRequest(
            subjectArea = "Pensjon",
            sakId = "EESSI-PEN-123",
            sed = "P2100",
            buc = "P_BUC_02",
            aktoerId = "0105094340092"
        )
        assertThrows<ResponseStatusException> {
            innhentingService.getAvdodId(BucType.from(apiRequest.buc)!!, apiRequest.riktigAvdod())
        }
    }

    @Test
    fun `call getAvdodAktoerId expect error when avdodfnr is invalid and sed is P15000`() {
        val apiRequest = ApiRequest(
            subjectArea = "Pensjon",
            sakId = "EESSI-PEN-123",
            sed = "P15000",
            buc = "P_BUC_10",
            aktoerId = "0105094340092",
            avdodfnr = "12345566"
        )
        assertThrows<ResponseStatusException> {
            innhentingService.getAvdodId(BucType.from(apiRequest.buc)!!, apiRequest.riktigAvdod())
        }
    }

    @Test
    fun `call getAvdodAktoerId  expect null value when sed is P2000`() {
        val apiRequest = ApiRequest(
            subjectArea = "Pensjon",
            sakId = "EESSI-PEN-123",
            sed = "P2000",
            buc = "P_BUC_01",
            aktoerId = "0105094340092",
            avdodfnr = "12345566"
        )
        val result = innhentingService.getAvdodId(BucType.from(apiRequest.buc)!!, apiRequest.avdodfnr)
        assertEquals(null, result)
    }
}