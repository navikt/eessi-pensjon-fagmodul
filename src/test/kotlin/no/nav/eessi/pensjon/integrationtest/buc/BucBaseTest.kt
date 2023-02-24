package no.nav.eessi.pensjon.integrationtest.buc

import no.nav.eessi.pensjon.eux.klient.EuxKlientForSystemUser
import no.nav.eessi.pensjon.eux.klient.Properties
import no.nav.eessi.pensjon.eux.klient.Rinasak
import no.nav.eessi.pensjon.eux.klient.Traits
import no.nav.eessi.pensjon.vedlegg.client.BrukerId
import no.nav.eessi.pensjon.vedlegg.client.BrukerIdType
import no.nav.eessi.pensjon.vedlegg.client.SafRequest
import no.nav.eessi.pensjon.vedlegg.client.Variables
import no.nav.pensjon.v1.avdod.V1Avdod
import no.nav.pensjon.v1.pensjonsinformasjon.Pensjonsinformasjon
import no.nav.pensjon.v1.person.V1Person
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.util.UriComponents

open class BucBaseTest {

    fun dummyHeader(value: String?): HttpEntity<String> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        return HttpEntity(value, headers)
    }

    fun dummySafReqeust(aktoerId: String): String {
        val request = SafRequest(variables = Variables(BrukerId(aktoerId, BrukerIdType.AKTOERID), 10000))
        return request.toJson()
    }

    fun dummySafMetaResponse(): String {
        return """
            {
              "data": {
                "dokumentoversiktBruker": {
                  "journalposter": [
                    {
                      "tilleggsopplysninger": [],
                      "journalpostId": "439532144",
                      "datoOpprettet": "2018-06-08T17:06:58",
                      "tittel": "MASKERT_FELT",
                      "tema": "PEN",
                      "dokumenter": []
                    }
                  ]
                }
              }
            }
        """.trimIndent()
    }

    fun dummySafMetaResponseMedRina(rinaid: String): String {
        return """
            {
              "data": {
                "dokumentoversiktBruker": {
                  "journalposter": [
                    {
                      "tilleggsopplysninger": [
                          {
                              "nokkel":"eessi_pensjon_bucid",
                              "verdi":"$rinaid"
                            }  
                      ],
                      "journalpostId": "439532144",
                      "datoOpprettet": "2018-06-08T17:06:58",
                      "tittel": "MASKERT_FELT",
                      "tema": "PEN",
                      "dokumenter": []
                    }
                  ]
                }
              }
            }
        """.trimIndent()
    }

    fun dummySafMetaResponseMedRina(rinaid1: String, rinaid2: String): String {
        return """
            {
              "data": {
                "dokumentoversiktBruker": {
                  "journalposter": [
                    {
                      "tilleggsopplysninger": [
                          {
                              "nokkel":"eessi_pensjon_bucid",
                              "verdi":"$rinaid1"
                            }  
                      ],
                      "journalpostId": "4395321441",
                      "datoOpprettet": "2018-06-08T17:06:58",
                      "tittel": "MASKERT_FELT",
                      "tema": "PEN",
                      "dokumenter": []
                    },
                    {
                      "tilleggsopplysninger": [
                          {
                              "nokkel":"eessi_pensjon_bucid",
                              "verdi":"$rinaid2"
                            }  
                      ],
                      "journalpostId": "4395321440",
                      "datoOpprettet": "2018-06-08T17:06:58",
                      "tittel": "MASKERT_FELT",
                      "tema": "PEN",
                      "dokumenter": []
                    }                    
                  ]
                }
              }
            }
        """.trimIndent()
    }

    private fun journalfPortData(rinaid: String): String {
        return """
             {
              "tilleggsopplysninger": [
                  {
                      "nokkel":"eessi_pensjon_bucid",
                      "verdi":"$rinaid"
                    }  
              ],
              "journalpostId": "$rinaid-144",
              "datoOpprettet": "2018-06-08T17:06:58",
              "tittel": "MASKERT_FELT",
              "tema": "PEN",
              "dokumenter": []
            },
        """.trimIndent()
    }

    fun dummyRinasakAvdodUrl(avdod: String? = null) = dummyRinasakUrl(avdod, null)
    fun dummyRinasakUrl(fnr: String? = null, euxCaseId: String? = null) : UriComponents {
        return EuxKlientForSystemUser.getRinasakerUri(fnr, euxCaseId).also { println(it) }
    }

    fun dummyRinasak(rinaSakId: String, bucType: String): Rinasak {
        return Rinasak(rinaSakId, bucType, Traits(), "", Properties(), "open")
    }

    fun mockVedtak(avdofnr: String, gjenlevAktoerid: String): Pensjonsinformasjon {
        val pen = Pensjonsinformasjon()
        val avdod = V1Avdod()
        val person = V1Person()
        avdod.avdod = avdofnr
        person.aktorId = gjenlevAktoerid
        pen.avdod = avdod
        pen.person = person

        return pen
    }
    fun mockVedtakUtenAvdod(gjenlevAktoerid: String): Pensjonsinformasjon {
        val pen = Pensjonsinformasjon()
        val person = V1Person()
        person.aktorId = gjenlevAktoerid
        pen.person = person
        return pen
    }

}