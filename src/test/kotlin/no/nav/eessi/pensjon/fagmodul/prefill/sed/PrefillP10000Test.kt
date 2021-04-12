package no.nav.eessi.pensjon.fagmodul.prefill.sed

import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.fagmodul.models.PersonDataCollection
import no.nav.eessi.pensjon.fagmodul.models.PersonId
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.models.PrefillDataModelMother
import no.nav.eessi.pensjon.fagmodul.prefill.PersonPDLMock
import no.nav.eessi.pensjon.fagmodul.prefill.pdl.FodselsnummerMother.generateRandomFnr
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillPDLNav
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PrefillP10000Test {
    private val personFnr = generateRandomFnr(68)
    private val ekteFnr = generateRandomFnr(70)
    private val pesysSaksnummer = "14398627"
    lateinit var prefillData: PrefillDataModel
    lateinit var prefill: PrefillP10000
    lateinit var prefillNav: PrefillPDLNav
    lateinit var persondataCollection: PersonDataCollection

    @BeforeEach
    fun setup() {
        persondataCollection = PersonPDLMock.createEnkelFamilie(personFnr, ekteFnr)

        prefillNav = PrefillPDLNav(
                prefillAdresse = mock(),
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO")

        prefill = PrefillP10000(prefillNav)
        prefillData = PrefillDataModelMother.initialPrefillDataModel(
            SedType.P8000,
            personFnr,
            penSaksnummer = pesysSaksnummer,
            avdod = PersonId("12345678910", "123456789"))

    }

    @Test
    fun `Gitt en preutfylt P10 000 så fyll ut annen person dersom den finnes`() {
        val p10000 = prefill.prefill(
            prefillData.penSaksnummer,
            prefillData.bruker,
            prefillData.avdod,
            prefillData.getPersonInfoFromRequestData(),
            persondataCollection)

        assertNotNull(p10000.nav!!.annenperson)
    }
}

