package com.example.rest_api.service;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class ExchangeRateSimulationService {


    public static class SimulationConfigPOJO {
        @JsonProperty("stream_amount")
        public int streamAmount;

        @JsonProperty("stream_frequency")
        public int streamFrequency;

        @JsonProperty("currency_pairs")
        public List<PairConfig> currencyPairs;
    }

    public static class PairConfig{
        @JsonProperty("name")
        public String name;

        @JsonProperty("initial_bid")
        public BigDecimal initialBid;

        @JsonProperty("initial_ask")
        public BigDecimal initialAsk;

        @JsonProperty("update_factor")
        public BigDecimal updateFactor;
    }

    private static class Simulator{
        private final String name;
        private final BigDecimal initialBid , initialAsk , updateFactor;
        private final int streamAmount;
        private int streamCount;
        private BigDecimal currentBid , currentAsk;
        private LocalDateTime lastUpdate;
        private final Random random = new Random();
        private ExchangeRateDTO lastEmitted;

        Simulator(PairConfig cfg , int streamAmount){
            this.name = cfg.name;
            this.initialBid = cfg.initialBid;
            this.initialAsk = cfg.initialAsk;
            this.updateFactor = cfg.updateFactor;
            this.streamAmount = streamAmount;
            reset();
        }

        private void reset() {
            this.currentBid = initialBid;
            this.currentAsk = initialAsk;
            this.streamCount = 0;
            this.lastUpdate = LocalDateTime.now();
            this.lastEmitted = buildDTO();
        }

        private ExchangeRateDTO buildDTO() {
            ExchangeRateDTO dto = new ExchangeRateDTO();
            dto.setRateName(name);
            dto.setBid(currentBid);
            dto.setAsk(currentAsk);
            dto.setTimeStamp(lastUpdate);
            return dto;
        }

        void tick(){
            if(streamCount >= streamAmount){
                reset();
            } else {
                double diffBid = random.nextDouble() * updateFactor.doubleValue();
                double diffAsk = random.nextDouble() * updateFactor.doubleValue();
                BigDecimal candBid = currentBid.add(BigDecimal.valueOf(random.nextBoolean() ? diffBid : -diffBid));
                BigDecimal candAsk = currentBid.add(BigDecimal.valueOf(random.nextBoolean() ? diffAsk : -diffAsk));

                if (candAsk.compareTo(candBid) <= 0){
                    candAsk = candBid.add(BigDecimal.valueOf(0.01));
                }
                LocalDateTime now = LocalDateTime.now();

                if (pctDiff(currentBid , candBid) > 1 || pctDiff(currentAsk , candAsk) > 1){
                    candBid = currentBid;
                    candAsk = currentAsk;
                    now = lastUpdate;
                }
                currentBid = candBid;
                currentAsk = candAsk;
                lastUpdate = now;
                streamCount++;
                lastEmitted = buildDTO();
            }
        }

        ExchangeRateDTO getLastEmitted(){
            return lastEmitted;
        }

        /**
         * Calculates the absolute percentage difference between two {@link BigDecimal} values.
         * <p>
         * This does:
         * <ol>
         *   <li>Take the absolute difference: {@code |oldVal − newVal|}.</li>
         *   <li>Divide by {@code oldVal} with 8 decimal places, rounded half‑up.</li>
         *   <li>Multiply the result by 100 to convert to a percentage.</li>
         * </ol>
         * </p>
         *
         * @param oldVal the original value (the denominator); if zero, this method returns 0
         * @param newVal the new value to compare against
         * @return the absolute percent change between oldVal and newVal (e.g. 2.0 for a 2% change)
         * @throws NullPointerException if {@code oldVal} or {@code newVal} is null
         */
        private double pctDiff(BigDecimal oldVal , BigDecimal newVal){
            if (oldVal.compareTo(newVal) == 0){
                return 0;
            }

            return oldVal.subtract(newVal).abs()
                    .divide(oldVal , 8 , BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }
    }

    private final Map<String , Simulator> sim = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() throws Exception{
        ObjectMapper objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        try(InputStream is = new ClassPathResource("config.json").getInputStream()){
            SimulationConfigPOJO simulationConfigPOJO =
                    objectMapper.readValue(is , SimulationConfigPOJO.class);

            for(PairConfig pc : simulationConfigPOJO.currencyPairs){
                sim.put(pc.name , new Simulator(pc , simulationConfigPOJO.streamAmount));
            }

            ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
            sched.scheduleAtFixedRate(
                    () -> sim.values().forEach(Simulator::tick), 0,
                    simulationConfigPOJO.streamFrequency,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    public ExchangeRateDTO getCurrentRate(String currencyPair){
        Simulator Sim = sim.get(currencyPair);
        if(Sim == null){
            throw new NoSuchElementException("Unknown pair: " + currencyPair);
        }
        return Sim.getLastEmitted();
    }


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
}
