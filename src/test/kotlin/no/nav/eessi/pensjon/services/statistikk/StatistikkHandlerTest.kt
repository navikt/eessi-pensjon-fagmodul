package no.nav.eessi.pensjon.services.statistikk

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.spyk
import io.mockk.verify
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.util.concurrent.SettableListenableFuture


class StatistikkHandlerTest{

    @MockK
    lateinit var template : KafkaTemplate<String, String>

    @MockK
    lateinit var recordMetadata: RecordMetadata

    lateinit var statHandler : StatistikkHandler

    @BeforeEach
    fun setup(){
        MockKAnnotations.init(this, relaxed = true, relaxUnitFun = true)

        statHandler = spyk(StatistikkHandler( template, "eessi-pensjon-statistikk"))
        every { statHandler.populerMDC() } returns "key"
    }

    @Test
    fun `Det legges en buc melding på kakfa-kø`(){
        val future: SettableListenableFuture<SendResult<String, String>> = SettableListenableFuture()
        every { template.sendDefault(any(), any()) } returns future

        ReflectionTestUtils.setField(statHandler, "statistikkTopic", "eessi-pensjon-statistikk" )

        val record =  ProducerRecord<String, String>("","")
        future.set( SendResult(record, recordMetadata ) )

        statHandler.produserBucOpprettetHendelse(rinaid = "", dokumentId = null)
        verify (exactly = 1) { template.sendDefault(any(), any()) }
    }
}