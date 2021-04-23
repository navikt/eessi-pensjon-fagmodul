
package no.nav.eessi.pensjon.fagmodul.api

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doNothing
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedView
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.eux.EuxPrefillService
import no.nav.eessi.pensjon.fagmodul.eux.SedDokumentIkkeOpprettetException
import no.nav.eessi.pensjon.fagmodul.eux.SedDokumentKanIkkeOpprettesException
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucSedResponse
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ActionsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ConversationsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Organisation
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Sender
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.UserMessagesItem
import no.nav.eessi.pensjon.fagmodul.models.ApiRequest
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.fagmodul.prefill.klient.PrefillKlient
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentType
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonService
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.eessi.pensjon.utils.typeRefs
import no.nav.eessi.pensjon.vedlegg.VedleggService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.Month

@ExtendWith(MockitoExtension::class)
class PrefillControllerTest {

    @Spy
    lateinit var auditLogger: AuditLogger

    @Spy
    lateinit var mockEuxPrefillService: EuxPrefillService

    @Spy
    lateinit var mockEuxInnhentingService: EuxInnhentingService
    
    @Mock
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Mock
    lateinit var vedleggService: VedleggService

    @Mock
    private lateinit var pensjonsinformasjonService: PensjonsinformasjonService

    @Mock
    private lateinit var personService: PersonService

    @Mock
    lateinit var prefillKlient: PrefillKlient

    private lateinit var prefillController: PrefillController

    @BeforeEach
    fun before() {

        val innhentingService = InnhentingService(personService, vedleggService, prefillKlient, pensjonsinformasjonService)
        innhentingService.initMetrics()

        prefillController = PrefillController(
            "default",
            mockEuxPrefillService,
            mockEuxInnhentingService,
            innhentingService,
            auditLogger
        )

        prefillController.initMetrics()
    }

  
    @Test
    fun `createBuc run ok and return id`() {
        val gyldigBuc = javaClass.getResource("/json/buc/buc-279020big.json").readText()
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())

        doReturn("1231231").whenever(mockEuxPrefillService).createBuc("P_BUC_03")
        doReturn(buc).whenever(mockEuxInnhentingService).getBuc(any())

        val excpeted = BucAndSedView.from(buc)
        val actual = prefillController.createBuc("P_BUC_03")

        assertEquals(excpeted.toJson(), actual.toJson())
    }

    @Test
    fun `createBuc run ok and does not run statistics in default namespace`() {
        val gyldigBuc = javaClass.getResource("/json/buc/buc-279020big.json").readText()
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())

        doReturn("1231231").whenever(mockEuxPrefillService).createBuc("P_BUC_03")
        doReturn(buc).whenever(mockEuxInnhentingService).getBuc(any())

        prefillController.createBuc("P_BUC_03")

        verify(kafkaTemplate, times(0)).sendDefault(any(), any())
    }

    @Test
    fun `call addInstutionAndDocument mock adding two institusjon when X005 exists already`() {
        val euxCaseId = "1234567890"

        doReturn(NorskIdent("12345")).whenever(personService).hentIdent(eq(IdentType.NorskIdent), any<AktoerId>())

        val mockParticipants = listOf(ParticipantsItem(role = "CaseOwner", organisation = Organisation(countryCode = "NO", name = "NAV", id = "NAV")))
        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = mockParticipants)
        mockBuc.documents = listOf(createDummyBucDocumentItem(), DocumentsItem(type = SedType.X005))
        mockBuc.actions = listOf(ActionsItem(name = "Send"))

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id, null)

        doReturn(mockBuc).whenever(mockEuxInnhentingService).getBuc(euxCaseId)
        doReturn(BucSedResponse(euxCaseId,"1")).whenever(mockEuxPrefillService).opprettJsonSedOnBuc(any(), any(),eq(euxCaseId),eq(dummyPrefillData.vedtakId))
        doReturn(SED(type = dummyPrefillData.sedType).toJson()).whenever(prefillKlient).hentPreutfyltSed(any())

        val newParticipants = listOf(
            InstitusjonItem(country = "FI", institution = "Finland", name="Finland test"),
            InstitusjonItem(country = "DE", institution = "Tyskland", name="Tyskland test")
        )

        prefillController.addInstutionAndDocument(apiRequestWith(euxCaseId, newParticipants))

        verify(mockEuxPrefillService, times(newParticipants.size  + 1)).opprettJsonSedOnBuc(any(), any(), eq(euxCaseId),eq(dummyPrefillData.vedtakId))
    }

    @Test
    fun `call addInstutionAndDocument mock adding two institusjon when we are not CaseOwner badrequest execption is thrown`() {
        val euxCaseId = "1234567890"

        val mockParticipants = listOf(ParticipantsItem(role = "CaseOwner", organisation = Organisation(countryCode = "SE", name = "SE", id = "SE")))
        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = mockParticipants)
        mockBuc.documents = listOf(createDummyBucDocumentItem(), DocumentsItem(type = SedType.X005))
        mockBuc.actions = listOf(ActionsItem(name = "Send"))

        val newParticipants = listOf(
            InstitusjonItem(country = "FI", institution = "Finland", name="Finland test"),
            InstitusjonItem(country = "DE", institution = "Tyskland", name="Tyskland test")
        )

        doReturn(NorskIdent("12345")).whenever(personService).hentIdent(eq(IdentType.NorskIdent), any<AktoerId>())

        doReturn(mockBuc).whenever(mockEuxInnhentingService).getBuc(euxCaseId)

        val apirequest = apiRequestWith(euxCaseId, newParticipants)
        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id)

        doReturn(SED(type = dummyPrefillData.sedType).toJson()).whenever(prefillKlient).hentPreutfyltSed(any())

        assertThrows<ResponseStatusException> {
            prefillController.addInstutionAndDocument(apirequest)
        }

    }

    @Test
    fun `call addInstutionAndDocument mock check on X007 will fail on matching newparticipants with exception`() {
        val euxCaseId = "1234567890"
        val mockParticipants = listOf(ParticipantsItem(role = "CaseOwner", organisation = Organisation(countryCode = "SE", name = "SE", id = "SE")))
        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = mockParticipants)
        mockBuc.documents = listOf(
            createDummyBucDocumentItem(),
            DocumentsItem(
                type = SedType.X007, status = "received" ,
                conversations = listOf(
                    ConversationsItem(
                        id = "1",
                        userMessages =listOf(
                            UserMessagesItem(
                                sender = Sender(
                                    name = "Danish test",
                                    id = "DK:213231"
                                )
                            )
                        )
                    )
                )
            )
        )
        mockBuc.actions = listOf(ActionsItem(name = "Send"))

        val newParticipants = listOf(
            InstitusjonItem(country = "FI", institution = "FI:213231", name="Finland test"),
            InstitusjonItem(country = "DK", institution = "DK:213231", name="Tyskland test")
        )

        doReturn(NorskIdent("12345")).whenever(personService).hentIdent(eq(IdentType.NorskIdent), any<AktoerId>())
        doReturn(mockBuc).whenever(mockEuxInnhentingService).getBuc(euxCaseId)
        val apirequest = apiRequestWith(euxCaseId, newParticipants)

        assertThrows<ResponseStatusException> {
            prefillController.addInstutionAndDocument(apirequest)
        }
    }

    @Test
    fun `call addInstutionAndDocument  ingen ny Deltaker kun hovedsed`() {
        val euxCaseId = "1234567890"

        doReturn(NorskIdent("12345")).whenever(personService).hentIdent(eq(IdentType.NorskIdent), any<AktoerId>())

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(ParticipantsItem()))
        mockBuc.documents = listOf(createDummyBucDocumentItem())
        mockBuc.actions = listOf(ActionsItem(name = "Send"))

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id, null)

        doReturn(mockBuc).whenever(mockEuxInnhentingService).getBuc(euxCaseId)
        doReturn(SED(type = dummyPrefillData.sedType).toJson()).whenever(prefillKlient).hentPreutfyltSed(any())
        doReturn(BucSedResponse(euxCaseId, "1")).whenever(mockEuxPrefillService).opprettJsonSedOnBuc(any(), any(),eq(euxCaseId),eq(dummyPrefillData.vedtakId))

        val noNewParticipants = listOf<InstitusjonItem>()
        prefillController.addInstutionAndDocument(apiRequestWith(euxCaseId, noNewParticipants))

        verify(mockEuxPrefillService, times(noNewParticipants.size + 1)).opprettJsonSedOnBuc(any(), any(), eq(euxCaseId),eq(dummyPrefillData.vedtakId))
    }

    @Test
    fun `call addDocumentToParent ingen ny Deltaker kun hovedsed`() {
        val euxCaseId = "1100220033"
        val parentDocumentId = "1122334455666"
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        doReturn(NorskIdent("12345")).whenever(personService).hentIdent(eq(IdentType.NorskIdent), any<AktoerId>())

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(ParticipantsItem()), processDefinitionVersion = "4.2")
        mockBuc.documents = listOf(
            DocumentsItem(id = "3123123", type = SedType.P9000, status = "empty", allowsAttachments = true, lastUpdate = lastupdate, creationDate = lastupdate, parentDocumentId = parentDocumentId),
            DocumentsItem(id = parentDocumentId, type = SedType.P8000, status = "received", allowsAttachments = true,  lastUpdate = lastupdate, creationDate = lastupdate)
        )
        mockBuc.actions = listOf(ActionsItem(id = "1000", name = "Received"))

        val api = apiRequestWith(euxCaseId, sed = "P9000", institutions = emptyList())

        val sed = SED(SedType.P9000)

        doReturn(mockBuc).whenever(mockEuxInnhentingService).getBuc(euxCaseId)
        doReturn(sed.toJsonSkipEmpty()).whenever(prefillKlient).hentPreutfyltSed(any())
        doReturn(BucSedResponse(euxCaseId, "3123123")).whenever(mockEuxPrefillService).opprettSvarJsonSedOnBuc(
            any(),
            eq(euxCaseId),
            eq(parentDocumentId),
            eq(api.vedtakId)
        )

        val result = prefillController.addDocumentToParent(api, parentDocumentId)
        val expected = """
        {
          "attachments" : [ ],
          "displayName" : null,
          "type" : "P9000",
          "conversations" : null,
          "isSendExecuted" : null,
          "id" : "3123123",
          "direction" : null,
          "creationDate" : 1596751200000,
          "typeVersion" : null,
          "allowsAttachments" : true,
          "versions" : null,
          "lastUpdate" : 1596751200000,
          "parentDocumentId" : "1122334455666",
          "status" : "empty",
          "participants" : null,
          "firstVersion" : null,
          "lastVersion" : null,
          "version" : "1",
          "message" : null
        }
        """.trimIndent()

        assertEquals(expected, result?.toJson())

        verify(mockEuxInnhentingService, times( 2)).getBuc(any())
        verify(mockEuxPrefillService, times( 1)).opprettSvarJsonSedOnBuc(any(), any(), any(), any())
    }

    @Test
    fun `call addDocumentToParent svarsed finnes kaster exception`()  {
        val euxCaseId = "1100220033"
        val parentDocumentId = "1122334455666"
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        doReturn(NorskIdent("12345")).whenever(personService).hentIdent(eq(IdentType.NorskIdent), any<AktoerId>())

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(ParticipantsItem()), processDefinitionVersion = "4.2")
        mockBuc.documents = listOf(
            DocumentsItem(id = "3123123", type = SedType.P9000, status = "draft", allowsAttachments = true, lastUpdate = lastupdate, creationDate = lastupdate, parentDocumentId = parentDocumentId)
        )

        val api = apiRequestWith(euxCaseId, sed = "P9000", institutions = emptyList())

        doReturn(mockBuc).whenever(mockEuxInnhentingService).getBuc(euxCaseId)

        assertThrows<SedDokumentKanIkkeOpprettesException> {
            prefillController.addDocumentToParent(api, parentDocumentId)
        }
    }

    @Test
    fun `call addInstutionAndDocument valider om SED alt finnes i BUC kaster Exception`() {
        val euxCaseId = "1234567890"

        doReturn(NorskIdent("12345")).whenever(personService).hentIdent(eq(IdentType.NorskIdent), any<AktoerId>())
        val mockBucJson = javaClass.getResource("/json/buc/buc-P_BUC_06-P6000_Sendt.json").readText()
        doReturn( mapJsonToAny(mockBucJson, typeRefs<Buc>())).whenever(mockEuxInnhentingService).getBuc(euxCaseId)
        val apiRequest = apiRequestWith(euxCaseId, emptyList())

        assertThrows<SedDokumentKanIkkeOpprettesException> {
            prefillController.addInstutionAndDocument(apiRequest)
        }

    }

    @Test
    fun `Gitt det opprettes en SED P10000 på tom P_BUC_06 Så skal bucmodel hents på nyt og shortDocument returneres som response`() {
        val euxCaseId = "1234567890"

        doReturn(NorskIdent("12345")).whenever(personService).hentIdent(eq(IdentType.NorskIdent), any<AktoerId>())
        doReturn(SED(SedType.P10000).toJson()).whenever(prefillKlient).hentPreutfyltSed(any())

        val mockBucJson = javaClass.getResource("/json/buc/buc_P_BUC_06_4.2_tom.json").readText()
        val mockBucJson2 = javaClass.getResource("/json/buc/P_BUC_06_P10000.json").readText()

        doReturn( mapJsonToAny(mockBucJson, typeRefs<Buc>()))
            .doReturn( mapJsonToAny(mockBucJson2, typeRefs<Buc>()))
            .whenever(mockEuxInnhentingService).getBuc(euxCaseId)

        val newParticipants = listOf(
            InstitusjonItem(country = "FI", institution = "FI:Finland", name="Finland test")
        )
        val apiRequest = apiRequestWith(euxCaseId, newParticipants, "P10000")

        doReturn(BucSedResponse(euxCaseId, "58c26271b21f4feebcc36b949b4865fe")).whenever(mockEuxPrefillService).opprettJsonSedOnBuc(any(), any(),eq(euxCaseId),eq(apiRequest.vedtakId))
        doNothing().whenever(mockEuxPrefillService).addInstitution(any(), any())

        val result =  prefillController.addInstutionAndDocument(apiRequest)

        verify(mockEuxPrefillService, times(1)).opprettJsonSedOnBuc(any(), any(), eq(euxCaseId),eq(apiRequest.vedtakId))
        verify(mockEuxInnhentingService, times(2)).getBuc(eq(euxCaseId))

        Assertions.assertNotNull(result)
        assertEquals(DocumentsItem::class.java, result?.javaClass)
    }

    @Test
    fun `call addInstutionAndDocument  to nye deltakere, men ingen X005`() {
        val euxCaseId = "1234567890"

        doReturn(NorskIdent("12345")).whenever(personService).hentIdent(eq(IdentType.NorskIdent), any<AktoerId>())

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(ParticipantsItem()))
        mockBuc.documents = listOf(createDummyBucDocumentItem())
        mockBuc.actions = listOf(ActionsItem(name = "Send"))

        doReturn(mockBuc).whenever(mockEuxInnhentingService).getBuc(euxCaseId)

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id, null)
        doNothing().whenever(mockEuxPrefillService).addInstitution(any(), any())

        doReturn(BucSedResponse(euxCaseId,"1")).whenever(mockEuxPrefillService).opprettJsonSedOnBuc(any(), any(), eq(euxCaseId),eq(dummyPrefillData.vedtakId))
        doReturn(SED(type = dummyPrefillData.sedType).toJson()).whenever(prefillKlient).hentPreutfyltSed(any())

        val newParticipants = listOf(
            InstitusjonItem(country = "FI", institution = "FI:Finland", name="Finland test"),
            InstitusjonItem(country = "DE", institution = "DE:Tyskland", name="Tyskland test")
        )
        prefillController.addInstutionAndDocument(apiRequestWith(euxCaseId, newParticipants))

        verify(mockEuxPrefillService, times(1)).addInstitution(any(), any())
        verify(mockEuxPrefillService, times(1)).opprettJsonSedOnBuc(any(), any(), eq(euxCaseId),eq(dummyPrefillData.vedtakId))
    }

    @Test
    fun `call addInstutionAndDocument  Exception eller feiler ved oppretting av SED naar X005 ikke finnes`() {
        val euxCaseId = "1234567890"

        doReturn(NorskIdent("12345")).whenever(personService).hentIdent(eq(IdentType.NorskIdent), any<AktoerId>())

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(ParticipantsItem()))
        mockBuc.documents = listOf(createDummyBucDocumentItem(), DocumentsItem())
        mockBuc.actions = listOf(ActionsItem(name = "Send"))

        doReturn(mockBuc).whenever(mockEuxInnhentingService).getBuc(euxCaseId)
        doNothing().whenever(mockEuxPrefillService).addInstitution(any(), any())

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id, null)
        doReturn(SED(type = dummyPrefillData.sedType).toJson()).whenever(prefillKlient).hentPreutfyltSed(any())
        doThrow(SedDokumentIkkeOpprettetException("Expected!")).whenever(mockEuxPrefillService).opprettJsonSedOnBuc(any(), any(), eq(euxCaseId),eq(dummyPrefillData.vedtakId))

        val newParticipants = listOf(
            InstitusjonItem(country = "FI", institution = "FI:Finland", name="Finland test"),
            InstitusjonItem(country = "DE", institution = "DE:Tyskland", name="Tyskland test")
        )
        assertThrows<SedDokumentIkkeOpprettetException> {
            prefillController.addInstutionAndDocument(apiRequestWith(euxCaseId, newParticipants))
        }
    }

    private fun apiRequestWith(euxCaseId: String, institutions: List<InstitusjonItem> = listOf(), sed: String? = "P6000"): ApiRequest {
        return ApiRequest(
            subjectArea = "Pensjon",
            sakId = "EESSI-PEN-123",
            euxCaseId = euxCaseId,
            vedtakId = "1234567",
            institutions = institutions,
            sed = sed,
            buc = "P_BUC_06",
            aktoerId = "0105094340092"
        )
    }

    private fun createDummyBucDocumentItem() : DocumentsItem {
        return DocumentsItem(
            id = "3123123",
            type = SedType.P6000,
            status = "empty",
            allowsAttachments = true
        )
    }


}

