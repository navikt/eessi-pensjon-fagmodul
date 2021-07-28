package no.nav.eessi.pensjon.fagmodul.eux

import com.fasterxml.jackson.annotation.JsonFormat
import no.nav.eessi.pensjon.eux.model.document.P6000Dokument
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.eux.model.sed.SedType.P5000
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.DocumentsItem
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Organisation
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.eessi.pensjon.utils.typeRefs
import no.nav.eessi.pensjon.utils.validateJson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*


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
    fun getCreator() {
        val result = buc.creator
        assertEquals("NAVT003", result?.organisation?.name)
        assertEquals("NO:NAVT003", result?.organisation?.id)
        assertEquals("NO", result?.organisation?.countryCode)
    }

    @Test
    fun findFirstDocumentItemByType() {
        val result = bucUtils.findFirstDocumentItemByType(SedType.P2000)
        assertEquals(SedType.P2000, result?.type)
        assertEquals("sent", result?.status)
        assertEquals("1b934260853d49ec98080da433a6ef91", result?.id)

        val result2 = bucUtils.findFirstDocumentItemByType(SedType.P6000)
        assertEquals(SedType.P6000, result2?.type)
        assertEquals("empty", result2?.status)
        assertEquals("85db6f21f01541899cc80ffc80dff88b", result2?.id)

    }

    @Test
    fun `sjekk for om parentId finnes alt med valgt SedType`() {
        val SedType = SedType.P9000
        val parentId = "a89676b0ea7c4d8684e17f15d2471188"

        val bucjson = getTestJsonFile("buc-285268-answerid.json")
        val bucUtil = BucUtils(mapJsonToAny(bucjson, typeRefs()))

        assertTrue(bucUtil.sjekkOmSvarSedKanOpprettes(SedType, parentId))

        val parentIdStatusSendt = "fd9fd9ee97ee46d0a3f5c58d1b245268"

        assertThrows<SedDokumentKanIkkeOpprettesException> {
            bucUtil.sjekkOmSvarSedKanOpprettes(SedType, parentIdStatusSendt)
        }
    }

    @Test
    fun getProcessDefinitionName() {
        val result = bucUtils.getProcessDefinitionName()
        assertEquals("P_BUC_01", result)
    }

    @Test
    fun getAllP6000AsDocumentItemTest() {
        val bucfile = getTestJsonFile("buc-2019-p6000.json")
        val bucn : Buc = mapJsonToAny(bucfile, typeRefs())
        val bucutils = BucUtils(bucn)

        val expected = listOf(
            P6000Dokument(
                type = SedType.P6000,
                bucid = "1362305",
                documentID = "a550606682f24785adf0919a1147f7f2",
                fraLand = "NO",
                sisteVersjon = "1",
                pdfUrl = "https://pdfurl/buc/1362305/sed/a550606682f24785adf0919a1147f7f2/pdf",
                sistMottatt = LocalDate.of(2021, 7,21)))

        val result = bucutils.getAllP6000AsDocumentItem("https://pdfurl")

        assertEquals(expected.first(), result.first())

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
        val name = buc.processDefinitionName
        assertEquals("P_BUC_01", name)
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
    fun `getEndDateLong parses dates correctly2`() {
        val listOfArgs = listOf(Pair(1567150657318L, 1567150657318L),
            Pair(1567154257318L, 1567154257318L),
            Pair(1567154257318L, "2019-08-30T10:37:37.318"),
                    Pair(1567154257318L, "2019-08-30T09:37:37.318+0100")
        )

        listOfArgs.forEach { assertEquals(it.first, BucUtils(Buc(lastUpdate= it.second) ).getLastDateLong()) }
    }




    @Test
    fun getRinaAksjoner() {
        val result = bucUtils.getRinaAksjon()
        assertEquals(16, result.size)
        val rinaaksjon = result[5]
        assertEquals(SedType.P2000, rinaaksjon.dokumentType)
        assertEquals("P_BUC_01", rinaaksjon.id)
        assertEquals("Update", rinaaksjon.navn)
    }

    @Test
    fun getRinaAksjonerFilteredOnP() {
        val result = bucUtils.getRinaAksjon()
        assertEquals(16, result.size)
        val rinaaksjon = result[5]
        assertEquals(SedType.P2000, rinaaksjon.dokumentType)
        assertEquals("P_BUC_01", rinaaksjon.id)
        assertEquals("Update", rinaaksjon.navn)

        val filterlist = result.filter { it.dokumentType?.name?.startsWith("P")!! }

        assertEquals(9, filterlist.size)
        val rinaaksjon2 = filterlist[5]
        assertEquals(P5000, rinaaksjon2.dokumentType)
        assertEquals("P_BUC_01", rinaaksjon2.id)
        assertEquals("Create", rinaaksjon2.navn)
    }

    @Test
    fun getBucAndDocumentsWithAttachment() {
        bucjson = getTestJsonFile("buc-158123_2_v4.1.json")
        buc = mapJsonToAny(bucjson, typeRefs())
        bucUtils = BucUtils(buc)

        assertEquals(2, buc.attachments?.size)

        assertEquals(18, bucUtils.getAllDocuments().size)

        bucUtils.getAllDocuments().forEach {

            if (it.type == SedType.P8000) {
                assertEquals("1557825747269", it.creationDate.toString())
                assertEquals("1558362934400", it.lastUpdate.toString())
                assertEquals(2, it.attachments?.size)

            }
        }
    }

    @Test
    fun getParticipantsTestOnMock_2() {
        bucjson = getTestJsonFile("buc-158123_2_v4.1.json")
        buc = mapJsonToAny(bucjson, typeRefs())
        bucUtils = BucUtils(buc)
        assertEquals(2, buc.attachments?.size)
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
        assertEquals(P5000, actualOutput[6])
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
        assertEquals(SedType.P6000, actualOutput[7])
    }

    @Test
    fun `getGyldigSedAksjonListAsString  returns 14 sorted elements`(){
        val actualOutput = bucUtils.getSedsThatCanBeCreated()
        assertEquals(14, actualOutput.size)
        assertEquals(SedType.P6000, actualOutput[7])
    }

    @Test
    fun `Test liste med SED kun PensjonSED skal returneres`() {
        val list = listOf(SedType.X005, SedType.P2000, SedType.P4000, SedType.H021, SedType.P9000)

        val result = bucUtils.filterSektorPandRelevantHorizontalSeds(list)
        assertEquals(4, result.size)

        val expected = listOf(SedType.H021, SedType.P2000, SedType.P4000, SedType.P9000)
        assertEquals(expected, result)
    }

    @Test
    fun `Test liste med SED som skal returneres`() {
        val list =
            listOf(SedType.X005, SedType.P2000, SedType.P4000, SedType.H020, SedType.H070, SedType.H121, SedType.P9000)

        val result = bucUtils.filterSektorPandRelevantHorizontalSeds(list)
        assertEquals(6, result.size)

        val expected = listOf(SedType.H020, SedType.H070, SedType.H121, SedType.P2000, SedType.P4000, SedType.P9000)
        assertEquals(expected, result)
    }


    @Test
    fun `Test av liste med SEDer der kun PensjonSEDer skal returneres`() {
        val list = listOf(SedType.X005, SedType.P2000, SedType.P4000, SedType.H020, SedType.P9000)

        val result = bucUtils.filterSektorPandRelevantHorizontalSeds(list)
        assertEquals(4, result.size)

        val expected = listOf(SedType.H020, SedType.P2000, SedType.P4000, SedType.P9000)
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
    fun `sjekk deltakere mot buc og om den er fjernet i x007`() {
        val bucjson = getTestJsonFile("buc-4929378.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        val list = listOf(InstitusjonItem("FI", "FI:0200000010", ""), InstitusjonItem("FI", "FI:0200000046", ""))
        assertThrows<ResponseStatusException> {
            bucUtils.checkForParticipantsNoLongerActiveFromXSEDAsInstitusjonItem(list)
        }

        val result = bucUtils.checkForParticipantsNoLongerActiveFromXSEDAsInstitusjonItem(listOf(InstitusjonItem("FI", "FI:0200000046", "")))
        assertEquals(true, result)

    }

    @Test
    fun `sjekk for om x100 inneholder avsender ikke lenger i bruk`() {
        val bucjson = getTestJsonFile("buc-3059699-x100.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        val lists = listOf(InstitusjonItem("FI", "FI:0200000046", ""), InstitusjonItem("DE", "DE:DRV66001", "German Federal Pension"))
        assertThrows<ResponseStatusException> {
            bucUtils.checkForParticipantsNoLongerActiveFromXSEDAsInstitusjonItem(lists)
        }

        val result = bucUtils.checkForParticipantsNoLongerActiveFromXSEDAsInstitusjonItem(listOf(InstitusjonItem("FI", "FI:0200000046", "")))
        assertEquals(true, result)


    }


    @Test
    fun findNewParticipantsMockwithExternalCaseOwnerResultExpectedToBeZero(){
        val bucjson = getTestJsonFile("buc-254740_v4.1.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        assertEquals(3, bucUtils.getParticipants().size)

        val candidates = listOf(InstitusjonItem(country = "NO", institution = "NO:NAVT003", name = "NAV T003"))

        assertEquals(0, bucUtils.findNewParticipants(candidates).size)
    }

    @Test
    fun findNewParticipantsMockwithExternalCaseOwnerResultExpectedOne(){
        val bucjson = getTestJsonFile("buc-254740_v4.1.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        assertEquals(3, bucUtils.getParticipants().size)

        val candidates = listOf(InstitusjonItem(country = "NO", institution = "NO:NAVT007", name = "NAV T007"))

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

        val exception = mapOf<SedType, Boolean?>(SedType.P7000 to false, SedType.P2200 to true, SedType.P8000 to true, SedType.P9000 to true, SedType.P6000 to true, SedType.H070 to false)
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

        val single = seds!!.filter { it.type == SedType.P12000 }.map { it }.first()
        assertEquals(false , single.allowsAttachments)

    }

    @Test
    fun `check that document P12000 is allready on buc throw Exception`() {
        val bucjson = getTestJsonFile("buc-P_BUC_08-P12000.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        assertThrows<SedDokumentKanIkkeOpprettesException> {
            bucUtils.checkIfSedCanBeCreated(SedType.P12000, "123333")
        }
    }

    @Test
    fun `check that document P10000 is allready on buc throw Exception`() {
        val bucjson = getTestJsonFile("buc-P_BUC_06-P6000_Sendt.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        assertThrows<SedDokumentKanIkkeOpprettesException> {
            bucUtils.checkIfSedCanBeCreated(SedType.P10000, "123333")
        }
    }

    @Test
    fun `check that document P50000 is allready on buc throw Exception`() {
        val bucjson = getTestJsonFile("buc-P_BUC_06-P6000_Sendt.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        assertThrows<SedDokumentKanIkkeOpprettesException> {
            bucUtils.checkIfSedCanBeCreated(P5000, "123333")
        }
    }

    @Test
    fun `check that different document on BUC_03 is allowed or not will throw Exception`() {
        val bucjson = getTestJsonFile("buc-279020big.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        val bucUtils = BucUtils(buc)

        val allowed = listOf(P5000, SedType.P6000, SedType.P8000, SedType.P10000, SedType.H121, SedType.H020)

        allowed.forEach {
            assertEquals(true, bucUtils.checkIfSedCanBeCreated(it, "123333") )
        }

        val notAllowd = listOf(SedType.P2200, SedType.P4000, SedType.P3000_NO, SedType.P9000)
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

        val notAllowd = listOf(SedType.P2000, SedType.P4000, SedType.P3000_NO, SedType.P9000)
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

        val allowed = listOf(SedType.P4000, P5000, SedType.P6000, SedType.P8000, SedType.P7000, SedType.P10000, SedType.H120, SedType.H020)

        allowed.forEach {
            assertEquals(true, bucUtils.checkIfSedCanBeCreated(it, "123333") )
        }

        val notAllowd = listOf(SedType.P2000)
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
    fun validateOneRina2020Buc() {


        val bucjson = getTestJsonFile( "buc-id-rina2020new.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())

        val view = BucAndSedView.from(buc)

        println(view.toJsonSkipEmpty())

    }

    @Test
    fun `sjekk for documentlist contains dummyparts on pbuc06 buc rina2020`() {
        val bucjson = getTestJsonFile("buc-id-rina2020new.json")
        val buc = mapJsonToAny(bucjson, typeRefs<Buc>())

        val bucUtils = BucUtils(buc)

        val result = bucUtils.getAllDocuments()

        assertEquals(SedType.DummyChooseParts, result.first().type)

        val seds = bucUtils.getFiltrerteGyldigSedAksjonListAsString()
        val validsed = listOf<SedType>(P5000, SedType.P6000, SedType.P7000, SedType.P10000)

        assertEquals(seds.toString(), validsed.toString())

        assertThat(seds.containsAll(validsed) )

        assertEquals(true, bucUtils.isNewRina2020Buc())
    }

    @Test
    fun `sjekk for documentlist contains p2000 on pbuc01 buc rina2020`() {
        val buc = Buc(processDefinitionName = "P_BUC_01", processDefinitionVersion = "4.2")
        val bucUtils = BucUtils(buc)
        val result = bucUtils.getAllDocuments()

        assertEquals(SedType.P2000, result.first().type)
        val seds = bucUtils.getFiltrerteGyldigSedAksjonListAsString()
        assertEquals(listOf(SedType.P2000).toString(), seds.toString())
    }

    @Test
    fun mapDatoFraJsonTilKotlin() {
//        val json = """
//                {
//                  "creationDate": "2021-07-21T11:30:16.000+0000",
//                  "lastUpdate": "2021-07-21T11:30:16.000+0000"
//                }
//        """.trimIndent()
////                                "2016-12-18@07:53:34.740+0000"
//
//        val result: TestDates = mapJsonToAny(json, typeRefs() )
//
//        println(result)
//        println(result?.creationDate?.javaClass)
//
////       val milliSeconds = "1596751200000".toLong()


        println(fromMils("1567154257318".toLong()))
        println(fromMils("1567161457318".toLong()))
        println(fromMils("1567150657318".toLong()))


    }
    private fun fromMils(milliSeconds: Long): LocalDateTime {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = milliSeconds
        return LocalDateTime.ofInstant(calendar.toInstant(), calendar.timeZone.toZoneId())
    }


    data class TestDates(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", locale = "no_NO")
        val creationDate: LocalDateTime,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", locale = "no_NO")
        val lastUpdate: LocalDateTime
    )

}
