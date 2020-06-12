package no.nav.eessi.pensjon.fagmodul.api

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.fagmodul.eux.BucAndSedView
import no.nav.eessi.pensjon.fagmodul.eux.EuxService
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Buc
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.Organisation
import no.nav.eessi.pensjon.fagmodul.eux.bucmodel.ParticipantsItem
import no.nav.eessi.pensjon.logging.AuditLogger
import no.nav.eessi.pensjon.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.typeRefs
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(MockitoExtension::class)
class BucControllerTest {

    @Spy
    lateinit var auditLogger: AuditLogger

    @Spy
    lateinit var mockEuxService: EuxService

    @Mock
    lateinit var mockAktoerIdHelper: AktoerregisterService

    private lateinit var bucController: BucController

    @BeforeEach
    fun before() {
        this.bucController = BucController(mockEuxService, mockAktoerIdHelper, auditLogger)
    }


    @Test
    fun `gets valid bucs fagmodul can handle excpect list`() {
        val result = bucController.getBucs()
        Assertions.assertEquals(10, result.size)
    }

    @Test
    fun `get valud buc json and convert to object ok`() {
        val gyldigBuc = String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-279020big.json")))
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())

        doReturn(buc).whenever(mockEuxService).getBuc(any())

        val result = bucController.getBuc("1213123123")
        Assertions.assertEquals(buc, result)
    }

    @Test
    fun getProcessDefinitionName() {
        val gyldigBuc = String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-279020big.json")))
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())
        doReturn(buc).whenever(mockEuxService).getBuc(any())

        val result = bucController.getProcessDefinitionName("1213123123")
        Assertions.assertEquals("P_BUC_03", result)
    }

    @Test
    fun getCreator() {
        val gyldigBuc = String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-279020big.json")))
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())
        doReturn(buc).whenever(mockEuxService).getBuc(any())

        val result = bucController.getCreator("1213123123")
        Assertions.assertEquals("Z990787", result?.name)
    }

    @Test
    fun getBucDeltakere() {
        val expected = listOf(ParticipantsItem("asdas", Organisation(), false))
        doReturn(expected).whenever(mockEuxService).getBucDeltakere(any())

        val result = bucController.getBucDeltakere("1213123123")
        Assertions.assertEquals(expected.toJson(), result)
    }

    @Test
    fun `gitt at det finnes en gydlig euxCaseid og Buc skal det returneres en liste over sedid`() {
        val gyldigBuc = String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-279020big.json")))

        val mockEuxRinaid = "123456"
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())

        doReturn(buc).whenever(mockEuxService).getBuc(any())

        val actual = bucController.getAllDocuments(mockEuxRinaid)

        Assertions.assertNotNull(actual)
        Assertions.assertEquals(25, actual.size)
    }

    @Test
    fun `createBuc run ok and return id`() {
        val gyldigBuc = String(Files.readAllBytes(Paths.get("src/test/resources/json/buc/buc-279020big.json")))
        val buc : Buc =  mapJsonToAny(gyldigBuc, typeRefs())

        doReturn("1231231").whenever(mockEuxService).createBuc("P_BUC_03")
        doReturn(buc).whenever(mockEuxService).getBuc(any())

        val excpeted = BucAndSedView.from(buc)
        val actual = bucController.createBuc("P_BUC_03")

        Assertions.assertEquals(excpeted.toJson(), actual.toJson())
    }

}