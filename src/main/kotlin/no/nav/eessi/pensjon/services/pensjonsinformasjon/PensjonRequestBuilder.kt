package no.nav.eessi.pensjon.services.pensjonsinformasjon

//@Component
//class PensjonRequestBuilder {
//
//    private final val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
//    private final val documentBuilder = documentBuilderFactory.newDocumentBuilder()
//
//    private val fullRequestDocument: Document
//
//    init {
//        fullRequestDocument = documentBuilder.parse(ResourceUtils.getURL("classpath:pensjonsinformasjon/v1/v1.Pensjonsinformasjon.xsd").openStream())
//    }
//
//    fun requestBodyForVedtakFromAString() =
//            """
//                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
//                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns="http://nav.no/pensjon/v1/pensjonsinformasjon" xmlns:avdod="http://nav.no/pensjon/v1/avdod" xmlns:brukersBarnListe="http://nav.no/pensjon/v1/brukersBarnListe" xmlns:brukersSakerListe="http://nav.no/pensjon/v1/brukersSakerListe" xmlns:ektefellePartnerSamboerListe="http://nav.no/pensjon/v1/ektefellePartnerSamboerListe" xmlns:inngangOgEksport="http://nav.no/pensjon/v1/inngangOgEksport" xmlns:kravHistorikkListe="http://nav.no/pensjon/v1/kravHistorikkListe" xmlns:person="http://nav.no/pensjon/v1/person" xmlns:sakAlder="http://nav.no/pensjon/v1/sakAlder" xmlns:trygdeavtale="http://nav.no/pensjon/v1/trygdeavtale" xmlns:trygdetidAvdodFarListe="http://nav.no/pensjon/v1/trygdetidAvdodFarListe" xmlns:trygdetidAvdodListe="http://nav.no/pensjon/v1/trygdetidAvdodListe" xmlns:trygdetidAvdodMorListe="http://nav.no/pensjon/v1/trygdetidAvdodMorListe" xmlns:trygdetidListe="http://nav.no/pensjon/v1/trygdetidListe" xmlns:vedtak="http://nav.no/pensjon/v1/vedtak" xmlns:vilkarsvurderingListe="http://nav.no/pensjon/v1/vilkarsvurderingListe" xmlns:ytelsePerMaanedListe="http://nav.no/pensjon/v1/ytelsePerMaanedListe" attributeFormDefault="unqualified" elementFormDefault="qualified" targetNamespace="http://nav.no/pensjon/v1/pensjonsinformasjon">
//                    <xs:import namespace="http://nav.no/pensjon/v1/ytelsePerMaanedListe" schemaLocation="v1.YtelsePerMaanedListe.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/vilkarsvurderingListe" schemaLocation="v1.VilkarsvurderingListe.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/vedtak" schemaLocation="v1.Vedtak.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/trygdetidListe" schemaLocation="v1.TrygdetidListe.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/trygdetidAvdodMorListe" schemaLocation="v1.TrygdetidAvdodMorListe.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/trygdetidAvdodListe" schemaLocation="v1.TrygdetidAvdodListe.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/trygdetidAvdodFarListe" schemaLocation="v1.TrygdetidAvdodFarListe.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/trygdeavtale" schemaLocation="v1.Trygdeavtale.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/sakAlder" schemaLocation="v1.SakAlder.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/person" schemaLocation="v1.Person.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/inngangOgEksport" schemaLocation="v1.InngangOgEksport.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/avdod" schemaLocation="v1.Avdod.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/brukersSakerListe" schemaLocation="v1.BrukersSakerListe.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/brukersBarnListe" schemaLocation="v1.BrukersBarnListe.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/kravHistorikkListe" schemaLocation="v1.KravHistorikkListe.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/ektefellePartnerSamboerListe" schemaLocation="v1.EktefellePartnerSamboerListe.xsd"/>
//                    <xs:element name="pensjonsinformasjon" type="Pensjonsinformasjon"/>
//                    <xs:complexType name="Pensjonsinformasjon">
//                        <xs:all>
//                            <xs:element minOccurs="0" name="avdod" type="avdod:v1.Avdod"/>
//                            <xs:element minOccurs="0" name="inngangOgEksport" type="inngangOgEksport:v1.InngangOgEksport"/>
//                            <xs:element minOccurs="0" name="person" type="person:v1.Person"/>
//                            <xs:element minOccurs="0" name="sakAlder" type="sakAlder:v1.SakAlder"/>
//                            <xs:element minOccurs="0" name="trygdeavtale" type="trygdeavtale:v1.Trygdeavtale"/>
//                            <xs:element minOccurs="0" name="trygdetidAvdodFarListe" type="trygdetidAvdodFarListe:v1.TrygdetidAvdodFarListe"/>
//                            <xs:element minOccurs="0" name="trygdetidAvdodListe" type="trygdetidAvdodListe:v1.TrygdetidAvdodListe"/>
//                            <xs:element minOccurs="0" name="trygdetidAvdodMorListe" type="trygdetidAvdodMorListe:v1.TrygdetidAvdodMorListe"/>
//                            <xs:element minOccurs="0" name="trygdetidListe" type="trygdetidListe:v1.TrygdetidListe"/>
//                            <xs:element minOccurs="0" name="vedtak" type="vedtak:v1.Vedtak"/>
//                            <xs:element minOccurs="0" name="vilkarsvurderingListe" type="vilkarsvurderingListe:v1.VilkarsvurderingListe"/>
//                            <xs:element minOccurs="0" name="ytelsePerMaanedListe" type="ytelsePerMaanedListe:v1.YtelsePerMaanedListe"/>
//                        </xs:all>
//                    </xs:complexType>
//                </xs:schema>
//            """.trimIndent()
//
//
//    fun requestBodyForSakslisteFromAString() =
//            """
//                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
//                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns="http://nav.no/pensjon/v1/pensjonsinformasjon" xmlns:avdod="http://nav.no/pensjon/v1/avdod" xmlns:brukersBarnListe="http://nav.no/pensjon/v1/brukersBarnListe" xmlns:brukersSakerListe="http://nav.no/pensjon/v1/brukersSakerListe" xmlns:ektefellePartnerSamboerListe="http://nav.no/pensjon/v1/ektefellePartnerSamboerListe" xmlns:inngangOgEksport="http://nav.no/pensjon/v1/inngangOgEksport" xmlns:kravHistorikkListe="http://nav.no/pensjon/v1/kravHistorikkListe" xmlns:person="http://nav.no/pensjon/v1/person" xmlns:sakAlder="http://nav.no/pensjon/v1/sakAlder" xmlns:trygdeavtale="http://nav.no/pensjon/v1/trygdeavtale" xmlns:trygdetidAvdodFarListe="http://nav.no/pensjon/v1/trygdetidAvdodFarListe" xmlns:trygdetidAvdodListe="http://nav.no/pensjon/v1/trygdetidAvdodListe" xmlns:trygdetidAvdodMorListe="http://nav.no/pensjon/v1/trygdetidAvdodMorListe" xmlns:trygdetidListe="http://nav.no/pensjon/v1/trygdetidListe" xmlns:vedtak="http://nav.no/pensjon/v1/vedtak" xmlns:vilkarsvurderingListe="http://nav.no/pensjon/v1/vilkarsvurderingListe" xmlns:ytelsePerMaanedListe="http://nav.no/pensjon/v1/ytelsePerMaanedListe" attributeFormDefault="unqualified" elementFormDefault="qualified" targetNamespace="http://nav.no/pensjon/v1/pensjonsinformasjon">
//                    <xs:import namespace="http://nav.no/pensjon/v1/ytelsePerMaanedListe" schemaLocation="v1.YtelsePerMaanedListe.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/vilkarsvurderingListe" schemaLocation="v1.VilkarsvurderingListe.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/vedtak" schemaLocation="v1.Vedtak.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/trygdetidListe" schemaLocation="v1.TrygdetidListe.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/trygdetidAvdodMorListe" schemaLocation="v1.TrygdetidAvdodMorListe.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/trygdetidAvdodListe" schemaLocation="v1.TrygdetidAvdodListe.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/trygdetidAvdodFarListe" schemaLocation="v1.TrygdetidAvdodFarListe.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/trygdeavtale" schemaLocation="v1.Trygdeavtale.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/sakAlder" schemaLocation="v1.SakAlder.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/person" schemaLocation="v1.Person.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/inngangOgEksport" schemaLocation="v1.InngangOgEksport.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/avdod" schemaLocation="v1.Avdod.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/brukersSakerListe" schemaLocation="v1.BrukersSakerListe.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/brukersBarnListe" schemaLocation="v1.BrukersBarnListe.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/kravHistorikkListe" schemaLocation="v1.KravHistorikkListe.xsd"/>
//                    <xs:import namespace="http://nav.no/pensjon/v1/ektefellePartnerSamboerListe" schemaLocation="v1.EktefellePartnerSamboerListe.xsd"/>
//                    <xs:element name="pensjonsinformasjon" type="Pensjonsinformasjon"/>
//                    <xs:complexType name="Pensjonsinformasjon">
//                        <xs:all>
//                            <xs:element minOccurs="0" name="brukersSakerListe" type="brukersSakerListe:v1.BrukersSakerListe"/>
//                        </xs:all>
//                    </xs:complexType>
//                </xs:schema>
//            """.trimIndent()
//
//}
