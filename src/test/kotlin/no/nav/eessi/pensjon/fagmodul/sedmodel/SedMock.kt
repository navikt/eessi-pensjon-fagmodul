package no.nav.eessi.pensjon.fagmodul.sedmodel

class SedMock {

    fun genererP6000Mock(): SED {
        return SED(
                nav = NavMock().genererNavMock(),
                pensjon = PensjonMock().genererMockData(),
                sed = "vedtak",
                sedVer = "0",
                sedGVer = "4"
                //sector = "Sector Components/Pensions/vedtak"
        )
    }

    fun genererP5000Mock(): SED {
        return SED(
                nav = NavMock().genererNavMock(),
                pensjon = PensjonMock().genererMockDataMedMeldemskap(),
                sed = "P5000",
                sedVer = "0",
                sedGVer = "4"
        )
    }

    fun genererP2000Mock(): SED {
        return SED(
                nav = NavMock().genererNavMock(),
                pensjon = PensjonMock().genererMockData(),
                sed = "P2200",
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
                )
        )
    }

}

//Mock prefill av en vedtak
class PensjonMock {

    fun genererMockDataMedMeldemskap(): Pensjon {
        val pensjonMeldemskap = createMedlemskapMock()

        val pensjon = genererMockData(
            medlemskap= pensjonMeldemskap.medlemskap,
            medlemskapAnnen= pensjonMeldemskap.medlemskapAnnen,
            medlemskapTotal= pensjonMeldemskap.medlemskapTotal,
            trygdetid= pensjonMeldemskap.trygdetid
        )

        return pensjon
    }


    fun genererMockData(
            medlemskap: List<MedlemskapItem>? = null,
            medlemskapAnnen: List<MedlemskapItem>? = null,
            medlemskapTotal: List<MedlemskapItem>? = null,
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
                )
                //end Gjendlevende//Bruker
                ,
                vedtak = listOf(
                        VedtakItem(
                                grunnlag = Grunnlag(
                                        framtidigtrygdetid = "0",
                                        medlemskap = "02",
                                        opptjening = Opptjening(
                                                forsikredeAnnen = "01"
                                        )
                                ),
                                beregning = listOf(
                                        BeregningItem(
                                                beloepBrutto = BeloepBrutto(
                                                        ytelseskomponentGrunnpensjon = "2344",
                                                        beloep = "523",
                                                        ytelseskomponentTilleggspensjon = "234"
                                                ),
                                                periode = Periode(
                                                        tom = "2030-10-01",
                                                        fom = "2023-10-01"
                                                ),
                                                valuta = "HUF",
                                                beloepNetto = BeloepNetto(
                                                        beloep = "344"
                                                ),
                                                utbetalingshyppighetAnnen = "13123",
                                                utbetalingshyppighet = "maaned_12_per_aar"
                                        ),
                                        BeregningItem(
                                                utbetalingshyppighetAnnen = "werwer",
                                                beloepBrutto = BeloepBrutto(
                                                        beloep = "234",
                                                        ytelseskomponentTilleggspensjon = "22",
                                                        ytelseskomponentGrunnpensjon = "342"
                                                ),
                                                periode = Periode(
                                                        fom = "2020-10-01",
                                                        tom = "2025-10-01"
                                                ),
                                                beloepNetto = BeloepNetto(
                                                        beloep = "344"
                                                ),
                                                utbetalingshyppighet = "annet",
                                                valuta = "ISK"
                                        )
                                ),
                                basertPaa = "02",
                                delvisstans = Delvisstans(
                                        utbetaling = Utbetaling(
                                                begrunnelse = "sfdgsdf\\nfdg\\ns",
                                                beloepBrutto = "24234",
                                                valuta = "SEK"
                                        ),
                                        indikator = "1"
                                ),
                                virkningsdato = "2020-10-01",
                                artikkel = "02",
                                kjoeringsdato = "2020-12-01",
                                type = "02",
                                basertPaaAnnen = "sadfsdf",
                                ukjent = Ukjent(
                                        beloepBrutto = BeloepBrutto(
                                                ytelseskomponentAnnen = "sdfsfd\\nsdf\\nsfd"
                                        )
                                ),
                                resultat = "01",
                                avslagbegrunnelse = listOf(
                                        AvslagbegrunnelseItem(
                                                begrunnelse = "03",
                                                annenbegrunnelse = "fsafasfd\\nasd\\nfsda"
                                        ),
                                        AvslagbegrunnelseItem(
                                                begrunnelse = "02",
                                                annenbegrunnelse = "tet\\nertert\\nretret"
                                        )
                                ),
                                begrunnelseAnnen = "afsdaf\\nsdafsfasd\\nsadfsd"
                        ),
                        VedtakItem(
                                beregning = listOf(
                                        BeregningItem(
                                                utbetalingshyppighetAnnen = "gagfdgg",
                                                valuta = "ERN",
                                                beloepBrutto = BeloepBrutto(
                                                        ytelseskomponentTilleggspensjon = "12",
                                                        ytelseskomponentGrunnpensjon = "122",
                                                        beloep = "234"
                                                ),
                                                beloepNetto = BeloepNetto(
                                                        beloep = "23"
                                                ),
                                                periode = Periode(
                                                        tom = "2043-10-01",
                                                        fom = "2032-10-01"
                                                ),
                                                utbetalingshyppighet = "kvartalsvis"
                                        )
                                ),
                                avslagbegrunnelse = listOf(
                                        AvslagbegrunnelseItem(
                                                annenbegrunnelse = "324234\\n234\\n234\\n4",
                                                begrunnelse = "04"
                                        ),
                                        AvslagbegrunnelseItem(
                                                annenbegrunnelse = "sdfafs\\nsdfsdf\\nfsadfsdf",
                                                begrunnelse = "04"
                                        )
                                ),
                                grunnlag = Grunnlag(
                                        framtidigtrygdetid = "0",
                                        medlemskap = "03",
                                        opptjening = Opptjening(
                                                forsikredeAnnen = "03"
                                        )
                                ),
                                artikkel = "03",
                                basertPaaAnnen = "wertwertwert",
                                delvisstans = Delvisstans(
                                        utbetaling = Utbetaling(
                                                begrunnelse = "sdfsdf\nsdfsdf\nsdf\nfsd",
                                                beloepBrutto = "234",
                                                valuta = "NZD"
                                        ),
                                        indikator = "0"
                                ),
                                type = "03",
                                begrunnelseAnnen = "sdfsdf\\nsd\\nfsd",
                                resultat = "03",
                                kjoeringsdato = "2022-10-01",
                                ukjent = Ukjent(
                                        beloepBrutto = BeloepBrutto(
                                                ytelseskomponentAnnen = "dsfsdf\\ns\\ndf\\nsdf"
                                        )
                                ),
                                virkningsdato = "2030-10-01",
                                basertPaa = "01"
                        )
                )
                //end of vedtak Vedtakitem
                ,
                sak = Sak(
                        artikkel54 = "0",
                        kravtype = listOf(
                                KravtypeItem(
                                        datoFrist = "fasfsda"
                                )
                        ),
                        reduksjon = listOf(
                                ReduksjonItem(
                                        artikkeltype = "02"
                                ),
                                ReduksjonItem(
                                        artikkeltype = "03"
                                )
                        )
                ),
                reduksjon = listOf(
                        ReduksjonItem(
                                type = "02",
                                virkningsdato = listOf(
                                        VirkningsdatoItem(
                                                sluttdato = "2021-09-01",
                                                startdato = "2020-12-01"
                                        ),
                                        VirkningsdatoItem(
                                                sluttdato = "2022-10-01",
                                                startdato = "2034-10-01"
                                        )
                                ),
                                aarsak = Arsak(
                                        annenytelseellerinntekt = "06",
                                        inntektAnnen = "adfasfsd"
                                )
                        ),
                        ReduksjonItem(
                                virkningsdato = listOf(
                                        VirkningsdatoItem(
                                                sluttdato = "2034-10-01",
                                                startdato = "2033-10-01"
                                        )
                                ),
                                aarsak = Arsak(
                                        annenytelseellerinntekt = "02",
                                        inntektAnnen = "ewrwer"
                                ),
                                type = "02"
                        )
                ),
                tilleggsinformasjon = Tilleggsinformasjon(
                        andreinstitusjoner = listOf(
                                AndreinstitusjonerItem(
                                        institusjonsadresse = "asdfsdf",
                                        region = "sadfasdf",
                                        postnummer = "asdfsdf",
                                        bygningsnr = "sdafsadf",
                                        poststed = "safsd",
                                        land = "HR"
                                )
                        ),
                        person = Person(
                                pinannen = PinItem(
                                        identifikator = "retertret",
                                        sektor = "alle"
                                )

                        ),
                        dato = "2019-10-01",
                        anneninformation = "werwer\\nwer\\nwer",
                        annen = Annen(
                                institusjonsadresse = Institusjonsadresse(
                                        land = "BE"
                                )
                        ),
                        opphoer = Opphoer(
                                dato = "2022-10-01",
                                annulleringdato = "2024-10-01"
                        ),
                        saksnummerAnnen = "werwer",
                        saksnummer = "werwer",
                        artikkel48 = "0"
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
                // MockDataMedMeldemskap
                medlemskap= medlemskap,
                medlemskapAnnen= medlemskapAnnen,
                medlemskapTotal= medlemskapTotal,
                trygdetid= trygdetid
        )
    }

}

//P5000 - bekreftforsikred
fun createMedlemskapMock(): Pensjon {

    return Pensjon(
            sak = Sak(
                    enkeltkrav = KravtypeItem(krav = "10")
            ),
            medlemskap = listOf(
                    MedlemskapItem(
                            land = "DK",
                            ordning = "01",
                            type = "10",
                            relevans = "100",
                            gyldigperiode = "1",
                            beregning = "100",
                            periode = Periode(
                                    fom = "2000-01-01",
                                    tom = "2010-01-01"
                            ),
                            sum = TotalSum(
                                    aar = "4",
                                    dager = Dager(nr = "2"),
                                    maaneder = "2"
                            )
                    )
            ),
            medlemskapAnnen = listOf(
                    MedlemskapItem(
                            land = "DE",
                            type = "21",
                            ordning = "01",
                            relevans = "100",
                            beregning = "100",
                            sum = TotalSum(
                                    aar = "4",
                                    maaneder = "2",
                                    dager = Dager(nr = "5")
                            )

                    )
            ),
            medlemskapTotal = listOf(
                    MedlemskapItem(
                            type = "10",
                            relevans = "100",
                            sum = TotalSum(
                                    aar = "11",
                                    maaneder = "1",
                                    dager = Dager(nr = "6")
                            )
                    )
            ),
            trygdetid = listOf(
                    MedlemskapItem(
                            type = "11",
                            sum = TotalSum(
                                    aar = "10",
                                    maaneder = "2",
                                    dager = Dager(nr = "5")
                            )
                    )
            )
    )
}