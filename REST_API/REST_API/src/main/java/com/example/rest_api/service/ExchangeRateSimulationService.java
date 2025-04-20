package com.example.rest_api.service;


import com.example.rest_api.controller.ExchangeRateController;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class ExchangeRateSimulationService {

    @JsonProperty("initial_bid")
    private BigDecimal bid;

    @JsonProperty("initial_ask")
    private BigDecimal ask;

    @JsonProperty("update_factor")
    private BigDecimal updateFactor;

    @JsonProperty("stream_amount")
    private int streamAmount;

    @JsonProperty("stream_frequency")
    private int streamFrequency;
    private int streamCount;
    private Random random;
    private LocalDateTime lastUpdate;
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");



    public static class SimulationConfigPOJO {

        public BigDecimal initialBid;
        public BigDecimal initialAsk;
        public BigDecimal updateFactor;
        public int streamAmount;
        public int streamFrequency;

    }

    private SimulationConfigPOJO simulationConfigPOJO;

    public static class ExchangeRateDTO{

        private String rateName;
        private BigDecimal bid;
        private BigDecimal ask;
        private LocalDateTime timeStamp;

        public String getRateName(){
            return this.rateName;
        }

        public void setRateName(String rateName){
            this.rateName = rateName;
        }

        public BigDecimal getBid(){
            return bid;
        }

        public void setBid(BigDecimal bid){
            this.bid = bid;
        }

        public BigDecimal getAsk(){
            return this.ask;
        }

        public void setAsk(BigDecimal ask){
            this.ask = ask;
        }

        public LocalDateTime getTimeStamp(){
            return this.timeStamp;
        }

        public void setTimeStamp(LocalDateTime timeStamp){
            this.timeStamp = timeStamp;
        }
    }

    @PostConstruct
    public void init(){
        ObjectMapper objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        try(InputStream is = new ClassPathResource("config.json").getInputStream()){
            simulationConfigPOJO = objectMapper.readValue(is , SimulationConfigPOJO.class);

        } catch (IOException e){
            throw new RuntimeException("Could not load config file ", e);
        }

        this.bid = simulationConfigPOJO.initialBid;
        this.ask = simulationConfigPOJO.initialAsk;
        this.updateFactor = simulationConfigPOJO.updateFactor;
        this.streamAmount = simulationConfigPOJO.streamAmount;
        this.streamFrequency = simulationConfigPOJO.streamFrequency;
        this.streamCount = 0;
        this.lastUpdate = LocalDateTime.now();
        this.random = new Random();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate( this::simulateUpdate ,0 , streamFrequency , TimeUnit.MILLISECONDS);


    }

    public void simulateUpdate(){
        if(streamCount >= streamAmount){
            streamCount = 0;
            bid = simulationConfigPOJO.initialBid;
            ask = simulationConfigPOJO.initialAsk;
        }
        else {
            double realBid = random.nextDouble() * updateFactor.doubleValue();
            double realAsk = random.nextDouble() * updateFactor.doubleValue();

            bid = bid.add(BigDecimal.valueOf(random.nextBoolean() ? realBid : -realBid));
            ask = ask.add(BigDecimal.valueOf(random.nextBoolean() ? realAsk : -realAsk));

            if(ask.compareTo(bid) <= 0){
                ask = bid.add(BigDecimal.valueOf(0.01));
            }

            lastUpdate = LocalDateTime.now();
            streamCount++;
        }
    }

    public ExchangeRateDTO getCurrentRate(String currencyPair){
        ExchangeRateDTO getRate = new ExchangeRateDTO();
        getRate.setRateName(currencyPair);
        getRate.setBid(bid);
        getRate.setAsk(ask);
        getRate.setTimeStamp(lastUpdate);
        return getRate;
    }




}
