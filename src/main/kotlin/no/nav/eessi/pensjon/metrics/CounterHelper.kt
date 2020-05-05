package no.nav.eessi.pensjon.metrics

import io.micrometer.core.instrument.*
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct


@Component
class CounterHelper(val registry: MeterRegistry) {


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
        MeterNameExtraTag.values().forEach { counterName ->
            Counter.builder(measureMeterNameExtra)
                    .tag(methodTag, counterName.name)
                    .tags( counterName.tags )
                    .register(registry)
        }
    }

    fun count(
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
        const val measureMeterNameExtra: String = "methodTags"
        const val methodTag: String = "method"
    }
}

