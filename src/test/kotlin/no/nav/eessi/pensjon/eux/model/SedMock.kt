package no.nav.eessi.pensjon.eux.model

import no.nav.eessi.pensjon.eux.model.sed.*

class SedMock {

    fun genererP5000Mock(): SED {
        return P5000(
                nav = NavMock().genererNavMock(),
                p5000Pensjon = PensjonMock().genererP5000Pensjon(),
                type = SedType.P5000,
                sedVer = "0",
                sedGVer = "4"
        )
    }

    fun genererP2000Mock(): SED {
        return SED(
                nav = NavMock().genererNavMock(),
                pensjon = PensjonMock().genererMockData(),
                type = SedType.P2200,
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
                        mor = Foreldre(
                                Person(
                                        fornavn = "asfsdf")
                        ),
                        far = Foreldre(
                                Person(
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
                        ),
                        bank = Bank(
                                konto = Konto(
                                        innehaver = Innehaver(
                                                rolle = "02",
                                                navn = "sdfsfsfsdf"
                                        )
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

        fun genererP5000Pensjon(): P5000Pensjon {
                val pensjonMeldemskap = createMedlemskapMock()

                return P5000Pensjon(
                        trygdetid = pensjonMeldemskap.trygdetid
                )
        }


    fun genererMockData(): Pensjon {
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
                                        fornavn = "sdfsdf"
                                )
                        ),
                        mor = Foreldre(
                                person = Person(
                                        fornavn = "asdfsdafsdf"
                                )
                        ),
                        person = Person(
                                fornavn = "gafgfdga",
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
                                etternavn = "asdffsdaf",
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
                                foedselsdato = "1964-12-01"
                        )
                )
        )
    }

}

//P5000 - bekreftforsikred
fun createMedlemskapMock(): P5000Pensjon {

        return P5000Pensjon(
                trygdetid = listOf(
                        MedlemskapItem()
                )
        )

}