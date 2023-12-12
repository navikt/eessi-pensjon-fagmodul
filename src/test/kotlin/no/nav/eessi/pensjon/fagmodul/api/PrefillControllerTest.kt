
package no.nav.eessi.pensjon.fagmodul.api

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import no.nav.eessi.pensjon.eux.klient.BucSedResponse
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.eux.klient.SedDokumentIkkeOpprettetException
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_03
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.X007
import no.nav.eessi.pensjon.eux.model.buc.*
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedView
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.eux.EuxPrefillService
import no.nav.eessi.pensjon.fagmodul.eux.EuxTestUtils.Companion.apiRequestWith
import no.nav.eessi.pensjon.fagmodul.eux.EuxTestUtils.Companion.createDummyBucDocumentItem
import no.nav.eessi.pensjon.fagmodul.eux.SedDokumentKanIkkeOpprettesException
import no.nav.eessi.pensjon.fagmodul.prefill.InnhentingService
import no.nav.eessi.pensjon.fagmodul.prefill.klient.PrefillKlient
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonService
import no.nav.eessi.pensjon.services.statistikk.StatistikkHandler
import no.nav.eessi.pensjon.shared.api.ApiRequest
import no.nav.eessi.pensjon.shared.api.InstitusjonItem
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.eessi.pensjon.vedlegg.VedleggService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.Month

internal class PrefillControllerTest {

    @SpyK
    private var auditLogger: AuditLogger = AuditLogger()

    @SpyK
    private lateinit var mockEuxPrefillService: EuxPrefillService

    private  val mockEuxKlient: EuxKlientAsSystemUser = mockk()

    @SpyK
    private var mockEuxInnhentingService: EuxInnhentingService = EuxInnhentingService("Q2", mockEuxKlient)

    @MockK
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @MockK
    private lateinit var vedleggService: VedleggService

    @MockK
    private lateinit var personService: PersonService

    @MockK
    private lateinit var pensjonsinformasjonService: PensjonsinformasjonService

    @MockK
    private lateinit var prefillKlient: PrefillKlient

    private lateinit var prefillController: PrefillController

    @BeforeEach
    fun before() {
        mockEuxPrefillService = EuxPrefillService(mockEuxKlient,
            StatistikkHandler( KafkaTemplate(DefaultKafkaProducerFactory(emptyMap())), "")
        )

        MockKAnnotations.init(this, relaxed = true)

        val innhentingService = InnhentingService(personService, vedleggService, prefillKlient, pensjonsinformasjonService)
        prefillController = PrefillController(
            mockEuxPrefillService,
            mockEuxInnhentingService,
            innhentingService,
            mockk(relaxed = true),
            auditLogger
        )
    }


    @Test
    fun `createBuc run ok and return id`() {
        val gyldigBuc = javaClass.getResource("/json/buc/buc-279020big.json")!!.readText()
        val buc : Buc =  mapJsonToAny(gyldigBuc)

        every { mockEuxPrefillService.createdBucForType(P_BUC_03.name) } returns "1231231"
        every { mockEuxInnhentingService.getBuc(any()) } returns buc

        val expected = BucAndSedView.from(buc)
        val actual = prefillController.createBuc(P_BUC_03.name, null)

        assertEquals(expected.toJson(), actual.toJson())
    }

    @Test
    fun `createBuc run ok and does not run statistics in default namespace`() {
        val gyldigBuc = javaClass.getResource("/json/buc/buc-279020big.json")!!.readText()
        val buc : Buc =  mapJsonToAny(gyldigBuc)

        every {  mockEuxPrefillService.createdBucForType(P_BUC_03.name)} returns "1231231"
        every { mockEuxInnhentingService.getBuc(any()) } returns buc

        prefillController.createBuc(P_BUC_03.name, null)

        verify(exactly = 0) { kafkaTemplate.sendDefault(any(), any()) }
    }

    @Test
    fun `call addInstutionAndDocument mock adding two institusjon when X005 exists already as new will throw bad request`() {
        val euxCaseId = "1234567890"

        every { personService.hentIdent(eq(IdentGruppe.FOLKEREGISTERIDENT), any<AktoerId>()) } returns NorskIdent("12345")

        val mockParticipants = listOf(Participant(role = "CaseOwner", organisation = Organisation(countryCode = "NO", name = "NAV", id = "NAV")))
        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = mockParticipants)
        mockBuc.documents = listOf(createDummyBucDocumentItem(), DocumentsItem(type = SedType.X005, status = "new", direction = "OUT"))
        mockBuc.actions = listOf(ActionsItem(operation = ActionOperation.Send))

        val newParticipants = listOf(
            InstitusjonItem(country = "FI", institution = "Finland", name="Finland test"),
            InstitusjonItem(country = "DE", institution = "Tyskland", name="Tyskland test")
        )

        every { mockEuxInnhentingService.getBuc(euxCaseId) } returns mockBuc

        assertThrows<ResponseStatusException> {
            prefillController.addInstutionAndDocument(apiRequestWith(euxCaseId, newParticipants))
        }

        verify(exactly = 1) { mockEuxInnhentingService.getBuc(any()) }

    }


    @Test
    fun `Ved kall til addInstutionAndDocument med en institusjon så skal vi få en preutfylt P8000 med en deltaker`() {
        val euxCaseId = "1443996"
        val mockBuc = javaClass.getResource("/json/buc/Buc.json")!!.readText()

        every { personService.hentIdent(eq(IdentGruppe.FOLKEREGISTERIDENT), any<AktoerId>()) } returns NorskIdent("12345")
        every {  prefillKlient.hentPreutfyltSed(any())} returns javaClass.getResource("/json/nav/P8000_NO-NAV.json")!!.readText()


        val apiRequest = ApiRequest(sakId="22971111",aktoerId="2105768869843", buc=P_BUC_01, sed= SedType.P8000, euxCaseId="1443996", institutions= listOf(
            InstitusjonItem(country="SE", institution="SE:ACC2001", name="The Swedish Pensions Agency", acronym="PM Sverige"),
            InstitusjonItem(country="FI", institution="FI:0100222222", name="ACC_The Social Insurance Institution of Finland", acronym="KELA, FPA"))
        )

        val buc = mapJsonToAny<Buc>(mockBuc, false)

        every { mockEuxKlient.getBucJsonAsNavIdent(euxCaseId) } returns buc.toJson()
        every { mockEuxKlient.opprettSed(any(), any()) } returns BucSedResponse(euxCaseId, "0aa6a0d67e4046e38aa126987ab2763f")

        val docItem = prefillController.addInstutionAndDocument(apiRequest)!!

        val participant = docItem.participants
            ?.filter { it?.organisation?.name == "The Swedish Pensions Agency" }

        assertEquals(1, participant?.size)
        assertEquals("P8000", docItem.type.toString())

    }

    @Test
    fun `call addInstutionAndDocument mock adding two institusjon when we are not CaseOwner badrequest execption is thrown`() {
        val euxCaseId = "1234567890"

        val mockParticipants = listOf(Participant(role = "CaseOwner", organisation = Organisation(countryCode = "SE", name = "SE", id = "SE")))
        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = mockParticipants)
        mockBuc.documents = listOf(createDummyBucDocumentItem(), DocumentsItem(type = SedType.X005, status = "empty", direction = "OUT"))
        mockBuc.actions = listOf(ActionsItem(operation = ActionOperation.Send))

        val newParticipants = listOf(
            InstitusjonItem(country = "FI", institution = "Finland", name="Finland test"),
            InstitusjonItem(country = "DE", institution = "Tyskland", name="Tyskland test")
        )
        every { personService.hentIdent(eq(IdentGruppe.FOLKEREGISTERIDENT), any<AktoerId>()) } returns NorskIdent("12345")
        every { mockEuxInnhentingService.getBuc(euxCaseId) } returns mockBuc

        val apirequest = apiRequestWith(euxCaseId, newParticipants, buc = P_BUC_01)

        every {  prefillKlient.hentPreutfyltSed(any())} returns
                createDummyX005(newParticipants.first()) andThen
                createDummyX005(newParticipants.last())

        assertThrows<ResponseStatusException> {
            prefillController.addInstutionAndDocument(apirequest)
        }
        verify(exactly = 1 ) { personService.hentIdent(any(), any<AktoerId>()) }
        verify(exactly = 1 ) { mockEuxInnhentingService.getBuc(any()) }
        verify(exactly = 2 ) { prefillKlient.hentPreutfyltSed(any()) }

    }

    private fun createDummyX005(newParticipants: InstitusjonItem): String {
        return X005(xnav = XNav(sak = Navsak(leggtilinstitusjon = Leggtilinstitusjon(institusjon = InstitusjonX005(id = newParticipants.institution, navn = newParticipants.name ?: "" ))))).toJson()
    }

    @Test
    fun `call addInstutionAndDocument mock check on X007 will fail on matching newparticipants with exception`() {

        val euxCaseId = "1234567890"
        val mockParticipants = listOf(Participant(role = "CaseOwner", organisation = Organisation(countryCode = "SE", name = "SE", id = "SE")))
        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = mockParticipants)
        mockBuc.documents = listOf(
            createDummyBucDocumentItem(),
            DocumentsItem(
                type = X007, status = "received" ,
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
                ),
                direction = "OUT"
            )
        )
        mockBuc.actions = listOf(ActionsItem(operation = ActionOperation.Send))

        val newParticipants = listOf(
            InstitusjonItem(country = "FI", institution = "FI:213231", name="Finland test"),
            InstitusjonItem(country = "DK", institution = "DK:213231", name="Tyskland test")
        )

        every { personService.hentIdent(eq(IdentGruppe.FOLKEREGISTERIDENT), any<AktoerId>()) } returns NorskIdent("12345")
        every { mockEuxInnhentingService.getBuc(euxCaseId) } returns mockBuc

        assertThrows<ResponseStatusException> {
            prefillController.addInstutionAndDocument(apiRequestWith(euxCaseId, newParticipants))
        }
        verify(exactly = 1 ) { mockEuxInnhentingService.getBuc(any()) }

    }

    @Test
    fun `call addInstutionAndDocument add newInstitusjonItem on empty buc NAV is caseOwner`() {
        val euxCaseId = "1234567890"
        val fnr = "123123123"

        val mockBuc = Buc(
            id = "23123",
            processDefinitionName = "P_BUC_01",
            participants = listOf(Participant(role = "CaseOwner", organisation = Organisation(countryCode = "SE", name = "SE", id = "SE"))),
            documents = listOf(DocumentsItem(type = SedType.P2000, status = "empty", direction = "OUT", id = "1") ),
            actions = listOf(ActionsItem(operation = ActionOperation.Create))
        )

        val newParticipants = listOf(
            InstitusjonItem(country = "FI", institution = "FI:213231", name="Finland test"),
            InstitusjonItem(country = "DK", institution = "DK:213231", name="Tyskland test")
        )

        val apirequest = apiRequestWith(euxCaseId, newParticipants, sed = SedType.P2000, P_BUC_01)
        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apirequest, fnr )

        every { personService.hentIdent(eq(IdentGruppe.FOLKEREGISTERIDENT), any<AktoerId>())  } returns NorskIdent("12345")
        every { mockEuxInnhentingService.getBuc(euxCaseId) } returns mockBuc
        every { prefillKlient.hentPreutfyltSed(any()) } returns SED(type = dummyPrefillData.sedType).toJson()
        every { mockEuxKlient.putBucMottakere(any(), any())  } returns true
        every { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(),eq(euxCaseId), dummyPrefillData.vedtakId) } returns BucSedResponse(euxCaseId, "1")

        prefillController.addInstutionAndDocument(apirequest)

        verify(exactly = 1 ) { mockEuxInnhentingService.getBuc(any()) }
        verify(exactly = 1 ) { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(), euxCaseId, dummyPrefillData.vedtakId) }
        verify(exactly = 1 ) { prefillKlient.hentPreutfyltSed(any()) }
        verify(exactly = 1 ) { mockEuxKlient.putBucMottakere(any(), any()) }
        verify(exactly = 1 ) { personService.hentIdent(any(), any<AktoerId>())}
    }

    @Test
    fun `call addInstutionAndDocument add newInstitusjonItem on empty buc rina2020 NAV is caseOwner`() {
        val euxCaseId = "4326040"
        val fnr = "123123123"

        val jsonbuc = javaClass.getResource("/json/buc/buc-4326040-rina2020new-P_BUC_01.json")?.readText()!!
        val mockBuc: Buc = mapJsonToAny(jsonbuc)

        val jsonDocbuc = javaClass.getResource("/json/buc/buc-4326040-rina2020docs-P_BUC_01.json")?.readText()!!
        val mockDocBuc: Buc = mapJsonToAny(jsonDocbuc)

        val newParticipants = listOf(
            InstitusjonItem(country = "FI", institution = "FI:213231", name="Finland test"),
        )

        val apirequest = apiRequestWith(euxCaseId, newParticipants, sed = SedType.P2000, P_BUC_01)
        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apirequest, fnr )

        every { personService.hentIdent(eq(IdentGruppe.FOLKEREGISTERIDENT), any<AktoerId>())  } returns NorskIdent("12345")
        every { mockEuxInnhentingService.getBuc(any()) } returns mockBuc andThen mockDocBuc
        every { prefillKlient.hentPreutfyltSed(any()) } returns SED(type = dummyPrefillData.sedType).toJson()
        every { mockEuxKlient.putBucMottakere(any(), any())  } returns true
        every { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(),eq(euxCaseId), dummyPrefillData.vedtakId) } returns BucSedResponse(
            euxCaseId,
            "5a61468eb8cb4fd78c5c44d75b9bb890"
        )

        val responseresult = prefillController.addInstutionAndDocument(apirequest)

        assertEquals("5a61468eb8cb4fd78c5c44d75b9bb890", responseresult?.id)
        assertEquals(SedType.P2000, responseresult?.type)

    }

    @Test
    fun `call addInstutionAndDocument ingen ny Deltaker kun hovedsed`() {
        val euxCaseId = "1234567890"

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(Participant()))
        mockBuc.documents = listOf(createDummyBucDocumentItem())
        mockBuc.actions = listOf(ActionsItem(operation = ActionOperation.Send))

        val noNewParticipants = listOf<InstitusjonItem>()
        val apiRequest = apiRequestWith(euxCaseId, institutions = noNewParticipants, buc = P_BUC_01)
        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequest, NorskIdent("12345").id, null)

        println(dummyPrefillData)

        every { personService.hentIdent(eq(IdentGruppe.FOLKEREGISTERIDENT), any<AktoerId>())  } returns NorskIdent("12345")
        every { mockEuxInnhentingService.getBuc(euxCaseId) } returns mockBuc
        every { prefillKlient.hentPreutfyltSed(any()) } returns SED(type = dummyPrefillData.sedType).toJson()
        every { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(),eq(euxCaseId), dummyPrefillData.vedtakId) } returns BucSedResponse(euxCaseId, "3123123")

        prefillController.addInstutionAndDocument(apiRequest)

        verify( exactly = noNewParticipants.size + 1) { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(), euxCaseId, dummyPrefillData.vedtakId) }
        verify(exactly = 1 ) { mockEuxInnhentingService.getBuc(any()) }
        verify(exactly = 1 ) { prefillKlient.hentPreutfyltSed(any()) }
        verify(exactly = 1 ) { personService.hentIdent(any(), any<AktoerId>())}

    }

    @Test
    fun `call addDocumentToParent ingen ny Deltaker kun hovedsed`() {
        val euxCaseId = "1100220033"
        val parentDocumentId = "1122334455666"
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01",
            participants = listOf(Participant()), processDefinitionVersion = "4.2",
            documents = listOf(
                DocumentsItem(id = "3123123", type = SedType.P9000, status = "empty", allowsAttachments = true, lastUpdate = lastupdate, creationDate = lastupdate, parentDocumentId = parentDocumentId, direction = "OUT"),
                DocumentsItem(id = parentDocumentId, type = SedType.P8000, status = "received", allowsAttachments = true,  lastUpdate = lastupdate, creationDate = lastupdate, direction = "IN")
            ),
            actions = listOf(ActionsItem(documentType = SedType.P8000, documentId = parentDocumentId , operation = ActionOperation.Read), ActionsItem(documentType = SedType.P9000, documentId = "3123123", operation = ActionOperation.Create))
        )

        val api = apiRequestWith(euxCaseId, sed = SedType.P9000, institutions = emptyList())
        val sed = SED(SedType.P9000)

        every { personService.hentIdent(eq(IdentGruppe.FOLKEREGISTERIDENT), any<AktoerId>()) } returns NorskIdent("12345")
        every { mockEuxInnhentingService.getBuc(euxCaseId) } returns mockBuc
        every { prefillKlient.hentPreutfyltSed(any()) } returns sed.toJsonSkipEmpty()
        every { mockEuxPrefillService.opprettSvarJsonSedOnBuc(any(), euxCaseId, parentDocumentId, api.vedtakId, SedType.P9000) } returns BucSedResponse(euxCaseId, "3123123")

        val result = prefillController.addDocumentToParent(api, parentDocumentId)
        val expected = """
        {
          "attachments" : [ ],
          "displayName" : null,
          "type" : "P9000",
          "conversations" : null,
          "isSendExecuted" : null,
          "id" : "3123123",
          "direction" : "OUT",
          "creationDate" : 1596751200000,
          "receiveDate" : null,
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
          "message" : null,
          "name" : null,
          "mimeType" : null,
          "creator" : null
        }
        """.trimIndent()

        JSONAssert.assertEquals(expected, result?.toJson(), true)

        verify(exactly = 2) { mockEuxInnhentingService.getBuc(any()) }
        verify(exactly = 1) { mockEuxPrefillService.opprettSvarJsonSedOnBuc(any(), any(), any(), any(), any()) }
        verify(exactly = 1) { personService.hentIdent(any(), any<AktoerId>()) }
        verify(exactly = 1) { prefillKlient.hentPreutfyltSed(any()) }

    }

    @Test
    fun `call addDocumentToParent svarsed finnes kaster exception`()  {
        val euxCaseId = "1100220033"
        val parentDocumentId = "1122334455666"
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()


        every { personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, any<AktoerId>()) } returns NorskIdent("12345")

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(Participant()), processDefinitionVersion = "4.2")
        mockBuc.documents = listOf(
            DocumentsItem(id = "3123123", type = SedType.P9000, status = "draft", allowsAttachments = true, lastUpdate = lastupdate, creationDate = lastupdate, parentDocumentId = parentDocumentId, direction = "OUT")
        )

        val api = apiRequestWith(euxCaseId, sed = SedType.P9000, institutions = emptyList())

        every{mockEuxInnhentingService.getBuc(euxCaseId)} returns mockBuc

        assertThrows<ResponseStatusException> {
            prefillController.addDocumentToParent(api, parentDocumentId)
        }
    }

    @Test
    fun `call addInstutionAndDocument valider om SED alt finnes i BUC kaster Exception`() {
        val euxCaseId = "1234567890"

        every { personService.hentIdent(eq(IdentGruppe.FOLKEREGISTERIDENT), any<AktoerId>()) } returns NorskIdent("12345")
        val mockBucJson = javaClass.getResource("/json/buc/buc-P_BUC_06-P6000_Sendt.json")!!.readText()
        every { mockEuxInnhentingService.getBuc(euxCaseId) } returns mapJsonToAny(mockBucJson)
        val apiRequest = apiRequestWith(euxCaseId, emptyList())

        assertThrows<SedDokumentKanIkkeOpprettesException> {
            prefillController.addInstutionAndDocument(apiRequest)
        }

    }

    @Test
    fun `Gitt det opprettes en SED P10000 på tom P_BUC_06 Så skal bucmodel hents på nyt og shortDocument returneres som response`() {
        val euxCaseId = "1234567890"

        every { personService.hentIdent(eq(IdentGruppe.FOLKEREGISTERIDENT), any<AktoerId>()) } returns NorskIdent("12345")
        every { prefillKlient.hentPreutfyltSed(any()) } returns SED(SedType.P10000).toJson()

        val mockBucJson = javaClass.getResource("/json/buc/buc_P_BUC_06_4.2_tom.json")!!.readText()
        val mockBucJson2 = javaClass.getResource("/json/buc/P_BUC_06_P10000.json")!!.readText()

        every { mockEuxInnhentingService.getBuc(euxCaseId) } returns mapJsonToAny(mockBucJson) andThen mapJsonToAny<Buc>((mockBucJson2))
        val newParticipants = listOf(
            InstitusjonItem(country = "FI", institution = "FI:Finland", name="Finland test")
        )
        val apiRequest = apiRequestWith(euxCaseId, newParticipants, SedType.P10000)

        every { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(), euxCaseId, apiRequest.vedtakId) } returns BucSedResponse(euxCaseId, "58c26271b21f4feebcc36b949b4865fe")
        justRun { mockEuxPrefillService.addInstitution(any(), any()) }

        val result =  prefillController.addInstutionAndDocument(apiRequest)

        verify (exactly = 1) { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(), eq(euxCaseId), apiRequest.vedtakId) }
        verify (exactly = 2) { mockEuxInnhentingService.getBuc(eq(euxCaseId)) }

        Assertions.assertNotNull(result)
        assertEquals(DocumentsItem::class.java, result?.javaClass)
    }

    @Test
    fun `call addInstutionAndDocument  to nye deltakere, men ingen X005`() {
        val euxCaseId = "1234567890"

        every{ personService.hentIdent(eq(IdentGruppe.FOLKEREGISTERIDENT), any<AktoerId>()) } returns NorskIdent("12345")

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(Participant()))
        mockBuc.documents = listOf(createDummyBucDocumentItem())
        mockBuc.actions = listOf(ActionsItem(operation = ActionOperation.Send))

        every{mockEuxInnhentingService.getBuc(euxCaseId)} returns mockBuc

        val newParticipants = listOf(
            InstitusjonItem(country = "FI", institution = "FI:Finland", name="Finland test"),
            InstitusjonItem(country = "DE", institution = "DE:Tyskland", name="Tyskland test")
        )
        val apiRequest = apiRequestWith(euxCaseId, newParticipants, buc = P_BUC_01)
        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id, null)

        justRun { mockEuxPrefillService.addInstitution(any(), any()) }
        every { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(), eq(euxCaseId),dummyPrefillData.vedtakId) } returns BucSedResponse(euxCaseId, "3123123")
        every { prefillKlient.hentPreutfyltSed(any()) } returns SED(type = dummyPrefillData.sedType).toJson()

        prefillController.addInstutionAndDocument(apiRequest)

        verify (exactly = 1) { prefillKlient.hentPreutfyltSed(any()) }
        verify (exactly = 1) { mockEuxPrefillService.addInstitution(any(), any()) }
        verify (exactly = 1) { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(), euxCaseId, dummyPrefillData.vedtakId) }
    }

    @Test
    fun `call addInstutionAndDocument  Exception eller feiler ved oppretting av SED naar X005 ikke finnes`() {
        val euxCaseId = "1234567890"

        every{ personService.hentIdent(eq(IdentGruppe.FOLKEREGISTERIDENT), any<AktoerId>()) } returns NorskIdent("12345")

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(Participant()))
        mockBuc.documents = listOf(createDummyBucDocumentItem(), DocumentsItem(direction = "OUT"))
        mockBuc.actions = listOf(ActionsItem(operation = ActionOperation.Send))

        every{mockEuxInnhentingService.getBuc(euxCaseId)} returns mockBuc
        justRun { mockEuxPrefillService.addInstitution(any(), any()) }

        val newParticipants = listOf(
            InstitusjonItem(country = "FI", institution = "FI:Finland", name="Finland test"),
            InstitusjonItem(country = "DE", institution = "DE:Tyskland", name="Tyskland test")
        )
        val apiRequest = apiRequestWith(euxCaseId, newParticipants, buc = P_BUC_01)
        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequest, NorskIdent("12345").id, null)

        every { prefillKlient.hentPreutfyltSed(any()) } returns SED(type = dummyPrefillData.sedType).toJson()
        every { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(), eq(euxCaseId),dummyPrefillData.vedtakId) } throws SedDokumentIkkeOpprettetException("Expected!")

        assertThrows<SedDokumentIkkeOpprettetException> {
            prefillController.addInstutionAndDocument(apiRequest)
        }
    }
}

