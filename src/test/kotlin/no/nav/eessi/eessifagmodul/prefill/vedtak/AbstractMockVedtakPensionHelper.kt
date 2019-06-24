package no.nav.eessi.eessifagmodul.prefill.vedtak

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.whenever
import no.nav.eessi.eessifagmodul.models.InstitusjonItem
import no.nav.eessi.eessifagmodul.models.Pensjon
import no.nav.eessi.eessifagmodul.models.SED
import no.nav.eessi.eessifagmodul.prefill.EessiInformasjon
import no.nav.eessi.eessifagmodul.prefill.PensjonsinformasjonHjelper
import no.nav.eessi.eessifagmodul.prefill.PrefillDataModel
import no.nav.eessi.eessifagmodul.services.pensjonsinformasjon.PensjonsinformasjonService
import no.nav.eessi.eessifagmodul.services.pensjonsinformasjon.RequestBuilder
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.sakalder.V1SakAlder
import no.nav.pensjon.v1.trygdetid.V1Trygdetid
import no.nav.pensjon.v1.trygdetidliste.V1TrygdetidListe
import no.nav.pensjon.v1.vedtak.V1Vedtak
import no.nav.pensjon.v1.vilkarsvurdering.V1Vilkarsvurdering
import no.nav.pensjon.v1.vilkarsvurderingliste.V1VilkarsvurderingListe
import no.nav.pensjon.v1.vilkarsvurderinguforetrygd.V1VilkarsvurderingUforetrygd
import no.nav.pensjon.v1.ytelsepermaaned.V1YtelsePerMaaned
import no.nav.pensjon.v1.ytelsepermaanedliste.V1YtelsePerMaanedListe
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.util.ResourceUtils
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar
import kotlin.test.assertEquals

@RunWith(SpringRunner::class)
@ActiveProfiles("test")
@SpringBootTest
abstract class AbstractMockVedtakPensionHelper(private val xmlFilename: String) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(AbstractMockVedtakPensionHelper::class.java) }

    @Mock
    private lateinit var mockRestTemplate: RestTemplate

    protected lateinit var dataFromPESYS: VedtakDataFromPEN

    protected lateinit var prefill: PrefillDataModel

    protected lateinit var pendata: Pensjonsinformasjon

    @Autowired
    protected lateinit var eessiInformasjon: EessiInformasjon

    @Before
    fun setup() {
        prefill = PrefillDataModel()
        dataFromPESYS = mockPrefillPensionDataFromPESYS(xmlFilename)
        pendata = readPensjonsinformasjon(dataFromPESYS)
    }

    fun readPensjonsinformasjon(penDatafromPesys: VedtakDataFromPEN): Pensjonsinformasjon {
        prefill = generatePrefillData(60, "P6000")
        return penDatafromPesys.getPensjoninformasjonFraVedtak(prefill)
    }

    fun readXMLresponse(file: String): ResponseEntity<String> {
        val resource = ResourceUtils.getFile("classpath:pensjonsinformasjon/vedtak/$file").readText()
        return ResponseEntity(resource, HttpStatus.OK)
    }

    @Primary
    @Bean
    fun mockPrefillPensionDataFromPEN(pensjonsinformasjonService: PensjonsinformasjonService): PensjonsinformasjonHjelper {
        return PensjonsinformasjonHjelper(pensjonsinformasjonService, eessiInformasjon)
    }

    private fun mockVedtakFromPEN(mockPrefillPensionDataFromPEN: PensjonsinformasjonHjelper): VedtakDataFromPEN {
        return VedtakDataFromPEN(mockPrefillPensionDataFromPEN)
    }

    private fun mockPensjonsinformasjonService(mockRestTemplate: RestTemplate): PensjonsinformasjonService {
        return PensjonsinformasjonService(mockRestTemplate, RequestBuilder())
    }

    private fun mockPensjonsinformasjonRestTemplate(responseXMLfilename: String): RestTemplate {
        whenever(mockRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))).thenReturn(readXMLresponse(responseXMLfilename))
        return mockRestTemplate
    }

    fun mockPrefillPensionDataFromPESYS(responseXMLfilename: String): VedtakDataFromPEN {
        val pensjonsinformasjonService = mockPensjonsinformasjonService(mockPensjonsinformasjonRestTemplate(responseXMLfilename))
        return mockVedtakFromPEN(mockPrefillPensionDataFromPEN(pensjonsinformasjonService))
    }


    fun debugPrintFinalResult(result: Pensjon) {
        val sed = prefill.sed
        sed.pensjon = result
        val json = sed.toJson()
        logger.info("vedtak")
        logger.info("----------------------------------------------------------------------\n")
        logger.info(json)
        logger.info("----------------------------------------------------------------------\n")
    }


    fun generateFakePensjoninformasjonForALDER(): Pensjonsinformasjon {
        return generateFakePensjoninformasjonForKSAK("ALDER")
    }

    fun generateFakePensjoninformasjonForUFOREP(): Pensjonsinformasjon {
        return generateFakePensjoninformasjonForKSAK("UFOREP")
    }

    fun generateFakePensjoninformasjonForGJENLEV(): Pensjonsinformasjon {
        return generateFakePensjoninformasjonForKSAK("GJENLEV")
    }

    fun generatePrefillData(subtractYear: Int, sedId: String): PrefillDataModel {
        val items = listOf(InstitusjonItem(country = "NO", institution = "DUMMY"))
        prefill.apply {
            rinaSubject = "Pensjon"
            sed = SED.create(sedId)
            penSaksnummer = "12345"
            vedtakId = "12312312"
            buc = "P_BUC_99"
            aktoerID = "123456789"
            personNr = generateRandomFnr(subtractYear)
            institution = items
        }
        return prefill
    }

    private fun generateRandomFnr(yearsToSubtract: Int): String {
        val fnrdate = LocalDate.now().minusYears(yearsToSubtract.toLong())
        val y = fnrdate.year.toString()
        val day = fixDigits(fnrdate.dayOfMonth.toString())
        val month = fixDigits(fnrdate.month.value.toString())
        val fixedyear = y.substring(2, y.length)
        val fnr = day + month + fixedyear + 43352
        return fnr
    }

    private fun fixDigits(str: String): String {
        if (str.length == 1) {
            return "0$str"
        }
        return str
    }

    private fun generateFakePensjoninformasjonForKSAK(ksak: String): Pensjonsinformasjon {
        val xmlcal = DatatypeFactory.newInstance().newXMLGregorianCalendar()

        val v1sak = V1SakAlder()
        v1sak.sakType = ksak // "ALDER"
        v1sak.isUttakFor67 = false
        val v1vedtak = V1Vedtak()

        v1vedtak.datoFattetVedtak = xmlcal
        v1vedtak.virkningstidspunkt = xmlcal
        v1vedtak.isBoddArbeidetUtland = true

        val ytelsePerMaanedListe = V1YtelsePerMaanedListe()

        val v1YtelsePerMaaned = V1YtelsePerMaaned()
        v1YtelsePerMaaned.isMottarMinstePensjonsniva = true
        v1YtelsePerMaaned.fom = xmlcal
        v1YtelsePerMaaned.tom = xmlcal
        ytelsePerMaanedListe.ytelsePerMaanedListe.add(v1YtelsePerMaaned)

        val vilvirdUtfor = V1VilkarsvurderingUforetrygd()
        vilvirdUtfor.alder = "ALDER"
        vilvirdUtfor.nedsattInntektsevne = "SANN"

        val vilvurder = V1Vilkarsvurdering()
        vilvurder.avslagHovedytelse = "ALDER_PPP"
        vilvurder.vilkarsvurderingUforetrygd = vilvirdUtfor

        val vilkarsvurderingListe = V1VilkarsvurderingListe()
        vilkarsvurderingListe.vilkarsvurderingListe.add(vilvurder)

        val trygdetidListe = createTrygdelisteTid()

        val peninfo = Pensjonsinformasjon()
        peninfo.sakAlder = v1sak
        peninfo.vedtak = v1vedtak

        peninfo.ytelsePerMaanedListe = ytelsePerMaanedListe
        peninfo.vilkarsvurderingListe = vilkarsvurderingListe
        peninfo.trygdetidListe = trygdetidListe

        return peninfo
    }

    private fun createTrygdelisteTid(): V1TrygdetidListe {
        val ttid1 = V1Trygdetid()
        ttid1.fom = convertToXMLcal(LocalDate.now().minusDays(25))
        ttid1.tom = convertToXMLcal(LocalDate.now().minusDays(20))

        val ttid2 = V1Trygdetid()
        ttid2.fom = convertToXMLcal(LocalDate.now().minusDays(10))
        ttid2.tom = convertToXMLcal(LocalDate.now().minusDays(5))

        val ttid3 = V1Trygdetid()
        ttid3.fom = convertToXMLcal(LocalDate.now().minusDays(0))
        ttid3.tom = convertToXMLcal(LocalDate.now().plusDays(5))

        val trygdetidListe = V1TrygdetidListe()
        trygdetidListe.trygdetidListe.add(ttid1)
        trygdetidListe.trygdetidListe.add(ttid2)
        trygdetidListe.trygdetidListe.add(ttid3)
        return trygdetidListe
    }

    private fun convertToXMLcal(time: LocalDate): XMLGregorianCalendar {
        val gcal = GregorianCalendar()
        gcal.setTime(Date.from(time.atStartOfDay(ZoneId.systemDefault()).toInstant()))
        val xgcal = DatatypeFactory.newInstance().newXMLGregorianCalendar(gcal)
        return xgcal
    }


    @Test(expected = IllegalArgumentException::class)
    fun `preutfylling P6000 feiler ved mangler av vedtakId`() {
        prefill = generatePrefillData(68, "P6000")
        prefill.vedtakId = ""
        dataFromPESYS.prefill(prefill)

    }

    @Test
    fun `summerTrygdeTid forventet 10 dager, erTrygdeTid forventet til false`() {
        val ttid1 = V1Trygdetid()
        ttid1.fom = convertToXMLcal(LocalDate.now().minusDays(50))
        ttid1.tom = convertToXMLcal(LocalDate.now().minusDays(40))


        val trygdetidListe = V1TrygdetidListe()
        trygdetidListe.trygdetidListe.add(ttid1)

        val result = dataFromPESYS.summerTrygdeTid(trygdetidListe)
        logger.info("resultat: $result")
        assertEquals(10, result)

        val pendata = Pensjonsinformasjon()
        pendata.trygdetidListe = trygdetidListe
        val bolresult = dataFromPESYS.erTrygdeTid(pendata)
        //bod i utland mindre totalt 10dager en mer en mindre en 30 og mindre en 360
        assertEquals(false, bolresult)

    }

    @Test
    fun `summerTrygdeTid forventet 70 dager, erTrygdeTid forventet til true`() {
        val ttid1 = V1Trygdetid()
        ttid1.fom = convertToXMLcal(LocalDate.now().minusDays(170))
        ttid1.tom = convertToXMLcal(LocalDate.now().minusDays(100))

        val trygdetidListe = V1TrygdetidListe()
        trygdetidListe.trygdetidListe.add(ttid1)

        val result = dataFromPESYS.summerTrygdeTid(trygdetidListe)
        assertEquals(70, result)

        val pendata = Pensjonsinformasjon()
        pendata.trygdetidListe = trygdetidListe
        val bolresult = dataFromPESYS.erTrygdeTid(pendata)
        //bod i utland mindre en mer en 30 mindre en 360?
        assertEquals(true, bolresult)
    }

    @Test
    fun `summerTrygdeTid forventet 15 dager, erTrygdeTid forventet til false`() {
        val trygdetidListe = createTrygdelisteTid()

        val result = dataFromPESYS.summerTrygdeTid(trygdetidListe)
        logger.info("resultat: $result")
        assertEquals(15, result)

        val pendata = Pensjonsinformasjon()
        pendata.trygdetidListe = trygdetidListe

        val bolresult = dataFromPESYS.erTrygdeTid(pendata)
        //bod for lite i utland mindre en 30 dager?
        assertEquals(false, bolresult)
    }

    @Test
    fun `summerTrygdeTid forventet 500 dager, erTrygdeTid forventet til false`() {
        val ttid1 = V1Trygdetid()
        ttid1.fom = convertToXMLcal(LocalDate.now().minusDays(700))
        ttid1.tom = convertToXMLcal(LocalDate.now().minusDays(200))

        val trygdetidListe = V1TrygdetidListe()
        trygdetidListe.trygdetidListe.add(ttid1)

        val result = dataFromPESYS.summerTrygdeTid(trygdetidListe)
        logger.info("resultat: $result")
        assertEquals(500, result)

        val pendata = Pensjonsinformasjon()
        pendata.trygdetidListe = trygdetidListe

        val bolresult = dataFromPESYS.erTrygdeTid(pendata)
        //bod mye i utland mer en 360d.
        assertEquals(false, bolresult)
    }

    @Test
    fun `summerTrygdeTid forventet 0`() {
        val fom = LocalDate.now().minusDays(0)
        val tom = LocalDate.now().plusDays(0)
        val trygdetidListe = V1TrygdetidListe()
        val ttid1 = V1Trygdetid()
        ttid1.fom = convertToXMLcal(fom)
        ttid1.tom = convertToXMLcal(tom)
        trygdetidListe.trygdetidListe.add(ttid1)
        val result = dataFromPESYS.summerTrygdeTid(trygdetidListe)
        assertEquals(0, result)
    }

    @Test(expected = java.lang.IllegalArgumentException::class)
    fun `feiler ved boddArbeidetUtland ikke sann`() {
        prefill = generatePrefillData(66, "P6000")
        val resdata = mockPrefillPensionDataFromPESYS("P6000-AP-101.xml")
        resdata.prefill(prefill)
    }

    @Test
    fun `forventer "07" på AvlsagsBegrunnelse IKKE_MOTTATT_DOK`() {

        val pendata = generateFakePensjoninformasjonForALDER()
        pendata.vilkarsvurderingListe.vilkarsvurderingListe.get(0).resultatHovedytelse = "AVSLAG"
        pendata.vilkarsvurderingListe.vilkarsvurderingListe.get(0).avslagHovedytelse = "IKKE_MOTTATT_DOK"
        val result = dataFromPESYS.pensjonVedtak.createAvlsagsBegrunnelse(pendata)
        assertEquals("07", result)
    }

}
