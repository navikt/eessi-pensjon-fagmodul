package no.nav.eessi.eessifagmodul.models

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.whenever
import no.nav.eessi.eessifagmodul.controllers.SedController
import no.nav.eessi.eessifagmodul.prefill.PrefillDataModel
import no.nav.eessi.eessifagmodul.prefill.PrefillP4000
import no.nav.eessi.eessifagmodul.prefill.PrefillSED
import no.nav.eessi.eessifagmodul.prefill.nav.PrefillPerson
import no.nav.eessi.eessifagmodul.services.PrefillService
import no.nav.eessi.eessifagmodul.services.aktoerregister.AktoerregisterService
import no.nav.eessi.eessifagmodul.services.eux.EuxService
import no.nav.eessi.eessifagmodul.services.personv3.PersonV3Service
import no.nav.eessi.eessifagmodul.utils.mapAnyToJson
import no.nav.eessi.eessifagmodul.utils.mapJsonToAny
import no.nav.eessi.eessifagmodul.utils.typeRefs
import no.nav.eessi.eessifagmodul.utils.validateJson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


@RunWith(MockitoJUnitRunner::class)
class SedP4000Test {

    @Mock
    lateinit var mockEuxService: EuxService

    @Mock
    private lateinit var mockAktoerregisterService: AktoerregisterService

    @Mock
    lateinit var mockPrefillService: PrefillService

    @Mock
    lateinit var personService: PersonV3Service

    @Mock
    private lateinit var mockPrefillSED: PrefillSED

    @Mock
    private lateinit var prefillPerson: PrefillPerson

    lateinit var pre4000: PrefillP4000

    private lateinit var prefillDataMock: PrefillDataModel
    private lateinit var sedController: SedController

    val logger: Logger by lazy { LoggerFactory.getLogger(SedP4000Test::class.java) }

    @Before
    fun setup() {
        prefillDataMock = PrefillDataModel()

        pre4000 = PrefillP4000(prefillPerson)

        sedController = SedController(mockEuxService, mockPrefillService, mockAktoerregisterService)
        logger.debug("Starting tests.... ...")
    }

    @Test
    fun `create mock structure P4000`() {
        val result = createPersonTrygdeTidMock()
        assertNotNull(result)

        val sed = SED.create("P4000")
        val nav = NavMock().genererNavMock()
        val pen = PensjonMock().genererMockData()
        sed.nav = nav
        sed.pensjon = pen
        sed.trygdetid = result

        val json2 = sed.toJson()
        val mapSED = SED.fromJson(json2)

        assertNotNull(mapSED)
        assertEquals(result, mapSED.trygdetid)
    }

    @Test
    fun `create and validate P4000 from json to nav-sed back to json`() {
        //map load P4000-NAV refrence
        val path = Paths.get("src/test/resources/json/nav/P4000-NAV.json")
        val p4000file = String(Files.readAllBytes(path))
        assertNotNull(p4000file)
        validateJson(p4000file)
        val sed = SED.fromJson(p4000file)
        assertNotNull(sed)
        assertNotNull(sed.trygdetid)
        assertNotNull(sed.trygdetid?.ansattSelvstendigPerioder)
        val json = sed.toJson()
        JSONAssert.assertEquals(p4000file, json, false)
    }

    @Test
    fun `create dummy or mock apiRequest with p4000 json as payload`() {

        val trygdetid = createPersonTrygdeTidMock()
        val payload = mapAnyToJson(trygdetid)
        //logger.debug(payload)

        val req = SedController.ApiRequest(
                sed = "P4000",
                sakId = "12231231",
                euxCaseId = "99191999911",
                aktoerId = "00000",
                buc = "P_BUC_01",
                subjectArea = "Pensjon",
                payload = payload
        )
        val json = mapAnyToJson(req)
        assertNotNull(json)
        val apireq = mapJsonToAny(json, typeRefs<SedController.ApiRequest>())
        val payjson = apireq.payload ?: ""
        assertNotNull(payjson)
        assertEquals(payload, payjson)

        val check = mapJsonToAny(payjson, typeRefs<PersonArbeidogOppholdUtland>())
        assertNotNull(check)
        assertEquals("DK", check.boPerioder!![0].land)
    }

    @Test
    fun `create insurance periods P4000 from file`() {

        val path = Paths.get("src/test/resources/json/nav/other/p4000_trygdetid_part.json")
        val jsonfile = String(Files.readAllBytes(path))
        assertNotNull(jsonfile)
        validateJson(jsonfile)

        val obj = mapJsonToAny(jsonfile, typeRefs<PersonArbeidogOppholdUtland>(), true)
        assertNotNull(obj)

        val backtojson = mapAnyToJson(obj, true)
        assertNotNull(backtojson)
        validateJson(backtojson)
        val payload = mapAnyToJson(obj)
        val items = listOf(InstitusjonItem(country = "NO", institution = "DUMMY"))
        val req = SedController.ApiRequest(
                institutions = items,
                sed = "P4000",
                sakId = "12231231",
                euxCaseId = "99191999911",
                aktoerId = "00000",
                buc = "P_BUC_01",
                subjectArea = "Pensjon",
                payload = payload
        )
        assertNotNull(req)
        JSONAssert.assertEquals(jsonfile, backtojson, false)
    }

    @Test
    fun `validate and prefill P4000_2 from file`() {
        val path = Paths.get("src/test/resources/json/nav/other/requestP4000.json")
        val jsonfile = String(Files.readAllBytes(path))
        assertNotNull(jsonfile)
        validateJson(jsonfile)

        val items = listOf(InstitusjonItem(country = "NO", institution = "DUMMY"))
        val req = SedController.ApiRequest(
                institutions = items,
                sed = "P4000",
                sakId = "12231231",
                euxCaseId = "99191999911",
                aktoerId = "1000060964183",
                buc = "P_BUC_01",
                subjectArea = "Pensjon",
                payload = jsonfile
        )
        val reqjson = mapAnyToJson(req, true)
        assertNotNull(reqjson)
        validateJson(reqjson)

        whenever(mockAktoerregisterService.hentGjeldendeNorskIdentForAktorId(ArgumentMatchers.anyString())).thenReturn("12345")
        val data = sedController.buildPrefillDataModelConfirm(req)

        assertNotNull(data)
        assertNotNull(data.getPartSEDasJson("P4000"))
        assertEquals("12345", data.personNr)

        val resultData = data
        whenever(prefillPerson.prefill(any())).thenReturn(data.sed)
        val sed = pre4000.prefill(resultData)
        assertNotNull(sed)


        whenever(mockPrefillService.prefillSed(any())).thenReturn(resultData)

        val result = sedController.confirmDocument(req)

        //val jsondata = result.toJson()
        assertNotNull(result)
    }
}

fun createPersonTrygdeTidMock(): PersonArbeidogOppholdUtland {

    return PersonArbeidogOppholdUtland(
            foedselspermisjonPerioder = listOf(
                    StandardItem(
                            land = "NO",
                            usikkerDatoIndikator = "1",
                            annenInformasjon = "førdeslperm i Norge",
                            periode = TrygdeTidPeriode(
                                    lukketPeriode = Periode(
                                            fom = "2000-01-01",
                                            tom = "2001-01-01"
                                    )
                            )
                    ),
                    StandardItem(
                            land = "FR",
                            usikkerDatoIndikator = "0",
                            annenInformasjon = "fødselperm i frankrike",
                            periode = TrygdeTidPeriode(
                                    openPeriode = Periode(
                                            fom = "2002-01-01",
                                            extra = "98"
                                    )
                            )

                    )
            ),
            ansattSelvstendigPerioder = listOf(
                    AnsattSelvstendigItem(
                            typePeriode = "01",
                            jobbUnderAnsattEllerSelvstendig = "Kanin fabrikk ansatt",
                            annenInformasjon = "Noting else",
                            adresseFirma = Adresse(
                                    gate = "foo",
                                    postnummer = "23123",
                                    bygning = "Bygg",
                                    region = "Region",
                                    land = "NO",
                                    by = "Oslo"
                            ),
                            periode = TrygdeTidPeriode(
                                    lukketPeriode = Periode(
                                            tom = "1995-01-01",
                                            fom = "1990-01-01"
                                    )
                            ),
                            navnFirma = "Store Kaniner AS",
                            forsikkringEllerRegistreringNr = "12123123123123123",
                            usikkerDatoIndikator = "1"
                    )
            ),
            andrePerioder = listOf(
                    StandardItem(
                            land = "SE",
                            usikkerDatoIndikator = "1",
                            annenInformasjon = "ikkenoe",
                            typePeriode = "Ingen spesielt",
                            periode = TrygdeTidPeriode(
                                    lukketPeriode = Periode(
                                            fom = "2000-01-01",
                                            tom = "2001-01-01"
                                    )
                            )
                    ),
                    StandardItem(
                            land = "SE",
                            usikkerDatoIndikator = "1",
                            annenInformasjon = "ikkenoemere",
                            typePeriode = "Leve og ha det gøy",
                            periode = TrygdeTidPeriode(
                                    openPeriode = Periode(
                                            fom = "2000-01-01",
                                            extra = "01"
                                    )
                            )
                    )
            ),
            boPerioder = listOf(
                    StandardItem(
                            land = "DK",
                            usikkerDatoIndikator = "1",
                            annenInformasjon = "Deilig i Danmark",
                            periode = TrygdeTidPeriode(
                                    lukketPeriode = Periode(
                                            fom = "2003-01-01",
                                            tom = "2004-01-01"
                                    )
                            )
                    )
            ),
            arbeidsledigPerioder = listOf(
                    StandardItem(
                            land = "IT",
                            usikkerDatoIndikator = "1",
                            annenInformasjon = "Arbeidsledig i Itelia for en kort periode.",
                            navnPaaInstitusjon = "NAV stønad for arbeidsledigetstrygd",
                            periode = TrygdeTidPeriode(
                                    lukketPeriode = Periode(
                                            fom = "2002-01-01",
                                            tom = "2004-01-01"
                                    )
                            )

                    )
            ),
            forsvartjenestePerioder = listOf(
                    StandardItem(
                            land = "SE",
                            usikkerDatoIndikator = "1",
                            annenInformasjon = "Forsvar og mlitærtjeneste fullført i Svergige",
                            periode = TrygdeTidPeriode(
                                    lukketPeriode = Periode(
                                            fom = "2001-01-01",
                                            tom = "2004-01-01"
                                    )
                            )

                    )
            ),
            sykePerioder = listOf(
                    StandardItem(
                            land = "ES",
                            usikkerDatoIndikator = "1",
                            annenInformasjon = "Sykdom og forkjølelse i Spania",
                            navnPaaInstitusjon = "Støtte for sykeophold NAV",
                            periode = TrygdeTidPeriode(
                                    lukketPeriode = Periode(
                                            fom = "2005-01-01",
                                            tom = "2007-01-01"
                                    )
                            )

                    )

            ),
            frivilligPerioder = listOf(
                    StandardItem(
                            land = "GR",
                            usikkerDatoIndikator = "1",
                            annenInformasjon = "Frivilig hjelpemedarbeider i Helles",
                            periode = TrygdeTidPeriode(
                                    lukketPeriode = Periode(
                                            fom = "2006-01-01",
                                            tom = "2007-01-01"
                                    )
                            )

                    )
            ),
            opplaeringPerioder = listOf(
                    StandardItem(
                            land = "SE",
                            usikkerDatoIndikator = "1",
                            annenInformasjon = "Opplæring høyere utdanning i Sverige",
                            navnPaaInstitusjon = "Det Akademiske instutt for høgere lære, Stockholm",
                            periode = TrygdeTidPeriode(
                                    lukketPeriode = Periode(
                                            fom = "2000-01-01",
                                            tom = "2007-01-01"
                                    )
                            )

                    )
            ),
            barnepassPerioder = listOf(
                    BarnepassItem(
                            usikkerDatoIndikator = "1",
                            annenInformasjon = "Pass av barn under opphold i Danmark",
                            periode = TrygdeTidPeriode(
                                    lukketPeriode = Periode(
                                            tom = "2008-01-01",
                                            fom = "2004-01-01"
                                    )
                            ),
                            informasjonBarn = InformasjonBarn(
                                    fornavn = "Ole",
                                    etternavn = "Olsen",
                                    foedseldato = "2002-01-01",
                                    land = "DK"
                            )
                    ),
                    BarnepassItem(
                            usikkerDatoIndikator = "1",
                            annenInformasjon = "Pass av barn under opphold i Danmark",
                            periode = TrygdeTidPeriode(
                                    lukketPeriode = Periode(
                                            tom = "2008-01-01",
                                            fom = "2004-01-01"
                                    )
                            ),
                            informasjonBarn = InformasjonBarn(
                                    fornavn = "Teddy",
                                    etternavn = "Olsen",
                                    foedseldato = "2003-01-01",
                                    land = "DK"
                            )
                    )
            )
    )

}

//P5000 - bekreftforsikred
fun createMedlemskapMock(): Pensjon {

    return Pensjon(
            sak = Sak(
                    enkeltkrav = KravtypeItem(krav = "10")
            ),
            medlemskap = listOf(
                    MedlemskapItem(
                            land = "DK",
                            ordning = "01",
                            type = "10",
                            relevans = "100",
                            gyldigperiode = "1",
                            beregning = "100",
                            periode = Periode(
                                    fom = "2000-01-01",
                                    tom = "2010-01-01"
                            ),
                            sum = TotalSum(
                                    aar = "4",
                                    dager = Dager(nr = "2"),
                                    maaneder = "2"
                            )
                    )
            ),
            medlemskapAnnen = listOf(
                    MedlemskapItem(
                            land = "DE",
                            type = "21",
                            ordning = "01",
                            relevans = "100",
                            beregning = "100",
                            sum = TotalSum(
                                    aar = "4",
                                    maaneder = "2",
                                    dager = Dager(nr = "5")
                            )

                    )
            ),
            medlemskapTotal = listOf(
                    MedlemskapItem(
                            type = "10",
                            relevans = "100",
                            sum = TotalSum(
                                    aar = "11",
                                    maaneder = "1",
                                    dager = Dager(nr = "6")
                            )
                    )
            ),
            trygdetid = listOf(
                    MedlemskapItem(
                            type = "11",
                            sum = TotalSum(
                                    aar = "10",
                                    maaneder = "2",
                                    dager = Dager(nr = "5")
                            )
                    )
            )
    )
}
