package it.polito.wa2.g15.lab5.paymentservice.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class Consumer {
    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Value("\${kafka.topics.produce}")
    private lateinit var topic :String

    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["\${kafka.topics.consume}"], groupId = "ppr")
    fun listenGroupFoo(consumerRecord: ConsumerRecord<Any, Any>/*, ack: Acknowledgment*/) {
        logger.info("Message received {}", consumerRecord)
        //ack.acknowledge()

        val message: Message<OrderInformationMessage> = MessageBuilder
                .withPayload(OrderInformationMessage(PaymentInfo("prr", LocalDate.now(), "prr"), 9999.999))
                .setHeader(KafkaHeaders.TOPIC, topic)
                .setHeader("X-Custom-Header", "Custom header here")
                .build()
        kafkaTemplate.send(message)
        logger.info("Message sent with success")
    }
}