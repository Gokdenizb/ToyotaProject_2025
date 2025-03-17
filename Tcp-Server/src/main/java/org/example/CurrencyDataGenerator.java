package org.example;

import java.util.Random;

public class CurrencyDataGenerator {
    private static final Random random = new Random();

    public static String generateRate(String currencyPair){
        double bid = (random.nextDouble() * 2);
        double ask = bid + (random.nextDouble() * 0.1);

        return currencyPair + "|" + String.format("%.4f" , bid) + "|" + String.format("%.4f" , ask);
    }
}
