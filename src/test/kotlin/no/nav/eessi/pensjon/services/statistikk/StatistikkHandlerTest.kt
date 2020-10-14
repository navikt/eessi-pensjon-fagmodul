package no.nav.eessi.pensjon.services.statistikk

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.util.concurrent.SettableListenableFuture

@ExtendWith(MockitoExtension::class)
class StatistikkHandlerTest{
    @Mock
    lateinit var template : KafkaTemplate<String, String>

    @Mock
    lateinit var recordMetadata: RecordMetadata

     lateinit var statHandler : StatistikkHandler

    @BeforeEach
    fun setup(){
        val key = "key"

        statHandler = spy(StatistikkHandler(template, "eessi-pensjon-statistikk"))
        doReturn(key).`when`(statHandler).populerMDC()
    }

    @Test
    fun `Det legges en buc melding på kakfa-kø`(){
        val future: SettableListenableFuture<SendResult<String, String>> = SettableListenableFuture()
        doReturn(future).`when`(template).sendDefault(any(), any())
        ReflectionTestUtils.setField(statHandler, "statistikkTopic", "eessi-pensjon-statistikk" )

        val record =  ProducerRecord<String, String>("","")
        future.set( SendResult(record, recordMetadata ) )

        statHandler.produserBucOpprettetHendelse(rinaid = "", bucType = "P_BUC_01", timestamp = 10000L)

        verify(template, times(1)).sendDefault(any(), any())
    }
}