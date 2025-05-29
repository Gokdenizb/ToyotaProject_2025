package com.example.mainprogram.Kafka;


import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.springframework.kafka.core.KafkaTemplate;

public class KafkaLogProducer extends AppenderBase<ILoggingEvent> {
    private KafkaTemplate<String, String> kafkaTemplate;

    @Override
    protected void append(ILoggingEvent event) {
        kafkaTemplate.send("log-topic", event.getFormattedMessage());
    }
}
