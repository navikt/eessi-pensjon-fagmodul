
package no.nav.eessi.pensjon.fagmodul.api

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.sed.InstitusjonX005
import no.nav.eessi.pensjon.eux.model.sed.Leggtilinstitusjon
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.Navsak
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.eux.model.sed.X005
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedView
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.eux.EuxKlient
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
import no.nav.eessi.pensjon.services.statistikk.StatistikkHandler
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
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.Month

class PrefillControllerTest {

    @SpyK
    var auditLogger: AuditLogger = AuditLogger()

    @SpyK
    lateinit var mockEuxPrefillService: EuxPrefillService

    @SpyK
    var mockEuxInnhentingService: EuxInnhentingService = EuxInnhentingService(mockk())

    @MockK
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @MockK
    lateinit var vedleggService: VedleggService

    @MockK
    private lateinit var personService: PersonService

    @MockK
    private lateinit var pensjonsinformasjonService: PensjonsinformasjonService

    @MockK
    lateinit var prefillKlient: PrefillKlient

    private lateinit var prefillController: PrefillController

    private  val mockEuxKlient: EuxKlient = mockk()

    @BeforeEach
    fun before() {
//        mockEuxKlient.initMetrics()

        mockEuxPrefillService = EuxPrefillService(mockEuxKlient,
            StatistikkHandler("test", KafkaTemplate(DefaultKafkaProducerFactory(emptyMap())), "")
        )

        MockKAnnotations.init(this, relaxed = true)

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

        every { mockEuxPrefillService.createBuc("P_BUC_03") } returns "1231231"
        every { mockEuxInnhentingService.getBuc(any()) } returns buc

        val excpeted = BucAndSedView.from(buc)
        val actual = prefillController.createBuc("P_BUC_03")

        assertEquals(excpeted.toJson(), actual.toJson())
    }

    @Test
    fun `createBuc run ok and does not run statistics in default namespace`() {
        val gyldigBuc = javaClass.getResource("/json/buc/buc-279020big.json").readText()
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())

        every {  mockEuxPrefillService.createBuc("P_BUC_03")} returns "1231231"
        every { mockEuxInnhentingService.getBuc(any()) } returns buc

        prefillController.createBuc("P_BUC_03")

        verify(exactly = 0) { kafkaTemplate.sendDefault(any(), any()) }
    }

    @Test
    fun `call addInstutionAndDocument mock adding two institusjon when X005 exists already`() {
        val euxCaseId = "1234567890"

        every { personService.hentIdent(eq(IdentType.NorskIdent), any<AktoerId>()) } returns NorskIdent("12345")

        val mockParticipants = listOf(ParticipantsItem(role = "CaseOwner", organisation = Organisation(countryCode = "NO", name = "NAV", id = "NAV")))
        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = mockParticipants)
        mockBuc.documents = listOf(createDummyBucDocumentItem(), DocumentsItem(type = SedType.X005))
        mockBuc.actions = listOf(ActionsItem(name = "Send"))

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id, null)

        val newParticipants = listOf(
            InstitusjonItem(country = "FI", institution = "Finland", name="Finland test"),
            InstitusjonItem(country = "DE", institution = "Tyskland", name="Tyskland test")
        )

        every { mockEuxInnhentingService.getBuc(euxCaseId) } returns mockBuc
        every { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(), euxCaseId, dummyPrefillData.vedtakId) } returns BucSedResponse(euxCaseId,"1")
        every { prefillKlient.hentPreutfyltSed(any()) } returns createDummyX005(newParticipants.first()) andThen createDummyX005(newParticipants.last()) andThen SED(type = dummyPrefillData.sedType).toJson()

        prefillController.addInstutionAndDocument(apiRequestWith(euxCaseId, newParticipants))

        verify(exactly = newParticipants.size  + 1) { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(), eq(euxCaseId), dummyPrefillData.vedtakId)}
        verify(exactly = 3) { prefillKlient.hentPreutfyltSed(any()) }

    }

    private fun createDummyX005(newParticipants: InstitusjonItem): String {
        return X005(nav = Nav(sak = Navsak(leggtilinstitusjon = Leggtilinstitusjon(institusjon = InstitusjonX005(id = newParticipants.institution, navn = newParticipants.name ?: "" ))))).toJson()
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
        every { personService.hentIdent(eq(IdentType.NorskIdent), any<AktoerId>()) } returns NorskIdent("12345")
        every { mockEuxInnhentingService.getBuc(euxCaseId) } returns mockBuc

        val apirequest = apiRequestWith(euxCaseId, newParticipants)
        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id)

        every {  prefillKlient.hentPreutfyltSed(any())} returns
                createDummyX005(newParticipants.first()) andThen
                createDummyX005(newParticipants.last()) andThen
                SED(type = dummyPrefillData.sedType).toJson()

        assertThrows<ResponseStatusException> {
            prefillController.addInstutionAndDocument(apirequest)
        }
        verify(exactly = 1 ) { personService.hentIdent(any<IdentType.NorskIdent>(), any<AktoerId>()) }
        verify(exactly = 1 ) { mockEuxInnhentingService.getBuc(any()) }
        verify(exactly = 3 ) { prefillKlient.hentPreutfyltSed(any()) }

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

        every { personService.hentIdent(eq(IdentType.NorskIdent), any<AktoerId>()) } returns NorskIdent("12345")
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
            participants = listOf(ParticipantsItem(role = "CaseOwner", organisation = Organisation(countryCode = "SE", name = "SE", id = "SE"))),
            documents = listOf(DocumentsItem(type = SedType.P2000, status = "empty") ),
            actions = listOf(ActionsItem(name = "Create"))
        )

        val newParticipants = listOf(
            InstitusjonItem(country = "FI", institution = "FI:213231", name="Finland test"),
            InstitusjonItem(country = "DK", institution = "DK:213231", name="Tyskland test")
        )

        val apirequest = apiRequestWith(euxCaseId, newParticipants, sed = "P2000", "P_BUC_01")
        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apirequest, fnr )

        every { personService.hentIdent(eq(IdentType.NorskIdent), any<AktoerId>())  } returns NorskIdent("12345")
        every { mockEuxInnhentingService.getBuc(euxCaseId) } returns mockBuc
        every { prefillKlient.hentPreutfyltSed(any()) } returns SED(type = dummyPrefillData.sedType).toJson()
        every { mockEuxKlient.putBucMottakere(any(), any())  } returns true
        every { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(),eq(euxCaseId), dummyPrefillData.vedtakId) } returns BucSedResponse(euxCaseId, "1")

        prefillController.addInstutionAndDocument(apirequest)

        verify(exactly = 1 ) { mockEuxInnhentingService.getBuc(any()) }
        verify(exactly = 1 ) { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(), euxCaseId, dummyPrefillData.vedtakId) }
        verify(exactly = 1 ) { prefillKlient.hentPreutfyltSed(any()) }
        verify(exactly = 1 ) { mockEuxKlient.putBucMottakere(any(), any()) }
        verify(exactly = 1 ) { personService.hentIdent(any<IdentType.NorskIdent>(), any<AktoerId>())}
    }

    @Test
    fun `call addInstutionAndDocument add newInstitusjonItem on empty buc rina2020 NAV is caseOwner`() {
        val euxCaseId = "4326040"
        val fnr = "123123123"

        val jsonbuc = javaClass.getResource("/json/buc/buc-4326040-rina2020new-P_BUC_01.json")?.readText()!!
        val mockBuc: Buc = mapJsonToAny(jsonbuc, typeRefs())

        val jsonDocbuc = javaClass.getResource("/json/buc/buc-4326040-rina2020docs-P_BUC_01.json")?.readText()!!
        val mockDocBuc: Buc = mapJsonToAny(jsonDocbuc, typeRefs())

        val newParticipants = listOf(
            InstitusjonItem(country = "FI", institution = "FI:213231", name="Finland test"),
        )

        val apirequest = apiRequestWith(euxCaseId, newParticipants, sed = "P2000", "P_BUC_01")
        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apirequest, fnr )

        every { personService.hentIdent(eq(IdentType.NorskIdent), any<AktoerId>())  } returns NorskIdent("12345")
        every { mockEuxInnhentingService.getBuc(any()) } returns mockBuc andThen mockDocBuc
        every { prefillKlient.hentPreutfyltSed(any()) } returns SED(type = dummyPrefillData.sedType).toJson()
        every { mockEuxKlient.putBucMottakere(any(), any())  } returns true
        every { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(),eq(euxCaseId), dummyPrefillData.vedtakId) } returns BucSedResponse(euxCaseId, "5a61468eb8cb4fd78c5c44d75b9bb890")

        val responseresult = prefillController.addInstutionAndDocument(apirequest)

        verify(exactly = 2 ) { mockEuxInnhentingService.getBuc(any()) }
        verify(exactly = 1 ) { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(), euxCaseId, dummyPrefillData.vedtakId) }
        verify(exactly = 1 ) { prefillKlient.hentPreutfyltSed(any()) }
        verify(exactly = 1 ) { mockEuxKlient.putBucMottakere(any(), any()) }
        verify(exactly = 1 ) { personService.hentIdent(any<IdentType.NorskIdent>(), any<AktoerId>())}

        assertEquals("5a61468eb8cb4fd78c5c44d75b9bb890", responseresult?.id)
        assertEquals(SedType.P2000, responseresult?.type)

    }


    @Test
    fun `call addInstutionAndDocument ingen ny Deltaker kun hovedsed`() {
        val euxCaseId = "1234567890"


        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(ParticipantsItem()))
        mockBuc.documents = listOf(createDummyBucDocumentItem())
        mockBuc.actions = listOf(ActionsItem(name = "Send"))

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id, null)
        val noNewParticipants = listOf<InstitusjonItem>()

        every { personService.hentIdent(eq(IdentType.NorskIdent), any<AktoerId>())  } returns NorskIdent("12345")
        every { mockEuxInnhentingService.getBuc(euxCaseId) } returns mockBuc
        every { prefillKlient.hentPreutfyltSed(any()) } returns SED(type = dummyPrefillData.sedType).toJson()
        every { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(),eq(euxCaseId), dummyPrefillData.vedtakId) } returns BucSedResponse(euxCaseId, "1")

        prefillController.addInstutionAndDocument(apiRequestWith(euxCaseId, noNewParticipants))

        verify( exactly = noNewParticipants.size + 1) { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(), euxCaseId, dummyPrefillData.vedtakId) }
        verify(exactly = 2 ) { mockEuxInnhentingService.getBuc(any()) }
        verify(exactly = 1 ) { prefillKlient.hentPreutfyltSed(any()) }
        verify(exactly = 1 ) { personService.hentIdent(any<IdentType.NorskIdent>(), any<AktoerId>())}

    }

    @Test
    fun `call addDocumentToParent ingen ny Deltaker kun hovedsed`() {
        val euxCaseId = "1100220033"
        val parentDocumentId = "1122334455666"
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01",
            participants = listOf(ParticipantsItem()), processDefinitionVersion = "4.2",
            documents = listOf(DocumentsItem(id = "3123123", type = SedType.P9000, status = "empty", allowsAttachments = true, lastUpdate = lastupdate, creationDate = lastupdate, parentDocumentId = parentDocumentId),
            DocumentsItem(id = parentDocumentId, type = SedType.P8000, status = "received", allowsAttachments = true,  lastUpdate = lastupdate, creationDate = lastupdate)),
           actions = listOf(ActionsItem(id = "1000", name = "Received"))
        )

        val api = apiRequestWith(euxCaseId, sed = "P9000", institutions = emptyList())
        val sed = SED(SedType.P9000)

        every { personService.hentIdent(eq(IdentType.NorskIdent), any<AktoerId>()) } returns NorskIdent("12345")
        every { mockEuxInnhentingService.getBuc(euxCaseId) } returns mockBuc
        every { prefillKlient.hentPreutfyltSed(any()) } returns sed.toJsonSkipEmpty()
        every { mockEuxPrefillService.opprettSvarJsonSedOnBuc(any(), euxCaseId, parentDocumentId, api.vedtakId) } returns BucSedResponse(euxCaseId, "3123123")

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

        verify(exactly = 2) { mockEuxInnhentingService.getBuc(any()) }
        verify(exactly = 1) { mockEuxPrefillService.opprettSvarJsonSedOnBuc(any(), any(), any(), any()) }
        verify(exactly = 1) { personService.hentIdent(any<IdentType.NorskIdent>(), any<AktoerId>()) }
        verify(exactly = 1) { prefillKlient.hentPreutfyltSed(any()) }

    }

    @Test
    fun `call addDocumentToParent svarsed finnes kaster exception`()  {
        val euxCaseId = "1100220033"
        val parentDocumentId = "1122334455666"
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        every { personService.hentIdent(IdentType.NorskIdent, any<AktoerId>()) } returns NorskIdent("12345")

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(ParticipantsItem()), processDefinitionVersion = "4.2")
        mockBuc.documents = listOf(
            DocumentsItem(id = "3123123", type = SedType.P9000, status = "draft", allowsAttachments = true, lastUpdate = lastupdate, creationDate = lastupdate, parentDocumentId = parentDocumentId)
        )

        val api = apiRequestWith(euxCaseId, sed = "P9000", institutions = emptyList())

        every{mockEuxInnhentingService.getBuc(euxCaseId)} returns mockBuc

        assertThrows<SedDokumentKanIkkeOpprettesException> {
            prefillController.addDocumentToParent(api, parentDocumentId)
        }
    }

    @Test
    fun `call addInstutionAndDocument valider om SED alt finnes i BUC kaster Exception`() {
        val euxCaseId = "1234567890"

        every { personService.hentIdent(eq(IdentType.NorskIdent), any<AktoerId>()) } returns NorskIdent("12345")
        val mockBucJson = javaClass.getResource("/json/buc/buc-P_BUC_06-P6000_Sendt.json").readText()
        every { mockEuxInnhentingService.getBuc(euxCaseId) } returns mapJsonToAny(mockBucJson, typeRefs<Buc>())
        val apiRequest = apiRequestWith(euxCaseId, emptyList())

        assertThrows<SedDokumentKanIkkeOpprettesException> {
            prefillController.addInstutionAndDocument(apiRequest)
        }

    }

    @Test
    fun `Gitt det opprettes en SED P10000 på tom P_BUC_06 Så skal bucmodel hents på nyt og shortDocument returneres som response`() {
        val euxCaseId = "1234567890"

        every { personService.hentIdent(eq(IdentType.NorskIdent), any<AktoerId>()) } returns NorskIdent("12345")
        every { prefillKlient.hentPreutfyltSed(any()) } returns SED(SedType.P10000).toJson()

        val mockBucJson = javaClass.getResource("/json/buc/buc_P_BUC_06_4.2_tom.json").readText()
        val mockBucJson2 = javaClass.getResource("/json/buc/P_BUC_06_P10000.json").readText()

        every { mockEuxInnhentingService.getBuc(euxCaseId) } returns mapJsonToAny(mockBucJson, typeRefs<Buc>()) andThen mapJsonToAny(mockBucJson2, typeRefs<Buc>())
        val newParticipants = listOf(
            InstitusjonItem(country = "FI", institution = "FI:Finland", name="Finland test")
        )
        val apiRequest = apiRequestWith(euxCaseId, newParticipants, "P10000")

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

        every{ personService.hentIdent(eq(IdentType.NorskIdent), any<AktoerId>()) } returns NorskIdent("12345")

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(ParticipantsItem()))
        mockBuc.documents = listOf(createDummyBucDocumentItem())
        mockBuc.actions = listOf(ActionsItem(name = "Send"))

        every{mockEuxInnhentingService.getBuc(euxCaseId)} returns mockBuc

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id, null)
        justRun { mockEuxPrefillService.addInstitution(any(), any()) }

        every { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(), eq(euxCaseId),dummyPrefillData.vedtakId) } returns BucSedResponse(euxCaseId,"1")
        every { prefillKlient.hentPreutfyltSed(any()) } returns SED(type = dummyPrefillData.sedType).toJson()

        val newParticipants = listOf(
            InstitusjonItem(country = "FI", institution = "FI:Finland", name="Finland test"),
            InstitusjonItem(country = "DE", institution = "DE:Tyskland", name="Tyskland test")
        )
        prefillController.addInstutionAndDocument(apiRequestWith(euxCaseId, newParticipants))

        verify (exactly = 1) { mockEuxPrefillService.addInstitution(any(), any()) }
        verify { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(), euxCaseId, dummyPrefillData.vedtakId) }
    }

    @Test
    fun `call addInstutionAndDocument  Exception eller feiler ved oppretting av SED naar X005 ikke finnes`() {
        val euxCaseId = "1234567890"

        every{ personService.hentIdent(eq(IdentType.NorskIdent), any<AktoerId>()) } returns NorskIdent("12345")

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(ParticipantsItem()))
        mockBuc.documents = listOf(createDummyBucDocumentItem(), DocumentsItem())
        mockBuc.actions = listOf(ActionsItem(name = "Send"))

        every{mockEuxInnhentingService.getBuc(euxCaseId)} returns mockBuc
        justRun { mockEuxPrefillService.addInstitution(any(), any()) }

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id, null)

        every { prefillKlient.hentPreutfyltSed(any()) } returns SED(type = dummyPrefillData.sedType).toJson()
        every { mockEuxPrefillService.opprettJsonSedOnBuc(any(), any(), eq(euxCaseId),dummyPrefillData.vedtakId) } throws SedDokumentIkkeOpprettetException("Expected!")

        val newParticipants = listOf(
            InstitusjonItem(country = "FI", institution = "FI:Finland", name="Finland test"),
            InstitusjonItem(country = "DE", institution = "DE:Tyskland", name="Tyskland test")
        )
        assertThrows<SedDokumentIkkeOpprettetException> {
            prefillController.addInstutionAndDocument(apiRequestWith(euxCaseId, newParticipants))
        }
    }

    private fun apiRequestWith(euxCaseId: String, institutions: List<InstitusjonItem> = listOf(), sed: String? = "P6000", buc: String? = "P_BUC_06"): ApiRequest {
        return ApiRequest(
            subjectArea = "Pensjon",
            sakId = "EESSI-PEN-123",
            euxCaseId = euxCaseId,
            vedtakId = "1234567",
            institutions = institutions,
            sed = sed,
            buc = buc,
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

