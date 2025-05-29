package com.example.mainprogram.Kafka;

import com.example.mainprogram.Rate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaRateProducer {
    private final KafkaTemplate<String, Rate> kafkaTemplate;

    public KafkaRateProducer(KafkaTemplate<String, Rate> kafkaTemplate){
        this.kafkaTemplate = kafkaTemplate;
    }
    public void sendRateData(Rate rate){
        kafkaTemplate.send("rate-topic", rate.getRateName(), rate);
    }
}
