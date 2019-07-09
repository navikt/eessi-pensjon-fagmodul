package no.nav.eessi.pensjon.fagmodul.prefill

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.whenever
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.models.SED
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.krav.KravDataFromPEN
import no.nav.eessi.pensjon.fagmodul.prefill.nav.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.nav.PrefillPersonDataFromTPS
import no.nav.eessi.pensjon.fagmodul.prefill.person.PersonDataFromTPS
import no.nav.eessi.pensjon.fagmodul.prefill.person.PersonDataFromTPS.Companion.generateRandomFnr
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonService
import no.nav.eessi.pensjon.services.pensjonsinformasjon.RequestBuilder
import no.nav.eessi.pensjon.services.personv3.PersonV3Service
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.util.ResourceUtils
import org.springframework.web.client.RestTemplate

@RunWith(SpringRunner::class)
@ActiveProfiles("test")
@SpringBootTest
abstract class AbstractPrefillIntegrationTestHelper {

    var personFnr: String = ""

    @Mock
    protected lateinit var pensjonsinformasjonRestTemplate: RestTemplate

    @Mock
    protected lateinit var mockPersonV3Service: PersonV3Service

    protected lateinit var prefillData: PrefillDataModel

    protected lateinit var pendata: Pensjonsinformasjon

    protected lateinit var kravdata: KravDataFromPEN

    private lateinit var pensionDataFromPEN: PensjonsinformasjonHjelper

    @Autowired
    protected lateinit var eessiInformasjon: EessiInformasjon

    private lateinit var prefillNav: PrefillNav

    private lateinit var personTPS: PrefillPersonDataFromTPS

    protected lateinit var prefill: Prefill<SED>

    @Before
    fun onStart() {
        setFakePersonFnr(createFakePersonFnr())
        val mockPair = mockPesysTestfilepath()

        //mock prefillDataModel
        val sedId = mockPair.first
        SEDType.valueOf(sedId)
        val mockKravXMLfil = mockPair.second

        prefillData = generatePrefillData(sedId, "02345678901", sakId = createSaksnummer())

        createPayload(prefillData)

        //mock TPS data
        personTPS = initMockPrefillPersonDataFromTPS()
        //mock prefillNav data
        prefillNav = PrefillNav(personTPS)
        prefillNav.institutionid = "NO:noinst002"
        prefillNav.institutionnavn = "NOINST002, NO INST002, NO"

        //mock pensjonData
        pensionDataFromPEN = mockPrefillPensionDataFromPEN(mockKravXMLfil)

        //mock kravData
        kravdata = mockKravDataFromPEN(pensionDataFromPEN)

        //mock PrefillP2x00 class
        prefill = createTestClass(prefillNav, personTPS, pensionDataFromPEN)
    }

    //genererer et tilfeldig falsk personnr for person under kjøring av test
    abstract fun createFakePersonFnr(): String

    private fun setFakePersonFnr(fnr: String) {
        personFnr = fnr
    }

    //henter et tilfeldig generert personnr.
    fun getFakePersonFnr(): String {
        return personFnr
    }

    //pesys saksnummer
    abstract fun createSaksnummer(): String

    //mock pesys pensjoninformasjon datafil i xml format
    abstract fun mockPesysTestfilepath(): Pair<String, String>

    //mock prefill SED class
    abstract fun createTestClass(prefillNav: PrefillNav, personTPS: PrefillPersonDataFromTPS, pensionDataFromPEN: PensjonsinformasjonHjelper): Prefill<SED>

    //mock payloiad from api
    abstract fun createPayload(prefillData: PrefillDataModel)

    //mock person informastion payload
    abstract fun createPersonInfoPayLoad(): String

    //mock person trygdetid utland opphold (p4000) payload
    abstract fun createPersonTrygdetidHistorikk(): String

    //mock datafromtps..
    open class DataFromTPS(mocktps: Set<MockTPS>, eessiInformasjon: EessiInformasjon) : PersonDataFromTPS(mocktps, eessiInformasjon)

    //metod person tps to override default..
    abstract fun opprettMockPersonDataTPS(): Set<PersonDataFromTPS.MockTPS>?

    //mock person tps default.. enke with 1chold u 18y
    //alle person mock er lik siiden de hentes fra disse 3 datafilene.
    private fun initMockPersonDataTPS(): Set<PersonDataFromTPS.MockTPS> {
        return setOf(
                PersonDataFromTPS.MockTPS("Person-20000.json", generateRandomFnr(67), PersonDataFromTPS.MockTPS.TPSType.PERSON),
                PersonDataFromTPS.MockTPS("Person-21000.json", generateRandomFnr(43), PersonDataFromTPS.MockTPS.TPSType.BARN),
                PersonDataFromTPS.MockTPS("Person-22000.json", generateRandomFnr(17), PersonDataFromTPS.MockTPS.TPSType.BARN)
        )
    }

    //alle tester med aamme personlist for tiden. MOCK TPS
    private fun initMockPrefillPersonDataFromTPS(): PrefillPersonDataFromTPS {
        //løsning for å laste in abstract mockTPStestklasse
        val mockDataSet = opprettMockPersonDataTPS() ?: initMockPersonDataTPS()

        val datatps = DataFromTPS(mockDataSet, eessiInformasjon)
        datatps.mockPersonV3Service = mockPersonV3Service
        return datatps.mockPrefillPersonDataFromTPS()
    }


    private fun readXMLresponse(file: String): ResponseEntity<String> {
        val resource = ResourceUtils.getFile("classpath:pensjonsinformasjon/krav/$file").readText()
        return ResponseEntity(resource, HttpStatus.OK)
    }

    protected fun readJsonResponse(file: String): String {
        return ResourceUtils.getFile("classpath:json/nav/$file").readText()
    }

    private fun mockPrefillPensionDataFromPEN(responseXMLfilename: String): PensjonsinformasjonHjelper {
        whenever(pensjonsinformasjonRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))).thenReturn(readXMLresponse(responseXMLfilename))

        val pensjonsinformasjonService1 = PensjonsinformasjonService(pensjonsinformasjonRestTemplate, RequestBuilder())

        return PensjonsinformasjonHjelper(pensjonsinformasjonService1, eessiInformasjon)
    }

    private fun mockKravDataFromPEN(prefillPensionDataFromPEN: PensjonsinformasjonHjelper): KravDataFromPEN {
        return KravDataFromPEN(prefillPensionDataFromPEN)
    }


    private fun generatePrefillData(sedId: String, fnr: String? = null, subtractYear: Int? = null, sakId: String? = null): PrefillDataModel {
        val items = listOf(InstitusjonItem(country = "NO", institution = "DUMMY"))

        val year = subtractYear ?: 68

        return PrefillDataModel().apply {
            rinaSubject = "Pensjon"
            sed = SED.create(sedId)
            penSaksnummer = sakId ?: "12345678"
            vedtakId = "12312312"
            buc = "P_BUC_99"
            aktoerID = "123456789"
            personNr = fnr ?: generateRandomFnr(year)
            institution = items
        }
    }


}