package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.fagmodul.models.PersonId
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModelMother
import no.nav.eessi.pensjon.fagmodul.prefill.PersonPDLMock
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.FodselsnummerMother.generateRandomFnr
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.PrefillPDLAdresse
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper.lesPensjonsdataFraFil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PrefillP2100UforePRevurdering {

    private val personFnr = generateRandomFnr(45)
    private val avdodPersonFnr = generateRandomFnr(45)
    private val pesysSaksnummer = "22917763"
    private val pesysKravid = "12354"

    private lateinit var prefillData: PrefillDataModel
    private lateinit var prefillSEDService: PrefillSEDService
    private lateinit var prefillNav: PrefillPDLNav

    @BeforeEach
    fun setup() {

        prefillNav = PrefillPDLNav(
                prefillAdresse = mock<PrefillPDLAdresse>(),
                institutionid = "NO:NAVAT02",
                institutionnavn = "NOINST002, NO INST002, NO")


        prefillData = PrefillDataModelMother.initialPrefillDataModel(
                sedType = SedType.P2100,
                pinId = personFnr,
                penSaksnummer = pesysSaksnummer,
                avdod = PersonId(avdodPersonFnr, "112233445566"),
                kravId = pesysKravid)

    }

    @Test
    fun `forventet korrekt utfylt P2100 uforepensjon med kap4 og 9`() {
        val personDataCollection = PersonPDLMock.createAvdodFamilie(personFnr, avdodPersonFnr)
        val dataFromPEN = lesPensjonsdataFraFil("P2100-UP-GJ-REVURD-M-KRAVID.xml")

        prefillSEDService = PrefillSEDService(dataFromPEN, EessiInformasjon(), prefillNav)

        val p2100 = prefillSEDService.prefill(prefillData, personDataCollection)

        val sed = p2100
        assertNotNull(sed.nav?.krav)
        assertEquals("2020-08-01", sed.nav?.krav?.dato)
        assertEquals("Kravdato fra det opprinnelige vedtak med gjenlevenderett er angitt i SED P2100", prefillData.melding)
    }

}

