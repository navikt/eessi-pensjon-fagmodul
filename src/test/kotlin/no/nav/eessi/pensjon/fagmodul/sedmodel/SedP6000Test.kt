package no.nav.eessi.pensjon.fagmodul.sedmodel

import no.nav.eessi.pensjon.fagmodul.models.SEDType
import no.nav.eessi.pensjon.utils.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class SedP6000Test {

    @Test
    fun createP6000sed() {
        val sed6000 = populerP6000()
        assertNotNull(sed6000)

        val json = sed6000.toJson()
        assertNotNull(json)

        val pensjondata = mapJsonToAny(json, typeRefs<P6000>())
        assertNotNull(pensjondata)
        assertEquals(sed6000.toJson(), pensjondata.toJson())

        val path = Paths.get("src/test/resources/json/nav/P6000-NAV.json")
        val p6000file = String(Files.readAllBytes(path))
        assertNotNull(p6000file)
        validateJson(p6000file)

        //map vedtak-NAV back to vedtak object.
        val pensjondataFile = mapJsonToAny(p6000file, typeRefs<SED>())

        assertNotNull(pensjondataFile)
        mapAnyToJson(pensjondataFile, true)
    }

    @Test
    fun `create part json and validate to object`() {
        val sed6000 = populerP6000()
        assertNotNull(sed6000)

        val p6000Pensjon = sed6000.p6000Pensjon
        val p6000PensjonDeserialisert = mapJsonToAny(mapAnyToJson(p6000Pensjon), typeRefs<P6000Pensjon>())
        assertNotNull(p6000PensjonDeserialisert)
        assertEquals(p6000Pensjon.toJson(), p6000PensjonDeserialisert.toJson())
    }

    fun populerP6000(): P6000 {
        return P6000(
            type = SEDType.P6000,
            p6000Pensjon = P6000Pensjon(
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
                            etternavnvedfoedsel = "sdfsd"
                        )
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
                            by = "Testaeveisgiverby2"
                        ),
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
                        virkningsdato = "2020-10-01",
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
                        )
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
                        basertPaaAnnen = "wertwertwert",
                        type = "03",
                        resultat = "03",
                        ukjent = Ukjent(
                            beloepBrutto = BeloepBrutto(
                                ytelseskomponentAnnen = "dsfsdf\\ns\\ndf\\nsdf"
                            )
                        ),
                        virkningsdato = "2030-10-01",
                        basertPaa = "01"
                    )
                ),
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
                )
            )
        )
    }

}
