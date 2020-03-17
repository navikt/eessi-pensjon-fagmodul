package no.nav.eessi.pensjon.metrics

import io.micrometer.core.instrument.*
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct


@Component
class MetricsHelper(val registry: MeterRegistry) {

    enum class MeterName {
        PrefillSed,
        PensjonControllerHentSakType,
        SafControllerMetadata,
        SafControllerInnhold,
        PersonControllerHentPerson,
        PersonControllerHentPersonNavn,
        VedleggPaaDokument,
        OpprettSvarSED,
        SEDByDocumentId,
        Institusjoner,
        HentRinasaker,
        CreateBUC,
        BUCDeltakere,
        PutMottaker,
        OpprettSED,
        AddInstutionAndDocument,
        AddDocumentToParent,
        SendSED,
        SlettSED,
        GetBUC,
        PensjoninformasjonHentKunSakType,
        PensjoninformasjonHentAltPaaIdent,
        PensjoninformasjonHentAltPaaIdentRequester,
        PensjoninformasjonAltPaaVedtak,
        PensjoninformasjonAltPaaVedtakRequester,
        AktoerNorskIdentForAktorId,
        AktoerforNorskIdent,
        AktoerRequester,
        HentPersonV3,
        PingEux,
        HentDokumentMetadata,
        HentDokumentInnhold,
        HentRinaSakIderFraDokumentMetadata,
        KodeverkHentLandKode;
    }

    enum class MeterNameExtraTag(val tags: Iterable<Tag>) {
        AddInstutionAndDocument(tags = listOf<Tag>(Tag.of("sedType",""), Tag.of("bucType",""), Tag.of("rinaId",""), Tag.of("land",""), Tag.of("sakNr",""), Tag.of("type","Opprett"), Tag.of("timeStamp",""))),
        AddDocumentToParent(tags = listOf(Tag.of("sedType",""), Tag.of("bucType",""), Tag.of("rinaId",""), Tag.of("land",""), Tag.of("sakNr",""),  Tag.of("type","Opprett"), Tag.of("timeStamp","")));
    }

    /**
     * Alle counters må legges inn i init listen slik at counteren med konkrete tagger blir initiert med 0.
     * Dette er nødvendig for at grafana alarmer skal fungere i alle tilfeller
     */
    @PostConstruct
    fun initCounters() {
        MeterName.values().forEach { counterName ->
            Counter.builder(measureMeterName)
                    .tag(typeTag, successTypeTagValue)
                    .tag(methodTag, counterName.name)
                    .register(registry)

            Counter.builder(measureMeterName)
                    .tag(typeTag, failureTypeTagValue)
                    .tag(methodTag, counterName.name)
                    .register(registry)
        }

        MeterNameExtraTag.values().forEach { counterName ->
            Counter.builder(measureMeterNameExtra)
                    .tag(methodTag, counterName.name)
                    .tags( counterName.tags )
                    .register(registry)
        }


    }

    fun <R> measure(
            method: MeterName,
            failure: String = failureTypeTagValue,
            success: String = successTypeTagValue,
            meterName: String = measureMeterName,
            block: () -> R): R {

        var typeTagValue = success

        try {
            return Timer.builder("$meterName.$measureTimerSuffix")
                    .tag(methodTag, method.name)
                    .register(registry)
                    .recordCallable {
                        block.invoke()
                    }
        } catch (throwable: Throwable) {
            typeTagValue = failure
            throw throwable
        } finally {
            try {
                Counter.builder(meterName)
                        .tag(methodTag, method.name)
                        .tag(typeTag, typeTagValue)
                        .register(registry)
                        .increment()
            } catch (e: Exception) {
                // ignoring on purpose
            }
        }
    }

    fun measureExtra(
            method: MeterNameExtraTag,
            extraTag: Iterable<Tag>,
            meterName: String = measureMeterNameExtra) {

            Counter.builder(meterName)
                    .tag(methodTag, method.name)
                    .tags(extraTag)
                    .register(registry)
                    .increment()
        }

    companion object Configuration {
        const val incrementMeterName: String = "event"
        const val measureMeterName: String = "method"
        const val measureMeterNameExtra: String = "methodTags"
        const val measureTimerSuffix: String = "timer"

        const val eventTag: String = "event"
        const val methodTag: String = "method"
        const val typeTag: String = "type"

        const val successTypeTagValue: String = "successful"
        const val failureTypeTagValue: String = "failed"
    }
}

