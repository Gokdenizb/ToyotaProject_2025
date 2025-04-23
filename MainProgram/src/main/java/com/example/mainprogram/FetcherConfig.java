package com.example.mainprogram;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FetcherConfig {
    public String name;
    @JsonProperty("class")
    public String className;
    public Map<String, Object> config;
    public List<String> rates;
}
