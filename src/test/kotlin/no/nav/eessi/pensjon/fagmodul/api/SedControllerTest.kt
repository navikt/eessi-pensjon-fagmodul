package no.nav.eessi.pensjon.fagmodul.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockitokotlin2.*
import no.nav.eessi.pensjon.fagmodul.eux.EuxService
import no.nav.eessi.pensjon.fagmodul.eux.SedDokumentIkkeOpprettetException
import no.nav.eessi.pensjon.fagmodul.eux.SedDokumentKanIkkeOpprettesException
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucSedResponse
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.*
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.*
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.sedmodel.*
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentType
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.utils.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDate
import java.time.Month

@ExtendWith(MockitoExtension::class)
class SedControllerTest {

    @Spy
    lateinit var mockEuxService: EuxService

    @Spy
    lateinit var auditLogger: AuditLogger

    @Mock
    lateinit var mockPrefillSEDService: PrefillSEDService

    @Mock
    lateinit var personService: PersonDataService

    private lateinit var sedController: SedController

    @BeforeEach
    fun setUp() {
        mockEuxService.initMetrics()

        val prefillService = PrefillService(mockPrefillSEDService)
        prefillService.initMetrics()

        personService.initMetrics()
        this.sedController = SedController(
            mockEuxService,
            prefillService,
            personService,
            auditLogger
        )
        sedController.initMetrics()
    }

    @Test
    fun `create frontend request`() {
        val json = "{\"institutions\":[{\"country\":\"NO\",\"institution\":\"DUMMY\"}],\"buc\":\"P_BUC_06\",\"sed\":\"P6000\",\"sakId\":\"123456\",\"aktoerId\":\"0105094340092\"}"

        //map json request back to FrontendRequest obj
        val map = jacksonObjectMapper()
        val req = map.readValue(json, ApiRequest::class.java)


        assertEquals("P_BUC_06", req.buc)
        assertEquals("DUMMY", req.institutions!![0].institution)
        assertEquals("123456", req?.sakId)
    }

    @Test
    fun `confirm document`() {
        val mockData = ApiRequest(
                subjectArea = "Pensjon",
                sakId = "EESSI-PEN-123",
                vedtakId = "1234567",
                institutions = listOf(InstitusjonItem("NO", "DUMMY")),
                euxCaseId = "1234567890",
                sed = "P6000",
                buc = "P_BUC_06",
                aktoerId = "0105094340092"
        )

        doReturn(NorskIdent("12345")).whenever(personService).hentIdent(eq(IdentType.NorskIdent), any< Ident<*>>())


        val utfyllMock = ApiRequest.buildPrefillDataModelOnExisting(mockData, NorskIdent("12345").id, null)

        doReturn(PersonDataCollection(PersonPDLMock.createWith(), PersonPDLMock.createWith())).whenever(personService).hentPersonData(any())

        val nav = Nav(bruker = Bruker(person = Person(fornavn = "Dummy", etternavn = "Dummy", foedselsdato = "1900-10-11", kjoenn = "K")), krav = Krav("1937-12-11"))
        val mockSed = SED(
            type = utfyllMock.sedType,
            nav = nav
        )

        doReturn(mockSed).whenever(mockPrefillSEDService).prefill(any(), any())

        val response = sedController.prefillDocument(mockData)
        assertNotNull(response)

        val sed = SED.fromJson(response)

        assertEquals(SEDType.P6000, sed.type)
        assertEquals("Dummy", sed.nav?.bruker?.person?.fornavn)
        assertEquals("Dummy", sed.nav?.bruker?.person?.etternavn)
    }

    @Test
    fun getDocumentfromRina() {

        val sed = SED(SEDType.P2000)
        doReturn(sed).`when`(mockEuxService).getSedOnBucByDocumentId("2313", "23123123123")

        val result = sedController.getDocument("2313", "23123123123")
        assertEquals(sed.toJson(), result)
    }

    @Test
    fun `check rest api path correct`() {
        val path = "/sed/get/{rinanr}/{documentid}"
        val uriParams = mapOf("rinanr" to "123456789", "documentid" to "DOC1223213234234")
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)
        assertEquals("/sed/get/123456789/DOC1223213234234", builder.path)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString   buc_01 returns 1 sed`() {
        val mockBuc = mapJsonToAny(javaClass.getResource("/json/buc/P_BUC_01_4.2_tom.json").readText(), typeRefs<Buc>())
        val buc = "P_BUC_01"
        val rinanr = "1000101"

        doReturn(mockBuc).whenever(mockEuxService).getBuc(rinanr)

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson( listOf(SEDType.P2000)))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(1, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString   buc_06 returns 4 seds`() {
        val mockBuc = mapJsonToAny(javaClass.getResource("/json/buc/buc_P_BUC_06_4.2_tom.json").readText(), typeRefs<Buc>())
        val buc = "P_BUC_06"
        val rinanr = "1000101"

        doReturn(mockBuc).whenever(mockEuxService).getBuc(rinanr)

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse =
            ResponseEntity.ok(mapAnyToJson(listOf(SEDType.P5000, SEDType.P6000, SEDType.P7000, SEDType.P10000)))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(4, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString   buc_06 returns 3 seds if a sed already exists`() {
        val mockBuc = mapJsonToAny(javaClass.getResource("/json/buc/buc-P_BUC_06_4.2_P5000.json").readText(), typeRefs<Buc>())
        val buc = "P_BUC_06"
        val rinanr = "1000101"

        doReturn(mockBuc).whenever(mockEuxService).getBuc(rinanr)

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson(listOf(SEDType.P10000, SEDType.P6000, SEDType.P7000)))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(3, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString buc_01 returns lots of seds`() {
        val mockBuc = mapJsonToAny(javaClass.getResource("/json/buc/buc-22909_v4.1.json").readText(), typeRefs<Buc>())
        val buc = "P_BUC_01"
        val rinanr = "1000101"

        doReturn(mockBuc).whenever(mockEuxService).getBuc(rinanr)

        val actualResponse = sedController.getSeds(buc, rinanr)

        val sedList = listOf(SEDType.H020, SEDType.H070, SEDType.H120, SEDType.P10000, SEDType.P3000_NO, SEDType.P4000, SEDType.P5000, SEDType.P6000, SEDType.P7000, SEDType.P8000 )
        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson(sedList))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(10, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString buc_06 returns 0 seds if a sed is sent`() {
        val mockBuc = mapJsonToAny(javaClass.getResource("/json/buc/buc-P_BUC_06-P5000_Sendt.json").readText(), typeRefs<Buc>())
        val buc = "P_BUC_06"
        val rinanr = "1000101"

        doReturn(mockBuc).whenever(mockEuxService).getBuc(rinanr)

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson( listOf<String>()))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(0, list.size)
    }

    @Test
    fun `call addInstutionAndDocument mock adding two institusjon when X005 exists already`() {
        val euxCaseId = "1234567890"

        doReturn(NorskIdent("12345")).`when`(personService).hentIdent(eq(IdentType.NorskIdent), any< Ident<*>>())
        doReturn(PersonDataCollection(PersonPDLMock.createWith(), PersonPDLMock.createWith())).whenever(personService).hentPersonData(any())

        val mockParticipants = listOf(ParticipantsItem(role = "CaseOwner", organisation = Organisation(countryCode = "NO", name = "NAV", id = "NAV")))
        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = mockParticipants)
        mockBuc.documents = listOf(createDummyBucDocumentItem(), DocumentsItem(type = SEDType.X005))
        mockBuc.actions = listOf(ActionsItem(name = "Send"))

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id, null)

        doReturn(mockBuc).whenever(mockEuxService).getBuc(euxCaseId)
        doReturn(SED(type = dummyPrefillData.sedType)).whenever(mockPrefillSEDService).prefill(any(), any())
        doReturn(BucSedResponse(euxCaseId,"1")).whenever(mockEuxService).opprettJsonSedOnBuc(any(), any(),eq(euxCaseId),eq(dummyPrefillData.vedtakId))

        val newParticipants = listOf(
                InstitusjonItem(country = "FI", institution = "Finland", name="Finland test"),
                InstitusjonItem(country = "DE", institution = "Tyskland", name="Tyskland test")
        )

        sedController.addInstutionAndDocument(apiRequestWith(euxCaseId, newParticipants))

        verify(mockEuxService, times(newParticipants.size  + 1)).opprettJsonSedOnBuc(any(), any(), eq(euxCaseId),eq(dummyPrefillData.vedtakId))
    }

    @Test
    fun `call addInstutionAndDocument mock adding two institusjon when we are not CaseOwner badrequest execption is thrown`() {
        val euxCaseId = "1234567890"

        val mockParticipants = listOf(ParticipantsItem(role = "CaseOwner", organisation = Organisation(countryCode = "SE", name = "SE", id = "SE")))
        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = mockParticipants)
        mockBuc.documents = listOf(createDummyBucDocumentItem(), DocumentsItem(type = SEDType.X005))
        mockBuc.actions = listOf(ActionsItem(name = "Send"))

        val newParticipants = listOf(
                InstitusjonItem(country = "FI", institution = "Finland", name="Finland test"),
                InstitusjonItem(country = "DE", institution = "Tyskland", name="Tyskland test")
        )

        doReturn(NorskIdent("12345")).whenever(personService).hentIdent(eq(IdentType.NorskIdent), any< Ident<*>>())
        doReturn(PersonDataCollection(PersonPDLMock.createWith(), PersonPDLMock.createWith())).whenever(personService).hentPersonData(any())
        doReturn(mockBuc).whenever(mockEuxService).getBuc(euxCaseId)

        val apirequest = apiRequestWith(euxCaseId, newParticipants)
        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id)

        doReturn(SED(type = dummyPrefillData.sedType)).whenever(mockPrefillSEDService).prefill(any(), any())

        assertThrows<ResponseStatusException> {
            sedController.addInstutionAndDocument(apirequest)
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
                type = SEDType.X007, status = "received" ,
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

        doReturn(NorskIdent("12345")).whenever(personService).hentIdent(eq(IdentType.NorskIdent), any< Ident<*>>())
        doReturn(mockBuc).whenever(mockEuxService).getBuc(euxCaseId)
        val apirequest = apiRequestWith(euxCaseId, newParticipants)

        assertThrows<ResponseStatusException> {
            sedController.addInstutionAndDocument(apirequest)
        }
    }

    @Test
    fun `call addInstutionAndDocument  ingen ny Deltaker kun hovedsed`() {
        val euxCaseId = "1234567890"

        doReturn(NorskIdent("12345")).whenever(personService).hentIdent(eq(IdentType.NorskIdent), any< Ident<*>>())
        doReturn(PersonDataCollection(PersonPDLMock.createWith(), PersonPDLMock.createWith())).whenever(personService).hentPersonData(any())

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(ParticipantsItem()))
        mockBuc.documents = listOf(createDummyBucDocumentItem())
        mockBuc.actions = listOf(ActionsItem(name = "Send"))

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id, null)

        doReturn(SED(type = dummyPrefillData.sedType)).whenever(mockPrefillSEDService).prefill(any(), any())
        doReturn(mockBuc).whenever(mockEuxService).getBuc(euxCaseId)

        doReturn(BucSedResponse(euxCaseId, "1")).whenever(mockEuxService).opprettJsonSedOnBuc(any(), any(),eq(euxCaseId),eq(dummyPrefillData.vedtakId))

        val noNewParticipants = listOf<InstitusjonItem>()
        sedController.addInstutionAndDocument(apiRequestWith(euxCaseId, noNewParticipants))

        verify(mockEuxService, times(noNewParticipants.size + 1)).opprettJsonSedOnBuc(any(), any(), eq(euxCaseId),eq(dummyPrefillData.vedtakId))
    }

    @Test
    fun `call addDocumentToParent ingen ny Deltaker kun hovedsed`() {
        val euxCaseId = "1100220033"
        val parentDocumentId = "1122334455666"
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        doReturn(NorskIdent("12345")).whenever(personService).hentIdent(eq(IdentType.NorskIdent), any< Ident<*>>())
        doReturn(PersonDataCollection(PersonPDLMock.createWith(), PersonPDLMock.createWith())).whenever(personService).hentPersonData(any())

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(ParticipantsItem()), processDefinitionVersion = "4.2")
        mockBuc.documents = listOf(
            DocumentsItem(id = "3123123", type = SEDType.P9000, status = "empty", allowsAttachments = true, lastUpdate = lastupdate, creationDate = lastupdate, parentDocumentId = parentDocumentId),
            DocumentsItem(id = parentDocumentId, type = SEDType.P8000, status = "received", allowsAttachments = true,  lastUpdate = lastupdate, creationDate = lastupdate))
        mockBuc.actions = listOf(ActionsItem(id = "1000", name = "Received"))

        val api = apiRequestWith(euxCaseId, sed = "P9000", institutions = emptyList())

        val sed = SED(SEDType.P9000)
        val sedandtype = SedAndType(SEDType.P9000, sed.toJsonSkipEmpty())

        doReturn(sed).whenever(mockPrefillSEDService).prefill(any(),any())
        doReturn(mockBuc).whenever(mockEuxService).getBuc(euxCaseId)
        doReturn(BucSedResponse(euxCaseId, "3123123")).whenever(mockEuxService).opprettSvarJsonSedOnBuc(
            sedandtype.sed,
            euxCaseId,
            parentDocumentId,
            api.vedtakId
        )

        val result = sedController.addDocumentToParent(api, parentDocumentId)
        val expected = """
            {
              "id" : "3123123",
              "parentDocumentId" : "1122334455666",
              "type" : "P9000",
              "status" : "empty",
              "creationDate" : 1596751200000,
              "lastUpdate" : 1596751200000,
              "displayName" : null,
              "participants" : null,
              "attachments" : [ ],
              "version" : "1",
              "allowsAttachments" : true,
              "message" : null
            }
        """.trimIndent()

        assertEquals(expected, result?.toJson())

        verify(mockEuxService, times( 2)).getBuc(any())
        verify(mockEuxService, times( 1)).opprettSvarJsonSedOnBuc(any(), any(), any(), any())
        verify(personService, times(1)).hentPersonData(any())
    }

    @Test
    fun `call addDocumentToParent svarsed finnes kaster exception`()  {
        val euxCaseId = "1100220033"
        val parentDocumentId = "1122334455666"
        val lastupdate = LocalDate.of(2020, Month.AUGUST, 7).toString()

        doReturn(NorskIdent("12345")).whenever(personService).hentIdent(eq(IdentType.NorskIdent), any< Ident<*>>())

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(ParticipantsItem()), processDefinitionVersion = "4.2")
        mockBuc.documents = listOf(
            DocumentsItem(id = "3123123", type = SEDType.P9000, status = "draft", allowsAttachments = true, lastUpdate = lastupdate, creationDate = lastupdate, parentDocumentId = parentDocumentId))

        val api = apiRequestWith(euxCaseId, sed = "P9000", institutions = emptyList())

        doReturn(mockBuc).whenever(mockEuxService).getBuc(euxCaseId)

        assertThrows<SedDokumentKanIkkeOpprettesException> {
            sedController.addDocumentToParent(api, parentDocumentId)
        }
    }

    @Test
    fun `call addInstutionAndDocument valider om SED alt finnes i BUC kaster Exception`() {
        val euxCaseId = "1234567890"

        doReturn(NorskIdent("12345")).whenever(personService).hentIdent(eq(IdentType.NorskIdent), any< Ident<*>>())
        val mockBucJson = javaClass.getResource("/json/buc/buc-P_BUC_06-P6000_Sendt.json").readText()
        doReturn( mapJsonToAny(mockBucJson, typeRefs<Buc>())).whenever(mockEuxService).getBuc(euxCaseId)
        val apiRequest = apiRequestWith(euxCaseId, emptyList())

        assertThrows<SedDokumentKanIkkeOpprettesException> {
            sedController.addInstutionAndDocument(apiRequest)
        }

    }

    @Test
    fun `Gitt det opprettes en SED P10000 på tom P_BUC_06 Så skal bucmodel hents på nyt og shortDocument returneres som response`() {
        val euxCaseId = "1234567890"

        doReturn(NorskIdent("12345")).whenever(personService).hentIdent(eq(IdentType.NorskIdent), any< Ident<*>>())
        doReturn(PersonDataCollection(PersonPDLMock.createWith(), PersonPDLMock.createWith())).whenever(personService).hentPersonData(any())
        doReturn(SED(SEDType.P10000)).whenever(mockPrefillSEDService).prefill(any(), any())

        val mockBucJson = javaClass.getResource("/json/buc/buc_P_BUC_06_4.2_tom.json").readText()
        val mockBucJson2 = javaClass.getResource("/json/buc/P_BUC_06_P10000.json").readText()

        doReturn( mapJsonToAny(mockBucJson, typeRefs<Buc>()))
                .doReturn( mapJsonToAny(mockBucJson2, typeRefs<Buc>()))
                .whenever(mockEuxService).getBuc(euxCaseId)

        val newParticipants = listOf(
                InstitusjonItem(country = "FI", institution = "FI:Finland", name="Finland test")
        )
        val apiRequest = apiRequestWith(euxCaseId, newParticipants, "P10000")

        doReturn(BucSedResponse(euxCaseId, "58c26271b21f4feebcc36b949b4865fe")).whenever(mockEuxService).opprettJsonSedOnBuc(any(), any(),eq(euxCaseId),eq(apiRequest.vedtakId))
        doNothing().whenever(mockEuxService).addInstitution(any(), any())

        val result =  sedController.addInstutionAndDocument(apiRequest)

        verify(mockEuxService, times(1)).opprettJsonSedOnBuc(any(), any(), eq(euxCaseId),eq(apiRequest.vedtakId))
        verify(mockEuxService, times(2)).getBuc(eq(euxCaseId))

        assertNotNull(result)
        assertEquals(ShortDocumentItem::class.java, result?.javaClass)
    }

    @Test
    fun `call addInstutionAndDocument  to nye deltakere, men ingen X005`() {
        val euxCaseId = "1234567890"
        doReturn(NorskIdent("12345")).whenever(personService).hentIdent(eq(IdentType.NorskIdent), any< Ident<*>>())
        doReturn(PersonDataCollection(PersonPDLMock.createWith(), PersonPDLMock.createWith())).whenever(personService).hentPersonData(any())

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(ParticipantsItem()))
        mockBuc.documents = listOf(createDummyBucDocumentItem())
        mockBuc.actions = listOf(ActionsItem(name = "Send"))

        doReturn(mockBuc).whenever(mockEuxService).getBuc(euxCaseId)

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id, null)

        doReturn(SED(type = dummyPrefillData.sedType)).whenever(mockPrefillSEDService).prefill(any(), any() )
        doNothing().whenever(mockEuxService).addInstitution(any(), any())

        doReturn(BucSedResponse(euxCaseId,"1")).whenever(mockEuxService).opprettJsonSedOnBuc(any(), any(), eq(euxCaseId),eq(dummyPrefillData.vedtakId))

        val newParticipants = listOf(
                InstitusjonItem(country = "FI", institution = "FI:Finland", name="Finland test"),
                InstitusjonItem(country = "DE", institution = "DE:Tyskland", name="Tyskland test")
        )
        sedController.addInstutionAndDocument(apiRequestWith(euxCaseId, newParticipants))

        verify(mockEuxService, times(1)).addInstitution(any(), any())
        verify(mockEuxService, times(1)).opprettJsonSedOnBuc(any(), any(), eq(euxCaseId),eq(dummyPrefillData.vedtakId))
    }

    @Test
    fun `call addInstutionAndDocument  Exception eller feiler ved oppretting av SED naar X005 ikke finnes`() {
        val euxCaseId = "1234567890"

        doReturn(NorskIdent("12345")).whenever(personService).hentIdent(eq(IdentType.NorskIdent), any< Ident<*>>())
        doReturn(PersonDataCollection(PersonPDLMock.createWith(), PersonPDLMock.createWith())).whenever(personService).hentPersonData(any())

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(ParticipantsItem()))
        mockBuc.documents = listOf(createDummyBucDocumentItem(), DocumentsItem())
        mockBuc.actions = listOf(ActionsItem(name = "Send"))

        doReturn(mockBuc).whenever(mockEuxService).getBuc(euxCaseId)
        doNothing().whenever(mockEuxService).addInstitution(any(), any())

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id, null)

        doReturn(SED(type = dummyPrefillData.sedType)).whenever(mockPrefillSEDService).prefill(any(), any())
        doThrow(SedDokumentIkkeOpprettetException("Expected!")).whenever(mockEuxService).opprettJsonSedOnBuc(any(), any(), eq(euxCaseId),eq(dummyPrefillData.vedtakId))

        val newParticipants = listOf(
                InstitusjonItem(country = "FI", institution = "FI:Finland", name="Finland test"),
                InstitusjonItem(country = "DE", institution = "DE:Tyskland", name="Tyskland test")
        )
        assertThrows<SedDokumentIkkeOpprettetException> {
            sedController.addInstutionAndDocument(apiRequestWith(euxCaseId, newParticipants))
        }
    }

    @Test
    fun `update SED Version from old version to new version`() {
        val sed = SED(SEDType.P2000)
        val bucVersion = "v4.2"

        sedController.updateSEDVersion(sed, bucVersion)
        assertEquals(bucVersion, "v${sed.sedGVer}.${sed.sedVer}")
    }

    @Test
    fun `update SED Version from old version to same version`() {
        val sed = SED(SEDType.P2000)
        val bucVersion = "v4.1"

        sedController.updateSEDVersion(sed, bucVersion)
        assertEquals(bucVersion, "v${sed.sedGVer}.${sed.sedVer}")
    }


    @Test
    fun `update SED Version from old version to unknown new version`() {
        val sed = SED(SEDType.P2000)
        val bucVersion = "v4.4"

        sedController.updateSEDVersion(sed, bucVersion)
        assertEquals("v4.1", "v${sed.sedGVer}.${sed.sedVer}")
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
        doReturn(AktoerId("1122334455")).whenever(personService).hentIdent(eq(IdentType.AktoerId), any< Ident<*>>())

        val result = sedController.getAvdodAktoerIdPDL(apiRequest)
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

        doReturn(AktoerId("467846784671")).whenever(personService).hentIdent(eq(IdentType.AktoerId), any< Ident<*>>())

        val result = sedController.getAvdodAktoerIdPDL(apiRequest)
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
        assertThrows<MangelfulleInndataException> {
            sedController.getAvdodAktoerIdPDL(apiRequest)
        }
    }

    @Test
    fun `call getAvdodAktoerId  expect null value when sed is P2000`() {
        val apireq = ApiRequest(
                subjectArea = "Pensjon",
                sakId = "EESSI-PEN-123",
                sed = "P2000",
                buc = "P_BUC_01",
                aktoerId = "0105094340092",
                avdodfnr = "12345566"
        )
        val result = sedController.getAvdodAktoerIdPDL(request = apireq)
        assertEquals(null, result)
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
                        type = SEDType.P6000,
                        status = "empty",
                        allowsAttachments = true
              )
    }

}

