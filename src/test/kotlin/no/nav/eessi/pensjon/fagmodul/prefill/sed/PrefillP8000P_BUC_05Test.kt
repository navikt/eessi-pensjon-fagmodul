package no.nav.eessi.pensjon.fagmodul.prefill.sed

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PersonId
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModelMother
import no.nav.eessi.pensjon.fagmodul.models.ReferanseTilPerson
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.LagPDLPerson
import no.nav.eessi.pensjon.fagmodul.prefill.LagPDLPerson.Companion.medAdresse
import no.nav.eessi.pensjon.fagmodul.prefill.PersonPDLMock.medUtlandAdresse
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.FodselsnummerMother.generateRandomFnr
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.PrefillPDLAdresse
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.services.geo.PostnummerService
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.services.pensjonsinformasjon.EPSaktype
import no.nav.eessi.pensjon.services.pensjonsinformasjon.KravArsak
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.pensjon.v1.kravhistorikk.V1KravHistorikk
import no.nav.pensjon.v1.kravhistorikkliste.V1KravHistorikkListe
import no.nav.pensjon.v1.sak.V1Sak
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.skyscreamer.jsonassert.JSONAssert


@ExtendWith(MockitoExtension::class)
class PrefillP8000P_BUC_05Test {

    private val personFnr = generateRandomFnr(68)
    private val pesysSaksnummer = "14398627"

    lateinit var prefillData: PrefillDataModel
    lateinit var prefillNav: PrefillPDLNav
    lateinit var personDataCollection: PersonDataCollection

    @Mock
    lateinit var pensjoninformasjonservice: PensjonsinformasjonService

    @Mock
    lateinit var kodeverkClient: KodeverkClient

    lateinit var prefillAdresse: PrefillPDLAdresse
    lateinit var prefillSEDService: PrefillSEDService

    @BeforeEach
    fun setup() {

        prefillAdresse = PrefillPDLAdresse(PostnummerService(), kodeverkClient)
        prefillNav = PrefillPDLNav( prefillAdresse,
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

        prefillSEDService = PrefillSEDService(pensjoninformasjonservice, EessiInformasjon(), prefillNav)
        prefillData = PrefillDataModelMother.initialPrefillDataModel(SEDType.P8000, personFnr, penSaksnummer = pesysSaksnummer)

    }

    @Test
    fun `Forventer korrekt utfylt P8000 med adresse`() {
        val fnr = generateRandomFnr(68)

        val personforsikret = LagPDLPerson.lagPerson(fnr, "Christopher", "Robin")
            .medUtlandAdresse("LUNGJTEGATA 12", "1231" , "SWE")

        personDataCollection = PersonDataCollection(personforsikret,personforsikret)

        doReturn("NO").whenever(kodeverkClient).finnLandkode2("NOR")
        doReturn("SE").whenever(kodeverkClient).finnLandkode2("SWE")

        val sed = prefillSEDService.prefill(prefillData, personDataCollection)

        assertEquals("Christopher", sed.nav?.bruker?.person?.fornavn)
        assertEquals("LUNGJTEGATA 12", sed.nav?.bruker?.adresse?.gate)
        assertEquals("SE", sed.nav?.bruker?.adresse?.land)
        assertEquals(pesysSaksnummer, sed.nav?.eessisak?.firstOrNull()?.saksnummer)
        assertEquals("Robin", sed.nav?.bruker?.person?.etternavn)
        assertEquals(fnr, sed.nav?.bruker?.person?.pin?.firstOrNull()?.identifikator)

    }

    @Test
    fun `Forventerkorrekt utfylt P8000 hvor det finnes en sak i Pesys som er gjenlevendepensjon eller barnepensjon - henvendelse gjelder avdøde`() {
        val fnr = generateRandomFnr(40)
        val avdodFnr = generateRandomFnr(93)

        val forsikretPerson = LagPDLPerson.lagPerson(fnr, "Christopher", "Robin")
            .medAdresse("Gate")

        val avdod = LagPDLPerson.lagPerson(avdodFnr, "Winnie", "Pooh", erDod = true)
            .medAdresse("Gate")

        personDataCollection = PersonDataCollection(avdod, forsikretPerson)

        doReturn("NO").whenever(kodeverkClient).finnLandkode2("NOR")

        prefillData = PrefillDataModelMother.initialPrefillDataModel(SEDType.P8000, fnr, penSaksnummer = pesysSaksnummer, avdod = PersonId(norskIdent = avdodFnr, aktorId = "21323"),  refTilPerson = ReferanseTilPerson.AVDOD)

        val sed =  prefillSEDService.prefill(prefillData, personDataCollection)

        //daua person
        assertEquals("Winnie", sed.nav?.bruker?.person?.fornavn)
        assertEquals("Pooh", sed.nav?.bruker?.person?.etternavn)

        //levende person
        assertEquals("Christopher", sed.nav?.annenperson?.person?.fornavn)
        assertEquals("Robin", sed.nav?.annenperson?.person?.etternavn)
        assertEquals("01", sed.nav?.annenperson?.person?.rolle)
        assertEquals("01",  sed.pensjon?.anmodning?.referanseTilPerson)

    }

    @Test
    fun `Forventer korrekt utfylt P8000 hvor det finnes en sak i Pesys som er gjenlevendepensjon eller barnepensjon - henvendelse gjelder gjenlevende-søker`() {
        val fnr = generateRandomFnr(40)
        val avdodFnr = generateRandomFnr(93)

        val forsikretPerson = LagPDLPerson.lagPerson(fnr, "Christopher", "Robin")
            .medAdresse("Gate")

        val avdod = LagPDLPerson.lagPerson(avdodFnr, "Winnie", "Pooh", erDod = true)
            .medAdresse("Gate")

        personDataCollection = PersonDataCollection(avdod, forsikretPerson)

        doReturn("NO").whenever(kodeverkClient).finnLandkode2("NOR")

        prefillData = PrefillDataModelMother.initialPrefillDataModel(SEDType.P8000, fnr, penSaksnummer = pesysSaksnummer, avdod = PersonId(norskIdent = avdodFnr, aktorId = "21323"), refTilPerson = ReferanseTilPerson.SOKER )

        val sed =  prefillSEDService.prefill(prefillData, personDataCollection)

        //daua person
        assertEquals("Winnie", sed.nav?.bruker?.person?.fornavn)
        assertEquals("Pooh", sed.nav?.bruker?.person?.etternavn)

        //levende person
        assertEquals("Christopher", sed.nav?.annenperson?.person?.fornavn)
        assertEquals("Robin", sed.nav?.annenperson?.person?.etternavn)
        assertEquals("01", sed.nav?.annenperson?.person?.rolle)

        //$pensjon.anmodning.referanseTilPerson"
        assertEquals("02",  sed.pensjon?.anmodning?.referanseTilPerson)
    }


    @Test
    fun `Forventer korrekt utfylt P8000 hvor det finnes en sak i Pesys som har alderpensjon  med revurdering og henvendelsen gjelder gjenlevende`() {
        val fnr = generateRandomFnr(40)
        val avdodFnr = generateRandomFnr(93)

        val forsikretPerson = LagPDLPerson.lagPerson(fnr, "Christopher", "Robin")
            .medUtlandAdresse("LUNGJTEGATA 12", "1231" , "SWE")

        val avdod = LagPDLPerson.lagPerson(avdodFnr, "Winnie", "Pooh", erDod = true)
            .medUtlandAdresse("LUNGJTEGATA 12", "1231" , "SWE")

        personDataCollection = PersonDataCollection(avdod, forsikretPerson)

        val sak = V1Sak()
        val v1Kravhistorikk = V1KravHistorikk()
        v1Kravhistorikk.kravArsak = KravArsak.GJNL_SKAL_VURD.name

        sak.sakType = EPSaktype.ALDER.toString()
        sak.sakId = 100
        sak.kravHistorikkListe = V1KravHistorikkListe()
        sak.kravHistorikkListe.kravHistorikkListe.add(v1Kravhistorikk)

      doReturn(sak).`when`(pensjoninformasjonservice).hentRelevantPensjonSak(any(), any())

        doReturn("NO").whenever(kodeverkClient).finnLandkode2("NOR")
        doReturn("SE").whenever(kodeverkClient).finnLandkode2("SWE")

        prefillData = PrefillDataModelMother.initialPrefillDataModel(SEDType.P8000, fnr, penSaksnummer = "100", avdod = PersonId(norskIdent = avdodFnr, aktorId = "21323"),  refTilPerson = ReferanseTilPerson.SOKER, bucType = "P_BUC_05")

        val sed =  prefillSEDService.prefill(prefillData, personDataCollection)

        val expected = """
            {
              "sed" : "P8000",
              "sedGVer" : "4",
              "sedVer" : "1",
              "nav" : {
                "eessisak" : [ {
                  "institusjonsid" : "NO:noinst002",
                  "institusjonsnavn" : "NOINST002, NO INST002, NO",
                  "saksnummer" : "100",
                  "land" : "NO"
                } ],
                "bruker" : {
                  "person" : {
                    "pin" : [ {
                      "identifikator" : "$fnr",
                      "land" : "NO"
                    } ],
                    "etternavn" : "Robin",
                    "fornavn" : "Christopher",
                    "kjoenn" : "M",
                    "foedselsdato" : "1981-02-01"
                  },
                  "adresse" : {
                    "gate" : "LUNGJTEGATA 12",
                    "by" : "UTLANDBY",
                    "land" : "SE"
                  }
                }
              },
            "pensjon" : {
                "anmodning" : {
                  "referanseTilPerson" : "02"
                }
              }              
             }

        """.trimIndent()

       JSONAssert.assertEquals(sed.toJsonSkipEmpty(), expected, true)
    }

    @Test
    fun `Forventerkorrekt utfylt P8000 hvor det finnes en sak i Pesys som har uføretrygd og henvendelsen gjelder gjenlevende`() {
        val fnr = generateRandomFnr(40)
        val avdodFnr = generateRandomFnr(93)

        val forsikretPerson = LagPDLPerson.lagPerson(fnr, "Christopher", "Robin")
            .medUtlandAdresse("LUNGJTÖEGATA 12", "1231" , "SWE")

        val avdod = LagPDLPerson.lagPerson(avdodFnr, "Winnie", "Pooh", erDod = true)
            .medUtlandAdresse("LUNGJTÖEGATA 12", "1231" , "SWE")

        personDataCollection = PersonDataCollection(avdod, forsikretPerson)

        val sak = V1Sak()
        val v1Kravhistorikk = V1KravHistorikk()
        sak.sakType = EPSaktype.UFOREP.toString()
        sak.sakId = 100
        sak.kravHistorikkListe = V1KravHistorikkListe()
        sak.kravHistorikkListe.kravHistorikkListe.add(v1Kravhistorikk)

        doReturn(sak).`when`(pensjoninformasjonservice).hentRelevantPensjonSak(any(), any())

        doReturn("NO").whenever(kodeverkClient).finnLandkode2("NOR")
        doReturn("SE").whenever(kodeverkClient).finnLandkode2("SWE")

        prefillData = PrefillDataModelMother.initialPrefillDataModel(SEDType.P8000, fnr, penSaksnummer = "100", avdod = PersonId(norskIdent = avdodFnr, aktorId = "21323"),  refTilPerson = ReferanseTilPerson.SOKER, bucType = "P_BUC_05")

        val sed =  prefillSEDService.prefill(prefillData, personDataCollection)

        val expected = """
            {
              "sed" : "P8000",
              "sedGVer" : "4",
              "sedVer" : "1",
              "nav" : {
                "eessisak" : [ {
                  "institusjonsid" : "NO:noinst002",
                  "institusjonsnavn" : "NOINST002, NO INST002, NO",
                  "saksnummer" : "100",
                  "land" : "NO"
                } ],
                "bruker" : {
                  "person" : {
                    "pin" : [ {
                      "identifikator" : "$fnr",
                      "land" : "NO"
                    } ],
                    "etternavn" : "Robin",
                    "fornavn" : "Christopher",
                    "kjoenn" : "M",
                    "foedselsdato" : "1981-02-01"                    
                  },
                  "adresse" : {
                    "gate" : "LUNGJTÖEGATA 12",
                    "by" : "UTLANDBY",
                    "land" : "SE"
                  }
                }
              },
            "pensjon" : {
                "anmodning" : {
                  "referanseTilPerson" : "02"
                }
              }              
            }

        """.trimIndent()

        JSONAssert.assertEquals(sed.toJsonSkipEmpty(), expected, true)
    }

}

