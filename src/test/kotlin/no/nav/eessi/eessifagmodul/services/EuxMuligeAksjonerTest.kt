package no.nav.eessi.eessifagmodul.services

import com.nhaarman.mockito_kotlin.whenever
import no.nav.eessi.eessifagmodul.models.RINAaksjoner
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals

@RunWith(MockitoJUnitRunner::class)
class EuxMuligeAksjonerTest {

    private val logger: Logger by lazy { LoggerFactory.getLogger(EuxMuligeAksjonerTest::class.java) }


    @Mock
    private lateinit var mockEuxService: EuxService

    private lateinit var muligeAksjoner: EuxMuligeAksjoner

    @Before
    fun setup() {
        logger.debug("Starting tests.... ...")
        muligeAksjoner = EuxMuligeAksjoner(mockEuxService)
    }

    private fun mockNotValidData(): List<RINAaksjoner> {
        val aksjonlist = listOf(
                RINAaksjoner(
                    navn = "Read",
                    id = "123123123123",
                    kategori = "Documents",
                    dokumentType = "P2000",
                    dokumentId = "23123123"
                ),
                RINAaksjoner(
                    navn = "Send",
                    id = "123123123123",
                    kategori = "Documents",
                    dokumentType = "P2000",
                    dokumentId = "23123123"
                ),
                RINAaksjoner(
                    navn = "Delete",
                    id = "123123123123",
                    kategori = "Documents",
                    dokumentType = "P2000",
                    dokumentId = "23123123"
                )
        )
        return aksjonlist
    }

    private fun mockValidData(navn: String): List<RINAaksjoner> {
        val aksjonlist: MutableList<RINAaksjoner> = mutableListOf()
        aksjonlist.addAll(mockNotValidData())
        aksjonlist.add(
                RINAaksjoner(
                        navn = navn,
                        id = "123123123123",
                        kategori = "Documents",
                        dokumentType = "P2000",
                        dokumentId = "23123123"
                )
        )
        return aksjonlist
    }


    @Test
    fun `check confirmUpdate muligeaksjoner found at end`() {
        val response = mockNotValidData()
        val finalResponse = mockValidData("Update")

        whenever(mockEuxService.getMuligeAksjoner (ArgumentMatchers.anyString()))
                .thenReturn(response)
                .thenReturn(response)
                .thenReturn(response)
                .thenReturn(finalResponse)

        val result = muligeAksjoner.confirmUpdate("P2000", "92223424234")
        assertEquals(true, result)
    }

    @Test
    fun `check confirmUpdate muligeaksjoner ikke funnet`() {
        val response = mockNotValidData()
        whenever(mockEuxService.getMuligeAksjoner (ArgumentMatchers.anyString()))
                .thenReturn(response)

        val result = muligeAksjoner.confirmUpdate("P2000", "92223424234")
        assertEquals(false, result)
    }

    @Test
    fun `check confirmUpdate muligeaksjoner valid med en gang`() {
        val finalResponse = mockValidData("Update")

        whenever(mockEuxService.getMuligeAksjoner (ArgumentMatchers.anyString()))
                .thenReturn(finalResponse)

        val result = muligeAksjoner.confirmUpdate("P2000", "92223424234")
        assertEquals(true, result)
    }

    @Test
    fun `check confirmCreate muligeaksjoner kan ikke create`() {
        val response = mockNotValidData()

        whenever(mockEuxService.getMuligeAksjoner (ArgumentMatchers.anyString()))
                .thenReturn(response)
        val result = muligeAksjoner.confirmCreate("P2000", "92223424234")
        assertEquals(false, result)
    }

    @Test
    fun `check confirmCreate muligeaksjoner kan create`() {
        val response = mockValidData("Create")

        whenever(mockEuxService.getMuligeAksjoner (ArgumentMatchers.anyString()))
                .thenReturn(response)
        val result = muligeAksjoner.confirmCreate("P2000", "92223424234")
        assertEquals(true, result)
    }




}