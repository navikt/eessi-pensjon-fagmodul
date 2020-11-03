package no.nav.eessi.pensjon.fagmodul.prefill.sed.vedtak

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjonMother.standardEessiInfo
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModelMother
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.MockTpsPersonServiceFactory
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper
import no.nav.eessi.pensjon.fagmodul.prefill.tps.FodselsnummerMother
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillAdresse
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import kotlin.test.assertNull

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrefillP6000Pensjon_UFORE_AVSLAG_Test {

    private val personFnr = FodselsnummerMother.generateRandomFnr(67)

    private lateinit var prefillData: PrefillDataModel
    private lateinit var prefillSEDService: PrefillSEDService
    private lateinit var dataFromPEN: PensjonsinformasjonService
    private lateinit var prefillNav: PrefillNav
    private lateinit var prefillPersonService: PersonV3Service
    private lateinit var eessiInformasjon: EessiInformasjon

    @Mock
    lateinit var aktorRegisterService: AktoerregisterService

    @BeforeEach
    fun setup() {
        prefillPersonService = PrefillTestHelper.setupPersondataFraTPS(setOf(
                MockTpsPersonServiceFactory.MockTPS("Person-11000-GIFT.json", personFnr, MockTpsPersonServiceFactory.MockTPS.TPSType.PERSON),
                MockTpsPersonServiceFactory.MockTPS("Person-12000-EKTE.json", FodselsnummerMother.generateRandomFnr(69), MockTpsPersonServiceFactory.MockTPS.TPSType.EKTE)
        ))

        prefillNav = PrefillNav(
                prefillAdresse = mock<PrefillAdresse>(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

        eessiInformasjon = standardEessiInfo()

    }

    @Test
    fun `forventet korrekt utfylling av Pensjon objekt på Uførepensjon med avslag`() {
        dataFromPEN = PrefillTestHelper.lesPensjonsdataVedtakFraFil("P6000-UF-Avslag.xml")
        prefillData = PrefillDataModelMother.initialPrefillDataModel("P6000", personFnr, penSaksnummer = "22580170", vedtakId = "12312312")
        prefillSEDService = PrefillSEDService(prefillNav, prefillPersonService, eessiInformasjon, dataFromPEN, aktorRegisterService)

        val result = prefillSEDService.prefill(prefillData)

        assertNotNull(result)
        assertNotNull(result.pensjon)
        assertNull(result.pensjon?.vedtak)
        assertNull(result.pensjon?.sak)

//        assertNotNull(result.vedtak)
//        assertNotNull(result.sak)
//        assertNotNull(result.tilleggsinformasjon)
//
//        val vedtak = result.vedtak?.get(0)
//        assertEquals("2019-08-01", vedtak?.virkningsdato, "vedtak.virkningsdato")
//        assertEquals("02", vedtak?.type)
//        assertEquals(null, vedtak?.basertPaa)
//        assertEquals(null, vedtak?.basertPaaAnnen)
//        assertEquals("02", vedtak?.resultat, "vedtak.resultat")
//        assertEquals(null, vedtak?.kjoeringsdato)
//        assertEquals(null, vedtak?.artikkel, "4.1.5 vedtak.artikkel (må fylles ut manuelt nå)")
//
//        assertEquals(null, vedtak?.grunnlag?.opptjening?.forsikredeAnnen)
//        assertEquals(null, vedtak?.grunnlag?.framtidigtrygdetid)
//
//        assertEquals(null, vedtak?.ukjent?.beloepBrutto?.ytelseskomponentAnnen)
//
//        val avslagBegrunnelse = vedtak?.avslagbegrunnelse?.get(0)
//        assertEquals(null, avslagBegrunnelse?.begrunnelse)
//
//        assertEquals("six weeks from the date the decision is received", result.sak?.kravtype?.get(0)?.datoFrist)
//
//        assertEquals("2019-08-26", result.tilleggsinformasjon?.dato)
//
//        assertEquals("NO:noinst002", result.tilleggsinformasjon?.andreinstitusjoner?.get(0)?.institusjonsid)
//        assertEquals("Postboks 6600 Etterstad TEST", result.tilleggsinformasjon?.andreinstitusjoner?.get(0)?.institusjonsadresse)
//        assertEquals("0607", result.tilleggsinformasjon?.andreinstitusjoner?.get(0)?.postnummer)

    }

    @Test
    fun `preutfylling P6000 feiler ved mangler av vedtakId`() {
        dataFromPEN = PrefillTestHelper.lesPensjonsdataVedtakFraFil("P6000-UF-Avslag.xml")
        prefillData = PrefillDataModelMother.initialPrefillDataModel("6000", personFnr, penSaksnummer = "22580170", vedtakId = "")
        prefillSEDService = PrefillSEDService(prefillNav, prefillPersonService, eessiInformasjon, dataFromPEN, aktorRegisterService)

        assertThrows<IllegalArgumentException> {
            prefillSEDService.prefill(prefillData)
        }
    }
}
