package no.nav.eessi.pensjon.fagmodul.prefill.person

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.models.PersonId
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModelMother.initialPrefillDataModel
import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.fagmodul.prefill.PersonPDLMock
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.FodselsnummerMother.generateRandomFnr
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.NavFodselsnummer
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.PrefillPDLAdresse
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillSEDService
import no.nav.eessi.pensjon.fagmodul.prefill.sed.PrefillTestHelper
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrefillSedEnkeTest {

    private lateinit var pensjonsinformasjonService: PensjonsinformasjonService
    private lateinit var pensjonsinformasjonServiceGjen: PensjonsinformasjonService

    private lateinit var prefillPDLNav: PrefillPDLNav

    private val fnr = generateRandomFnr(67)
    private val b1fnr = generateRandomFnr(37)
    private val b2fnr = generateRandomFnr(17)

    @BeforeEach
    fun setup() {
        pensjonsinformasjonService = PrefillTestHelper.lesPensjonsdataFraFil("KravAlderEllerUfore_AP_UTLAND.xml")
        pensjonsinformasjonServiceGjen = PrefillTestHelper.lesPensjonsdataFraFil("P2100-GL-UTL-INNV.xml")

        prefillPDLNav = PrefillPDLNav(mock<PrefillPDLAdresse>(), institutionid = "NO:noinst002", institutionnavn = "NOINST002, NO INST002, NO")

    }

    @Test
    fun `forvent utfylling av person data av ENKE fra TPS P2100`() {
        val persondataCollection = PersonPDLMock.createEnkeWithBarn(fnr, b1fnr, b2fnr)

        val prefillData = initialPrefillDataModel(sedType = SEDType.P2100, pinId = fnr, avdod = PersonId(norskIdent = fnr, aktorId = "212"), vedtakId = "", penSaksnummer = "22875355")

        val response = prefillPDLNav.prefill(
            penSaksnummer = prefillData.penSaksnummer,
            bruker = prefillData.bruker,
            avdod = prefillData.avdod,
            personData = persondataCollection,
            brukerInformasjon = prefillData.getPersonInfoFromRequestData(),
            krav = null,
            annenPerson = null
        )

        val sed = SED(
            type = SEDType.P2100,
            nav = response
        )

        assertEquals("JESSINE TORDNU", sed.nav?.bruker?.person?.fornavn)
        assertEquals("BOUWMANS", sed.nav?.bruker?.person?.etternavn)
        assertEquals("K", sed.nav?.bruker?.person?.kjoenn)

        assertEquals(2, sed.nav?.barn?.size)

        val resultBarn = sed.nav?.barn!!

        val item1 = resultBarn.first()
        assertEquals("BOUWMANS", item1.person?.etternavn)
        assertEquals("TOPPI DOTTO", item1.person?.fornavn)
        val ident1 = item1.person?.pin?.get(0)?.identifikator
        val navfnr1 = NavFodselsnummer(ident1!!)
        assertEquals(false, navfnr1.isUnder18Year())
        assertEquals(37, navfnr1.getAge())


        val item2 = resultBarn.last()
        assertEquals("BOUWMANS", item2.person?.etternavn)
        assertEquals("EGIDIJS MASKOT", item2.person?.fornavn)
        val ident = item2.person?.pin?.get(0)?.identifikator
        val navfnr = NavFodselsnummer(ident!!)
        assertEquals(true, navfnr.isUnder18Year())
    }

    @Test
    fun `forvent utfylling av person data av ENKE fra TPS P2200`() {
        val prefillData = initialPrefillDataModel(sedType = SEDType.P2200, pinId = fnr, vedtakId = "", penSaksnummer = "14915730")
        val personCollection = PersonPDLMock.createEnkeWithBarn(fnr, b2fnr)

        val sed = PrefillSEDService(pensjonsinformasjonService, EessiInformasjon(), prefillPDLNav).prefill(prefillData, personCollection)

        assertEquals(SEDType.P2200, sed.type)

        assertEquals("JESSINE TORDNU", sed.nav?.bruker?.person?.fornavn)
        assertEquals("BOUWMANS", sed.nav?.bruker?.person?.etternavn)
        assertEquals("K", sed.nav?.bruker?.person?.kjoenn)
        assertEquals(1, sed.nav?.barn?.size)

        val barn = sed.nav?.barn?.last()!!
        val ident = barn.person?.pin?.get(0)?.identifikator
        val navfnr = NavFodselsnummer(ident!!)

        assertEquals("BOUWMANS", barn.person?.etternavn)
        assertEquals("TOPPI DOTTO", barn.person?.fornavn)
        assertEquals(true, navfnr.isUnder18Year())

    }


}
