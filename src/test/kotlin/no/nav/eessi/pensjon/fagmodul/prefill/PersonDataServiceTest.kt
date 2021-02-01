package no.nav.eessi.pensjon.fagmodul.prefill

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.fagmodul.prefill.LagPDLPerson.Companion.lagPerson
import no.nav.eessi.pensjon.fagmodul.prefill.LagPDLPerson.Companion.medAdresse
import no.nav.eessi.pensjon.fagmodul.prefill.LagPDLPerson.Companion.medBarn
import no.nav.eessi.pensjon.fagmodul.prefill.LagPDLPerson.Companion.medForeldre
import no.nav.eessi.pensjon.fagmodul.prefill.model.PersonId
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModelMother
import no.nav.eessi.pensjon.fagmodul.prefill.tps.FodselsnummerMother
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Sivilstandstype
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class PersonDataServiceTest {

    companion object {
        const val SAK_ID = "12345"
        const val EUX_RINA = "23123"

        const val FNR_OVER_60 = "09035225916"   // SLAPP SKILPADDE
        const val FNR_VOKSEN = "11067122781"    // KRAFTIG VEGGPRYD
        const val FNR_VOKSEN_2 = "22117320034"  // LEALAUS KAKE
        const val FNR_BARN = "12011577847"      // STERK BUSK

        const val AKTOER_ID = "0123456789000"
        const val AKTOER_ID_2 = "0009876543210"
    }

    private val personService: PersonService = mockk(relaxed = false)

    private val persondataService: PersonDataService = PersonDataService(
        personService = personService
    )

    @BeforeEach
    fun setup() {
         persondataService.initMetrics()
    }

    @AfterEach
    fun after() {
        clearAllMocks()
    }

    @Test
    fun `test henting av forsikretperson for persondatacollection`() {

        val mockPerson = lagPerson(FNR_VOKSEN)

        every { personService.hentPerson(any<Ident<*>>()) } returns mockPerson

        val data = PrefillDataModelMother.initialPrefillDataModel("P2001", FNR_VOKSEN, SAK_ID, euxCaseId = EUX_RINA)

        val result = persondataService.hentPersonData(data)

        assertEquals(null, result.ektefellePerson)
        assertEquals(null, result.sivilstandstype)
        assertEquals(emptyList(), result.barnPersonList)
        assertEquals(mockPerson, result.gjenlevendeEllerAvdod)
        assertEquals(mockPerson, result.forsikretPerson)

        verify ( exactly = 1 ) { personService.hentPerson(any<Ident<*>>())  }

    }

    @Test
    fun `test henting av forsikretperson og avdødperson for persondatacollection`() {

        val gjenlev = lagPerson(FNR_VOKSEN)
        val avdod = lagPerson(FNR_VOKSEN_2, erDod = true)

        every { personService.hentPerson(NorskIdent(FNR_VOKSEN)) } returns gjenlev
        every { personService.hentPerson(NorskIdent(FNR_VOKSEN_2)) } returns avdod

        val data = PrefillDataModelMother.initialPrefillDataModel("P2001", FNR_VOKSEN, SAK_ID, euxCaseId = EUX_RINA, avdod = PersonId(FNR_VOKSEN_2, AKTOER_ID_2))

        val result = persondataService.hentPersonData(data)

        assertEquals(null, result.ektefellePerson)
        assertEquals(null, result.sivilstandstype)
        assertEquals(emptyList(), result.barnPersonList)
        assertEquals(avdod, result.gjenlevendeEllerAvdod)
        assertEquals(gjenlev, result.forsikretPerson)

        verify ( exactly = 2 ) { personService.hentPerson(any<Ident<*>>())  }


    }

    @Test
    fun `test henting av forsikretperson og barn for persondatacollection`() {

        val forelder = lagPerson(FNR_VOKSEN, "Christopher", "Robin").medBarn(FNR_BARN)
        val barn = lagPerson(FNR_BARN, "Ole", "Brum").medForeldre(forelder)

        every { personService.hentPerson(NorskIdent(FNR_VOKSEN)) } returns forelder
        every { personService.hentPerson(NorskIdent(FNR_BARN)) } returns barn

        val data = PrefillDataModelMother.initialPrefillDataModel("P2001", FNR_VOKSEN, SAK_ID, euxCaseId = EUX_RINA)

        val result = persondataService.hentPersonData(data)

        assertEquals(null, result.ektefellePerson)
        assertEquals(null, result.sivilstandstype)
        assertEquals(barn, result.barnPersonList.firstOrNull())
        assertEquals(forelder, result.gjenlevendeEllerAvdod)
        assertEquals(forelder, result.forsikretPerson)

        verify ( exactly = 2 ) { personService.hentPerson(any<Ident<*>>())  }

    }

    @Test
    fun `test henting komplett familie med barn for persondatacollection`() {

        //generer fnr
        val farfnr = FodselsnummerMother.generateRandomFnr(42)
        val morfnr = FodselsnummerMother.generateRandomFnr(41)
        val barn1 = FodselsnummerMother.generateRandomFnr(11)
        val barn2 = FodselsnummerMother.generateRandomFnr(13)

        //far og mor i pair
        val pair = LagPDLPerson.createPersonMedEktefellePartner(farfnr, morfnr, Sivilstandstype.GIFT)

        //far og mor med barn
        val far = pair.first.medAdresse("STORGATA").medBarn(barn1).medBarn(barn2)
        val mor = pair.second.medAdresse("STORGATA").medBarn(barn1).medBarn(barn2)

        //barn
        val barnet = lagPerson(barn1, fornavn = "OLE", etternavn = "BRUM").medForeldre(far).medForeldre(mor)
        val barnto = lagPerson(barn2, fornavn = "NASSE", etternavn = "NØFF").medForeldre(far).medForeldre(mor)

        every { personService.hentPerson(NorskIdent(farfnr)) } returns far
        every { personService.hentPerson(NorskIdent(morfnr)) } returns mor
        every { personService.hentPerson(NorskIdent(barn1)) } returns barnet
        every { personService.hentPerson(NorskIdent(barn2)) } returns barnto

        val data = PrefillDataModelMother.initialPrefillDataModel("P2001", farfnr, SAK_ID, euxCaseId = EUX_RINA)

        val result = persondataService.hentPersonData(data)

        assertEquals(mor, result.ektefellePerson)
        assertEquals(Sivilstandstype.GIFT, result.sivilstandstype)
        assertEquals(listOf(barnet, barnto), result.barnPersonList)
        assertEquals(far, result.gjenlevendeEllerAvdod)
        assertEquals(far, result.forsikretPerson)

        verify ( exactly = 4 ) { personService.hentPerson(any<Ident<*>>())  }

    }


}