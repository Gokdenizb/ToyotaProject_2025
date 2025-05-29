package com.example.mainprogram;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class Rate implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String rateName;

    private String platformName;
    private BigDecimal bid;
    private BigDecimal ask;
    private LocalDateTime timestamp;

    public Rate() {
    }

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

    public String getPlatformName() { return platformName; }
    public void setPlatformName(String platformName) { this.platformName = platformName; }

    public String getRateName() { return rateName; }
    public void setRateName(String rateName) { this.rateName = rateName; }

    public BigDecimal getBid() { return bid; }
    public void setBid(BigDecimal bid) { this.bid = bid; }

    public BigDecimal getAsk() { return ask; }
    public void setAsk(BigDecimal ask) { this.ask = ask; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return String.format("[%s][%s] bid=%s ask=%s @%s",
                platformName, rateName, bid, ask, timestamp);
    }
}
