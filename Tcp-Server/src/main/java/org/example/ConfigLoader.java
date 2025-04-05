package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

public class ConfigLoader {

    @JsonProperty("initial_bid")
    private double initial_bid;

    @JsonProperty("initial_ask")
    private double initial_ask;

    @JsonProperty("update_factor")
    private double update_factor;

    @JsonProperty("stream_amount")
    private int stream_amount;

    @JsonProperty("stream_frequency")
    private int stream_frequency;

    public static ConfigLoader loadFromConfigFile(String path) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(new File(path), ConfigLoader.class);
    }

    public double getInitialBid() { return initial_bid; }
    public double getInitialAsk() { return initial_ask; }
    public double getVariationUpdateFactor() { return update_factor; }
    public int getStream_amount() { return stream_amount; }
    public int getStream_frequency() { return stream_frequency; }



}
