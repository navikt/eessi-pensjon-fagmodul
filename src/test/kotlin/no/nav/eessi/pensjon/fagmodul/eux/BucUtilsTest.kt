package no.nav.eessi.pensjon.fagmodul.eux

import com.google.common.collect.Lists
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Organisation
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import no.nav.eessi.pensjon.utils.validateJson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner::class)
class BucUtilsTest {

    private val logger: Logger by lazy { LoggerFactory.getLogger(BucUtilsTest::class.java) }

    lateinit var bucUtils: BucUtils
    lateinit var bucjson: String
    lateinit var buc: Buc

    fun getTestJsonFile(filename: String): String {
        val filepath = "src/test/resources/json/buc/${filename}"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        assertTrue(validateJson(json))
        return json
    }

    @Before
    fun bringItOn() {
        bucjson = getTestJsonFile("buc-22909_v4.1.json")
        buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        bucUtils = BucUtils(buc)

    }

    @Test
    fun getListofSbdh() {
        val result = bucUtils.getSbdh()
        val resjson = mapAnyToJson(result)
        logger.info(resjson)
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
        val json = mapAnyToJson(result)
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
    fun getActions() {
        val result = bucUtils.getBucAction()
        assertEquals(18, result?.size)
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

        var counter = 1
        bucUtils.getAllDocuments().forEach {

            print("Nr:\t${counter++}\ttype:\t${it.type}\t")

            if (it.type == "P8000") {
                logger.info("\tattachments:\t${it.attachments?.size}")
                assertEquals("1557825747269", it.creationDate.toString())
                assertEquals("1558362934400", it.lastUpdate.toString())
                assertEquals(2, it.attachments?.size)

            } else {
                logger.info("\tattachments:\t0")
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
    fun `getAksjonListAsString | returns sorted list ok`(){
        val actualOutput = bucUtils.getAksjonListAsString()

        assertEquals(14, actualOutput.size)
        assertEquals("P5000", actualOutput[6])

    }

    @Test
    fun `getAksjonListAsString | returns sorted of one element ok`(){
        //mocking data
        val actionitems = buc.actions
        actionitems?.forEach {
            it.name = "Update"
        }
        actionitems?.get(0)?.name = "Create"

        //run impl. for test
        val actualOutput = bucUtils.getAksjonListAsString()
        assertEquals(1, actualOutput.size)
    }

    @Test
    fun `getAksjonListAsString | returns no element`(){
        //mocking data
        val actionitems = buc.actions
        actionitems?.forEach {
            it.name = "Update"
        }

        //run impl. for test
        val actualOutput = bucUtils.getAksjonListAsString()
        assertEquals(0, actualOutput.size)
    }

    @Test
    fun `getAksjonListAsString | returns 16 sorted elements`(){
        //mocking data
        val actionitems = buc.actions
        actionitems?.forEach {
            it.name = "Create"
        }

        //run impl. for test
        val actualOutput = bucUtils.getAksjonListAsString()
        assertEquals(16, actualOutput.size)
        assertEquals("P6000", actualOutput[9])

    }


    @Test(expected = ManglerDeltakereException::class)
    fun `findNewParticipants | listene er tom forventer exception`(){
        val bucUtils = BucUtils(Buc(participants = listOf()))
        val candidates = listOf<InstitusjonItem>()
        bucUtils.findNewParticipants(candidates)
    }

    @Test
    fun `findNewParticipants | bucDeltaker tom og list har data forventer 2 size`(){
        val bucUtils = BucUtils(Buc(participants = listOf()))
        val candidates = listOf(
                InstitusjonItem(country = "DK", institution = "DK006"),
                InstitusjonItem(country = "PL", institution = "PolishAcc"))
        assertEquals(2, bucUtils.findNewParticipants(candidates).size)
    }

    @Test
    fun `findNewParticipants | list er lik forventer 0 size`(){
        val bucUtils = BucUtils(Buc(participants = listOf(
                ParticipantsItem(organisation = Organisation(countryCode = "DK", id = "DK006")),
                ParticipantsItem(organisation = Organisation(countryCode = "PL", id = "PolishAcc")))))

        val candidates = listOf(
                InstitusjonItem(country = "PL", institution = "PolishAcc"),
                InstitusjonItem(country = "DK", institution = "DK006"))

        assertEquals(0, bucUtils.findNewParticipants(candidates).size)
    }

    @Test
    fun `findNewParticipants | buclist er 2 mens list er 3 forventer 1 size`(){
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
    fun `findNewParticipants | buclist er 5 og list er 0 forventer 0 size`(){
        val bucUtils = BucUtils(Buc(participants = listOf(
                ParticipantsItem(organisation = Organisation(countryCode = "DK", id = "DK006")),
                ParticipantsItem(organisation = Organisation(countryCode = "PL", id = "PolishAcc")),
                ParticipantsItem(organisation = Organisation(countryCode = "PL", id = "PolishAcc")),
                ParticipantsItem(organisation = Organisation(countryCode = "DK", id = "DK006")),
                ParticipantsItem(organisation = Organisation(countryCode = "FI", id = "FINLAND")))))

        val candidates = listOf<InstitusjonItem>()

        assertEquals(0, bucUtils.findNewParticipants(candidates).size)
    }
}