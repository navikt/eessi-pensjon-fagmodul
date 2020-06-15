package no.nav.eessi.pensjon.fagmodul.prefill.person

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.fagmodul.prefill.tps.NavFodselsnummer
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.typeRefs
import no.nav.tjeneste.virksomhet.person.v3.informasjon.*
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonResponse
import org.springframework.util.ResourceUtils

class MockTpsPersonServiceFactory(private val mocktps: Set<MockTPS>) {

    private fun initMockHentPersonResponse(mockTPS: MockTPS, mockTPSset: Set<MockTPS>): HentPersonResponse {
        val resource = ResourceUtils.getFile("classpath:personv3/${mockTPS.mockFile}").readText()

        val mockBarnList = mutableListOf<MockTPS>()
        val mockEkteItem = mutableListOf<MockTPS>()
        mockTPSset.forEach {
            if (it.mockType == MockTPS.TPSType.BARN) {
                mockBarnList.add(it)
            }
            if (it.mockType == MockTPS.TPSType.EKTE) {
                mockEkteItem.add(it)
            }
        }

        val mapper = jacksonObjectMapper()
        val rootNode = mapper.readValue(resource, JsonNode::class.java)

        val bostedsadresse = rootNode["bostedsadresse"]
        val sivilstand = rootNode["sivilstand"]
        val personnavn = rootNode["personnavn"]
        val personstatus = rootNode["personstatus"]
        val statsborgerskap = rootNode["statsborgerskap"]
        val kjoenn = rootNode["kjoenn"]
        val aktoer = rootNode["aktoer"]
        val strukturertAdresse = bostedsadresse["strukturertAdresse"]
        val poststed = strukturertAdresse["poststed"]
        val landkode = strukturertAdresse["landkode"]
        val harFraRolleI = rootNode["harFraRolleI"]
        val foedselsdato = rootNode["foedselsdato"]


        val v3struktAdr = mapJsonToAny(bostedsadresse.toString(), typeRefs<Gateadresse>())
        val v3personstatus = mapJsonToAny(personstatus.toString(), typeRefs<Personstatus>())
        val v3statsborgerskap = mapJsonToAny(statsborgerskap.toString(), typeRefs<Statsborgerskap>())
        val v3pernavn = mapJsonToAny(personnavn.toString(), typeRefs<Personnavn>())
        val v3sivilstand = mapJsonToAny(sivilstand.toString(), typeRefs<Sivilstand>())
        val v3kjoenn = mapJsonToAny(kjoenn.toString(), typeRefs<Kjoenn>())
        val v3aktoer = mapJsonToAny(aktoer.toString(), typeRefs<PersonIdent>())
        val v3poststed = mapJsonToAny(poststed.toString(), typeRefs<Postnummer>())
        val v3landkode = mapJsonToAny(landkode.toString(), typeRefs<Landkoder>())
        val v3foedselsdato = mapJsonToAny(foedselsdato.toString(), typeRefs<Foedselsdato>())

        val v3person = Bruker()

        harFraRolleI.forEach {
            val tilRolle = it["tilRolle"]
            val tilPerson = it["tilPerson"]
            val kjoennitem = tilPerson["kjoenn"]
            val aktoeritem = tilPerson["aktoer"]
            val personnavnitem = tilPerson["personnavn"]

            val v3familieRelasjon = Familierelasjon()
            v3familieRelasjon.tilRolle = mapJsonToAny(tilRolle.toString(), typeRefs<Familierelasjoner>())

            v3familieRelasjon.tilPerson = Bruker()
            if (!kjoennitem.isNull) {
                v3familieRelasjon.tilPerson.kjoenn = mapJsonToAny(kjoennitem.toString(), typeRefs<Kjoenn>())
            }
            val familieIdent = mapJsonToAny(aktoeritem.toString(), typeRefs<PersonIdent>())

            if (v3familieRelasjon.tilRolle.value == "BARN") {
                for (i in mockBarnList) {
                    if (i.mockType == MockTPS.TPSType.BARN && !i.used) {
                        familieIdent.ident.ident = i.replaceMockfnr
                        i.used = true
                        break
                    }
                }
            }
            if (v3familieRelasjon.tilRolle.value == "EKTE") {
                for (i in mockEkteItem) {
                    if (i.mockType == MockTPS.TPSType.EKTE && !i.used)
                        familieIdent.ident.ident = i.replaceMockfnr
                    i.used = true
                    break
                }
            }
            v3familieRelasjon.tilPerson.aktoer = familieIdent

            v3familieRelasjon.tilPerson.personnavn = mapJsonToAny(personnavnitem.toString(), typeRefs<Personnavn>())
            v3person.harFraRolleI.add(v3familieRelasjon)
        }

        v3person.bostedsadresse = Bostedsadresse()
        v3person.bostedsadresse.strukturertAdresse = v3struktAdr
        v3struktAdr.poststed = v3poststed
        v3struktAdr.landkode = v3landkode
        v3person.sivilstand = v3sivilstand
        v3person.personnavn = v3pernavn
        v3person.personstatus = v3personstatus
        v3person.foedselsdato = v3foedselsdato
        v3person.statsborgerskap = v3statsborgerskap
        v3person.kjoenn = v3kjoenn
        v3person.aktoer = v3aktoer

        val ident = v3person.aktoer as PersonIdent
        ident.ident.ident = mockTPS.replaceMockfnr

        NavFodselsnummer(ident.ident.ident)

        val v3PersonResponse = HentPersonResponse()
        v3PersonResponse.person = v3person

        return v3PersonResponse
    }

    fun mockPersonV3Service(): PersonV3Service {
        val mockPersonV3Service = mock<PersonV3Service>()
        mocktps.forEach {
            val result = initMockHentPersonResponse(it, mocktps).person as Bruker
            doReturn(result).whenever(mockPersonV3Service).hentBruker (it.replaceMockfnr)
        }
        return mockPersonV3Service
    }

    fun getRandomNavFodselsnummer(): String? {
        mocktps.forEach {
            if (it.mockType == MockTPS.TPSType.PERSON) {
                return it.replaceMockfnr
            }
        }
        return null
    }

    data class MockTPS(
            val mockFile: String,
            val replaceMockfnr: String,
            val mockType: TPSType,
            var used: Boolean = false
    ) {
        enum class TPSType {
            PERSON,
            EKTE,
            BARN;
        }
    }

}
