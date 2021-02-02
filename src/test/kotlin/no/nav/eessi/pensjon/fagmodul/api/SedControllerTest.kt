package no.nav.eessi.pensjon.fagmodul.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doNothing
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.fagmodul.eux.EuxService
import no.nav.eessi.pensjon.fagmodul.eux.PinOgKrav
import no.nav.eessi.pensjon.fagmodul.eux.SedDokumentIkkeOpprettetException
import no.nav.eessi.pensjon.fagmodul.eux.SedDokumentKanIkkeOpprettesException
import no.nav.eessi.pensjon.fagmodul.eux.basismodel.BucSedResponse
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ActionsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Organisation
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ShortDocumentItem
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.ApiRequest
import no.nav.eessi.pensjon.fagmodul.prefill.ApiSubject
import no.nav.eessi.pensjon.fagmodul.prefill.MangelfulleInndataException
import no.nav.eessi.pensjon.fagmodul.prefill.PersonDataService
import no.nav.eessi.pensjon.fagmodul.prefill.PrefillService
import no.nav.eessi.pensjon.fagmodul.prefill.SedAndType
import no.nav.eessi.pensjon.fagmodul.prefill.SubjectFnr
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.sedmodel.Bruker
import no.nav.eessi.pensjon.fagmodul.sedmodel.Krav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Nav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Person
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerId
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.eessi.pensjon.utils.typeRefs
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
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(MockitoExtension::class)
class SedControllerTest {

    @Spy
    lateinit var mockEuxService: EuxService

    @Spy
    lateinit var auditLogger: AuditLogger

    @Mock
    lateinit var mockAktoerIdHelper: AktoerregisterService

    @Mock
    lateinit var mockPrefillSEDService: PrefillSEDService

    @Mock
    lateinit var personService: PersonDataService

//    private lateinit var prefillService: PrefillService

    private lateinit var sedController: SedController

    @BeforeEach
    fun setUp() {
        mockEuxService.initMetrics()

        val prefillService = PrefillService(mockPrefillSEDService)
        prefillService.initMetrics()

        personService.initMetrics()
        this.sedController = SedController(mockEuxService,
                prefillService,
                personService,
                mockAktoerIdHelper,
                auditLogger)
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
        doReturn(NorskIdent("12345")).whenever(mockAktoerIdHelper).hentGjeldendeIdent(eq(IdentGruppe.NorskIdent), any<AktoerId>())

        val utfyllMock = ApiRequest.buildPrefillDataModelOnExisting(mockData, NorskIdent("12345").id, null)

        utfyllMock.sed.nav = Nav(bruker = Bruker(person = Person(fornavn = "Dummy", etternavn = "Dummy", foedselsdato = "1900-10-11", kjoenn = "K")), krav = Krav("1937-12-11"))

        doReturn(utfyllMock.sed).whenever(mockPrefillSEDService).prefill(any(), eq(null))

        val response = sedController.prefillDocument(mockData)
        assertNotNull(response)

        val sed = SED.fromJson(response)

        assertEquals("P6000", sed.sed)
        assertEquals("Dummy", sed.nav?.bruker?.person?.fornavn)
        assertEquals("Dummy", sed.nav?.bruker?.person?.etternavn)
    }

    @Test
    fun getDocumentfromRina() {

        val sed = SED("P2000")
        doReturn(sed).`when`(mockEuxService).getSedOnBucByDocumentId("2313", "23123123123")

        val result = sedController.getDocument("2313", "23123123123")
        assertEquals(sed, result)
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
        val mockBuc = mapJsonToAny(String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/P_BUC_01_4.2_tom.json"))), typeRefs<Buc>())
        val buc = "P_BUC_01"
        val rinanr = "1000101"

        doReturn(mockBuc).whenever(mockEuxService).getBuc(rinanr)

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson( listOf("P2000")))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(1, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString   buc_06 returns 4 seds`() {
        val mockBuc = mapJsonToAny(String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc_P_BUC_06_4.2_tom.json"))), typeRefs<Buc>())
        val buc = "P_BUC_06"
        val rinanr = "1000101"

        doReturn(mockBuc).whenever(mockEuxService).getBuc(rinanr)

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson( listOf("P5000", "P6000", "P7000", "P10000")))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(4, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString   buc_06 returns 3 seds if a sed already exists`() {
        val mockBuc = mapJsonToAny(String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-P_BUC_06_4.2_P5000.json"))), typeRefs<Buc>())
        val buc = "P_BUC_06"
        val rinanr = "1000101"

        doReturn(mockBuc).whenever(mockEuxService).getBuc(rinanr)

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson( listOf("P10000", "P6000", "P7000")))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(3, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString buc_01 returns lots of seds`() {
        val mockBuc = mapJsonToAny(String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-22909_v4.1.json"))), typeRefs<Buc>())
        val buc = "P_BUC_01"
        val rinanr = "1000101"

        doReturn(mockBuc).whenever(mockEuxService).getBuc(rinanr)

        val actualResponse = sedController.getSeds(buc, rinanr)

        val expectedResponse = ResponseEntity.ok().body(mapAnyToJson( listOf("H020", "H070", "H120", "P10000", "P3000_NO", "P4000", "P5000", "P6000", "P7000", "P8000" )))

        assertEquals(expectedResponse, actualResponse)

        val list = mapJsonToAny(actualResponse.body!!, typeRefs<List<String>>())
        assertEquals(10, list.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString buc_06 returns 0 seds if a sed is sent`() {
        val mockBuc = mapJsonToAny(String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-P_BUC_06-P5000_Sendt.json"))), typeRefs<Buc>())
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
    fun getYtelseKravtypeOk() {
        val mockKrav = PinOgKrav(fnr = "13212312", krav = Krav(dato = "2019-02-01", type = "01"))

        doReturn(mockKrav).whenever(mockEuxService).
                hentFnrOgYtelseKravtype(
                        any(),
                        any()
                )

        val mockResult =  sedController.getPinOgYtelseKravtype("12123", "3123123")

        assertEquals("13212312", mockResult.fnr)
        assertEquals("01", mockResult.krav?.type)
        assertEquals("2019-02-01", mockResult.krav?.dato)
    }

    @Test
    fun `call addInstutionAndDocument mock adding two institusjon when X005 exists already`() {
        val euxCaseId = "1234567890"

        doReturn(NorskIdent("12345")).whenever(mockAktoerIdHelper).hentGjeldendeIdent(eq(IdentGruppe.NorskIdent), any<AktoerId>())

        val mockParticipants = listOf(ParticipantsItem(role = "CaseOwner", organisation = Organisation(countryCode = "NO", name = "NAV", id = "NAV")))
        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = mockParticipants)
        mockBuc.documents = listOf(createDummyBucDocumentItem(), DocumentsItem(type = "X005"))
        mockBuc.actions = listOf(ActionsItem(type = "Send", name = "Send"))

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id, null)

        doReturn(mockBuc).whenever(mockEuxService).getBuc(euxCaseId)
        doReturn(dummyPrefillData.sed).whenever(mockPrefillSEDService).prefill(any(), eq(null))
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
        mockBuc.documents = listOf(createDummyBucDocumentItem(), DocumentsItem(type = "X005"))
        mockBuc.actions = listOf(ActionsItem(type = "Send", name = "Send"))

        val newParticipants = listOf(
                InstitusjonItem(country = "FI", institution = "Finland", name="Finland test"),
                InstitusjonItem(country = "DE", institution = "Tyskland", name="Tyskland test")
        )

        doReturn(NorskIdent("12345")).whenever(mockAktoerIdHelper).hentGjeldendeIdent(eq(IdentGruppe.NorskIdent), any<AktoerId>())
        doReturn(mockBuc).whenever(mockEuxService).getBuc(euxCaseId)

        val apirequest = apiRequestWith(euxCaseId, newParticipants)
        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id)

        doReturn(dummyPrefillData.sed).whenever(mockPrefillSEDService).prefill(any(), eq(null))

        assertThrows<ResponseStatusException> {
            sedController.addInstutionAndDocument(apirequest)
        }

    }

    @Test
    fun `call addInstutionAndDocument  ingen ny Deltaker kun hovedsed`() {
        val euxCaseId = "1234567890"

        doReturn(NorskIdent("12345")).whenever(mockAktoerIdHelper).hentGjeldendeIdent(eq(IdentGruppe.NorskIdent), any<AktoerId>())

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(ParticipantsItem()))
        mockBuc.documents = listOf(createDummyBucDocumentItem())
        mockBuc.actions = listOf(ActionsItem(type = "Send", name = "Send"))

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id, null)

        doReturn(dummyPrefillData.sed).whenever(mockPrefillSEDService).prefill(any(), eq(null))
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
       doReturn(NorskIdent("12345")).whenever(mockAktoerIdHelper).hentGjeldendeIdent(eq(IdentGruppe.NorskIdent), any<AktoerId>())

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(ParticipantsItem()), processDefinitionVersion = "4.2")
        mockBuc.documents = listOf(DocumentsItem(id = "3123123", type = "P9000", status = "empty", allowsAttachments = true  ), DocumentsItem(id = parentDocumentId, type = "P8000", status = "received", allowsAttachments = true))
        mockBuc.actions = listOf(ActionsItem(id = "1000", type = "Received", name = "Received"))

        val api = apiRequestWith(euxCaseId, sed = "P9000", institutions = emptyList())

        val sed = SED("P9000")
        val sedandtype = SedAndType(SEDType.P9000, sed.toJsonSkipEmpty())

        doReturn(sed).whenever(mockPrefillSEDService).prefill(any(), eq(null))

        doReturn(mockBuc).whenever(mockEuxService).getBuc(euxCaseId)
        doReturn(BucSedResponse(euxCaseId, "3123123")).whenever(mockEuxService).opprettSvarJsonSedOnBuc(
            sedandtype.sed,
            euxCaseId,
            parentDocumentId,
            api.vedtakId
        )

        val result = sedController.addDocumentToParent(api, parentDocumentId)

        print(result?.toJson())

    }


    @Test
    fun `call addInstutionAndDocument valider om SED alt finnes i BUC kaster Exception`() {
        val euxCaseId = "1234567890"

        doReturn(NorskIdent("12345")).whenever(mockAktoerIdHelper).hentGjeldendeIdent(eq(IdentGruppe.NorskIdent), any<AktoerId>())
        val mockBucJson = String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-P_BUC_06-P6000_Sendt.json")))
        doReturn( mapJsonToAny(mockBucJson, typeRefs<Buc>())).whenever(mockEuxService).getBuc(euxCaseId)
        val apiRequest = apiRequestWith(euxCaseId, emptyList())

        assertThrows<SedDokumentKanIkkeOpprettesException> {
            sedController.addInstutionAndDocument(apiRequest)
        }

    }

    @Test
    fun `Gitt det opprettes en SED P10000 på tom P_BUC_06 Så skal bucmodel hents på nyt og shortDocument returneres som response`() {
        val euxCaseId = "1234567890"
        doReturn(NorskIdent("12345")).whenever(mockAktoerIdHelper).hentGjeldendeIdent(eq(IdentGruppe.NorskIdent), any<AktoerId>())
        doReturn(SED("P10000")).whenever(mockPrefillSEDService).prefill(any(), eq(null))

        val mockBucJson = String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc_P_BUC_06_4.2_tom.json")))
        val mockBucJson2 = String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/P_BUC_06_P10000.json")))

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

        val expected = """
            {
              "id" : "58c26271b21f4feebcc36b949b4865fe",
              "parentDocumentId" : null,
              "type" : "P10000",
              "status" : "received",
              "creationDate" : 1593520973389,
              "lastUpdate" : 1593520973389,
              "displayName" : "Transfer of additional information",
              "participants" : [ {
                "role" : "Sender",
                "organisation" : {
                  "address" : {
                    "country" : "NO",
                    "town" : null,
                    "street" : null,
                    "postalCode" : null,
                    "region" : null
                  },
                  "activeSince" : "2018-08-26T22:00:00.000+0000",
                  "registryNumber" : null,
                  "acronym" : "NAV ACCT 08",
                  "countryCode" : "NO",
                  "contactMethods" : null,
                  "name" : "NAV ACCEPTANCE TEST 08",
                  "location" : null,
                  "assignedBUCs" : null,
                  "id" : "NO:NAVAT08",
                  "accessPoint" : null,
                  "identifier" : null,
                  "contactTypeIdentifier" : null,
                  "authority" : null
                },
                "selected" : false
              }, {
                "role" : "Receiver",
                "organisation" : {
                  "address" : {
                    "country" : "NO",
                    "town" : null,
                    "street" : null,
                    "postalCode" : null,
                    "region" : null
                  },
                  "activeSince" : "2018-08-26T22:00:00.000+0000",
                  "registryNumber" : null,
                  "acronym" : "NAV ACCT 07",
                  "countryCode" : "NO",
                  "contactMethods" : null,
                  "name" : "NAV ACCEPTANCE TEST 07",
                  "location" : null,
                  "assignedBUCs" : null,
                  "id" : "NO:NAVAT07",
                  "accessPoint" : null
                },
                "selected" : false
              } ],
              "attachments" : [ ],
              "version" : "1",
              "firstVersion" : {
                "date" : "2020-06-30T12:42:53.389+0000",
                "id" : "1"
              },
              "lastVersion" : {
                "date" : "2020-06-30T12:42:53.389+0000",
                "id" : "1"
              },
              "allowsAttachments" : true,
              "message" : null
            }
        """.trimIndent()

        assertEquals(expected, result?.toJson())

    }

    @Test
    fun `call addInstutionAndDocument  to nye deltakere, men ingen X005`() {
        val euxCaseId = "1234567890"
        doReturn(NorskIdent("12345")).whenever(mockAktoerIdHelper).hentGjeldendeIdent(eq(IdentGruppe.NorskIdent), any<AktoerId>())

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(ParticipantsItem()))
        mockBuc.documents = listOf(createDummyBucDocumentItem())
        mockBuc.actions = listOf(ActionsItem(type = "Send", name = "Send"))

        doReturn(mockBuc).whenever(mockEuxService).getBuc(euxCaseId)

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id, null)

        doReturn(dummyPrefillData.sed).whenever(mockPrefillSEDService).prefill(any(), eq(null))

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
        doReturn(NorskIdent("12345")).whenever(mockAktoerIdHelper).hentGjeldendeIdent(eq(IdentGruppe.NorskIdent), any<AktoerId>())

        val mockBuc = Buc(id = "23123", processDefinitionName = "P_BUC_01", participants = listOf(ParticipantsItem()))
        mockBuc.documents = listOf(createDummyBucDocumentItem(), DocumentsItem())
        mockBuc.actions = listOf(ActionsItem(type = "Send", name = "Send"))

        doReturn(mockBuc).whenever(mockEuxService).getBuc(euxCaseId)
        doNothing().whenever(mockEuxService).addInstitution(any(), any())

        val dummyPrefillData = ApiRequest.buildPrefillDataModelOnExisting(apiRequestWith(euxCaseId), NorskIdent("12345").id, null)

        doReturn(dummyPrefillData.sed).whenever(mockPrefillSEDService).prefill(any(), eq(null))
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
        val sed = SED("P2000")
        val bucVersion = "v4.2"

        sedController.updateSEDVersion(sed, bucVersion)
        assertEquals(bucVersion, "v${sed.sedGVer}.${sed.sedVer}")
    }

    @Test
    fun `update SED Version from old version to same version`() {
        val sed = SED("P2000")
        val bucVersion = "v4.1"

        sedController.updateSEDVersion(sed, bucVersion)
        assertEquals(bucVersion, "v${sed.sedGVer}.${sed.sedVer}")
    }


    @Test
    fun `update SED Version from old version to unknown new version`() {
        val sed = SED("P2000")
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
        whenever(mockAktoerIdHelper.hentGjeldendeIdent(eq(IdentGruppe.AktoerId), any<NorskIdent>())).thenReturn(AktoerId("1122334455"))
        val result = sedController.getAvdodAktoerId(apiRequest)
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
        whenever(mockAktoerIdHelper.hentGjeldendeIdent(eq(IdentGruppe.AktoerId), any<NorskIdent>())).thenReturn(AktoerId("467846784671"))

        val result = sedController.getAvdodAktoerId(apiRequest)
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
            sedController.getAvdodAktoerId(apiRequest)
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
        val result = sedController.getAvdodAktoerId(request = apireq)
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
                        type = "P6000",
                        status = "empty",
                        allowsAttachments = true
              )
    }

}
