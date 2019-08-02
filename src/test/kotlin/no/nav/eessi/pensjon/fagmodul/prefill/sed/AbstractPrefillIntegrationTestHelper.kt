package no.nav.eessi.pensjon.fagmodul.prefill.sed

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import no.nav.eessi.pensjon.fagmodul.models.InstitusjonItem
import no.nav.eessi.pensjon.fagmodul.sedmodel.SED
import no.nav.eessi.pensjon.fagmodul.prefill.eessi.EessiInformasjon
import no.nav.eessi.pensjon.fagmodul.prefill.pen.PensjonsinformasjonHjelper
import no.nav.eessi.pensjon.fagmodul.prefill.model.Prefill
import no.nav.eessi.pensjon.fagmodul.prefill.model.PrefillDataModel
import no.nav.eessi.pensjon.fagmodul.prefill.sed.krav.SakHelper
import no.nav.eessi.pensjon.fagmodul.prefill.person.PrefillNav
import no.nav.eessi.pensjon.fagmodul.prefill.tps.PrefillPersonDataFromTPS
import no.nav.eessi.pensjon.fagmodul.prefill.person.PersonDataFromTPS
import no.nav.eessi.pensjon.fagmodul.prefill.person.PersonDataFromTPS.Companion.generateRandomFnr
import no.nav.eessi.pensjon.fagmodul.prefill.sed.krav.KravHistorikkHelper
import no.nav.eessi.pensjon.services.pensjonsinformasjon.PensjonsinformasjonService
import no.nav.eessi.pensjon.services.pensjonsinformasjon.RequestBuilder
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.lenient
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.util.ResourceUtils
import org.springframework.web.client.RestTemplate

abstract class AbstractPrefillIntegrationTestHelper {

    companion object {
        fun pensjonsDataFraPEN(responseXMLfilename: String): PensjonsinformasjonHjelper {
            val pensjonsinformasjonRestTemplate = mock<RestTemplate>()
            lenient().`when`(pensjonsinformasjonRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), ArgumentMatchers.eq(String::class.java))).thenReturn(readXMLresponse(responseXMLfilename))

            val pensjonsinformasjonService = PensjonsinformasjonService(pensjonsinformasjonRestTemplate, RequestBuilder())

            return PensjonsinformasjonHjelper(pensjonsinformasjonService)
        }

        val mockEessiInformasjon = EessiInformasjon(
                institutionid = "NO:noinst002",
                institutionnavn = "NOINST002, NO INST002, NO",
                institutionGate = "Postboks 6600 Etterstad TEST",
                institutionBy = "Oslo",
                institutionPostnr = "0607",
                institutionLand = "NO"
        )

        fun mockPrefillPersonDataFromTPS(mockPersonDataFraTPS: Set<PersonDataFromTPS.MockTPS>): PrefillPersonDataFromTPS {
            open class DataFromTPS(mocktps: Set<MockTPS>, eessiInformasjon: EessiInformasjon) : PersonDataFromTPS(mocktps, eessiInformasjon)
            val datatps = DataFromTPS(mockPersonDataFraTPS, mockEessiInformasjon)
            datatps.mockPersonV3Service = mock()
            return datatps.mockPrefillPersonDataFromTPS()
        }

        fun defaultPersondataFromTPS(): Set<PersonDataFromTPS.MockTPS> {
            val defaultPersonDataFromTPS = setOf(
                    PersonDataFromTPS.MockTPS("Person-20000.json", generateRandomFnr(67), PersonDataFromTPS.MockTPS.TPSType.PERSON),
                    PersonDataFromTPS.MockTPS("Person-21000.json", generateRandomFnr(43), PersonDataFromTPS.MockTPS.TPSType.BARN),
                    PersonDataFromTPS.MockTPS("Person-22000.json", generateRandomFnr(17), PersonDataFromTPS.MockTPS.TPSType.BARN)
            )
            return defaultPersonDataFromTPS
        }


        fun generatePrefillData(sedId: String, fnr: String? = null, subtractYear: Int? = null, sakId: String? = null): PrefillDataModel {
            val items = listOf(InstitusjonItem(country = "NO", institution = "DUMMY"))

            val year = subtractYear ?: 68

            return PrefillDataModel().apply {
                rinaSubject = "Pensjon"
                sed = SED(sedId)
                penSaksnummer = sakId ?: "12345678"
                vedtakId = "12312312"
                buc = "P_BUC_99"
                aktoerID = "123456789"
                personNr = fnr ?: generateRandomFnr(year)
                institution = items
            }
        }

        fun readJsonResponse(file: String): String {
            return ResourceUtils.getFile("classpath:json/nav/$file").readText()
        }

        private fun readXMLresponse(file: String): ResponseEntity<String> {
            val resource = ResourceUtils.getFile("classpath:pensjonsinformasjon/krav/$file").readText()
            return ResponseEntity(resource, HttpStatus.OK)
        }
    }
}
