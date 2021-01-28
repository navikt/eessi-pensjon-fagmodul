package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjonMother.standardEessiInfo
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonId
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModelMother
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.MockTpsPersonServiceFactory
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper
import no.nav.eessi.pensjon.fagmodul.prefill.tps.FodselsnummerMother
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillAdresse
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrefillP6000Pensjon_GJENLEV_Test {

    private val personFnr = FodselsnummerMother.generateRandomFnr(57)
    private val avdodPersonFnr = FodselsnummerMother.generateRandomFnr(63)

    private lateinit var prefillData: PrefillDataModel
    private lateinit var prefillSEDService: PrefillSEDService
    private lateinit var dataFromPEN: PensjonsinformasjonService
    private lateinit var prefillNav: PrefillNav
    private lateinit var prefillPersonService: PersonV3Service
    private lateinit var eessiInformasjon: EessiInformasjon

    @Mock
    lateinit var aktorRegisterService: AktoerregisterService
    @Mock
    lateinit var prefillPDLNav: PrefillPDLNav

    @BeforeEach
    fun setup() {
        prefillPersonService = PrefillTestHelper.setupPersondataFraTPS(setOf(
                MockTpsPersonServiceFactory.MockTPS("Person-11000-GIFT.json", personFnr, MockTpsPersonServiceFactory.MockTPS.TPSType.PERSON),
                MockTpsPersonServiceFactory.MockTPS("Person-12000-EKTE.json", avdodPersonFnr, MockTpsPersonServiceFactory.MockTPS.TPSType.EKTE)
        ))

        prefillNav = PrefillNav(
                prefillAdresse = mock<PrefillAdresse>(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

        eessiInformasjon = standardEessiInfo()

    }

    @Test
    fun `forventet korrekt utfylling av Pensjon objekt på Gjenlevendepensjon`() {
        dataFromPEN = PrefillTestHelper.lesPensjonsdataVedtakFraFil("P6000-GP-401.xml")
        prefillData = PrefillDataModelMother.initialPrefillDataModel("P6000", personFnr, penSaksnummer = "22580170", vedtakId = "12312312", avdod = PersonId(avdodPersonFnr, "1234567891234"))
        prefillSEDService = PrefillSEDService(prefillNav, prefillPersonService, eessiInformasjon, dataFromPEN, aktorRegisterService, prefillPDLNav)

        val sed = prefillSEDService.prefill(prefillData)
        val result = sed.pensjon!!

        assertNotNull(result.vedtak)
        assertNotNull(result.sak)
        assertNotNull(result.tilleggsinformasjon)

        val avdod = sed.nav?.bruker?.person
        val gjenlev = sed.pensjon?.gjenlevende

        assertEquals("THOR-DOPAPIR", avdod?.fornavn)
        assertEquals("RAGNAROK", avdod?.etternavn)

        assertEquals("ODIN ETTØYE", gjenlev?.person?.fornavn)
        assertEquals("BALDER", gjenlev?.person?.etternavn)


        val vedtak = result.vedtak?.get(0)
        assertEquals("2018-05-01", vedtak?.virkningsdato, "vedtak.virkningsdato")
        assertEquals("03", vedtak?.type, "vedtak.type")
        assertEquals("02", vedtak?.basertPaa, "vedtak.basertPaa")
        assertEquals("03", vedtak?.resultat, "vedtak.resultat")
        assertEquals(null, vedtak?.kjoeringsdato)
        assertEquals(null, vedtak?.artikkel, "4.1.5 vedtak.artikkel (må fylles ut manuelt nå)")

        assertEquals("03", vedtak?.grunnlag?.opptjening?.forsikredeAnnen)
        assertEquals("1", vedtak?.grunnlag?.framtidigtrygdetid)

        val beregning = vedtak?.beregning?.get(0)
        assertEquals("2018-05-01", beregning?.periode?.fom)
        assertEquals(null, beregning?.periode?.tom)
        assertEquals("NOK", beregning?.valuta)
        assertEquals("maaned_12_per_aar", beregning?.utbetalingshyppighet)

        assertEquals("5248", beregning?.beloepBrutto?.beloep)
        assertEquals("3519", beregning?.beloepBrutto?.ytelseskomponentGrunnpensjon)
        assertEquals("1729", beregning?.beloepBrutto?.ytelseskomponentTilleggspensjon)

        assertEquals(null, vedtak?.ukjent?.beloepBrutto?.ytelseskomponentAnnen)

        val avslagBegrunnelse = vedtak?.avslagbegrunnelse?.get(0)
        assertEquals(null, avslagBegrunnelse?.begrunnelse)

        assertEquals("six weeks from the date the decision is received", result.sak?.kravtype?.get(0)?.datoFrist)

        assertEquals("2018-05-26", result.tilleggsinformasjon?.dato)
    }

    @Test
    fun `forventet korrekt utfylt P6000 gjenlevende ikke bosat utland (avdød bodd i utland)`() {
        dataFromPEN = PrefillTestHelper.lesPensjonsdataVedtakFraFil("P6000-GP-IkkeUtland.xml")
        prefillData = PrefillDataModelMother.initialPrefillDataModel("P6000", personFnr, penSaksnummer = "22580170", vedtakId = "12312312")
        prefillSEDService = PrefillSEDService(prefillNav, prefillPersonService, eessiInformasjon, dataFromPEN, aktorRegisterService, prefillPDLNav)

        val result = prefillSEDService.prefill(prefillData).pensjon!!

        assertNotNull(result.vedtak)
        assertNotNull(result.sak)
        assertNotNull(result.tilleggsinformasjon)

        val vedtak = result.vedtak?.get(0)
        assertEquals("2018-05-01", vedtak?.virkningsdato, "vedtak.virkningsdato")
        assertEquals("03", vedtak?.type, "vedtak.type")
        assertEquals("02", vedtak?.basertPaa, "vedtak.basertPaa")
        assertEquals("03", vedtak?.resultat, "vedtak.resultat")
        assertEquals(null, vedtak?.kjoeringsdato)
        assertEquals(null, vedtak?.artikkel, "4.1.5 vedtak.artikkel (må fylles ut manuelt nå)")

        assertEquals("03", vedtak?.grunnlag?.opptjening?.forsikredeAnnen)
        assertEquals("1", vedtak?.grunnlag?.framtidigtrygdetid)

        val beregning = vedtak?.beregning?.get(0)
        assertEquals("2018-05-01", beregning?.periode?.fom)
        assertEquals(null, beregning?.periode?.tom)
        assertEquals("NOK", beregning?.valuta)
        assertEquals("maaned_12_per_aar", beregning?.utbetalingshyppighet)

        assertEquals("6766", beregning?.beloepBrutto?.beloep)
        assertEquals("4319", beregning?.beloepBrutto?.ytelseskomponentGrunnpensjon)
        assertEquals("2447", beregning?.beloepBrutto?.ytelseskomponentTilleggspensjon)

        assertEquals(null, vedtak?.ukjent?.beloepBrutto?.ytelseskomponentAnnen)

        val avslagBegrunnelse = vedtak?.avslagbegrunnelse?.get(0)
        assertEquals(null, avslagBegrunnelse?.begrunnelse)

        assertEquals("six weeks from the date the decision is received", result.sak?.kravtype?.get(0)?.datoFrist)

        assertEquals("2018-05-26", result.tilleggsinformasjon?.dato)

        assertEquals("NO:noinst002", result.tilleggsinformasjon?.andreinstitusjoner?.get(0)?.institusjonsid)
        assertEquals("NOINST002, NO INST002, NO", result.tilleggsinformasjon?.andreinstitusjoner?.get(0)?.institusjonsnavn)
        assertEquals("Postboks 6600 Etterstad TEST", result.tilleggsinformasjon?.andreinstitusjoner?.get(0)?.institusjonsadresse)
        assertEquals("0607", result.tilleggsinformasjon?.andreinstitusjoner?.get(0)?.postnummer)
    }

    @Test
    fun `preutfylling P6000 feiler ved mangler av vedtakId`() {
        dataFromPEN = PrefillTestHelper.lesPensjonsdataVedtakFraFil("P6000-GP-IkkeUtland.xml")
        prefillData = PrefillDataModelMother.initialPrefillDataModel("6000", personFnr, penSaksnummer = "22580170", vedtakId = "")
        prefillSEDService = PrefillSEDService(prefillNav, prefillPersonService, eessiInformasjon, dataFromPEN, aktorRegisterService, prefillPDLNav)

        assertThrows<IllegalArgumentException> {
            prefillSEDService.prefill(prefillData)
        }
    }
}
