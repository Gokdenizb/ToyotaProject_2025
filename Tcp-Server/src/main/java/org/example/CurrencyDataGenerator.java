package org.example;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;


public class CurrencyDataGenerator {
    private static final Random random = new Random();
    private static final LocalDateTime localDateTime = LocalDateTime.now();
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static double initial_bid;
    private static double initial_ask;
    private static double update_factor;

    public static void configure(ConfigLoader configLoader){
        initial_bid = configLoader.getInitialBid();
        initial_ask = configLoader.getInitialAsk();
        update_factor = configLoader.getVariationUpdateFactor();
    }

    public static String generateRateUSDTRY(String currencyPair) {
        double bid = initial_bid + (random.nextDouble() * update_factor);
        double ask = initial_ask + (random.nextDouble() * update_factor);

        return currencyPair + "|22:number:" + String.format("%.4f", bid) +
                "|25:number:" + String.format("%.4f", ask) +
                "|5:timestamp:" + LocalDateTime.now().format(dateTimeFormatter);
    }
}
