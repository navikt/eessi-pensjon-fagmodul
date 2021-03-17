package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.fagmodul.models.SEDType

class SedMock {

    fun genererP5000Mock(): SED {
        return SED(
                nav = NavMock().genererNavMock(),
                pensjon = PensjonMock().genererMockDataMedMeldemskap(),
                type = SEDType.P5000,
                sedVer = "0",
                sedGVer = "4"
        )
    }

    fun genererP2000Mock(): SED {
        return SED(
                nav = NavMock().genererNavMock(),
                pensjon = PensjonMock().genererMockData(),
                type = SEDType.P2200,
                sedVer = "0",
                sedGVer = "4"
        )
    }

}

/**
 * Mock class genererer mock/fake Nav objekt
 */
class NavMock {

    /**
     * genererer mock av Nav
     * @return Nav
     */
    fun genererNavMock(): Nav {
        return Nav(
                bruker = Bruker(
                        //mor = Mor(
                        mor = Foreldre(
                                Person(
                                        etternavnvedfoedsel = "asdfsdf",
                                        fornavn = "asfsdf")
                        ),
                        //far = Far(
                        far = Foreldre(
                                Person(
                                        etternavnvedfoedsel = "safasfsd",
                                        fornavn = "farfornavn"
                                )
                        ),
                        person = Person(
                                pin = listOf(
                                        PinItem(
                                                sektor = "pensjoner",
                                                land = "BG",
                                                identifikator = "weqrwerwqe"
                                        ),
                                        PinItem(
                                                sektor = "alle",
                                                land = "NO",
                                                identifikator = "01126712345"
                                        )
                                )
                                ,
                                fornavn = "Gul",
                                kjoenn = "f",
                                foedselsdato = "1967-12-01",
                                etternavn = "Konsoll",
                                tidligereetternavn = "sdfsfasdf",
                                statsborgerskap = listOf(
                                        StatsborgerskapItem("BE"),
                                        StatsborgerskapItem("BG"),
                                        StatsborgerskapItem("GR"),
                                        StatsborgerskapItem("GB")
                                ),
                                foedested = Foedested(
                                        region = "sfgdfdgs",
                                        land = "DK",
                                        by = "gafdgsf"
                                ),
                                fornavnvedfoedsel = "werwerwe",
                                tidligerefornavn = "asdfdsffsd",
                                etternavnvedfoedsel = "werwreq"
                        ),
                        bank = Bank(
                                konto = Konto(
                                        innehaver = Innehaver(
                                                rolle = "02",
                                                navn = "sdfsfsfsdf"
                                        ),
                                        ikkesepa = IkkeSepa(
                                                swift = "AAAADK32323"
                                        ),
                                        kontonr = "12323434"
                                ),
                                adresse = Adresse(
                                        postnummer = "12344",
                                        bygning = "sfsfsdf",
                                        by = "fsdfsfs",
                                        land = "NO",
                                        region = "dsgdf",
                                        gate = "fsdfsdf"
                                ),
                                navn = "sdfsdfsdfsdf sdfsdfsdf"
                        )
                ),
                eessisak = listOf(
                        EessisakItem(
                                saksnummer = "24234sdsd-4",
                                land = "NO"
                        ),
                        EessisakItem(
                                saksnummer = "retretretert",
                                land = "HR"
                        )
                ),
                krav = Krav("1960-06-12")
        )
    }

}

//Mock prefill av en vedtak
class PensjonMock {

        fun genererMockDataMedMeldemskap(): Pensjon {
                val pensjonMeldemskap = createMedlemskapMock()

                return genererMockData(
                        trygdetid = pensjonMeldemskap.trygdetid
                )
        }


    fun genererMockData(
            trygdetid: List<MedlemskapItem>? = null
    ): Pensjon {
        return Pensjon(
                gjenlevende = Bruker(
                        adresse = Adresse(
                                postnummer = "sdfsdf",
                                by = "sfsdf",
                                land = "BG",
                                gate = "sdfsdfs",
                                bygning = "sdfsdfs",
                                region = "dfsdf"
                        ),
                        far = Foreldre(
                                person = Person(
                                        fornavn = "sdfsdf",
                                        etternavnvedfoedsel = "sdfsd")
                        ),
                        mor = Foreldre(
                                person = Person(
                                        etternavnvedfoedsel = "asdfsdf",
                                        fornavn = "asdfsdafsdf"
                                )
                        ),
                        person = Person(
                                fornavn = "gafgfdga",
                                fornavnvedfoedsel = "ffsdfgsfgadfg",
                                kjoenn = "m",
                                statsborgerskap = listOf(
                                        StatsborgerskapItem("AT"),
                                        StatsborgerskapItem("BE"),
                                        StatsborgerskapItem("GR")
                                ),
                                foedested = Foedested(
                                        land = "BE",
                                        region = "sdfsdfsdf",
                                        by = "Testaeveisgiverby2"),
                                tidligerefornavn = "sadfsdf",
                                etternavn = "asdffsdaf",
                                etternavnvedfoedsel = "sdfsd",
                                pin = listOf(
                                        PinItem(
                                                sektor = "dagpenger",
                                                land = "BE",
                                                identifikator = "sdafsdf"
                                        ),
                                        PinItem(
                                                sektor = "alle",
                                                land = "HR",
                                                identifikator = "sdfgsdgh"
                                        )
                                ),
                                tidligereetternavn = "asdfasdf",
                                foedselsdato = "1964-12-01"
                        )
                ),
                bruker = Bruker(
                        arbeidsforhold = listOf(
                                ArbeidsforholdItem(
                                        yrke = "Test yrke",
                                        type = "03",
                                        planlagtstartdato = "2024-10-01",
                                        planlagtpensjoneringsdato = "2024-10-01",
                                        sluttdato = "2024-10-01",
                                        arbeidstimerperuke = "42",
                                        inntekt = listOf(InntektItem(
                                                beloep = "1234",
                                                valuta = "NOK",
                                                beloeputbetaltsiden = "2024-10-01",
                                                annenbetalingshyppighetinntekt = "234234",
                                                betalingshyppighetinntekt = "06"

                                        )
                                        )
                                )
                        )
                ),
                trygdetid= trygdetid
        )
    }

}

//P5000 - bekreftforsikred
fun createMedlemskapMock(): Pensjon {

        return Pensjon(
                trygdetid = listOf(
                        MedlemskapItem()
                )
        )

}