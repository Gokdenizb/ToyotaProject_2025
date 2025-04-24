package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class ConfigLoader {

    @JsonProperty("stream_amount")
    private int streamAmount;

    @JsonProperty("stream_frequency")
    private int streamFrequency;

    @JsonProperty("currency_pairs")
    private List<CurrencyConfig> currencyConfigs;

    public static class CurrencyConfig{
        @JsonProperty("name")
        private String name;
        @JsonProperty("initial_bid")
        private double initialBid;
        @JsonProperty("initial_ask")
        private double initialAsk;
        @JsonProperty("update_factor")
        private double updateFactor;

        public String getName() { return name; }
        public double getInitialBid() { return initialBid; }
        public double getInitialAsk() { return initialAsk; }
        public double getUpdateFactor() { return updateFactor; }
    }

    public static ConfigLoader loadFromResource(String resource) throws IOException {
        InputStream in = ConfigLoader.class
                .getClassLoader()
                .getResourceAsStream(resource);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + resource);
        }
        return new ObjectMapper().readValue(in, ConfigLoader.class);
    }


    public static ConfigLoader loadFromConfigFile(String path) throws IOException {
        return new ObjectMapper().readValue(new File(path),ConfigLoader.class);
    }

    public int getStreamAmount() { return streamAmount; }
    public int getStreamFrequency() { return streamFrequency; }
    public List<CurrencyConfig> getCurrencyConfigs() { return currencyConfigs; }

    public CurrencyConfig getConfigFor(String pairName){
        return currencyConfigs.stream()
                .filter(c -> c.getName().equalsIgnoreCase(pairName))
                .findFirst()
                .orElse(null);
    }



}
