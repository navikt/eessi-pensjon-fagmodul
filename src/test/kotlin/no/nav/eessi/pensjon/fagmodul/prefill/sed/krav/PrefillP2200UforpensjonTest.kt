package no.nav.eessi.pensjon.fagmodul.prefill.sed.krav

import no.nav.eessi.pensjon.fagmodul.models.SED
import no.nav.eessi.pensjon.fagmodul.prefill.sed.AbstractPrefillIntegrationTestHelper
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonHjelper
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillPersonDataFromTPS
import no.nav.eessi.pensjon.fagmodul.prefill.person.PersonDataFromTPS
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.assertNotNull

class PrefillP2200UforpensjonTest : AbstractPrefillIntegrationTestHelper() {

    val logger: Logger by lazy { LoggerFactory.getLogger(PrefillP2200UforpensjonTest::class.java) }

    override fun mockPesysTestfilepath(): Pair<String, String> {
        return Pair("P2200", "P2000-AP-14069110.xml")
    }

    override fun opprettMockPersonDataTPS(): Set<PersonDataFromTPS.MockTPS>? {
        return setOf(
                PersonDataFromTPS.MockTPS("Person-20000.json", getFakePersonFnr(), PersonDataFromTPS.MockTPS.TPSType.PERSON),
                PersonDataFromTPS.MockTPS("Person-21000.json", PersonDataFromTPS.generateRandomFnr(43), PersonDataFromTPS.MockTPS.TPSType.BARN),
                PersonDataFromTPS.MockTPS("Person-22000.json", PersonDataFromTPS.generateRandomFnr(17), PersonDataFromTPS.MockTPS.TPSType.BARN)
        )
    }

    override fun createFakePersonFnr(): String {
        return PersonDataFromTPS.generateRandomFnr(67)
    }

    override fun createSaksnummer(): String {
        return "14069110"
    }

    override fun createTestClass(prefillNav: PrefillNav, personTPS: PrefillPersonDataFromTPS, pensionDataFromPEN: PensjonsinformasjonHjelper): Prefill<SED> {
        return PrefillP2200(prefillNav, personTPS, pensionDataFromPEN)
    }

    override fun createPayload(prefillData: PrefillDataModel) {
        prefillData.personNr = getFakePersonFnr()
        prefillData.partSedAsJson["PersonInfo"] = createPersonInfoPayLoad()
    }

    override fun createPersonInfoPayLoad(): String {
        return readJsonResponse("other/person_informasjon_selvb.json")
    }

    override fun createPersonTrygdetidHistorikk(): String {
        return ""
    }


    @Test
    fun `Testing av komplett utfylling kravsøknad uførepensjon P2200`() {

        pendata = kravdata.getPensjoninformasjonFraSak(prefillData)
        assertNotNull(pendata)

        val pensak = kravdata.getPensjonSak(prefillData, pendata)
        assertNotNull(pendata.brukersSakerListe)

        val P2200 = prefill.prefill(prefillData)

        logger.info(P2200.toString())
    }

}