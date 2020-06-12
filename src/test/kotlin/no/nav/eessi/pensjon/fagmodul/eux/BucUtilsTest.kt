package no.nav.eessi.pensjon.fagmodul.eux

import com.google.common.collect.Lists
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Organisation
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import no.nav.eessi.pensjon.utils.validateJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Files
import java.nio.file.Paths
import java.time.format.DateTimeFormatter
import kotlin.streams.toList

@ExtendWith(MockitoExtension::class)
class BucUtilsTest {

    lateinit var bucUtils: BucUtils
    lateinit var bucjson: String
    lateinit var buc: Buc

    fun getTestJsonFile(filename: String): String {
        val filepath = "src/test/resources/json/buc/${filename}"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))
        return json
    }

    @BeforeEach
    fun bringItOn() {
        bucjson = getTestJsonFile("buc-22909_v4.1.json")
        buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        bucUtils = BucUtils(buc)
    }

    @Test
    fun getListofSbdh() {
        val result = bucUtils.getSbdh()
        assertEquals(1, result.size)
        val sbdh = result.first()

        assertEquals("NO:NAVT003", sbdh.sender?.identifier)
        assertEquals("NO:NAVT002", sbdh.receivers?.first()?.identifier)
        assertEquals("P2000", sbdh.documentIdentification?.type)
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
        assertEquals("NO", Lists.newArrayList(result.values).get(0))
    }


    @Test
    fun findFirstDocumentItemByType() {
        val result = bucUtils.findFirstDocumentItemByType(SEDType.P2000)
        assertEquals(SEDType.P2000.name, result?.type)
        assertEquals("sent", result?.status)
        assertEquals("1b934260853d49ec98080da433a6ef91", result?.id)

        val result2 = bucUtils.findFirstDocumentItemByType(SEDType.P6000)
        assertEquals(SEDType.P6000.name, result2?.type)
        assertEquals("empty", result2?.status)
        assertEquals("85db6f21f01541899cc80ffc80dff88b", result2?.id)

    }

    @Test
    fun getListShortDocOnBuc() {
        val result = bucUtils.findAndFilterDocumentItemByType(SEDType.P2000)
        assertEquals(1, result.size)

        assertEquals(SEDType.P2000.name, result.first().type)
        assertEquals("sent", result.first().status)
        assertEquals("1b934260853d49ec98080da433a6ef91", result.first().id)
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
        assertEquals("P2000", rinaaksjon.dokumentType)
        assertEquals("P_BUC_01", rinaaksjon.id)
        assertEquals("Update", rinaaksjon.navn)

    }

    @Test
    fun getRinaAksjonerFilteredOnP() {
        val result = bucUtils.getRinaAksjon()
        assertEquals(16, result.size)
        val rinaaksjon = result.get(5)
        assertEquals("P2000", rinaaksjon.dokumentType)
        assertEquals("P_BUC_01", rinaaksjon.id)
        assertEquals("Update", rinaaksjon.navn)

        val filterlist = result.filter { it.dokumentType?.startsWith("P")!! }.toList()

        assertEquals(9, filterlist.size)
        val rinaaksjon2 = filterlist.get(5)
        assertEquals("P5000", rinaaksjon2.dokumentType)
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

            if (it.type == "P8000") {
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
        assertEquals("P5000", actualOutput[6])
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
        assertEquals("P6000", actualOutput[7])
    }

    @Test
    fun `getGyldigSedAksjonListAsString  returns 14 sorted elements`(){
        val actualOutput = bucUtils.getSedsThatCanBeCreated()
        assertEquals(14, actualOutput.size)
        assertEquals("P6000", actualOutput[7])
    }

    @Test
    fun `Test liste med SED kun PensjonSED skal returneres`() {
        val list  = listOf("X005","P2000","P4000","H021","X06","P9000", "")

        val result = bucUtils.filterSektorPandRelevantHorizontalSeds(list)

        assertEquals(4, result.size)
        assertEquals("[H021, P2000, P4000, P9000]", result.toString())
    }

    @Test
    fun `Test liste med SED som skal returneres`() {
        val list  = listOf("X005","P2000","P4000","H020","H070", "X06", "H121","P9000", "XYZ")

        val result = bucUtils.filterSektorPandRelevantHorizontalSeds(list)

        assertEquals(6, result.size)
        assertEquals("[H020, H070, H121, P2000, P4000, P9000]", result.toString())
    }


    @Test
    fun `Test av liste med SEDer der kun PensjonSEDer skal returneres`() {
        val list  = listOf("X005","P2000","P4000","H020","X06","P9000", "")

        val result = bucUtils.filterSektorPandRelevantHorizontalSeds(list)

        assertEquals(4, result.size)
        assertEquals("[ \"H020\", \"P2000\", \"P4000\", \"P9000\" ]", mapAnyToJson(result))
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

        val filterParentId = result.stream().filter { it.parentDocumentId != null }.toList()
        assertEquals(3, filterParentId.size)

    }

    @Test
    fun bucsedandviewCheck() {
        val bucjson = getTestJsonFile("buc-285268-answerid.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())

        val bucAndSedView = BucAndSedView.from(buc)

        val seds = bucAndSedView.seds
        val filterParentId = seds?.filter { it.parentDocumentId != null }?.toList()
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

        val exception = mapOf<String, Boolean?>("P7000" to false, "P2200" to true, "P8000" to true, "P9000" to true, "P6000" to true, "H070" to false)
        val result = seds.distinctBy { it.type }.filter { exception.contains(it.type) }.map { it }
        //utvalg av sed som støtter eller ikke støtter vedlegg
        result.forEach {
            assertEquals(exception[it.type!!], it.allowsAttachments)
        }

    }

    @Test
    fun `check that document P12000 on buc support attchemnt`() {
        val bucjson = getTestJsonFile("buc-P_BUC_08-P12000.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucAndSedView = BucAndSedView.from(buc)
        val seds = bucAndSedView.seds

        val single = seds!!.filter { it.type == "P12000" }.map { it }.first()
        assertEquals(false , single.allowsAttachments)

    }

    @Test
    fun `check that document P12000 is allready on buc throw Exception`() {
        val bucjson = getTestJsonFile("buc-P_BUC_08-P12000.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        assertThrows<SedDokumentKanIkkeOpprettesException> {
            bucUtils.checkIfSedCanBeCreated("P120000")
        }
    }

    @Test
    fun `check that document P10000 is allready on buc throw Exception`() {
        val bucjson = getTestJsonFile("buc-P_BUC_06-P6000_Sendt.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        assertThrows<SedDokumentKanIkkeOpprettesException> {
            bucUtils.checkIfSedCanBeCreated("P10000")
        }
    }

    @Test
    fun `check that document P50000 is allready on buc throw Exception`() {
        val bucjson = getTestJsonFile("buc-P_BUC_06-P6000_Sendt.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        assertThrows<SedDokumentKanIkkeOpprettesException> {
            bucUtils.checkIfSedCanBeCreated("P50000")
        }
    }

    @Test
    fun `check that different document on BUC_03 is allowed or not will throw Exception`() {
        val bucjson = getTestJsonFile("buc-279020big.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        val docs = bucUtils.getAllDocuments()
        docs.forEach { doc -> println("type ${doc.type}  status ${doc.status} ") }


        val allowed = listOf("P5000", "P6000", "P8000", "P10000", "H121", "H020")

        allowed.forEach {
            assertEquals(true, bucUtils.checkIfSedCanBeCreated(it) )
        }

        val notAllowd = listOf("P2200", "P4000", "P3000_NO", "P9000")
        notAllowd.forEach {
            assertThrows<SedDokumentKanIkkeOpprettesException> {
                bucUtils.checkIfSedCanBeCreated(it)
            }
        }
    }

    @Test
    fun `check that different document on BUC_01 is allowed or not will throw Exception`() {
        val bucjson = getTestJsonFile("P_BUC_01_4.2_P2000.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        val docs = bucUtils.getAllDocuments()
        docs.forEach { doc -> println("type ${doc.type}  status ${doc.status} ") }


        val notAllowd = listOf("P2000", "P4000", "P3000_NO", "P9000")
        notAllowd.forEach {
            assertThrows<SedDokumentKanIkkeOpprettesException> {
                bucUtils.checkIfSedCanBeCreated(it)
            }
        }
    }

    @Test
    fun `check that different document on other BUC_01 is allowed or not will throw Exception`() {
        val bucjson = getTestJsonFile("buc-22909_v4.1.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        val docs = bucUtils.getAllDocuments()
        docs.forEach { doc -> println("type ${doc.type}  status ${doc.status} ") }


        val allowed = listOf("P4000", "P5000", "P6000", "P8000", "P7000", "P10000", "H120", "H020")

        allowed.forEach {
            assertEquals(true, bucUtils.checkIfSedCanBeCreated(it) )
        }

        val notAllowd = listOf("P2000")
        notAllowd.forEach {
            assertThrows<SedDokumentKanIkkeOpprettesException> {
                bucUtils.checkIfSedCanBeCreated(it)
            }
        }
    }

}
