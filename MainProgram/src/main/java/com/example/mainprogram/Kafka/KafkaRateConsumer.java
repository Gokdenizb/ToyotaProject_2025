package com.example.mainprogram.Kafka;

import com.example.mainprogram.Rate.Rate;
import com.example.mainprogram.Rate.RateRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaRateConsumer {
    private final RateRepository rateRepository;

    public KafkaRateConsumer(RateRepository rateRepository){
        this.rateRepository = rateRepository;
    }

    @KafkaListener(topics = "rate-topic", groupId = "postgres-group")
    public void consumeRate(Rate rate){
        rateRepository.save(rate);
    }

}
