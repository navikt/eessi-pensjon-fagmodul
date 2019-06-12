package no.nav.eessi.eessifagmodul.services.eux

import com.google.common.collect.Lists
import no.nav.eessi.eessifagmodul.models.IkkeGyldigKallException
import no.nav.eessi.eessifagmodul.models.InstitusjonItem
import no.nav.eessi.eessifagmodul.models.SEDType
import no.nav.eessi.eessifagmodul.services.eux.bucmodel.Buc
import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import no.nav.eessi.eessifagmodul.utils.mapJsonToAny
import no.nav.eessi.eessifagmodul.utils.typeRefs
import no.nav.eessi.eessifagmodul.utils.validateJson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner::class)
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

    @Before
    fun bringItOn() {
        bucjson = getTestJsonFile("buc-22909_v4.1.json")
        buc = mapJsonToAny(bucjson, typeRefs<Buc>())
        bucUtils = BucUtils(buc)

    }

    @Test
    fun generteShortBuc() {
        val bucutil = BucUtils(bucjson)
        val result = bucutil.getSubject()
        assertEquals("øjøløjøjø", result.name)
        assertEquals("06018120915", result.pid)
    }

    @Test
    fun getListofSbdh() {
        val result = bucUtils.getSbdh()
        val resjson = mapAnyToJson(result)
        println(resjson)
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
        println(mapAnyToJson(result!!))
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
                println("\tattachments:\t${it.attachments?.size}")
                assertEquals("2019-05-20", it.lastUpdate.toString())
                assertEquals(2, it.attachments?.size)

            } else {
                println("\tattachments:\t0")
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

        assertEquals(2, parts?.size)
    }

    @Test
    fun getParticipantsTestOnMock() {
        val parts = bucUtils.getParticipants()
        assertEquals(2, parts?.size)
    }

    @Test(expected = IkkeGyldigKallException::class)
    fun `matchParticipantsToInstitusjonItem | listene er tom forventer exception`(){
        val buclist = listOf<InstitusjonItem>()
        val list = listOf<InstitusjonItem>()
        bucUtils.matchParticipantsToInstitusjonItem(buclist, list)
    }

    @Test
    fun `matchParticipantsToInstitusjonItem | bucDeltaker tom og list har data forventer 2 size`(){
        val buclist = listOf<InstitusjonItem>()
        val list = listOf( InstitusjonItem(country = "DK", institution = "DK006"), InstitusjonItem(country = "PL", institution = "PolishAcc"))

        val result = bucUtils.matchParticipantsToInstitusjonItem(buclist, list)

        assertEquals(2, result.size)
    }

    @Test
    fun `matchParticipantsToInstitusjonItem | list er lik forventer 0 size`(){
        val buclist = listOf( InstitusjonItem(country = "DK", institution = "DK006"), InstitusjonItem(country = "PL", institution = "PolishAcc"))
        val list = listOf(InstitusjonItem(country = "PL", institution = "PolishAcc"), InstitusjonItem(country = "DK", institution = "DK006"))

        val result = bucUtils.matchParticipantsToInstitusjonItem(buclist, list)
        assertEquals(0, result.size)
    }

    @Test
    fun `matchParticipantsToInstitusjonItem | buclist er 2 mens list er 3 forventer 1 size`(){
        val buclist = listOf( InstitusjonItem(country = "DK", institution = "DK006"), InstitusjonItem(country = "PL", institution = "PolishAcc"))
        val list = listOf(InstitusjonItem(country = "PL", institution = "PolishAcc"), InstitusjonItem(country = "DK", institution = "DK006"), InstitusjonItem(country = "FI", institution = "FINLAND"))

        val result = bucUtils.matchParticipantsToInstitusjonItem(buclist, list)
        assertEquals(1, result.size)
    }

    @Test
    fun `matchParticipantsToInstitusjonItem | buclist er 5 og list er 0 forventer 0 size`(){
        val buclist = listOf<InstitusjonItem>( InstitusjonItem(country = "DK", institution = "DK006"), InstitusjonItem(country = "PL", institution = "PolishAcc"),InstitusjonItem(country = "PL", institution = "PolishAcc"), InstitusjonItem(country = "DK", institution = "DK006"), InstitusjonItem(country = "FI", institution = "FINLAND"))
        val list = listOf<InstitusjonItem>()

        val result = bucUtils.matchParticipantsToInstitusjonItem(buclist, list)
        assertEquals(0, result.size)
    }



}