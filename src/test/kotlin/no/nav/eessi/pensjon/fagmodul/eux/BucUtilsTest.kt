package no.nav.eessi.pensjon.fagmodul.eux

import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ConversationsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Organisation
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ReceiversItem
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.typeRefs
import no.nav.eessi.pensjon.utils.validateJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.skyscreamer.jsonassert.JSONAssert
import java.time.format.DateTimeFormatter

@ExtendWith(MockitoExtension::class)
class BucUtilsTest {

    lateinit var bucUtils: BucUtils
    lateinit var bucjson: String
    lateinit var buc: Buc

    fun getTestJsonFile(filename: String): String {
        val json = javaClass.getResource("/json/buc/${filename}").readText()
        assertTrue(validateJson(json))
        return json
    }

    @BeforeEach
    fun bringItOn() {
        bucjson = getTestJsonFile("buc-22909_v4.1.json")
        buc = mapJsonToAny(bucjson, typeRefs())
        bucUtils = BucUtils(buc)
    }

    @Test
    fun getListofSbdh() {
        val result = bucUtils.getSbdh()
        assertEquals(1, result.size)
        val sbdh = result.first()

        assertEquals("NO:NAVT003", sbdh.sender?.identifier)
        assertEquals("NO:NAVT002", sbdh.receivers?.first()?.identifier)
        assertEquals(SEDType.P2000, sbdh.documentIdentification?.type)
        assertEquals("4.1", sbdh.documentIdentification?.schemaVersion)

    }

    @Test
    fun getCreator() {
        val result = bucUtils.getCreator()
        assertEquals("NAVT003", result?.organisation?.name)
        assertEquals("NO:NAVT003", result?.organisation?.id)
        assertEquals("NO", result?.organisation?.countryCode)
    }

    @Test
    fun getCreatorCountryCode() {
        val result = bucUtils.getCreatorContryCode()
        assertEquals("NO", result.values.first())
    }


    @Test
    fun findFirstDocumentItemByType() {
        val result = bucUtils.findFirstDocumentItemByType(SEDType.P2000)
        assertEquals(SEDType.P2000, result?.type)
        assertEquals("sent", result?.status)
        assertEquals("1b934260853d49ec98080da433a6ef91", result?.id)

        val result2 = bucUtils.findFirstDocumentItemByType(SEDType.P6000)
        assertEquals(SEDType.P6000, result2?.type)
        assertEquals("empty", result2?.status)
        assertEquals("85db6f21f01541899cc80ffc80dff88b", result2?.id)

    }

    @Test
    fun `sjekk for om parentId finnes alt med valgt sedtype`() {
        val sedType = SEDType.P9000
        val parentId = "a89676b0ea7c4d8684e17f15d2471188"

        val bucjson = getTestJsonFile("buc-285268-answerid.json")
        val bucUtil = BucUtils(mapJsonToAny(bucjson, typeRefs()))

        assertTrue(bucUtil.checkIfSedCanBeCreatedEmptyStatus(sedType, parentId))

        val parentIdStatusSendt = "fd9fd9ee97ee46d0a3f5c58d1b245268"

        assertThrows<SedDokumentKanIkkeOpprettesException> {
            bucUtil.checkIfSedCanBeCreatedEmptyStatus(sedType, parentIdStatusSendt)
        }
    }


    @Test
    fun getBucCaseOwnerAndCreatorCountry() {
        val result = bucUtils.getCreatorContryCode()
        assertEquals("NO", result["countrycode"])
    }

    @Test
    fun getProcessDefinitionName() {
        val result = bucUtils.getProcessDefinitionName()
        assertEquals("P_BUC_01", result)
    }

    @Test
    fun getProcessDefinitionVersion() {
        val result41 = bucUtils.getProcessDefinitionVersion()
        assertEquals("v4.1", result41)
        val bucdef41 = bucUtils.getProcessDefinitionName()
        assertEquals("P_BUC_01", bucdef41)

        val bucjson = getTestJsonFile("buc-362590_v4.0.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtilsLocal = BucUtils(buc)

        val result = bucUtilsLocal.getProcessDefinitionVersion()
        assertEquals("v1.0", result)
        val name = bucUtilsLocal.getProcessDefinitionName()
        assertEquals("P_BUC_01", name)
    }

    @Test
    fun getLastDate() {
        val result41 = bucUtils.getLastDate()
        assertEquals("2019-01-23", result41.toString())

        val bucjson = getTestJsonFile("buc-362590_v4.0.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtilsLocal = BucUtils(buc)

        val result10 = bucUtilsLocal.getLastDate()
        assertEquals("2018-11-08", result10.toString())
    }

    @Test
    fun `getStartDateLong parses dates correctly`() {
        val unixTimeStamp = 1567154257318L
        val listOfArgs = listOf<Any>(
            1567154257318L,
            "2019-08-30T10:37:37.318",
            "2019-08-30T09:37:37.318+0100",
            "2019-08-30T09:37:37.318+01:00"
        )
        listOfArgs.forEach { assertEquals(unixTimeStamp, BucUtils(Buc(startDate = it)).getStartDateLong()) }
    }

    @Test
    fun `getEndDateLong parses dates correctly`() {
        val unixTimeStamp = 1567154257318L
        val listOfArgs = listOf(
            1567154257318L,
            "2019-08-30T10:37:37.318",
            "2019-08-30T09:37:37.318+0100",
            "2019-08-30T08:37:37.318+00:00",
            "2019-08-30T09:37:37.318+01:00"
        )
        listOfArgs.forEach { assertEquals(unixTimeStamp, BucUtils(Buc(lastUpdate= it)).getLastDateLong()) }
    }

    @Test
    fun getRinaAksjoner() {
        val result = bucUtils.getRinaAksjon()
        assertEquals(16, result.size)
        val rinaaksjon = result.get(5)
        assertEquals(SEDType.P2000, rinaaksjon.dokumentType)
        assertEquals("P_BUC_01", rinaaksjon.id)
        assertEquals("Update", rinaaksjon.navn)
    }

    @Test
    fun getRinaAksjonerFilteredOnP() {
        val result = bucUtils.getRinaAksjon()
        assertEquals(16, result.size)
        val rinaaksjon = result.get(5)
        assertEquals(SEDType.P2000, rinaaksjon.dokumentType)
        assertEquals("P_BUC_01", rinaaksjon.id)
        assertEquals("Update", rinaaksjon.navn)

        val filterlist = result.filter { it.dokumentType?.name?.startsWith("P")!! }

        assertEquals(9, filterlist.size)
        val rinaaksjon2 = filterlist.get(5)
        assertEquals(SEDType.P5000, rinaaksjon2.dokumentType)
        assertEquals("P_BUC_01", rinaaksjon2.id)
        assertEquals("Create", rinaaksjon2.navn)
    }

    @Test
    fun getBucAndDocumentsWithAttachment() {
        bucjson = getTestJsonFile("buc-158123_2_v4.1.json")
        buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        bucUtils = BucUtils(buc)

        assertEquals(2, bucUtils.getBucAttachments()?.size)

        assertEquals(18, bucUtils.getAllDocuments().size)

        bucUtils.getAllDocuments().forEach {

            if (it.type == SEDType.P8000) {
                assertEquals("1557825747269", it.creationDate.toString())
                assertEquals("1558362934400", it.lastUpdate.toString())
                assertEquals(2, it.attachments?.size)

            }
        }

        assertEquals("2019-05-20", bucUtils.getLastDate().toString())

    }

    @Test
    fun getParticipantsTestOnMock_2() {
        bucjson = getTestJsonFile("buc-158123_2_v4.1.json")
        buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        bucUtils = BucUtils(buc)
        assertEquals(2, bucUtils.getBucAttachments()?.size)
        assertEquals(18, bucUtils.getAllDocuments().size)
        val parts = bucUtils.getParticipants()
        assertEquals(2, parts.size)
    }

    @Test
    fun getParticipantsTestOnMock() {
        val parts = bucUtils.getParticipants()
        assertEquals(2, parts.size)
    }

    @Test
    fun `getGyldigSedAksjonListAsString   returns sorted list ok`(){
        val actualOutput = bucUtils.getSedsThatCanBeCreated()
        assertEquals(14, actualOutput.size)
        assertEquals(SEDType.P5000, actualOutput[6])
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString   returns sorted of one element ok`(){
        val tmpbuc3 = mapJsonToAny(getTestJsonFile("P_BUC_01_4.2_tom.json"), typeRefs<Buc>())
        val bucUtil = BucUtils(tmpbuc3)
        val actualOutput = bucUtil.getFiltrerteGyldigSedAksjonListAsString()
        assertEquals(1, actualOutput.size)
    }

    @Test
    fun `getGyldigSedAksjonListAsString   returns sorted of one element ok`(){
        val tmpbuc3 = mapJsonToAny(getTestJsonFile("P_BUC_01_4.2_tom.json"), typeRefs<Buc>())
        val bucUtil = BucUtils(tmpbuc3)
        val actualOutput = bucUtil.getSedsThatCanBeCreated()
        assertEquals(1, actualOutput.size)
    }

    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString   returns no element`(){
        val tmpbuc3 = mapJsonToAny(getTestJsonFile("P_BUC_01_4.2_P2000.json"), typeRefs<Buc>())
        val bucUtil = BucUtils(tmpbuc3)
        val actualOutput = bucUtil.getFiltrerteGyldigSedAksjonListAsString()
        assertEquals(0, actualOutput.size)
    }



    @Test
    fun `getFiltrerteGyldigSedAksjonListAsString  returns filtered 10 sorted elements`(){
        val actualOutput = bucUtils.getFiltrerteGyldigSedAksjonListAsString()
        assertEquals(10, actualOutput.size)
        assertEquals(SEDType.P6000, actualOutput[7])
    }

    @Test
    fun `getGyldigSedAksjonListAsString  returns 14 sorted elements`(){
        val actualOutput = bucUtils.getSedsThatCanBeCreated()
        assertEquals(14, actualOutput.size)
        assertEquals(SEDType.P6000, actualOutput[7])
    }

    @Test
    fun `Test liste med SED kun PensjonSED skal returneres`() {
        val list = listOf(SEDType.X005, SEDType.P2000, SEDType.P4000, SEDType.H021, SEDType.P9000)

        val result = bucUtils.filterSektorPandRelevantHorizontalSeds(list)
        assertEquals(4, result.size)

        val expected = listOf(SEDType.H021, SEDType.P2000, SEDType.P4000, SEDType.P9000)
        assertEquals(expected, result)
    }

    @Test
    fun `Test liste med SED som skal returneres`() {
        val list =
            listOf(SEDType.X005, SEDType.P2000, SEDType.P4000, SEDType.H020, SEDType.H070, SEDType.H121, SEDType.P9000)

        val result = bucUtils.filterSektorPandRelevantHorizontalSeds(list)
        assertEquals(6, result.size)

        val expected = listOf(SEDType.H020, SEDType.H070, SEDType.H121, SEDType.P2000, SEDType.P4000, SEDType.P9000)
        assertEquals(expected, result)
    }


    @Test
    fun `Test av liste med SEDer der kun PensjonSEDer skal returneres`() {
        val list = listOf(SEDType.X005, SEDType.P2000, SEDType.P4000, SEDType.H020, SEDType.P9000)

        val result = bucUtils.filterSektorPandRelevantHorizontalSeds(list)
        assertEquals(4, result.size)

        val expected = listOf(SEDType.H020, SEDType.P2000, SEDType.P4000, SEDType.P9000)
        assertEquals(expected, result)
    }


    @Test
    fun `findNewParticipants   listene er tom forventer exception`(){
        val bucUtils = BucUtils(Buc(participants = listOf()))
        val candidates = listOf<InstitusjonItem>()
        assertThrows<ManglerDeltakereException> {
            bucUtils.findNewParticipants(candidates)
        }
    }

    @Test
    fun `findNewParticipants   bucDeltaker tom og list har data forventer 2 size`(){
        val bucUtils = BucUtils(Buc(participants = listOf()))
        val candidates = listOf(
                InstitusjonItem(country = "DK", institution = "DK006"),
                InstitusjonItem(country = "PL", institution = "PolishAcc"))
        assertEquals(2, bucUtils.findNewParticipants(candidates).size)
    }

    @Test
    fun `findNewParticipants   list er lik forventer 0 size`(){
        val bucUtils = BucUtils(Buc(participants = listOf(
                ParticipantsItem(organisation = Organisation(countryCode = "DK", id = "DK006")),
                ParticipantsItem(organisation = Organisation(countryCode = "PL", id = "PolishAcc")))))

        val candidates = listOf(
                InstitusjonItem(country = "PL", institution = "PolishAcc"),
                InstitusjonItem(country = "DK", institution = "DK006"))

        assertEquals(0, bucUtils.findNewParticipants(candidates).size)
    }

    @Test
    fun `findNewParticipants   buclist er 2 mens list er 3 forventer 1 size`(){
        val bucUtils = BucUtils(Buc(participants = listOf(
                ParticipantsItem(organisation = Organisation(countryCode = "DK", id = "DK006")),
                ParticipantsItem(organisation = Organisation(countryCode = "PL", id = "PolishAcc")))))

        val candidates = listOf(
                InstitusjonItem(country = "PL", institution = "PolishAcc"),
                InstitusjonItem(country = "DK", institution = "DK006"),
                InstitusjonItem(country = "FI", institution = "FINLAND"))

        assertEquals(1, bucUtils.findNewParticipants(candidates).size)
    }

    @Test
    fun `findNewParticipants   buclist er 5 og list er 0 forventer 0 size`(){
        val bucUtils = BucUtils(Buc(participants = listOf(
                ParticipantsItem(organisation = Organisation(countryCode = "DK", id = "DK006")),
                ParticipantsItem(organisation = Organisation(countryCode = "PL", id = "PolishAcc")),
                ParticipantsItem(organisation = Organisation(countryCode = "PL", id = "PolishAcc")),
                ParticipantsItem(organisation = Organisation(countryCode = "DK", id = "DK006")),
                ParticipantsItem(organisation = Organisation(countryCode = "FI", id = "FINLAND")))))

        val candidates = listOf<InstitusjonItem>()

        assertEquals(0, bucUtils.findNewParticipants(candidates).size)
    }

    @Test
    fun findNewParticipantsMockwithExternalCaseOwnerAddEveryoneInBucResultExpectedToBeZero(){
        val bucjson = getTestJsonFile("buc-254740_v4.1.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        assertEquals(3, bucUtils.getParticipants().size)

        val candidates = listOf<InstitusjonItem>(
                InstitusjonItem(country = "NO", institution = "NO:NAVT003", name = "NAV T003"),
                InstitusjonItem(country = "NO", institution = "NO:NAVT002", name = "NAV T002"),
                InstitusjonItem(country = "NO", institution = "NO:NAVT008", name = "NAV T008")
        )
        assertEquals(0, bucUtils.findNewParticipants(candidates).size)
    }

    @Test
    fun `finn alle deltakere på buc som er aktive og deaktive`() {
        val bucjson = getTestJsonFile("buc-4929378.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        fun prettyPrint(organisation: Organisation?): String {
            return """
                ID  : ${organisation?.id}
                Name: ${organisation?.name}
                Land: ${organisation?.countryCode}
                ${"-".repeat(80)}
            """.trimIndent()
        }

        fun prettyPrintSbdhReceivers(reciver: ReceiversItem?): String {
            return """
              id:    ${reciver?.identifier}
              type:  ${reciver?.contactTypeIdentifier}
            """.trimIndent()
        }

fun prettyPrintConversation(conversationsItem: ConversationsItem?) {

println("id   : ${conversationsItem?.id}")
println("date : ${conversationsItem?.date}")
conversationsItem?.userMessages?.onEach {
    println(prettyPrint(it.receiver))
    println(prettyPrint(it.sender))
    println(
        """
        ident : ${it.sbdh?.sender?.identifier}
        type :  ${it.sbdh?.sender?.contactTypeIdentifier}
        """.trimIndent()
    )
        it.sbdh?.receivers?.onEach {
            println( prettyPrintSbdhReceivers(it) )
        }
    }
}
        bucUtils.getParticipants().onEach {
            println("""
ORG:               
${prettyPrint(it.organisation)}   
ROLE:
${it.role}
${"-".repeat(80)}
            """.trimIndent()
            )
        }

        bucUtils.getBuc().documents?.filter { doc -> doc.type == SEDType.X007 && doc.status == "received"}
            ?.onEach { doc ->
            println("""
                ID: ${doc.id}
                TYPE: ${doc.type}
                STATUS: ${doc.status}
                ${"-".repeat(80)}
            """)
                doc.conversations?.forEach {  con ->
                    prettyPrintConversation(con)
                }
                println("-".repeat(80))
            }

    }


    @Test
    fun findNewParticipantsMockwithExternalCaseOwnerResultExpectedToBeZero(){
        val bucjson = getTestJsonFile("buc-254740_v4.1.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        assertEquals(3, bucUtils.getParticipants().size)

        bucUtils.getCreator()

        val candidates = listOf<InstitusjonItem>(InstitusjonItem(country = "NO", institution = "NO:NAVT003", name = "NAV T003"))

        assertEquals(0, bucUtils.findNewParticipants(candidates).size)
    }

    @Test
    fun findNewParticipantsMockwithExternalCaseOwnerResultExpectedOne(){
        val bucjson = getTestJsonFile("buc-254740_v4.1.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        assertEquals(3, bucUtils.getParticipants().size)

        val candidates = listOf<InstitusjonItem>(InstitusjonItem(country = "NO", institution = "NO:NAVT007", name = "NAV T007"))

        assertEquals(1, bucUtils.findNewParticipants(candidates).size)

    }

    @Test
    fun findCaseOwnerOnBucIsNotAllwaysSameAsCreator() {
        val result = bucUtils.getCaseOwner()
        assertEquals("NO:NAVT003", result?.institution)
    }

    @Test
    fun parseAndTestBucAndSedView() {
        val bucjson = getTestJsonFile("buc-280670.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())

        val bucview =  BucAndSedView.from(buc)

        assertEquals(1567155195638, bucview.startDate)
        assertEquals(1567155212000, bucview.lastUpdate)
    }

    @Test
    fun parseAndTestBucAttachmentsDate() {
        val bucjson = getTestJsonFile("buc-279020big.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())

        val bucview =  BucAndSedView.from(buc)
        assertEquals(1567088832589, bucview.startDate)
        assertEquals(1567178490000, bucview.lastUpdate)
    }

    @Test
    fun parseAndTestBucMockError() {
        val error = "Error; no access"
        val bucview =  BucAndSedView.fromErr(error)
        assertEquals(error, bucview.error)
    }


    @Test
    fun bucsedandviewDisplaySedsWithParentIdToReply() {
        val bucjson = getTestJsonFile("buc-285268-answerid.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        val result = bucUtils.getAllDocuments()
        assertEquals(16, result.size)

        val filterParentId = result.filter { it.parentDocumentId != null }
        assertEquals(3, filterParentId.size)

    }

    @Test
    fun bucsedandviewCheck() {
        val bucjson = getTestJsonFile("buc-285268-answerid.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())

        val bucAndSedView = BucAndSedView.from(buc)

        val seds = bucAndSedView.seds
        val filterParentId = seds?.filter { it.parentDocumentId != null }
        assertEquals(3, filterParentId?.size)

        assertEquals("NO", bucAndSedView.creator?.country)
        assertEquals("NO:NAVT003", bucAndSedView.creator?.institution)
        assertEquals("NAVT003", bucAndSedView.creator?.name)


    }

    @Test
    fun bucsedandviewCheckforCaseOwnerIfmissingUseCreator() {
        val bucjson = getTestJsonFile("buc-287679short.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())

        val bucAndSedView = BucAndSedView.from(buc)

        val seds = bucAndSedView.seds
        assertEquals(1, seds?.size)

        assertEquals("NO", bucAndSedView.creator?.country)
        assertEquals("NO:NAVT002", bucAndSedView.creator?.institution)
        assertEquals("NAVT002", bucAndSedView.creator?.name)

    }

    @Test
    fun bucsedandviewCheckforCaseOwner() {
        val bucAndSedView = BucAndSedView.from(buc)

        val seds = bucAndSedView.seds
        assertEquals(15, seds?.size)

        assertEquals("NO", bucAndSedView.creator?.country)
        assertEquals("NO:NAVT003", bucAndSedView.creator?.institution)
        assertEquals("NAVT003", bucAndSedView.creator?.name)

    }

    @Test
    fun hentutBucsedviewmedDato() {
        val bucjson = getTestJsonFile("buc-279020big.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucAndSedView = BucAndSedView.from(buc)

        val seds = bucAndSedView.seds.orEmpty()

        assertEquals(25, seds.size)
        assertEquals("NO", bucAndSedView.creator?.country)
        assertEquals("NO:NAVT002", bucAndSedView.creator?.institution)
        assertEquals("NAVT002", bucAndSedView.creator?.name)
        assertEquals(1567088832589, bucAndSedView.startDate)
        assertEquals(1567178490000, bucAndSedView.lastUpdate)

        val startDate = DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.ofEpochMilli (1567088832589))
        val startDlen = startDate.length -5
        assertEquals(startDate.substring(0, startDlen), buc.startDate.toString().substring(0,19))
        assertEquals("2019-08-29T14:27:12.589+0000", buc.startDate)
        assertEquals("2019-08-30T15:21:30.000+0000", buc.lastUpdate)

        val exception = mapOf<SEDType, Boolean?>(SEDType.P7000 to false, SEDType.P2200 to true, SEDType.P8000 to true, SEDType.P9000 to true, SEDType.P6000 to true, SEDType.H070 to false)
        val result = seds.distinctBy { it.type }.filter { exception.contains(it.type) }.map { it }
        //utvalg av sed som støtter eller ikke støtter vedlegg
        result.forEach {
            assertEquals(exception[it.type], it.allowsAttachments)
        }

    }

    @Test
    fun `check that document P12000 on buc support attchemnt`() {
        val bucjson = getTestJsonFile("buc-P_BUC_08-P12000.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucAndSedView = BucAndSedView.from(buc)
        val seds = bucAndSedView.seds

        val single = seds!!.filter { it.type == SEDType.P12000 }.map { it }.first()
        assertEquals(false , single.allowsAttachments)

    }

    @Test
    fun `check that document P12000 is allready on buc throw Exception`() {
        val bucjson = getTestJsonFile("buc-P_BUC_08-P12000.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        assertThrows<SedDokumentKanIkkeOpprettesException> {
            bucUtils.checkIfSedCanBeCreated(SEDType.P12000, "123333")
        }
    }

    @Test
    fun `check that document P10000 is allready on buc throw Exception`() {
        val bucjson = getTestJsonFile("buc-P_BUC_06-P6000_Sendt.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        assertThrows<SedDokumentKanIkkeOpprettesException> {
            bucUtils.checkIfSedCanBeCreated(SEDType.P10000, "123333")
        }
    }

    @Test
    fun `check that document P50000 is allready on buc throw Exception`() {
        val bucjson = getTestJsonFile("buc-P_BUC_06-P6000_Sendt.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        assertThrows<SedDokumentKanIkkeOpprettesException> {
            bucUtils.checkIfSedCanBeCreated(SEDType.P5000, "123333")
        }
    }

    @Test
    fun `check that different document on BUC_03 is allowed or not will throw Exception`() {
        val bucjson = getTestJsonFile("buc-279020big.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        val allowed = listOf(SEDType.P5000, SEDType.P6000, SEDType.P8000, SEDType.P10000, SEDType.H121, SEDType.H020)

        allowed.forEach {
            assertEquals(true, bucUtils.checkIfSedCanBeCreated(it, "123333") )
        }

        val notAllowd = listOf(SEDType.P2200, SEDType.P4000, SEDType.P3000_NO, SEDType.P9000)
        notAllowd.forEach {
            assertThrows<SedDokumentKanIkkeOpprettesException> {
                bucUtils.checkIfSedCanBeCreated(it, "123333")
            }
        }
    }

    @Test
    fun `check that different document on BUC_01 is allowed or not will throw Exception`() {
        val bucjson = getTestJsonFile("P_BUC_01_4.2_P2000.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        val notAllowd = listOf(SEDType.P2000, SEDType.P4000, SEDType.P3000_NO, SEDType.P9000)
        notAllowd.forEach {
            assertThrows<SedDokumentKanIkkeOpprettesException> {
                bucUtils.checkIfSedCanBeCreated(it, "123333")
            }
        }
    }

    @Test
    fun `check that different document on other BUC_01 is allowed or not will throw Exception`() {
        val bucjson = getTestJsonFile("buc-22909_v4.1.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        val allowed = listOf(SEDType.P4000, SEDType.P5000, SEDType.P6000, SEDType.P8000, SEDType.P7000, SEDType.P10000, SEDType.H120, SEDType.H020)

        allowed.forEach {
            assertEquals(true, bucUtils.checkIfSedCanBeCreated(it, "123333") )
        }

        val notAllowd = listOf(SEDType.P2000)
        notAllowd.forEach {
            assertThrows<SedDokumentKanIkkeOpprettesException> {
                bucUtils.checkIfSedCanBeCreated(it, "123333")
            }
        }
    }

    @Test
    fun `a draft SED should have sender and receiver based on the last conversation`() {
        // filen vi har
        val bucjson = getTestJsonFile("BucResponseFraEUXMedX007.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())


        // behandle
        val view = BucAndSedView.from(buc)
        val p5000KladdId = "88243596f81f4553a3fe3260d751a6b8"

        val p5000 = view.seds?.filter { it.id == p5000KladdId }
                ?.map { it }
                ?.first()

        // sjekk at P5000 har rett mottaker (og avsender)
        assertEquals(2, p5000?.participants?.size)

        val participants = p5000?.participants

        assertEquals("NO:NAVAT07", participants?.filter { it?.role == "Sender" }?.first()?.organisation?.id)
        assertEquals("NO:NAVAT05", participants?.filter { it?.role == "Receiver" }?.first()?.organisation?.id)

        // sjekk at P8000 har rett mottaker (og avsender)

        val p8000KladdId = "86f89d344e4940ee87adc11ff21ea78a"

        val p8000 = view.seds?.filter { it.id == p8000KladdId }
                ?.map { it }
                ?.first()

        // sjekk at P5000 har rett mottaker (og avsender)
        assertEquals(2, p8000?.participants?.size)

        val participantsP8000 = p8000?.participants

        assertEquals("NO:NAVAT07", participantsP8000?.filter { it?.role == "Sender" }?.first()?.organisation?.id)
        assertEquals("NO:NAVAT05", participantsP8000?.filter { it?.role == "Receiver" }?.first()?.organisation?.id)
    }

    @Test
    fun `an empty sed should have sender and receiver based on the last conversation`() {
        // filen vi har
        val bucjson = getTestJsonFile("BucResponseFraEUXMedX007.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())


        // behandle
        val view = BucAndSedView.from(buc)
        val p6000Empty = "53c2a6d9642d4c089284ef5762d64b13"

        val p6000 = view.seds?.filter { it.id == p6000Empty }
                ?.map { it }
                ?.first()

        // sjekk at P5000 har rett mottaker (og avsender)
        assertEquals(2, p6000?.participants?.size)

        val participantsP6000 = p6000?.participants

        assertEquals("NO:NAVAT07", participantsP6000?.filter { it?.role == "Sender" }?.first()?.organisation?.id)
        assertEquals("NO:NAVAT05", participantsP6000?.filter { it?.role == "Receiver" }?.first()?.organisation?.id)
    }


    @Test
    fun `a sent SED sent after X007 should have new receiver`() {
        //Vi velger å ikke benytte oss av usermessages, siden det ser ut til at det holder å bruke conversations.last()
        // filen vi har
        val sedjson = getTestJsonFile("Buc-P8000-sendt.json")
        val documentItem = mapJsonToAny(sedjson, typeRefs<DocumentsItem>())
        val mockBuc = Buc(documents = listOf(documentItem))

        // behandle
        val view = BucAndSedView.from(mockBuc)

        assertEquals(1, view.seds?.size)
        val p8000 = view.seds?.first()
        val participantsP8000 = p8000?.participants

        assertEquals("NO:NAVAT07", participantsP8000?.filter { it?.role == "Sender" }?.first()?.organisation?.id)
        assertEquals("NO:NAVAT05", participantsP8000?.filter { it?.role == "Receiver" }?.first()?.organisation?.id)
    }

    @Test
    fun `an sed exchanged before X007 should have old sender and receiver`() {
        val sedP2200CancelledId = "49bd11a447db48fc8edace43477781c9"
        val bucjson = getTestJsonFile("BucResponseFraEUXMedX007.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())

        val viewP2200 = BucAndSedView.from(buc)

        assertEquals(14, viewP2200.seds?.size)

        val p22000 = viewP2200.seds?.filter { it.id == sedP2200CancelledId }
                ?.map { it }
                ?.first()

        val participantsP22000 = p22000?.participants

        assertEquals(2, participantsP22000?.size)

        assertEquals("NO:NAVAT08", participantsP22000?.filter { it?.role == "Sender" }?.first()?.organisation?.id)
        assertEquals("NO:NAVAT07", participantsP22000?.filter { it?.role == "Receiver" }?.first()?.organisation?.id)
    }

    @Test
    fun `compare generated result with expectation - for BUC with X007`() {
        val input = getTestJsonFile("BucResponseFraEUXMedX007.json")
        val expected = getTestJsonFile("BucResponseFraFagmodulenMedX007.json")
        val buc = mapJsonToAny(input, typeRefs<Buc>())

        val bucAndSedView = BucAndSedView.from(buc)

        val actual = listOf(bucAndSedView).toJson()
        JSONAssert.assertEquals(expected, actual, false)
    }

    @Test
    fun `compare generated result with expectation - for BUC with X007 - caseid 1410989`() {
        val input = getTestJsonFile("BucResponseFraEUXMedX007-1410989.json")
        val expected = getTestJsonFile("BucResponseFraFagmodulenMedX007_1410989.json")
        val buc = mapJsonToAny(input, typeRefs<Buc>())

        val bucAndSedView = BucAndSedView.from(buc)

        val actual = listOf(bucAndSedView).toJson()
        JSONAssert.assertEquals(expected, actual, false)
    }
}
