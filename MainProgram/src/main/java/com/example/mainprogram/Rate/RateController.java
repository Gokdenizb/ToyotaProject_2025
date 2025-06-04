package com.example.mainprogram.Rate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/rates")
public class RateController {
    @GetMapping("/{rateName}")
    public Rate getRate(@PathVariable String rateName) {
        //End point deneme verileri fakat dinamik işlenmede kullanılmıyorlar.
        //Bunlar yerine data-fetchers.config içindeki veriler okunuyor.
        BigDecimal bid = BigDecimal.valueOf(38.0);
        BigDecimal ask = BigDecimal.valueOf(38.5);
        return new Rate("PF2", rateName, bid, ask, LocalDateTime.now());
    }
}
