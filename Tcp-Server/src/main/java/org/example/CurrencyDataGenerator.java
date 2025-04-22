package org.example;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;


public class CurrencyDataGenerator {
    private static final Random random = new Random();
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    private static Map<String, ConfigLoader.CurrencyConfig> configMap;


    public static void configure(ConfigLoader configLoader){
        configMap = new HashMap<>();
        for (ConfigLoader.CurrencyConfig cfg : configLoader.getCurrencyConfigs()){
            configMap.put(cfg.getName() , cfg);
        }
    }

    public static String generateRate(String currencyPair) {
        ConfigLoader.CurrencyConfig cfg = configMap.get(currencyPair);
        if(cfg == null){
            throw new IllegalArgumentException("Bilinmeyen veri: " + currencyPair);
        }

        double bid = cfg.getInitialBid() + (random.nextDouble() * cfg.getUpdateFactor());
        double ask = cfg.getInitialAsk() + (random.nextDouble() * cfg.getUpdateFactor());

        return currencyPair
                + "|22:number:" + String.format(Locale.US , "%.4f", bid)
                + "|25:number:" + String.format(Locale.US , "%.4f", ask)
                + "|5:timestamp:" + LocalDateTime.now().format(dateTimeFormatter);

    }

}
