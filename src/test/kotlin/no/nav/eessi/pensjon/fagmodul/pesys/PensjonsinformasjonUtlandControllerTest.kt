package no.nav.eessi.pensjon.fagmodul.pesys

import com.google.api.gax.paging.Page
import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.SedMetadata
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.fagmodul.eux.EuxInnhentingService
import no.nav.eessi.pensjon.fagmodul.pesys.SED_RETNING.*
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.kodeverk.Postnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals

class PensjonsinformasjonUtlandControllerTest {

    private val gcpStorage = mockk<Storage>(relaxed = true)
    private val kodeverkClient = mockk<KodeverkClient>(relaxed = true)
    private val euxInnhentingService = mockk<EuxInnhentingService>(relaxed = true)
    private val trygdeTidService = TrygdeTidService(euxInnhentingService, kodeverkClient)
    private val gcpStorageService = GcpStorageService(
        "_",
        "_",
        "_",
        "pesys",
        gcpStorage
    )

    private val penInfoUtlandService = PensjonsinformasjonUtlandService(
        mockk(),
        mockk(),
        euxInnhentingService,
        kodeverkClient
    )

    private val controller = PensjonsinformasjonUtlandController(
        penInfoUtlandService = penInfoUtlandService,
        gcpStorageService = gcpStorageService,
        euxInnhentingService,
        trygdeTidService = trygdeTidService
    )
    private val aktoerId1 = "2477958344057"
    private val rinaNr = 1446033

    @BeforeEach
    fun setup() {
        every { kodeverkClient.finnLandkode("NO") } returns "NOR"
        every { kodeverkClient.finnLandkode("CY") } returns "CYR"
        every { kodeverkClient.finnLandkode("BG") } returns "BGD"
        every { kodeverkClient.finnLandkode("HR") } returns "HRD"
    }

    @Test
    fun `gitt en aktørid og rinanr som matcher trygdetid i gcp saa skal denne returneres`() {
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { name } returns "${aktoerId1}___PESYS___$rinaNr"
            every { getContent() } returns trygdeTidGCP().toJson().toByteArray()
        }
        mockGcpListeSok(listOf("$rinaNr"))

        val result = controller.hentTrygdetid(TrygdetidRequest(fnr = aktoerId1, rinaNr = rinaNr))
        println(result.toJson())
        assertEquals(aktoerId1, result.fnr)
        assertEquals(trygdeTidListResultat(), result.trygdetid.toString())
    }

    /**
     * Trygdetid som henter
     **/
    private fun trygdeTidGCP() =
            """
            [
              {"land":"HR","acronym":"NAVAT05","type":"10","startdato":"2016-06-01","sluttdato":"2016-06-22","aar":"","mnd":"","dag":"21","dagtype":"7","ytelse":"001","ordning":"00","beregning":"111"},
              {"land":"NO","acronym":"NAVAT07","type":"21","startdato":"2010-10-31","sluttdato":"2010-12-01","aar":"","mnd":"1","dag":"2","dagtype":"7","ytelse":"111","ordning":"00","beregning":"111"},
              {"land":"CY","acronym":"NAVAT05","type":"10","startdato":"2016-01-01","sluttdato":"2018-02-28","aar":"2","mnd":"2","dag":"","dagtype":"7","ytelse":"","ordning":"","beregning":"111"},
              {"land":"BG","acronym":"NAVAT05","type":"10","startdato":"2020-01-01","sluttdato":"2024-08-15","aar":"","mnd":"3","dag":"","dagtype":"7","ytelse":"001","ordning":"00","beregning":"001"},
              {"land":"NO","acronym":"NAVAT07","type":"41","startdato":"2023-05-01","sluttdato":"2023-05-31","aar":"","mnd":"1","dag":"","dagtype":"7","ytelse":"","ordning":"","beregning":""}
            ]
            """.trimIndent()

    private fun trygdeTidListResultat() =
        ("[Trygdetid(land=NOR, acronym=NAVAT07, type=21, startdato=2010-10-31, sluttdato=2010-12-01, aar=null, mnd=1, dag=2, dagtype=7, ytelse=111, ordning=00, beregning=111), " +
                "Trygdetid(land=CYR, acronym=NAVAT05, type=10, startdato=2016-01-01, sluttdato=2018-02-28, aar=2, mnd=2, dag=null, dagtype=7, ytelse=null, ordning=null, beregning=111), " +
                "Trygdetid(land=HRD, acronym=NAVAT05, type=10, startdato=2016-06-01, sluttdato=2016-06-22, aar=null, mnd=null, dag=21, dagtype=7, ytelse=001, ordning=00, beregning=111), " +
                "Trygdetid(land=BGD, acronym=NAVAT05, type=10, startdato=2020-01-01, sluttdato=2024-08-15, aar=null, mnd=3, dag=null, dagtype=7, ytelse=001, ordning=00, beregning=001), " +
                "Trygdetid(land=NOR, acronym=NAVAT07, type=41, startdato=2023-05-01, sluttdato=2023-05-31, aar=null, mnd=1, dag=null, dagtype=7, ytelse=null, ordning=null, beregning=null)]").trimMargin()

    @Test
    fun `gitt en samlet periode med flag fra gcp saa skal ogsaa denne hente ut trygdetid`() {
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { name } returns "${aktoerId1}___PESYS___$rinaNr"
            every { getContent() } returns trygdeTidSamletJson().toJson().toByteArray()
        }

        mockGcpListeSok(listOf("$rinaNr"))

        val result = controller.hentTrygdetid(TrygdetidRequest(fnr = aktoerId1))
        val forventertResultat = "[Trygdetid(land=, acronym=NAVAT05, type=10, startdato=1995-01-01, sluttdato=1995-12-31, aar=1, mnd=0, dag=1, dagtype=7, ytelse=111, ordning=null, beregning=111)]"
        assertEquals(aktoerId1, result.fnr)
        assertEquals(forventertResultat, result.trygdetid.toString())
    }

    @Test
    fun `gitt en aktorid tilknyttet flere buc saa skal den gi en liste med flere trygdetider`() {
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns trygdeTidSamletJson().toJson().toByteArray()
            every { name } returns "${aktoerId1}___PESYS___111111" andThen "${aktoerId1}___PESYS___222222"
        }

        mockGcpListeSok(listOf("111111", "222222"))

        val result = controller.hentTrygdetid(TrygdetidRequest(fnr = aktoerId1))
        assertEquals(aktoerId1, result.fnr)
        assertEquals(trygdeTidForFlereBuc(), result.toJson())
    }

    @Test
    fun `gitt en pesysId som finnes i gcp saa skal sedene hentes fra Rina`() {
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns p6000Detaljer(listOf("1111")).toByteArray()
        }
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser(any(), any()) } returns hentTestP6000("P6000-RINA.json")
        every { euxInnhentingService.hentSedMetadata(any(), "1111") } returns sedMetadata()

        val result = controller.hentP6000Detaljer("22975052")

        assertEquals("ALDER", result.sakstype)
        assertEquals("æøå", result.innehaver.etternavn)
        assertEquals("æøå", result.forsikrede.fornavn)
    }

    @Test
    fun `Gitt at vi har en innvilget norsk pensjon men resultat mangler og beloepbrutto er med i p6000 så skal vi returnere det som en innvilget norsk pensjon med beloep`() {
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns p6000Detaljer(listOf("1111")).toByteArray()
        }
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "1111") } returns hentTestP6000("P6000-InnvilgedePensjonerUtenResultat.json")
        every { euxInnhentingService.hentSedMetadata("1446704", "1111") } returns sedMetadata()

        val result = controller.hentP6000Detaljer("22975052")
        with(result) {
            assertEquals("GJENLEVENDE", sakstype)
            assertEquals(1, innvilgedePensjoner.size)
            assertEquals("9174", innvilgedePensjoner.firstOrNull()?.bruttobeloep)
        }
    }

    @Test
    fun `Gitt at vi en innvilget norsk og et avslag fra GB så skal innvilget norsk og avslått gb returneres`() {
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns p6000Detaljer(listOf("1111", "2222")).toByteArray()
        }

        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "2222") } returns hentTestP6000("P6000-InnvilgetPensjonNO.json")
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "1111") } returns hentTestP6000("P6000-AvslaattePensjonerUtlandGB.json")

        every { euxInnhentingService.hentSedMetadata("1446704", "2222") } returns sedMetadata()
        every { euxInnhentingService.hentSedMetadata("1446704", "1111") } returns sedMetadata(RECEIVED)
        every { kodeverkClient.hentPostSted(any()) } returns Postnummer("0607", "Oslo")

        val result = controller.hentP6000Detaljer("22975052")

        with(result) {
            assertEquals("UFORE", sakstype)
            println("innehaver: ${innehaver.toJson()}")
            assertEquals("ROSA", forsikrede.fornavn)
            assertEquals(null, innehaver.etternavn)

            assertEquals(1, innvilgedePensjoner.size)
            assertEquals(1, avslaattePensjoner.size)
            assertEquals("[EessisakItemP1(institusjonsid=NO:889640782, institusjonsnavn=The Norwegian Labour and Welfare Administration, saksnummer=25814615, land=NO, identifikatorForsikrede=04117512849, identifikatorInnehaver=null)]", innvilgedePensjoner[0].institusjon.toString())
            assertEquals("[]", avslaattePensjoner[0].institusjon.toString())
        }
    }

    @Test
    fun `Gitt at vi får fler enn en innvilget pensjon fra Norge men at den andre er fra DE saa skal vi levere ut P1Dto med DE sin institusjon`() {
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns p6000Detaljer(listOf("1111", "2222")).toByteArray()
        }
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "1111") } returns hentTestP6000("P6000-InnvilgedePensjonerUtlandOgInnland.json")
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "2222") } returns hentTestP6000("P6000-InnvilgedetPensjonNO.json")

        every { euxInnhentingService.hentSedMetadata("1446704", "1111") } returns sedMetadata(RECEIVED)
        every { euxInnhentingService.hentSedMetadata("1446704", "2222") } returns sedMetadata()


        val result = controller.hentP6000Detaljer("22975052")
        println("resultat: ${result.toJson()}")
        with(result) {
            assertEquals("GJENLEVENDE", sakstype)
            assertEquals("AKROBAT", innehaver.etternavn)
            assertEquals("ROSA", forsikrede.fornavn)

            assertEquals(2, innvilgedePensjoner.size)
            assertEquals("[EessisakItemP1(institusjonsid=DEEEEEEE, institusjonsnavn=Tysker, saksnummer=null, land=DE, identifikatorForsikrede=06448422184, identifikatorInnehaver=16888697822)]", innvilgedePensjoner[0].institusjon.toString())
            assertEquals("[EessisakItemP1(institusjonsid=NO:NAVAT07, institusjonsnavn=NAV ACCEPTANCE TEST 07, saksnummer=1003563, land=NO, identifikatorForsikrede=06448422184, identifikatorInnehaver=16888697822)]", innvilgedePensjoner[1].institusjon.toString())
        }
    }

    @Test
    fun `Gitt at vi får fler enn en innvilget pensjon fra Norge men at den andre mangler institusjon saa skal vi levere ut P1Dto med én institusjon`() {
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns p6000Detaljer(listOf("1111", "2222")).toByteArray()
        }
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "1111") } returns hentTestP6000("P6000-InnvilgedePensjonerUtenInstitusjon.json")
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "2222") } returns hentTestP6000("P6000-InnvilgedetPensjonNO.json")

        every { euxInnhentingService.hentSedMetadata(any(), "1111") } returns sedMetadata()
        every { euxInnhentingService.hentSedMetadata(any(), "2222") } returns sedMetadata()

        val result = controller.hentP6000Detaljer("22975052")
        with(result){
            assertEquals("GJENLEVENDE", sakstype)
            assertEquals("AKROBAT", innehaver.etternavn)
            assertEquals("ROSA", forsikrede.fornavn)
            assertEquals(1, innvilgedePensjoner.size)
            assertEquals("[EessisakItemP1(institusjonsid=NO:NAVAT07, institusjonsnavn=NAV ACCEPTANCE TEST 07, saksnummer=1003563, land=NO, identifikatorForsikrede=06448422184, identifikatorInnehaver=16888697822)]", innvilgedePensjoner[0].institusjon.toString())
        }
    }


    @Test
    fun `Gitt to innvilgede pensjoner en fra norge og en fra tyskland saa skal begge taes med inkl pin fra begge land`() {
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns p6000Detaljer(listOf("1111", "2222")).toByteArray()
        }
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "2222") } returns hentTestP6000("P6000-InnvilgedePensjonerDEogNorsk.json")
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "1111") } returns hentTestP6000("P6000-InnvilgedePensjonerNorskOgDE.json")

        every { euxInnhentingService.hentSedMetadata("1446704", "2222") } returns sedMetadata(RECEIVED)
        every { euxInnhentingService.hentSedMetadata("1446704", "1111") } returns sedMetadata()

        val result = controller.hentP6000Detaljer("22975052")
        with(result){
            assertEquals(2, innvilgedePensjoner.size)
            assertEquals("[EessisakItemP1(institusjonsid=NO:NAVAT07, institusjonsnavn=NAV ACCEPTANCE TEST 07, saksnummer=1003563, land=NO, identifikatorForsikrede=06448422184, identifikatorInnehaver=null)]", innvilgedePensjoner[0].institusjon.toString())
            assertEquals("[EessisakItemP1(institusjonsid=DE:111111, institusjonsnavn=Deutsche Bayersche Rentenversicherung, saksnummer=null, land=DE, identifikatorForsikrede=06448422184, identifikatorInnehaver=null)]", innvilgedePensjoner[1].institusjon.toString())

        }
    }

    @Test
    fun `Gitt to innvilget pensjoner men en fra norge og en fra tyskland saa skal begge taes med inkl pin fra begge land selv om vi mangler tilleggsinformasjon`() {
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns p6000Detaljer(listOf("1111", "2222")).toByteArray()
        }
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "2222") } returns hentTestP6000("P6000-InnvilgedePensjonerDEogNorsk.json")
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "1111") } returns hentTestP6000("P6000-InnvilgedePensjonerDEogNorskUtenTillegg.json")

        every { euxInnhentingService.hentSedMetadata("1446704", "1111") } returns sedMetadata(RECEIVED)
        every { euxInnhentingService.hentSedMetadata("1446704", "2222") } returns sedMetadata(RECEIVED)

        val result = controller.hentP6000Detaljer("22975052")
        with(result) {
            assertEquals(2, innvilgedePensjoner.size)
            assertEquals("[]", innvilgedePensjoner[0].institusjon.toString())
            assertEquals("[EessisakItemP1(institusjonsid=DE:111111, institusjonsnavn=Deutsche Bayersche Rentenversicherung, saksnummer=null, land=DE, identifikatorForsikrede=06448422184, identifikatorInnehaver=null)]", innvilgedePensjoner[1].institusjon.toString())
        }
    }

    @Test
    fun `Gitt at vi får fler enn en innvilget pensjon fra Norge men den andre mangler adresseNyvurdering dermed returneres kun den ene norske med andreinstitusjoner oppgitt `() {
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns p6000Detaljer().toByteArray()
        }
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "b152e3cf041a4b829e56e6b1353dd8cb") } returns hentTestP6000("P6000-InnvilgedePensjonSomManglerAdresseNyVurdering.json")
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "a6bacca841cf4c7195d694729151d4f3") } returns hentTestP6000("P6000-InnvilgedePensjoner.json")

        every { euxInnhentingService.hentSedMetadata(any(), any()) } returns sedMetadata()

        val result = controller.hentP6000Detaljer("22975052")
        println("resultat: ${result.toJson()}")

        assertEquals(1, result.innvilgedePensjoner.size)
        assertEquals("[EessisakItemP1(institusjonsid=NO:NAVAT07, institusjonsnavn=NAV ACCEPTANCE TEST 07, saksnummer=1003563, land=NO, identifikatorForsikrede=06448422184, identifikatorInnehaver=16888697822)]", result.innvilgedePensjoner[0].institusjon.toString())
    }

    @Test
    fun `Gitt at vi får ingen innvilget pensjon fra Norge men har adresseNyvurdering saa skal vi levere ut institusjon fra  adresseNyvurdering`() {
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns p6000Detaljer(listOf("11111")).toByteArray()
        }
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "11111") } returns hentTestP6000("P6000-InnvilgedePensjonerUtenInstitusjon.json")
        every { euxInnhentingService.hentSedMetadata(any(), any()) } returns sedMetadata()


        val result = controller.hentP6000Detaljer("22975052")
        with(result){
            assertEquals(1, innvilgedePensjoner.size)
            assertEquals("[EessisakItemP1(institusjonsid=NO:NAVAT07, institusjonsnavn=NAV ACCEPTANCE TEST 07, saksnummer=null, land=NO, identifikatorForsikrede=06448422184, identifikatorInnehaver=16888697822)]", innvilgedePensjoner[0].institusjon.toString())
        }
    }


    @Test
    fun `Gitt at vi skal hente opp P6000 for P1 saa skal vi returnere P1Dto med innvilgede pensjoner`() {
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns p6000Detaljer(listOf("1111")).toByteArray()
        }
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser(any(), "1111") } returns hentTestP6000("P6000-InnvilgedePensjoner.json")
        every { euxInnhentingService.hentSedMetadata(any(), any()) } returns sedMetadata()


        val p6000Detaljer = controller.hentP6000Detaljer("22975052")
        assertEquals("[]", p6000Detaljer.avslaattePensjoner.toString())

        with(p6000Detaljer) {
            assertEquals("ROSA", forsikrede.fornavn)
            assertEquals("AKROBAT", innehaver.etternavn)

            assertEquals("GJENLEVENDE", sakstype)
            assertEquals(1, innvilgedePensjoner.size)
        }

        val innvilgetPensjon = p6000Detaljer.innvilgedePensjoner.first()
        with(innvilgetPensjon) {
            assertEquals("03", pensjonstype)
            assertEquals("9174", bruttobeloep)
            assertEquals(null, grunnlagInnvilget)
            assertEquals("2025-02-05", vedtaksdato)
            assertEquals(null, reduksjonsgrunnlag)
            assertEquals("six weeks from the date the decision is received", vurderingsperiode)
        }
    }

    @Test
    fun `Gitt to norske avslaatte pensjoner og en fra utlandet saa det gi i én norsk og én utenlandsk`() {
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns p6000Detaljer(listOf("3333", "1111", "2222")).toByteArray()
        }
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "1111") } returns hentTestP6000("P6000-AvslaattPensjonNO.json")
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "2222") } returns hentTestP6000("P6000-AvslaattPensjonNO2.json")
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "3333") } returns hentTestP6000("P6000-AvslaattePensjonerUtland.json")

        every { euxInnhentingService.hentSedMetadata("1446704", "1111") } returns sedMetadata(SENT)
        every { euxInnhentingService.hentSedMetadata("1446704", "2222") } returns sedMetadata(SENT)
        every { euxInnhentingService.hentSedMetadata("1446704", "3333") } returns sedMetadata(RECEIVED)


        val p6000Detaljer = controller.hentP6000Detaljer("22975052")

        with(p6000Detaljer) {
            assertEquals("Gjenlevende", sakstype)
            assertEquals("ROSA", forsikrede.fornavn)
            assertEquals("AKROBAT", innehaver.etternavn)
            assertEquals(0, innvilgedePensjoner.size)
            assertEquals(2, avslaattePensjoner.size)
        }

        val avslaattePensjoner = p6000Detaljer.avslaattePensjoner
        with(avslaattePensjoner) {
            assertEquals(2, avslaattePensjoner.size)
            //Det nyeste avslaget fra Norge
            assertEquals("2025-10-05", avslaattePensjoner.firstOrNull()?.vedtaksdato)
            assertEquals("[EessisakItemP1(institusjonsid=NO:NAVAT07, institusjonsnavn=NAV ACCEPTANCE TEST 07, saksnummer=1003563, land=NO, identifikatorForsikrede=06448422184, identifikatorInnehaver=16888697822)]", avslaattePensjoner.firstOrNull()?.institusjon.toString())
            //Avslått pensjon fra Tyskland
            assertEquals("2025-02-05", avslaattePensjoner.last().vedtaksdato)
            assertEquals("[EessisakItemP1(institusjonsid=DE:DEUTCHE, institusjonsnavn=Tysk Inst, saksnummer=null, land=DE, identifikatorForsikrede=06448422184, identifikatorInnehaver=16888697822)]", avslaattePensjoner.last().institusjon.toString())
        }
    }

    @Test
    fun `Gitt en avslaatt pensjon fra utland og en norsk innvilget pensjon`() {
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns p6000Detaljer(listOf("1111", "2222")).toByteArray()
        }
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "1111") } returns hentTestP6000("p6000JsonNo.json")
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "2222") } returns hentTestP6000("P6000JsonUTL.json")

        every { euxInnhentingService.hentSedMetadata("1446704", "1111") } returns sedMetadata(SENT)
        every { euxInnhentingService.hentSedMetadata("1446704", "2222") } returns sedMetadata(RECEIVED)


        val p6000Detaljer = controller.hentP6000Detaljer("22975052")

        with(p6000Detaljer) {
            assertEquals("UFORE", sakstype)
            assertEquals(1, innvilgedePensjoner.size)
            assertEquals(1, avslaattePensjoner.size)
        }

        val avslaattePensjoner = p6000Detaljer.avslaattePensjoner
        with(avslaattePensjoner) {
            assertEquals(1, avslaattePensjoner.size)
            assertEquals("[EessisakItemP1(institusjonsid=IS:6602692669, institusjonsnavn=Tryggingastofnun rikisins, saksnummer=not known, land=IS, identifikatorForsikrede=not known, identifikatorInnehaver=null)]", avslaattePensjoner.firstOrNull()?.institusjon.toString())
            //Avslått pensjon fra Tyskland
        }
    }

    @Test
    fun `Gitt en norsk avslaatt utenlandsk pensjon og en norsk avslaatt pensjon saa skal det gis ut én norsk og én utenlandsk`() {
        every { gcpStorage.get(any<BlobId>()) } returns mockk<Blob>().apply {
            every { exists() } returns true
            every { getContent() } returns p6000Detaljer(listOf("3333", "1111", "2222")).toByteArray()
        }
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "1111") } returns hentTestP6000("P6000-AvslaattPensjonNO.json")
        every { euxInnhentingService.getSedOnBucByDocumentIdAsSystemuser("1446704", "3333") } returns hentTestP6000("P6000-AvslaattePensjonerUtlandMedUTLInst.json")

        every { euxInnhentingService.hentSedMetadata("1446704", "1111") } returns sedMetadata(SENT)
        every { euxInnhentingService.hentSedMetadata("1446704", "3333") } returns sedMetadata(RECEIVED)


        val p6000Detaljer = controller.hentP6000Detaljer("22975052")

        with(p6000Detaljer) {
            assertEquals("GJENLEVENDE", sakstype)
            assertEquals("ROSA", forsikrede.fornavn)
            assertEquals("AKROBAT", innehaver.etternavn)

            assertEquals(0, innvilgedePensjoner.size)
        }

        val avslaattePensjoner = p6000Detaljer.avslaattePensjoner
        with(avslaattePensjoner) {
            assertEquals(2, avslaattePensjoner.size)
            //Det nyeste avslaget fra Norge
            assertEquals("2025-10-05", avslaattePensjoner.firstOrNull()?.vedtaksdato)
            assertEquals("[EessisakItemP1(institusjonsid=NO:NAVAT07, institusjonsnavn=NAV ACCEPTANCE TEST 07, saksnummer=1003563, land=NO, identifikatorForsikrede=06448422184, identifikatorInnehaver=16888697822)]", avslaattePensjoner.firstOrNull()?.institusjon.toString())
            //Avslått pensjon fra Tyskland
            assertEquals("[EessisakItemP1(institusjonsid=DE:DEUTCHE, institusjonsnavn=Tysk Inst, saksnummer=88888, land=DE, identifikatorForsikrede=06448422184, identifikatorInnehaver=16888697822)]", avslaattePensjoner.last().institusjon.toString())
        }
    }


    @Nested
    @DisplayName("Feilsituasjoner")
    inner class Feilsituasjoner {
        @Test
        fun `det skal kastes 404 exception ved manglende innvilget og avslaatt pensjon`() {
            mockGcpListeSok(emptyList())
            val exception = assertThrows<org.springframework.web.server.ResponseStatusException> {
                controller.hentP6000Detaljer("22975052")
            }
            assertEquals("404 NOT_FOUND \"Ingen P6000-detaljer funnet for pesysId: 22975052\"", exception.message)
            assertEquals("Ingen P6000-detaljer funnet for pesysId: 22975052", exception.reason!!)
        }
    }

    private fun mockGcpListeSok(rinaNrList: List<String>) {
        val blobs = rinaNrList.map { rinaNr ->
            val blob = mockk<Blob>(relaxed = true)
            every { blob.name } returns "${aktoerId1}___PESYS___$rinaNr"
            blob
        }
        val page = mockk<Page<Blob>>(relaxed = true)
        every { page.iterateAll() } returns blobs
        every { gcpStorage.list(any<String>(), *anyVararg()) } returns page
    }

    private fun p6000Detaljer(sedliste: List<String> ? = null) : String {
        val seds = sedliste?: listOf("b152e3cf041a4b829e56e6b1353dd8cb", "a6bacca841cf4c7195d694729151d4f3")
        val est = """
        {
          "pesysId" : "22975052",
          "rinaSakId" : "1446704",
          "dokumentId" : [ ${seds.joinToString(separator = ",") { "\"$it\"" } }]
        }
    """.trimIndent()
        println(est)
        return est
    }

    private fun trygdeTidForFlereBuc(): String {
        return """
            {
              "fnr" : "2477958344057",
              "trygdetid" : [ {
                "land" : "",
                "acronym" : "NAVAT05",
                "type" : "10",
                "startdato" : "1995-01-01",
                "sluttdato" : "1995-12-31",
                "aar" : "1",
                "mnd" : "0",
                "dag" : "1",
                "dagtype" : "7",
                "ytelse" : "111",
                "ordning" : null,
                "beregning" : "111"
              }, {
                "land" : "",
                "acronym" : "NAVAT05",
                "type" : "10",
                "startdato" : "1995-01-01",
                "sluttdato" : "1995-12-31",
                "aar" : "1",
                "mnd" : "0",
                "dag" : "1",
                "dagtype" : "7",
                "ytelse" : "111",
                "ordning" : null,
                "beregning" : "111"
              } ],
              "error" : null
            }
        """.trimIndent()

    }

    private fun trygdeTidSamletJson(): String {
        val trygdetidList = """
            [
                {"flag":true,"land":"GB","acronym":"NAVAT05","type":"10","startdato":"1995-01-01","sluttdato":"1995-12-31","aar":"1","mnd":"0","dag":"1","dagtype":"7","ytelse":"111","ordning":"","beregning":"111","hasSubrows":true,"flagLabel":"OBS! Periodesum er mindre enn registrert periode"}
            ]
            """.trimIndent()
        return trygdetidList
    }

    private fun hentTestP6000(filnavn: String): SED {
        return javaClass.getResource("/json/sed/$filnavn")?.readText()?.let { json -> mapJsonToAny<P6000>(json) }!!
    }

    private fun sedMetadata(status: SED_RETNING? = SENT) = SedMetadata(
        sedTittel = "Vedtak om pensjon",
        sedType = "P6000",
        sedId = "a6bacca841cf4c7195d694729151d4f3",
        status = status?.name
    )
}

