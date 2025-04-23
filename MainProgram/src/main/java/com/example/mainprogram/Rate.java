package com.example.mainprogram;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Rate {
    private final String platformName;
    private final String rateName;
    private final BigDecimal bid;
    private final BigDecimal ask;
    private final LocalDateTime timestamp;

    public Rate(String platformName,
                String rateName,
                BigDecimal bid,
                BigDecimal ask,
                LocalDateTime timestamp) {
        this.platformName = platformName;
        this.rateName     = rateName;
        this.bid          = bid;
        this.ask          = ask;
        this.timestamp    = timestamp;
    }

    // getter’lar
    public String getPlatformName() { return platformName; }
    public String getRateName()     { return rateName;     }
    public BigDecimal getBid()      { return bid;          }
    public BigDecimal getAsk()      { return ask;          }
    public LocalDateTime getTimestamp() { return timestamp;  }

    @Override
    public String toString() {
        return String.format("[%s][%s] bid=%s ask=%s @%s",
                platformName, rateName, bid, ask, timestamp);
    }
}
