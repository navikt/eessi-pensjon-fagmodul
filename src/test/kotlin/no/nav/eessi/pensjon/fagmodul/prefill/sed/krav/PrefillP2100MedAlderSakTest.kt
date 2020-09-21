package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonId
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModelMother
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.person.MockTpsPersonServiceFactory
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.lesPensjonsdataFraFil
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.setupPersondataFraTPS
import no.nav.eessi.pensjon.fagmodul.prefill.tps.FodselsnummerMother.generateRandomFnr
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillAdresse
import no.nav.eessi.pensjon.fagmodul.sedmodel.Krav
import no.nav.eessi.pensjon.fagmodul.sedmodel.Pensjon
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.pensjon.v1.kravhistorikk.V1KravHistorikk
import no.nav.pensjon.v1.kravhistorikkliste.V1KravHistorikkListe
import no.nav.pensjon.v1.sak.V1Sak
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.web.server.ResponseStatusException

@Disabled
@ExtendWith(MockitoExtension::class)
class PrefillP2100MedAlderSakTest {

    private val personFnr = generateRandomFnr(68)
    private val pesysSaksnummer = "21975717"
    private val avdodPersonFnr = generateRandomFnr(75)

    lateinit var prefillData: PrefillDataModel

    private lateinit var dataFromPEN: PensjonsinformasjonService
    private lateinit var prefillSEDService: PrefillSEDService
    private lateinit var persondataFraTPS :PersonV3Service
    private lateinit var prefillNav: PrefillNav

    @BeforeEach
    fun setup() {
        prefillNav = PrefillNav(
                prefillAdresse = mock<PrefillAdresse>(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

    }

    @Test
    fun `forventer utfylt P2100`() {
        persondataFraTPS = setupPersondataFraTPS(setOf(
                MockTpsPersonServiceFactory.MockTPS("Person-30000.json", personFnr, MockTpsPersonServiceFactory.MockTPS.TPSType.PERSON),
                MockTpsPersonServiceFactory.MockTPS("Person-31000.json", avdodPersonFnr, MockTpsPersonServiceFactory.MockTPS.TPSType.PERSON)
        ))

        dataFromPEN = lesPensjonsdataFraFil("KravAlderEllerUfore_AP_UTLAND.xml")

        prefillData = PrefillDataModelMother.initialPrefillDataModel(
                sedType = "P2100",
                pinId = personFnr,
                penSaksnummer = pesysSaksnummer,
                avdod = PersonId(avdodPersonFnr,"112233445566"),
                kravDato = "01-03-2020")

        prefillSEDService = PrefillSEDService(prefillNav, persondataFraTPS, EessiInformasjon(), dataFromPEN)
        val p2100 = prefillSEDService.prefill(prefillData)

        assertEquals("P2100", p2100.sed)
        assertEquals("BAMSE ULUR", p2100.pensjon?.gjenlevende?.person?.fornavn)
        assertEquals("BAMSE LUR", p2100.nav?.bruker?.person?.fornavn)
        assertEquals("2020-03-01", p2100.pensjon?.kravDato?.dato)
    }

    @Test
    fun `Gitt en P2100 uten kravdato når prefill utføres så kast en bad request`() {
        prefillData = PrefillDataModelMother.initialPrefillDataModel(
                sedType = "P2100",
                pinId = personFnr,
                penSaksnummer = pesysSaksnummer,
                avdod = PersonId(avdodPersonFnr,"112233445566"))
        val sak  = V1Sak()
        sak.sakType = EPSaktype.ALDER.name
        sak.kravHistorikkListe = V1KravHistorikkListe()


        assertThrows<ResponseStatusException> {
            PrefillP2100(prefillNav).kravDatoOverider(prefillData, sak)
        }
        sak.sakType = EPSaktype.UFOREP.name
        assertThrows<ResponseStatusException> {
            PrefillP2100(prefillNav).kravDatoOverider(prefillData, sak)
        }
    }

    @Test
    fun `Gitt en P2100 uten kravdato og uten kravhistrikk utføres xyz`() {
        prefillData = PrefillDataModelMother.initialPrefillDataModel(
                sedType = "P2100",
                pinId = personFnr,
                penSaksnummer = pesysSaksnummer,
                avdod = PersonId(avdodPersonFnr,"112233445566"),
                kravDato = "09-31-2010")

        val sed = prefillData.sed
        val mockPen = Pensjon(kravDato = Krav("01-07-2018"))
        sed.pensjon = mockPen

        val sak  = V1Sak()
        sak.sakType = EPSaktype.ALDER.name
        sak.kravHistorikkListe = V1KravHistorikkListe()
        sak.kravHistorikkListe.kravHistorikkListe.add(V1KravHistorikk())

        PrefillP2100(prefillNav).kravDatoOverider(prefillData, sak)

        assertEquals("01-07-2018", prefillData.sed.pensjon?.kravDato?.dato)
    }

    @Test
    fun `Gitt en P2100 med kravdato når prefill utføres så preutfylles kravdato`() {
        prefillData = PrefillDataModelMother.initialPrefillDataModel(
                kravDato = "01-01-2020",
                sedType = "P2100",
                pinId = personFnr,
                penSaksnummer = pesysSaksnummer,
                avdod = PersonId(avdodPersonFnr,"112233445566"))

        val sak  = V1Sak()
        sak.sakType = EPSaktype.ALDER.name
        sak.kravHistorikkListe = V1KravHistorikkListe()

        val sed = prefillData.sed
        val mockPen = Pensjon(kravDato = Krav())
        sed.pensjon = mockPen

        PrefillP2100(prefillNav).kravDatoOverider(prefillData, sak)

        assertEquals("2020-01-01", sed.pensjon?.kravDato?.dato)
    }
}

