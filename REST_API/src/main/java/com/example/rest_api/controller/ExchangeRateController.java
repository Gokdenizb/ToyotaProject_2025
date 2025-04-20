package com.example.rest_api.controller;

import com.example.rest_api.service.ExchangeRateSimulationService;
import com.example.rest_api.service.ExchangeRateSimulationService.ExchangeRateDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/rates")
public class ExchangeRateController {

    private final ExchangeRateSimulationService simulationService;

    public ExchangeRateController(ExchangeRateSimulationService exchangeRateSimulationService){
        this.simulationService = exchangeRateSimulationService;
    }

    @GetMapping("/{currencyPair}")
    public ExchangeRateDTO getExchangeRates(@PathVariable String currencyPair){
        return simulationService.getCurrentRate(currencyPair);
    }
}
