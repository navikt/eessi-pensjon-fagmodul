package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModelMother
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.PersonPDLMock
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjonMother.standardEessiInfo
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.FodselsnummerMother
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.PrefillPDLAdresse
import no.nav.eessi.pensjon.fagmodul.prefill.pen.IkkeGyldigKallException
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.web.client.HttpClientErrorException

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrefillP6000Pensjon_ALDER_Test {

    private val personFnr = FodselsnummerMother.generateRandomFnr(67)
    private val ekteFnr = FodselsnummerMother.generateRandomFnr(70)

    private lateinit var prefillData: PrefillDataModel
    private lateinit var prefillSEDService: PrefillSEDService
    private lateinit var dataFromPEN: PensjonsinformasjonService
    private lateinit var eessiInformasjon: EessiInformasjon
    private lateinit var personDataCollection: PersonDataCollection
    private lateinit var prefillNav: PrefillPDLNav

    @BeforeEach
    fun setup() {
        personDataCollection = PersonPDLMock.createEnkelFamilie(personFnr, ekteFnr)

        prefillNav = PrefillPDLNav(
                prefillAdresse = mock<PrefillPDLAdresse>(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

        eessiInformasjon = standardEessiInfo()

    }

    @Test
    fun `forventet korrekt utfylling av Pensjon objekt på Alderpensjon`() {
        dataFromPEN = PrefillTestHelper.lesPensjonsdataVedtakFraFil("P6000-APUtland-301.xml")
        prefillData = PrefillDataModelMother.initialPrefillDataModel(SEDType.P6000, personFnr, penSaksnummer = "22580170", vedtakId = "12312312")
        prefillSEDService = PrefillSEDService(dataFromPEN, eessiInformasjon, prefillNav)

        val sed = prefillSEDService.prefill(prefillData, personDataCollection)
        val result = sed.pensjon!!

        assertNotNull(result.vedtak)
        assertNotNull(result.sak)
        assertNotNull(result.tilleggsinformasjon)

        assertEquals(1, result.vedtak?.size, "4.1  pensjon.vedtak")

        val vedtak = result.vedtak?.firstOrNull()
        assertEquals("2017-05-01", vedtak?.virkningsdato, "4.1.6  pensjon.vedtak[x].virkningsdato")
        assertEquals("01", vedtak?.type, "4.1.1 vedtak.type")
        assertEquals("02", vedtak?.basertPaa, "4.1.2 vedtak.basertPaa")

        assertEquals(null, vedtak?.basertPaaAnnen, "4.1.3.1 artikkel.basertPaaAnnen")
        assertEquals("01", vedtak?.resultat, "4.1.4 vedtak.resultat ")

        assertEquals(null, vedtak?.artikkel, "4.1.5 vedtak.artikkel ")
        assertEquals(null, vedtak?.artikkel, "4.1.5 vedtak.artikkel (må fylles ut manuelt nå)")

        assertEquals(null, vedtak?.kjoeringsdato, "4.1.8 vedtak.kjoeringsdato")

        assertEquals("01", vedtak?.grunnlag?.opptjening?.forsikredeAnnen, "4.1.10 vedtak?.grunnlag?.opptjening?.forsikredeAnnen")
        assertEquals("0", vedtak?.grunnlag?.framtidigtrygdetid, "4.1.10 vedtak?.grunnlag?.framtidigtrygdetid")

        assertEquals(null, vedtak?.avslagbegrunnelse, "4.1.13.1 -- 4.1.13.2.1")

        assertEquals(1, vedtak?.beregning?.size, "4.1.7 vedtak?.beregning")
        val beregning = vedtak?.beregning?.firstOrNull()
        assertEquals("2017-05-01", beregning?.periode?.fom)
        assertEquals(null, beregning?.periode?.tom)
        assertEquals("NOK", beregning?.valuta)
        assertEquals("2017-05-01", beregning?.periode?.fom)
        assertEquals("maaned_12_per_aar", beregning?.utbetalingshyppighet)
        assertEquals("11831", beregning?.beloepBrutto?.beloep)
        assertEquals("2719", beregning?.beloepBrutto?.ytelseskomponentGrunnpensjon)
        assertEquals("8996", beregning?.beloepBrutto?.ytelseskomponentTilleggspensjon)

        assertEquals("116", vedtak?.ukjent?.beloepBrutto?.ytelseskomponentAnnen)

        val avslagBegrunnelse = vedtak?.avslagbegrunnelse?.first()
        assertEquals(null, avslagBegrunnelse?.begrunnelse, "4.1.13.1 vedtak?.avslagbegrunnelse?")

        assertEquals("six weeks from the date the decision is received", result.sak?.kravtype?.get(0)?.datoFrist)
        assertEquals("2017-05-21", result.tilleggsinformasjon?.dato)

        assertEquals("NO:noinst002", result.tilleggsinformasjon?.andreinstitusjoner?.get(0)?.institusjonsid)
        assertEquals("Postboks 6600 Etterstad TEST", result.tilleggsinformasjon?.andreinstitusjoner?.get(0)?.institusjonsadresse)
        assertEquals("0607", result.tilleggsinformasjon?.andreinstitusjoner?.get(0)?.postnummer)
    }

    @Test
    fun `preutfylling P6000 feiler ved mangler av vedtakId`() {
        dataFromPEN = PrefillTestHelper.lesPensjonsdataVedtakFraFil("P6000-AP-101.xml")
        prefillData = PrefillDataModelMother.initialPrefillDataModel(SEDType.P6000, personFnr, penSaksnummer = "22580170", vedtakId = "")
        prefillSEDService = PrefillSEDService(dataFromPEN, eessiInformasjon, prefillNav)

        assertThrows<IkkeGyldigKallException> {
            prefillSEDService.prefill(prefillData, personDataCollection)
        }
    }

    @Test
    fun `feiler ved boddArbeidetUtland ikke sann`() {
        dataFromPEN = PrefillTestHelper.lesPensjonsdataVedtakFraFil("P6000-AP-101.xml")
        prefillData = PrefillDataModelMother.initialPrefillDataModel(SEDType.P6000, personFnr, penSaksnummer = "22580170", vedtakId = "12312312")
        prefillSEDService = PrefillSEDService(dataFromPEN, eessiInformasjon, prefillNav)

        assertThrows<HttpClientErrorException> {
            prefillSEDService.prefill(prefillData)
        }
    }
}
