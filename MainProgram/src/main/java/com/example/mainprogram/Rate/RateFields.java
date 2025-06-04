package com.example.mainprogram.Rate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RateFields {
    private final BigDecimal bid;
    private final BigDecimal ask;
    private final LocalDateTime timestamp;

    public RateFields(BigDecimal bid,
                      BigDecimal ask,
                      LocalDateTime timestamp) {
        this.bid       = bid;
        this.ask       = ask;
        this.timestamp = timestamp;
    }

    public BigDecimal getBid()         { return bid;       }
    public BigDecimal getAsk()         { return ask;       }
    public LocalDateTime getTimestamp(){ return timestamp; }
}
